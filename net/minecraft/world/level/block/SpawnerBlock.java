package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnerBlock extends BaseEntityBlock {
    public static final MapCodec<SpawnerBlock> CODEC = simpleCodec(SpawnerBlock::new);

    @Override
    public MapCodec<SpawnerBlock> codec() {
        return CODEC;
    }

    public SpawnerBlock(BlockBehaviour.Properties p_56781_) {
        super(p_56781_);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p_154687_, BlockState p_154688_) {
        return new SpawnerBlockEntity(p_154687_, p_154688_);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_154683_, BlockState p_154684_, BlockEntityType<T> p_154685_) {
        return createTickerHelper(
            p_154685_, BlockEntityType.MOB_SPAWNER, p_154683_.isClientSide ? SpawnerBlockEntity::clientTick : SpawnerBlockEntity::serverTick
        );
    }

    @Override
    protected void spawnAfterBreak(BlockState p_222477_, ServerLevel p_222478_, BlockPos p_222479_, ItemStack p_222480_, boolean p_222481_) {
        super.spawnAfterBreak(p_222477_, p_222478_, p_222479_, p_222480_, p_222481_);
    }

    // Neo: Patch-in override for getExpDrop. Also fixes MC-273642 (Spawner XP drops bypass enchantments)
    // Original vanilla logic passes 15 + p_222478_.random.nextInt(15) + p_222478_.random.nextInt(15) to popExperience, bypassing enchantments
    @Override
    public int getExpDrop(BlockState state, net.minecraft.world.level.LevelAccessor level, BlockPos pos,
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.block.entity.BlockEntity blockEntity,
            @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity breaker, ItemStack tool) {
        return 15 + level.getRandom().nextInt(15) + level.getRandom().nextInt(15);
    }

    @Override
    protected RenderShape getRenderShape(BlockState p_56794_) {
        return RenderShape.MODEL;
    }

    @Override
    public void appendHoverText(ItemStack p_255714_, Item.TooltipContext p_339639_, List<Component> p_255708_, TooltipFlag p_255667_) {
        super.appendHoverText(p_255714_, p_339639_, p_255708_, p_255667_);
        Spawner.appendHoverText(p_255714_, p_255708_, "SpawnData");
    }
}
