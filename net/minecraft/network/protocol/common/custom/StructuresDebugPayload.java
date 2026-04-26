package net.minecraft.network.protocol.common.custom;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record StructuresDebugPayload(ResourceKey<Level> dimension, BoundingBox mainBB, List<StructuresDebugPayload.PieceInfo> pieces)
    implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, StructuresDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        StructuresDebugPayload::write, StructuresDebugPayload::new
    );
    public static final CustomPacketPayload.Type<StructuresDebugPayload> TYPE = CustomPacketPayload.createType("debug/structures");

    private StructuresDebugPayload(FriendlyByteBuf p_294983_) {
        this(p_294983_.readResourceKey(Registries.DIMENSION), readBoundingBox(p_294983_), p_294983_.readList(StructuresDebugPayload.PieceInfo::new));
    }

    private void write(FriendlyByteBuf p_295318_) {
        p_295318_.writeResourceKey(this.dimension);
        writeBoundingBox(p_295318_, this.mainBB);
        p_295318_.writeCollection(this.pieces, (p_294583_, p_296047_) -> p_296047_.write(p_295318_));
    }

    @Override
    public CustomPacketPayload.Type<StructuresDebugPayload> type() {
        return TYPE;
    }

    static BoundingBox readBoundingBox(FriendlyByteBuf p_294387_) {
        return new BoundingBox(p_294387_.readInt(), p_294387_.readInt(), p_294387_.readInt(), p_294387_.readInt(), p_294387_.readInt(), p_294387_.readInt());
    }

    static void writeBoundingBox(FriendlyByteBuf p_296195_, BoundingBox p_294095_) {
        p_296195_.writeInt(p_294095_.minX());
        p_296195_.writeInt(p_294095_.minY());
        p_296195_.writeInt(p_294095_.minZ());
        p_296195_.writeInt(p_294095_.maxX());
        p_296195_.writeInt(p_294095_.maxY());
        p_296195_.writeInt(p_294095_.maxZ());
    }

    public static record PieceInfo(BoundingBox boundingBox, boolean isStart) {
        public PieceInfo(FriendlyByteBuf p_294562_) {
            this(StructuresDebugPayload.readBoundingBox(p_294562_), p_294562_.readBoolean());
        }

        public void write(FriendlyByteBuf p_295794_) {
            StructuresDebugPayload.writeBoundingBox(p_295794_, this.boundingBox);
            p_295794_.writeBoolean(this.isStart);
        }
    }
}
