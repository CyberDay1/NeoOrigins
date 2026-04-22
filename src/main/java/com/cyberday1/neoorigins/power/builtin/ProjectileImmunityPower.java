package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;

import java.util.List;

/**
 * Blocks specific projectile types from dealing damage to this player.
 * projectile_types values: "arrow", "fireball", "trident", "all"
 *
 * <p>Optional {@code chance} field (0.0–1.0, default 1.0) rolls an RNG gate
 * per impact — at 0.5 it behaves like vanilla Endermen's projectile dodge
 * (about half of incoming projectiles are no-ops).
 *
 * <p>Optional {@code teleport} flag (default false) triggers a short random
 * teleport on successful dodge, matching Enderman flavour. Teleport distance
 * capped by {@code teleport_range} (default 16 blocks).
 *
 * <p>Handled via ProjectileImpactEvent in CombatPowerEvents.
 */
public class ProjectileImmunityPower extends PowerType<ProjectileImmunityPower.Config> {

    public record Config(
        List<String> projectileTypes,
        float chance,
        boolean teleport,
        int teleportRange,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("projectile_types", List.of("arrow")).forGetter(Config::projectileTypes),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(Config::chance),
            Codec.BOOL.optionalFieldOf("teleport", false).forGetter(Config::teleport),
            Codec.INT.optionalFieldOf("teleport_range", 16).forGetter(Config::teleportRange),
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
