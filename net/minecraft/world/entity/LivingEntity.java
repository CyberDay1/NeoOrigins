package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;

public abstract class LivingEntity extends Entity implements Attackable, net.neoforged.neoforge.common.extensions.ILivingEntityExtension {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_ACTIVE_EFFECTS = "active_effects";
    private static final ResourceLocation SPEED_MODIFIER_POWDER_SNOW_ID = ResourceLocation.withDefaultNamespace("powder_snow");
    private static final ResourceLocation SPRINTING_MODIFIER_ID = ResourceLocation.withDefaultNamespace("sprinting");
    private static final AttributeModifier SPEED_MODIFIER_SPRINTING = new AttributeModifier(
        SPRINTING_MODIFIER_ID, 0.3F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );
    public static final int HAND_SLOTS = 2;
    public static final int ARMOR_SLOTS = 4;
    public static final int EQUIPMENT_SLOT_OFFSET = 98;
    public static final int ARMOR_SLOT_OFFSET = 100;
    public static final int BODY_ARMOR_OFFSET = 105;
    public static final int SWING_DURATION = 6;
    public static final int PLAYER_HURT_EXPERIENCE_TIME = 100;
    private static final int DAMAGE_SOURCE_TIMEOUT = 40;
    public static final double MIN_MOVEMENT_DISTANCE = 0.003;
    public static final double DEFAULT_BASE_GRAVITY = 0.08;
    public static final int DEATH_DURATION = 20;
    private static final int TICKS_PER_ELYTRA_FREE_FALL_EVENT = 10;
    private static final int FREE_FALL_EVENTS_PER_ELYTRA_BREAK = 2;
    public static final int USE_ITEM_INTERVAL = 4;
    public static final float BASE_JUMP_POWER = 0.42F;
    private static final double MAX_LINE_OF_SIGHT_TEST_RANGE = 128.0;
    protected static final int LIVING_ENTITY_FLAG_IS_USING = 1;
    protected static final int LIVING_ENTITY_FLAG_OFF_HAND = 2;
    protected static final int LIVING_ENTITY_FLAG_SPIN_ATTACK = 4;
    protected static final EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_HEALTH_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<List<ParticleOptions>> DATA_EFFECT_PARTICLES = SynchedEntityData.defineId(
        LivingEntity.class, EntityDataSerializers.PARTICLES
    );
    private static final EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ARROW_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEPING_POS_ID = SynchedEntityData.defineId(
        LivingEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS
    );
    private static final int PARTICLE_FREQUENCY_WHEN_INVISIBLE = 15;
    protected static final EntityDimensions SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F).withEyeHeight(0.2F);
    public static final float EXTRA_RENDER_CULLING_SIZE_WITH_BIG_HAT = 0.5F;
    public static final float DEFAULT_BABY_SCALE = 0.5F;
    private static final float ITEM_USE_EFFECT_START_FRACTION = 0.21875F;
    public static final String ATTRIBUTES_FIELD = "attributes";
    private final AttributeMap attributes;
    private final CombatTracker combatTracker = new CombatTracker(this);
    private final Map<Holder<MobEffect>, MobEffectInstance> activeEffects = Maps.newHashMap();
    private final NonNullList<ItemStack> lastHandItemStacks = NonNullList.withSize(2, ItemStack.EMPTY);
    private final NonNullList<ItemStack> lastArmorItemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
    private ItemStack lastBodyItemStack = ItemStack.EMPTY;
    public boolean swinging;
    private boolean discardFriction = false;
    public InteractionHand swingingArm;
    public int swingTime;
    public int removeArrowTime;
    public int removeStingerTime;
    public int hurtTime;
    public int hurtDuration;
    public int deathTime;
    public float oAttackAnim;
    public float attackAnim;
    protected int attackStrengthTicker;
    public final WalkAnimationState walkAnimation = new WalkAnimationState();
    public final int invulnerableDuration = 20;
    public final float timeOffs;
    public final float rotA;
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    @Nullable
    protected Player lastHurtByPlayer;
    protected int lastHurtByPlayerTime;
    protected boolean dead;
    protected int noActionTime;
    protected float oRun;
    protected float run;
    protected float animStep;
    protected float animStepO;
    protected float rotOffs;
    protected int deathScore;
    protected float lastHurt;
    protected boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    protected int lerpSteps;
    protected double lerpX;
    protected double lerpY;
    protected double lerpZ;
    protected double lerpYRot;
    protected double lerpXRot;
    protected double lerpYHeadRot;
    protected int lerpHeadSteps;
    private boolean effectsDirty = true;
    @Nullable
    private LivingEntity lastHurtByMob;
    private int lastHurtByMobTimestamp;
    @Nullable
    private LivingEntity lastHurtMob;
    private int lastHurtMobTimestamp;
    private float speed;
    private int noJumpDelay;
    private float absorptionAmount;
    protected ItemStack useItem = ItemStack.EMPTY;
    protected int useItemRemaining;
    protected int fallFlyTicks;
    private BlockPos lastPos;
    private Optional<BlockPos> lastClimbablePos = Optional.empty();
    @Nullable
    private DamageSource lastDamageSource;
    private long lastDamageStamp;
    protected int autoSpinAttackTicks;
    protected float autoSpinAttackDmg;
    @Nullable
    protected ItemStack autoSpinAttackItemStack;
    private float swimAmount;
    private float swimAmountO;
    protected Brain<?> brain;
    private boolean skipDropExperience;
    private final Reference2ObjectMap<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEnchantments = new Reference2ObjectArrayMap<>();
    protected float appliedScale = 1.0F;
    /**
     * This field stores information about damage dealt to this entity.
     * a new {@link net.neoforged.neoforge.common.damagesource.DamageContainer} is instantiated
     * via {@link #hurt(DamageSource, float)} after invulnerability checks, and is removed from
     * the stack before the method's return.
    **/
    @Nullable
    protected java.util.Stack<net.neoforged.neoforge.common.damagesource.DamageContainer> damageContainers = new java.util.Stack<>();

    protected LivingEntity(EntityType<? extends LivingEntity> p_20966_, Level p_20967_) {
        super(p_20966_, p_20967_);
        this.attributes = new AttributeMap(DefaultAttributes.getSupplier(p_20966_));
        this.setHealth(this.getMaxHealth());
        this.blocksBuilding = true;
        this.rotA = (float)((Math.random() + 1.0) * 0.01F);
        this.reapplyPosition();
        this.timeOffs = (float)Math.random() * 12398.0F;
        this.setYRot((float)(Math.random() * (float) (Math.PI * 2)));
        this.yHeadRot = this.getYRot();
        NbtOps nbtops = NbtOps.INSTANCE;
        this.brain = this.makeBrain(new Dynamic<>(nbtops, nbtops.createMap(ImmutableMap.of(nbtops.createString("memories"), nbtops.emptyMap()))));
    }

    public Brain<?> getBrain() {
        return this.brain;
    }

    protected Brain.Provider<?> brainProvider() {
        return Brain.provider(ImmutableList.of(), ImmutableList.of());
    }

    protected Brain<?> makeBrain(Dynamic<?> p_21069_) {
        return this.brainProvider().makeBrain(p_21069_);
    }

    @Override
    public void kill() {
        this.hurt(this.damageSources().genericKill(), Float.MAX_VALUE);
    }

    public boolean canAttackType(EntityType<?> p_21032_) {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_326178_) {
        p_326178_.define(DATA_LIVING_ENTITY_FLAGS, (byte)0);
        p_326178_.define(DATA_EFFECT_PARTICLES, List.of());
        p_326178_.define(DATA_EFFECT_AMBIENCE_ID, false);
        p_326178_.define(DATA_ARROW_COUNT_ID, 0);
        p_326178_.define(DATA_STINGER_COUNT_ID, 0);
        p_326178_.define(DATA_HEALTH_ID, 1.0F);
        p_326178_.define(SLEEPING_POS_ID, Optional.empty());
    }

    public static AttributeSupplier.Builder createLivingAttributes() {
        return AttributeSupplier.builder()
            .add(Attributes.MAX_HEALTH)
            .add(Attributes.KNOCKBACK_RESISTANCE)
            .add(Attributes.MOVEMENT_SPEED)
            .add(Attributes.ARMOR)
            .add(Attributes.ARMOR_TOUGHNESS)
            .add(Attributes.MAX_ABSORPTION)
            .add(Attributes.STEP_HEIGHT)
            .add(Attributes.SCALE)
            .add(Attributes.GRAVITY)
            .add(Attributes.SAFE_FALL_DISTANCE)
            .add(Attributes.FALL_DAMAGE_MULTIPLIER)
            .add(Attributes.JUMP_STRENGTH)
            .add(Attributes.OXYGEN_BONUS)
            .add(Attributes.BURNING_TIME)
            .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
            .add(Attributes.WATER_MOVEMENT_EFFICIENCY)
            .add(Attributes.MOVEMENT_EFFICIENCY)
            .add(Attributes.ATTACK_KNOCKBACK)
            .add(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED)
            .add(net.neoforged.neoforge.common.NeoForgeMod.NAMETAG_DISTANCE);
    }

    @Override
    protected void checkFallDamage(double p_20990_, boolean p_20991_, BlockState p_20992_, BlockPos p_20993_) {
        if (!this.isInWater()) {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        if (this.level() instanceof ServerLevel serverlevel && p_20991_ && this.fallDistance > 0.0F) {
            this.onChangedBlock(serverlevel, p_20993_);
            double d7 = this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
            if ((double)this.fallDistance > d7 && !p_20992_.isAir()) {
                double d0 = this.getX();
                double d1 = this.getY();
                double d2 = this.getZ();
                BlockPos blockpos = this.blockPosition();
                if (p_20993_.getX() != blockpos.getX() || p_20993_.getZ() != blockpos.getZ()) {
                    double d3 = d0 - (double)p_20993_.getX() - 0.5;
                    double d5 = d2 - (double)p_20993_.getZ() - 0.5;
                    double d6 = Math.max(Math.abs(d3), Math.abs(d5));
                    d0 = (double)p_20993_.getX() + 0.5 + d3 / d6 * 0.5;
                    d2 = (double)p_20993_.getZ() + 0.5 + d5 / d6 * 0.5;
                }

                float f = (float)Mth.ceil((double)this.fallDistance - d7);
                double d4 = Math.min((double)(0.2F + f / 15.0F), 2.5);
                int i = (int)(150.0 * d4);
                if (!p_20992_.addLandingEffects((ServerLevel) this.level(), p_20993_, p_20992_, this, i))
                ((ServerLevel)this.level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p_20992_).setPos(p_20993_), d0, d1, d2, i, 0.0, 0.0, 0.0, 0.15F);
            }
        }

        super.checkFallDamage(p_20990_, p_20991_, p_20992_, p_20993_);
        if (p_20991_) {
            this.lastClimbablePos = Optional.empty();
        }
    }

    @Deprecated //FORGE: Use canDrownInFluidType instead
    public final boolean canBreatheUnderwater() {
        return this.getType().is(EntityTypeTags.CAN_BREATHE_UNDER_WATER);
    }

    public float getSwimAmount(float p_20999_) {
        return Mth.lerp(p_20999_, this.swimAmountO, this.swimAmount);
    }

    public boolean hasLandedInLiquid() {
        return this.getDeltaMovement().y() < 1.0E-5F && this.isInLiquid();
    }

    @Override
    public void baseTick() {
        this.oAttackAnim = this.attackAnim;
        if (this.firstTick) {
            this.getSleepingPos().ifPresent(this::setPosToBed);
        }

        if (this.level() instanceof ServerLevel serverlevel) {
            EnchantmentHelper.tickEffects(serverlevel, this);
        }

        super.baseTick();
        this.level().getProfiler().push("livingEntityBaseTick");
        if (this.fireImmune() || this.level().isClientSide) {
            this.clearFire();
        }

        if (this.isAlive()) {
            boolean flag = this instanceof Player;
            if (!this.level().isClientSide) {
                if (this.isInWall()) {
                    this.hurt(this.damageSources().inWall(), 1.0F);
                } else if (flag && !this.level().getWorldBorder().isWithinBounds(this.getBoundingBox())) {
                    double d4 = this.level().getWorldBorder().getDistanceToBorder(this) + this.level().getWorldBorder().getDamageSafeZone();
                    if (d4 < 0.0) {
                        double d0 = this.level().getWorldBorder().getDamagePerBlock();
                        if (d0 > 0.0) {
                            this.hurt(this.damageSources().outOfBorder(), (float)Math.max(1, Mth.floor(-d4 * d0)));
                        }
                    }
                }
            }

            int airSupply = this.getAirSupply();
            net.neoforged.neoforge.common.CommonHooks.onLivingBreathe(this, airSupply - decreaseAirSupply(airSupply), increaseAirSupply(airSupply) - airSupply);
            if (false) // Forge: Handled in ForgeHooks#onLivingBreathe(LivingEntity, int, int)
            if (this.isEyeInFluid(FluidTags.WATER)
                && !this.level().getBlockState(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ())).is(Blocks.BUBBLE_COLUMN)) {
                boolean flag1 = !this.canBreatheUnderwater()
                    && !MobEffectUtil.hasWaterBreathing(this)
                    && (!flag || !((Player)this).getAbilities().invulnerable);
                if (flag1) {
                    this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));
                    if (this.getAirSupply() == -20) {
                        this.setAirSupply(0);
                        Vec3 vec3 = this.getDeltaMovement();

                        for (int i = 0; i < 8; i++) {
                            double d1 = this.random.nextDouble() - this.random.nextDouble();
                            double d2 = this.random.nextDouble() - this.random.nextDouble();
                            double d3 = this.random.nextDouble() - this.random.nextDouble();
                            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() + d1, this.getY() + d2, this.getZ() + d3, vec3.x, vec3.y, vec3.z);
                        }

                        this.hurt(this.damageSources().drown(), 2.0F);
                    }
                }

                if (!this.level().isClientSide && this.isPassenger() && this.getVehicle() != null && this.getVehicle().dismountsUnderwater()) {
                    this.stopRiding();
                }
            } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
            }

            if (this.level() instanceof ServerLevel serverlevel1) {
                BlockPos blockpos = this.blockPosition();
                if (!Objects.equal(this.lastPos, blockpos)) {
                    this.lastPos = blockpos;
                    this.onChangedBlock(serverlevel1, blockpos);
                }
            }
        }

        if (this.isAlive() && (this.isInWaterRainOrBubble() || this.isInPowderSnow || this.isInFluidType((fluidType, height) -> this.canFluidExtinguish(fluidType)))) {
            this.extinguishFire();
        }

        if (this.hurtTime > 0) {
            this.hurtTime--;
        }

        if (this.invulnerableTime > 0 && !(this instanceof ServerPlayer)) {
            this.invulnerableTime--;
        }

        if (this.isDeadOrDying() && this.level().shouldTickDeath(this)) {
            this.tickDeath();
        }

        if (this.lastHurtByPlayerTime > 0) {
            this.lastHurtByPlayerTime--;
        } else {
            this.lastHurtByPlayer = null;
        }

        if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
            this.lastHurtMob = null;
        }

        if (this.lastHurtByMob != null) {
            if (!this.lastHurtByMob.isAlive()) {
                this.setLastHurtByMob(null);
            } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
                this.setLastHurtByMob(null);
            }
        }

        this.tickEffects();
        this.animStepO = this.animStep;
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.level().getProfiler().pop();
    }

    @Override
    protected float getBlockSpeedFactor() {
        return Mth.lerp((float)this.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY), super.getBlockSpeedFactor(), 1.0F);
    }

    protected void removeFrost() {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attributeinstance != null) {
            if (attributeinstance.getModifier(SPEED_MODIFIER_POWDER_SNOW_ID) != null) {
                attributeinstance.removeModifier(SPEED_MODIFIER_POWDER_SNOW_ID);
            }
        }
    }

    protected void tryAddFrost() {
        if (!this.getBlockStateOnLegacy().isAir()) {
            int i = this.getTicksFrozen();
            if (i > 0) {
                AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attributeinstance == null) {
                    return;
                }

                float f = -0.05F * this.getPercentFrozen();
                attributeinstance.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_POWDER_SNOW_ID, (double)f, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    protected void onChangedBlock(ServerLevel p_345126_, BlockPos p_21175_) {
        EnchantmentHelper.runLocationChangedEffects(p_345126_, this);
    }

    public boolean isBaby() {
        return false;
    }

    public float getAgeScale() {
        return this.isBaby() ? 0.5F : 1.0F;
    }

    public float getScale() {
        AttributeMap attributemap = this.getAttributes();
        return attributemap == null ? 1.0F : this.sanitizeScale((float)attributemap.getValue(Attributes.SCALE));
    }

    protected float sanitizeScale(float p_320290_) {
        return p_320290_;
    }

    protected boolean isAffectedByFluids() {
        return true;
    }

    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= 20 && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte)60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    public boolean shouldDropExperience() {
        return !this.isBaby();
    }

    protected boolean shouldDropLoot() {
        return !this.isBaby();
    }

    protected int decreaseAirSupply(int p_21303_) {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.OXYGEN_BONUS);
        double d0;
        if (attributeinstance != null) {
            d0 = attributeinstance.getValue();
        } else {
            d0 = 0.0;
        }

        return d0 > 0.0 && this.random.nextDouble() >= 1.0 / (d0 + 1.0) ? p_21303_ : p_21303_ - 1;
    }

    protected int increaseAirSupply(int p_21307_) {
        return Math.min(p_21307_ + 4, this.getMaxAirSupply());
    }

    public final int getExperienceReward(ServerLevel p_345212_, @Nullable Entity p_345512_) {
        return EnchantmentHelper.processMobExperience(p_345212_, p_345512_, this, this.getBaseExperienceReward());
    }

    protected int getBaseExperienceReward() {
        return 0;
    }

    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    @Nullable
    public LivingEntity getLastHurtByMob() {
        return this.lastHurtByMob;
    }

    @Override
    public LivingEntity getLastAttacker() {
        return this.getLastHurtByMob();
    }

    public int getLastHurtByMobTimestamp() {
        return this.lastHurtByMobTimestamp;
    }

    public void setLastHurtByPlayer(@Nullable Player p_21248_) {
        this.lastHurtByPlayer = p_21248_;
        this.lastHurtByPlayerTime = this.tickCount;
    }

    public void setLastHurtByMob(@Nullable LivingEntity p_21039_) {
        this.lastHurtByMob = p_21039_;
        this.lastHurtByMobTimestamp = this.tickCount;
    }

    @Nullable
    public LivingEntity getLastHurtMob() {
        return this.lastHurtMob;
    }

    public int getLastHurtMobTimestamp() {
        return this.lastHurtMobTimestamp;
    }

    public void setLastHurtMob(Entity p_21336_) {
        if (p_21336_ instanceof LivingEntity) {
            this.lastHurtMob = (LivingEntity)p_21336_;
        } else {
            this.lastHurtMob = null;
        }

        this.lastHurtMobTimestamp = this.tickCount;
    }

    public int getNoActionTime() {
        return this.noActionTime;
    }

    public void setNoActionTime(int p_21311_) {
        this.noActionTime = p_21311_;
    }

    public boolean shouldDiscardFriction() {
        return this.discardFriction;
    }

    public void setDiscardFriction(boolean p_147245_) {
        this.discardFriction = p_147245_;
    }

    protected boolean doesEmitEquipEvent(EquipmentSlot p_217035_) {
        return true;
    }

    public void onEquipItem(EquipmentSlot p_238393_, ItemStack p_238394_, ItemStack p_238395_) {
        boolean flag = p_238395_.isEmpty() && p_238394_.isEmpty();
        if (!flag && !ItemStack.isSameItemSameComponents(p_238394_, p_238395_) && !this.firstTick) {
            Equipable equipable = Equipable.get(p_238395_);
            if (!this.level().isClientSide() && !this.isSpectator()) {
                if (!this.isSilent() && equipable != null && equipable.getEquipmentSlot() == p_238393_) {
                    this.level()
                        .playSeededSound(
                            null, this.getX(), this.getY(), this.getZ(), equipable.getEquipSound(), this.getSoundSource(), 1.0F, 1.0F, this.random.nextLong()
                        );
                }

                if (this.doesEmitEquipEvent(p_238393_)) {
                    this.gameEvent(equipable != null ? GameEvent.EQUIP : GameEvent.UNEQUIP);
                }
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason p_276115_) {
        if (p_276115_ == Entity.RemovalReason.KILLED || p_276115_ == Entity.RemovalReason.DISCARDED) {
            this.triggerOnDeathMobEffects(p_276115_);
        }

        super.remove(p_276115_);
        this.brain.clearMemories();
    }

    protected void triggerOnDeathMobEffects(Entity.RemovalReason p_350496_) {
        for (MobEffectInstance mobeffectinstance : this.getActiveEffects()) {
            mobeffectinstance.onMobRemoved(this, p_350496_);
        }

        this.activeEffects.clear();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_21145_) {
        p_21145_.putFloat("Health", this.getHealth());
        p_21145_.putShort("HurtTime", (short)this.hurtTime);
        p_21145_.putInt("HurtByTimestamp", this.lastHurtByMobTimestamp);
        p_21145_.putShort("DeathTime", (short)this.deathTime);
        p_21145_.putFloat("AbsorptionAmount", this.getAbsorptionAmount());
        p_21145_.put("attributes", this.getAttributes().save());
        if (!this.activeEffects.isEmpty()) {
            ListTag listtag = new ListTag();

            for (MobEffectInstance mobeffectinstance : this.activeEffects.values()) {
                listtag.add(mobeffectinstance.save());
            }

            p_21145_.put("active_effects", listtag);
        }

        p_21145_.putBoolean("FallFlying", this.isFallFlying());
        this.getSleepingPos().ifPresent(p_21099_ -> {
            p_21145_.putInt("SleepingX", p_21099_.getX());
            p_21145_.putInt("SleepingY", p_21099_.getY());
            p_21145_.putInt("SleepingZ", p_21099_.getZ());
        });
        DataResult<Tag> dataresult = this.brain.serializeStart(NbtOps.INSTANCE);
        dataresult.resultOrPartial(LOGGER::error).ifPresent(p_21102_ -> p_21145_.put("Brain", p_21102_));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_21096_) {
        this.internalSetAbsorptionAmount(p_21096_.getFloat("AbsorptionAmount"));
        if (p_21096_.contains("attributes", 9) && this.level() != null && !this.level().isClientSide) {
            this.getAttributes().load(p_21096_.getList("attributes", 10));
        }

        if (p_21096_.contains("active_effects", 9)) {
            ListTag listtag = p_21096_.getList("active_effects", 10);

            for (int i = 0; i < listtag.size(); i++) {
                CompoundTag compoundtag = listtag.getCompound(i);
                MobEffectInstance mobeffectinstance = MobEffectInstance.load(compoundtag);
                if (mobeffectinstance != null) {
                    this.activeEffects.put(mobeffectinstance.getEffect(), mobeffectinstance);
                }
            }
        }

        if (p_21096_.contains("Health", 99)) {
            this.setHealth(p_21096_.getFloat("Health"));
        }

        this.hurtTime = p_21096_.getShort("HurtTime");
        this.deathTime = p_21096_.getShort("DeathTime");
        this.lastHurtByMobTimestamp = p_21096_.getInt("HurtByTimestamp");
        if (p_21096_.contains("Team", 8)) {
            String s = p_21096_.getString("Team");
            Scoreboard scoreboard = this.level().getScoreboard();
            PlayerTeam playerteam = scoreboard.getPlayerTeam(s);
            boolean flag = playerteam != null && scoreboard.addPlayerToTeam(this.getStringUUID(), playerteam);
            if (!flag) {
                LOGGER.warn("Unable to add mob to team \"{}\" (that team probably doesn't exist)", s);
            }
        }

        if (p_21096_.getBoolean("FallFlying")) {
            this.setSharedFlag(7, true);
        }

        if (p_21096_.contains("SleepingX", 99) && p_21096_.contains("SleepingY", 99) && p_21096_.contains("SleepingZ", 99)) {
            BlockPos blockpos = new BlockPos(p_21096_.getInt("SleepingX"), p_21096_.getInt("SleepingY"), p_21096_.getInt("SleepingZ"));
            this.setSleepingPos(blockpos);
            this.entityData.set(DATA_POSE, Pose.SLEEPING);
            if (!this.firstTick) {
                this.setPosToBed(blockpos);
            }
        }

        if (p_21096_.contains("Brain", 10)) {
            this.brain = this.makeBrain(new Dynamic<>(NbtOps.INSTANCE, p_21096_.get("Brain")));
        }
    }

    protected void tickEffects() {
        Iterator<Holder<MobEffect>> iterator = this.activeEffects.keySet().iterator();

        try {
            while (iterator.hasNext()) {
                Holder<MobEffect> holder = iterator.next();
                MobEffectInstance mobeffectinstance = this.activeEffects.get(holder);
                if (!mobeffectinstance.tick(this, () -> this.onEffectUpdated(mobeffectinstance, true, null))) {
                    if (!this.level().isClientSide && !net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired(this, mobeffectinstance)).isCanceled()) {
                        iterator.remove();
                        this.onEffectRemoved(mobeffectinstance);
                    }
                } else if (mobeffectinstance.getDuration() % 600 == 0) {
                    this.onEffectUpdated(mobeffectinstance, false, null);
                }
            }
        } catch (ConcurrentModificationException concurrentmodificationexception) {
        }

        if (this.effectsDirty) {
            if (!this.level().isClientSide) {
                this.updateInvisibilityStatus();
                this.updateGlowingStatus();
            }

            this.effectsDirty = false;
        }

        List<ParticleOptions> list = this.entityData.get(DATA_EFFECT_PARTICLES);
        if (!list.isEmpty()) {
            boolean flag = this.entityData.get(DATA_EFFECT_AMBIENCE_ID);
            int i = this.isInvisible() ? 15 : 4;
            int j = flag ? 5 : 1;
            if (this.random.nextInt(i * j) == 0) {
                this.level().addParticle(Util.getRandom(list, this.random), this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 1.0, 1.0, 1.0);
            }
        }
    }

    protected void updateInvisibilityStatus() {
        if (this.activeEffects.isEmpty()) {
            this.removeEffectParticles();
            this.setInvisible(false);
        } else {
            this.setInvisible(this.hasEffect(MobEffects.INVISIBILITY));
            this.updateSynchronizedMobEffectParticles();
        }
    }

    private void updateSynchronizedMobEffectParticles() {
        List<ParticleOptions> list = this.activeEffects
            .values()
            .stream()
            .map(effect -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent(this, effect)))
            .filter(net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent::isVisible)
            .map(net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent::getParticleOptions)
            .toList();
        this.entityData.set(DATA_EFFECT_PARTICLES, list);
        this.entityData.set(DATA_EFFECT_AMBIENCE_ID, areAllEffectsAmbient(this.activeEffects.values()));
    }

    private void updateGlowingStatus() {
        boolean flag = this.isCurrentlyGlowing();
        if (this.getSharedFlag(6) != flag) {
            this.setSharedFlag(6, flag);
        }
    }

    public double getVisibilityPercent(@Nullable Entity p_20969_) {
        double d0 = 1.0;
        if (this.isDiscrete()) {
            d0 *= 0.8;
        }

        if (this.isInvisible()) {
            float f = this.getArmorCoverPercentage();
            if (f < 0.1F) {
                f = 0.1F;
            }

            d0 *= 0.7 * (double)f;
        }

        if (p_20969_ != null) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
            EntityType<?> entitytype = p_20969_.getType();
            if (entitytype == EntityType.SKELETON && itemstack.is(Items.SKELETON_SKULL)
                || entitytype == EntityType.ZOMBIE && itemstack.is(Items.ZOMBIE_HEAD)
                || entitytype == EntityType.PIGLIN && itemstack.is(Items.PIGLIN_HEAD)
                || entitytype == EntityType.PIGLIN_BRUTE && itemstack.is(Items.PIGLIN_HEAD)
                || entitytype == EntityType.CREEPER && itemstack.is(Items.CREEPER_HEAD)) {
                d0 *= 0.5;
            }
        }

        d0 = net.neoforged.neoforge.common.CommonHooks.getEntityVisibilityMultiplier(this, p_20969_, d0);
        return d0;
    }

    public boolean canAttack(LivingEntity p_21171_) {
        return p_21171_ instanceof Player && this.level().getDifficulty() == Difficulty.PEACEFUL ? false : p_21171_.canBeSeenAsEnemy();
    }

    public boolean canAttack(LivingEntity p_21041_, TargetingConditions p_21042_) {
        return p_21042_.test(this, p_21041_);
    }

    public boolean canBeSeenAsEnemy() {
        return !this.isInvulnerable() && this.canBeSeenByAnyone();
    }

    public boolean canBeSeenByAnyone() {
        return !this.isSpectator() && this.isAlive();
    }

    public static boolean areAllEffectsAmbient(Collection<MobEffectInstance> p_21180_) {
        for (MobEffectInstance mobeffectinstance : p_21180_) {
            if (mobeffectinstance.isVisible() && !mobeffectinstance.isAmbient()) {
                return false;
            }
        }

        return true;
    }

    protected void removeEffectParticles() {
        this.entityData.set(DATA_EFFECT_PARTICLES, List.of());
    }

    public boolean removeAllEffects() {
        if (this.level().isClientSide) {
            return false;
        } else {
            Iterator<MobEffectInstance> iterator = this.activeEffects.values().iterator();

            boolean flag;
            for (flag = false; iterator.hasNext(); flag = true) {
                MobEffectInstance effect = iterator.next();
                if(net.neoforged.neoforge.event.EventHooks.onEffectRemoved(this, effect, null)) continue;
                this.onEffectRemoved(effect);
                iterator.remove();
            }

            return flag;
        }
    }

    public Collection<MobEffectInstance> getActiveEffects() {
        return this.activeEffects.values();
    }

    public Map<Holder<MobEffect>, MobEffectInstance> getActiveEffectsMap() {
        return this.activeEffects;
    }

    public boolean hasEffect(Holder<MobEffect> p_316430_) {
        return this.activeEffects.containsKey(p_316430_);
    }

    @Nullable
    public MobEffectInstance getEffect(Holder<MobEffect> p_316375_) {
        return this.activeEffects.get(p_316375_);
    }

    public final boolean addEffect(MobEffectInstance p_21165_) {
        return this.addEffect(p_21165_, null);
    }

    public boolean addEffect(MobEffectInstance p_147208_, @Nullable Entity p_147209_) {
        if (!net.neoforged.neoforge.common.CommonHooks.canMobEffectBeApplied(this, p_147208_, p_147209_)) {
            return false;
        } else {
            MobEffectInstance mobeffectinstance = this.activeEffects.get(p_147208_.getEffect());
            boolean flag = false;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added(this, mobeffectinstance, p_147208_, p_147209_));
            if (mobeffectinstance == null) {
                this.activeEffects.put(p_147208_.getEffect(), p_147208_);
                this.onEffectAdded(p_147208_, p_147209_);
                flag = true;
                p_147208_.onEffectAdded(this);
            } else if (mobeffectinstance.update(p_147208_)) {
                this.onEffectUpdated(mobeffectinstance, true, p_147209_);
                flag = true;
            }

            p_147208_.onEffectStarted(this);
            return flag;
        }
    }

    /**
     * Neo: Override-Only. Call via {@link net.neoforged.neoforge.common.CommonHooks#canMobEffectBeApplied(LivingEntity, MobEffectInstance,Entity)}
     *
     * @param p_21197_ A mob effect instance
     * @return If the mob effect instance can be applied to this entity
     */
    @Deprecated
    @org.jetbrains.annotations.ApiStatus.OverrideOnly
    public boolean canBeAffected(MobEffectInstance p_21197_) {
        if (this.getType().is(EntityTypeTags.IMMUNE_TO_INFESTED)) {
            return !p_21197_.is(MobEffects.INFESTED);
        } else if (this.getType().is(EntityTypeTags.IMMUNE_TO_OOZING)) {
            return !p_21197_.is(MobEffects.OOZING);
        } else {
            return !this.getType().is(EntityTypeTags.IGNORES_POISON_AND_REGEN)
                ? true
                : !p_21197_.is(MobEffects.REGENERATION) && !p_21197_.is(MobEffects.POISON);
        }
    }

    public void forceAddEffect(MobEffectInstance p_147216_, @Nullable Entity p_147217_) {
        if (net.neoforged.neoforge.common.CommonHooks.canMobEffectBeApplied(this, p_147216_, p_147217_)) {
            MobEffectInstance mobeffectinstance = this.activeEffects.put(p_147216_.getEffect(), p_147216_);
            if (mobeffectinstance == null) {
                this.onEffectAdded(p_147216_, p_147217_);
            } else {
                p_147216_.copyBlendState(mobeffectinstance);
                this.onEffectUpdated(p_147216_, true, p_147217_);
            }
        }
    }

    public boolean isInvertedHealAndHarm() {
        return this.getType().is(EntityTypeTags.INVERTED_HEALING_AND_HARM);
    }

    @Nullable
    public MobEffectInstance removeEffectNoUpdate(Holder<MobEffect> p_316233_) {
        return this.activeEffects.remove(p_316233_);
    }

    public boolean removeEffect(Holder<MobEffect> p_316570_) {
        if (net.neoforged.neoforge.event.EventHooks.onEffectRemoved(this, p_316570_, null)) return false;
        MobEffectInstance mobeffectinstance = this.removeEffectNoUpdate(p_316570_);
        if (mobeffectinstance != null) {
            this.onEffectRemoved(mobeffectinstance);
            return true;
        } else {
            return false;
        }
    }

    protected void onEffectAdded(MobEffectInstance p_147190_, @Nullable Entity p_147191_) {
        this.effectsDirty = true;
        if (!this.level().isClientSide) {
            p_147190_.getEffect().value().addAttributeModifiers(this.getAttributes(), p_147190_.getAmplifier());
            this.sendEffectToPassengers(p_147190_);
        }
    }

    public void sendEffectToPassengers(MobEffectInstance p_289695_) {
        for (Entity entity : this.getPassengers()) {
            if (entity instanceof ServerPlayer serverplayer) {
                serverplayer.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), p_289695_, false));
            }
        }
    }

    protected void onEffectUpdated(MobEffectInstance p_147192_, boolean p_147193_, @Nullable Entity p_147194_) {
        this.effectsDirty = true;
        if (p_147193_ && !this.level().isClientSide) {
            MobEffect mobeffect = p_147192_.getEffect().value();
            mobeffect.removeAttributeModifiers(this.getAttributes());
            mobeffect.addAttributeModifiers(this.getAttributes(), p_147192_.getAmplifier());
            this.refreshDirtyAttributes();
        }

        if (!this.level().isClientSide) {
            this.sendEffectToPassengers(p_147192_);
        }
    }

    protected void onEffectRemoved(MobEffectInstance p_21126_) {
        this.effectsDirty = true;
        if (!this.level().isClientSide) {
            p_21126_.getEffect().value().removeAttributeModifiers(this.getAttributes());
            this.refreshDirtyAttributes();

            for (Entity entity : this.getPassengers()) {
                if (entity instanceof ServerPlayer serverplayer) {
                    serverplayer.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), p_21126_.getEffect()));
                }
            }
        }
    }

    private void refreshDirtyAttributes() {
        Set<AttributeInstance> set = this.getAttributes().getAttributesToUpdate();

        for (AttributeInstance attributeinstance : set) {
            this.onAttributeUpdated(attributeinstance.getAttribute());
        }

        set.clear();
    }

    private void onAttributeUpdated(Holder<Attribute> p_316778_) {
        if (p_316778_.is(Attributes.MAX_HEALTH)) {
            float f = this.getMaxHealth();
            if (this.getHealth() > f) {
                this.setHealth(f);
            }
        } else if (p_316778_.is(Attributes.MAX_ABSORPTION)) {
            float f1 = this.getMaxAbsorption();
            if (this.getAbsorptionAmount() > f1) {
                this.setAbsorptionAmount(f1);
            }
        }
    }

    public void heal(float p_21116_) {
        p_21116_ = net.neoforged.neoforge.event.EventHooks.onLivingHeal(this, p_21116_);
        if (p_21116_ <= 0) return;
        float f = this.getHealth();
        if (f > 0.0F) {
            this.setHealth(f + p_21116_);
        }
    }

    public float getHealth() {
        return this.entityData.get(DATA_HEALTH_ID);
    }

    public void setHealth(float p_21154_) {
        this.entityData.set(DATA_HEALTH_ID, Mth.clamp(p_21154_, 0.0F, this.getMaxHealth()));
    }

    public boolean isDeadOrDying() {
        return this.getHealth() <= 0.0F;
    }

    @Override
    public boolean hurt(DamageSource p_21016_, float p_21017_) {
        if (this.isInvulnerableTo(p_21016_)) {
            return false;
        } else if (this.level().isClientSide) {
            return false;
        } else if (this.isDeadOrDying()) {
            return false;
        } else if (p_21016_.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else {
            this.damageContainers.push(new net.neoforged.neoforge.common.damagesource.DamageContainer(p_21016_, p_21017_));
            if (net.neoforged.neoforge.common.CommonHooks.onEntityIncomingDamage(this, this.damageContainers.peek())) return false;
            if (this.isSleeping() && !this.level().isClientSide) {
                this.stopSleeping();
            }

            this.noActionTime = 0;
            p_21017_ = this.damageContainers.peek().getNewDamage(); //Neo: enforce damage container as source of truth for damage amount
            float f = p_21017_;
            boolean flag = false;
            float f1 = 0.0F;
            net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent ev;
            if (p_21017_ > 0.0F && (ev = net.neoforged.neoforge.common.CommonHooks.onDamageBlock(this, this.damageContainers.peek(), this.isDamageSourceBlocked(p_21016_))).getBlocked()) {
                this.damageContainers.peek().setBlockedDamage(ev);
                if(ev.shieldDamage() > 0) {
                    this.hurtCurrentlyUsedShield(ev.shieldDamage());
                }
                f1 = ev.getBlockedDamage();
                p_21017_ = ev.getDamageContainer().getNewDamage();
                if (!p_21016_.is(DamageTypeTags.IS_PROJECTILE) && p_21016_.getDirectEntity() instanceof LivingEntity livingentity) {
                    this.blockUsingShield(livingentity);
                }

                flag = p_21017_ <= 0;
            }

            if (p_21016_.is(DamageTypeTags.IS_FREEZING) && this.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
                p_21017_ *= 5.0F;
            }

            if (p_21016_.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.hurtHelmet(p_21016_, p_21017_);
                p_21017_ *= 0.75F;
            }

            this.damageContainers.peek().setNewDamage(p_21017_); //update container with vanilla changes
            this.walkAnimation.setSpeed(1.5F);
            boolean flag1 = true;
            if ((float)this.invulnerableTime > 10.0F && !p_21016_.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
                if (p_21017_ <= this.lastHurt) {
                    this.damageContainers.pop();
                    return false;
                }

                this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.INVULNERABILITY, this.lastHurt);
                this.actuallyHurt(p_21016_, p_21017_ - this.lastHurt);
                this.lastHurt = p_21017_;
                flag1 = false;
            } else {
                this.lastHurt = p_21017_;
                this.invulnerableTime = this.damageContainers.peek().getPostAttackInvulnerabilityTicks();
                this.actuallyHurt(p_21016_, p_21017_);
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }

            p_21017_ = this.damageContainers.peek().getNewDamage(); //update local with container value
            Entity entity = p_21016_.getEntity();
            if (entity != null) {
                if (entity instanceof LivingEntity livingentity1
                    && !p_21016_.is(DamageTypeTags.NO_ANGER)
                    && (!p_21016_.is(DamageTypes.WIND_CHARGE) || !this.getType().is(EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE))) {
                    this.setLastHurtByMob(livingentity1);
                }

                if (entity instanceof Player player1) {
                    this.lastHurtByPlayerTime = 100;
                    this.lastHurtByPlayer = player1;
                } else if (entity instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame()) {
                    this.lastHurtByPlayerTime = 100;
                    if (tamableAnimal.getOwner() instanceof Player player) {
                        this.lastHurtByPlayer = player;
                    } else {
                        this.lastHurtByPlayer = null;
                    }
                }
            }

            if (flag1) {
                if (flag) {
                    this.level().broadcastEntityEvent(this, (byte)29);
                } else {
                    this.level().broadcastDamageEvent(this, p_21016_);
                }

                if (!p_21016_.is(DamageTypeTags.NO_IMPACT) && (!flag || p_21017_ > 0.0F)) {
                    this.markHurt();
                }

                if (!p_21016_.is(DamageTypeTags.NO_KNOCKBACK)) {
                    double d0 = 0.0;
                    double d1 = 0.0;
                    if (p_21016_.getDirectEntity() instanceof Projectile projectile) {
                        DoubleDoubleImmutablePair doubledoubleimmutablepair = projectile.calculateHorizontalHurtKnockbackDirection(this, p_21016_);
                        d0 = -doubledoubleimmutablepair.leftDouble();
                        d1 = -doubledoubleimmutablepair.rightDouble();
                    } else if (p_21016_.getSourcePosition() != null) {
                        d0 = p_21016_.getSourcePosition().x() - this.getX();
                        d1 = p_21016_.getSourcePosition().z() - this.getZ();
                    }

                    this.knockback(0.4F, d0, d1);
                    if (!flag) {
                        this.indicateDamage(d0, d1);
                    }
                }
            }

            if (this.isDeadOrDying()) {
                if (!this.checkTotemDeathProtection(p_21016_)) {
                    if (flag1) {
                        this.makeSound(this.getDeathSound());
                    }

                    this.die(p_21016_);
                }
            } else if (flag1) {
                this.playHurtSound(p_21016_);
            }

            boolean flag2 = !flag || p_21017_ > 0.0F;
            if (flag2) {
                this.lastDamageSource = p_21016_;
                this.lastDamageStamp = this.level().getGameTime();

                for (MobEffectInstance mobeffectinstance : this.getActiveEffects()) {
                    mobeffectinstance.onMobHurt(this, p_21016_, p_21017_);
                }
            }

            if (this instanceof ServerPlayer) {
                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger((ServerPlayer)this, p_21016_, f, p_21017_, flag);
                if (f1 > 0.0F && f1 < 3.4028235E37F) {
                    ((ServerPlayer)this).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(f1 * 10.0F));
                }
            }

            if (entity instanceof ServerPlayer) {
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger((ServerPlayer)entity, this, p_21016_, f, p_21017_, flag);
            }

            this.damageContainers.pop();
            return flag2;
        }
    }

    protected void blockUsingShield(LivingEntity p_21200_) {
        p_21200_.blockedByShield(this);
    }

    protected void blockedByShield(LivingEntity p_21246_) {
        p_21246_.knockback(0.5, p_21246_.getX() - this.getX(), p_21246_.getZ() - this.getZ());
    }

    private boolean checkTotemDeathProtection(DamageSource p_21263_) {
        if (p_21263_.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            ItemStack itemstack = null;

            for (InteractionHand interactionhand : InteractionHand.values()) {
                ItemStack itemstack1 = this.getItemInHand(interactionhand);
                if (itemstack1.is(Items.TOTEM_OF_UNDYING) && net.neoforged.neoforge.common.CommonHooks.onLivingUseTotem(this, p_21263_, itemstack1, interactionhand)) {
                    itemstack = itemstack1.copy();
                    itemstack1.shrink(1);
                    break;
                }
            }

            if (itemstack != null) {
                if (this instanceof ServerPlayer serverplayer) {
                    serverplayer.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING), 1);
                    CriteriaTriggers.USED_TOTEM.trigger(serverplayer, itemstack);
                    this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
                }

                this.setHealth(1.0F);
                this.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);
                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                this.level().broadcastEntityEvent(this, (byte)35);
            }

            return itemstack != null;
        }
    }

    @Nullable
    public DamageSource getLastDamageSource() {
        if (this.level().getGameTime() - this.lastDamageStamp > 40L) {
            this.lastDamageSource = null;
        }

        return this.lastDamageSource;
    }

    protected void playHurtSound(DamageSource p_21160_) {
        this.makeSound(this.getHurtSound(p_21160_));
    }

    public void makeSound(@Nullable SoundEvent p_320810_) {
        if (p_320810_ != null) {
            this.playSound(p_320810_, this.getSoundVolume(), this.getVoicePitch());
        }
    }

    public boolean isDamageSourceBlocked(DamageSource p_21276_) {
        Entity entity = p_21276_.getDirectEntity();
        boolean flag = false;
        if (entity instanceof AbstractArrow abstractarrow && abstractarrow.getPierceLevel() > 0) {
            flag = true;
        }

        if (!p_21276_.is(DamageTypeTags.BYPASSES_SHIELD) && this.isBlocking() && !flag) {
            Vec3 vec32 = p_21276_.getSourcePosition();
            if (vec32 != null) {
                Vec3 vec3 = this.calculateViewVector(0.0F, this.getYHeadRot());
                Vec3 vec31 = vec32.vectorTo(this.position());
                vec31 = new Vec3(vec31.x, 0.0, vec31.z).normalize();
                return vec31.dot(vec3) < 0.0;
            }
        }

        return false;
    }

    private void breakItem(ItemStack p_21279_) {
        if (!p_21279_.isEmpty()) {
            if (!this.isSilent()) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        p_21279_.getBreakingSound(),
                        this.getSoundSource(),
                        0.8F,
                        0.8F + this.level().random.nextFloat() * 0.4F,
                        false
                    );
            }

            this.spawnItemParticles(p_21279_, 5);
        }
    }

    public void die(DamageSource p_21014_) {
        if (net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, p_21014_)) return;
        if (!this.isRemoved() && !this.dead) {
            Entity entity = p_21014_.getEntity();
            LivingEntity livingentity = this.getKillCredit();
            if (this.deathScore >= 0 && livingentity != null) {
                livingentity.awardKillScore(this, this.deathScore, p_21014_);
            }

            if (this.isSleeping()) {
                this.stopSleeping();
            }

            if (!this.level().isClientSide && this.hasCustomName()) {
                LOGGER.info("Named entity {} died: {}", this, this.getCombatTracker().getDeathMessage().getString());
            }

            this.dead = true;
            this.getCombatTracker().recheckStatus();
            if (this.level() instanceof ServerLevel serverlevel) {
                if (entity == null || entity.killedEntity(serverlevel, this)) {
                    this.gameEvent(GameEvent.ENTITY_DIE);
                    this.dropAllDeathLoot(serverlevel, p_21014_);
                    this.createWitherRose(livingentity);
                }

                this.level().broadcastEntityEvent(this, (byte)3);
            }

            this.setPose(Pose.DYING);
        }
    }

    protected void createWitherRose(@Nullable LivingEntity p_21269_) {
        if (!this.level().isClientSide) {
            boolean flag = false;
            if (p_21269_ instanceof WitherBoss) {
                if (net.neoforged.neoforge.event.EventHooks.canEntityGrief(this.level(), p_21269_)) {
                    BlockPos blockpos = this.blockPosition();
                    BlockState blockstate = Blocks.WITHER_ROSE.defaultBlockState();
                    if (this.level().getBlockState(blockpos).isAir() && blockstate.canSurvive(this.level(), blockpos)) {
                        this.level().setBlock(blockpos, blockstate, 3);
                        flag = true;
                    }
                }

                if (!flag) {
                    ItemEntity itementity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), new ItemStack(Items.WITHER_ROSE));
                    this.level().addFreshEntity(itementity);
                }
            }
        }
    }

    protected void dropAllDeathLoot(ServerLevel p_348524_, DamageSource p_21192_) {
        this.captureDrops(new java.util.ArrayList<>());
        boolean flag = this.lastHurtByPlayerTime > 0;
        if (this.shouldDropLoot() && p_348524_.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.dropFromLootTable(p_21192_, flag);
            this.dropCustomDeathLoot(p_348524_, p_21192_, flag);
        }

        this.dropEquipment();
        this.dropExperience(p_21192_.getEntity());

        Collection<ItemEntity> drops = captureDrops(null);
        if (!net.neoforged.neoforge.common.CommonHooks.onLivingDrops(this, p_21192_, drops, lastHurtByPlayerTime > 0))
            drops.forEach(e -> level().addFreshEntity(e));
    }

    protected void dropEquipment() {
    }

    protected void dropExperience(@Nullable Entity p_345346_) {
        if (this.level() instanceof ServerLevel serverlevel
            && !this.wasExperienceConsumed()
            && (
                this.isAlwaysExperienceDropper()
                    || this.lastHurtByPlayerTime > 0 && this.shouldDropExperience() && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
            )) {
            int reward = net.neoforged.neoforge.event.EventHooks.getExperienceDrop(this, this.lastHurtByPlayer, this.getExperienceReward(serverlevel, p_345346_));
            ExperienceOrb.award((ServerLevel) this.level(), this.position(), reward);
        }
    }

    protected void dropCustomDeathLoot(ServerLevel p_348639_, DamageSource p_21018_, boolean p_21020_) {
    }

    public ResourceKey<LootTable> getLootTable() {
        return this.getType().getDefaultLootTable();
    }

    public long getLootTableSeed() {
        return 0L;
    }

    protected float getKnockback(Entity p_344813_, DamageSource p_344976_) {
        float f = (float)this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        return this.level() instanceof ServerLevel serverlevel
            ? EnchantmentHelper.modifyKnockback(serverlevel, this.getWeaponItem(), p_344813_, p_344976_, f)
            : f;
    }

    protected void dropFromLootTable(DamageSource p_21021_, boolean p_21022_) {
        ResourceKey<LootTable> resourcekey = this.getLootTable();
        LootTable loottable = this.level().getServer().reloadableRegistries().getLootTable(resourcekey);
        LootParams.Builder lootparams$builder = new LootParams.Builder((ServerLevel)this.level())
            .withParameter(LootContextParams.THIS_ENTITY, this)
            .withParameter(LootContextParams.ORIGIN, this.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, p_21021_)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, p_21021_.getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, p_21021_.getDirectEntity());
        if (p_21022_ && this.lastHurtByPlayer != null) {
            lootparams$builder = lootparams$builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer)
                .withLuck(this.lastHurtByPlayer.getLuck());
        }

        LootParams lootparams = lootparams$builder.create(LootContextParamSets.ENTITY);
        loottable.getRandomItems(lootparams, this.getLootTableSeed(), this::spawnAtLocation);
    }

    public void knockback(double p_147241_, double p_147242_, double p_147243_) {
        net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event = net.neoforged.neoforge.common.CommonHooks.onLivingKnockBack(this, (float) p_147241_, p_147242_, p_147243_);
        if(event.isCanceled()) return;
        p_147241_ = event.getStrength();
        p_147242_ = event.getRatioX();
        p_147243_ = event.getRatioZ();
        p_147241_ *= 1.0 - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (!(p_147241_ <= 0.0)) {
            this.hasImpulse = true;
            Vec3 vec3 = this.getDeltaMovement();

            while (p_147242_ * p_147242_ + p_147243_ * p_147243_ < 1.0E-5F) {
                p_147242_ = (Math.random() - Math.random()) * 0.01;
                p_147243_ = (Math.random() - Math.random()) * 0.01;
            }

            Vec3 vec31 = new Vec3(p_147242_, 0.0, p_147243_).normalize().scale(p_147241_);
            this.setDeltaMovement(vec3.x / 2.0 - vec31.x, this.onGround() ? Math.min(0.4, vec3.y / 2.0 + p_147241_) : vec3.y, vec3.z / 2.0 - vec31.z);
        }
    }

    public void indicateDamage(double p_270514_, double p_270826_) {
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource p_21239_) {
        return SoundEvents.GENERIC_HURT;
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_DEATH;
    }

    private SoundEvent getFallDamageSound(int p_21313_) {
        return p_21313_ > 4 ? this.getFallSounds().big() : this.getFallSounds().small();
    }

    public void skipDropExperience() {
        this.skipDropExperience = true;
    }

    public boolean wasExperienceConsumed() {
        return this.skipDropExperience;
    }

    public float getHurtDir() {
        return 0.0F;
    }

    public AABB getHitbox() {
        AABB aabb = this.getBoundingBox();
        Entity entity = this.getVehicle();
        if (entity != null) {
            Vec3 vec3 = entity.getPassengerRidingPosition(this);
            return aabb.setMinY(Math.max(vec3.y, aabb.minY));
        } else {
            return aabb;
        }
    }

    public Map<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEnchantments() {
        return this.activeLocationDependentEnchantments;
    }

    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.GENERIC_SMALL_FALL, SoundEvents.GENERIC_BIG_FALL);
    }

    protected SoundEvent getDrinkingSound(ItemStack p_21174_) {
        return p_21174_.getDrinkingSound();
    }

    public SoundEvent getEatingSound(ItemStack p_21202_) {
        return p_21202_.getEatingSound();
    }

    public Optional<BlockPos> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    public boolean onClimbable() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos blockpos = this.blockPosition();
            BlockState blockstate = this.getInBlockState();
            Optional<BlockPos> ladderPos = net.neoforged.neoforge.common.CommonHooks.isLivingOnLadder(blockstate, level(), blockpos, this);
            if (ladderPos.isPresent()) this.lastClimbablePos = ladderPos;
            return ladderPos.isPresent();
        }
    }

    private boolean trapdoorUsableAsLadder(BlockPos p_21177_, BlockState p_21178_) {
        if (!p_21178_.getValue(TrapDoorBlock.OPEN)) {
            return false;
        } else {
            BlockState blockstate = this.level().getBlockState(p_21177_.below());
            return blockstate.is(Blocks.LADDER) && blockstate.getValue(LadderBlock.FACING) == p_21178_.getValue(TrapDoorBlock.FACING);
        }
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved() && this.getHealth() > 0.0F;
    }

    @Override
    public int getMaxFallDistance() {
        return this.getComfortableFallDistance(0.0F);
    }

    protected final int getComfortableFallDistance(float p_326294_) {
        return Mth.floor(p_326294_ + 3.0F);
    }

    @Override
    public boolean causeFallDamage(float p_147187_, float p_147188_, DamageSource p_147189_) {
        float[] ret = net.neoforged.neoforge.common.CommonHooks.onLivingFall(this, p_147187_, p_147188_);
        if (ret == null) return false;
        p_147187_ = ret[0];
        p_147188_ = ret[1];

        boolean flag = super.causeFallDamage(p_147187_, p_147188_, p_147189_);
        int i = this.calculateFallDamage(p_147187_, p_147188_);
        if (i > 0) {
            this.playSound(this.getFallDamageSound(i), 1.0F, 1.0F);
            this.playBlockFallSound();
            this.hurt(p_147189_, (float)i);
            return true;
        } else {
            return flag;
        }
    }

    protected int calculateFallDamage(float p_21237_, float p_21238_) {
        if (this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return 0;
        } else {
            float f = (float)this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
            float f1 = p_21237_ - f;
            return Mth.ceil((double)(f1 * p_21238_) * this.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
        }
    }

    protected void playBlockFallSound() {
        if (!this.isSilent()) {
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY() - 0.2F);
            int k = Mth.floor(this.getZ());
            BlockPos pos = new BlockPos(i, j, k);
            BlockState blockstate = this.level().getBlockState(pos);
            if (!blockstate.isAir()) {
                SoundType soundtype = blockstate.getSoundType(level(), pos, this);
                this.playSound(soundtype.getFallSound(), soundtype.getVolume() * 0.5F, soundtype.getPitch() * 0.75F);
            }
        }
    }

    @Override
    public void animateHurt(float p_265265_) {
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
    }

    public int getArmorValue() {
        return Mth.floor(this.getAttributeValue(Attributes.ARMOR));
    }

    protected void hurtArmor(DamageSource p_21122_, float p_21123_) {
    }

    protected void hurtHelmet(DamageSource p_147213_, float p_147214_) {
    }

    protected void hurtCurrentlyUsedShield(float p_21316_) {
    }

    protected void doHurtEquipment(DamageSource p_330843_, float p_330394_, EquipmentSlot... p_331314_) {
        if (!(p_330394_ <= 0.0F)) {
            int i = (int)Math.max(1.0F, p_330394_ / 4.0F);

            net.neoforged.neoforge.common.CommonHooks.onArmorHurt(p_330843_, p_331314_, i, this);
            if (true) return; //Neo: Invalidates the loop. Armor damage happens in common hook.
            for (EquipmentSlot equipmentslot : p_331314_) {
                ItemStack itemstack = this.getItemBySlot(equipmentslot);
                if (itemstack.getItem() instanceof ArmorItem && itemstack.canBeHurtBy(p_330843_)) {
                    itemstack.hurtAndBreak(i, this, equipmentslot);
                }
            }
        }
    }

    protected float getDamageAfterArmorAbsorb(DamageSource p_21162_, float p_21163_) {
        if (!p_21162_.is(DamageTypeTags.BYPASSES_ARMOR)) {
            this.hurtArmor(p_21162_, p_21163_);
            p_21163_ = CombatRules.getDamageAfterAbsorb(
                this, p_21163_, p_21162_, (float)this.getArmorValue(), (float)this.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
            );
        }

        return p_21163_;
    }

    protected float getDamageAfterMagicAbsorb(DamageSource p_21193_, float p_21194_) {
        if (p_21193_.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return p_21194_;
        } else {
            if (this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !p_21193_.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                int i = (this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                int j = 25 - i;
                float f = p_21194_ * (float)j;
                float f1 = p_21194_;
                p_21194_ = Math.max(f / 25.0F, 0.0F);
                float f2 = f1 - p_21194_;
                if (f2 > 0.0F && f2 < 3.4028235E37F) {
                    this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.MOB_EFFECTS, f2);
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer)this).awardStat(Stats.DAMAGE_RESISTED, Math.round(f2 * 10.0F));
                    } else if (p_21193_.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer)p_21193_.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f2 * 10.0F));
                    }
                }
            }

            if (p_21194_ <= 0.0F) {
                return 0.0F;
            } else if (p_21193_.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                return p_21194_;
            } else {
                float f3;
                if (this.level() instanceof ServerLevel serverlevel) {
                    f3 = EnchantmentHelper.getDamageProtection(serverlevel, this, p_21193_);
                } else {
                    f3 = 0.0F;
                }

                if (f3 > 0.0F) {
                    p_21194_ = CombatRules.getDamageAfterMagicAbsorb(p_21194_, f3);
                    this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ENCHANTMENTS,this.damageContainers.peek().getNewDamage() - p_21194_);
                }

                return p_21194_;
            }
        }
    }

    protected void actuallyHurt(DamageSource p_21240_, float p_21241_) {
        if (!this.isInvulnerableTo(p_21240_)) {
            this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR, this.damageContainers.peek().getNewDamage() - this.getDamageAfterArmorAbsorb(p_21240_, this.damageContainers.peek().getNewDamage()));
            this.getDamageAfterMagicAbsorb(p_21240_, this.damageContainers.peek().getNewDamage());
            float damage = net.neoforged.neoforge.common.CommonHooks.onLivingDamagePre(this, this.damageContainers.peek());
            this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION, Math.min(this.getAbsorptionAmount(), damage));
            float absorbed = Math.min(damage, this.damageContainers.peek().getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION));
            this.setAbsorptionAmount(Math.max(0, this.getAbsorptionAmount() - absorbed));
            float f1 = this.damageContainers.peek().getNewDamage();
            float f = absorbed;
            if (f > 0.0F && f < 3.4028235E37F && p_21240_.getEntity() instanceof ServerPlayer serverplayer) {
                serverplayer.awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(f * 10.0F));
            }

            if (f1 != 0.0F) {
                this.getCombatTracker().recordDamage(p_21240_, f1);
                this.setHealth(this.getHealth() - f1);
                this.gameEvent(GameEvent.ENTITY_DAMAGE);
                this.onDamageTaken(this.damageContainers.peek());
            }
            net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(this, this.damageContainers.peek());
        }
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    @Nullable
    public LivingEntity getKillCredit() {
        if (this.lastHurtByPlayer != null) {
            return this.lastHurtByPlayer;
        } else {
            return this.lastHurtByMob != null ? this.lastHurtByMob : null;
        }
    }

    public final float getMaxHealth() {
        return (float)this.getAttributeValue(Attributes.MAX_HEALTH);
    }

    public final float getMaxAbsorption() {
        return (float)this.getAttributeValue(Attributes.MAX_ABSORPTION);
    }

    public final int getArrowCount() {
        return this.entityData.get(DATA_ARROW_COUNT_ID);
    }

    public final void setArrowCount(int p_21318_) {
        this.entityData.set(DATA_ARROW_COUNT_ID, p_21318_);
    }

    public final int getStingerCount() {
        return this.entityData.get(DATA_STINGER_COUNT_ID);
    }

    public final void setStingerCount(int p_21322_) {
        this.entityData.set(DATA_STINGER_COUNT_ID, p_21322_);
    }

    public int getCurrentSwingDuration() {
        if (MobEffectUtil.hasDigSpeed(this)) {
            return 6 - (1 + MobEffectUtil.getDigSpeedAmplification(this));
        } else {
            return this.hasEffect(MobEffects.DIG_SLOWDOWN) ? 6 + (1 + this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) * 2 : 6;
        }
    }

    public void swing(InteractionHand p_21007_) {
        this.swing(p_21007_, false);
    }

    public void swing(InteractionHand p_21012_, boolean p_21013_) {
        ItemStack stack = this.getItemInHand(p_21012_);
        if (!stack.isEmpty() && stack.onEntitySwing(this, p_21012_)) return;
        if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
            this.swingTime = -1;
            this.swinging = true;
            this.swingingArm = p_21012_;
            if (this.level() instanceof ServerLevel) {
                ClientboundAnimatePacket clientboundanimatepacket = new ClientboundAnimatePacket(this, p_21012_ == InteractionHand.MAIN_HAND ? 0 : 3);
                ServerChunkCache serverchunkcache = ((ServerLevel)this.level()).getChunkSource();
                if (p_21013_) {
                    serverchunkcache.broadcastAndSend(this, clientboundanimatepacket);
                } else {
                    serverchunkcache.broadcast(this, clientboundanimatepacket);
                }
            }
        }
    }

    @Override
    public void handleDamageEvent(DamageSource p_270229_) {
        this.walkAnimation.setSpeed(1.5F);
        this.invulnerableTime = 20;
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        SoundEvent soundevent = this.getHurtSound(p_270229_);
        if (soundevent != null) {
            this.playSound(soundevent, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }

        this.hurt(this.damageSources().generic(), 0.0F);
        this.lastDamageSource = p_270229_;
        this.lastDamageStamp = this.level().getGameTime();
    }

    @Override
    public void handleEntityEvent(byte p_20975_) {
        switch (p_20975_) {
            case 3:
                SoundEvent soundevent = this.getDeathSound();
                if (soundevent != null) {
                    this.playSound(soundevent, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                if (!(this instanceof Player)) {
                    this.setHealth(0.0F);
                    this.die(this.damageSources().generic());
                }
                break;
            case 29:
                this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + this.level().random.nextFloat() * 0.4F);
                break;
            case 30:
                this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
                break;
            case 46:
                int i = 128;

                for (int j = 0; j < 128; j++) {
                    double d0 = (double)j / 127.0;
                    float f = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f1 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f2 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    double d1 = Mth.lerp(d0, this.xo, this.getX()) + (this.random.nextDouble() - 0.5) * (double)this.getBbWidth() * 2.0;
                    double d2 = Mth.lerp(d0, this.yo, this.getY()) + this.random.nextDouble() * (double)this.getBbHeight();
                    double d3 = Mth.lerp(d0, this.zo, this.getZ()) + (this.random.nextDouble() - 0.5) * (double)this.getBbWidth() * 2.0;
                    this.level().addParticle(ParticleTypes.PORTAL, d1, d2, d3, (double)f, (double)f1, (double)f2);
                }
                break;
            case 47:
                this.breakItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
                break;
            case 48:
                this.breakItem(this.getItemBySlot(EquipmentSlot.OFFHAND));
                break;
            case 49:
                this.breakItem(this.getItemBySlot(EquipmentSlot.HEAD));
                break;
            case 50:
                this.breakItem(this.getItemBySlot(EquipmentSlot.CHEST));
                break;
            case 51:
                this.breakItem(this.getItemBySlot(EquipmentSlot.LEGS));
                break;
            case 52:
                this.breakItem(this.getItemBySlot(EquipmentSlot.FEET));
                break;
            case 54:
                HoneyBlock.showJumpParticles(this);
                break;
            case 55:
                this.swapHandItems();
                break;
            case 60:
                this.makePoofParticles();
                break;
            case 65:
                this.breakItem(this.getItemBySlot(EquipmentSlot.BODY));
                break;
            default:
                super.handleEntityEvent(p_20975_);
        }
    }

    private void makePoofParticles() {
        for (int i = 0; i < 20; i++) {
            double d0 = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            this.level().addParticle(ParticleTypes.POOF, this.getRandomX(1.0), this.getRandomY(), this.getRandomZ(1.0), d0, d1, d2);
        }
    }

    private void swapHandItems() {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.OFFHAND);
        var event = net.neoforged.neoforge.common.CommonHooks.onLivingSwapHandItems(this);
        if (event.isCanceled()) return;
        this.setItemSlot(EquipmentSlot.OFFHAND, event.getItemSwappedToOffHand());
        this.setItemSlot(EquipmentSlot.MAINHAND, event.getItemSwappedToMainHand());
    }

    @Override
    protected void onBelowWorld() {
        this.hurt(this.damageSources().fellOutOfWorld(), 4.0F);
    }

    protected void updateSwingTime() {
        int i = this.getCurrentSwingDuration();
        if (this.swinging) {
            this.swingTime++;
            if (this.swingTime >= i) {
                this.swingTime = 0;
                this.swinging = false;
            }
        } else {
            this.swingTime = 0;
        }

        this.attackAnim = (float)this.swingTime / (float)i;
    }

    @Nullable
    public AttributeInstance getAttribute(Holder<Attribute> p_316333_) {
        return this.getAttributes().getInstance(p_316333_);
    }

    public double getAttributeValue(Holder<Attribute> p_251296_) {
        return this.getAttributes().getValue(p_251296_);
    }

    public double getAttributeBaseValue(Holder<Attribute> p_248605_) {
        return this.getAttributes().getBaseValue(p_248605_);
    }

    public AttributeMap getAttributes() {
        return this.attributes;
    }

    public ItemStack getMainHandItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return this.getItemBySlot(EquipmentSlot.OFFHAND);
    }

    @Nonnull
    @Override
    public ItemStack getWeaponItem() {
        return this.getMainHandItem();
    }

    public boolean isHolding(Item p_21056_) {
        return this.isHolding(p_147200_ -> p_147200_.is(p_21056_));
    }

    public boolean isHolding(Predicate<ItemStack> p_21094_) {
        return p_21094_.test(this.getMainHandItem()) || p_21094_.test(this.getOffhandItem());
    }

    public ItemStack getItemInHand(InteractionHand p_21121_) {
        if (p_21121_ == InteractionHand.MAIN_HAND) {
            return this.getItemBySlot(EquipmentSlot.MAINHAND);
        } else if (p_21121_ == InteractionHand.OFF_HAND) {
            return this.getItemBySlot(EquipmentSlot.OFFHAND);
        } else {
            throw new IllegalArgumentException("Invalid hand " + p_21121_);
        }
    }

    public void setItemInHand(InteractionHand p_21009_, ItemStack p_21010_) {
        if (p_21009_ == InteractionHand.MAIN_HAND) {
            this.setItemSlot(EquipmentSlot.MAINHAND, p_21010_);
        } else {
            if (p_21009_ != InteractionHand.OFF_HAND) {
                throw new IllegalArgumentException("Invalid hand " + p_21009_);
            }

            this.setItemSlot(EquipmentSlot.OFFHAND, p_21010_);
        }
    }

    public boolean hasItemInSlot(EquipmentSlot p_21034_) {
        return !this.getItemBySlot(p_21034_).isEmpty();
    }

    public boolean canUseSlot(EquipmentSlot p_326058_) {
        return false;
    }

    public abstract Iterable<ItemStack> getArmorSlots();

    public abstract ItemStack getItemBySlot(EquipmentSlot p_21127_);

    public abstract void setItemSlot(EquipmentSlot p_21036_, ItemStack p_21037_);

    public Iterable<ItemStack> getHandSlots() {
        return List.of();
    }

    public Iterable<ItemStack> getArmorAndBodyArmorSlots() {
        return this.getArmorSlots();
    }

    public Iterable<ItemStack> getAllSlots() {
        return Iterables.concat(this.getHandSlots(), this.getArmorAndBodyArmorSlots());
    }

    protected void verifyEquippedItem(ItemStack p_181123_) {
        p_181123_.getItem().verifyComponentsAfterLoad(p_181123_);
    }

    public float getArmorCoverPercentage() {
        Iterable<ItemStack> iterable = this.getArmorSlots();
        int i = 0;
        int j = 0;

        for (ItemStack itemstack : iterable) {
            if (!itemstack.isEmpty()) {
                j++;
            }

            i++;
        }

        return i > 0 ? (float)j / (float)i : 0.0F;
    }

    @Override
    public void setSprinting(boolean p_21284_) {
        super.setSprinting(p_21284_);
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
        attributeinstance.removeModifier(SPEED_MODIFIER_SPRINTING.id());
        if (p_21284_) {
            attributeinstance.addTransientModifier(SPEED_MODIFIER_SPRINTING);
        }
    }

    protected float getSoundVolume() {
        return 1.0F;
    }

    public float getVoicePitch() {
        return this.isBaby()
            ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F
            : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    @Override
    public void push(Entity p_21294_) {
        if (!this.isSleeping()) {
            super.push(p_21294_);
        }
    }

    private void dismountVehicle(Entity p_21029_) {
        Vec3 vec3;
        if (this.isRemoved()) {
            vec3 = this.position();
        } else if (!p_21029_.isRemoved() && !this.level().getBlockState(p_21029_.blockPosition()).is(BlockTags.PORTALS)) {
            vec3 = p_21029_.getDismountLocationForPassenger(this);
        } else {
            double d0 = Math.max(this.getY(), p_21029_.getY());
            vec3 = new Vec3(this.getX(), d0, this.getZ());
        }

        this.dismountTo(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    protected float getJumpPower() {
        return this.getJumpPower(1.0F);
    }

    protected float getJumpPower(float p_326107_) {
        return (float)this.getAttributeValue(Attributes.JUMP_STRENGTH) * p_326107_ * this.getBlockJumpFactor() + this.getJumpBoostPower();
    }

    public float getJumpBoostPower() {
        return this.hasEffect(MobEffects.JUMP) ? 0.1F * ((float)this.getEffect(MobEffects.JUMP).getAmplifier() + 1.0F) : 0.0F;
    }

    @VisibleForTesting
    public void jumpFromGround() {
        float f = this.getJumpPower();
        if (!(f <= 1.0E-5F)) {
            Vec3 vec3 = this.getDeltaMovement();
            this.setDeltaMovement(vec3.x, (double)f, vec3.z);
            if (this.isSprinting()) {
                float f1 = this.getYRot() * (float) (Math.PI / 180.0);
                this.addDeltaMovement(new Vec3((double)(-Mth.sin(f1)) * 0.2, 0.0, (double)Mth.cos(f1) * 0.2));
            }

            this.hasImpulse = true;
            net.neoforged.neoforge.common.CommonHooks.onLivingJump(this);
        }
    }

    @Deprecated // FORGE: use sinkInFluid instead
    protected void goDownInWater() {
        this.sinkInFluid(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value());
    }

    @Deprecated // FORGE: use jumpInFluid instead
    protected void jumpInLiquid(TagKey<Fluid> p_204043_) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double)0.04F * this.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), 0.0D));
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    public boolean canStandOnFluid(FluidState p_204042_) {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return this.getAttributeValue(Attributes.GRAVITY);
    }

    public void travel(Vec3 p_21280_) {
        if (this.isControlledByLocalInstance()) {
            double d0 = this.getGravity();
            boolean flag = this.getDeltaMovement().y <= 0.0;
            if (flag && this.hasEffect(MobEffects.SLOW_FALLING)) {
                d0 = Math.min(d0, 0.01);
            }

            FluidState fluidstate = this.level().getFluidState(this.blockPosition());
            if ((this.isInWater() || (this.isInFluidType(fluidstate) && fluidstate.getFluidType() != net.neoforged.neoforge.common.NeoForgeMod.LAVA_TYPE.value())) && this.isAffectedByFluids() && !this.canStandOnFluid(fluidstate)) {
                if (this.isInWater() || (this.isInFluidType(fluidstate) && !this.moveInFluid(fluidstate, p_21280_, d0))) {
                double d9 = this.getY();
                float f4 = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
                float f5 = 0.02F;
                float f6 = (float)this.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
                if (!this.onGround()) {
                    f6 *= 0.5F;
                }

                if (f6 > 0.0F) {
                    f4 += (0.54600006F - f4) * f6;
                    f5 += (this.getSpeed() - f5) * f6;
                }

                if (this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                    f4 = 0.96F;
                }

                f5 *= (float)this.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED);
                this.moveRelative(f5, p_21280_);
                this.move(MoverType.SELF, this.getDeltaMovement());
                Vec3 vec36 = this.getDeltaMovement();
                if (this.horizontalCollision && this.onClimbable()) {
                    vec36 = new Vec3(vec36.x, 0.2, vec36.z);
                }

                this.setDeltaMovement(vec36.multiply((double)f4, 0.8F, (double)f4));
                Vec3 vec32 = this.getFluidFallingAdjustedMovement(d0, flag, this.getDeltaMovement());
                this.setDeltaMovement(vec32);
                if (this.horizontalCollision && this.isFree(vec32.x, vec32.y + 0.6F - this.getY() + d9, vec32.z)) {
                    this.setDeltaMovement(vec32.x, 0.3F, vec32.z);
                }
                }
            } else if (this.isInLava() && this.isAffectedByFluids() && !this.canStandOnFluid(fluidstate)) {
                double d8 = this.getY();
                this.moveRelative(0.02F, p_21280_);
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.getFluidHeight(FluidTags.LAVA) <= this.getFluidJumpThreshold()) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.8F, 0.5));
                    Vec3 vec33 = this.getFluidFallingAdjustedMovement(d0, flag, this.getDeltaMovement());
                    this.setDeltaMovement(vec33);
                } else {
                    this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
                }

                if (d0 != 0.0) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0.0, -d0 / 4.0, 0.0));
                }

                Vec3 vec34 = this.getDeltaMovement();
                if (this.horizontalCollision && this.isFree(vec34.x, vec34.y + 0.6F - this.getY() + d8, vec34.z)) {
                    this.setDeltaMovement(vec34.x, 0.3F, vec34.z);
                }
            } else if (this.isFallFlying()) {
                this.checkSlowFallDistance();
                Vec3 vec3 = this.getDeltaMovement();
                Vec3 vec31 = this.getLookAngle();
                float f = this.getXRot() * (float) (Math.PI / 180.0);
                double d1 = Math.sqrt(vec31.x * vec31.x + vec31.z * vec31.z);
                double d3 = vec3.horizontalDistance();
                double d4 = vec31.length();
                double d5 = Math.cos((double)f);
                d5 = d5 * d5 * Math.min(1.0, d4 / 0.4);
                vec3 = this.getDeltaMovement().add(0.0, d0 * (-1.0 + d5 * 0.75), 0.0);
                if (vec3.y < 0.0 && d1 > 0.0) {
                    double d6 = vec3.y * -0.1 * d5;
                    vec3 = vec3.add(vec31.x * d6 / d1, d6, vec31.z * d6 / d1);
                }

                if (f < 0.0F && d1 > 0.0) {
                    double d10 = d3 * (double)(-Mth.sin(f)) * 0.04;
                    vec3 = vec3.add(-vec31.x * d10 / d1, d10 * 3.2, -vec31.z * d10 / d1);
                }

                if (d1 > 0.0) {
                    vec3 = vec3.add((vec31.x / d1 * d3 - vec3.x) * 0.1, 0.0, (vec31.z / d1 * d3 - vec3.z) * 0.1);
                }

                this.setDeltaMovement(vec3.multiply(0.99F, 0.98F, 0.99F));
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.horizontalCollision && !this.level().isClientSide) {
                    double d11 = this.getDeltaMovement().horizontalDistance();
                    double d7 = d3 - d11;
                    float f1 = (float)(d7 * 10.0 - 3.0);
                    if (f1 > 0.0F) {
                        this.playSound(this.getFallDamageSound((int)f1), 1.0F, 1.0F);
                        this.hurt(this.damageSources().flyIntoWall(), f1);
                    }
                }

                if (this.onGround() && !this.level().isClientSide) {
                    this.setSharedFlag(7, false);
                }
            } else {
                BlockPos blockpos = this.getBlockPosBelowThatAffectsMyMovement();
                float f2 = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getFriction(level(), this.getBlockPosBelowThatAffectsMyMovement(), this);
                float f3 = this.onGround() ? f2 * 0.91F : 0.91F;
                Vec3 vec35 = this.handleRelativeFrictionAndCalculateMovement(p_21280_, f2);
                double d2 = vec35.y;
                if (this.hasEffect(MobEffects.LEVITATION)) {
                    d2 += (0.05 * (double)(this.getEffect(MobEffects.LEVITATION).getAmplifier() + 1) - vec35.y) * 0.2;
                } else if (!this.level().isClientSide || this.level().hasChunkAt(blockpos)) {
                    d2 -= d0;
                } else if (this.getY() > (double)this.level().getMinBuildHeight()) {
                    d2 = -0.1;
                } else {
                    d2 = 0.0;
                }

                if (this.shouldDiscardFriction()) {
                    this.setDeltaMovement(vec35.x, d2, vec35.z);
                } else {
                    this.setDeltaMovement(vec35.x * (double)f3, this instanceof FlyingAnimal ? d2 * (double)f3 : d2 * 0.98F, vec35.z * (double)f3);
                }
            }
        }

        this.calculateEntityAnimation(this instanceof FlyingAnimal);
    }

    private void travelRidden(Player p_278244_, Vec3 p_278231_) {
        Vec3 vec3 = this.getRiddenInput(p_278244_, p_278231_);
        this.tickRidden(p_278244_, vec3);
        if (this.isControlledByLocalInstance()) {
            this.setSpeed(this.getRiddenSpeed(p_278244_));
            this.travel(vec3);
        } else {
            this.calculateEntityAnimation(false);
            this.setDeltaMovement(Vec3.ZERO);
            this.tryCheckInsideBlocks();
        }
    }

    protected void tickRidden(Player p_278262_, Vec3 p_275242_) {
    }

    protected Vec3 getRiddenInput(Player p_278326_, Vec3 p_275300_) {
        return p_275300_;
    }

    protected float getRiddenSpeed(Player p_278286_) {
        return this.getSpeed();
    }

    public void calculateEntityAnimation(boolean p_268129_) {
        float f = (float)Mth.length(this.getX() - this.xo, p_268129_ ? this.getY() - this.yo : 0.0, this.getZ() - this.zo);
        this.updateWalkAnimation(f);
    }

    protected void updateWalkAnimation(float p_268283_) {
        float f = Math.min(p_268283_ * 4.0F, 1.0F);
        this.walkAnimation.update(f, 0.4F);
    }

    public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 p_21075_, float p_21076_) {
        this.moveRelative(this.getFrictionInfluencedSpeed(p_21076_), p_21075_);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 vec3 = this.getDeltaMovement();
        if ((this.horizontalCollision || this.jumping)
            && (this.onClimbable() || this.getInBlockState().is(Blocks.POWDER_SNOW) && PowderSnowBlock.canEntityWalkOnPowderSnow(this))) {
            vec3 = new Vec3(vec3.x, 0.2, vec3.z);
        }

        return vec3;
    }

    public Vec3 getFluidFallingAdjustedMovement(double p_20995_, boolean p_20996_, Vec3 p_20997_) {
        if (p_20995_ != 0.0 && !this.isSprinting()) {
            double d0;
            if (p_20996_ && Math.abs(p_20997_.y - 0.005) >= 0.003 && Math.abs(p_20997_.y - p_20995_ / 16.0) < 0.003) {
                d0 = -0.003;
            } else {
                d0 = p_20997_.y - p_20995_ / 16.0;
            }

            return new Vec3(p_20997_.x, d0, p_20997_.z);
        } else {
            return p_20997_;
        }
    }

    private Vec3 handleOnClimbable(Vec3 p_21298_) {
        if (this.onClimbable()) {
            this.resetFallDistance();
            float f = 0.15F;
            double d0 = Mth.clamp(p_21298_.x, -0.15F, 0.15F);
            double d1 = Mth.clamp(p_21298_.z, -0.15F, 0.15F);
            double d2 = Math.max(p_21298_.y, -0.15F);
            if (d2 < 0.0D && !this.getInBlockState().isScaffolding(this) && this.isSuppressingSlidingDownLadder() && this instanceof Player) {
                d2 = 0.0;
            }

            p_21298_ = new Vec3(d0, d2, d1);
        }

        return p_21298_;
    }

    private float getFrictionInfluencedSpeed(float p_21331_) {
        return this.onGround() ? this.getSpeed() * (0.21600002F / (p_21331_ * p_21331_ * p_21331_)) : this.getFlyingSpeed();
    }

    protected float getFlyingSpeed() {
        return this.getControllingPassenger() instanceof Player ? this.getSpeed() * 0.1F : 0.02F;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(float p_21320_) {
        this.speed = p_21320_;
    }

    public boolean doHurtTarget(Entity p_20970_) {
        this.setLastHurtMob(p_20970_);
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.updatingUsingItem();
        this.updateSwimAmount();
        if (!this.level().isClientSide) {
            int i = this.getArrowCount();
            if (i > 0) {
                if (this.removeArrowTime <= 0) {
                    this.removeArrowTime = 20 * (30 - i);
                }

                this.removeArrowTime--;
                if (this.removeArrowTime <= 0) {
                    this.setArrowCount(i - 1);
                }
            }

            int j = this.getStingerCount();
            if (j > 0) {
                if (this.removeStingerTime <= 0) {
                    this.removeStingerTime = 20 * (30 - j);
                }

                this.removeStingerTime--;
                if (this.removeStingerTime <= 0) {
                    this.setStingerCount(j - 1);
                }
            }

            this.detectEquipmentUpdates();
            if (this.tickCount % 20 == 0) {
                this.getCombatTracker().recheckStatus();
            }

            if (this.isSleeping() && !this.checkBedExists()) {
                this.stopSleeping();
            }
        }

        if (!this.isRemoved()) {
            this.aiStep();
        }

        double d1 = this.getX() - this.xo;
        double d0 = this.getZ() - this.zo;
        float f = (float)(d1 * d1 + d0 * d0);
        float f1 = this.yBodyRot;
        float f2 = 0.0F;
        this.oRun = this.run;
        float f3 = 0.0F;
        if (f > 0.0025000002F) {
            f3 = 1.0F;
            f2 = (float)Math.sqrt((double)f) * 3.0F;
            float f4 = (float)Mth.atan2(d0, d1) * (180.0F / (float)Math.PI) - 90.0F;
            float f5 = Mth.abs(Mth.wrapDegrees(this.getYRot()) - f4);
            if (95.0F < f5 && f5 < 265.0F) {
                f1 = f4 - 180.0F;
            } else {
                f1 = f4;
            }
        }

        if (this.attackAnim > 0.0F) {
            f1 = this.getYRot();
        }

        if (!this.onGround()) {
            f3 = 0.0F;
        }

        this.run = this.run + (f3 - this.run) * 0.3F;
        this.level().getProfiler().push("headTurn");
        f2 = this.tickHeadTurn(f1, f2);
        this.level().getProfiler().pop();
        this.level().getProfiler().push("rangeChecks");

        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }

        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }

        while (this.getXRot() - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }

        while (this.getXRot() - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }

        this.level().getProfiler().pop();
        this.animStep += f2;
        if (this.isFallFlying()) {
            this.fallFlyTicks++;
        } else {
            this.fallFlyTicks = 0;
        }

        if (this.isSleeping()) {
            this.setXRot(0.0F);
        }

        this.refreshDirtyAttributes();
        float f6 = this.getScale();
        if (f6 != this.appliedScale) {
            this.appliedScale = f6;
            this.refreshDimensions();
        }
    }

    private void detectEquipmentUpdates() {
        Map<EquipmentSlot, ItemStack> map = this.collectEquipmentChanges();
        if (map != null) {
            this.handleHandSwap(map);
            if (!map.isEmpty()) {
                this.handleEquipmentChanges(map);
            }
        }
    }

    @Nullable
    private Map<EquipmentSlot, ItemStack> collectEquipmentChanges() {
        Map<EquipmentSlot, ItemStack> map = null;

        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            ItemStack itemstack = switch (equipmentslot.getType()) {
                case HAND -> this.getLastHandItem(equipmentslot);
                case HUMANOID_ARMOR -> this.getLastArmorItem(equipmentslot);
                case ANIMAL_ARMOR -> this.lastBodyItemStack;
            };
            ItemStack itemstack1 = this.getItemBySlot(equipmentslot);
            if (this.equipmentHasChanged(itemstack, itemstack1)) {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent(this, equipmentslot, itemstack, itemstack1));
                if (map == null) {
                    map = Maps.newEnumMap(EquipmentSlot.class);
                }

                map.put(equipmentslot, itemstack1);
                AttributeMap attributemap = this.getAttributes();
                if (!itemstack.isEmpty()) {
                    itemstack.forEachModifier(equipmentslot, (p_344283_, p_344284_) -> {
                        AttributeInstance attributeinstance = attributemap.getInstance(p_344283_);
                        if (attributeinstance != null) {
                            attributeinstance.removeModifier(p_344284_);
                        }

                        EnchantmentHelper.stopLocationBasedEffects(itemstack, this, equipmentslot);
                    });
                }
            }
        }

        if (map != null) {
            for (Entry<EquipmentSlot, ItemStack> entry : map.entrySet()) {
                EquipmentSlot equipmentslot1 = entry.getKey();
                ItemStack itemstack2 = entry.getValue();
                if (!itemstack2.isEmpty()) {
                    itemstack2.forEachModifier(equipmentslot1, (p_352705_, p_352706_) -> {
                        AttributeInstance attributeinstance = this.attributes.getInstance(p_352705_);
                        if (attributeinstance != null) {
                            attributeinstance.removeModifier(p_352706_.id());
                            attributeinstance.addTransientModifier(p_352706_);
                        }

                        if (this.level() instanceof ServerLevel serverlevel) {
                            EnchantmentHelper.runLocationChangedEffects(serverlevel, itemstack2, this, equipmentslot1);
                        }
                    });
                }
            }
        }

        return map;
    }

    public boolean equipmentHasChanged(ItemStack p_252265_, ItemStack p_251043_) {
        return !ItemStack.matches(p_251043_, p_252265_);
    }

    private void handleHandSwap(Map<EquipmentSlot, ItemStack> p_21092_) {
        ItemStack itemstack = p_21092_.get(EquipmentSlot.MAINHAND);
        ItemStack itemstack1 = p_21092_.get(EquipmentSlot.OFFHAND);
        if (itemstack != null
            && itemstack1 != null
            && ItemStack.matches(itemstack, this.getLastHandItem(EquipmentSlot.OFFHAND))
            && ItemStack.matches(itemstack1, this.getLastHandItem(EquipmentSlot.MAINHAND))) {
            ((ServerLevel)this.level()).getChunkSource().broadcast(this, new ClientboundEntityEventPacket(this, (byte)55));
            p_21092_.remove(EquipmentSlot.MAINHAND);
            p_21092_.remove(EquipmentSlot.OFFHAND);
            this.setLastHandItem(EquipmentSlot.MAINHAND, itemstack.copy());
            this.setLastHandItem(EquipmentSlot.OFFHAND, itemstack1.copy());
        }
    }

    private void handleEquipmentChanges(Map<EquipmentSlot, ItemStack> p_21143_) {
        List<Pair<EquipmentSlot, ItemStack>> list = Lists.newArrayListWithCapacity(p_21143_.size());
        p_21143_.forEach((p_323229_, p_323230_) -> {
            ItemStack itemstack = p_323230_.copy();
            list.add(Pair.of(p_323229_, itemstack));
            switch (p_323229_.getType()) {
                case HAND:
                    this.setLastHandItem(p_323229_, itemstack);
                    break;
                case HUMANOID_ARMOR:
                    this.setLastArmorItem(p_323229_, itemstack);
                    break;
                case ANIMAL_ARMOR:
                    this.lastBodyItemStack = itemstack;
            }
        });
        ((ServerLevel)this.level()).getChunkSource().broadcast(this, new ClientboundSetEquipmentPacket(this.getId(), list));
    }

    private ItemStack getLastArmorItem(EquipmentSlot p_21199_) {
        return this.lastArmorItemStacks.get(p_21199_.getIndex());
    }

    private void setLastArmorItem(EquipmentSlot p_21129_, ItemStack p_21130_) {
        this.lastArmorItemStacks.set(p_21129_.getIndex(), p_21130_);
    }

    private ItemStack getLastHandItem(EquipmentSlot p_21245_) {
        return this.lastHandItemStacks.get(p_21245_.getIndex());
    }

    private void setLastHandItem(EquipmentSlot p_21169_, ItemStack p_21170_) {
        this.lastHandItemStacks.set(p_21169_.getIndex(), p_21170_);
    }

    protected float tickHeadTurn(float p_21260_, float p_21261_) {
        float f = Mth.wrapDegrees(p_21260_ - this.yBodyRot);
        this.yBodyRot += f * 0.3F;
        float f1 = Mth.wrapDegrees(this.getYRot() - this.yBodyRot);
        float f2 = this.getMaxHeadRotationRelativeToBody();
        if (Math.abs(f1) > f2) {
            this.yBodyRot = this.yBodyRot + (f1 - (float)Mth.sign((double)f1) * f2);
        }

        boolean flag = f1 < -90.0F || f1 >= 90.0F;
        if (flag) {
            p_21261_ *= -1.0F;
        }

        return p_21261_;
    }

    protected float getMaxHeadRotationRelativeToBody() {
        return 50.0F;
    }

    public void aiStep() {
        if (this.noJumpDelay > 0) {
            this.noJumpDelay--;
        }

        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }

        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        } else if (!this.isEffectiveAi()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        }

        if (this.lerpHeadSteps > 0) {
            this.lerpHeadRotationStep(this.lerpHeadSteps, this.lerpYHeadRot);
            this.lerpHeadSteps--;
        }

        Vec3 vec3 = this.getDeltaMovement();
        double d0 = vec3.x;
        double d1 = vec3.y;
        double d2 = vec3.z;
        if (Math.abs(vec3.x) < 0.003) {
            d0 = 0.0;
        }

        if (Math.abs(vec3.y) < 0.003) {
            d1 = 0.0;
        }

        if (Math.abs(vec3.z) < 0.003) {
            d2 = 0.0;
        }

        this.setDeltaMovement(d0, d1, d2);
        this.level().getProfiler().push("ai");
        if (this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        } else if (this.isEffectiveAi()) {
            this.level().getProfiler().push("newAi");
            this.serverAiStep();
            this.level().getProfiler().pop();
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("jump");
        if (this.jumping && this.isAffectedByFluids()) {
            double d3;
            net.neoforged.neoforge.fluids.FluidType fluidType = this.getMaxHeightFluidType();
            if (!fluidType.isAir()) d3 = this.getFluidTypeHeight(fluidType);
            else
            if (this.isInLava()) {
                d3 = this.getFluidHeight(FluidTags.LAVA);
            } else {
                d3 = this.getFluidHeight(FluidTags.WATER);
            }

            boolean flag = this.isInWater() && d3 > 0.0;
            double d4 = this.getFluidJumpThreshold();
            if (!flag || this.onGround() && !(d3 > d4)) {
                if (!this.isInLava() || this.onGround() && !(d3 > d4)) {
                    if (fluidType.isAir() || this.onGround() && !(d3 > d4)) {
                    if ((this.onGround() || flag && d3 <= d4) && this.noJumpDelay == 0) {
                        this.jumpFromGround();
                        this.noJumpDelay = 10;
                    }
                    } else this.jumpInFluid(fluidType);
                } else {
                    this.jumpInFluid(net.neoforged.neoforge.common.NeoForgeMod.LAVA_TYPE.value());
                }
            } else {
                this.jumpInFluid(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value());
            }
        } else {
            this.noJumpDelay = 0;
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("travel");
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
        this.updateFallFlying();
        AABB aabb = this.getBoundingBox();
        Vec3 vec31 = new Vec3((double)this.xxa, (double)this.yya, (double)this.zza);
        if (this.hasEffect(MobEffects.SLOW_FALLING) || this.hasEffect(MobEffects.LEVITATION)) {
            this.resetFallDistance();
        }

        label104: {
            if (this.getControllingPassenger() instanceof Player player && this.isAlive()) {
                this.travelRidden(player, vec31);
                break label104;
            }

            this.travel(vec31);
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("freezing");
        if (!this.level().isClientSide && !this.isDeadOrDying()) {
            int i = this.getTicksFrozen();
            if (this.isInPowderSnow && this.canFreeze()) {
                this.setTicksFrozen(Math.min(this.getTicksRequiredToFreeze(), i + 1));
            } else {
                this.setTicksFrozen(Math.max(0, i - 2));
            }
        }

        this.removeFrost();
        this.tryAddFrost();
        if (!this.level().isClientSide && this.tickCount % 40 == 0 && this.isFullyFrozen() && this.canFreeze()) {
            this.hurt(this.damageSources().freeze(), 1.0F);
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("push");
        if (this.autoSpinAttackTicks > 0) {
            this.autoSpinAttackTicks--;
            this.checkAutoSpinAttack(aabb, this.getBoundingBox());
        }

        this.pushEntities();
        this.level().getProfiler().pop();
        if (!this.level().isClientSide && this.isSensitiveToWater() && this.isInWaterRainOrBubble()) {
            this.hurt(this.damageSources().drown(), 1.0F);
        }
    }

    public boolean isSensitiveToWater() {
        return false;
    }

    private void updateFallFlying() {
        boolean flag = this.getSharedFlag(7);
        if (flag && !this.onGround() && !this.isPassenger() && !this.hasEffect(MobEffects.LEVITATION)) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.CHEST);
            flag = itemstack.canElytraFly(this) && itemstack.elytraFlightTick(this, this.fallFlyTicks);
            if (false) //Neo: Moved to ElytraItem
            if (itemstack.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(itemstack)) {
                flag = true;
                int i = this.fallFlyTicks + 1;
                if (!this.level().isClientSide && i % 10 == 0) {
                    int j = i / 10;
                    if (j % 2 == 0) {
                        itemstack.hurtAndBreak(1, this, EquipmentSlot.CHEST);
                    }

                    this.gameEvent(GameEvent.ELYTRA_GLIDE);
                }
            } else {
                flag = false;
            }
        } else {
            flag = false;
        }

        if (!this.level().isClientSide) {
            this.setSharedFlag(7, flag);
        }
    }

    protected void serverAiStep() {
    }

    protected void pushEntities() {
        if (this.level().isClientSide()) {
            this.level().getEntities(EntityTypeTest.forClass(Player.class), this.getBoundingBox(), EntitySelector.pushableBy(this)).forEach(this::doPush);
        } else {
            List<Entity> list = this.level().getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this));
            if (!list.isEmpty()) {
                int i = this.level().getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
                if (i > 0 && list.size() > i - 1 && this.random.nextInt(4) == 0) {
                    int j = 0;

                    for (Entity entity : list) {
                        if (!entity.isPassenger()) {
                            j++;
                        }
                    }

                    if (j > i - 1) {
                        this.hurt(this.damageSources().cramming(), 6.0F);
                    }
                }

                for (Entity entity1 : list) {
                    this.doPush(entity1);
                }
            }
        }
    }

    protected void checkAutoSpinAttack(AABB p_21072_, AABB p_21073_) {
        AABB aabb = p_21072_.minmax(p_21073_);
        List<Entity> list = this.level().getEntities(this, aabb);
        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (entity instanceof LivingEntity) {
                    this.doAutoAttackOnTouch((LivingEntity)entity);
                    this.autoSpinAttackTicks = 0;
                    this.setDeltaMovement(this.getDeltaMovement().scale(-0.2));
                    break;
                }
            }
        } else if (this.horizontalCollision) {
            this.autoSpinAttackTicks = 0;
        }

        if (!this.level().isClientSide && this.autoSpinAttackTicks <= 0) {
            this.setLivingEntityFlag(4, false);
            this.autoSpinAttackDmg = 0.0F;
            this.autoSpinAttackItemStack = null;
        }
    }

    protected void doPush(Entity p_20971_) {
        p_20971_.push(this);
    }

    protected void doAutoAttackOnTouch(LivingEntity p_21277_) {
    }

    public boolean isAutoSpinAttack() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 4) != 0;
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();
        super.stopRiding();
        if (entity != null && entity != this.getVehicle() && !this.level().isClientSide) {
            this.dismountVehicle(entity);
        }
    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.oRun = this.run;
        this.run = 0.0F;
        this.resetFallDistance();
    }

    @Override
    public void lerpTo(double p_20977_, double p_20978_, double p_20979_, float p_20980_, float p_20981_, int p_20982_) {
        this.lerpX = p_20977_;
        this.lerpY = p_20978_;
        this.lerpZ = p_20979_;
        this.lerpYRot = (double)p_20980_;
        this.lerpXRot = (double)p_20981_;
        this.lerpSteps = p_20982_;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float)this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float)this.lerpYRot : this.getYRot();
    }

    @Override
    public void lerpHeadTo(float p_21005_, int p_21006_) {
        this.lerpYHeadRot = (double)p_21005_;
        this.lerpHeadSteps = p_21006_;
    }

    public void setJumping(boolean p_21314_) {
        this.jumping = p_21314_;
    }

    public void onItemPickup(ItemEntity p_21054_) {
        Entity entity = p_21054_.getOwner();
        if (entity instanceof ServerPlayer) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_ENTITY.trigger((ServerPlayer)entity, p_21054_.getItem(), this);
        }
    }

    public void take(Entity p_21030_, int p_21031_) {
        if (!p_21030_.isRemoved()
            && !this.level().isClientSide
            && (p_21030_ instanceof ItemEntity || p_21030_ instanceof AbstractArrow || p_21030_ instanceof ExperienceOrb)) {
            ((ServerLevel)this.level()).getChunkSource().broadcast(p_21030_, new ClientboundTakeItemEntityPacket(p_21030_.getId(), this.getId(), p_21031_));
        }
    }

    public boolean hasLineOfSight(Entity p_147185_) {
        if (p_147185_.level() != this.level()) {
            return false;
        } else {
            Vec3 vec3 = new Vec3(this.getX(), this.getEyeY(), this.getZ());
            Vec3 vec31 = new Vec3(p_147185_.getX(), p_147185_.getEyeY(), p_147185_.getZ());
            return vec31.distanceTo(vec3) > 128.0
                ? false
                : this.level().clip(new ClipContext(vec3, vec31, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
        }
    }

    @Override
    public float getViewYRot(float p_21286_) {
        return p_21286_ == 1.0F ? this.yHeadRot : Mth.lerp(p_21286_, this.yHeadRotO, this.yHeadRot);
    }

    public float getAttackAnim(float p_21325_) {
        float f = this.attackAnim - this.oAttackAnim;
        if (f < 0.0F) {
            f++;
        }

        return this.oAttackAnim + f * p_21325_;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return this.isAlive() && !this.isSpectator() && !this.onClimbable();
    }

    @Override
    public float getYHeadRot() {
        return this.yHeadRot;
    }

    @Override
    public void setYHeadRot(float p_21306_) {
        this.yHeadRot = p_21306_;
    }

    @Override
    public void setYBodyRot(float p_21309_) {
        this.yBodyRot = p_21309_;
    }

    @Override
    public Vec3 getRelativePortalPosition(Direction.Axis p_21085_, BlockUtil.FoundRectangle p_21086_) {
        return resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(p_21085_, p_21086_));
    }

    public static Vec3 resetForwardDirectionOfRelativePortalPosition(Vec3 p_21290_) {
        return new Vec3(p_21290_.x, p_21290_.y, 0.0);
    }

    public float getAbsorptionAmount() {
        return this.absorptionAmount;
    }

    public final void setAbsorptionAmount(float p_21328_) {
        this.internalSetAbsorptionAmount(Mth.clamp(p_21328_, 0.0F, this.getMaxAbsorption()));
    }

    protected void internalSetAbsorptionAmount(float p_295258_) {
        this.absorptionAmount = p_295258_;
    }

    public void onEnterCombat() {
    }

    public void onLeaveCombat() {
    }

    protected void updateEffectVisibility() {
        this.effectsDirty = true;
    }

    public abstract HumanoidArm getMainArm();

    public boolean isUsingItem() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
    }

    public InteractionHand getUsedItemHand() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private void updatingUsingItem() {
        if (this.isUsingItem()) {
            ItemStack itemStack = this.getItemInHand(this.getUsedItemHand());
            if (net.neoforged.neoforge.common.CommonHooks.canContinueUsing(this.useItem, itemStack)) {
                this.useItem = itemStack;
            }
            if (itemStack == this.useItem) {
                this.updateUsingItem(this.useItem);
            } else {
                this.stopUsingItem();
            }
        }
    }

    protected void updateUsingItem(ItemStack p_147201_) {
        if (!p_147201_.isEmpty())
            this.useItemRemaining = net.neoforged.neoforge.event.EventHooks.onItemUseTick(this, p_147201_, this.getUseItemRemainingTicks());
        if (this.getUseItemRemainingTicks() > 0)
        p_147201_.onUseTick(this.level(), this, this.getUseItemRemainingTicks());
        if (this.shouldTriggerItemUseEffects()) {
            this.triggerItemUseEffects(p_147201_, 5);
        }

        if (--this.useItemRemaining <= 0 && !this.level().isClientSide && !p_147201_.useOnRelease()) {
            this.completeUsingItem();
        }
    }

    private boolean shouldTriggerItemUseEffects() {
        int i = this.useItem.getUseDuration(this) - this.getUseItemRemainingTicks();
        int j = (int)((float)this.useItem.getUseDuration(this) * 0.21875F);
        boolean flag = i > j;
        return flag && this.getUseItemRemainingTicks() % 4 == 0;
    }

    private void updateSwimAmount() {
        this.swimAmountO = this.swimAmount;
        if (this.isVisuallySwimming()) {
            this.swimAmount = Math.min(1.0F, this.swimAmount + 0.09F);
        } else {
            this.swimAmount = Math.max(0.0F, this.swimAmount - 0.09F);
        }
    }

    protected void setLivingEntityFlag(int p_21156_, boolean p_21157_) {
        int i = this.entityData.get(DATA_LIVING_ENTITY_FLAGS);
        if (p_21157_) {
            i |= p_21156_;
        } else {
            i &= ~p_21156_;
        }

        this.entityData.set(DATA_LIVING_ENTITY_FLAGS, (byte)i);
    }

    public void startUsingItem(InteractionHand p_21159_) {
        ItemStack itemstack = this.getItemInHand(p_21159_);
        if (!itemstack.isEmpty() && !this.isUsingItem()) {
            int duration = net.neoforged.neoforge.event.EventHooks.onItemUseStart(this, itemstack, p_21159_, itemstack.getUseDuration(this));
            if (duration < 0) return; // Neo: Early return for negative values, as that indicates event cancellation.
            this.useItem = itemstack;
            this.useItemRemaining = duration;
            if (!this.level().isClientSide) {
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, p_21159_ == InteractionHand.OFF_HAND);
                this.gameEvent(GameEvent.ITEM_INTERACT_START);
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> p_21104_) {
        super.onSyncedDataUpdated(p_21104_);
        if (SLEEPING_POS_ID.equals(p_21104_)) {
            if (this.level().isClientSide) {
                this.getSleepingPos().ifPresent(this::setPosToBed);
            }
        } else if (DATA_LIVING_ENTITY_FLAGS.equals(p_21104_) && this.level().isClientSide) {
            if (this.isUsingItem() && this.useItem.isEmpty()) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                if (!this.useItem.isEmpty()) {
                    this.useItemRemaining = this.useItem.getUseDuration(this);
                }
            } else if (!this.isUsingItem() && !this.useItem.isEmpty()) {
                this.useItem = ItemStack.EMPTY;
                this.useItemRemaining = 0;
            }
        }
    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor p_21078_, Vec3 p_21079_) {
        super.lookAt(p_21078_, p_21079_);
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRot = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    @Override
    public float getPreciseBodyRotation(float p_352089_) {
        return Mth.lerp(p_352089_, this.yBodyRotO, this.yBodyRot);
    }

    protected void triggerItemUseEffects(ItemStack p_21138_, int p_21139_) {
        if (!p_21138_.isEmpty() && this.isUsingItem()) {
            if (p_21138_.getUseAnimation() == UseAnim.DRINK) {
                this.playSound(this.getDrinkingSound(p_21138_), 0.5F, this.level().random.nextFloat() * 0.1F + 0.9F);
            }

            if (p_21138_.getUseAnimation() == UseAnim.EAT) {
                this.spawnItemParticles(p_21138_, p_21139_);
                this.playSound(
                    this.getEatingSound(p_21138_),
                    0.5F + 0.5F * (float)this.random.nextInt(2),
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F
                );
            }
        }
    }

    private void spawnItemParticles(ItemStack p_21061_, int p_21062_) {
        for (int i = 0; i < p_21062_; i++) {
            Vec3 vec3 = new Vec3(((double)this.random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0);
            vec3 = vec3.xRot(-this.getXRot() * (float) (Math.PI / 180.0));
            vec3 = vec3.yRot(-this.getYRot() * (float) (Math.PI / 180.0));
            double d0 = (double)(-this.random.nextFloat()) * 0.6 - 0.3;
            Vec3 vec31 = new Vec3(((double)this.random.nextFloat() - 0.5) * 0.3, d0, 0.6);
            vec31 = vec31.xRot(-this.getXRot() * (float) (Math.PI / 180.0));
            vec31 = vec31.yRot(-this.getYRot() * (float) (Math.PI / 180.0));
            vec31 = vec31.add(this.getX(), this.getEyeY(), this.getZ());
            this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, p_21061_), vec31.x, vec31.y, vec31.z, vec3.x, vec3.y + 0.05, vec3.z);
        }
    }

    protected void completeUsingItem() {
        if (!this.level().isClientSide || this.isUsingItem()) {
            InteractionHand interactionhand = this.getUsedItemHand();
            if (!this.useItem.equals(this.getItemInHand(interactionhand))) {
                this.releaseUsingItem();
            } else {
                if (!this.useItem.isEmpty() && this.isUsingItem()) {
                    this.triggerItemUseEffects(this.useItem, 16);
                    ItemStack copy = this.useItem.copy();
                    ItemStack itemstack = net.neoforged.neoforge.event.EventHooks.onItemUseFinish(this, copy, getUseItemRemainingTicks(), this.useItem.finishUsingItem(this.level(), this));
                    if (itemstack != this.useItem) {
                        this.setItemInHand(interactionhand, itemstack);
                    }

                    this.stopUsingItem();
                }
            }
        }
    }

    public ItemStack getUseItem() {
        return this.useItem;
    }

    public int getUseItemRemainingTicks() {
        return this.useItemRemaining;
    }

    public int getTicksUsingItem() {
        return this.isUsingItem() ? this.useItem.getUseDuration(this) - this.getUseItemRemainingTicks() : 0;
    }

    public void releaseUsingItem() {
        if (!this.useItem.isEmpty()) {
            if (!net.neoforged.neoforge.event.EventHooks.onUseItemStop(this, useItem, this.getUseItemRemainingTicks())) {
                ItemStack copy = this instanceof Player ? useItem.copy() : null;
            this.useItem.releaseUsing(this.level(), this, this.getUseItemRemainingTicks());
              if (copy != null && useItem.isEmpty()) net.neoforged.neoforge.event.EventHooks.onPlayerDestroyItem((Player)this, copy, getUsedItemHand());
            }
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        this.stopUsingItem();
    }

    public void stopUsingItem() {
        if (this.isUsingItem() && !this.useItem.isEmpty()) this.useItem.onStopUsing(this, useItemRemaining);
        if (!this.level().isClientSide) {
            boolean flag = this.isUsingItem();
            this.setLivingEntityFlag(1, false);
            if (flag) {
                this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            }
        }

        this.useItem = ItemStack.EMPTY;
        this.useItemRemaining = 0;
    }

    public boolean isBlocking() {
        if (this.isUsingItem() && !this.useItem.isEmpty()) {
            Item item = this.useItem.getItem();
            return !this.useItem.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK) ? false : item.getUseDuration(this.useItem, this) - this.useItemRemaining >= 5;
        } else {
            return false;
        }
    }

    public boolean isSuppressingSlidingDownLadder() {
        return this.isShiftKeyDown();
    }

    public boolean isFallFlying() {
        return this.getSharedFlag(7);
    }

    @Override
    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || !this.isFallFlying() && this.hasPose(Pose.FALL_FLYING);
    }

    public int getFallFlyingTicks() {
        return this.fallFlyTicks;
    }

    public boolean randomTeleport(double p_20985_, double p_20986_, double p_20987_, boolean p_20988_) {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        double d3 = p_20986_;
        boolean flag = false;
        BlockPos blockpos = BlockPos.containing(p_20985_, p_20986_, p_20987_);
        Level level = this.level();
        if (level.hasChunkAt(blockpos)) {
            boolean flag1 = false;

            while (!flag1 && blockpos.getY() > level.getMinBuildHeight()) {
                BlockPos blockpos1 = blockpos.below();
                BlockState blockstate = level.getBlockState(blockpos1);
                if (blockstate.blocksMotion()) {
                    flag1 = true;
                } else {
                    d3--;
                    blockpos = blockpos1;
                }
            }

            if (flag1) {
                this.teleportTo(p_20985_, d3, p_20987_);
                if (level.noCollision(this) && !level.containsAnyLiquid(this.getBoundingBox())) {
                    flag = true;
                }
            }
        }

        if (!flag) {
            this.teleportTo(d0, d1, d2);
            return false;
        } else {
            if (p_20988_) {
                level.broadcastEntityEvent(this, (byte)46);
            }

            if (this instanceof PathfinderMob pathfindermob) {
                pathfindermob.getNavigation().stop();
            }

            return true;
        }
    }

    public boolean isAffectedByPotions() {
        return !this.isDeadOrDying();
    }

    public boolean attackable() {
        return true;
    }

    public void setRecordPlayingNearby(BlockPos p_21082_, boolean p_21083_) {
    }

    public boolean canTakeItem(ItemStack p_21249_) {
        return false;
    }

    @Override
    public final EntityDimensions getDimensions(Pose p_21047_) {
        return p_21047_ == Pose.SLEEPING ? SLEEPING_DIMENSIONS : this.getDefaultDimensions(p_21047_).scale(this.getScale());
    }

    protected EntityDimensions getDefaultDimensions(Pose p_316700_) {
        return this.getType().getDimensions().scale(this.getAgeScale());
    }

    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING);
    }

    public AABB getLocalBoundsForPose(Pose p_21271_) {
        EntityDimensions entitydimensions = this.getDimensions(p_21271_);
        return new AABB(
            (double)(-entitydimensions.width() / 2.0F),
            0.0,
            (double)(-entitydimensions.width() / 2.0F),
            (double)(entitydimensions.width() / 2.0F),
            (double)entitydimensions.height(),
            (double)(entitydimensions.width() / 2.0F)
        );
    }

    protected boolean wouldNotSuffocateAtTargetPose(Pose p_294977_) {
        AABB aabb = this.getDimensions(p_294977_).makeBoundingBox(this.position());
        return this.level().noBlockCollision(this, aabb);
    }

    @Override
    public boolean canUsePortal(boolean p_352946_) {
        return super.canUsePortal(p_352946_) && !this.isSleeping();
    }

    public Optional<BlockPos> getSleepingPos() {
        return this.entityData.get(SLEEPING_POS_ID);
    }

    public void setSleepingPos(BlockPos p_21251_) {
        this.entityData.set(SLEEPING_POS_ID, Optional.of(p_21251_));
    }

    public void clearSleepingPos() {
        this.entityData.set(SLEEPING_POS_ID, Optional.empty());
    }

    public boolean isSleeping() {
        return this.getSleepingPos().isPresent();
    }

    public void startSleeping(BlockPos p_21141_) {
        if (this.isPassenger()) {
            this.stopRiding();
        }

        BlockState blockstate = this.level().getBlockState(p_21141_);
        if (blockstate.isBed(level(), p_21141_, this)) {
            blockstate.setBedOccupied(level(), p_21141_, this, true);
        }

        this.setPose(Pose.SLEEPING);
        this.setPosToBed(p_21141_);
        this.setSleepingPos(p_21141_);
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    private void setPosToBed(BlockPos p_21081_) {
        this.setPos((double)p_21081_.getX() + 0.5, (double)p_21081_.getY() + 0.6875, (double)p_21081_.getZ() + 0.5);
    }

    private boolean checkBedExists() {
        // Neo: Overwrite the vanilla instanceof BedBlock check with isBed and fire the CanContinueSleepingEvent.
        boolean hasBed = this.getSleepingPos().map(pos -> this.level().getBlockState(pos).isBed(this.level(), pos, this)).orElse(false);
        return net.neoforged.neoforge.event.EventHooks.canEntityContinueSleeping(this, hasBed ? null : Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
    }

    public void stopSleeping() {
        this.getSleepingPos().filter(this.level()::hasChunkAt).ifPresent(p_261435_ -> {
            BlockState blockstate = this.level().getBlockState(p_261435_);
            if (blockstate.isBed(level(), p_261435_, this)) {
                Direction direction = blockstate.getValue(BedBlock.FACING);
                blockstate.setBedOccupied(level(), p_261435_, this, false);
                Vec3 vec31 = BedBlock.findStandUpPosition(this.getType(), this.level(), p_261435_, direction, this.getYRot()).orElseGet(() -> {
                    BlockPos blockpos = p_261435_.above();
                    return new Vec3((double)blockpos.getX() + 0.5, (double)blockpos.getY() + 0.1, (double)blockpos.getZ() + 0.5);
                });
                Vec3 vec32 = Vec3.atBottomCenterOf(p_261435_).subtract(vec31).normalize();
                float f = (float)Mth.wrapDegrees(Mth.atan2(vec32.z, vec32.x) * 180.0F / (float)Math.PI - 90.0);
                this.setPos(vec31.x, vec31.y, vec31.z);
                this.setYRot(f);
                this.setXRot(0.0F);
            }
        });
        Vec3 vec3 = this.position();
        this.setPose(Pose.STANDING);
        this.setPos(vec3.x, vec3.y, vec3.z);
        this.clearSleepingPos();
    }

    @Nullable
    public Direction getBedOrientation() {
        BlockPos blockpos = this.getSleepingPos().orElse(null);
        if (blockpos == null) return Direction.UP;
        BlockState state = this.level().getBlockState(blockpos);
        return !state.isBed(level(), blockpos, this) ? Direction.UP : state.getBedDirection(level(), blockpos);
    }

    @Override
    public boolean isInWall() {
        return !this.isSleeping() && super.isInWall();
    }

    public ItemStack getProjectile(ItemStack p_21272_) {
        return net.neoforged.neoforge.common.CommonHooks.getProjectile(this, p_21272_, ItemStack.EMPTY);
    }

    public final ItemStack eat(Level p_21067_, ItemStack p_21068_) {
        FoodProperties foodproperties = p_21068_.getFoodProperties(this);
        return foodproperties != null ? this.eat(p_21067_, p_21068_, foodproperties) : p_21068_;
    }

    public ItemStack eat(Level p_347678_, ItemStack p_347507_, FoodProperties p_347707_) {
        p_347678_.playSound(
            null,
            this.getX(),
            this.getY(),
            this.getZ(),
            this.getEatingSound(p_347507_),
            SoundSource.NEUTRAL,
            1.0F,
            1.0F + (p_347678_.random.nextFloat() - p_347678_.random.nextFloat()) * 0.4F
        );
        this.addEatEffect(p_347707_);
        p_347507_.consume(1, this);
        this.gameEvent(GameEvent.EAT);
        return p_347507_;
    }

    private void addEatEffect(FoodProperties p_335472_) {
        if (!this.level().isClientSide()) {
            for (FoodProperties.PossibleEffect foodproperties$possibleeffect : p_335472_.effects()) {
                if (this.random.nextFloat() < foodproperties$possibleeffect.probability()) {
                    this.addEffect(foodproperties$possibleeffect.effect());
                }
            }
        }
    }

    private static byte entityEventForEquipmentBreak(EquipmentSlot p_21267_) {
        return switch (p_21267_) {
            case MAINHAND -> 47;
            case OFFHAND -> 48;
            case HEAD -> 49;
            case CHEST -> 50;
            case FEET -> 52;
            case LEGS -> 51;
            case BODY -> 65;
        };
    }

    public void onEquippedItemBroken(Item p_348620_, EquipmentSlot p_348655_) {
        this.level().broadcastEntityEvent(this, entityEventForEquipmentBreak(p_348655_));
    }

    public static EquipmentSlot getSlotForHand(InteractionHand p_320526_) {
        return p_320526_ == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    /**
     * Neo: Removes all potion effects that have the given {@link net.neoforged.neoforge.common.EffectCure} in their set of cures
     * @param cure the EffectCure being used
     */
    public boolean removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCure cure) {
        if (this.level().isClientSide)
            return false;
        boolean ret = false;
        Iterator<MobEffectInstance> itr = this.activeEffects.values().iterator();
        while (itr.hasNext()) {
            MobEffectInstance effect = itr.next();
            if (effect.getCures().contains(cure) && !net.neoforged.neoforge.event.EventHooks.onEffectRemoved(this, effect, cure)) {
                this.onEffectRemoved(effect);
                itr.remove();
                ret = true;
                this.effectsDirty = true;
            }
        }
        return ret;
    }

    /**
     * Neo: Returns true if the entity's rider (EntityPlayer) should face forward when mounted.
     * currently only used in vanilla code by pigs.
     *
     * @param player The player who is riding the entity.
     * @return If the player should orient the same direction as this entity.
     */
    public boolean shouldRiderFaceForward(Player player) {
        return this instanceof net.minecraft.world.entity.animal.Pig;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (this.getItemBySlot(EquipmentSlot.HEAD).is(Items.DRAGON_HEAD)) {
            float f = 0.5F;
            return this.getBoundingBox().inflate(0.5, 0.5, 0.5);
        } else {
            return super.getBoundingBoxForCulling();
        }
    }

    public EquipmentSlot getEquipmentSlotForItem(ItemStack p_147234_) {
        final EquipmentSlot slot = p_147234_.getEquipmentSlot();
        if (slot != null) return slot; // FORGE: Allow modders to set a non-default equipment slot for a stack; e.g. a non-armor chestplate-slot item
        Equipable equipable = Equipable.get(p_147234_);
        if (equipable != null) {
            EquipmentSlot equipmentslot = equipable.getEquipmentSlot();
            if (this.canUseSlot(equipmentslot)) {
                return equipmentslot;
            }
        }

        return EquipmentSlot.MAINHAND;
    }

    private static SlotAccess createEquipmentSlotAccess(LivingEntity p_147196_, EquipmentSlot p_147197_) {
        return p_147197_ != EquipmentSlot.HEAD && p_147197_ != EquipmentSlot.MAINHAND && p_147197_ != EquipmentSlot.OFFHAND
            ? SlotAccess.forEquipmentSlot(p_147196_, p_147197_, p_348156_ -> p_348156_.isEmpty() || p_147196_.getEquipmentSlotForItem(p_348156_) == p_147197_)
            : SlotAccess.forEquipmentSlot(p_147196_, p_147197_);
    }

    @Nullable
    private static EquipmentSlot getEquipmentSlot(int p_147212_) {
        if (p_147212_ == 100 + EquipmentSlot.HEAD.getIndex()) {
            return EquipmentSlot.HEAD;
        } else if (p_147212_ == 100 + EquipmentSlot.CHEST.getIndex()) {
            return EquipmentSlot.CHEST;
        } else if (p_147212_ == 100 + EquipmentSlot.LEGS.getIndex()) {
            return EquipmentSlot.LEGS;
        } else if (p_147212_ == 100 + EquipmentSlot.FEET.getIndex()) {
            return EquipmentSlot.FEET;
        } else if (p_147212_ == 98) {
            return EquipmentSlot.MAINHAND;
        } else if (p_147212_ == 99) {
            return EquipmentSlot.OFFHAND;
        } else {
            return p_147212_ == 105 ? EquipmentSlot.BODY : null;
        }
    }

    @Override
    public SlotAccess getSlot(int p_147238_) {
        EquipmentSlot equipmentslot = getEquipmentSlot(p_147238_);
        return equipmentslot != null ? createEquipmentSlotAccess(this, equipmentslot) : super.getSlot(p_147238_);
    }

    @Override
    public boolean canFreeze() {
        if (this.isSpectator()) {
            return false;
        } else {
            boolean flag = !this.getItemBySlot(EquipmentSlot.HEAD).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.CHEST).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.LEGS).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.FEET).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.BODY).is(ItemTags.FREEZE_IMMUNE_WEARABLES);
            return flag && super.canFreeze();
        }
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return !this.level().isClientSide() && this.hasEffect(MobEffects.GLOWING) || super.isCurrentlyGlowing();
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return this.yBodyRot;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket p_217037_) {
        double d0 = p_217037_.getX();
        double d1 = p_217037_.getY();
        double d2 = p_217037_.getZ();
        float f = p_217037_.getYRot();
        float f1 = p_217037_.getXRot();
        this.syncPacketPositionCodec(d0, d1, d2);
        this.yBodyRot = p_217037_.getYHeadRot();
        this.yHeadRot = p_217037_.getYHeadRot();
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.setId(p_217037_.getId());
        this.setUUID(p_217037_.getUUID());
        this.absMoveTo(d0, d1, d2, f, f1);
        this.setDeltaMovement(p_217037_.getXa(), p_217037_.getYa(), p_217037_.getZa());
    }

    public boolean canDisableShield() {
        return this.getMainHandItem().canDisableShield(this.useItem, this, this); // Neo: Switch to using the item to determine if disables shield instead of hardcoded Axe check
    }

    @Override
    public float maxUpStep() {
        float f = (float)this.getAttributeValue(Attributes.STEP_HEIGHT);
        return this.getControllingPassenger() instanceof Player ? Math.max(f, 1.0F) : f;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity p_295664_) {
        return this.position().add(this.getPassengerAttachmentPoint(p_295664_, this.getDimensions(this.getPose()), this.getScale() * this.getAgeScale()));
    }

    protected void lerpHeadRotationStep(int p_296134_, double p_296397_) {
        this.yHeadRot = (float)Mth.rotLerp(1.0 / (double)p_296134_, (double)this.yHeadRot, p_296397_);
    }

    @Override
    public void igniteForTicks(int p_319861_) {
        super.igniteForTicks(Mth.ceil((double)p_319861_ * this.getAttributeValue(Attributes.BURNING_TIME)));
    }

    public boolean hasInfiniteMaterials() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource p_345073_) {
        if (super.isInvulnerableTo(p_345073_)) {
            return true;
        } else {
            if (this.level() instanceof ServerLevel serverlevel && EnchantmentHelper.isImmuneToDamage(serverlevel, this, p_345073_)) {
                return true;
            }

            return false;
        }
    }

    public static record Fallsounds(SoundEvent small, SoundEvent big) {
    }
}
