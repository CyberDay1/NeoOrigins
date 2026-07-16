package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Route B {@code origins:prevent_block_selection} (narrow cobweb mapping):
 * while the {@code "cobweb_selection_passthrough"} capability is active, blocks
 * in the {@code origins:cobwebs} tag return an EMPTY outline shape for that
 * player's collision context, so the crosshair raytrace passes straight through
 * them — the Broodmother "punch through your own webs" behaviour. Mirrors
 * Apoli's outline-shape mixin for PreventBlockSelectionPower.
 *
 * <p>getShape is hot AND runs during block-shape caching at class-init, before
 * tags are bound. So the player-gating checks run first: they bail immediately
 * for the player-less contexts of static init and the ordinary render/collision
 * path, and the tag lookup only runs for a player who has the capability active.
 * Querying {@code BlockState.is(tag)} first would throw "Tags not bound" during
 * Blocks static init on 26.x, cascading into a fatal Blocks NoClassDefFoundError.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseCobwebShapeMixin {

    @Unique
    private static final TagKey<Block> NEOORIGINS$COBWEBS =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("origins", "cobwebs"));

    @Shadow protected abstract BlockState asState();

    @Inject(
        method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("HEAD"), cancellable = true)
    private void neoorigins$cobwebSelectionPassthrough(BlockGetter level, BlockPos pos,
                                                       CollisionContext context,
                                                       CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityCollisionContext ecc)) return;
        if (!(ecc.getEntity() instanceof Player player)) return;
        if (!PowerCapabilities.hasActive(player, "cobweb_selection_passthrough")) return;
        if (!this.asState().is(NEOORIGINS$COBWEBS)) return;
        cir.setReturnValue(Shapes.empty());
    }
}
