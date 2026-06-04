package com.cyberday1.neoorigins.compat.ftbultimine;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.UltiminePower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import dev.ftb.mods.ftbultimine.api.restriction.RegisterRestrictionHandlerEvent;
import dev.ftb.mods.ftbultimine.api.restriction.RestrictionHandler;
import dev.ftb.mods.ftbultimine.api.util.CanUltimineResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Isolated, typed bridge to the FTB Ultimine restriction API.
 *
 * <p>Every symbol here resolves against the compile-only {@code ftbultimine}
 * dependency, so this class is <b>only ever classloaded</b> behind a
 * {@code ModList.isLoaded("ftbultimine")} gate in {@link NeoOrigins} — mirroring
 * the Accessories ({@code compat.condition.AccessoriesCompat}) and FTBQ reward
 * ({@code compat.ftbquests.FtbQuestsRewardRegistration}) isolation patterns. A
 * runtime without FTB Ultimine never triggers a {@code NoClassDefFoundError}
 * because the gate prevents this class from loading at all.
 *
 * <h3>API surface confirmed against {@code ftb-ultimine-neoforge-2101.1.14} via
 * {@code javap}</h3>
 * <ul>
 *   <li>{@code RegisterRestrictionHandlerEvent.REGISTER} is an Architectury
 *       {@code Event<RegisterRestrictionHandlerEvent>}; register a listener that
 *       receives a {@code Registry} and calls {@code registry.register(handler)}.</li>
 *   <li>{@link RestrictionHandler#canUltimine(Player)} returns {@code boolean};
 *       {@code true} = "no objection" (neutral). {@code ultimineBlockReason} (a
 *       default method) supplies the denial reason when {@code canUltimine} is
 *       {@code false}.</li>
 *   <li>FTB Ultimine aggregates handlers as an <b>AND-gate</b>: the first handler
 *       whose {@code canUltimine} returns {@code false} short-circuits and its
 *       {@code ultimineBlockReason} is the result; only if every handler returns
 *       {@code true} does the player get {@code CanUltimineResult.ALLOWED}.</li>
 * </ul>
 *
 * <h3>Why this denies rather than "grants"</h3>
 * The restriction API is a permission <i>veto</i>, not a grant: FTB Ultimine
 * already allows everyone by default and there is no per-player "enable" hook.
 * To make the {@code neoorigins:ultimine} power meaningful we register a handler
 * that <b>denies non-holders</b> ({@code canUltimine → false}) and stays neutral
 * for holders ({@code canUltimine → true}). The net effect: when this power
 * exists on an origin, only active holders may vein-mine. There is intentionally
 * no override for block-count, require-tool, or shape — the API exposes no setter
 * for those (they follow FTB Ultimine's own server config).
 */
public final class FtbUltimineCompat {

    private FtbUltimineCompat() {}

    /** Denial reason surfaced to non-holders (translation key resolved client-side). */
    private static final CanUltimineResult NO_ORIGIN_GRANT =
        CanUltimineResult.prevent("neoorigins.ultimine.no_power");

    /**
     * Registers the origin-gated restriction handler with FTB Ultimine. Must run
     * only when {@code ftbultimine} is on the mod list (the {@link NeoOrigins}
     * caller gates this). Best-effort: any failure logs and leaves FTB Ultimine
     * at its own defaults.
     */
    public static void register() {
        try {
            RegisterRestrictionHandlerEvent.REGISTER.register(registry ->
                registry.register(new OriginUltimineRestriction()));
            NeoOrigins.LOGGER.info(
                "[Compat] FTB Ultimine restriction handler registered — vein-mining "
                + "now requires an active neoorigins:ultimine power.");
        } catch (Throwable t) {
            NeoOrigins.LOGGER.warn(
                "[Compat] FTB Ultimine restriction handler failed to register ({}); "
                + "vein-mining will follow FTB Ultimine's own defaults.", t.toString());
        }
    }

    /**
     * Allows ultimine only for players holding an active {@code neoorigins:ultimine}
     * power; vetoes everyone else. {@code canUltimine} returning {@code true} is
     * the neutral value, so a player who already has the power is unaffected and
     * other restriction handlers (FTB Ranks, tool/food checks) still apply.
     *
     * <p><b>Dormant when unused.</b> Because the restriction API is deny-only,
     * an unconditional "non-holders get {@code false}" would disable vein-mining
     * for everyone the moment NeoOrigins + FTB Ultimine are installed together —
     * even when no loaded pack ever defines an ultimine power, violating the
     * soft-dep "dormant if unused" contract. So this handler first asks
     * {@link PowerDataManager#isUltiminePowerInUse()} (a cheap cached flag,
     * recomputed on each datapack reload): if zero {@code neoorigins:ultimine}
     * powers are loaded it returns {@code true} (neutral) for <i>everyone</i>,
     * leaving FTB Ultimine exactly at its vanilla behaviour. Only once at least
     * one ultimine power is loaded does the origin gating apply.
     */
    private static final class OriginUltimineRestriction implements RestrictionHandler {
        @Override
        public boolean canUltimine(Player player) {
            // Dormant unless a loaded pack actually defines an ultimine power.
            // Without this the deny-only API would gate vein-mining for everyone
            // even when no pack opted in.
            if (!PowerDataManager.INSTANCE.isUltiminePowerInUse()) {
                return true; // no opinion — FTB Ultimine behaves as vanilla
            }
            // Only server players carry origin data; the check runs server-side.
            if (!(player instanceof ServerPlayer sp)) {
                return true; // no opinion on non-server contexts
            }
            return ActiveOriginService.has(sp, UltiminePower.class, cfg -> true);
        }

        @Override
        public CanUltimineResult ultimineBlockReason(Player player) {
            return NO_ORIGIN_GRANT;
        }
    }
}
