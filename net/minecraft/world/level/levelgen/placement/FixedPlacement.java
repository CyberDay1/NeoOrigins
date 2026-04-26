package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;

public class FixedPlacement extends PlacementModifier {
    public static final MapCodec<FixedPlacement> CODEC = RecordCodecBuilder.mapCodec(
        p_352897_ -> p_352897_.group(BlockPos.CODEC.listOf().fieldOf("positions").forGetter(p_352962_ -> p_352962_.positions))
                .apply(p_352897_, FixedPlacement::new)
    );
    private final List<BlockPos> positions;

    public static FixedPlacement of(BlockPos... p_352896_) {
        return new FixedPlacement(List.of(p_352896_));
    }

    private FixedPlacement(List<BlockPos> p_352933_) {
        this.positions = p_352933_;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext p_352915_, RandomSource p_352928_, BlockPos p_352899_) {
        int i = SectionPos.blockToSectionCoord(p_352899_.getX());
        int j = SectionPos.blockToSectionCoord(p_352899_.getZ());
        boolean flag = false;

        for (BlockPos blockpos : this.positions) {
            if (isSameChunk(i, j, blockpos)) {
                flag = true;
                break;
            }
        }

        return !flag ? Stream.empty() : this.positions.stream().filter(p_352956_ -> isSameChunk(i, j, p_352956_));
    }

    private static boolean isSameChunk(int p_352906_, int p_352932_, BlockPos p_352907_) {
        return p_352906_ == SectionPos.blockToSectionCoord(p_352907_.getX()) && p_352932_ == SectionPos.blockToSectionCoord(p_352907_.getZ());
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.FIXED_PLACEMENT;
    }
}
