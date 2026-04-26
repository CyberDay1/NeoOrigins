package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult.Error;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;

public class TrialSpawnerBlockEntity extends BlockEntity implements Spawner, TrialSpawner.StateAccessor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private TrialSpawner trialSpawner;

    public TrialSpawnerBlockEntity(BlockPos p_312399_, BlockState p_312206_) {
        super(BlockEntityType.TRIAL_SPAWNER, p_312399_, p_312206_);
        PlayerDetector playerdetector = PlayerDetector.NO_CREATIVE_PLAYERS;
        PlayerDetector.EntitySelector playerdetector$entityselector = PlayerDetector.EntitySelector.SELECT_FROM_LEVEL;
        this.trialSpawner = new TrialSpawner(this, playerdetector, playerdetector$entityselector);
    }

    @Override
    protected void loadAdditional(CompoundTag p_338752_, HolderLookup.Provider p_338872_) {
        super.loadAdditional(p_338752_, p_338872_);
        if (p_338752_.contains("normal_config")) {
            CompoundTag compoundtag = p_338752_.getCompound("normal_config").copy();
            p_338752_.put("ominous_config", compoundtag.merge(p_338752_.getCompound("ominous_config")));
        }

        this.trialSpawner.codec().parse(NbtOps.INSTANCE, p_338752_).resultOrPartial(LOGGER::error).ifPresent(p_311911_ -> this.trialSpawner = p_311911_);
        if (this.level != null) {
            this.markUpdated();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag p_311806_, HolderLookup.Provider p_324342_) {
        super.saveAdditional(p_311806_, p_324342_);
        this.trialSpawner
            .codec()
            .encodeStart(NbtOps.INSTANCE, this.trialSpawner)
            .ifSuccess(p_312175_ -> p_311806_.merge((CompoundTag)p_312175_))
            .ifError(p_338001_ -> LOGGER.warn("Failed to encode TrialSpawner {}", p_338001_.message()));
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p_323524_) {
        return this.trialSpawner.getData().getUpdateTag(this.getBlockState().getValue(TrialSpawnerBlock.STATE));
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    @Override
    public void setEntityId(EntityType<?> p_311807_, RandomSource p_311976_) {
        this.trialSpawner.getData().setEntityId(this.trialSpawner, p_311976_, p_311807_);
        this.setChanged();
    }

    public TrialSpawner getTrialSpawner() {
        return this.trialSpawner;
    }

    @Override
    public TrialSpawnerState getState() {
        return !this.getBlockState().hasProperty(BlockStateProperties.TRIAL_SPAWNER_STATE)
            ? TrialSpawnerState.INACTIVE
            : this.getBlockState().getValue(BlockStateProperties.TRIAL_SPAWNER_STATE);
    }

    @Override
    public void setState(Level p_311786_, TrialSpawnerState p_312721_) {
        this.setChanged();
        p_311786_.setBlockAndUpdate(this.worldPosition, this.getBlockState().setValue(BlockStateProperties.TRIAL_SPAWNER_STATE, p_312721_));
    }

    @Override
    public void markUpdated() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }
}
