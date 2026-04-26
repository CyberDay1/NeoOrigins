package net.minecraft.core;

import io.netty.buffer.ByteBuf;
import java.util.Iterator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;

public record BlockBox(BlockPos min, BlockPos max) implements Iterable<BlockPos> {
    public static final StreamCodec<ByteBuf, BlockBox> STREAM_CODEC = new StreamCodec<ByteBuf, BlockBox>() {
        public BlockBox decode(ByteBuf p_333801_) {
            return new BlockBox(FriendlyByteBuf.readBlockPos(p_333801_), FriendlyByteBuf.readBlockPos(p_333801_));
        }

        public void encode(ByteBuf p_333786_, BlockBox p_334091_) {
            FriendlyByteBuf.writeBlockPos(p_333786_, p_334091_.min());
            FriendlyByteBuf.writeBlockPos(p_333786_, p_334091_.max());
        }
    };

    public BlockBox(BlockPos min, BlockPos max) {
        this.min = BlockPos.min(min, max);
        this.max = BlockPos.max(min, max);
    }

    public static BlockBox of(BlockPos p_334061_) {
        return new BlockBox(p_334061_, p_334061_);
    }

    public static BlockBox of(BlockPos p_333833_, BlockPos p_333709_) {
        return new BlockBox(p_333833_, p_333709_);
    }

    public BlockBox include(BlockPos p_333934_) {
        return new BlockBox(BlockPos.min(this.min, p_333934_), BlockPos.max(this.max, p_333934_));
    }

    public boolean isBlock() {
        return this.min.equals(this.max);
    }

    public boolean contains(BlockPos p_333773_) {
        return p_333773_.getX() >= this.min.getX()
            && p_333773_.getY() >= this.min.getY()
            && p_333773_.getZ() >= this.min.getZ()
            && p_333773_.getX() <= this.max.getX()
            && p_333773_.getY() <= this.max.getY()
            && p_333773_.getZ() <= this.max.getZ();
    }

    public AABB aabb() {
        return AABB.encapsulatingFullBlocks(this.min, this.max);
    }

    @Override
    public Iterator<BlockPos> iterator() {
        return BlockPos.betweenClosed(this.min, this.max).iterator();
    }

    public int sizeX() {
        return this.max.getX() - this.min.getX() + 1;
    }

    public int sizeY() {
        return this.max.getY() - this.min.getY() + 1;
    }

    public int sizeZ() {
        return this.max.getZ() - this.min.getZ() + 1;
    }

    public BlockBox extend(Direction p_333798_, int p_333978_) {
        if (p_333978_ == 0) {
            return this;
        } else {
            return p_333798_.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? of(this.min, BlockPos.max(this.min, this.max.relative(p_333798_, p_333978_)))
                : of(BlockPos.min(this.min.relative(p_333798_, p_333978_), this.max), this.max);
        }
    }

    public BlockBox move(Direction p_333771_, int p_333826_) {
        return p_333826_ == 0 ? this : new BlockBox(this.min.relative(p_333771_, p_333826_), this.max.relative(p_333771_, p_333826_));
    }

    public BlockBox offset(Vec3i p_333730_) {
        return new BlockBox(this.min.offset(p_333730_), this.max.offset(p_333730_));
    }
}
