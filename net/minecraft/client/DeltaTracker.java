package net.minecraft.client;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface DeltaTracker {
    DeltaTracker ZERO = new DeltaTracker.DefaultValue(0.0F);
    DeltaTracker ONE = new DeltaTracker.DefaultValue(1.0F);

    float getGameTimeDeltaTicks();

    float getGameTimeDeltaPartialTick(boolean p_348668_);

    float getRealtimeDeltaTicks();

    @OnlyIn(Dist.CLIENT)
    public static class DefaultValue implements DeltaTracker {
        private final float value;

        DefaultValue(float p_348484_) {
            this.value = p_348484_;
        }

        @Override
        public float getGameTimeDeltaTicks() {
            return this.value;
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean p_348527_) {
            return this.value;
        }

        @Override
        public float getRealtimeDeltaTicks() {
            return this.value;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Timer implements DeltaTracker {
        private float deltaTicks;
        private float deltaTickResidual;
        private float realtimeDeltaTicks;
        private float pausedDeltaTickResidual;
        private long lastMs;
        private long lastUiMs;
        private final float msPerTick;
        private final FloatUnaryOperator targetMsptProvider;
        private boolean paused;
        private boolean frozen;

        public Timer(float p_348629_, long p_348537_, FloatUnaryOperator p_348550_) {
            this.msPerTick = 1000.0F / p_348629_;
            this.lastUiMs = this.lastMs = p_348537_;
            this.targetMsptProvider = p_348550_;
        }

        public int advanceTime(long p_348462_, boolean p_348492_) {
            this.advanceRealTime(p_348462_);
            return p_348492_ ? this.advanceGameTime(p_348462_) : 0;
        }

        private int advanceGameTime(long p_348532_) {
            this.deltaTicks = (float)(p_348532_ - this.lastMs) / this.targetMsptProvider.apply(this.msPerTick);
            this.lastMs = p_348532_;
            this.deltaTickResidual = this.deltaTickResidual + this.deltaTicks;
            int i = (int)this.deltaTickResidual;
            this.deltaTickResidual -= (float)i;
            return i;
        }

        private void advanceRealTime(long p_348534_) {
            this.realtimeDeltaTicks = (float)(p_348534_ - this.lastUiMs) / this.msPerTick;
            this.lastUiMs = p_348534_;
        }

        public void updatePauseState(boolean p_348523_) {
            if (p_348523_) {
                this.pause();
            } else {
                this.unPause();
            }
        }

        private void pause() {
            if (!this.paused) {
                this.pausedDeltaTickResidual = this.deltaTickResidual;
            }

            this.paused = true;
        }

        private void unPause() {
            if (this.paused) {
                this.deltaTickResidual = this.pausedDeltaTickResidual;
            }

            this.paused = false;
        }

        public void updateFrozenState(boolean p_348502_) {
            this.frozen = p_348502_;
        }

        @Override
        public float getGameTimeDeltaTicks() {
            return this.deltaTicks;
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean p_348526_) {
            if (!p_348526_ && this.frozen) {
                return 1.0F;
            } else {
                return this.paused ? this.pausedDeltaTickResidual : this.deltaTickResidual;
            }
        }

        @Override
        public float getRealtimeDeltaTicks() {
            return this.realtimeDeltaTicks > 7.0F ? 0.5F : this.realtimeDeltaTicks;
        }
    }
}
