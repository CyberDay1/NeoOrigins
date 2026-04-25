package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Applies a virtual Fortune-level drop multiplier whenever a configured
 * {@code MobEffect} is active on the player. Used by Caveborn's Mining
 * Fortune (gated by {@code minecraft:luck} from eating diamond), but
 * deliberately generic — any origin can emulate an enchantment-like buff by
 * pairing this with any MobEffect.
 *
 * <p>Config:
 * <ul>
 *   <li>{@code effect} — the gating {@code MobEffect} ID. If the player
 *       doesn't have this effect, nothing happens.</li>
 *   <li>{@code level} — integer Fortune level to apply. The vanilla
 *       {@code ORE_DROPS} formula is used to roll extra drops:
 *       {@code count * (max(0, random(level + 2) - 1) + 1)}.</li>
 *   <li>{@code target} — block tag the bonus applies to. Defaults to
 *       {@code #c:ores} (NeoForge common ores tag — covers every modded ore
 *       that participates in the common tag convention). Pack authors can
 *       override to a narrower vanilla sub-tag (e.g.
 *       {@code #minecraft:diamond_ores}).</li>
 * </ul>
 *
 * <p>Vanilla parity: {@code minecraft:ancient_debris} is hardcoded-excluded
 * because netherite is the single vanilla ore that ignores Fortune.
 *
 * <p>Wired via {@link com.cyberday1.neoorigins.event.FortuneEffectEvents}
 * on the NeoForge event bus, subscribing to {@code BlockDropsEvent}.
 */
public class FortuneWhenEffectPower extends PowerType<FortuneWhenEffectPower.Config> {

    private static final Identifier DEFAULT_EFFECT =
        Identifier.fromNamespaceAndPath("minecraft", "luck");
    private static final String DEFAULT_TARGET = "#c:ores";

    public record Config(
        Identifier effect,
        int level,
        String target,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.optionalFieldOf("effect", DEFAULT_EFFECT).forGetter(Config::effect),
            Codec.INT.optionalFieldOf("level", 2).forGetter(Config::level),
            Codec.STRING.optionalFieldOf("target", DEFAULT_TARGET).forGetter(Config::target),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
