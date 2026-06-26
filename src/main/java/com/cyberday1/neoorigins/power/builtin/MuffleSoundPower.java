package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Muffles the game-event vibrations the player emits, so sculk sensors,
 * calibrated sculk sensors and wardens stop detecting the player's footsteps,
 * item use, block interactions, etc.
 *
 * <p>The "kitsune" Origins pack called this a "sneaky" power; NeoOrigins had no
 * native equivalent, so the feature was lost when the pack was rewritten
 * natively. This restores it as a proper registered power type.
 *
 * <p>Hook: the player's outgoing game events flow through
 * {@code Level.gameEvent(Entity, GameEvent, Vec3)} and surface on NeoForge's
 * cancelable {@link net.neoforged.neoforge.event.VanillaGameEvent}. The handler
 * in {@code com.cyberday1.neoorigins.event.WorldPowerEvents} cancels that event
 * when its cause is a player holding this power, which stops the vibration from
 * ever being enqueued for any {@code GameEventListener} (sculk / warden).
 *
 * <p>By default emission is fully suppressed. {@code strength} (0.0-1.0) is the
 * fraction of emissions suppressed: {@code 1.0} = silent (default), {@code 0.0}
 * = no muffling, {@code 0.5} = roughly half the player's vibrations are dropped.
 * Per-emission randomness keeps the partial case cheap and stateless.
 */
public class MuffleSoundPower extends PowerType<MuffleSoundPower.Config> {

    public record Config(double strength, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("strength", 1.0).forGetter(Config::strength),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * True if the given player should have the in-progress game-event emission
     * suppressed. Honours the {@code strength} knob: full suppression at 1.0,
     * none at 0.0, probabilistic in between.
     */
    public static boolean shouldMuffle(ServerPlayer player) {
        return ActiveOriginService.has(player, MuffleSoundPower.class, cfg -> {
            double strength = cfg.strength();
            if (strength >= 1.0) return true;
            if (strength <= 0.0) return false;
            return player.getRandom().nextDouble() < strength;
        });
    }
}
