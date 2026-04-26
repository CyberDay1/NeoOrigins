package net.minecraft.world.entity;

import java.util.List;
import net.minecraft.world.phys.Vec3;

public enum EntityAttachment {
    PASSENGER(EntityAttachment.Fallback.AT_HEIGHT),
    VEHICLE(EntityAttachment.Fallback.AT_FEET),
    NAME_TAG(EntityAttachment.Fallback.AT_HEIGHT),
    WARDEN_CHEST(EntityAttachment.Fallback.AT_CENTER);

    private final EntityAttachment.Fallback fallback;

    private EntityAttachment(EntityAttachment.Fallback p_316243_) {
        this.fallback = p_316243_;
    }

    public List<Vec3> createFallbackPoints(float p_316596_, float p_316153_) {
        return this.fallback.create(p_316596_, p_316153_);
    }

    public interface Fallback {
        List<Vec3> ZERO = List.of(Vec3.ZERO);
        EntityAttachment.Fallback AT_FEET = (p_316289_, p_316334_) -> ZERO;
        EntityAttachment.Fallback AT_HEIGHT = (p_316259_, p_316219_) -> List.of(new Vec3(0.0, (double)p_316219_, 0.0));
        EntityAttachment.Fallback AT_CENTER = (p_319580_, p_319581_) -> List.of(new Vec3(0.0, (double)p_319581_ / 2.0, 0.0));

        List<Vec3> create(float p_316360_, float p_316865_);
    }
}
