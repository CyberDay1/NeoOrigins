package net.minecraft.world.entity.ai.attributes;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class Attribute implements net.neoforged.neoforge.common.extensions.IAttributeExtension {
    public static final Codec<Holder<Attribute>> CODEC = BuiltInRegistries.ATTRIBUTE.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Attribute>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ATTRIBUTE);
    private final double defaultValue;
    private boolean syncable;
    private final String descriptionId;
    private Attribute.Sentiment sentiment = Attribute.Sentiment.POSITIVE;

    protected Attribute(String p_22080_, double p_22081_) {
        this.defaultValue = p_22081_;
        this.descriptionId = p_22080_;
    }

    public double getDefaultValue() {
        return this.defaultValue;
    }

    public boolean isClientSyncable() {
        return this.syncable;
    }

    public Attribute setSyncable(boolean p_22085_) {
        this.syncable = p_22085_;
        return this;
    }

    public Attribute setSentiment(Attribute.Sentiment p_347714_) {
        this.sentiment = p_347714_;
        return this;
    }

    public double sanitizeValue(double p_22083_) {
        return p_22083_;
    }

    public String getDescriptionId() {
        return this.descriptionId;
    }

    public ChatFormatting getStyle(boolean p_347715_) {
        return this.sentiment.getStyle(p_347715_);
    }

    // Neo: Patch in the default implementation of IAttributeExtension#getMergedStyle since we need access to Attribute#sentiment

    protected static final net.minecraft.network.chat.TextColor MERGED_RED = net.minecraft.network.chat.TextColor.fromRgb(0xF93131);
    protected static final net.minecraft.network.chat.TextColor MERGED_BLUE = net.minecraft.network.chat.TextColor.fromRgb(0x7A7AF9);
    protected static final net.minecraft.network.chat.TextColor MERGED_GRAY = net.minecraft.network.chat.TextColor.fromRgb(0xCCCCCC);

    @Override
    public net.minecraft.network.chat.TextColor getMergedStyle(boolean isPositive) {
        return switch (this.sentiment) {
            case POSITIVE -> isPositive ? MERGED_BLUE : MERGED_RED;
            case NEGATIVE -> isPositive ? MERGED_RED : MERGED_BLUE;
            case NEUTRAL -> MERGED_GRAY;
        };
    }

    public static enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE;

        public ChatFormatting getStyle(boolean p_347500_) {
            return switch (this) {
                case POSITIVE -> p_347500_ ? ChatFormatting.BLUE : ChatFormatting.RED;
                case NEUTRAL -> ChatFormatting.GRAY;
                case NEGATIVE -> p_347500_ ? ChatFormatting.RED : ChatFormatting.BLUE;
            };
        }
    }
}
