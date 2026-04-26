package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class Bogged extends AbstractSkeleton implements Shearable {
    private static final int HARD_ATTACK_INTERVAL = 50;
    private static final int NORMAL_ATTACK_INTERVAL = 70;
    private static final EntityDataAccessor<Boolean> DATA_SHEARED = SynchedEntityData.defineId(Bogged.class, EntityDataSerializers.BOOLEAN);
    public static final String SHEARED_TAG_NAME = "sheared";

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes().add(Attributes.MAX_HEALTH, 16.0);
    }

    public Bogged(EntityType<? extends Bogged> p_326920_, Level p_326946_) {
        super(p_326920_, p_326946_);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_331269_) {
        super.defineSynchedData(p_331269_);
        p_331269_.define(DATA_SHEARED, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_331631_) {
        super.addAdditionalSaveData(p_331631_);
        p_331631_.putBoolean("sheared", this.isSheared());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_331225_) {
        super.readAdditionalSaveData(p_331225_);
        this.setSheared(p_331225_.getBoolean("sheared"));
    }

    public boolean isSheared() {
        return this.entityData.get(DATA_SHEARED);
    }

    public void setSheared(boolean p_330515_) {
        this.entityData.set(DATA_SHEARED, p_330515_);
    }

    @Override
    protected InteractionResult mobInteract(Player p_330736_, InteractionHand p_331786_) {
        ItemStack itemstack = p_330736_.getItemInHand(p_331786_);
        if (false && itemstack.is(Items.SHEARS) && this.readyForShearing()) { // Neo: Shear logic is handled by IShearable
            this.shear(SoundSource.PLAYERS);
            this.gameEvent(GameEvent.SHEAR, p_330736_);
            if (!this.level().isClientSide) {
                itemstack.hurtAndBreak(1, p_330736_, getSlotForHand(p_331786_));
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else {
            return super.mobInteract(p_330736_, p_331786_);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BOGGED_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource p_326909_) {
        return SoundEvents.BOGGED_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BOGGED_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.BOGGED_STEP;
    }

    @Override
    protected AbstractArrow getArrow(ItemStack p_326801_, float p_326846_, @Nullable ItemStack p_344869_) {
        AbstractArrow abstractarrow = super.getArrow(p_326801_, p_326846_, p_344869_);
        if (abstractarrow instanceof Arrow arrow) {
            arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        }

        return abstractarrow;
    }

    @Override
    protected int getHardAttackInterval() {
        return 50;
    }

    @Override
    protected int getAttackInterval() {
        return 70;
    }

    @Override
    public void shear(SoundSource p_331493_) {
        this.level().playSound(null, this, SoundEvents.BOGGED_SHEAR, p_331493_, 1.0F, 1.0F);
        this.spawnShearedMushrooms();
        this.setSheared(true);
    }

    private void spawnShearedMushrooms() {
        if (this.level() instanceof ServerLevel serverlevel) {
            LootTable loottable = serverlevel.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.BOGGED_SHEAR);
            LootParams lootparams = new LootParams.Builder(serverlevel)
                .withParameter(LootContextParams.ORIGIN, this.position())
                .withParameter(LootContextParams.THIS_ENTITY, this)
                .create(LootContextParamSets.SHEARING);

            for (ItemStack itemstack : loottable.getRandomItems(lootparams)) {
                this.spawnAtLocation(itemstack, this.getBbHeight());
            }
        }
    }

    @Override
    public boolean readyForShearing() {
        return !this.isSheared() && this.isAlive();
    }
}
