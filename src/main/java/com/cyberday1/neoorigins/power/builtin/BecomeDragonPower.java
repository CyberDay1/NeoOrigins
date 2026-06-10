package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.DragonSurvivalCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Soft-compat power that turns the holder into a Dragon Survival dragon of a
 * configured species while the power is granted, reverting them to human form
 * when it's revoked. Pure hook: Dragon Survival supplies all of the resulting
 * traits, growth and abilities. Inert when Dragon Survival is absent — which is
 * why dragon origins also carry {@code "required_mods": ["dragonsurvival"]} so
 * the origin and this power never load without the mod present.
 *
 * <p>Lifecycle: {@code onGranted} sets the species (and, by inheriting the base
 * defaults, {@code onLogin}/{@code onRespawn} re-apply it so the form survives
 * relog and death); {@code onRevoked} reverts to human.
 */
public class BecomeDragonPower extends PowerType<BecomeDragonPower.Config> {

    public record Config(String type, String species, String stage) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.fieldOf("species").forGetter(Config::species),
            Codec.STRING.optionalFieldOf("stage", "dragonsurvival:newborn").forGetter(Config::stage)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        DragonSurvivalCompat.becomeDragon(player, config.species(), config.stage());
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        DragonSurvivalCompat.revertToHuman(player);
    }
}
