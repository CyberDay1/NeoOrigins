package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Fires a slow-moving black gravity orb. On impact it creates a persistent
 * vortex that pulls nearby entities toward its center and deals damage.
 * The caster is immune to their own gravity well.
 */
public class ActiveGravityWellPower extends AbstractActivePower<ActiveGravityWellPower.Config> {

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
            Codec.FLOAT.optionalFieldOf("projectile_speed", 0.6f).forGetter(Config::projectileSpeed),
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

    /** Active gravity wells being simulated server-side. */
    private static final List<GravityWellState> ACTIVE_WELLS = new ArrayList<>();

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // Launch the orb — simulate travel instantly via raycast and place the well
        // at the hit location (avoids needing a custom entity)
        HitResult hit = player.pick(config.maxRange(), 0.0f, false);
        Vec3 wellPos;
        if (hit.getType() == HitResult.Type.MISS) {
            wellPos = eye.add(look.scale(config.maxRange()));
        } else {
            wellPos = hit.getLocation();
        }

        // Register the active well
        ACTIVE_WELLS.add(new GravityWellState(
            player.getUUID(), level, wellPos,
            config.pullRadius(), config.pullStrength(),
            config.damagePerTick(), player.tickCount + config.durationTicks()
        ));

        // Launch sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5f, 0.3f);

        // Impact sound at well location
        level.playSound(null, wellPos.x, wellPos.y, wellPos.z,
            SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);

        return true;
    }

    /** Last server tick we processed, to avoid running multiple times per tick. */
    private static int lastTickProcessed = -1;

    /**
     * Called every server tick to simulate all active gravity wells.
     * Guards against being called multiple times per tick (once per player).
     */
    public static void tickWells() {
        if (ACTIVE_WELLS.isEmpty()) return;
        // Deduplicate: only process once per server tick
        GravityWellState first = ACTIVE_WELLS.get(0);
        if (first.level.getServer() == null) return;
        int serverTick = first.level.getServer().getTickCount();
        if (serverTick == lastTickProcessed) return;
        lastTickProcessed = serverTick;

        Iterator<GravityWellState> it = ACTIVE_WELLS.iterator();
        while (it.hasNext()) {
            GravityWellState well = it.next();
            if (well.level.getServer() == null) {
                it.remove();
                continue;
            }

            int currentTick = well.level.getServer().getTickCount();
            if (currentTick >= well.expireTick) {
                it.remove();
                continue;
            }

            // Pull entities
            AABB box = new AABB(
                well.center.x - well.radius, well.center.y - well.radius, well.center.z - well.radius,
                well.center.x + well.radius, well.center.y + well.radius, well.center.z + well.radius
            );
            var entities = well.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive());

            for (LivingEntity entity : entities) {
                // Caster is immune to their own well
                if (entity.getUUID().equals(well.ownerUuid)) continue;

                Vec3 toCenter = well.center.subtract(entity.position());
                double dist = toCenter.length();
                if (dist < 0.5) dist = 0.5; // prevent division by zero / extreme force

                // Pull strength increases as entity gets closer (inverse relationship)
                double strength = well.pullStrength * (1.0 + (well.radius - dist) / well.radius);
                Vec3 pull = toCenter.normalize().scale(strength);
                entity.setDeltaMovement(entity.getDeltaMovement().add(pull));
                entity.hurtMarked = true;

                // Damage every 10 ticks
                if (currentTick % 10 == 0) {
                    entity.hurt(entity.damageSources().magic(), well.damagePerTick);
                }
            }

            // Visual effects — dark swirling particles
            if (currentTick % 2 == 0) {
                well.level.sendParticles(ParticleTypes.PORTAL,
                    well.center.x, well.center.y, well.center.z,
                    15, well.radius * 0.3, well.radius * 0.3, well.radius * 0.3, 0.5);
                well.level.sendParticles(ParticleTypes.SCULK_SOUL,
                    well.center.x, well.center.y + 0.5, well.center.z,
                    5, 0.3, 0.3, 0.3, 0.02);
                well.level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    well.center.x, well.center.y, well.center.z,
                    10, well.radius * 0.2, well.radius * 0.2, well.radius * 0.2, 0.3);
            }
        }
    }

    /** Clear all wells (e.g., on server shutdown). */
    public static void clearAll() {
        ACTIVE_WELLS.clear();
    }

    /** Clear wells owned by a specific player. */
    public static void clearForPlayer(UUID playerUuid) {
        ACTIVE_WELLS.removeIf(w -> w.ownerUuid.equals(playerUuid));
    }

    private record GravityWellState(
        UUID ownerUuid,
        ServerLevel level,
        Vec3 center,
        double radius,
        float pullStrength,
        float damagePerTick,
        int expireTick
    ) {}
}
