package net.minecraft.world.item.component;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

public final class CustomData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final CustomData EMPTY = new CustomData(new CompoundTag());
    public static final Codec<CustomData> CODEC = Codec.withAlternative(CompoundTag.CODEC, TagParser.AS_CODEC)
        .xmap(CustomData::new, p_331996_ -> p_331996_.tag);
    public static final Codec<CustomData> CODEC_WITH_ID = CODEC.validate(
        p_331848_ -> p_331848_.getUnsafe().contains("id", 8) ? DataResult.success(p_331848_) : DataResult.error(() -> "Missing id for entity in: " + p_331848_)
    );
    @Deprecated
    public static final StreamCodec<ByteBuf, CustomData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(CustomData::new, p_331280_ -> p_331280_.tag);
    private final CompoundTag tag;

    private CustomData(CompoundTag p_331863_) {
        this.tag = p_331863_;
    }

    public static CustomData of(CompoundTag p_330724_) {
        return new CustomData(p_330724_.copy());
    }

    public static Predicate<ItemStack> itemMatcher(DataComponentType<CustomData> p_332149_, CompoundTag p_330658_) {
        return p_332154_ -> {
            CustomData customdata = p_332154_.getOrDefault(p_332149_, EMPTY);
            return customdata.matchedBy(p_330658_);
        };
    }

    public boolean matchedBy(CompoundTag p_330550_) {
        return NbtUtils.compareNbt(p_330550_, this.tag, true);
    }

    public static void update(DataComponentType<CustomData> p_331895_, ItemStack p_332185_, Consumer<CompoundTag> p_331274_) {
        CustomData customdata = p_332185_.getOrDefault(p_331895_, EMPTY).update(p_331274_);
        if (customdata.tag.isEmpty()) {
            p_332185_.remove(p_331895_);
        } else {
            p_332185_.set(p_331895_, customdata);
        }
    }

    public static void set(DataComponentType<CustomData> p_330462_, ItemStack p_332015_, CompoundTag p_331019_) {
        if (!p_331019_.isEmpty()) {
            p_332015_.set(p_330462_, of(p_331019_));
        } else {
            p_332015_.remove(p_330462_);
        }
    }

    public CustomData update(Consumer<CompoundTag> p_331451_) {
        CompoundTag compoundtag = this.tag.copy();
        p_331451_.accept(compoundtag);
        return new CustomData(compoundtag);
    }

    public void loadInto(Entity p_331834_) {
        CompoundTag compoundtag = p_331834_.saveWithoutId(new CompoundTag());
        UUID uuid = p_331834_.getUUID();
        compoundtag.merge(this.tag);
        p_331834_.load(compoundtag);
        p_331834_.setUUID(uuid);
    }

    public boolean loadInto(BlockEntity p_331657_, HolderLookup.Provider p_331528_) {
        CompoundTag compoundtag = p_331657_.saveCustomOnly(p_331528_);
        CompoundTag compoundtag1 = compoundtag.copy();
        compoundtag.merge(this.tag);
        if (!compoundtag.equals(compoundtag1)) {
            try {
                p_331657_.loadCustomOnly(compoundtag, p_331528_);
                p_331657_.setChanged();
                return true;
            } catch (Exception exception1) {
                LOGGER.warn("Failed to apply custom data to block entity at {}", p_331657_.getBlockPos(), exception1);

                try {
                    p_331657_.loadCustomOnly(compoundtag1, p_331528_);
                } catch (Exception exception) {
                    LOGGER.warn("Failed to rollback block entity at {} after failure", p_331657_.getBlockPos(), exception);
                }
            }
        }

        return false;
    }

    public <T> DataResult<CustomData> update(DynamicOps<Tag> p_346001_, MapEncoder<T> p_331823_, T p_332045_) {
        return p_331823_.encode(p_332045_, p_346001_, p_346001_.mapBuilder()).build(this.tag).map(p_330397_ -> new CustomData((CompoundTag)p_330397_));
    }

    public <T> DataResult<T> read(MapDecoder<T> p_330352_) {
        return this.read(NbtOps.INSTANCE, p_330352_);
    }

    public <T> DataResult<T> read(DynamicOps<Tag> p_346230_, MapDecoder<T> p_344951_) {
        MapLike<Tag> maplike = p_346230_.getMap(this.tag).getOrThrow();
        return p_344951_.decode(p_346230_, maplike);
    }

    public int size() {
        return this.tag.size();
    }

    public boolean isEmpty() {
        return this.tag.isEmpty();
    }

    public CompoundTag copyTag() {
        return this.tag.copy();
    }

    public boolean contains(String p_331843_) {
        return this.tag.contains(p_331843_);
    }

    @Override
    public boolean equals(Object p_331195_) {
        if (p_331195_ == this) {
            return true;
        } else {
            return p_331195_ instanceof CustomData customdata ? this.tag.equals(customdata.tag) : false;
        }
    }

    @Override
    public int hashCode() {
        return this.tag.hashCode();
    }

    @Override
    public String toString() {
        return this.tag.toString();
    }

    @Deprecated
    public CompoundTag getUnsafe() {
        return this.tag;
    }
}
