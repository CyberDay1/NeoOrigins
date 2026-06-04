package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.LootPoolGrantPower;
import net.minecraft.resources.Identifier;
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
 * Tag-markers go through FTBQ's stable {@code ObjectCompletedEvent.QUEST}
 * contract (Architectury event) and require pack authors to add one tag string
 * — trading reward-GUI integration for soft-dep robustness, which is the right
 * call for a backlog item that explicitly permits "ship the standalone power"
 * if the reward route is too unstable.
 *
 * <p><b>Reflection (not typed) on this branch:</b> there is no FTB Quests build
 * for this MC version, so FTBQ is not on the compile classpath here and the
 * event is bound by reflection. The class/field names below are verified
 * against {@code ftb-quests-neoforge-2101.1.25} (the 1.21.1 line), whose
 * package layout FTBQ keeps stable across MC versions; the gate means none of
 * it classloads at runtime unless an {@code ftbquests} build is ever present.
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
        // TODO(v2.2): FTBQ soft-compat reward registration —
        // wire registerRewardType() once FTBQ's RewardType.Provider API
        // stabilises across versions. Tag-marker path covers the same use
        // case in the meantime.
        if (eventOk) {
            NeoOrigins.LOGGER.info("[Compat] FTB Quests loot_pool_grant tag-marker listener active "
                + "(use tag '{}<table_id>' on a quest to grant)", TAG_PREFIX);
        } else {
            NeoOrigins.LOGGER.warn("[Compat] FTB Quests detected but the quest-completed hook "
                + "could not be wired — pack-side tag-marker rewards will be inert. "
                + "loot_pool_grant still works as a normal active power.");
        }
    }

    // ── Architectury event registration via reflection ─────────────────

    private static boolean tryRegisterCompletedEventListener() {
        try {
            // The real completion event is ObjectCompletedEvent; its per-quest
            // variant is the static QUEST field — an Event<EventActor<QuestEvent>>.
            // (The old api.event.QuestCompletedEvent class never existed in any
            // shipped FTBQ build, so the hook silently never registered.)
            Class<?> eventClass = Class.forName(
                "dev.ftb.mods.ftbquests.events.ObjectCompletedEvent");
            Field questField = eventClass.getField("QUEST");
            Object archEvent = questField.get(null);
            Method register = archEvent.getClass().getMethod("register", Object.class);

            // Listener contract is Architectury's EventActor functional interface
            // (single method: EventResult act(T)). Proxy it.
            Class<?> listenerType = Class.forName("dev.architectury.event.EventActor");

            // act(T) MUST return an EventResult — returning null NPEs Architectury's
            // event combiner. Resolve EventResult.pass() (the neutral value) once.
            Object passResult = Class.forName("dev.architectury.event.EventResult")
                .getMethod("pass").invoke(null);

            InvocationHandler handler = (proxy, method, args) -> {
                if ("act".equals(method.getName()) && args != null && args.length == 1) {
                    onQuestCompleted(args[0]);
                    return passResult;
                }
                // Object methods (toString/hashCode/equals) on the proxy.
                return switch (method.getName()) {
                    case "toString" -> "NeoOriginsFtbqQuestCompletedActor";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            };
            Object proxy = Proxy.newProxyInstance(
                FtbQuestsCompat.class.getClassLoader(),
                new Class<?>[]{listenerType},
                handler);
            register.invoke(archEvent, proxy);
            return true;
        } catch (ClassNotFoundException cnf) {
            NeoOrigins.LOGGER.debug("[Compat] FTBQ ObjectCompletedEvent class not present — soft-compat inert");
            return false;
        } catch (Throwable t) {
            NeoOrigins.LOGGER.warn("[Compat] FTBQ event hook failed to register: {}", t.toString());
            return false;
        }
    }

    // ── Event handler ──────────────────────────────────────────────────

    private static void onQuestCompleted(Object event) {
        try {
            Object questData = reflectGet(event, "getQuest", "getObject", "quest", "object");
            if (questData == null) return;

            // FTBQ quests expose tags as Set<String> (QuestObjectBase.getTags()).
            Object tagsRaw = reflectGet(questData, "getTags", "tags");
            if (!(tagsRaw instanceof Collection<?> tags) || tags.isEmpty()) return;

            // FTBQ completion is team-based: no single player. Grant to every
            // online team member (ObjectProgressEvent.getOnlineMembers()).
            Object membersRaw = reflectGet(event, "getOnlineMembers", "getNotifiedPlayers");
            if (!(membersRaw instanceof Collection<?> members) || members.isEmpty()) return;

            String questId = stringFrom(reflectGet(questData, "getCodeString", "getId", "id"));
            if (questId == null) questId = "ftbq_unknown";

            for (Object t : tags) {
                if (!(t instanceof String tag)) continue;
                if (!tag.startsWith(TAG_PREFIX)) continue;
                String tableIdRaw = tag.substring(TAG_PREFIX.length()).trim();
                if (tableIdRaw.isEmpty()) continue;
                Identifier tableId;
                try {
                    tableId = Identifier.parse(tableIdRaw);
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn(
                        "[Compat][FTBQ] quest '{}' tag '{}' has unparseable loot_table id: {}",
                        questId, tag, e.getMessage());
                    continue;
                }
                String grantId = "ftbq:" + questId + ":" + tableIdRaw;
                for (Object m : members) {
                    if (m instanceof ServerPlayer sp) {
                        LootPoolGrantPower.fireLootPoolGrant(sp, tableId, grantId);
                    }
                }
            }
        } catch (Throwable t) {
            // Best-effort; never throw out of an FTBQ event listener.
            NeoOrigins.LOGGER.debug("[Compat][FTBQ] quest-completed handler errored: {}", t.toString());
        }
    }

    /**
     * Reserved hook for the eventual {@code RewardType} registration path —
     * intentionally a no-op today (see class doc). Pack authors who want
     * book-integrated rewards can use the tag-marker path now and migrate
     * trivially once this lights up.
     *
     * TODO(v2.2): implement registration against
     * {@code dev.ftb.mods.ftbquests.quest.reward.RewardType} once the
     * Provider API stabilises across FTBQ minor versions.
     */
    @SuppressWarnings("unused")
    private static void registerRewardType() {
        // No-op — see TODO above.
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
