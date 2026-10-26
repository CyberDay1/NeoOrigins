package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.service.LootTableDrops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * Records which captured death drops came from the loot table, so drop-multiplying
 * powers (Scavenging) can copy those and leave saddles, armor and inventories alone.
 * See {@link LootTableDrops}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLootTableDropsMixin {

    @Inject(
        method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            shift = At.Shift.AFTER))
    private void neoorigins$markLootTableDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Collection<ItemEntity> captured = self.captureDrops();
        if (captured != null) LootTableDrops.mark(self, captured);
    }

    @Inject(
        method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
        at = @At("RETURN"))
    private void neoorigins$clearLootTableDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        LootTableDrops.clear((LivingEntity) (Object) this);
    }
}
