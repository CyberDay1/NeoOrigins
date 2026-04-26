package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record PoiRemovedDebugPayload(BlockPos pos) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, PoiRemovedDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        PoiRemovedDebugPayload::write, PoiRemovedDebugPayload::new
    );
    public static final CustomPacketPayload.Type<PoiRemovedDebugPayload> TYPE = CustomPacketPayload.createType("debug/poi_removed");

    private PoiRemovedDebugPayload(FriendlyByteBuf p_295381_) {
        this(p_295381_.readBlockPos());
    }

    private void write(FriendlyByteBuf p_295470_) {
        p_295470_.writeBlockPos(this.pos);
    }

    @Override
    public CustomPacketPayload.Type<PoiRemovedDebugPayload> type() {
        return TYPE;
    }
}
