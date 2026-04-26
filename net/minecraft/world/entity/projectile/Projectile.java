package net.minecraft.world.entity.projectile;

import com.google.common.base.MoreObjects;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class Projectile extends Entity implements TraceableEntity {
    @Nullable
    private UUID ownerUUID;
    @Nullable
    private Entity cachedOwner;
    private boolean leftOwner;
    private boolean hasBeenShot;
    @Nullable
    private Entity lastDeflectedBy;

    protected Projectile(EntityType<? extends Projectile> p_37248_, Level p_37249_) {
        super(p_37248_, p_37249_);
    }

    public void setOwner(@Nullable Entity p_37263_) {
        if (p_37263_ != null) {
            this.ownerUUID = p_37263_.getUUID();
            this.cachedOwner = p_37263_;
        }
    }

    @Nullable
    @Override
    public Entity getOwner() {
        if (this.cachedOwner != null && !this.cachedOwner.isRemoved()) {
            return this.cachedOwner;
        } else if (this.ownerUUID != null && this.level() instanceof ServerLevel serverlevel) {
            this.cachedOwner = serverlevel.getEntity(this.ownerUUID);
            return this.cachedOwner;
        } else {
            return null;
        }
    }

    public Entity getEffectSource() {
        return MoreObjects.firstNonNull(this.getOwner(), this);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag p_37265_) {
        if (this.ownerUUID != null) {
            p_37265_.putUUID("Owner", this.ownerUUID);
        }

        if (this.leftOwner) {
            p_37265_.putBoolean("LeftOwner", true);
        }

        p_37265_.putBoolean("HasBeenShot", this.hasBeenShot);
    }

    protected boolean ownedBy(Entity p_150172_) {
        return p_150172_.getUUID().equals(this.ownerUUID);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag p_37262_) {
        if (p_37262_.hasUUID("Owner")) {
            this.ownerUUID = p_37262_.getUUID("Owner");
            this.cachedOwner = null;
        }

        this.leftOwner = p_37262_.getBoolean("LeftOwner");
        this.hasBeenShot = p_37262_.getBoolean("HasBeenShot");
    }

    @Override
    public void restoreFrom(Entity p_305838_) {
        super.restoreFrom(p_305838_);
        if (p_305838_ instanceof Projectile projectile) {
            this.cachedOwner = projectile.cachedOwner;
        }
    }

    @Override
    public void tick() {
        if (!this.hasBeenShot) {
            this.gameEvent(GameEvent.PROJECTILE_SHOOT, this.getOwner());
            this.hasBeenShot = true;
        }

        if (!this.leftOwner) {
            this.leftOwner = this.checkLeftOwner();
        }

        super.tick();
    }

    private boolean checkLeftOwner() {
        Entity entity = this.getOwner();
        if (entity != null) {
            for (Entity entity1 : this.level()
                .getEntities(
                    this,
                    this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0),
                    p_37272_ -> !p_37272_.isSpectator() && p_37272_.isPickable()
                )) {
                if (entity1.getRootVehicle() == entity.getRootVehicle()) {
                    return false;
                }
            }
        }

        return true;
    }

    public Vec3 getMovementToShoot(double p_338345_, double p_338731_, double p_338427_, float p_338430_, float p_338697_) {
        return new Vec3(p_338345_, p_338731_, p_338427_)
            .normalize()
            .add(
                this.random.triangle(0.0, 0.0172275 * (double)p_338697_),
                this.random.triangle(0.0, 0.0172275 * (double)p_338697_),
                this.random.triangle(0.0, 0.0172275 * (double)p_338697_)
            )
            .scale((double)p_338430_);
    }

    public void shoot(double p_37266_, double p_37267_, double p_37268_, float p_37269_, float p_37270_) {
        Vec3 vec3 = this.getMovementToShoot(p_37266_, p_37267_, p_37268_, p_37269_, p_37270_);
        this.setDeltaMovement(vec3);
        this.hasImpulse = true;
        double d0 = vec3.horizontalDistance();
        this.setYRot((float)(Mth.atan2(vec3.x, vec3.z) * 180.0F / (float)Math.PI));
        this.setXRot((float)(Mth.atan2(vec3.y, d0) * 180.0F / (float)Math.PI));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void shootFromRotation(Entity p_37252_, float p_37253_, float p_37254_, float p_37255_, float p_37256_, float p_37257_) {
        float f = -Mth.sin(p_37254_ * (float) (Math.PI / 180.0)) * Mth.cos(p_37253_ * (float) (Math.PI / 180.0));
        float f1 = -Mth.sin((p_37253_ + p_37255_) * (float) (Math.PI / 180.0));
        float f2 = Mth.cos(p_37254_ * (float) (Math.PI / 180.0)) * Mth.cos(p_37253_ * (float) (Math.PI / 180.0));
        this.shoot((double)f, (double)f1, (double)f2, p_37256_, p_37257_);
        Vec3 vec3 = p_37252_.getKnownMovement();
        this.setDeltaMovement(this.getDeltaMovement().add(vec3.x, p_37252_.onGround() ? 0.0 : vec3.y, vec3.z));
    }

    protected ProjectileDeflection hitTargetOrDeflectSelf(HitResult p_341949_) {
        if (p_341949_.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityhitresult = (EntityHitResult)p_341949_;
            Entity entity = entityhitresult.getEntity();
            ProjectileDeflection projectiledeflection = entity.deflection(this);
            if (projectiledeflection != ProjectileDeflection.NONE) {
                if (entity != this.lastDeflectedBy && this.deflect(projectiledeflection, entity, this.getOwner(), false)) {
                    this.lastDeflectedBy = entity;
                }

                return projectiledeflection;
            }
        }

        this.onHit(p_341949_);
        return ProjectileDeflection.NONE;
    }

    public boolean deflect(ProjectileDeflection p_341900_, @Nullable Entity p_341912_, @Nullable Entity p_341932_, boolean p_341948_) {
        if (!this.level().isClientSide) {
            p_341900_.deflect(this, p_341912_, this.random);
            this.setOwner(p_341932_);
            this.onDeflection(p_341912_, p_341948_);
        }

        return true;
    }

    protected void onDeflection(@Nullable Entity p_341918_, boolean p_341907_) {
    }

    protected void onHit(HitResult p_37260_) {
        HitResult.Type hitresult$type = p_37260_.getType();
        if (hitresult$type == HitResult.Type.ENTITY) {
            EntityHitResult entityhitresult = (EntityHitResult)p_37260_;
            Entity entity = entityhitresult.getEntity();
            if (entity.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile projectile) {
                projectile.deflect(ProjectileDeflection.AIM_DEFLECT, this.getOwner(), this.getOwner(), true);
            }

            this.onHitEntity(entityhitresult);
            this.level().gameEvent(GameEvent.PROJECTILE_LAND, p_37260_.getLocation(), GameEvent.Context.of(this, null));
        } else if (hitresult$type == HitResult.Type.BLOCK) {
            BlockHitResult blockhitresult = (BlockHitResult)p_37260_;
            this.onHitBlock(blockhitresult);
            BlockPos blockpos = blockhitresult.getBlockPos();
            this.level().gameEvent(GameEvent.PROJECTILE_LAND, blockpos, GameEvent.Context.of(this, this.level().getBlockState(blockpos)));
        }
    }

    protected void onHitEntity(EntityHitResult p_37259_) {
    }

    protected void onHitBlock(BlockHitResult p_37258_) {
        BlockState blockstate = this.level().getBlockState(p_37258_.getBlockPos());
        blockstate.onProjectileHit(this.level(), blockstate, p_37258_, this);
    }

    @Override
    public void lerpMotion(double p_37279_, double p_37280_, double p_37281_) {
        this.setDeltaMovement(p_37279_, p_37280_, p_37281_);
        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            double d0 = Math.sqrt(p_37279_ * p_37279_ + p_37281_ * p_37281_);
            this.setXRot((float)(Mth.atan2(p_37280_, d0) * 180.0F / (float)Math.PI));
            this.setYRot((float)(Mth.atan2(p_37279_, p_37281_) * 180.0F / (float)Math.PI));
            this.xRotO = this.getXRot();
            this.yRotO = this.getYRot();
            this.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        }
    }

    protected boolean canHitEntity(Entity p_37250_) {
        if (!p_37250_.canBeHitByProjectile()) {
            return false;
        } else {
            Entity entity = this.getOwner();
            return entity == null || this.leftOwner || !entity.isPassengerOfSameVehicle(p_37250_);
        }
    }

    protected void updateRotation() {
        Vec3 vec3 = this.getDeltaMovement();
        double d0 = vec3.horizontalDistance();
        this.setXRot(lerpRotation(this.xRotO, (float)(Mth.atan2(vec3.y, d0) * 180.0F / (float)Math.PI)));
        this.setYRot(lerpRotation(this.yRotO, (float)(Mth.atan2(vec3.x, vec3.z) * 180.0F / (float)Math.PI)));
    }

    protected static float lerpRotation(float p_37274_, float p_37275_) {
        while (p_37275_ - p_37274_ < -180.0F) {
            p_37274_ -= 360.0F;
        }

        while (p_37275_ - p_37274_ >= 180.0F) {
            p_37274_ += 360.0F;
        }

        return Mth.lerp(0.2F, p_37274_, p_37275_);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity p_352459_) {
        Entity entity = this.getOwner();
        return new ClientboundAddEntityPacket(this, p_352459_, entity == null ? 0 : entity.getId());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket p_150170_) {
        super.recreateFromPacket(p_150170_);
        Entity entity = this.level().getEntity(p_150170_.getData());
        if (entity != null) {
            this.setOwner(entity);
        }
    }

    @Override
    public boolean mayInteract(Level p_150167_, BlockPos p_150168_) {
        Entity entity = this.getOwner();
        return entity instanceof Player
            ? entity.mayInteract(p_150167_, p_150168_)
            : entity == null || net.neoforged.neoforge.event.EventHooks.canEntityGrief(p_150167_, entity);
    }

    public boolean mayBreak(Level p_307481_) {
        return this.getType().is(EntityTypeTags.IMPACT_PROJECTILES) && p_307481_.getGameRules().getBoolean(GameRules.RULE_PROJECTILESCANBREAKBLOCKS);
    }

    @Override
    public boolean isPickable() {
        return this.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE);
    }

    @Override
    public float getPickRadius() {
        return this.isPickable() ? 1.0F : 0.0F;
    }

    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity p_344992_, DamageSource p_345905_) {
        double d0 = this.getDeltaMovement().x;
        double d1 = this.getDeltaMovement().z;
        return DoubleDoubleImmutablePair.of(d0, d1);
    }
}
