package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

public record LodestoneTracker(Optional<GlobalPos> target, boolean tracked) {
    public static final Codec<LodestoneTracker> CODEC = RecordCodecBuilder.create(
        p_337949_ -> p_337949_.group(
                    GlobalPos.CODEC.optionalFieldOf("target").forGetter(LodestoneTracker::target),
                    Codec.BOOL.optionalFieldOf("tracked", Boolean.valueOf(true)).forGetter(LodestoneTracker::tracked)
                )
                .apply(p_337949_, LodestoneTracker::new)
    );
    public static final StreamCodec<ByteBuf, LodestoneTracker> STREAM_CODEC = StreamCodec.composite(
        GlobalPos.STREAM_CODEC.apply(ByteBufCodecs::optional), LodestoneTracker::target, ByteBufCodecs.BOOL, LodestoneTracker::tracked, LodestoneTracker::new
    );

    public LodestoneTracker tick(ServerLevel p_332798_) {
        if (this.tracked && !this.target.isEmpty()) {
            if (this.target.get().dimension() != p_332798_.dimension()) {
                return this;
            } else {
                BlockPos blockpos = this.target.get().pos();
                return p_332798_.isInWorldBounds(blockpos) && p_332798_.getPoiManager().existsAtPosition(PoiTypes.LODESTONE, blockpos)
                    ? this
                    : new LodestoneTracker(Optional.empty(), true);
            }
        } else {
            return this;
        }
    }
}
