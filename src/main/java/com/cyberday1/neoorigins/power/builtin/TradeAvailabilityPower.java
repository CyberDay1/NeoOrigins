package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Periodically resets villager trade uses in a radius around the player, and
 * resends the offers to anyone with that villager's screen open so the refill
 * is visible without reopening it.
 */
public class TradeAvailabilityPower extends PowerType<TradeAvailabilityPower.Config> {

    public record Config(int scanInterval, double radius, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("scan_interval", 40).forGetter(Config::scanInterval),
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(Config::radius),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % config.scanInterval() != 0) return;

        AABB area = player.getBoundingBox().inflate(config.radius());
        var villagers = player.level().getEntitiesOfClass(Villager.class, area);
        for (Villager villager : villagers) {
            var offers = villager.getOffers();
            boolean restocked = false;
            for (var offer : offers) {
                if (offer.getUses() > 0) {
                    offer.resetUses();
                    restocked = true;
                }
            }
            // Vanilla's own restock() resets uses and then resends the offers.
            // Skipping the resend leaves the client on the copy it was sent when
            // the screen opened, so a refilled trade keeps drawing its
            // out-of-stock cross until the player closes and reopens the screen.
            if (restocked && villager.getTradingPlayer() instanceof ServerPlayer viewer) {
                viewer.sendMerchantOffers(
                    viewer.containerMenu.containerId,
                    offers,
                    villager.getVillagerData().level(),
                    villager.getVillagerXp(),
                    villager.showProgressBar(),
                    villager.canRestock());
            }
        }
    }
}
