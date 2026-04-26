package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record NeighborUpdatesDebugPayload(long time, BlockPos pos) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, NeighborUpdatesDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        NeighborUpdatesDebugPayload::write, NeighborUpdatesDebugPayload::new
    );
    public static final CustomPacketPayload.Type<NeighborUpdatesDebugPayload> TYPE = CustomPacketPayload.createType("debug/neighbors_update");

    private NeighborUpdatesDebugPayload(FriendlyByteBuf p_295153_) {
        this(p_295153_.readVarLong(), p_295153_.readBlockPos());
    }

    private void write(FriendlyByteBuf p_296340_) {
        p_296340_.writeVarLong(this.time);
        p_296340_.writeBlockPos(this.pos);
    }

    @Override
    public CustomPacketPayload.Type<NeighborUpdatesDebugPayload> type() {
        return TYPE;
    }
}
