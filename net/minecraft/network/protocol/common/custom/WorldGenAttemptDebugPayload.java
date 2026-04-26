package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record WorldGenAttemptDebugPayload(BlockPos pos, float scale, float red, float green, float blue, float alpha) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, WorldGenAttemptDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        WorldGenAttemptDebugPayload::write, WorldGenAttemptDebugPayload::new
    );
    public static final CustomPacketPayload.Type<WorldGenAttemptDebugPayload> TYPE = CustomPacketPayload.createType("debug/worldgen_attempt");

    private WorldGenAttemptDebugPayload(FriendlyByteBuf p_295574_) {
        this(p_295574_.readBlockPos(), p_295574_.readFloat(), p_295574_.readFloat(), p_295574_.readFloat(), p_295574_.readFloat(), p_295574_.readFloat());
    }

    private void write(FriendlyByteBuf p_295822_) {
        p_295822_.writeBlockPos(this.pos);
        p_295822_.writeFloat(this.scale);
        p_295822_.writeFloat(this.red);
        p_295822_.writeFloat(this.green);
        p_295822_.writeFloat(this.blue);
        p_295822_.writeFloat(this.alpha);
    }

    @Override
    public CustomPacketPayload.Type<WorldGenAttemptDebugPayload> type() {
        return TYPE;
    }
}
