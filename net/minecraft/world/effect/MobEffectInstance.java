package net.minecraft.world.effect;

import com.google.common.collect.ComparisonChain;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

public class MobEffectInstance implements Comparable<MobEffectInstance> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int INFINITE_DURATION = -1;
    public static final int MIN_AMPLIFIER = 0;
    public static final int MAX_AMPLIFIER = 255;
    public static final Codec<MobEffectInstance> CODEC = RecordCodecBuilder.create(
        p_348152_ -> p_348152_.group(
                    MobEffect.CODEC.fieldOf("id").forGetter(MobEffectInstance::getEffect),
                    MobEffectInstance.Details.MAP_CODEC.forGetter(MobEffectInstance::asDetails)
                )
                .apply(p_348152_, MobEffectInstance::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MobEffectInstance> STREAM_CODEC = StreamCodec.composite(
        MobEffect.STREAM_CODEC, MobEffectInstance::getEffect, MobEffectInstance.Details.STREAM_CODEC, MobEffectInstance::asDetails, MobEffectInstance::new
    );
    private final Holder<MobEffect> effect;
    private int duration;
    private int amplifier;
    private boolean ambient;
    private boolean visible;
    private boolean showIcon;
    @Nullable
    private MobEffectInstance hiddenEffect;
    private final MobEffectInstance.BlendState blendState = new MobEffectInstance.BlendState();

    public MobEffectInstance(Holder<MobEffect> p_316782_) {
        this(p_316782_, 0, 0);
    }

    public MobEffectInstance(Holder<MobEffect> p_316473_, int p_19523_) {
        this(p_316473_, p_19523_, 0);
    }

    public MobEffectInstance(Holder<MobEffect> p_316819_, int p_216888_, int p_216889_) {
        this(p_316819_, p_216888_, p_216889_, false, true);
    }

    public MobEffectInstance(Holder<MobEffect> p_316846_, int p_19516_, int p_316691_, boolean p_316120_, boolean p_316433_) {
        this(p_316846_, p_19516_, p_316691_, p_316120_, p_316433_, p_316433_);
    }

    public MobEffectInstance(Holder<MobEffect> p_316870_, int p_316726_, int p_316828_, boolean p_316179_, boolean p_316397_, boolean p_316398_) {
        this(p_316870_, p_316726_, p_316828_, p_316179_, p_316397_, p_316398_, null);
    }

    public MobEffectInstance(
        Holder<MobEffect> p_316176_, int p_19529_, int p_19530_, boolean p_19531_, boolean p_19532_, boolean p_19533_, @Nullable MobEffectInstance p_316863_
    ) {
        this.effect = p_316176_;
        this.duration = p_19529_;
        this.amplifier = Mth.clamp(p_19530_, 0, 255);
        this.ambient = p_19531_;
        this.visible = p_19532_;
        this.showIcon = p_19533_;
        this.hiddenEffect = p_316863_;
        this.effect.value().fillEffectCures(this.cures, this);
    }

    public MobEffectInstance(MobEffectInstance p_19543_) {
        this.effect = p_19543_.effect;
        this.setDetailsFrom(p_19543_);
    }

    private MobEffectInstance(Holder<MobEffect> p_324441_, MobEffectInstance.Details p_324529_) {
        this(
            p_324441_,
            p_324529_.duration(),
            p_324529_.amplifier(),
            p_324529_.ambient(),
            p_324529_.showParticles(),
            p_324529_.showIcon(),
            p_324529_.hiddenEffect().map(p_323227_ -> new MobEffectInstance(p_324441_, p_323227_)).orElse(null)
        );
        this.cures.clear();
        p_324529_.cures().ifPresent(this.cures::addAll);
    }

    private MobEffectInstance.Details asDetails() {
        return new MobEffectInstance.Details(
            this.getAmplifier(),
            this.getDuration(),
            this.isAmbient(),
            this.isVisible(),
            this.showIcon(),
            Optional.ofNullable(this.hiddenEffect).map(MobEffectInstance::asDetails),
            Optional.of(this.getCures()).filter(cures -> !cures.isEmpty())
        );
    }

    public float getBlendFactor(LivingEntity p_316410_, float p_316194_) {
        return this.blendState.getFactor(p_316410_, p_316194_);
    }

    public ParticleOptions getParticleOptions() {
        return this.effect.value().createParticleOptions(this);
    }

    void setDetailsFrom(MobEffectInstance p_19549_) {
        this.duration = p_19549_.duration;
        this.amplifier = p_19549_.amplifier;
        this.ambient = p_19549_.ambient;
        this.visible = p_19549_.visible;
        this.showIcon = p_19549_.showIcon;
        this.cures.clear();
        this.cures.addAll(p_19549_.cures);
    }

    public boolean update(MobEffectInstance p_19559_) {
        if (!this.effect.equals(p_19559_.effect)) {
            LOGGER.warn("This method should only be called for matching effects!");
        }

        boolean flag = false;
        if (p_19559_.amplifier > this.amplifier) {
            if (p_19559_.isShorterDurationThan(this)) {
                MobEffectInstance mobeffectinstance = this.hiddenEffect;
                this.hiddenEffect = new MobEffectInstance(this);
                this.hiddenEffect.hiddenEffect = mobeffectinstance;
            }

            this.amplifier = p_19559_.amplifier;
            this.duration = p_19559_.duration;
            flag = true;
        } else if (this.isShorterDurationThan(p_19559_)) {
            if (p_19559_.amplifier == this.amplifier) {
                this.duration = p_19559_.duration;
                flag = true;
            } else if (this.hiddenEffect == null) {
                this.hiddenEffect = new MobEffectInstance(p_19559_);
            } else {
                this.hiddenEffect.update(p_19559_);
            }
        }

        if (!p_19559_.ambient && this.ambient || flag) {
            this.ambient = p_19559_.ambient;
            flag = true;
        }

        if (p_19559_.visible != this.visible) {
            this.visible = p_19559_.visible;
            flag = true;
        }

        if (p_19559_.showIcon != this.showIcon) {
            this.showIcon = p_19559_.showIcon;
            flag = true;
        }

        return flag;
    }

    private boolean isShorterDurationThan(MobEffectInstance p_268133_) {
        return !this.isInfiniteDuration() && (this.duration < p_268133_.duration || p_268133_.isInfiniteDuration());
    }

    public boolean isInfiniteDuration() {
        return this.duration == -1;
    }

    public boolean endsWithin(int p_268088_) {
        return !this.isInfiniteDuration() && this.duration <= p_268088_;
    }

    public int mapDuration(Int2IntFunction p_268089_) {
        return !this.isInfiniteDuration() && this.duration != 0 ? p_268089_.applyAsInt(this.duration) : this.duration;
    }

    public Holder<MobEffect> getEffect() {
        return this.effect;
    }

    public int getDuration() {
        return this.duration;
    }

    public int getAmplifier() {
        return this.amplifier;
    }

    public boolean isAmbient() {
        return this.ambient;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean showIcon() {
        return this.showIcon;
    }

    public boolean tick(LivingEntity p_19553_, Runnable p_19554_) {
        if (this.hasRemainingDuration()) {
            int i = this.isInfiniteDuration() ? p_19553_.tickCount : this.duration;
            if (this.effect.value().shouldApplyEffectTickThisTick(i, this.amplifier) && !this.effect.value().applyEffectTick(p_19553_, this.amplifier)) {
                p_19553_.removeEffect(this.effect);
            }

            this.tickDownDuration();
            if (this.duration == 0 && this.hiddenEffect != null) {
                this.setDetailsFrom(this.hiddenEffect);
                this.hiddenEffect = this.hiddenEffect.hiddenEffect;
                p_19554_.run();
            }
        }

        this.blendState.tick(this);
        return this.hasRemainingDuration();
    }

    private boolean hasRemainingDuration() {
        return this.isInfiniteDuration() || this.duration > 0;
    }

    private int tickDownDuration() {
        if (this.hiddenEffect != null) {
            this.hiddenEffect.tickDownDuration();
        }

        return this.duration = this.mapDuration(p_267916_ -> p_267916_ - 1);
    }

    public void onEffectStarted(LivingEntity p_295220_) {
        this.effect.value().onEffectStarted(p_295220_, this.amplifier);
    }

    public void onMobRemoved(LivingEntity p_338566_, Entity.RemovalReason p_338384_) {
        this.effect.value().onMobRemoved(p_338566_, this.amplifier, p_338384_);
    }

    public void onMobHurt(LivingEntity p_338201_, DamageSource p_338572_, float p_338779_) {
        this.effect.value().onMobHurt(p_338201_, this.amplifier, p_338572_, p_338779_);
    }

    public String getDescriptionId() {
        return this.effect.value().getDescriptionId();
    }

    @Override
    public String toString() {
        String s;
        if (this.amplifier > 0) {
            s = this.getDescriptionId() + " x " + (this.amplifier + 1) + ", Duration: " + this.describeDuration();
        } else {
            s = this.getDescriptionId() + ", Duration: " + this.describeDuration();
        }

        if (!this.visible) {
            s = s + ", Particles: false";
        }

        if (!this.showIcon) {
            s = s + ", Show Icon: false";
        }

        return s;
    }

    private String describeDuration() {
        return this.isInfiniteDuration() ? "infinite" : Integer.toString(this.duration);
    }

    @Override
    public boolean equals(Object p_19574_) {
        if (this == p_19574_) {
            return true;
        } else {
            return !(p_19574_ instanceof MobEffectInstance mobeffectinstance)
                ? false
                : this.duration == mobeffectinstance.duration
                    && this.amplifier == mobeffectinstance.amplifier
                    && this.ambient == mobeffectinstance.ambient
                    && this.visible == mobeffectinstance.visible
                    && this.showIcon == mobeffectinstance.showIcon
                    && this.effect.equals(mobeffectinstance.effect);
        }
    }

    @Override
    public int hashCode() {
        int i = this.effect.hashCode();
        i = 31 * i + this.duration;
        i = 31 * i + this.amplifier;
        i = 31 * i + (this.ambient ? 1 : 0);
        i = 31 * i + (this.visible ? 1 : 0);
        return 31 * i + (this.showIcon ? 1 : 0);
    }

    public Tag save() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    @Nullable
    public static MobEffectInstance load(CompoundTag p_19561_) {
        return CODEC.parse(NbtOps.INSTANCE, p_19561_).resultOrPartial(LOGGER::error).orElse(null);
    }

    public int compareTo(MobEffectInstance p_19566_) {
        int i = 32147;
        return (this.getDuration() <= 32147 || p_19566_.getDuration() <= 32147) && (!this.isAmbient() || !p_19566_.isAmbient())
            ? ComparisonChain.start()
                .compareFalseFirst(this.isAmbient(), p_19566_.isAmbient())
                .compareFalseFirst(this.isInfiniteDuration(), p_19566_.isInfiniteDuration())
                .compare(this.getDuration(), p_19566_.getDuration())
                .compare(this.getEffect().value().getSortOrder(this), p_19566_.getEffect().value().getSortOrder(p_19566_))
                .result()
            : ComparisonChain.start()
                .compare(this.isAmbient(), p_19566_.isAmbient())
                .compare(this.getEffect().value().getSortOrder(this), p_19566_.getEffect().value().getSortOrder(p_19566_))
                .result();
    }

    public void onEffectAdded(LivingEntity p_338286_) {
        this.effect.value().onEffectAdded(p_338286_, this.amplifier);
    }

    public boolean is(Holder<MobEffect> p_316657_) {
        return this.effect.equals(p_316657_);
    }

    public void copyBlendState(MobEffectInstance p_316485_) {
        this.blendState.copyFrom(p_316485_.blendState);
    }

    public void skipBlending() {
        this.blendState.setImmediate(this);
    }

    private final java.util.Set<net.neoforged.neoforge.common.EffectCure> cures = com.google.common.collect.Sets.newIdentityHashSet();

    /**
     * {@return the {@link net.neoforged.neoforge.common.EffectCure}s which can cure the {@link MobEffect} held by this {@link MobEffectInstance}}
     */
    public java.util.Set<net.neoforged.neoforge.common.EffectCure> getCures() {
        return cures;
    }

    static class BlendState {
        private float factor;
        private float factorPreviousFrame;

        public void setImmediate(MobEffectInstance p_316328_) {
            this.factor = computeTarget(p_316328_);
            this.factorPreviousFrame = this.factor;
        }

        public void copyFrom(MobEffectInstance.BlendState p_316847_) {
            this.factor = p_316847_.factor;
            this.factorPreviousFrame = p_316847_.factorPreviousFrame;
        }

        public void tick(MobEffectInstance p_316550_) {
            this.factorPreviousFrame = this.factor;
            int i = getBlendDuration(p_316550_);
            if (i == 0) {
                this.factor = 1.0F;
            } else {
                float f = computeTarget(p_316550_);
                if (this.factor != f) {
                    float f1 = 1.0F / (float)i;
                    this.factor = this.factor + Mth.clamp(f - this.factor, -f1, f1);
                }
            }
        }

        private static float computeTarget(MobEffectInstance p_316339_) {
            boolean flag = !p_316339_.endsWithin(getBlendDuration(p_316339_));
            return flag ? 1.0F : 0.0F;
        }

        private static int getBlendDuration(MobEffectInstance p_316448_) {
            return p_316448_.getEffect().value().getBlendDurationTicks();
        }

        public float getFactor(LivingEntity p_316317_, float p_316789_) {
            if (p_316317_.isRemoved()) {
                this.factorPreviousFrame = this.factor;
            }

            return Mth.lerp(p_316789_, this.factorPreviousFrame, this.factor);
        }
    }

    static record Details(
        int amplifier, int duration, boolean ambient, boolean showParticles, boolean showIcon, Optional<MobEffectInstance.Details> hiddenEffect, Optional<java.util.Set<net.neoforged.neoforge.common.EffectCure>> cures) {
        public static final MapCodec<MobEffectInstance.Details> MAP_CODEC = MapCodec.recursive(
            "MobEffectInstance.Details",
            p_323465_ -> RecordCodecBuilder.mapCodec(
                    p_324063_ -> p_324063_.group(
                                ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("amplifier", 0).forGetter(MobEffectInstance.Details::amplifier),
                                Codec.INT.optionalFieldOf("duration", Integer.valueOf(0)).forGetter(MobEffectInstance.Details::duration),
                                Codec.BOOL.optionalFieldOf("ambient", Boolean.valueOf(false)).forGetter(MobEffectInstance.Details::ambient),
                                Codec.BOOL.optionalFieldOf("show_particles", Boolean.valueOf(true)).forGetter(MobEffectInstance.Details::showParticles),
                                Codec.BOOL.optionalFieldOf("show_icon").forGetter(p_323788_ -> Optional.of(p_323788_.showIcon())),
                                p_323465_.optionalFieldOf("hidden_effect").forGetter(MobEffectInstance.Details::hiddenEffect)
                                // Neo: Add additional serialization logic for custom EffectCure(s)
                                , net.neoforged.neoforge.common.util.NeoForgeExtraCodecs.setOf(net.neoforged.neoforge.common.EffectCure.CODEC).optionalFieldOf("neoforge:cures").forGetter(MobEffectInstance.Details::cures)
                            )
                            .apply(p_324063_, MobEffectInstance.Details::create)
                )
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, MobEffectInstance.Details> STREAM_CODEC = StreamCodec.recursive(
            p_329990_ -> net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs.composite(
                    ByteBufCodecs.VAR_INT,
                    MobEffectInstance.Details::amplifier,
                    ByteBufCodecs.VAR_INT,
                    MobEffectInstance.Details::duration,
                    ByteBufCodecs.BOOL,
                    MobEffectInstance.Details::ambient,
                    ByteBufCodecs.BOOL,
                    MobEffectInstance.Details::showParticles,
                    ByteBufCodecs.BOOL,
                    MobEffectInstance.Details::showIcon,
                    p_329990_.apply(ByteBufCodecs::optional),
                    MobEffectInstance.Details::hiddenEffect,
                    // Neo: Add additional serialization logic for custom EffectCure(s)
                    net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs.connectionAware(
                            ByteBufCodecs.optional(net.neoforged.neoforge.common.EffectCure.STREAM_CODEC.apply(ByteBufCodecs.collection(java.util.HashSet::new))),
                            net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs.uncheckedUnit(Optional.empty())
                    ),
                    MobEffectInstance.Details::cures,
                    MobEffectInstance.Details::new
                )
        );

        private static MobEffectInstance.Details create(
            int p_323657_, int p_324205_, boolean p_324263_, boolean p_324000_, Optional<Boolean> p_323607_, Optional<MobEffectInstance.Details> p_324604_
        ) {
            return new MobEffectInstance.Details(p_323657_, p_324205_, p_324263_, p_324000_, p_323607_.orElse(p_324000_), p_324604_);
        }

        private static MobEffectInstance.Details create(
            int p_323657_, int p_324205_, boolean p_324263_, boolean p_324000_, Optional<Boolean> p_323607_, Optional<MobEffectInstance.Details> p_324604_, Optional<java.util.Set<net.neoforged.neoforge.common.EffectCure>> cures
        ) {
            return new MobEffectInstance.Details(p_323657_, p_324205_, p_324263_, p_324000_, p_323607_.orElse(p_324000_), p_324604_, cures);
        }

        @Deprecated
        Details(int amplifier, int duration, boolean ambient, boolean showParticles, boolean showIcon, Optional<MobEffectInstance.Details> hiddenEffect) {
            this(amplifier, duration, ambient, showParticles, showIcon, hiddenEffect, Optional.empty());
        }

    }
}
