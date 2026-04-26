package net.minecraft.world;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class RandomSequences extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final long worldSeed;
    private int salt;
    private boolean includeWorldSeed = true;
    private boolean includeSequenceId = true;
    private final Map<ResourceLocation, RandomSequence> sequences = new Object2ObjectOpenHashMap<>();

    public static SavedData.Factory<RandomSequences> factory(long p_294688_) {
        return new SavedData.Factory<>(
            () -> new RandomSequences(p_294688_), (p_293846_, p_324262_) -> load(p_294688_, p_293846_), DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
        );
    }

    public RandomSequences(long p_287622_) {
        this.worldSeed = p_287622_;
    }

    public RandomSource get(ResourceLocation p_287751_) {
        RandomSource randomsource = this.sequences.computeIfAbsent(p_287751_, this::createSequence).random();
        return new RandomSequences.DirtyMarkingRandomSource(randomsource);
    }

    private RandomSequence createSequence(ResourceLocation p_295796_) {
        return this.createSequence(p_295796_, this.salt, this.includeWorldSeed, this.includeSequenceId);
    }

    private RandomSequence createSequence(ResourceLocation p_295614_, int p_296284_, boolean p_296271_, boolean p_295917_) {
        long i = (p_296271_ ? this.worldSeed : 0L) ^ (long)p_296284_;
        return new RandomSequence(i, p_295917_ ? Optional.of(p_295614_) : Optional.empty());
    }

    public void forAllSequences(BiConsumer<ResourceLocation, RandomSequence> p_294252_) {
        this.sequences.forEach(p_294252_);
    }

    public void setSeedDefaults(int p_294511_, boolean p_294255_, boolean p_295889_) {
        this.salt = p_294511_;
        this.includeWorldSeed = p_294255_;
        this.includeSequenceId = p_295889_;
    }

    @Override
    public CompoundTag save(CompoundTag p_287658_, HolderLookup.Provider p_323500_) {
        p_287658_.putInt("salt", this.salt);
        p_287658_.putBoolean("include_world_seed", this.includeWorldSeed);
        p_287658_.putBoolean("include_sequence_id", this.includeSequenceId);
        CompoundTag compoundtag = new CompoundTag();
        this.sequences
            .forEach(
                (p_337697_, p_337698_) -> compoundtag.put(
                        p_337697_.toString(), RandomSequence.CODEC.encodeStart(NbtOps.INSTANCE, p_337698_).result().orElseThrow()
                    )
            );
        p_287658_.put("sequences", compoundtag);
        return p_287658_;
    }

    private static boolean getBooleanWithDefault(CompoundTag p_296109_, String p_295934_, boolean p_295516_) {
        return p_296109_.contains(p_295934_, 1) ? p_296109_.getBoolean(p_295934_) : p_295516_;
    }

    public static RandomSequences load(long p_287756_, CompoundTag p_287587_) {
        RandomSequences randomsequences = new RandomSequences(p_287756_);
        randomsequences.setSeedDefaults(
            p_287587_.getInt("salt"),
            getBooleanWithDefault(p_287587_, "include_world_seed", true),
            getBooleanWithDefault(p_287587_, "include_sequence_id", true)
        );
        CompoundTag compoundtag = p_287587_.getCompound("sequences");

        for (String s : compoundtag.getAllKeys()) {
            try {
                RandomSequence randomsequence = RandomSequence.CODEC.decode(NbtOps.INSTANCE, compoundtag.get(s)).result().get().getFirst();
                randomsequences.sequences.put(ResourceLocation.parse(s), randomsequence);
            } catch (Exception exception) {
                LOGGER.error("Failed to load random sequence {}", s, exception);
            }
        }

        return randomsequences;
    }

    public int clear() {
        int i = this.sequences.size();
        this.sequences.clear();
        return i;
    }

    public void reset(ResourceLocation p_296099_) {
        this.sequences.put(p_296099_, this.createSequence(p_296099_));
    }

    public void reset(ResourceLocation p_294608_, int p_295700_, boolean p_296000_, boolean p_294735_) {
        this.sequences.put(p_294608_, this.createSequence(p_294608_, p_295700_, p_296000_, p_294735_));
    }

    class DirtyMarkingRandomSource implements RandomSource {
        private final RandomSource random;

        DirtyMarkingRandomSource(RandomSource p_295768_) {
            this.random = p_295768_;
        }

        @Override
        public RandomSource fork() {
            RandomSequences.this.setDirty();
            return this.random.fork();
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            RandomSequences.this.setDirty();
            return this.random.forkPositional();
        }

        @Override
        public void setSeed(long p_295551_) {
            RandomSequences.this.setDirty();
            this.random.setSeed(p_295551_);
        }

        @Override
        public int nextInt() {
            RandomSequences.this.setDirty();
            return this.random.nextInt();
        }

        @Override
        public int nextInt(int p_294632_) {
            RandomSequences.this.setDirty();
            return this.random.nextInt(p_294632_);
        }

        @Override
        public long nextLong() {
            RandomSequences.this.setDirty();
            return this.random.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            RandomSequences.this.setDirty();
            return this.random.nextBoolean();
        }

        @Override
        public float nextFloat() {
            RandomSequences.this.setDirty();
            return this.random.nextFloat();
        }

        @Override
        public double nextDouble() {
            RandomSequences.this.setDirty();
            return this.random.nextDouble();
        }

        @Override
        public double nextGaussian() {
            RandomSequences.this.setDirty();
            return this.random.nextGaussian();
        }

        @Override
        public boolean equals(Object p_294463_) {
            if (this == p_294463_) {
                return true;
            } else {
                return p_294463_ instanceof RandomSequences.DirtyMarkingRandomSource randomsequences$dirtymarkingrandomsource
                    ? this.random.equals(randomsequences$dirtymarkingrandomsource.random)
                    : false;
            }
        }
    }
}
