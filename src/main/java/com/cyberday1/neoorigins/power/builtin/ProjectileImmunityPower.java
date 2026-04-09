package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;

import java.util.List;

/**
 * Blocks specific projectile types from dealing damage to this player.
 * projectile_types values: "arrow", "fireball", "trident", "all"
 * Handled via ProjectileImpactEvent in OriginEventHandler.
 */
public class ProjectileImmunityPower extends PowerType<ProjectileImmunityPower.Config> {

    public record Config(List<String> projectileTypes, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("projectile_types", List.of("arrow")).forGetter(Config::projectileTypes),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));

        public boolean blocks(Projectile projectile) {
            for (String t : projectileTypes) {
                if ("all".equalsIgnoreCase(t)) return true;
                if ("arrow".equalsIgnoreCase(t) && projectile instanceof AbstractArrow) return true;
                if ("fireball".equalsIgnoreCase(t) && projectile instanceof AbstractHurtingProjectile) return true;
                if ("trident".equalsIgnoreCase(t) && projectile instanceof ThrownTrident) return true;
            }
            return false;
        }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
