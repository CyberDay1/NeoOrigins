package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.condition.TargetConditionParser;
import com.cyberday1.neoorigins.network.payload.SyncGlowingEntitiesPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Native {@code neoorigins:entity_glow}: living entities matching
 * {@code entity_condition} (and {@code bientity_condition}, with the holder as
 * actor) glow for the holder, and only for the holder, the way a Spectral Arrow
 * outlines a target. Apoli's power of the same name, fields included; Origins'
 * Arachnid uses it to sense non-arthropods caught in cobweb.
 *
 * <p>Same shape as {@link PreventEntityRenderPower}, for the same reasons, and it
 * shares that power's condition reader and entity scan: the server evaluates the
 * conditions every few ticks and syncs the matching ids, each with its outline
 * colour, to the holder alone. The target gets no Glowing effect, so nobody else
 * sees the outline. Colour follows Apoli: with {@code use_teams} on, an entity on
 * a coloured team keeps its team colour; otherwise {@code red}/{@code green}/
 * {@code blue} is used.
 */
public class EntityGlowPower extends PowerType<EntityGlowPower.Config> {

    private static final int EVAL_INTERVAL_TICKS = 5;

    private static final BiPredicate<ServerPlayer, Entity> NEVER = (a, t) -> false;

    private static final Codec<PreventEntityRenderPower.Config> SELECTION =
        PreventEntityRenderPower.Config.codecFor("entity_glow", "outline");

    public record Config(
        PreventEntityRenderPower.Config selection,
        Optional<BiPredicate<ServerPlayer, Entity>> bientityCondition,
        boolean useTeams,
        float red,
        float green,
        float blue
    ) implements PowerConfiguration {

        /** The packed RGB the outline uses when the team colour does not apply. */
        public int rgb() {
            return (channel(red) << 16) | (channel(green) << 8) | channel(blue);
        }

        private static int channel(float v) {
            return Math.round(Math.max(0F, Math.min(1F, v)) * 255F);
        }

        /** The colour to sync for {@code target}: {@code TEAM_COLOR} when vanilla's colour is right. */
        public int colorFor(Entity target) {
            if (useTeams && target.getTeam() != null && target.getTeam().getColor().isPresent()) {
                return SyncGlowingEntitiesPayload.TEAM_COLOR;
            }
            int rgb = rgb();
            return rgb == 0xFFFFFF ? SyncGlowingEntitiesPayload.TEAM_COLOR : rgb;
        }

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "entity_glow: not a JSON object");
                JsonObject obj = json.getAsJsonObject();
                return SELECTION.parse(JsonOps.INSTANCE, obj).map(selection -> {
                    Optional<BiPredicate<ServerPlayer, Entity>> bi = Optional.empty();
                    if (obj.has("bientity_condition") && obj.get("bientity_condition").isJsonObject()) {
                        var parsed = TargetConditionParser.parseBiEntity(
                            obj.getAsJsonObject("bientity_condition"), selection.type());
                        if (parsed == null) {
                            NeoOrigins.LOGGER.warn("[NeoOrigins] entity_glow ({}): bientity_condition uses a verb "
                                + "that cannot be evaluated against an arbitrary entity, so the power will "
                                + "outline nothing rather than outline everything", selection.type());
                            parsed = NEVER;
                        }
                        bi = Optional.of(parsed);
                    }
                    Config config = new Config(selection, bi,
                        !obj.has("use_teams") || obj.get("use_teams").getAsBoolean(),
                        channelField(obj, "red"), channelField(obj, "green"), channelField(obj, "blue"));
                    return Pair.of(config, ops.empty());
                });
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };

        private static float channelField(JsonObject obj, String key) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsFloat() : 1.0F;
        }
    }

    private static final Map<UUID, Map<Integer, Integer>> lastSent = new ConcurrentHashMap<>();

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % EVAL_INTERVAL_TICKS != 0) return;
        Map<Integer, Integer> glowing = new LinkedHashMap<>();
        for (int id : PreventEntityRenderPower.matchingEntityIds(player, config.selection())) {
            Entity target = player.level().getEntity(id);
            if (target == null) continue;
            if (config.bientityCondition().isPresent() && !config.bientityCondition().get().test(player, target)) continue;
            glowing.put(id, config.colorFor(target));
        }
        if (glowing.equals(lastSent.get(player.getUUID()))) return;
        lastSent.put(player.getUUID(), glowing);
        PacketDistributor.sendToPlayer(player, new SyncGlowingEntitiesPayload(glowing));
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        lastSent.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new SyncGlowingEntitiesPayload(Map.of()));
    }

    @Override
    public void onRespawn(ServerPlayer player, Config config) {
        lastSent.remove(player.getUUID());
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        lastSent.remove(player.getUUID());
    }
}
