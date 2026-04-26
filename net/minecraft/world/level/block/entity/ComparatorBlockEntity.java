package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public class ComparatorBlockEntity extends BlockEntity {
    private int output;

    public ComparatorBlockEntity(BlockPos p_155386_, BlockState p_155387_) {
        super(BlockEntityType.COMPARATOR, p_155386_, p_155387_);
    }

    @Override
    protected void saveAdditional(CompoundTag p_187493_, HolderLookup.Provider p_323979_) {
        super.saveAdditional(p_187493_, p_323979_);
        p_187493_.putInt("OutputSignal", this.output);
    }

    @Override
    protected void loadAdditional(CompoundTag p_338778_, HolderLookup.Provider p_338355_) {
        super.loadAdditional(p_338778_, p_338355_);
        this.output = p_338778_.getInt("OutputSignal");
    }

    public int getOutputSignal() {
        return this.output;
    }

    public void setOutputSignal(int p_59176_) {
        this.output = p_59176_;
    }
}
