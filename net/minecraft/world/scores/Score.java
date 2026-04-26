package net.minecraft.world.scores;

import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.NumberFormatTypes;

public class Score implements ReadOnlyScoreInfo {
    private static final String TAG_SCORE = "Score";
    private static final String TAG_LOCKED = "Locked";
    private static final String TAG_DISPLAY = "display";
    private static final String TAG_FORMAT = "format";
    private int value;
    private boolean locked = true;
    @Nullable
    private Component display;
    @Nullable
    private NumberFormat numberFormat;

    @Override
    public int value() {
        return this.value;
    }

    public void value(int p_313791_) {
        this.value = p_313791_;
    }

    @Override
    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean p_83399_) {
        this.locked = p_83399_;
    }

    @Nullable
    public Component display() {
        return this.display;
    }

    public void display(@Nullable Component p_313838_) {
        this.display = p_313838_;
    }

    @Nullable
    @Override
    public NumberFormat numberFormat() {
        return this.numberFormat;
    }

    public void numberFormat(@Nullable NumberFormat p_313931_) {
        this.numberFormat = p_313931_;
    }

    public CompoundTag write(HolderLookup.Provider p_330377_) {
        CompoundTag compoundtag = new CompoundTag();
        compoundtag.putInt("Score", this.value);
        compoundtag.putBoolean("Locked", this.locked);
        if (this.display != null) {
            compoundtag.putString("display", Component.Serializer.toJson(this.display, p_330377_));
        }

        if (this.numberFormat != null) {
            NumberFormatTypes.CODEC
                .encodeStart(p_330377_.createSerializationContext(NbtOps.INSTANCE), this.numberFormat)
                .ifSuccess(p_313666_ -> compoundtag.put("format", p_313666_));
        }

        return compoundtag;
    }

    public static Score read(CompoundTag p_313855_, HolderLookup.Provider p_331997_) {
        Score score = new Score();
        score.value = p_313855_.getInt("Score");
        score.locked = p_313855_.getBoolean("Locked");
        if (p_313855_.contains("display", 8)) {
            score.display = Component.Serializer.fromJson(p_313855_.getString("display"), p_331997_);
        }

        if (p_313855_.contains("format", 10)) {
            NumberFormatTypes.CODEC
                .parse(p_331997_.createSerializationContext(NbtOps.INSTANCE), p_313855_.get("format"))
                .ifSuccess(p_313664_ -> score.numberFormat = p_313664_);
        }

        return score;
    }
}
