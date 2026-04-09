package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ItemMagnetismPower extends AbstractTogglePower<ItemMagnetismPower.Config> {

    public record Config(double radius, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("radius", 4.0).forGetter(Config::radius),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        if (player.tickCount % 2 != 0) return;
        AABB box = player.getBoundingBox().inflate(config.radius());
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        List<ItemEntity> items = sl.getEntitiesOfClass(ItemEntity.class, box,
            item -> !item.hasPickUpDelay() && item.isAlive());
        for (ItemEntity item : items) {
            Vec3 dir = player.position().subtract(item.position()).normalize().scale(0.3);
            item.setDeltaMovement(item.getDeltaMovement().add(dir));
        }
    }

    @Override
    protected void removeEffect(ServerPlayer player, Config config) {}
}
