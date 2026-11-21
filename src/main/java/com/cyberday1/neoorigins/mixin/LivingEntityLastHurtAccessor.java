package com.cyberday1.neoorigins.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the protected {@code lastHurt} that vanilla's damage-cooldown gate compares against. */
@Mixin(LivingEntity.class)
public interface LivingEntityLastHurtAccessor {

    @Accessor("lastHurt")
    float neoorigins$getLastHurt();
}
