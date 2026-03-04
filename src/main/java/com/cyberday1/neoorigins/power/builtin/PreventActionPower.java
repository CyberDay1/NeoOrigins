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

    public record Config(Action action, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Action.CODEC.fieldOf("action").forGetter(Config::action),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    // Actual prevention is handled by OriginEventHandler routing to this type
    // These methods are intentionally no-op; the event handler checks the action field
    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
