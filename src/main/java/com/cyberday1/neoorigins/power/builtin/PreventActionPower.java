package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

public class PreventActionPower extends PowerType<PreventActionPower.Config> {

    public enum Action {
        FALL_DAMAGE, FIRE, DROWN, FREEZE, SPRINT_FOOD, CHESTPLATE_EQUIP,
        EYE_DAMAGE, WATER_DAMAGE, NONE;

        public static final Codec<Action> CODEC = Codec.STRING.xmap(
            s -> {
                try { return Action.valueOf(s.toUpperCase()); }
                catch (IllegalArgumentException e) { return NONE; }
            },
            a -> a.name().toLowerCase()
        );
    }

    /**
     * Optional gate that disables the prevention under specific player states.
     * Primary use: let pack authors opt-out of fall-damage prevention while
     * crouching so players can still land critical hits on Avian-style origins.
     * Default {@link #ALWAYS} preserves legacy behaviour.
     */
    public enum ActiveWhen {
        ALWAYS, NOT_SNEAKING, SNEAKING, NOT_ON_GROUND, ON_GROUND;

        public static final Codec<ActiveWhen> CODEC = Codec.STRING.xmap(
            s -> {
                try { return ActiveWhen.valueOf(s.toUpperCase()); }
                catch (IllegalArgumentException e) { return ALWAYS; }
            },
            a -> a.name().toLowerCase()
        );
    }

    public record Config(Action action, ActiveWhen activeWhen, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Action.CODEC.fieldOf("action").forGetter(Config::action),
            ActiveWhen.CODEC.optionalFieldOf("active_when", ActiveWhen.ALWAYS).forGetter(Config::activeWhen),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /** True when the configured gate permits the prevention to apply right now. */
    public static boolean isGateOpen(ServerPlayer player, Config config) {
        return switch (config.activeWhen()) {
            case ALWAYS -> true;
            case NOT_SNEAKING -> !player.isShiftKeyDown();
            case SNEAKING -> player.isShiftKeyDown();
            case NOT_ON_GROUND -> !player.onGround();
            case ON_GROUND -> player.onGround();
        };
    }

    // Actual prevention is handled by OriginEventHandler routing to this type
    // These methods are intentionally no-op; the event handler checks the action field
    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}

    // Clear residual fire ticks for FIRE-immune players so the burning visual
    // doesn't flicker on contact with lava / fire sources — damage is already
    // cancelled in CombatPowerEvents, but the fire-tick counter ticks on
    // independently and the player model still renders flames. See issue #27.
    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.action() == Action.FIRE && isGateOpen(player, config)
                && player.getRemainingFireTicks() > 0) {
            player.setRemainingFireTicks(0);
        }
    }
}
