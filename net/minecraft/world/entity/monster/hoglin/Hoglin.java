package net.minecraft.world.entity.monster.hoglin;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Hoglin extends Animal implements Enemy, HoglinBase {
    private static final EntityDataAccessor<Boolean> DATA_IMMUNE_TO_ZOMBIFICATION = SynchedEntityData.defineId(Hoglin.class, EntityDataSerializers.BOOLEAN);
    private static final float PROBABILITY_OF_SPAWNING_AS_BABY = 0.2F;
    private static final int MAX_HEALTH = 40;
    private static final float MOVEMENT_SPEED_WHEN_FIGHTING = 0.3F;
    private static final int ATTACK_KNOCKBACK = 1;
    private static final float KNOCKBACK_RESISTANCE = 0.6F;
    private static final int ATTACK_DAMAGE = 6;
    private static final float BABY_ATTACK_DAMAGE = 0.5F;
    private static final int CONVERSION_TIME = 300;
    private int attackAnimationRemainingTicks;
    private int timeInOverworld;
    private boolean cannotBeHunted;
    protected static final ImmutableList<? extends SensorType<? extends Sensor<? super Hoglin>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ADULT, SensorType.HOGLIN_SPECIFIC_SENSOR
    );
    protected static final ImmutableList<? extends MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.NEAREST_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.PATH,
        MemoryModuleType.ATTACK_TARGET,
        MemoryModuleType.ATTACK_COOLING_DOWN,
        MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN,
        MemoryModuleType.AVOID_TARGET,
        MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT,
        MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT,
        MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS,
        MemoryModuleType.NEAREST_VISIBLE_ADULT,
        MemoryModuleType.NEAREST_REPELLENT,
        MemoryModuleType.PACIFIED,
        MemoryModuleType.IS_PANICKING
    );

    public Hoglin(EntityType<? extends Hoglin> p_34488_, Level p_34489_) {
        super(p_34488_, p_34489_);
        this.xpReward = 5;
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6F)
            .add(Attributes.ATTACK_KNOCKBACK, 1.0)
            .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override
    public boolean doHurtTarget(Entity p_34491_) {
        if (!(p_34491_ instanceof LivingEntity)) {
            return false;
        } else {
            this.attackAnimationRemainingTicks = 10;
            this.level().broadcastEntityEvent(this, (byte)4);
            this.makeSound(SoundEvents.HOGLIN_ATTACK);
            HoglinAi.onHitTarget(this, (LivingEntity)p_34491_);
            return HoglinBase.hurtAndThrowTarget(this, (LivingEntity)p_34491_);
        }
    }

    @Override
    protected void blockedByShield(LivingEntity p_34550_) {
        if (this.isAdult()) {
            HoglinBase.throwTarget(this, p_34550_);
        }
    }

    @Override
    public boolean hurt(DamageSource p_34503_, float p_34504_) {
        boolean flag = super.hurt(p_34503_, p_34504_);
        if (this.level().isClientSide) {
            return false;
        } else {
            if (flag && p_34503_.getEntity() instanceof LivingEntity) {
                HoglinAi.wasHurtBy(this, (LivingEntity)p_34503_.getEntity());
            }

            return flag;
        }
    }

    @Override
    protected Brain.Provider<Hoglin> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> p_34514_) {
        return HoglinAi.makeBrain(this.brainProvider().makeBrain(p_34514_));
    }

    @Override
    public Brain<Hoglin> getBrain() {
        return (Brain<Hoglin>)super.getBrain();
    }

    @Override
    protected void customServerAiStep() {
        this.level().getProfiler().push("hoglinBrain");
        this.getBrain().tick((ServerLevel)this.level(), this);
        this.level().getProfiler().pop();
        HoglinAi.updateActivity(this);
        if (this.isConverting()) {
            this.timeInOverworld++;
            if (this.timeInOverworld > 300 && net.neoforged.neoforge.event.EventHooks.canLivingConvert(this, EntityType.ZOGLIN, (timer) -> this.timeInOverworld = timer)) {
                this.makeSound(SoundEvents.HOGLIN_CONVERTED_TO_ZOMBIFIED);
                this.finishConversion((ServerLevel)this.level());
            }
        } else {
            this.timeInOverworld = 0;
        }
    }

    @Override
    public void aiStep() {
        if (this.attackAnimationRemainingTicks > 0) {
            this.attackAnimationRemainingTicks--;
        }

        super.aiStep();
    }

    @Override
    protected void ageBoundaryReached() {
        if (this.isBaby()) {
            this.xpReward = 3;
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.5);
        } else {
            this.xpReward = 5;
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0);
        }
    }

    public static boolean checkHoglinSpawnRules(
        EntityType<Hoglin> p_219182_, LevelAccessor p_219183_, MobSpawnType p_219184_, BlockPos p_219185_, RandomSource p_219186_
    ) {
        return !p_219183_.getBlockState(p_219185_.below()).is(Blocks.NETHER_WART_BLOCK);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor p_34508_, DifficultyInstance p_34509_, MobSpawnType p_34510_, @Nullable SpawnGroupData p_34511_) {
        if (p_34508_.getRandom().nextFloat() < 0.2F) {
            this.setBaby(true);
        }

        return super.finalizeSpawn(p_34508_, p_34509_, p_34510_, p_34511_);
    }

    @Override
    public boolean removeWhenFarAway(double p_34559_) {
        return !this.isPersistenceRequired();
    }

    @Override
    public float getWalkTargetValue(BlockPos p_34516_, LevelReader p_34517_) {
        if (HoglinAi.isPosNearNearestRepellent(this, p_34516_)) {
            return -1.0F;
        } else {
            return p_34517_.getBlockState(p_34516_.below()).is(Blocks.CRIMSON_NYLIUM) ? 10.0F : 0.0F;
        }
    }

    @Override
    public InteractionResult mobInteract(Player p_34523_, InteractionHand p_34524_) {
        InteractionResult interactionresult = super.mobInteract(p_34523_, p_34524_);
        if (interactionresult.consumesAction()) {
            this.setPersistenceRequired();
        }

        return interactionresult;
    }

    @Override
    public void handleEntityEvent(byte p_34496_) {
        if (p_34496_ == 4) {
            this.attackAnimationRemainingTicks = 10;
            this.makeSound(SoundEvents.HOGLIN_ATTACK);
        } else {
            super.handleEntityEvent(p_34496_);
        }
    }

    @Override
    public int getAttackAnimationRemainingTicks() {
        return this.attackAnimationRemainingTicks;
    }

    @Override
    public boolean shouldDropExperience() {
        return true;
    }

    @Override
    protected int getBaseExperienceReward() {
        return this.xpReward;
    }

    private void finishConversion(ServerLevel p_34532_) {
        Zoglin zoglin = this.convertTo(EntityType.ZOGLIN, true);
        if (zoglin != null) {
            zoglin.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            net.neoforged.neoforge.event.EventHooks.onLivingConvert(this, zoglin);
        }
    }

    @Override
    public boolean isFood(ItemStack p_34562_) {
        return p_34562_.is(ItemTags.HOGLIN_FOOD);
    }

    public boolean isAdult() {
        return !this.isBaby();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_326309_) {
        super.defineSynchedData(p_326309_);
        p_326309_.define(DATA_IMMUNE_TO_ZOMBIFICATION, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_34529_) {
        super.addAdditionalSaveData(p_34529_);
        if (this.isImmuneToZombification()) {
            p_34529_.putBoolean("IsImmuneToZombification", true);
        }

        p_34529_.putInt("TimeInOverworld", this.timeInOverworld);
        if (this.cannotBeHunted) {
            p_34529_.putBoolean("CannotBeHunted", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_34519_) {
        super.readAdditionalSaveData(p_34519_);
        this.setImmuneToZombification(p_34519_.getBoolean("IsImmuneToZombification"));
        this.timeInOverworld = p_34519_.getInt("TimeInOverworld");
        this.setCannotBeHunted(p_34519_.getBoolean("CannotBeHunted"));
    }

    public void setImmuneToZombification(boolean p_34565_) {
        this.getEntityData().set(DATA_IMMUNE_TO_ZOMBIFICATION, p_34565_);
    }

    private boolean isImmuneToZombification() {
        return this.getEntityData().get(DATA_IMMUNE_TO_ZOMBIFICATION);
    }

    public boolean isConverting() {
        return !this.level().dimensionType().piglinSafe() && !this.isImmuneToZombification() && !this.isNoAi();
    }

    private void setCannotBeHunted(boolean p_34567_) {
        this.cannotBeHunted = p_34567_;
    }

    public boolean canBeHunted() {
        return this.isAdult() && !this.cannotBeHunted;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel p_149900_, AgeableMob p_149901_) {
        Hoglin hoglin = EntityType.HOGLIN.create(p_149900_);
        if (hoglin != null) {
            hoglin.setPersistenceRequired();
        }

        return hoglin;
    }

    @Override
    public boolean canFallInLove() {
        return !HoglinAi.isPacified(this) && super.canFallInLove();
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.level().isClientSide ? null : HoglinAi.getSoundForCurrentActivity(this).orElse(null);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource p_34548_) {
        return SoundEvents.HOGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HOGLIN_DEATH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.HOSTILE_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.HOSTILE_SPLASH;
    }

    @Override
    protected void playStepSound(BlockPos p_34526_, BlockState p_34527_) {
        this.playSound(SoundEvents.HOGLIN_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendEntityBrain(this);
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return this.getTargetFromBrain();
    }
}
