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
 * <p>Both the lift and the preserved horizontal speed are derived from per-tick
 * <b>position</b> deltas. A remote {@code ServerPlayer}'s {@code deltaMovement}
 * is never written by client movement on any axis, so reading it here produced
 * a zero launch (#102, vertical) and then destroyed the player's running speed
 * (#133, horizontal) — the velocity packet {@code hurtMarked} triggers replaces
 * motion rather than adding to it.
 *
 * <p>Pair with {@code neoorigins:no_fall_damage} so the impact driving the
 * bounce doesn't also hurt. Sneaking suppresses the bounce, matching slime
 * blocks.
 *
 * <pre>{@code
 * { "type": "neoorigins:bounce_on_land", "restitution": 0.8 }
 * }</pre>
 */
// hub: neoorigins/bounce-momentum.md
public class BounceOnLandPower extends PowerType<BounceOnLandPower.Config> {

    /** Previous-tick absolute position, per player — source of the deltas. */
    private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

    /** Previous-tick position delta, per player. */
    private static final Map<UUID, Vec3> LAST_DELTA = new ConcurrentHashMap<>();

    /**
     * Previous-tick ground state, per player — so the bounce fires only on the
     * airborne→ground transition, not on every tick spent standing still.
     */
    private static final Map<UUID, Boolean> LAST_ON_GROUND = new ConcurrentHashMap<>();

    /**
     * Impact floor for a bounce, in blocks/tick of per-tick position delta.
     * Sits in the window (0.5969, 0.6650] measured for "a 4-block fall bounces,
     * a 3-block fall and a plain jump do not" (#133).
     */
    static final double DEFAULT_MIN_VELOCITY = 0.63;

    public record Config(
        double restitution,
        double minVelocity,
        double maxVelocity,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("restitution", 0.8).forGetter(Config::restitution),
            Codec.DOUBLE.optionalFieldOf("min_velocity", DEFAULT_MIN_VELOCITY).forGetter(Config::minVelocity),
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

        Vec3 pos = player.position();
        Vec3 lastPos = LAST_POS.get(id);
        Vec3 deltaNow = lastPos != null ? pos.subtract(lastPos) : Vec3.ZERO;
        Vec3 lastDelta = LAST_DELTA.getOrDefault(id, Vec3.ZERO);

        if (onGroundNow && !wasOnGround && !player.isShiftKeyDown()) {
            Vec3 launch = launchVelocity(lastDelta, deltaNow, config);
            if (launch != null) {
                player.setDeltaMovement(launch);
                // hurtMarked makes the server send a velocity packet to the
                // client (the knockback sync path), so the launch is applied
                // client-side instead of being overridden by client movement.
                player.hurtMarked = true;
                player.fallDistance = 0.0F;
                player.setOnGround(false);
            }
        }

        LAST_POS.put(id, pos);
        LAST_DELTA.put(id, deltaNow);
        LAST_ON_GROUND.put(id, onGroundNow);
    }

    /**
     * The velocity to publish on a landing tick, or {@code null} for no bounce.
     *
     * <p>Vertical prefers {@code lastDelta} — the landing tick's own Y step is
     * truncated at ground contact, so the previous tick's full in-air step is
     * the genuine impact speed. Horizontal takes {@code nowDelta}, which a
     * vertical collision does not truncate, so the player keeps the speed they
     * actually landed with.
     */
    static Vec3 launchVelocity(Vec3 lastDelta, Vec3 nowDelta, Config config) {
        double impact = Math.max(-lastDelta.y, -nowDelta.y);
        if (impact < config.minVelocity()) return null;
        double bounce = Math.min(impact * config.restitution(), config.maxVelocity());
        return new Vec3(nowDelta.x, bounce, nowDelta.z);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        LAST_POS.remove(player.getUUID());
        LAST_DELTA.remove(player.getUUID());
        LAST_ON_GROUND.remove(player.getUUID());
    }
}
