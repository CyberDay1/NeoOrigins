package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

public interface ChunkTrackingView {
    ChunkTrackingView EMPTY = new ChunkTrackingView() {
        @Override
        public boolean contains(int p_294225_, int p_294897_, boolean p_294644_) {
            return false;
        }

        @Override
        public void forEach(Consumer<ChunkPos> p_295201_) {
        }
    };

    static ChunkTrackingView of(ChunkPos p_296254_, int p_295979_) {
        return new ChunkTrackingView.Positioned(p_296254_, p_295979_);
    }

    static void difference(ChunkTrackingView p_294391_, ChunkTrackingView p_294272_, Consumer<ChunkPos> p_295078_, Consumer<ChunkPos> p_294115_) {
        if (!p_294391_.equals(p_294272_)) {
            if (p_294391_ instanceof ChunkTrackingView.Positioned chunktrackingview$positioned
                && p_294272_ instanceof ChunkTrackingView.Positioned chunktrackingview$positioned1
                && chunktrackingview$positioned.squareIntersects(chunktrackingview$positioned1)) {
                int i = Math.min(chunktrackingview$positioned.minX(), chunktrackingview$positioned1.minX());
                int j = Math.min(chunktrackingview$positioned.minZ(), chunktrackingview$positioned1.minZ());
                int k = Math.max(chunktrackingview$positioned.maxX(), chunktrackingview$positioned1.maxX());
                int l = Math.max(chunktrackingview$positioned.maxZ(), chunktrackingview$positioned1.maxZ());

                for (int i1 = i; i1 <= k; i1++) {
                    for (int j1 = j; j1 <= l; j1++) {
                        boolean flag = chunktrackingview$positioned.contains(i1, j1);
                        boolean flag1 = chunktrackingview$positioned1.contains(i1, j1);
                        if (flag != flag1) {
                            if (flag1) {
                                p_295078_.accept(new ChunkPos(i1, j1));
                            } else {
                                p_294115_.accept(new ChunkPos(i1, j1));
                            }
                        }
                    }
                }

                return;
            }

            p_294391_.forEach(p_294115_);
            p_294272_.forEach(p_295078_);
        }
    }

    default boolean contains(ChunkPos p_296112_) {
        return this.contains(p_296112_.x, p_296112_.z);
    }

    default boolean contains(int p_295374_, int p_296479_) {
        return this.contains(p_295374_, p_296479_, true);
    }

    boolean contains(int p_294429_, int p_295591_, boolean p_296102_);

    void forEach(Consumer<ChunkPos> p_294937_);

    default boolean isInViewDistance(int p_295863_, int p_294569_) {
        return this.contains(p_295863_, p_294569_, false);
    }

    static boolean isInViewDistance(int p_294551_, int p_294918_, int p_296415_, int p_296475_, int p_295248_) {
        return isWithinDistance(p_294551_, p_294918_, p_296415_, p_296475_, p_295248_, false);
    }

    static boolean isWithinDistance(int p_294927_, int p_295703_, int p_294990_, int p_295161_, int p_295394_, boolean p_295219_) {
        int i = Math.max(0, Math.abs(p_295161_ - p_294927_) - 1);
        int j = Math.max(0, Math.abs(p_295394_ - p_295703_) - 1);
        long k = (long)Math.max(0, Math.max(i, j) - (p_295219_ ? 1 : 0));
        long l = (long)Math.min(i, j);
        long i1 = l * l + k * k;
        int j1 = p_294990_ * p_294990_;
        return i1 < (long)j1;
    }

    public static record Positioned(ChunkPos center, int viewDistance) implements ChunkTrackingView {
        int minX() {
            return this.center.x - this.viewDistance - 1;
        }

        int minZ() {
            return this.center.z - this.viewDistance - 1;
        }

        int maxX() {
            return this.center.x + this.viewDistance + 1;
        }

        int maxZ() {
            return this.center.z + this.viewDistance + 1;
        }

        @VisibleForTesting
        protected boolean squareIntersects(ChunkTrackingView.Positioned p_295100_) {
            return this.minX() <= p_295100_.maxX() && this.maxX() >= p_295100_.minX() && this.minZ() <= p_295100_.maxZ() && this.maxZ() >= p_295100_.minZ();
        }

        @Override
        public boolean contains(int p_295177_, int p_294248_, boolean p_294703_) {
            return ChunkTrackingView.isWithinDistance(this.center.x, this.center.z, this.viewDistance, p_295177_, p_294248_, p_294703_);
        }

        @Override
        public void forEach(Consumer<ChunkPos> p_294236_) {
            for (int i = this.minX(); i <= this.maxX(); i++) {
                for (int j = this.minZ(); j <= this.maxZ(); j++) {
                    if (this.contains(i, j)) {
                        p_294236_.accept(new ChunkPos(i, j));
                    }
                }
            }
        }
    }
}
