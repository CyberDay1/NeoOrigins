package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes fluid surfaces walkable for players with the {@code walk_on_water} /
 * {@code walk_on_lava} capabilities.
 *
 * <p>Vanilla already supports standing on fluids (Striders, via
 * {@code LivingEntity.canStandOnFluid}), but {@code LiquidBlock.getCollisionShape}
 * only hands back a solid surface for <em>source</em> blocks ({@code LEVEL == 0}),
 * and only a half-block {@code STABLE_SHAPE} at that. That means flowing lava —
 * almost everything you get from a poured bucket — is never walkable (you drop
 * straight through), and even on a source block you'd sink knee-deep and bob.
 *
 * <p>This injection mirrors vanilla's "topmost block of the fluid column" surface
 * check but drops the source-only requirement and returns a <em>full</em> block
 * shape, so the player stands cleanly on top of any fluid level — feet clear of
 * the fluid (so normal jumping works) and no sink/bob.
 *
 * <p>Runs first via {@code @At("HEAD")}, short-circuiting vanilla's own
 * {@code canStandOnFluid} path.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockWalkOnFluidMixin {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void neoorigins$walkOnFluidCollision(BlockState state, BlockGetter level, BlockPos pos,
                                                 CollisionContext context,
                                                 CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityCollisionContext ecc)) return;
        Entity entity = ecc.getEntity();
        if (!(entity instanceof Player player)) return;

        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) return;

        // Only the topmost block of a fluid column is a walkable surface — if the
        // block above holds the same fluid, this one is submerged.
        if (level.getFluidState(pos.above()).getType().isSame(fluid.getType())) return;

        // The entity's feet must be at or above the surface block — otherwise it's
        // submerged and should be free to swim up (don't trap it under a solid lid).
        // We deliberately avoid CollisionContext#isAbove here: that gate requires the
        // feet to clear the block's half-height surface, which is never true when you
        // step onto / pour fluid at your own level, so the surface never solidified.
        if (player.getY() < pos.getY() - 1.0E-2) return;

        boolean walk;
        if (player.level().isClientSide()) {
            // Only the local player predicts its own movement; other players'
            // collision contexts must not inherit the local capabilities.
            // Routed through ClientActivePowers (client-only) so this common
            // mixin never names LocalPlayer directly — a direct reference fails
            // the mixin apply on a dedicated server.
            if (!ClientActivePowers.isLocalPlayer(player)) return;
            if (fluid.is(FluidTags.LAVA)) {
                walk = ClientActivePowers.hasCapability("walk_on_lava")
                    && !player.isEyeInFluid(FluidTags.LAVA);
            } else if (fluid.is(FluidTags.WATER)) {
                walk = ClientActivePowers.hasCapability("walk_on_water")
                    && !player.isUnderWater();
            } else {
                return;
            }
        } else if (player instanceof ServerPlayer sp) {
            if (fluid.is(FluidTags.LAVA)) {
                walk = ActiveOriginService.hasCapability(sp, "walk_on_lava")
                    && !player.isEyeInFluid(FluidTags.LAVA);
            } else if (fluid.is(FluidTags.WATER)) {
                walk = ActiveOriginService.hasCapability(sp, "walk_on_water")
                    && !player.isUnderWater();
            } else {
                return;
            }
        } else {
            return;
        }

        if (walk) {
            cir.setReturnValue(Shapes.block());
        }
    }
}
