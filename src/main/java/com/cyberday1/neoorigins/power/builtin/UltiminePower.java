package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Grants FTB Ultimine vein-mining to the holder (soft dep on {@code ftbultimine}).
 *
 * <p>This power carries no behaviour of its own: it is a marker the FTB Ultimine
 * bridge looks for. When FTB Ultimine is installed, the FTB Ultimine compat
 * bridge registers
 * a {@code RestrictionHandler} that permits ultimine only for players with an
 * <i>active</i> {@code neoorigins:ultimine} power and denies it for everyone
 * else. FTB Ultimine aggregates restriction handlers as an AND-gate (the first
 * handler that disallows wins), so this effectively turns vein-mining into an
 * origin-gated ability: install this power on an origin and only that origin's
 * holders may ultimine.
 *
 * <p><b>What this power cannot configure.</b> FTB Ultimine's
 * {@code RestrictionHandler} API is a coarse allow/deny permission hook — it has
 * no setter for the per-player block limit, the require-tool toggle, or the
 * mining shape. Those follow FTB Ultimine's own server config (and FTB Ranks /
 * attribute overrides), so this power deliberately exposes <b>no</b>
 * {@code max_blocks} / {@code require_tool} / {@code shape} fields: faking config
 * the API can't honour would be a lie. The power is intentionally config-light —
 * "while this power is active you may vein-mine" — which is the full extent of
 * what the restriction hook supports.
 *
 * <p>When FTB Ultimine is absent, this power is an inert marker (no handler is
 * registered, nothing classloads the FTB-Ultimine-typed bridge).
 */
public class UltiminePower extends PowerType<UltiminePower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
