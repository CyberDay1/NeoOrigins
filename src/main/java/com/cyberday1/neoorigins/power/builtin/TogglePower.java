package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.power.util.PowerUpkeep;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A boolean power with optional upkeep while on. Its state
 * lives in {@code CompatAttachments.toggleState()} keyed by the power's
 * registered id (e.g. {@code mypack:my_toggle}).
 *
 * <p>Read the current value with {@code origins:power_active { power: "mypack:my_toggle" }}
 * and flip it with {@code neoorigins:toggle { power: "mypack:my_toggle" }}.
 *
 * <p>Unlike {@code active_ability}, TogglePower does not consume a keybind slot.
 * It is a named boolean other powers gate on.
 *
 * <p>The {@code default} field sets the value reads see before the toggle has
 * ever been flipped on this player. Read/write goes through the
 * {@link com.cyberday1.neoorigins.compat.Toggles} facade, which resolves the
 * configured default lazily — so {@code "default": true} declares
 * "on until flipped off" and no {@code action_on_event GAINED} hook is needed.
 */
public class TogglePower extends PowerType<TogglePower.Config> {

    public record Config(boolean defaultValue, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("default", false).forGetter(Config::defaultValue),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        ResourceLocation id = PowerHolder.currentDispatchId();
        PowerUpkeep.Upkeep upkeep = PowerUpkeep.forPower(id);
        if (id == null || upkeep.isFree()) return;
        var state = player.getData(CompatAttachments.toggleState());
        if (!state.isActive(id.toString(), config.defaultValue())) return;
        if (PowerUpkeep.tick(player, id, upkeep, true) == PowerUpkeep.Verdict.SHUT_OFF) {
            state.set(id.toString(), false);
            PowerUpkeep.announceShutOff(player, id);
        }
    }
}
