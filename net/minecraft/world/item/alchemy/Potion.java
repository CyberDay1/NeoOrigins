package net.minecraft.world.item.alchemy;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public class Potion implements FeatureElement {
    public static final Codec<Holder<Potion>> CODEC = BuiltInRegistries.POTION.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Potion>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.POTION);
    @Nullable
    private final String name;
    private final List<MobEffectInstance> effects;
    private FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;

    public Potion(MobEffectInstance... p_43487_) {
        this(null, p_43487_);
    }

    public Potion(@Nullable String p_43484_, MobEffectInstance... p_43485_) {
        this.name = p_43484_;
        this.effects = List.of(p_43485_);
    }

    public Potion requiredFeatures(FeatureFlag... p_338520_) {
        this.requiredFeatures = FeatureFlags.REGISTRY.subset(p_338520_);
        return this;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    public static String getName(Optional<Holder<Potion>> p_330503_, String p_43493_) {
        if (p_330503_.isPresent()) {
            String s = p_330503_.get().value().name;
            if (s != null) {
                return p_43493_ + s;
            }
        }

        String s1 = p_330503_.flatMap(Holder::unwrapKey).map(p_331494_ -> p_331494_.location().getPath()).orElse("empty");
        return p_43493_ + s1;
    }

    public List<MobEffectInstance> getEffects() {
        return this.effects;
    }

    public boolean hasInstantEffects() {
        if (!this.effects.isEmpty()) {
            for (MobEffectInstance mobeffectinstance : this.effects) {
                if (mobeffectinstance.getEffect().value().isInstantenous()) {
                    return true;
                }
            }
        }

        return false;
    }
}
