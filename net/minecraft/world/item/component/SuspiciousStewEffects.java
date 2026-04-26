package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public record SuspiciousStewEffects(List<SuspiciousStewEffects.Entry> effects) {
    public static final SuspiciousStewEffects EMPTY = new SuspiciousStewEffects(List.of());
    public static final Codec<SuspiciousStewEffects> CODEC = SuspiciousStewEffects.Entry.CODEC
        .listOf()
        .xmap(SuspiciousStewEffects::new, SuspiciousStewEffects::effects);
    public static final StreamCodec<RegistryFriendlyByteBuf, SuspiciousStewEffects> STREAM_CODEC = SuspiciousStewEffects.Entry.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(SuspiciousStewEffects::new, SuspiciousStewEffects::effects);

    public SuspiciousStewEffects withEffectAdded(SuspiciousStewEffects.Entry p_330534_) {
        return new SuspiciousStewEffects(Util.copyAndAdd(this.effects, p_330534_));
    }

    public static record Entry(Holder<MobEffect> effect, int duration) {
        public static final Codec<SuspiciousStewEffects.Entry> CODEC = RecordCodecBuilder.create(
            p_348389_ -> p_348389_.group(
                        MobEffect.CODEC.fieldOf("id").forGetter(SuspiciousStewEffects.Entry::effect),
                        Codec.INT.lenientOptionalFieldOf("duration", Integer.valueOf(160)).forGetter(SuspiciousStewEffects.Entry::duration)
                    )
                    .apply(p_348389_, SuspiciousStewEffects.Entry::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SuspiciousStewEffects.Entry> STREAM_CODEC = StreamCodec.composite(
            MobEffect.STREAM_CODEC,
            SuspiciousStewEffects.Entry::effect,
            ByteBufCodecs.VAR_INT,
            SuspiciousStewEffects.Entry::duration,
            SuspiciousStewEffects.Entry::new
        );

        public MobEffectInstance createEffectInstance() {
            return new MobEffectInstance(this.effect, this.duration);
        }
    }
}
