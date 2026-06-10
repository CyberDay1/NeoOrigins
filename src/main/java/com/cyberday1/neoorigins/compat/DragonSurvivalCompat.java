package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Soft-compat bridge to the (proprietary, un-versioned-on-Maven) Dragon Survival
 * mod. Everything is done reflectively and guarded by {@link #isLoaded()}, so
 * NeoOrigins never compiles against — nor classloads — any Dragon Survival type
 * when the mod is absent. We hook DS rather than re-implementing it: a
 * {@code neoorigins:become_dragon} power drives DS's own dragon state so DS
 * supplies the actual traits, growth, abilities, altar economy and hunters.
 *
 * <p><b>Brittleness note.</b> DS exposes no public addon API and isn't on a
 * Maven, so these calls bind to internal method/class names by reflection. If a
 * future DS release renames {@code DragonStateProvider.getData},
 * {@code DragonStateHandler.setSpecies}, {@code PlayerLoginHandler.syncComplete}
 * etc., the bridge no-ops. Every reflective miss logs <b>once, loudly</b> so the
 * breakage is diagnosable from the log rather than mysterious in-game.
 */
public final class DragonSurvivalCompat {

    private DragonSurvivalCompat() {}

    public static final String MOD_ID = "dragonsurvival";

    // DS package roots / class names (branch 1.21.1, v2.0.x).
    private static final String C_STATE_PROVIDER =
        "by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider";
    private static final String C_LOGIN_HANDLER =
        "by.dragonsurvivalteam.dragonsurvival.server.handlers.PlayerLoginHandler";
    private static final String SPECIES_REGISTRY = "dragon_species";
    private static final String STAGE_REGISTRY = "dragon_stage";

    private static Boolean loaded;

    // Resolved reflection handles (null until initReflection runs).
    private static boolean reflectionInit;
    private static Method mGetData;     // DragonStateProvider.getData(Player) -> handler
    private static Method mIsDragon;    // DragonStateProvider.isDragon(Entity) -> boolean
    private static Method mSyncComplete; // PlayerLoginHandler.syncComplete(Entity)

    // Handler-instance methods, resolved lazily off the first live handler.
    private static volatile boolean handlerMethodsInit;
    private static Method mSetSpecies;  // handler.setSpecies(Player, Holder)
    private static Method mSetStage;    // handler.setStage(Player, Holder)
    private static Method mRevert;      // handler.revertToHumanForm(Player, boolean)
    private static Method mSpeciesId;   // handler.speciesId() -> Identifier

    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    /** True when the Dragon Survival mod is on the mod list. Cached. */
    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }

    /**
     * Transform {@code player} into the given Dragon Survival species (and optional
     * starting stage), then push DS's full state sync. No-op if DS is absent or the
     * species/stage ids don't resolve.
     *
     * @param speciesId DS species id, e.g. {@code dragonsurvival:cave_dragon}
     * @param stageId   DS stage id, e.g. {@code dragonsurvival:newborn} (nullable/blank → species default)
     */
    public static void becomeDragon(ServerPlayer player, String speciesId, String stageId) {
        if (!isLoaded()) return;
        if (!initReflection()) return;
        try {
            Holder<?> species = resolveHolder(player, SPECIES_REGISTRY, Identifier.parse(speciesId));
            if (species == null) {
                warnOnce("unknown dragon species '" + speciesId + "'", null);
                return;
            }
            Object handler = mGetData.invoke(null, player);
            if (handler == null) return;
            if (!initHandlerMethods(handler)) return;

            mSetSpecies.invoke(handler, player, species);
            if (stageId != null && !stageId.isBlank() && mSetStage != null) {
                Holder<?> stage = resolveHolder(player, STAGE_REGISTRY, Identifier.parse(stageId));
                if (stage != null) mSetStage.invoke(handler, player, stage);
            }
            if (mSyncComplete != null) mSyncComplete.invoke(null, player);
        } catch (Throwable t) {
            warnOnce("becomeDragon(" + speciesId + ")", t);
        }
    }

    /** Revert {@code player} back to human form via DS, then sync. No-op if DS is absent. */
    public static void revertToHuman(ServerPlayer player) {
        if (!isLoaded()) return;
        if (!initReflection()) return;
        try {
            Object handler = mGetData.invoke(null, player);
            if (handler == null) return;
            if (!initHandlerMethods(handler)) return;
            if (mRevert != null) mRevert.invoke(handler, player, false);
            if (mSyncComplete != null) mSyncComplete.invoke(null, player);
        } catch (Throwable t) {
            warnOnce("revertToHuman", t);
        }
    }

    /** True if DS currently considers {@code player} a dragon. False if DS is absent. */
    public static boolean isDragon(ServerPlayer player) {
        if (!isLoaded() || !initReflection() || mIsDragon == null) return false;
        try {
            Object r = mIsDragon.invoke(null, player);
            return r instanceof Boolean b && b;
        } catch (Throwable t) {
            warnOnce("isDragon", t);
            return false;
        }
    }

    /** DS species id of {@code player}, or null if not a dragon / DS absent. */
    public static Identifier speciesOf(ServerPlayer player) {
        if (!isLoaded() || !initReflection()) return null;
        try {
            Object handler = mGetData.invoke(null, player);
            if (handler == null || !initHandlerMethods(handler) || mSpeciesId == null) return null;
            Object id = mSpeciesId.invoke(handler);
            return id instanceof Identifier rl ? rl : null;
        } catch (Throwable t) {
            warnOnce("speciesOf", t);
            return null;
        }
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static synchronized boolean initReflection() {
        if (reflectionInit) return mGetData != null;
        reflectionInit = true;
        try {
            Class<?> provider = Class.forName(C_STATE_PROVIDER);
            mGetData = findMethod(provider, "getData", 1);
            mIsDragon = findMethod(provider, "isDragon", 1);
            Class<?> login = Class.forName(C_LOGIN_HANDLER);
            mSyncComplete = findMethod(login, "syncComplete", 1);
            if (mGetData == null) {
                warnOnce("DragonStateProvider.getData not found", null);
            }
        } catch (Throwable t) {
            warnOnce("resolve DS classes", t);
        }
        return mGetData != null;
    }

    private static boolean initHandlerMethods(Object handler) {
        if (handlerMethodsInit) return mSetSpecies != null;
        synchronized (DragonSurvivalCompat.class) {
            if (handlerMethodsInit) return mSetSpecies != null;
            Class<?> h = handler.getClass();
            mSetSpecies = findMethod(h, "setSpecies", 2);
            mSetStage = findMethod(h, "setStage", 2);
            mRevert = findMethod(h, "revertToHumanForm", 2);
            mSpeciesId = findMethod(h, "speciesId", 0);
            handlerMethodsInit = true;
            if (mSetSpecies == null) warnOnce("DragonStateHandler.setSpecies not found", null);
            return mSetSpecies != null;
        }
    }

    /** First accessible method on {@code cls} (or a supertype) matching name + arity. */
    private static Method findMethod(Class<?> cls, String name, int paramCount) {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                m.setAccessible(true);
                return m;
            }
        }
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Holder<?> resolveHolder(ServerPlayer player, String registryPath, Identifier entryId) {
        try {
            RegistryAccess access = player.registryAccess();
            ResourceKey regKey = ResourceKey.createRegistryKey(
                Identifier.fromNamespaceAndPath(MOD_ID, registryPath));
            // 26.1: RegistryAccess.registryOrThrow → lookupOrThrow, and the
            // holder accessor is Registry.get(Identifier) → Optional<Holder.Reference>.
            Registry reg = access.lookupOrThrow(regKey);
            Optional<?> opt = reg.get(entryId);
            return (Holder<?>) opt.orElse(null);
        } catch (Throwable t) {
            warnOnce("resolve " + registryPath + "/" + entryId, t);
            return null;
        }
    }

    private static void warnOnce(String what, Throwable t) {
        if (WARNED.compareAndSet(false, true)) {
            NeoOrigins.LOGGER.warn(
                "[DragonSurvivalCompat] Dragon Survival integration hit a reflection issue ({}). "
                + "DS may have changed its internals; dragon origins will not transform players. "
                + "Further occurrences are suppressed.", what, t);
        }
    }
}
