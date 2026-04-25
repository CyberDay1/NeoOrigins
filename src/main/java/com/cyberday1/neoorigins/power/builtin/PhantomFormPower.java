package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Spectator-lite toggle: invisible, gravity-free, can fly, ignores block
 * collision, takes no fall damage. Matches "Phantom Form" semantics in the
 * original Origins mod — the player ghosts through walls, drifts in any
 * direction with jump/sneak, and lands without taking impact damage when
 * revoked mid-flight.
 *
 * <p>The movement abilities ({@code mayfly}, {@code flying}) are toggled via
 * {@code Player.getAbilities()} and pushed to the client via
 * {@code onUpdateAbilities()} on each tick — this avoids a sync race where a
 * survival player rejoining with the power on lands without the flight flag
 * set client-side.
 */
public class PhantomFormPower extends AbstractTogglePower<PhantomFormPower.Config> {

    public record Config(
        boolean invisibility,
        boolean noGravity,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("invisibility", true).forGetter(Config::invisibility),
            Codec.BOOL.optionalFieldOf("no_gravity", true).forGetter(Config::noGravity),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        if (config.invisibility()) {
            var existing = player.getEffect(MobEffects.INVISIBILITY);
            if (existing == null || existing.getDuration() < 210) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0, true, false));
            }
        }
        if (config.noGravity()) {
            player.setNoGravity(true);
        }
        // Flight — mayfly grants the capability, flying forces it on. Jump goes
        // up, shift (descend) goes down. Setting both each tick is cheap and
        // avoids drift if another mod clears them.
        var abilities = player.getAbilities();
        boolean abilitiesChanged = false;
        if (!abilities.mayfly)  { abilities.mayfly  = true;  abilitiesChanged = true; }
        if (!abilities.flying)  { abilities.flying  = true;  abilitiesChanged = true; }
        if (abilitiesChanged) player.onUpdateAbilities();

        // Noclip — player passes through solid blocks without collision.
        player.noPhysics = true;
        // Fall damage — reset each tick so the player lands safely when the
        // power is toggled off mid-fall (or the server crashes with it on).
        player.fallDistance = 0.0F;
    }

    @Override
    protected void removeEffect(ServerPlayer player, Config config) {
        if (config.invisibility()) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        player.setNoGravity(false);
        player.noPhysics = false;
        var abilities = player.getAbilities();
        boolean wasFlying = abilities.flying;
        // Restore survival defaults — do NOT set mayfly/flying false if the
        // player is in creative (would lock them out of their own mode).
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        if (wasFlying) player.onUpdateAbilities();
        player.fallDistance = 0.0F;
    }
}
