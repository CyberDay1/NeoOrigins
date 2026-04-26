package net.minecraft.world.effect;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public class MobEffect implements FeatureElement, net.neoforged.neoforge.common.extensions.IMobEffectExtension {
    public static final Codec<Holder<MobEffect>> CODEC = BuiltInRegistries.MOB_EFFECT.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<MobEffect>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.MOB_EFFECT);
    private static final int AMBIENT_ALPHA = Mth.floor(38.25F);
    private final Map<Holder<Attribute>, MobEffect.AttributeTemplate> attributeModifiers = new Object2ObjectOpenHashMap<>();
    private final MobEffectCategory category;
    private final int color;
    private final Function<MobEffectInstance, ParticleOptions> particleFactory;
    @Nullable
    private String descriptionId;
    private int blendDurationTicks;
    private Optional<SoundEvent> soundOnAdded = Optional.empty();
    private FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;

    protected MobEffect(MobEffectCategory p_19451_, int p_19452_) {
        this.category = p_19451_;
        this.color = p_19452_;
        this.particleFactory = p_333517_ -> {
            int i = p_333517_.isAmbient() ? AMBIENT_ALPHA : 255;
            return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, FastColor.ARGB32.color(i, p_19452_));
        };
    }

    protected MobEffect(MobEffectCategory p_333963_, int p_333864_, ParticleOptions p_333716_) {
        this.category = p_333963_;
        this.color = p_333864_;
        this.particleFactory = p_333515_ -> p_333716_;
    }

    public int getBlendDurationTicks() {
        return this.blendDurationTicks;
    }

    public boolean applyEffectTick(LivingEntity p_19467_, int p_19468_) {
        return true;
    }

    public void applyInstantenousEffect(@Nullable Entity p_19462_, @Nullable Entity p_19463_, LivingEntity p_19464_, int p_19465_, double p_19466_) {
        this.applyEffectTick(p_19464_, p_19465_);
    }

    public boolean shouldApplyEffectTickThisTick(int p_295329_, int p_295167_) {
        return false;
    }

    public void onEffectStarted(LivingEntity p_296490_, int p_296147_) {
    }

    public void onEffectAdded(LivingEntity p_338333_, int p_338715_) {
        this.soundOnAdded
            .ifPresent(
                p_352700_ -> p_338333_.level()
                        .playSound(null, p_338333_.getX(), p_338333_.getY(), p_338333_.getZ(), p_352700_, p_338333_.getSoundSource(), 1.0F, 1.0F)
            );
    }

    public void onMobRemoved(LivingEntity p_338500_, int p_338476_, Entity.RemovalReason p_338373_) {
    }

    public void onMobHurt(LivingEntity p_338186_, int p_338204_, DamageSource p_338393_, float p_338618_) {
    }

    public boolean isInstantenous() {
        return false;
    }

    protected String getOrCreateDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("effect", BuiltInRegistries.MOB_EFFECT.getKey(this));
        }

        return this.descriptionId;
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public Component getDisplayName() {
        return Component.translatable(this.getDescriptionId());
    }

    public MobEffectCategory getCategory() {
        return this.category;
    }

    public int getColor() {
        return this.color;
    }

    public MobEffect addAttributeModifier(Holder<Attribute> p_316656_, ResourceLocation p_350368_, double p_19475_, AttributeModifier.Operation p_19476_) {
        this.attributeModifiers.put(p_316656_, new MobEffect.AttributeTemplate(p_350368_, p_19475_, p_19476_));
        return this;
    }

    /**
     * Neo: attribute template with custom level curve, for mob effects providing non-linear attribute modifiers.
     * @param attribute The attribute of the modifier
     * @param id ID of the modifier
     * @param operation Operation of the modifier
     * @param curve A function mapping effect instance amplifier to modifier amount
     */
    public MobEffect addAttributeModifier(Holder<Attribute> attribute, ResourceLocation id, AttributeModifier.Operation operation, it.unimi.dsi.fastutil.ints.Int2DoubleFunction curve) {
        this.attributeModifiers.put(attribute, new MobEffect.AttributeTemplate(id, curve.apply(0), operation, curve));
        return this;
    }

    public MobEffect setBlendDuration(int p_316265_) {
        this.blendDurationTicks = p_316265_;
        return this;
    }

    public void createModifiers(int p_316803_, BiConsumer<Holder<Attribute>, AttributeModifier> p_316902_) {
        this.attributeModifiers.forEach((p_349971_, p_349972_) -> p_316902_.accept((Holder<Attribute>)p_349971_, p_349972_.create(p_316803_)));
    }

    public void removeAttributeModifiers(AttributeMap p_19470_) {
        for (Entry<Holder<Attribute>, MobEffect.AttributeTemplate> entry : this.attributeModifiers.entrySet()) {
            AttributeInstance attributeinstance = p_19470_.getInstance(entry.getKey());
            if (attributeinstance != null) {
                attributeinstance.removeModifier(entry.getValue().id());
            }
        }
    }

    public void addAttributeModifiers(AttributeMap p_19479_, int p_19480_) {
        for (Entry<Holder<Attribute>, MobEffect.AttributeTemplate> entry : this.attributeModifiers.entrySet()) {
            AttributeInstance attributeinstance = p_19479_.getInstance(entry.getKey());
            if (attributeinstance != null) {
                attributeinstance.removeModifier(entry.getValue().id());
                attributeinstance.addPermanentModifier(entry.getValue().create(p_19480_));
            }
        }
    }

    public boolean isBeneficial() {
        return this.category == MobEffectCategory.BENEFICIAL;
    }

    public ParticleOptions createParticleOptions(MobEffectInstance p_333815_) {
        return this.particleFactory.apply(p_333815_);
    }

    public MobEffect withSoundOnAdded(SoundEvent p_338383_) {
        this.soundOnAdded = Optional.of(p_338383_);
        return this;
    }

    public MobEffect requiredFeatures(FeatureFlag... p_338702_) {
        this.requiredFeatures = FeatureFlags.REGISTRY.subset(p_338702_);
        return this;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    /**
     * Neo: Allowing mods to define client behavior for their MobEffects
     * @deprecated Use {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent} instead
     */
    @Deprecated(forRemoval = true, since = "1.21")
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions> consumer) {
    }

    public static record AttributeTemplate(ResourceLocation id, double amount, AttributeModifier.Operation operation, @Nullable it.unimi.dsi.fastutil.ints.Int2DoubleFunction curve) {

        public AttributeTemplate(ResourceLocation id, double amount, AttributeModifier.Operation operation) {
            this(id, amount, operation, null);
        }

        public AttributeModifier create(int p_316614_) {
            if (curve != null) { // Neo: Use the custom attribute value curve if one is present
                return new AttributeModifier(this.id, this.curve.apply(p_316614_), this.operation);
            }
            return new AttributeModifier(this.id, this.amount * (double)(p_316614_ + 1), this.operation);
        }
    }
}
