package net.minecraft.network.protocol.common.custom;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public record BeeDebugPayload(BeeDebugPayload.BeeInfo beeInfo) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, BeeDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(BeeDebugPayload::write, BeeDebugPayload::new);
    public static final CustomPacketPayload.Type<BeeDebugPayload> TYPE = CustomPacketPayload.createType("debug/bee");

    private BeeDebugPayload(FriendlyByteBuf p_295882_) {
        this(new BeeDebugPayload.BeeInfo(p_295882_));
    }

    private void write(FriendlyByteBuf p_295949_) {
        this.beeInfo.write(p_295949_);
    }

    @Override
    public CustomPacketPayload.Type<BeeDebugPayload> type() {
        return TYPE;
    }

    public static record BeeInfo(
        UUID uuid,
        int id,
        Vec3 pos,
        @Nullable Path path,
        @Nullable BlockPos hivePos,
        @Nullable BlockPos flowerPos,
        int travelTicks,
        Set<String> goals,
        List<BlockPos> blacklistedHives
    ) {
        public BeeInfo(FriendlyByteBuf p_295195_) {
            this(
                p_295195_.readUUID(),
                p_295195_.readInt(),
                p_295195_.readVec3(),
                p_295195_.readNullable(Path::createFromStream),
                p_295195_.readNullable(BlockPos.STREAM_CODEC),
                p_295195_.readNullable(BlockPos.STREAM_CODEC),
                p_295195_.readInt(),
                p_295195_.readCollection(HashSet::new, FriendlyByteBuf::readUtf),
                p_295195_.readList(BlockPos.STREAM_CODEC)
            );
        }

        public void write(FriendlyByteBuf p_294673_) {
            p_294673_.writeUUID(this.uuid);
            p_294673_.writeInt(this.id);
            p_294673_.writeVec3(this.pos);
            p_294673_.writeNullable(this.path, (p_294507_, p_294902_) -> p_294902_.writeToStream(p_294507_));
            p_294673_.writeNullable(this.hivePos, BlockPos.STREAM_CODEC);
            p_294673_.writeNullable(this.flowerPos, BlockPos.STREAM_CODEC);
            p_294673_.writeInt(this.travelTicks);
            p_294673_.writeCollection(this.goals, FriendlyByteBuf::writeUtf);
            p_294673_.writeCollection(this.blacklistedHives, BlockPos.STREAM_CODEC);
        }

        public boolean hasHive(BlockPos p_296043_) {
            return Objects.equals(p_296043_, this.hivePos);
        }

        public String generateName() {
            return DebugEntityNameGenerator.getEntityName(this.uuid);
        }

        @Override
        public String toString() {
            return this.generateName();
        }
    }
}
