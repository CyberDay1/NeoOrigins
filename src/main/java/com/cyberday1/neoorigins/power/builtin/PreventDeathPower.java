package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.event.CombatPowerEvents;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Optional;

/**
 * Native {@code neoorigins:prevent_death} power — cancels the killing blow
 * instead of letting the player die. Faithful to Origins' {@code prevent_death}
 * but with first-class NeoOrigins gating:
 *
 * <ul>
 *   <li>{@code condition} — an entity {@link EntityCondition}; the power only
 *       saves the player while this is true (parsed with the same
 *       {@link ConditionParser} every other DSL power uses, so any condition
 *       shape works).</li>
 *   <li>{@code damage_types} — a damage-type filter string in the project's
 *       standard {@link CombatPowerEvents#matchesDamageType} convention
 *       (comma-separated, {@code #tags}, msgIds, or registry keys). When set,
 *       only matching killing blows are prevented.</li>
 *   <li>{@code invert} — flips {@code damage_types} into a blacklist: prevent
 *       <em>all</em> deaths <em>except</em> the listed types.</li>
 *   <li>{@code entity_action} — an optional action run on the player each time
 *       a death is prevented.</li>
 *   <li>{@code set_health} — health the player is left at after a save
 *       (default 1.0; the death is canceled, so without this they would sit at
 *       0 HP and die again next tick).</li>
 *   <li>{@code cooldown_ticks} — optional balance lever; after a save the
 *       power goes inert for N ticks. Default 0 = unlimited (Origins behavior).</li>
 * </ul>
 *
 * <p>Like Origins, this only cancels the lethal event — it does not clear the
 * damage source. For recurring damage-over-time (fire, lava, poison) pair it
 * with a {@code condition} or an {@code entity_action} that removes the source,
 * otherwise the player is re-killed on the next damage tick.
 *
 * <p>Hooked from {@link CombatPowerEvents#onLivingDeath} via
 * {@code ActiveOriginService.forEachOfType}, mirroring
 * {@link SlimeDeathSavePower}.
 */
public class PreventDeathPower extends PowerType<PreventDeathPower.Config> {

    private static final String CD_KEY_PREFIX = "prevent_death_cd:";

    public record Config(
        Optional<String> damageTypes,
        boolean invert,
        float setHealth,
        int cooldownTicks,
        EntityAction action,
        Optional<EntityCondition> condition,
        String type,
        String powerId
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "prevent_death: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "prevent_death: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();

                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:prevent_death";
                // _power_id is injected per power file by PowerDataManager; it
                // uniquely identifies THIS power instance, unlike `type` which
                // is the same literal for every prevent_death. Cooldown state is
                // keyed by it so two prevent_death powers don't share a timer.
                String pid = obj.has("_power_id")
                    ? obj.get("_power_id").getAsString() : "neoorigins:prevent_death";

                Optional<String> dmg = obj.has("damage_types")
                    ? Optional.of(obj.get("damage_types").getAsString()) : Optional.empty();
                boolean invert = obj.has("invert") && obj.get("invert").getAsBoolean();
                float setHealth = obj.has("set_health")
                    ? Math.max(1.0f, obj.get("set_health").getAsFloat()) : 1.0f;
                int cooldown = obj.has("cooldown_ticks")
                    ? Math.max(0, obj.get("cooldown_ticks").getAsInt()) : 0;

                EntityAction action = ActionParser.parseField(obj, "entity_action", t);

                Optional<EntityCondition> cond = obj.has("condition")
                    ? Optional.of(ConditionParser.parseField(obj, "condition", t))
                    : Optional.empty();

                return DataResult.success(Pair.of(
                    new Config(dmg, invert, setHealth, cooldown, action, cond, t, pid), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Called from the death event handler when the player would die. Returns
     * true if the death should be canceled (the player was saved).
     */
    public static boolean shouldPreventDeath(ServerPlayer player, Config config, DamageSource source) {
        // Condition gate — power only saves while the condition holds.
        if (config.condition().isPresent() && !config.condition().get().test(player)) {
            return false;
        }

        // Damage-type filter. With no filter every death is prevented; `invert`
        // is meaningless without a list and is ignored in that case.
        if (config.damageTypes().isPresent()) {
            boolean matches = CombatPowerEvents.matchesDamageType(source, config.damageTypes().get());
            if (config.invert()) matches = !matches;
            if (!matches) return false;
        }

        // Cooldown gate. Remaining ticks are stored per power instance (keyed by
        // the injected _power_id) so multiple prevent_death powers don't share
        // a timer.
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        String cdKey = CD_KEY_PREFIX + config.powerId();
        if (config.cooldownTicks() > 0 && data.getCustomFloat(cdKey, 0) > 0) {
            return false;
        }

        // Save: cancel happens in the caller; we just patch HP and fire the
        // action. Health is clamped to at least 1 so the player doesn't sit at
        // 0 HP and re-die on the next tick.
        player.setHealth(Math.max(1.0f, config.setHealth()));
        if (config.action() != EntityAction.noop()) {
            config.action().execute(player);
        }
        if (config.cooldownTicks() > 0) {
            data.setCustomFloat(cdKey, config.cooldownTicks());
        }
        return true;
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.cooldownTicks() <= 0) return;
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        String cdKey = CD_KEY_PREFIX + config.powerId();
        float remaining = data.getCustomFloat(cdKey, 0);
        if (remaining > 0) {
            data.setCustomFloat(cdKey, remaining - 1);
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        // Clear the persisted cooldown so a stale timer doesn't carry over to
        // the next holder of this power id (origin change / orb reroll). The
        // customFloats map is serialized and copy-on-death, so without this a
        // mid-cooldown revoke would freeze the timer until the power returns.
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.setCustomFloat(CD_KEY_PREFIX + config.powerId(), 0);
    }
}
