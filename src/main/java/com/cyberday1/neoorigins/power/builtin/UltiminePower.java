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
 *
 * <p><b>Not functional on this build.</b> The bridge described above lives on the
 * 26.1 branch. FTB Ultimine publishes no 26.2 artifact (ftb-ultimine-neoforge
 * stops at 26.1.2.5 on maven.ftb.dev/releases, checked 2026-07-29), so 26.2 has
 * nothing to compile it against and this power is always inert here. The type
 * stays registered so origins ported from 26.1 keep loading, but the creator's
 * type picker hides it ({@code FormModel.UNAVAILABLE_ON_THIS_VERSION}) and
 * {@code PowerDataManager} warns when a loaded pack defines one. Port
 * {@code compat.ftbultimine} and undo both when a 26.2 line ships.
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
