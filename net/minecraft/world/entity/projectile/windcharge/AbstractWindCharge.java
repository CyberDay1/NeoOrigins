package net.minecraft.world.entity.projectile.windcharge;

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractWindCharge extends AbstractHurtingProjectile implements ItemSupplier {
    public static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(
        true, false, Optional.empty(), BuiltInRegistries.BLOCK.getTag(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())
    );
    public static final double JUMP_SCALE = 0.25;

    public AbstractWindCharge(EntityType<? extends AbstractWindCharge> p_325927_, Level p_326350_) {
        super(p_325927_, p_326350_);
        this.accelerationPower = 0.0;
    }

    public AbstractWindCharge(
        EntityType<? extends AbstractWindCharge> p_326427_, Level p_325931_, Entity p_325997_, double p_326275_, double p_325936_, double p_326369_
    ) {
        super(p_326427_, p_326275_, p_325936_, p_326369_, p_325931_);
        this.setOwner(p_325997_);
        this.accelerationPower = 0.0;
    }

    AbstractWindCharge(
        EntityType<? extends AbstractWindCharge> p_326232_, double p_326236_, double p_326440_, double p_326413_, Vec3 p_347459_, Level p_326449_
    ) {
        super(p_326232_, p_326236_, p_326440_, p_326413_, p_347459_, p_326449_);
        this.accelerationPower = 0.0;
    }

    @Override
    protected AABB makeBoundingBox() {
        float f = this.getType().getDimensions().width() / 2.0F;
        float f1 = this.getType().getDimensions().height();
        float f2 = 0.15F;
        return new AABB(
            this.position().x - (double)f,
            this.position().y - 0.15F,
            this.position().z - (double)f,
            this.position().x + (double)f,
            this.position().y - 0.15F + (double)f1,
            this.position().z + (double)f
        );
    }

    @Override
    public boolean canCollideWith(Entity p_326023_) {
        return p_326023_ instanceof AbstractWindCharge ? false : super.canCollideWith(p_326023_);
    }

    @Override
    protected boolean canHitEntity(Entity p_326159_) {
        if (p_326159_ instanceof AbstractWindCharge) {
            return false;
        } else {
            return p_326159_.getType() == EntityType.END_CRYSTAL ? false : super.canHitEntity(p_326159_);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult p_326121_) {
        super.onHitEntity(p_326121_);
        if (!this.level().isClientSide) {
            LivingEntity livingentity = this.getOwner() instanceof LivingEntity livingentity1 ? livingentity1 : null;
            Entity entity = p_326121_.getEntity();
            if (livingentity != null) {
                livingentity.setLastHurtMob(entity);
            }

            DamageSource damagesource = this.damageSources().windCharge(this, livingentity);
            if (entity.hurt(damagesource, 1.0F) && entity instanceof LivingEntity livingentity2) {
                EnchantmentHelper.doPostAttackEffects((ServerLevel)this.level(), livingentity2, damagesource);
            }

            this.explode(this.position());
        }
    }

    @Override
    public void push(double p_334071_, double p_333979_, double p_333996_) {
    }

    protected abstract void explode(Vec3 p_352265_);

    @Override
    protected void onHitBlock(BlockHitResult p_325933_) {
        super.onHitBlock(p_325933_);
        if (!this.level().isClientSide) {
            Vec3i vec3i = p_325933_.getDirection().getNormal();
            Vec3 vec3 = Vec3.atLowerCornerOf(vec3i).multiply(0.25, 0.25, 0.25);
            Vec3 vec31 = p_325933_.getLocation().add(vec3);
            this.explode(vec31);
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult p_326337_) {
        super.onHit(p_326337_);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public ItemStack getItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected float getLiquidInertia() {
        return this.getInertia();
    }

    @Nullable
    @Override
    protected ParticleOptions getTrailParticle() {
        return null;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.getBlockY() > this.level().getMaxBuildHeight() + 30) {
            this.explode(this.position());
            this.discard();
        } else {
            super.tick();
        }
    }

    @Override
    public boolean hurt(DamageSource p_352147_, float p_352222_) {
        return false;
    }
}
