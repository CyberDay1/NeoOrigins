package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Random;

/**
 * Triggers an action when the player takes damage above a threshold.
 * action values: "teleport", "ignite_attacker", "effect_on_attacker"
 */
public class ActionOnHitTakenPower extends PowerType<ActionOnHitTakenPower.Config> {

    private static final Random RANDOM = new Random();

    public record Config(
        String action,
        float minDamage,
        float chance,
        Optional<Identifier> effect,
        int duration,
        int amplifier,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("action", "teleport").forGetter(Config::action),
            Codec.FLOAT.optionalFieldOf("min_damage", 0.0f).forGetter(Config::minDamage),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(Config::chance),
            Identifier.CODEC.optionalFieldOf("effect").forGetter(Config::effect),
            Codec.INT.optionalFieldOf("duration", 100).forGetter(Config::duration),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Config::amplifier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onHit(ServerPlayer player, Config config, float amount) {
        if (amount < config.minDamage()) return;
        if (RANDOM.nextFloat() > config.chance()) return;

        switch (config.action()) {
            case "teleport" -> randomTeleport(player);
            case "ignite_attacker" -> {
                // Attacker is not passed here; handled via OriginEventHandler
            }
            case "effect_on_attacker" -> {
                // Handled via OriginEventHandler which has access to the damage source
            }
        }
    }

    private void randomTeleport(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 pos = player.position();
        for (int i = 0; i < 16; i++) {
            double tx = pos.x + (RANDOM.nextDouble() - 0.5) * 16;
            double ty = pos.y + (RANDOM.nextDouble() - 0.5) * 8;
            double tz = pos.z + (RANDOM.nextDouble() - 0.5) * 16;
            ty = Math.max(level.getMinY(), Math.min(level.getMaxY(), ty));
            BlockPos target = new BlockPos((int) tx, (int) ty, (int) tz);
            if (level.getBlockState(target).isAir() && level.getBlockState(target.above()).isAir()) {
                player.teleportTo(tx, ty, tz);
                break;
            }
        }
    }
}
