package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class EntityAttachments {
    private final Map<EntityAttachment, List<Vec3>> attachments;

    EntityAttachments(Map<EntityAttachment, List<Vec3>> p_316675_) {
        this.attachments = p_316675_;
    }

    public static EntityAttachments createDefault(float p_316906_, float p_316905_) {
        return builder().build(p_316906_, p_316905_);
    }

    public static EntityAttachments.Builder builder() {
        return new EntityAttachments.Builder();
    }

    public EntityAttachments scale(float p_316378_, float p_316683_, float p_316463_) {
        Map<EntityAttachment, List<Vec3>> map = new EnumMap<>(EntityAttachment.class);

        for (Entry<EntityAttachment, List<Vec3>> entry : this.attachments.entrySet()) {
            map.put(entry.getKey(), scalePoints(entry.getValue(), p_316378_, p_316683_, p_316463_));
        }

        return new EntityAttachments(map);
    }

    private static List<Vec3> scalePoints(List<Vec3> p_316879_, float p_316759_, float p_316371_, float p_316711_) {
        List<Vec3> list = new ArrayList<>(p_316879_.size());

        for (Vec3 vec3 : p_316879_) {
            list.add(vec3.multiply((double)p_316759_, (double)p_316371_, (double)p_316711_));
        }

        return list;
    }

    @Nullable
    public Vec3 getNullable(EntityAttachment p_316263_, int p_316709_, float p_316113_) {
        List<Vec3> list = this.attachments.get(p_316263_);
        return p_316709_ >= 0 && p_316709_ < list.size() ? transformPoint(list.get(p_316709_), p_316113_) : null;
    }

    public Vec3 get(EntityAttachment p_316290_, int p_316517_, float p_316296_) {
        Vec3 vec3 = this.getNullable(p_316290_, p_316517_, p_316296_);
        if (vec3 == null) {
            throw new IllegalStateException("Had no attachment point of type: " + p_316290_ + " for index: " + p_316517_);
        } else {
            return vec3;
        }
    }

    public Vec3 getClamped(EntityAttachment p_316117_, int p_316379_, float p_316900_) {
        List<Vec3> list = this.attachments.get(p_316117_);
        if (list.isEmpty()) {
            throw new IllegalStateException("Had no attachment points of type: " + p_316117_);
        } else {
            Vec3 vec3 = list.get(Mth.clamp(p_316379_, 0, list.size() - 1));
            return transformPoint(vec3, p_316900_);
        }
    }

    private static Vec3 transformPoint(Vec3 p_316742_, float p_316708_) {
        return p_316742_.yRot(-p_316708_ * (float) (Math.PI / 180.0));
    }

    public static class Builder {
        private final Map<EntityAttachment, List<Vec3>> attachments = new EnumMap<>(EntityAttachment.class);

        Builder() {
        }

        public EntityAttachments.Builder attach(EntityAttachment p_316395_, float p_316627_, float p_316510_, float p_316313_) {
            return this.attach(p_316395_, new Vec3((double)p_316627_, (double)p_316510_, (double)p_316313_));
        }

        public EntityAttachments.Builder attach(EntityAttachment p_316903_, Vec3 p_316337_) {
            this.attachments.computeIfAbsent(p_316903_, p_316616_ -> new ArrayList<>(1)).add(p_316337_);
            return this;
        }

        public EntityAttachments build(float p_316746_, float p_316254_) {
            Map<EntityAttachment, List<Vec3>> map = new EnumMap<>(EntityAttachment.class);

            for (EntityAttachment entityattachment : EntityAttachment.values()) {
                List<Vec3> list = this.attachments.get(entityattachment);
                map.put(entityattachment, list != null ? List.copyOf(list) : entityattachment.createFallbackPoints(p_316746_, p_316254_));
            }

            return new EntityAttachments(map);
        }
    }
}
