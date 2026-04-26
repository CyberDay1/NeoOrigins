package net.minecraft.world.entity.decoration;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public abstract class BlockAttachedEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private int checkInterval;
    protected BlockPos pos;

    protected BlockAttachedEntity(EntityType<? extends BlockAttachedEntity> p_345070_, Level p_345079_) {
        super(p_345070_, p_345079_);
    }

    protected BlockAttachedEntity(EntityType<? extends BlockAttachedEntity> p_345456_, Level p_345187_, BlockPos p_345816_) {
        this(p_345456_, p_345187_);
        this.pos = p_345816_;
    }

    protected abstract void recalculateBoundingBox();

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            this.checkBelowWorld();
            if (this.checkInterval++ == 100) {
                this.checkInterval = 0;
                if (!this.isRemoved() && !this.survives()) {
                    this.discard();
                    this.dropItem(null);
                }
            }
        }
    }

    public abstract boolean survives();

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity p_346423_) {
        if (p_346423_ instanceof Player player) {
            return !this.level().mayInteract(player, this.pos) ? true : this.hurt(this.damageSources().playerAttack(player), 0.0F);
        } else {
            return false;
        }
    }

    @Override
    public boolean hurt(DamageSource p_345749_, float p_345893_) {
        if (this.isInvulnerableTo(p_345749_)) {
            return false;
        } else {
            if (!this.isRemoved() && !this.level().isClientSide) {
                this.kill();
                this.markHurt();
                this.dropItem(p_345749_.getEntity());
            }

            return true;
        }
    }

    @Override
    public void move(MoverType p_345778_, Vec3 p_345301_) {
        if (!this.level().isClientSide && !this.isRemoved() && p_345301_.lengthSqr() > 0.0) {
            this.kill();
            this.dropItem(null);
        }
    }

    @Override
    public void push(double p_345288_, double p_346171_, double p_345389_) {
        if (!this.level().isClientSide && !this.isRemoved() && p_345288_ * p_345288_ + p_346171_ * p_346171_ + p_345389_ * p_345389_ > 0.0) {
            this.kill();
            this.dropItem(null);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_344925_) {
        BlockPos blockpos = this.getPos();
        p_344925_.putInt("TileX", blockpos.getX());
        p_344925_.putInt("TileY", blockpos.getY());
        p_344925_.putInt("TileZ", blockpos.getZ());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_346402_) {
        BlockPos blockpos = new BlockPos(p_346402_.getInt("TileX"), p_346402_.getInt("TileY"), p_346402_.getInt("TileZ"));
        if (!blockpos.closerThan(this.blockPosition(), 16.0)) {
            LOGGER.error("Block-attached entity at invalid position: {}", blockpos);
        } else {
            this.pos = blockpos;
        }
    }

    public abstract void dropItem(@Nullable Entity p_345676_);

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    @Override
    public void setPos(double p_346360_, double p_344743_, double p_345636_) {
        this.pos = BlockPos.containing(p_346360_, p_344743_, p_345636_);
        this.recalculateBoundingBox();
        this.hasImpulse = true;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public void thunderHit(ServerLevel p_345825_, LightningBolt p_346288_) {
    }

    @Override
    public void refreshDimensions() {
    }
}
