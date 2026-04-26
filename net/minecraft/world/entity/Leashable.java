package net.minecraft.world.entity;

import com.mojang.datafixers.util.Either;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;

public interface Leashable {
    String LEASH_TAG = "leash";
    double LEASH_TOO_FAR_DIST = 10.0;
    double LEASH_ELASTIC_DIST = 6.0;

    @Nullable
    Leashable.LeashData getLeashData();

    void setLeashData(@Nullable Leashable.LeashData p_352114_);

    default boolean isLeashed() {
        return this.getLeashData() != null && this.getLeashData().leashHolder != null;
    }

    default boolean mayBeLeashed() {
        return this.getLeashData() != null;
    }

    default boolean canHaveALeashAttachedToIt() {
        return this.canBeLeashed() && !this.isLeashed();
    }

    default boolean canBeLeashed() {
        return true;
    }

    default void setDelayedLeashHolderId(int p_352387_) {
        this.setLeashData(new Leashable.LeashData(p_352387_));
        dropLeash((Entity & Leashable)this, false, false);
    }

    @Nullable
    default Leashable.LeashData readLeashData(CompoundTag p_352410_) {
        if (p_352410_.contains("leash", 10)) {
            return new Leashable.LeashData(Either.left(p_352410_.getCompound("leash").getUUID("UUID")));
        } else {
            if (p_352410_.contains("leash", 11)) {
                Either<UUID, BlockPos> either = NbtUtils.readBlockPos(p_352410_, "leash").<Either<UUID, BlockPos>>map(Either::right).orElse(null);
                if (either != null) {
                    return new Leashable.LeashData(either);
                }
            }

            return null;
        }
    }

    default void writeLeashData(CompoundTag p_352349_, @Nullable Leashable.LeashData p_352363_) {
        if (p_352363_ != null) {
            Either<UUID, BlockPos> either = p_352363_.delayedLeashInfo;
            if (p_352363_.leashHolder instanceof LeashFenceKnotEntity leashfenceknotentity) {
                either = Either.right(leashfenceknotentity.getPos());
            } else if (p_352363_.leashHolder != null) {
                either = Either.left(p_352363_.leashHolder.getUUID());
            }

            if (either != null) {
                p_352349_.put("leash", either.map(p_352326_ -> {
                    CompoundTag compoundtag = new CompoundTag();
                    compoundtag.putUUID("UUID", p_352326_);
                    return compoundtag;
                }, NbtUtils::writeBlockPos));
            }
        }
    }

    private static <E extends Entity & Leashable> void restoreLeashFromSave(E p_352354_, Leashable.LeashData p_352106_) {
        if (p_352106_.delayedLeashInfo != null && p_352354_.level() instanceof ServerLevel serverlevel) {
            Optional<UUID> optional1 = p_352106_.delayedLeashInfo.left();
            Optional<BlockPos> optional = p_352106_.delayedLeashInfo.right();
            if (optional1.isPresent()) {
                Entity entity = serverlevel.getEntity(optional1.get());
                if (entity != null) {
                    setLeashedTo(p_352354_, entity, true);
                    return;
                }
            } else if (optional.isPresent()) {
                setLeashedTo(p_352354_, LeashFenceKnotEntity.getOrCreateKnot(serverlevel, optional.get()), true);
                return;
            }

            if (p_352354_.tickCount > 100) {
                p_352354_.spawnAtLocation(Items.LEAD);
                p_352354_.setLeashData(null);
            }
        }
    }

    default void dropLeash(boolean p_352294_, boolean p_352456_) {
        dropLeash((Entity & Leashable)this, p_352294_, p_352456_);
    }

    private static <E extends Entity & Leashable> void dropLeash(E p_352163_, boolean p_352286_, boolean p_352272_) {
        Leashable.LeashData leashable$leashdata = p_352163_.getLeashData();
        if (leashable$leashdata != null && leashable$leashdata.leashHolder != null) {
            p_352163_.setLeashData(null);
            if (!p_352163_.level().isClientSide && p_352272_) {
                p_352163_.spawnAtLocation(Items.LEAD);
            }

            if (p_352286_ && p_352163_.level() instanceof ServerLevel serverlevel) {
                serverlevel.getChunkSource().broadcast(p_352163_, new ClientboundSetEntityLinkPacket(p_352163_, null));
            }
        }
    }

    static <E extends Entity & Leashable> void tickLeash(E p_352082_) {
        Leashable.LeashData leashable$leashdata = p_352082_.getLeashData();
        if (leashable$leashdata != null && leashable$leashdata.delayedLeashInfo != null) {
            restoreLeashFromSave(p_352082_, leashable$leashdata);
        }

        if (leashable$leashdata != null && leashable$leashdata.leashHolder != null) {
            if (!p_352082_.isAlive() || !leashable$leashdata.leashHolder.isAlive()) {
                dropLeash(p_352082_, true, true);
            }

            Entity entity = p_352082_.getLeashHolder();
            if (entity != null && entity.level() == p_352082_.level()) {
                float f = p_352082_.distanceTo(entity);
                if (!p_352082_.handleLeashAtDistance(entity, f)) {
                    return;
                }

                if ((double)f > 10.0) {
                    p_352082_.leashTooFarBehaviour();
                } else if ((double)f > 6.0) {
                    p_352082_.elasticRangeLeashBehaviour(entity, f);
                    p_352082_.checkSlowFallDistance();
                } else {
                    p_352082_.closeRangeLeashBehaviour(entity);
                }
            }
        }
    }

    default boolean handleLeashAtDistance(Entity p_352458_, float p_352101_) {
        return true;
    }

    default void leashTooFarBehaviour() {
        this.dropLeash(true, true);
    }

    default void closeRangeLeashBehaviour(Entity p_352073_) {
    }

    default void elasticRangeLeashBehaviour(Entity p_353036_, float p_353047_) {
        legacyElasticRangeLeashBehaviour((Entity & Leashable)this, p_353036_, p_353047_);
    }

    private static <E extends Entity & Leashable> void legacyElasticRangeLeashBehaviour(E p_353048_, Entity p_353039_, float p_353053_) {
        double d0 = (p_353039_.getX() - p_353048_.getX()) / (double)p_353053_;
        double d1 = (p_353039_.getY() - p_353048_.getY()) / (double)p_353053_;
        double d2 = (p_353039_.getZ() - p_353048_.getZ()) / (double)p_353053_;
        p_353048_.setDeltaMovement(
            p_353048_.getDeltaMovement().add(Math.copySign(d0 * d0 * 0.4, d0), Math.copySign(d1 * d1 * 0.4, d1), Math.copySign(d2 * d2 * 0.4, d2))
        );
    }

    default void setLeashedTo(Entity p_352411_, boolean p_352183_) {
        setLeashedTo((Entity & Leashable)this, p_352411_, p_352183_);
    }

    private static <E extends Entity & Leashable> void setLeashedTo(E p_352280_, Entity p_352109_, boolean p_352239_) {
        Leashable.LeashData leashable$leashdata = p_352280_.getLeashData();
        if (leashable$leashdata == null) {
            leashable$leashdata = new Leashable.LeashData(p_352109_);
            p_352280_.setLeashData(leashable$leashdata);
        } else {
            leashable$leashdata.setLeashHolder(p_352109_);
        }

        if (p_352239_ && p_352280_.level() instanceof ServerLevel serverlevel) {
            serverlevel.getChunkSource().broadcast(p_352280_, new ClientboundSetEntityLinkPacket(p_352280_, p_352109_));
        }

        if (p_352280_.isPassenger()) {
            p_352280_.stopRiding();
        }
    }

    @Nullable
    default Entity getLeashHolder() {
        return getLeashHolder((Entity & Leashable)this);
    }

    @Nullable
    private static <E extends Entity & Leashable> Entity getLeashHolder(E p_352466_) {
        Leashable.LeashData leashable$leashdata = p_352466_.getLeashData();
        if (leashable$leashdata == null) {
            return null;
        } else {
            if (leashable$leashdata.delayedLeashHolderId != 0 && p_352466_.level().isClientSide) {
                Entity entity = p_352466_.level().getEntity(leashable$leashdata.delayedLeashHolderId);
                if (entity instanceof Entity) {
                    leashable$leashdata.setLeashHolder(entity);
                }
            }

            return leashable$leashdata.leashHolder;
        }
    }

    public static final class LeashData {
        int delayedLeashHolderId;
        @Nullable
        public Entity leashHolder;
        @Nullable
        public Either<UUID, BlockPos> delayedLeashInfo;

        LeashData(Either<UUID, BlockPos> p_352282_) {
            this.delayedLeashInfo = p_352282_;
        }

        LeashData(Entity p_352066_) {
            this.leashHolder = p_352066_;
        }

        LeashData(int p_352297_) {
            this.delayedLeashHolderId = p_352297_;
        }

        public void setLeashHolder(Entity p_352464_) {
            this.leashHolder = p_352464_;
            this.delayedLeashInfo = null;
            this.delayedLeashHolderId = 0;
        }
    }
}
