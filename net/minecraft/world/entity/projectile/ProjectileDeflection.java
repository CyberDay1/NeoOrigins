package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface ProjectileDeflection {
    ProjectileDeflection NONE = (p_320379_, p_320626_, p_320122_) -> {
    };
    ProjectileDeflection REVERSE = (p_352838_, p_352839_, p_352840_) -> {
        float f = 170.0F + p_352840_.nextFloat() * 20.0F;
        p_352838_.setDeltaMovement(p_352838_.getDeltaMovement().scale(-0.5));
        p_352838_.setYRot(p_352838_.getYRot() + f);
        p_352838_.yRotO += f;
        p_352838_.hasImpulse = true;
    };
    ProjectileDeflection AIM_DEFLECT = (p_350137_, p_350138_, p_350139_) -> {
        if (p_350138_ != null) {
            Vec3 vec3 = p_350138_.getLookAngle().normalize();
            p_350137_.setDeltaMovement(vec3);
            p_350137_.hasImpulse = true;
        }
    };
    ProjectileDeflection MOMENTUM_DEFLECT = (p_350131_, p_350132_, p_350133_) -> {
        if (p_350132_ != null) {
            Vec3 vec3 = p_350132_.getDeltaMovement().normalize();
            p_350131_.setDeltaMovement(vec3);
            p_350131_.hasImpulse = true;
        }
    };

    void deflect(Projectile p_320311_, @Nullable Entity p_320130_, RandomSource p_320125_);
}
