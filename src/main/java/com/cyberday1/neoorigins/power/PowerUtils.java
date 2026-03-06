package com.cyberday1.neoorigins.power;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class PowerUtils {

    private PowerUtils() {}

    /**
     * Finds the first living entity within range that intersects the player's look ray.
     * Excludes the player themselves.
     */
    public static Optional<LivingEntity> findEntityInLookDirection(ServerPlayer player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookDir.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(lookDir.scale(range)).inflate(1.5);

        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return Optional.empty();
        List<LivingEntity> candidates = sl.getEntitiesOfClass(
            LivingEntity.class, searchBox, e -> e != player && e.isAlive());

        LivingEntity found = null;
        double closestDist = Double.MAX_VALUE;

        for (LivingEntity e : candidates) {
            Optional<Vec3> intersect = e.getBoundingBox().inflate(0.3).clip(eyePos, endPos);
            if (intersect.isPresent()) {
                double dist = eyePos.distanceTo(intersect.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    found = e;
                }
            }
        }
        return Optional.ofNullable(found);
    }
}
