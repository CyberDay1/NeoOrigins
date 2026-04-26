package net.minecraft.world.level.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public record DimensionTransition(
    ServerLevel newLevel,
    Vec3 pos,
    Vec3 speed,
    float yRot,
    float xRot,
    boolean missingRespawnBlock,
    DimensionTransition.PostDimensionTransition postDimensionTransition
) {
    public static final DimensionTransition.PostDimensionTransition DO_NOTHING = p_352417_ -> {
    };
    public static final DimensionTransition.PostDimensionTransition PLAY_PORTAL_SOUND = DimensionTransition::playPortalSound;
    public static final DimensionTransition.PostDimensionTransition PLACE_PORTAL_TICKET = DimensionTransition::placePortalTicket;

    public DimensionTransition(
        ServerLevel p_348637_, Vec3 p_348645_, Vec3 p_348472_, float p_348548_, float p_348664_, DimensionTransition.PostDimensionTransition p_352139_
    ) {
        this(p_348637_, p_348645_, p_348472_, p_348548_, p_348664_, false, p_352139_);
    }

    public DimensionTransition(ServerLevel p_348609_, Entity p_352432_, DimensionTransition.PostDimensionTransition p_352373_) {
        this(p_348609_, findAdjustedSharedSpawnPos(p_348609_, p_352432_), Vec3.ZERO, 0.0F, 0.0F, false, p_352373_);
    }

    private static void playPortalSound(Entity p_352075_) {
        if (p_352075_ instanceof ServerPlayer serverplayer) {
            serverplayer.connection.send(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
        }
    }

    private static void placePortalTicket(Entity p_352447_) {
        p_352447_.placePortalTicket(BlockPos.containing(p_352447_.position()));
    }

    public static DimensionTransition missingRespawnBlock(ServerLevel p_348517_, Entity p_352420_, DimensionTransition.PostDimensionTransition p_352305_) {
        return new DimensionTransition(p_348517_, findAdjustedSharedSpawnPos(p_348517_, p_352420_), Vec3.ZERO, 0.0F, 0.0F, true, p_352305_);
    }

    private static Vec3 findAdjustedSharedSpawnPos(ServerLevel p_352080_, Entity p_352400_) {
        return p_352400_.adjustSpawnLocation(p_352080_, p_352080_.getSharedSpawnPos()).getBottomCenter();
    }

    @FunctionalInterface
    public interface PostDimensionTransition {
        void onTransition(Entity p_352279_);

        default DimensionTransition.PostDimensionTransition then(DimensionTransition.PostDimensionTransition p_352277_) {
            return p_352242_ -> {
                this.onTransition(p_352242_);
                p_352277_.onTransition(p_352242_);
            };
        }
    }
}
