package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code LivingEntity.onClimbable()} return {@code true} when the entity has
 * the {@code "wall_climb"} capability tag active AND is pressed against a wall.
 *
 * <p>Enabling vanilla climbing via {@code onClimbable} causes both the server and
 * the client to apply vanilla ladder/vine physics — including jump-to-ascend and
 * slow-descent — in lockstep. This removes the need for server-side delta-movement
 * pushes that caused rubber-banding in the 1.x WallClimbingPower implementation.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void neoorigins$wallClimbCapability(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // cobweb_affinity with climb: a web is a ladder, on the ground or off it.
        if (PowerCapabilities.hasActive(self, com.cyberday1.neoorigins.power.builtin.CobwebAffinityPower.WEB_CLIMB)
                && overlapsCobweb(self)) {
            cir.setReturnValue(true);
            return;
        }
        if (self.onGround()) return;
        if (!PowerCapabilities.hasActive(self, "wall_climb")) return;
        // Accept both full and minor horizontal collision — when sneaking,
        // movement speed drops and may not trigger a full collision, but
        // minorHorizontalCollision still indicates wall contact. Without
        // this, sneaking on a wall loses the climbing state and the player
        // falls instead of holding position.
        if (!self.horizontalCollision && !self.minorHorizontalCollision) return;
        cir.setReturnValue(true);
    }

    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> COBWEBS =
        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("neoorigins", "cobwebs"));

    /** True when any block the entity's hitbox overlaps is in {@code #neoorigins:cobwebs}. */
    private static boolean overlapsCobweb(LivingEntity self) {
        var box = self.getBoundingBox().deflate(1.0E-3);
        var min = net.minecraft.core.BlockPos.containing(box.minX, box.minY, box.minZ);
        var max = net.minecraft.core.BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(min, max)) {
            if (self.level().getBlockState(pos).is(COBWEBS)) return true;
        }
        return false;
    }
}
