package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record PoiAddedDebugPayload(BlockPos pos, String poiType, int freeTicketCount) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, PoiAddedDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        PoiAddedDebugPayload::write, PoiAddedDebugPayload::new
    );
    public static final CustomPacketPayload.Type<PoiAddedDebugPayload> TYPE = CustomPacketPayload.createType("debug/poi_added");

    private PoiAddedDebugPayload(FriendlyByteBuf p_294710_) {
        this(p_294710_.readBlockPos(), p_294710_.readUtf(), p_294710_.readInt());
    }

    private void write(FriendlyByteBuf p_295635_) {
        p_295635_.writeBlockPos(this.pos);
        p_295635_.writeUtf(this.poiType);
        p_295635_.writeInt(this.freeTicketCount);
    }

    @Override
    public CustomPacketPayload.Type<PoiAddedDebugPayload> type() {
        return TYPE;
    }
}
