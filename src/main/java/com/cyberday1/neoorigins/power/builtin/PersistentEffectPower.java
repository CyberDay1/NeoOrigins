package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic condition-gated, toggleable status effect stack.
 *
 * <p>Part of the 2.0 power-type consolidation (Phase 2). Collapses the
 * behaviour of {@code status_effect}, {@code stacking_status_effects},
 * {@code night_vision}, {@code glow}, {@code water_breathing},
 * {@code breath_in_fluid}, and {@code regen_in_fluid} into a single type
 * that applies an arbitrary list of effects when an optional condition is
 * met. Toggleable via keybind; effects are cleared on toggle-off and on
 * revoke.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:persistent_effect",
 *   "toggleable": true,
 *   "default_off": false,
 *   "condition": { "type": "neoorigins:in_water" },
 *   "effects": [
 *     { "effect": "minecraft:water_breathing", "amplifier": 0,
 *       "ambient": true, "show_particles": false, "show_icon": true }
 *   ]
 * }
 * }</pre>
 */
public class PersistentEffectPower extends PowerType<PersistentEffectPower.Config> {

    public record EffectSpec(
        Holder<MobEffect> effect,
        int amplifier,
        boolean ambient,
        boolean showParticles,
        boolean showIcon
    ) {}

    public record Config(
        List<EffectSpec> effects,
        EntityCondition condition,
        boolean toggleable,
        boolean defaultOff,
        String type,
        String cooldownIcon,
        boolean alwaysShowIcon
    ) implements PowerConfiguration, com.cyberday1.neoorigins.power.builtin.base.HudIconConfig {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "persistent_effect: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "persistent_effect: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:persistent_effect";
                boolean toggleable = !obj.has("toggleable") || obj.get("toggleable").getAsBoolean();
                boolean defaultOff = obj.has("default_off") && obj.get("default_off").getAsBoolean();

                // Config kill-switch: a top-level "enabled":false (injected by the
                // power_overrides system when a server admin disables this power)
                // collapses the power to a no-op — no effects to apply, and not
                // toggleable so it never claims a keybind slot. Used by the Warden
                // dark-vision config toggles (issue #101).
                boolean enabled = com.cyberday1.neoorigins.power.util.EnabledGate.isEnabled(obj);
                if (!enabled) {
                    return DataResult.success(Pair.of(
                        new Config(List.of(), EntityCondition.alwaysTrue(), false, false, t, "", false),
                        ops.empty()));
                }

                // Top-level "amplifier" is a config-override hook: the
                // power_overrides system writes fields at the JSON root, so
                // a top-level amplifier lets server admins re-tune the
                // first effect's strength (Resistance I → II, etc.) without
                // touching the nested effects array. When present, it wins
                // over the spec-level value on the first effect; per-effect
                // amplifiers on subsequent specs are unchanged.
                Integer rootAmpOverride = obj.has("amplifier") && obj.get("amplifier").isJsonPrimitive()
                    ? obj.get("amplifier").getAsInt()
                    : null;

                // Top-level show_icon / show_particles / ambient cascade to
                // every nested spec that doesn't set the same field locally.
                // Mollan-reported: setting show_icon at the power root was
                // silently ignored because parseSpec only reads the nested
                // object. With a root value present, it now becomes the
                // default for any nested effect that omits the field.
                Boolean rootShowIcon = readBool(obj, "show_icon");
                Boolean rootShowParticles = readBool(obj, "show_particles");
                Boolean rootAmbient = readBool(obj, "ambient");

                List<EffectSpec> specs = new ArrayList<>();
                if (obj.has("effects") && obj.get("effects").isJsonArray()) {
                    for (var el : obj.getAsJsonArray("effects")) {
                        if (!el.isJsonObject()) continue;
                        EffectSpec spec = parseSpec(el.getAsJsonObject(),
                            rootShowIcon, rootShowParticles, rootAmbient);
                        if (spec != null) specs.add(spec);
                    }
                } else {
                    EffectSpec spec = parseSpec(obj, rootShowIcon, rootShowParticles, rootAmbient);
                    if (spec != null) specs.add(spec);
                }
                if (rootAmpOverride != null && !specs.isEmpty()) {
                    EffectSpec first = specs.get(0);
                    specs.set(0, new EffectSpec(
                        first.effect(), rootAmpOverride,
                        first.ambient(), first.showParticles(), first.showIcon()));
                }

                EntityCondition cond = ConditionParser.parseField(obj, "condition", t);

                // Optional HUD icon: lets toggleable persistent_effects surface on
                // the ability cluster like any other active power (bright/dim pip).
                String cooldownIcon = obj.has("cooldown_icon") && obj.get("cooldown_icon").isJsonPrimitive()
                    ? obj.get("cooldown_icon").getAsString() : "";
                boolean alwaysShowIcon = obj.has("always_show_icon") && obj.get("always_show_icon").getAsBoolean();

                return DataResult.success(Pair.of(
                    new Config(List.copyOf(specs), cond, toggleable, defaultOff, t, cooldownIcon, alwaysShowIcon),
                    ops.empty()));
            }

            private static EffectSpec parseSpec(JsonObject eff,
                                                Boolean rootShowIcon,
                                                Boolean rootShowParticles,
                                                Boolean rootAmbient) {
                String effectId = eff.has("effect") ? eff.get("effect").getAsString()
                               : eff.has("id") ? eff.get("id").getAsString() : null;
                if (effectId == null) return null;
                var holderOpt = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId));
                if (holderOpt.isEmpty()) return null;
                int amp = eff.has("amplifier") ? eff.get("amplifier").getAsInt() : 0;
                // Local field wins; otherwise inherit root override; otherwise use the original default.
                boolean ambient = eff.has("ambient")
                    ? eff.get("ambient").getAsBoolean()
                    : rootAmbient != null ? rootAmbient : true;
                boolean particles = eff.has("show_particles")
                    ? eff.get("show_particles").getAsBoolean()
                    : rootShowParticles != null ? rootShowParticles : false;
                boolean icon = eff.has("show_icon")
                    ? eff.get("show_icon").getAsBoolean()
                    : rootShowIcon != null ? rootShowIcon : true;
                return new EffectSpec(holderOpt.get(), amp, ambient, particles, icon);
            }

            private static Boolean readBool(JsonObject obj, String key) {
                if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return null;
                try { return obj.get(key).getAsBoolean(); } catch (Exception e) { return null; }
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public boolean isActivePower(Config config) { return config.toggleable(); }

    /**
     * Whether the holder currently has this toggleable power switched off.
     * Exposed so the network layer can mirror toggle state to the HUD ability
     * cluster (bright = on, dimmed = off) the same way it does for
     * {@code AbstractTogglePower}s.
     */
    public boolean isToggledOff(ServerPlayer player, Config config) {
        return isToggledOff(player, config, PowerHolder.currentDispatchId());
    }

    /** As above, for callers outside a {@link PowerHolder} dispatch (the HUD sync). */
    public boolean isToggledOff(ServerPlayer player, Config config, Identifier id) {
        if (!config.toggleable()) return false;
        return player.getData(OriginAttachments.originData())
            .isPowerToggledOff(toggleKey(id, config), legacyToggleKey(config));
    }

    /**
     * Per-instance toggle key: the power's own resource id.
     *
     * <p>This used to be built from the effect IDs, which separated Breeze's
     * Cushion of Air from its Updraft but still collapsed any two powers
     * granting the SAME effect into one flag — amplifier and duration were not
     * part of the key, so two tiers of one buff shared a toggle.
     */
    private String toggleKey(Config config) {
        return toggleKey(PowerHolder.currentDispatchId(), config);
    }

    String toggleKey(Identifier id, Config config) {
        return id != null ? id.toString() : legacyToggleKey(config);
    }

    /** The pre-2.2.24 effect-derived key, read as a fallback so saved toggles survive. */
    String legacyToggleKey(Config config) {
        if (config.effects().isEmpty()) return getClass().getName();
        StringBuilder sb = new StringBuilder(getClass().getName());
        for (EffectSpec spec : config.effects()) {
            sb.append(':');
            var key = spec.effect().unwrapKey();
            sb.append(key.map(k -> k.identifier().toString()).orElse("unknown"));
        }
        return sb.toString();
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        // Seed the toggle off-state on first grant when the pack authored
        // default_off:true — we want the power to START disabled so the
        // player opts in. Without this, PersistentEffectPower's onTick would
        // immediately apply the effect on the next tick.
        if (config.toggleable() && config.defaultOff()) {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            data.setPowerToggledOff(toggleKey(config), legacyToggleKey(config), true);
        }
    }

    @Override
    public void onActivated(ServerPlayer player, Config config) {
        if (!config.toggleable()) return;
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        boolean wasOff = data.isPowerToggledOff(toggleKey(config), legacyToggleKey(config));
        if (wasOff) {
            data.setPowerToggledOff(toggleKey(config), legacyToggleKey(config), false);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.on")
                .withStyle(ChatFormatting.GREEN));
        } else {
            data.setPowerToggledOff(toggleKey(config), legacyToggleKey(config), true);
            clearEffects(player, config);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.off")
                .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.toggleable()) {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            if (data.isPowerToggledOff(toggleKey(config), legacyToggleKey(config))) return;
        }
        if (!config.condition().test(player)) {
            // Condition now false: clear our effects so the player isn't permanently buffed.
            clearEffects(player, config);
            return;
        }
        // Apply effects with INFINITE_DURATION so they never tick down and the
        // vanilla "potion ending" flicker never triggers. Revoke / toggle-off /
        // condition-false all call clearEffects explicitly, so we don't rely on
        // the effect expiring naturally.
        for (EffectSpec spec : config.effects()) {
            if (isNightVisionSuppressed(player, spec)) continue;
            var existing = player.getEffect(spec.effect());
            // Reapply when: nothing present, OR the present effect is weaker
            // (lower amplifier) than ours, OR the present effect is a finite
            // buff that we can safely upgrade to our infinite form ONLY when it
            // is not STRONGER than us. This last clause prevents clobbering a
            // stronger finite buff (e.g. Frenzy's finite amp1 strength) with a
            // weaker infinite persistent effect (amp0).
            if (existing == null
                || existing.getAmplifier() < spec.amplifier()
                || (!existing.isInfiniteDuration() && existing.getAmplifier() <= spec.amplifier())) {
                player.addEffect(new MobEffectInstance(
                    spec.effect(), MobEffectInstance.INFINITE_DURATION, spec.amplifier(),
                    spec.ambient(), spec.showParticles(), spec.showIcon()));
            }
        }
    }

    private static final Identifier NIGHT_VISION_ID =
        Identifier.withDefaultNamespace("night_vision");

    /** True when this spec grants vanilla {@code minecraft:night_vision}. */
    private static boolean isNightVisionSpec(EffectSpec spec) {
        return spec.effect().unwrapKey()
            .map(k -> k.identifier().equals(NIGHT_VISION_ID))
            .orElse(false);
    }

    /**
     * Two-stage night-vision gate. Only {@code minecraft:night_vision} is ever
     * stripped — other effects on the same power still apply, so a power that
     * grants water_breathing + night_vision keeps the water_breathing.
     *
     * <ol>
     *   <li><b>Admin kill-switch</b> — {@code disable_night_vision} in
     *       content.toml. Checked FIRST and unconditionally: a server admin's
     *       decision outranks any player preference, so a player toggling their
     *       key can never re-enable night vision on a server that banned it.</li>
     *   <li><b>Player master switch</b> — the dedicated "Toggle Night Vision"
     *       keybind, stored on {@link PlayerOriginData}. Defaults to enabled, so
     *       untouched players get the historical always-on behaviour.</li>
     * </ol>
     *
     * <p>Gating on the EFFECT rather than on the owning power is what makes one
     * keypress cover an origin's whole night-vision kit: caveborn's base,
     * evolved and ascended night-vision powers are three separate
     * persistent_effect instances with three separate toggle keys, but they all
     * grant the same effect and so all consult the same single player flag.
     */
    private static boolean isNightVisionSuppressed(ServerPlayer player, EffectSpec spec) {
        if (!isNightVisionSpec(spec)) return false;
        if (com.cyberday1.neoorigins.config.ContentTogglesConfig.isNightVisionDisabled()) {
            return true;
        }
        return !player.getData(OriginAttachments.originData()).isNightVisionEnabled();
    }

    /**
     * True if any of {@code powers} is a persistent_effect granting
     * {@code minecraft:night_vision} — i.e. if the night-vision master toggle has
     * anything at all to act on for this player.
     *
     * <p>The toggle is consulted in exactly one place, {@link #isNightVisionSuppressed},
     * and only for night-vision effect specs. Roughly half the built-in origins own no
     * such spec, so without this test the keybind reported success to players it could
     * never do anything for.
     *
     * <p>Deliberately asks the whole granted set rather than the currently-satisfied
     * one: the question is "does your origin have night vision", not "is it lit right
     * now". A power gated on a condition (in water, at night) still counts, or the key
     * would deny the player the moment they stepped outside the gate.
     *
     * <p>Pure, and takes the list rather than the player, so the decision can be
     * exercised without a server.
     */
    public static boolean grantsNightVision(List<com.cyberday1.neoorigins.api.power.PowerHolder<?>> powers) {
        for (var holder : powers) {
            if (!(holder.type() instanceof PersistentEffectPower)) continue;
            if (!(holder.config() instanceof Config config)) continue;
            for (EffectSpec spec : config.effects()) {
                if (isNightVisionSpec(spec)) return true;
            }
        }
        return false;
    }

    /**
     * Drop any night vision this power system currently owns on the player.
     * Called when the player switches their master toggle off — without it the
     * INFINITE_DURATION instance already on them would simply never expire.
     *
     * <p>Only infinite-duration instances are removed: a finite night vision the
     * player drank or was given by an action is not ours to take away. Turning
     * the toggle back on needs no counterpart — {@link #onTick} reapplies on the
     * very next tick once the gate opens.
     */
    public static void clearOwnedNightVision(ServerPlayer player) {
        var existing = player.getEffect(
            net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        if (existing != null && existing.isInfiniteDuration()) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.setPowerToggledOff(toggleKey(config), legacyToggleKey(config), false);
        clearEffects(player, config);
    }

    private void clearEffects(ServerPlayer player, Config config) {
        for (EffectSpec spec : config.effects()) {
            // Only remove the effect if the instance currently on the player is
            // one THIS persistent system owns at THIS tier — i.e. an infinite
            // duration effect at our exact amplifier. This prevents an inactive
            // tier (e.g. Bloodrage II/III) from stripping a lower tier's effect
            // (different amplifier) or a timed buff from another source (Frenzy,
            // finite duration) that merely shares the same effect type.
            var existing = player.getEffect(spec.effect());
            if (existing != null
                && existing.isInfiniteDuration()
                && existing.getAmplifier() == spec.amplifier()) {
                player.removeEffect(spec.effect());
            }
        }
    }

    // ── Mob-origin support (reference implementation) ───────────────────────
    // Mobs have no keybind toggle and no per-player condition context, so the
    // effects are applied unconditionally as infinite-duration. Condition-gated
    // mob behavior arrives with neoorigins:mob_behavior (Phase 2b).

    @Override
    public boolean appliesToMobs(Config config) { return true; }

    @Override
    public void applyToMob(LivingEntity mob, Config config) {
        for (EffectSpec spec : config.effects()) {
            mob.addEffect(new MobEffectInstance(
                spec.effect(), MobEffectInstance.INFINITE_DURATION, spec.amplifier(),
                spec.ambient(), spec.showParticles(), spec.showIcon()));
        }
    }

    @Override
    public void removeFromMob(LivingEntity mob, Config config) {
        for (EffectSpec spec : config.effects()) {
            mob.removeEffect(spec.effect());
        }
    }
}
