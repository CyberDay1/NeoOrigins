package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractUniversalBuilder;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class NullOps implements DynamicOps<Unit> {
    public static final NullOps INSTANCE = new NullOps();

    private NullOps() {
    }

    public <U> U convertTo(DynamicOps<U> p_341193_, Unit p_340817_) {
        return p_341193_.empty();
    }

    public Unit empty() {
        return Unit.INSTANCE;
    }

    public Unit emptyMap() {
        return Unit.INSTANCE;
    }

    public Unit emptyList() {
        return Unit.INSTANCE;
    }

    public Unit createNumeric(Number p_341390_) {
        return Unit.INSTANCE;
    }

    public Unit createByte(byte p_341017_) {
        return Unit.INSTANCE;
    }

    public Unit createShort(short p_340945_) {
        return Unit.INSTANCE;
    }

    public Unit createInt(int p_341029_) {
        return Unit.INSTANCE;
    }

    public Unit createLong(long p_341019_) {
        return Unit.INSTANCE;
    }

    public Unit createFloat(float p_341123_) {
        return Unit.INSTANCE;
    }

    public Unit createDouble(double p_341242_) {
        return Unit.INSTANCE;
    }

    public Unit createBoolean(boolean p_341283_) {
        return Unit.INSTANCE;
    }

    public Unit createString(String p_341226_) {
        return Unit.INSTANCE;
    }

    public DataResult<Number> getNumberValue(Unit p_340886_) {
        return DataResult.error(() -> "Not a number");
    }

    public DataResult<Boolean> getBooleanValue(Unit p_341106_) {
        return DataResult.error(() -> "Not a boolean");
    }

    public DataResult<String> getStringValue(Unit p_341344_) {
        return DataResult.error(() -> "Not a string");
    }

    public DataResult<Unit> mergeToList(Unit p_340991_, Unit p_341154_) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Unit> mergeToList(Unit p_340869_, List<Unit> p_340960_) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Unit> mergeToMap(Unit p_340850_, Unit p_341356_, Unit p_341172_) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Unit> mergeToMap(Unit p_340821_, Map<Unit, Unit> p_341065_) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Unit> mergeToMap(Unit p_340862_, MapLike<Unit> p_341115_) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Stream<Pair<Unit, Unit>>> getMapValues(Unit p_341031_) {
        return DataResult.error(() -> "Not a map");
    }

    public DataResult<Consumer<BiConsumer<Unit, Unit>>> getMapEntries(Unit p_340815_) {
        return DataResult.error(() -> "Not a map");
    }

    public DataResult<MapLike<Unit>> getMap(Unit p_341149_) {
        return DataResult.error(() -> "Not a map");
    }

    public DataResult<Stream<Unit>> getStream(Unit p_340835_) {
        return DataResult.error(() -> "Not a list");
    }

    public DataResult<Consumer<Consumer<Unit>>> getList(Unit p_340931_) {
        return DataResult.error(() -> "Not a list");
    }

    public DataResult<ByteBuffer> getByteBuffer(Unit p_341064_) {
        return DataResult.error(() -> "Not a byte list");
    }

    public DataResult<IntStream> getIntStream(Unit p_341012_) {
        return DataResult.error(() -> "Not an int list");
    }

    public DataResult<LongStream> getLongStream(Unit p_341130_) {
        return DataResult.error(() -> "Not a long list");
    }

    public Unit createMap(Stream<Pair<Unit, Unit>> p_341095_) {
        return Unit.INSTANCE;
    }

    public Unit createMap(Map<Unit, Unit> p_341189_) {
        return Unit.INSTANCE;
    }

    public Unit createList(Stream<Unit> p_341162_) {
        return Unit.INSTANCE;
    }

    public Unit createByteList(ByteBuffer p_340905_) {
        return Unit.INSTANCE;
    }

    public Unit createIntList(IntStream p_341326_) {
        return Unit.INSTANCE;
    }

    public Unit createLongList(LongStream p_341025_) {
        return Unit.INSTANCE;
    }

    public Unit remove(Unit p_341250_, String p_340980_) {
        return p_341250_;
    }

    @Override
    public RecordBuilder<Unit> mapBuilder() {
        return new NullOps.NullMapBuilder(this);
    }

    @Override
    public String toString() {
        return "Null";
    }

    static final class NullMapBuilder extends AbstractUniversalBuilder<Unit, Unit> {
        public NullMapBuilder(DynamicOps<Unit> p_340884_) {
            super(p_340884_);
        }

        protected Unit initBuilder() {
            return Unit.INSTANCE;
        }

        protected Unit append(Unit p_341121_, Unit p_341327_, Unit p_341036_) {
            return p_341036_;
        }

        protected DataResult<Unit> build(Unit p_341068_, Unit p_341207_) {
            return DataResult.success(p_341207_);
        }
    }
}
