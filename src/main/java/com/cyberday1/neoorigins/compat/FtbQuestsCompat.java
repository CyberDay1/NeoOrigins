package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.LootPoolGrantPower;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;

/**
 * Soft-compat hook for FTB Quests (v2.1.6 backlog #3).
 *
 * <p>FTBQ is <b>not</b> on this project's compile classpath; every reference
 * here goes through reflection so the mod still builds and runs cleanly when
 * FTBQ is absent. Activation gate is
 * {@code ModList.get().isLoaded("ftbquests")} from {@link NeoOrigins}; this
 * class is only classloaded when that check passes.
 *
 * <h3>Integration shape (chosen path: tag-marker on quest completion)</h3>
 * On quest completion, we read the completing quest's user-defined tag list
 * and look for the opt-in marker
 * <pre>{@code neoorigins_loot_pool_grant:<loot_table_id>}</pre>
 * When found, we roll that loot table against the completing player via
 * {@link LootPoolGrantPower#fireLootPoolGrant}. The grantId is composed from
 * the quest's id so dedup tracking lines up with the player's existing
 * grant-equipment attachment.
 *
 * <p>Why tag-marker (not a registered RewardType): FTBQ's
 * {@code RewardType} registry requires implementing a {@code RewardType.Provider}
 * with config-GUI hooks (icon, NBT round-trip, type id registration on
 * {@code ftbquests:types}). Doing that safely via reflection across FTBQ
 * versions is brittle — the public RewardType API has shifted between minor
 * versions, and a registration mistake silently breaks every FTBQ quest book.
 * Tag-markers go through FTBQ's stable {@code QuestCompletedEvent} contract
 * (Architectury event, surfaces both client and server) and require pack
 * authors to add one tag string — trading reward-GUI integration for soft-dep
 * robustness, which is the right call for a backlog item that explicitly
 * permits "ship the standalone power" if the reward route is too unstable.
 *
 * <p>If a future version of FTBQ stabilises {@code RewardType.Provider}, the
 * {@link #registerRewardType()} stub below is the hook to wire it up.
 */
public final class FtbQuestsCompat {

    private FtbQuestsCompat() {}

    /** Tag prefix authors put on a quest to wire it to a loot pool. */
    public static final String TAG_PREFIX = "neoorigins_loot_pool_grant:";

    public static void register() {
        boolean eventOk = tryRegisterCompletedEventListener();
        if (eventOk) {
            NeoOrigins.LOGGER.info("[Compat] FTB Quests loot_pool_grant tag-marker listener active "
                + "(use tag '{}<table_id>' on a quest to grant)", TAG_PREFIX);
        } else {
            NeoOrigins.LOGGER.warn("[Compat] FTB Quests detected but the QuestCompletedEvent hook "
                + "could not be wired — pack-side tag-marker rewards will be inert. "
                + "loot_pool_grant still works as a normal active power.");
        }
    }

    // ── Architectury event registration via reflection ─────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean tryRegisterCompletedEventListener() {
        try {
            Class<?> eventClass = Class.forName(
                "dev.ftb.mods.ftbquests.api.event.QuestCompletedEvent");
            // Architectury Event field is the static EVENT singleton.
            Field eventField = eventClass.getField("EVENT");
            Object archEvent = eventField.get(null);
            Method register = archEvent.getClass().getMethod("register", Object.class);

            // The Architectury event parameter type is QuestCompletedEvent itself
            // (single-method consumer-style functional interface). Build a Proxy
            // implementing that interface that just dispatches every invoke into
            // our handler.
            Class<?> listenerType = findListenerInterface(eventClass);
            if (listenerType == null) {
                NeoOrigins.LOGGER.debug("[Compat] FTBQ QuestCompletedEvent: no listener interface located");
                return false;
            }

            InvocationHandler handler = (proxy, method, args) -> {
                if (args == null || args.length == 0) return null;
                onQuestCompleted(args[0]);
                return null;
            };
            Object proxy = Proxy.newProxyInstance(
                FtbQuestsCompat.class.getClassLoader(),
                new Class<?>[]{listenerType},
                handler);
            register.invoke(archEvent, proxy);
            return true;
        } catch (ClassNotFoundException cnf) {
            NeoOrigins.LOGGER.debug("[Compat] FTBQ QuestCompletedEvent class not present — soft-compat inert");
            return false;
        } catch (Throwable t) {
            NeoOrigins.LOGGER.warn("[Compat] FTBQ event hook failed to register: {}", t.toString());
            return false;
        }
    }

    /**
     * Architectury events conventionally use a single-method nested
     * functional interface (often named like the event itself). We search the
     * event class for any inner interface to use as the proxy contract.
     */
    private static Class<?> findListenerInterface(Class<?> eventClass) {
        for (Class<?> inner : eventClass.getDeclaredClasses()) {
            if (inner.isInterface()) return inner;
        }
        // Some FTBQ versions expose the listener as the event class itself
        // (used as a functional interface). Accept that as a fallback.
        if (eventClass.isInterface()) return eventClass;
        return null;
    }

    // ── Event handler ──────────────────────────────────────────────────

    private static void onQuestCompleted(Object event) {
        try {
            Object questData = reflectGet(event, "getQuest", "quest", "questObject", "object");
            if (questData == null) return;
            Object player = reflectGet(event, "getPlayer", "player", "getServerPlayer");
            if (!(player instanceof ServerPlayer sp)) return;

            // FTBQ quests expose tags as List<String>; some versions return a
            // Collection. Walk reflectively so we don't bind to either.
            Object tagsRaw = reflectGet(questData, "getTags", "tags");
            if (!(tagsRaw instanceof Collection<?> tags) || tags.isEmpty()) return;

            String questId = stringFrom(reflectGet(questData, "getId", "id", "getCodeString"));
            if (questId == null) questId = "ftbq_unknown";

            for (Object t : tags) {
                if (!(t instanceof String tag)) continue;
                if (!tag.startsWith(TAG_PREFIX)) continue;
                String tableIdRaw = tag.substring(TAG_PREFIX.length()).trim();
                if (tableIdRaw.isEmpty()) continue;
                ResourceLocation tableId;
                try {
                    tableId = ResourceLocation.parse(tableIdRaw);
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn(
                        "[Compat][FTBQ] quest '{}' tag '{}' has unparseable loot_table id: {}",
                        questId, tag, e.getMessage());
                    continue;
                }
                String grantId = "ftbq:" + questId + ":" + tableIdRaw;
                LootPoolGrantPower.fireLootPoolGrant(sp, tableId, grantId);
            }
        } catch (Throwable t) {
            // Best-effort; never throw out of an FTBQ event listener.
            NeoOrigins.LOGGER.debug("[Compat][FTBQ] quest-completed handler errored: {}", t.toString());
        }
    }

    /**
     * Registers the first-class {@code neoorigins:loot_pool} reward type with
     * FTBQ's {@code RewardTypes} registry so pack authors can add a
     * "NeoOrigins: grant loot pool" reward directly in the quest editor.
     *
     * <p>Must run after FTBQ's mod constructor (which calls
     * {@code RewardTypes.init()}); {@link NeoOrigins} drives it from common
     * setup. Indirected through
     * {@link com.cyberday1.neoorigins.compat.ftbquests.FtbQuestsRewardRegistration}
     * so the FTBQ-typed reward classes only classload behind this gated call.
     */
    public static void registerRewardType() {
        com.cyberday1.neoorigins.compat.ftbquests.FtbQuestsRewardRegistration.register();
    }

    // ── Reflection helpers ─────────────────────────────────────────────

    private static Object reflectGet(Object obj, String... names) {
        if (obj == null) return null;
        Class<?> clz = obj.getClass();
        for (String name : names) {
            try {
                Method m = findMethod(clz, name);
                if (m != null) { m.setAccessible(true); return m.invoke(obj); }
            } catch (Throwable ignored) {}
            try {
                Field f = findField(clz, name);
                if (f != null) { f.setAccessible(true); return f.get(obj); }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Method findMethod(Class<?> clz, String name) {
        for (Class<?> c = clz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
            }
        }
        return null;
    }

    private static Field findField(Class<?> clz, String name) {
        for (Class<?> c = clz; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static String stringFrom(Object o) {
        return o == null ? null : o.toString();
    }
}
