package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

public class Arrow extends AbstractArrow {
    private static final int EXPOSED_POTION_DECAY_TIME = 600;
    private static final int NO_EFFECT_COLOR = -1;
    private static final EntityDataAccessor<Integer> ID_EFFECT_COLOR = SynchedEntityData.defineId(Arrow.class, EntityDataSerializers.INT);
    private static final byte EVENT_POTION_PUFF = 0;

    public Arrow(EntityType<? extends Arrow> p_36858_, Level p_36859_) {
        super(p_36858_, p_36859_);
    }

    public Arrow(Level p_36861_, double p_345459_, double p_346135_, double p_345618_, ItemStack p_309188_, @Nullable ItemStack p_345233_) {
        super(EntityType.ARROW, p_345459_, p_346135_, p_345618_, p_36861_, p_309188_, p_345233_);
        this.updateColor();
    }

    public Arrow(Level p_36866_, LivingEntity p_345746_, ItemStack p_309200_, @Nullable ItemStack p_345461_) {
        super(EntityType.ARROW, p_345746_, p_36866_, p_309200_, p_345461_);
        this.updateColor();
    }

    private PotionContents getPotionContents() {
        return this.getPickupItemStackOrigin().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
    }

    private void setPotionContents(PotionContents p_331534_) {
        this.getPickupItemStackOrigin().set(DataComponents.POTION_CONTENTS, p_331534_);
        this.updateColor();
    }

    @Override
    protected void setPickupItemStack(ItemStack p_331667_) {
        super.setPickupItemStack(p_331667_);
        this.updateColor();
    }

    private void updateColor() {
        PotionContents potioncontents = this.getPotionContents();
        this.entityData.set(ID_EFFECT_COLOR, potioncontents.equals(PotionContents.EMPTY) ? -1 : potioncontents.getColor());
    }

    public void addEffect(MobEffectInstance p_36871_) {
        this.setPotionContents(this.getPotionContents().withEffectAdded(p_36871_));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_326324_) {
        super.defineSynchedData(p_326324_);
        p_326324_.define(ID_EFFECT_COLOR, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.inGround) {
                if (this.inGroundTime % 5 == 0) {
                    this.makeParticle(1);
                }
            } else {
                this.makeParticle(2);
            }
        } else if (this.inGround && this.inGroundTime != 0 && !this.getPotionContents().equals(PotionContents.EMPTY) && this.inGroundTime >= 600) {
            this.level().broadcastEntityEvent(this, (byte)0);
            this.setPickupItemStack(new ItemStack(Items.ARROW));
        }
    }

    private void makeParticle(int p_36877_) {
        int i = this.getColor();
        if (i != -1 && p_36877_ > 0) {
            for (int j = 0; j < p_36877_; j++) {
                this.level()
                    .addParticle(
                        ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, i),
                        this.getRandomX(0.5),
                        this.getRandomY(),
                        this.getRandomZ(0.5),
                        0.0,
                        0.0,
                        0.0
                    );
            }
        }
    }

    public int getColor() {
        return this.entityData.get(ID_EFFECT_COLOR);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity p_36873_) {
        super.doPostHurtEffects(p_36873_);
        Entity entity = this.getEffectSource();
        PotionContents potioncontents = this.getPotionContents();
        if (potioncontents.potion().isPresent()) {
            for (MobEffectInstance mobeffectinstance : potioncontents.potion().get().value().getEffects()) {
                p_36873_.addEffect(
                    new MobEffectInstance(
                        mobeffectinstance.getEffect(),
                        Math.max(mobeffectinstance.mapDuration(p_268168_ -> p_268168_ / 8), 1),
                        mobeffectinstance.getAmplifier(),
                        mobeffectinstance.isAmbient(),
                        mobeffectinstance.isVisible()
                    ),
                    entity
                );
            }
        }

        for (MobEffectInstance mobeffectinstance1 : potioncontents.customEffects()) {
            p_36873_.addEffect(mobeffectinstance1, entity);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    public void handleEntityEvent(byte p_36869_) {
        if (p_36869_ == 0) {
            int i = this.getColor();
            if (i != -1) {
                float f = (float)(i >> 16 & 0xFF) / 255.0F;
                float f1 = (float)(i >> 8 & 0xFF) / 255.0F;
                float f2 = (float)(i >> 0 & 0xFF) / 255.0F;

                for (int j = 0; j < 20; j++) {
                    this.level()
                        .addParticle(
                            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, f, f1, f2),
                            this.getRandomX(0.5),
                            this.getRandomY(),
                            this.getRandomZ(0.5),
                            0.0,
                            0.0,
                            0.0
                        );
                }
            }
        } else {
            super.handleEntityEvent(p_36869_);
        }
    }
}
