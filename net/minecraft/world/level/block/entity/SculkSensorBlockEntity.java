package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.slf4j.Logger;

public class SculkSensorBlockEntity extends BlockEntity implements GameEventListener.Provider<VibrationSystem.Listener>, VibrationSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private VibrationSystem.Data vibrationData;
    private final VibrationSystem.Listener vibrationListener;
    private final VibrationSystem.User vibrationUser = this.createVibrationUser();
    private int lastVibrationFrequency;

    protected SculkSensorBlockEntity(BlockEntityType<?> p_277405_, BlockPos p_277502_, BlockState p_277699_) {
        super(p_277405_, p_277502_, p_277699_);
        this.vibrationData = new VibrationSystem.Data();
        this.vibrationListener = new VibrationSystem.Listener(this);
    }

    public SculkSensorBlockEntity(BlockPos p_155635_, BlockState p_155636_) {
        this(BlockEntityType.SCULK_SENSOR, p_155635_, p_155636_);
    }

    public VibrationSystem.User createVibrationUser() {
        return new SculkSensorBlockEntity.VibrationUser(this.getBlockPos());
    }

    @Override
    protected void loadAdditional(CompoundTag p_338719_, HolderLookup.Provider p_338792_) {
        super.loadAdditional(p_338719_, p_338792_);
        this.lastVibrationFrequency = p_338719_.getInt("last_vibration_frequency");
        RegistryOps<Tag> registryops = p_338792_.createSerializationContext(NbtOps.INSTANCE);
        if (p_338719_.contains("listener", 10)) {
            VibrationSystem.Data.CODEC
                .parse(registryops, p_338719_.getCompound("listener"))
                .resultOrPartial(p_351973_ -> LOGGER.error("Failed to parse vibration listener for Sculk Sensor: '{}'", p_351973_))
                .ifPresent(p_281146_ -> this.vibrationData = p_281146_);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag p_187511_, HolderLookup.Provider p_323787_) {
        super.saveAdditional(p_187511_, p_323787_);
        p_187511_.putInt("last_vibration_frequency", this.lastVibrationFrequency);
        RegistryOps<Tag> registryops = p_323787_.createSerializationContext(NbtOps.INSTANCE);
        VibrationSystem.Data.CODEC
            .encodeStart(registryops, this.vibrationData)
            .resultOrPartial(p_351974_ -> LOGGER.error("Failed to encode vibration listener for Sculk Sensor: '{}'", p_351974_))
            .ifPresent(p_222820_ -> p_187511_.put("listener", p_222820_));
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    public int getLastVibrationFrequency() {
        return this.lastVibrationFrequency;
    }

    public void setLastVibrationFrequency(int p_222801_) {
        this.lastVibrationFrequency = p_222801_;
    }

    public VibrationSystem.Listener getListener() {
        return this.vibrationListener;
    }

    protected class VibrationUser implements VibrationSystem.User {
        public static final int LISTENER_RANGE = 8;
        protected final BlockPos blockPos;
        private final PositionSource positionSource;

        public VibrationUser(BlockPos p_283482_) {
            this.blockPos = p_283482_;
            this.positionSource = new BlockPositionSource(p_283482_);
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public boolean canTriggerAvoidVibration() {
            return true;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel p_282127_, BlockPos p_283268_, Holder<GameEvent> p_316115_, @Nullable GameEvent.Context p_282856_) {
            return !p_283268_.equals(this.blockPos) || !p_316115_.is(GameEvent.BLOCK_DESTROY) && !p_316115_.is(GameEvent.BLOCK_PLACE)
                ? SculkSensorBlock.canActivate(SculkSensorBlockEntity.this.getBlockState())
                : false;
        }

        @Override
        public void onReceiveVibration(
            ServerLevel p_282851_, BlockPos p_281608_, Holder<GameEvent> p_316423_, @Nullable Entity p_282123_, @Nullable Entity p_283090_, float p_283130_
        ) {
            BlockState blockstate = SculkSensorBlockEntity.this.getBlockState();
            if (SculkSensorBlock.canActivate(blockstate)) {
                SculkSensorBlockEntity.this.setLastVibrationFrequency(VibrationSystem.getGameEventFrequency(p_316423_));
                int i = VibrationSystem.getRedstoneStrengthForDistance(p_283130_, this.getListenerRadius());
                if (blockstate.getBlock() instanceof SculkSensorBlock sculksensorblock) {
                    sculksensorblock.activate(p_282123_, p_282851_, this.blockPos, blockstate, i, SculkSensorBlockEntity.this.getLastVibrationFrequency());
                }
            }
        }

        @Override
        public void onDataChanged() {
            SculkSensorBlockEntity.this.setChanged();
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() {
            return true;
        }
    }
}
