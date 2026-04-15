package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Fires a slow-moving black gravity orb. On impact it creates a persistent
 * vortex that pulls nearby entities toward its center and deals damage.
 * The caster is immune to their own gravity well.
 *
 * <p>Two phases: traveling (visible dark orb) then active (pull vortex).
 */
public class ActiveGravityWellPower extends AbstractActivePower<ActiveGravityWellPower.Config> {

    private static final DustParticleOptions BLACK_DUST = new DustParticleOptions(new Vector3f(0.05f, 0.0f, 0.08f), 2.0f);
    private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(new Vector3f(0.3f, 0.0f, 0.4f), 1.2f);

    public record Config(
        float projectileSpeed,
        double maxRange,
        double pullRadius,
        float pullStrength,
        float damagePerTick,
        int durationTicks,
        int cooldownTicks,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("projectile_speed", 1.2f).forGetter(Config::projectileSpeed),
            Codec.DOUBLE.optionalFieldOf("max_range", 32.0).forGetter(Config::maxRange),
            Codec.DOUBLE.optionalFieldOf("pull_radius", 8.0).forGetter(Config::pullRadius),
            Codec.FLOAT.optionalFieldOf("pull_strength", 0.35f).forGetter(Config::pullStrength),
            Codec.FLOAT.optionalFieldOf("damage_per_tick", 0.5f).forGetter(Config::damagePerTick),
            Codec.INT.optionalFieldOf("duration_ticks", 80).forGetter(Config::durationTicks),
            Codec.INT.optionalFieldOf("cooldown_ticks", 200).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    private static final List<GravityWellState> ACTIVE_WELLS = new ArrayList<>();

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        HitResult hit = player.pick(config.maxRange(), 0.0f, false);
        Vec3 target = hit.getType() == HitResult.Type.MISS
            ? eye.add(look.scale(config.maxRange()))
            : hit.getLocation();

        ACTIVE_WELLS.add(new GravityWellState(
            player.getUUID(), level,
            eye.add(look.scale(1.5)), target,
            look.normalize().scale(config.projectileSpeed()),
            config.pullRadius(), config.pullStrength(),
            config.damagePerTick(), config.durationTicks()
        ));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5f, 0.3f);

        return true;
    }

    private static int lastTickProcessed = -1;

    public static void tickWells() {
        if (ACTIVE_WELLS.isEmpty()) return;
        GravityWellState first = ACTIVE_WELLS.get(0);
        if (first.level.getServer() == null) return;
        int serverTick = first.level.getServer().getTickCount();
        if (serverTick == lastTickProcessed) return;
        lastTickProcessed = serverTick;

        Iterator<GravityWellState> it = ACTIVE_WELLS.iterator();
        while (it.hasNext()) {
            GravityWellState well = it.next();
            if (well.level.getServer() == null) { it.remove(); continue; }

            if (well.traveling) {
                tickTraveling(well);
            } else {
                if (well.level.getServer().getTickCount() >= well.expireTick) { it.remove(); continue; }
                tickActive(well);
            }
        }
    }

    private static void tickTraveling(GravityWellState well) {
        well.currentPos = well.currentPos.add(well.velocity);

        double distToTarget = well.currentPos.distanceToSqr(well.target);
        double speed = well.velocity.length();
        boolean arrived = distToTarget <= speed * speed * 1.5
            || well.currentPos.distanceToSqr(well.startPos) > well.target.distanceToSqr(well.startPos);

        if (arrived) {
            well.currentPos = well.target;
            well.traveling = false;
            well.expireTick = well.level.getServer().getTickCount() + well.durationTicks;

            well.level.playSound(null, well.target.x, well.target.y, well.target.z,
                SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
            well.level.sendParticles(BLACK_DUST,
                well.target.x, well.target.y, well.target.z, 30, 0.5, 0.5, 0.5, 0.02);
            well.level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                well.target.x, well.target.y, well.target.z, 20, 0.3, 0.3, 0.3, 0.5);
            return;
        }

        // Render traveling orb — black core + purple halo + portal trail
        well.level.sendParticles(BLACK_DUST,
            well.currentPos.x, well.currentPos.y, well.currentPos.z, 8, 0.1, 0.1, 0.1, 0.0);
        well.level.sendParticles(PURPLE_DUST,
            well.currentPos.x, well.currentPos.y, well.currentPos.z, 4, 0.2, 0.2, 0.2, 0.0);
        Vec3 trail = well.currentPos.subtract(well.velocity.scale(0.5));
        well.level.sendParticles(ParticleTypes.PORTAL,
            trail.x, trail.y, trail.z, 3, 0.05, 0.05, 0.05, 0.1);
        well.level.sendParticles(ParticleTypes.PORTAL,
            trail.x, trail.y, trail.z, 2, 0.05, 0.05, 0.05, 0.1);
    }

    private static void tickActive(GravityWellState well) {
        int currentTick = well.level.getServer().getTickCount();
        Vec3 center = well.currentPos;

        AABB box = new AABB(
            center.x - well.radius, center.y - well.radius, center.z - well.radius,
            center.x + well.radius, center.y + well.radius, center.z + well.radius
        );
        for (LivingEntity entity : well.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive())) {
            if (entity.getUUID().equals(well.ownerUuid)) continue;

            Vec3 toCenter = center.subtract(entity.position());
            double dist = Math.max(0.5, toCenter.length());
            double strength = well.pullStrength * (1.0 + (well.radius - dist) / well.radius);
            entity.setDeltaMovement(entity.getDeltaMovement().add(toCenter.normalize().scale(strength)));
            entity.hurtMarked = true;

            if (currentTick % 10 == 0) {
                entity.hurt(entity.damageSources().magic(), well.damagePerTick);
            }
        }

        if (currentTick % 2 == 0) {
            well.level.sendParticles(BLACK_DUST,
                center.x, center.y, center.z, 10, well.radius * 0.3, well.radius * 0.3, well.radius * 0.3, 0.02);
            well.level.sendParticles(PURPLE_DUST,
                center.x, center.y, center.z, 5, well.radius * 0.2, well.radius * 0.2, well.radius * 0.2, 0.01);
            well.level.sendParticles(ParticleTypes.PORTAL,
                center.x, center.y, center.z, 15, well.radius * 0.3, well.radius * 0.3, well.radius * 0.3, 0.5);
            well.level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                center.x, center.y, center.z, 8, well.radius * 0.2, well.radius * 0.2, well.radius * 0.2, 0.3);
        }
        if (currentTick % 20 == 0) {
            well.level.playSound(null, center.x, center.y, center.z,
                SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 0.4f, 0.2f);
        }
    }

    public static void clearAll() { ACTIVE_WELLS.clear(); }
    public static void clearForPlayer(UUID playerUuid) {
        ACTIVE_WELLS.removeIf(w -> w.ownerUuid.equals(playerUuid));
    }

    private static class GravityWellState {
        final UUID ownerUuid;
        final ServerLevel level;
        final Vec3 startPos;
        final Vec3 target;
        final Vec3 velocity;
        final double radius;
        final float pullStrength;
        final float damagePerTick;
        final int durationTicks;
        Vec3 currentPos;
        boolean traveling = true;
        int expireTick = -1;

        GravityWellState(UUID ownerUuid, ServerLevel level, Vec3 startPos, Vec3 target, Vec3 velocity,
                         double radius, float pullStrength, float damagePerTick, int durationTicks) {
            this.ownerUuid = ownerUuid;
            this.level = level;
            this.startPos = startPos;
            this.target = target;
            this.velocity = velocity;
            this.radius = radius;
            this.pullStrength = pullStrength;
            this.damagePerTick = damagePerTick;
            this.durationTicks = durationTicks;
            this.currentPos = startPos;
        }
    }
}
