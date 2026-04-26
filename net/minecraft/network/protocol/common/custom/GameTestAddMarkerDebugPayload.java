package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record GameTestAddMarkerDebugPayload(BlockPos pos, int color, String text, int durationMs) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, GameTestAddMarkerDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        GameTestAddMarkerDebugPayload::write, GameTestAddMarkerDebugPayload::new
    );
    public static final CustomPacketPayload.Type<GameTestAddMarkerDebugPayload> TYPE = CustomPacketPayload.createType("debug/game_test_add_marker");

    private GameTestAddMarkerDebugPayload(FriendlyByteBuf p_294353_) {
        this(p_294353_.readBlockPos(), p_294353_.readInt(), p_294353_.readUtf(), p_294353_.readInt());
    }

    private void write(FriendlyByteBuf p_294856_) {
        p_294856_.writeBlockPos(this.pos);
        p_294856_.writeInt(this.color);
        p_294856_.writeUtf(this.text);
        p_294856_.writeInt(this.durationMs);
    }

    @Override
    public CustomPacketPayload.Type<GameTestAddMarkerDebugPayload> type() {
        return TYPE;
    }
}
