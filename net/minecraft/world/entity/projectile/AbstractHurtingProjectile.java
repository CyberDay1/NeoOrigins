package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractHurtingProjectile extends Projectile {
    public static final double INITAL_ACCELERATION_POWER = 0.1;
    public static final double DEFLECTION_SCALE = 0.5;
    public double accelerationPower = 0.1;

    protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> p_36833_, Level p_36834_) {
        super(p_36833_, p_36834_);
    }

    protected AbstractHurtingProjectile(
        EntityType<? extends AbstractHurtingProjectile> p_312669_, double p_312561_, double p_312829_, double p_312607_, Level p_312011_
    ) {
        this(p_312669_, p_312011_);
        this.setPos(p_312561_, p_312829_, p_312607_);
    }

    public AbstractHurtingProjectile(
        EntityType<? extends AbstractHurtingProjectile> p_36826_, double p_36828_, double p_36829_, double p_36830_, Vec3 p_347477_, Level p_36831_
    ) {
        this(p_36826_, p_36831_);
        this.moveTo(p_36828_, p_36829_, p_36830_, this.getYRot(), this.getXRot());
        this.reapplyPosition();
        this.assignDirectionalMovement(p_347477_, this.accelerationPower);
    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> p_36817_, LivingEntity p_347648_, Vec3 p_347596_, Level p_36824_) {
        this(p_36817_, p_347648_.getX(), p_347648_.getY(), p_347648_.getZ(), p_347596_, p_36824_);
        this.setOwner(p_347648_);
        this.setRot(p_347648_.getYRot(), p_347648_.getXRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_326181_) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double p_36837_) {
        double d0 = this.getBoundingBox().getSize() * 4.0;
        if (Double.isNaN(d0)) {
            d0 = 4.0;
        }

        d0 *= 64.0;
        return p_36837_ < d0 * d0;
    }

    protected ClipContext.Block getClipType() {
        return ClipContext.Block.COLLIDER;
    }

    @Override
    public void tick() {
        Entity entity = this.getOwner();
        if (this.level().isClientSide || (entity == null || !entity.isRemoved()) && this.level().hasChunkAt(this.blockPosition())) {
            super.tick();
            if (this.shouldBurn()) {
                this.igniteForSeconds(1.0F);
            }

            HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, this.getClipType());
            if (hitresult.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hitresult)) {
                this.hitTargetOrDeflectSelf(hitresult);
            }

            this.checkInsideBlocks();
            Vec3 vec3 = this.getDeltaMovement();
            double d0 = this.getX() + vec3.x;
            double d1 = this.getY() + vec3.y;
            double d2 = this.getZ() + vec3.z;
            ProjectileUtil.rotateTowardsMovement(this, 0.2F);
            float f;
            if (this.isInWater()) {
                for (int i = 0; i < 4; i++) {
                    float f1 = 0.25F;
                    this.level().addParticle(ParticleTypes.BUBBLE, d0 - vec3.x * 0.25, d1 - vec3.y * 0.25, d2 - vec3.z * 0.25, vec3.x, vec3.y, vec3.z);
                }

                f = this.getLiquidInertia();
            } else {
                f = this.getInertia();
            }

            this.setDeltaMovement(vec3.add(vec3.normalize().scale(this.accelerationPower)).scale((double)f));
            ParticleOptions particleoptions = this.getTrailParticle();
            if (particleoptions != null) {
                this.level().addParticle(particleoptions, d0, d1 + 0.5, d2, 0.0, 0.0, 0.0);
            }

            this.setPos(d0, d1, d2);
        } else {
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource p_341896_, float p_341906_) {
        return !this.isInvulnerableTo(p_341896_);
    }

    @Override
    protected boolean canHitEntity(Entity p_36842_) {
        return super.canHitEntity(p_36842_) && !p_36842_.noPhysics;
    }

    protected boolean shouldBurn() {
        return true;
    }

    @Nullable
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.SMOKE;
    }

    protected float getInertia() {
        return 0.95F;
    }

    protected float getLiquidInertia() {
        return 0.8F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_36848_) {
        super.addAdditionalSaveData(p_36848_);
        p_36848_.putDouble("acceleration_power", this.accelerationPower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_36844_) {
        super.readAdditionalSaveData(p_36844_);
        if (p_36844_.contains("acceleration_power", 6)) {
            this.accelerationPower = p_36844_.getDouble("acceleration_power");
        }
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity p_352396_) {
        Entity entity = this.getOwner();
        int i = entity == null ? 0 : entity.getId();
        Vec3 vec3 = p_352396_.getPositionBase();
        return new ClientboundAddEntityPacket(
            this.getId(),
            this.getUUID(),
            vec3.x(),
            vec3.y(),
            vec3.z(),
            p_352396_.getLastSentXRot(),
            p_352396_.getLastSentYRot(),
            this.getType(),
            i,
            p_352396_.getLastSentMovement(),
            0.0
        );
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket p_150128_) {
        super.recreateFromPacket(p_150128_);
        Vec3 vec3 = new Vec3(p_150128_.getXa(), p_150128_.getYa(), p_150128_.getZa());
        this.setDeltaMovement(vec3);
    }

    private void assignDirectionalMovement(Vec3 p_347481_, double p_347589_) {
        this.setDeltaMovement(p_347481_.normalize().scale(p_347589_));
        this.hasImpulse = true;
    }

    @Override
    protected void onDeflection(@Nullable Entity p_341940_, boolean p_341895_) {
        super.onDeflection(p_341940_, p_341895_);
        if (p_341895_) {
            this.accelerationPower = 0.1;
        } else {
            this.accelerationPower *= 0.5;
        }
    }
}
