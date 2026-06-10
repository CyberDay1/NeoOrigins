package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounce on landing — reflects the player's downward impact velocity back
 * upward, mimicking a slime block, so a slime-morphed player springs off the
 * ground after a fall.
 *
 * <p>Server-side: each tick we remember the player's vertical velocity. On the
 * tick the player transitions from airborne to on-ground, the velocity captured
 * the previous tick (the genuine impact speed — by the landing tick the
 * player's own {@code deltaMovement.y} has already been collision-clamped to
 * ~0) is reflected upward, scaled by {@code restitution}. The new velocity is
 * pushed to the client via {@code hurtMarked}, the same path vanilla uses for
 * server-applied knockback, so the bounce is applied client-side and feels
 * responsive.
 *
 * <p>Impacts below {@code min_velocity} are ignored so walking and small steps
 * don't produce micro-bounces, and the launch is capped at {@code max_velocity}
 * so terminal-velocity falls don't fling the player absurdly high. With
 * {@code restitution < 1} each successive bounce is smaller, so the player
 * naturally settles. Sneaking suppresses the bounce — matching slime-block
 * behavior and giving players a deliberate way to stop bouncing.
 *
 * <p>Pair with {@code neoorigins:no_fall_damage} so the impact driving the
 * bounce doesn't also hurt.
 *
 * <pre>{@code
 * { "type": "neoorigins:bounce_on_land", "restitution": 0.8 }
 * }</pre>
 */
public class BounceOnLandPower extends PowerType<BounceOnLandPower.Config> {

    /**
     * Previous-tick vertical velocity, per player. On the landing tick the
     * player's own {@code deltaMovement.y} is already clamped to ~0 by the
     * ground collision, so the real impact speed is the value we recorded the
     * tick before.
     */
    private static final Map<UUID, Double> LAST_Y = new ConcurrentHashMap<>();

    /**
     * Previous-tick ground state, per player — so the bounce fires only on the
     * airborne→ground transition, not on every tick spent standing still.
     */
    private static final Map<UUID, Boolean> LAST_ON_GROUND = new ConcurrentHashMap<>();

    public record Config(
        double restitution,
        double minVelocity,
        double maxVelocity,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("restitution", 0.8).forGetter(Config::restitution),
            Codec.DOUBLE.optionalFieldOf("min_velocity", 0.3).forGetter(Config::minVelocity),
            Codec.DOUBLE.optionalFieldOf("max_velocity", 1.6).forGetter(Config::maxVelocity),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        UUID id = player.getUUID();
        boolean onGroundNow = player.onGround();
        boolean wasOnGround = LAST_ON_GROUND.getOrDefault(id, true);
        double lastY = LAST_Y.getOrDefault(id, 0.0);

        if (onGroundNow && !wasOnGround && !player.isShiftKeyDown()) {
            double impact = -lastY; // positive = downward speed at impact
            if (impact >= config.minVelocity()) {
                double bounce = Math.min(impact * config.restitution(), config.maxVelocity());
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x, bounce, v.z);
                // hurtMarked makes the server send a velocity packet to the
                // client (the knockback sync path), so the launch is applied
                // client-side instead of being overridden by client movement.
                player.hurtMarked = true;
                player.fallDistance = 0.0F;
                player.setOnGround(false);
            }
        }

        LAST_Y.put(id, player.getDeltaMovement().y);
        LAST_ON_GROUND.put(id, onGroundNow);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        LAST_Y.remove(player.getUUID());
        LAST_ON_GROUND.remove(player.getUUID());
    }
}
