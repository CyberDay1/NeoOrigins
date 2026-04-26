package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ThrownEnderpearl extends ThrowableItemProjectile {
    public ThrownEnderpearl(EntityType<? extends ThrownEnderpearl> p_37491_, Level p_37492_) {
        super(p_37491_, p_37492_);
    }

    public ThrownEnderpearl(Level p_37499_, LivingEntity p_37500_) {
        super(EntityType.ENDER_PEARL, p_37500_, p_37499_);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void onHitEntity(EntityHitResult p_37502_) {
        super.onHitEntity(p_37502_);
        p_37502_.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult p_37504_) {
        super.onHit(p_37504_);

        for (int i = 0; i < 32; i++) {
            this.level()
                .addParticle(
                    ParticleTypes.PORTAL,
                    this.getX(),
                    this.getY() + this.random.nextDouble() * 2.0,
                    this.getZ(),
                    this.random.nextGaussian(),
                    0.0,
                    this.random.nextGaussian()
                );
        }

        if (this.level() instanceof ServerLevel serverlevel && !this.isRemoved()) {
            Entity entity = this.getOwner();
            if (entity != null && isAllowedToTeleportOwner(entity, serverlevel)) {
                if (entity.isPassenger()) {
                    entity.unRide();
                }

                if (entity instanceof ServerPlayer serverplayer) {
                    if (serverplayer.connection.isAcceptingMessages()) {
                        net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderPearl event = net.neoforged.neoforge.event.EventHooks.onEnderPearlLand(serverplayer, this.getX(), this.getY(), this.getZ(), this, 5.0F, p_37504_);
                        if (!event.isCanceled()) { // Don't indent to lower patch size
                        if (this.random.nextFloat() < 0.05F && serverlevel.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                            Endermite endermite = EntityType.ENDERMITE.create(serverlevel);
                            if (endermite != null) {
                                endermite.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                                serverlevel.addFreshEntity(endermite);
                            }
                        }

                        entity.changeDimension(
                            new DimensionTransition(
                                serverlevel, event.getTarget(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING
                            )
                        );
                        entity.resetFallDistance();
                        serverplayer.resetCurrentImpulseContext();
                        entity.hurt(this.damageSources().fall(), event.getAttackDamage());
                        this.playSound(serverlevel, this.position());
                        } //Forge: End
                    }
                } else {
                    entity.changeDimension(
                        new DimensionTransition(
                            serverlevel, this.position(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING
                        )
                    );
                    entity.resetFallDistance();
                    this.playSound(serverlevel, this.position());
                }

                this.discard();
                return;
            }

            this.discard();
            return;
        }
    }

    private static boolean isAllowedToTeleportOwner(Entity p_352055_, Level p_352453_) {
        if (p_352055_.level().dimension() == p_352453_.dimension()) {
            return !(p_352055_ instanceof LivingEntity livingentity) ? p_352055_.isAlive() : livingentity.isAlive() && !livingentity.isSleeping();
        } else {
            return p_352055_.canUsePortal(true);
        }
    }

    @Override
    public void tick() {
        Entity entity = this.getOwner();
        if (entity instanceof ServerPlayer && !entity.isAlive() && this.level().getGameRules().getBoolean(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH)) {
            this.discard();
        } else {
            super.tick();
        }
    }

    private void playSound(Level p_350706_, Vec3 p_350543_) {
        p_350706_.playSound(null, p_350543_.x, p_350543_.y, p_350543_.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS);
    }

    @Override
    public boolean canChangeDimensions(Level p_352941_, Level p_352929_) {
        return p_352941_.dimension() == Level.END && this.getOwner() instanceof ServerPlayer serverplayer
            ? super.canChangeDimensions(p_352941_, p_352929_) && serverplayer.seenCredits
            : super.canChangeDimensions(p_352941_, p_352929_);
    }

    @Override
    protected void onInsideBlock(BlockState p_353066_) {
        super.onInsideBlock(p_353066_);
        if (p_353066_.is(Blocks.END_GATEWAY) && this.getOwner() instanceof ServerPlayer serverplayer) {
            serverplayer.onInsideBlock(p_353066_);
        }
    }
}
