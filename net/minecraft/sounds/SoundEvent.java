package net.minecraft.sounds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;

public class SoundEvent {
    public static final Codec<SoundEvent> DIRECT_CODEC = RecordCodecBuilder.create(
        p_337569_ -> p_337569_.group(
                    ResourceLocation.CODEC.fieldOf("sound_id").forGetter(SoundEvent::getLocation),
                    Codec.FLOAT.lenientOptionalFieldOf("range").forGetter(SoundEvent::fixedRange)
                )
                .apply(p_337569_, SoundEvent::create)
    );
    public static final Codec<Holder<SoundEvent>> CODEC = RegistryFileCodec.create(Registries.SOUND_EVENT, DIRECT_CODEC);
    public static final StreamCodec<ByteBuf, SoundEvent> DIRECT_STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, SoundEvent::getLocation, ByteBufCodecs.FLOAT.apply(ByteBufCodecs::optional), SoundEvent::fixedRange, SoundEvent::create
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<SoundEvent>> STREAM_CODEC = ByteBufCodecs.holder(
        Registries.SOUND_EVENT, DIRECT_STREAM_CODEC
    );
    private static final float DEFAULT_RANGE = 16.0F;
    private final ResourceLocation location;
    private final float range;
    private final boolean newSystem;

    private static SoundEvent create(ResourceLocation p_263406_, Optional<Float> p_263346_) {
        return p_263346_.<SoundEvent>map(p_263360_ -> createFixedRangeEvent(p_263406_, p_263360_)).orElseGet(() -> createVariableRangeEvent(p_263406_));
    }

    public static SoundEvent createVariableRangeEvent(ResourceLocation p_262973_) {
        return new SoundEvent(p_262973_, 16.0F, false);
    }

    public static SoundEvent createFixedRangeEvent(ResourceLocation p_263003_, float p_263029_) {
        return new SoundEvent(p_263003_, p_263029_, true);
    }

    private SoundEvent(ResourceLocation p_215665_, float p_215666_, boolean p_215667_) {
        this.location = p_215665_;
        this.range = p_215666_;
        this.newSystem = p_215667_;
    }

    public ResourceLocation getLocation() {
        return this.location;
    }

    public float getRange(float p_215669_) {
        if (this.newSystem) {
            return this.range;
        } else {
            return p_215669_ > 1.0F ? 16.0F * p_215669_ : 16.0F;
        }
    }

    private Optional<Float> fixedRange() {
        return this.newSystem ? Optional.of(this.range) : Optional.empty();
    }
}
