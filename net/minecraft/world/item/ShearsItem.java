package net.minecraft.world.item;

import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class ShearsItem extends Item {
    public ShearsItem(Item.Properties p_43074_) {
        super(p_43074_);
    }

    public static Tool createToolProperties() {
        return new Tool(
            List.of(
                Tool.Rule.minesAndDrops(List.of(Blocks.COBWEB), 15.0F),
                Tool.Rule.overrideSpeed(BlockTags.LEAVES, 15.0F),
                Tool.Rule.overrideSpeed(BlockTags.WOOL, 5.0F),
                Tool.Rule.overrideSpeed(List.of(Blocks.VINE, Blocks.GLOW_LICHEN), 2.0F)
            ),
            1.0F,
            1
        );
    }

    @Override
    public boolean mineBlock(ItemStack p_43078_, Level p_43079_, BlockState p_43080_, BlockPos p_43081_, LivingEntity p_43082_) {
        if (!p_43079_.isClientSide && !p_43080_.is(BlockTags.FIRE)) {
            p_43078_.hurtAndBreak(1, p_43082_, EquipmentSlot.MAINHAND);
        }

        return p_43080_.is(BlockTags.LEAVES)
            || p_43080_.is(Blocks.COBWEB)
            || p_43080_.is(Blocks.SHORT_GRASS)
            || p_43080_.is(Blocks.FERN)
            || p_43080_.is(Blocks.DEAD_BUSH)
            || p_43080_.is(Blocks.HANGING_ROOTS)
            || p_43080_.is(Blocks.VINE)
            || p_43080_.is(Blocks.TRIPWIRE)
            || p_43080_.is(BlockTags.WOOL);
    }

    /**
     * Neo: Migrate shear behavior into {@link ShearsItem#interactLivingEntity} to call into IShearable instead of relying on {@link net.minecraft.world.entity.Mob#mobInteract}
     * <p>
     * To preserve vanilla behavior, this method retains the original flow shared by the various mobInteract overrides.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, net.minecraft.world.InteractionHand hand) {
        if (entity instanceof net.neoforged.neoforge.common.IShearable target) {
            BlockPos pos = entity.blockPosition();
            boolean isClient = entity.level().isClientSide();
            // Check isShearable on both sides (mirrors vanilla readyForShearing())
            if (target.isShearable(player, stack, entity.level(), pos)) {
                // Call onSheared on both sides (mirrors vanilla shear())
                List<ItemStack> drops = target.onSheared(player, stack, entity.level(), pos);
                // Spawn drops on the server side using spawnShearedDrop to retain vanilla mob-specific behavior
                if (!isClient) {
                    for(ItemStack drop : drops) {
                        target.spawnShearedDrop(entity.level(), pos, drop);
                    }
                }
                // Call GameEvent.SHEAR on both sides
                entity.gameEvent(GameEvent.SHEAR, player);
                // Damage the shear item stack by 1 on the server side
                if (!isClient) {
                    stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                }
                // Return sided success if the entity was shearable
                return InteractionResult.sidedSuccess(isClient);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility itemAbility) {
        return net.neoforged.neoforge.common.ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(itemAbility);
    }

    @Override
    public InteractionResult useOn(UseOnContext p_186371_) {
        Level level = p_186371_.getLevel();
        BlockPos blockpos = p_186371_.getClickedPos();
        BlockState blockstate = level.getBlockState(blockpos);
        BlockState blockstate1 = blockstate.getToolModifiedState(p_186371_, net.neoforged.neoforge.common.ItemAbilities.SHEARS_TRIM, false);
        if (blockstate1 != null) {
            Player player = p_186371_.getPlayer();
            ItemStack itemstack = p_186371_.getItemInHand();
            if (player instanceof ServerPlayer) {
                CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger((ServerPlayer)player, blockpos, itemstack);
            }

            level.setBlockAndUpdate(blockpos, blockstate1);
            level.gameEvent(GameEvent.BLOCK_CHANGE, blockpos, GameEvent.Context.of(p_186371_.getPlayer(), blockstate1));
            if (player != null) {
                itemstack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(p_186371_.getHand()));
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(p_186371_);
    }
}
