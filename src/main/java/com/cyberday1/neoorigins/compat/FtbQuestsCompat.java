package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.LootPoolGrantPower;
import dev.architectury.event.EventResult;
import dev.ftb.mods.ftbquests.events.ObjectCompletedEvent;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Soft-compat hook for FTB Quests (v2.1.6 backlog #3).
 *
 * <p>FTBQ is a <b>compile-only</b> soft dependency: every symbol here resolves
 * against {@code ftb-quests-neoforge} at compile time, but the mod is optional
 * at runtime. Activation gate is {@code ModList.get().isLoaded("ftbquests")}
 * from {@link NeoOrigins}; this class is only classloaded when that check
 * passes, so a runtime without FTBQ never hits a {@code NoClassDefFoundError}.
 *
 * <h3>Integration shape (chosen path: tag-marker on quest completion)</h3>
 * On quest completion, we read the completing quest's user-defined tag list
 * and look for the opt-in marker
 * <pre>{@code neoorigins_loot_pool_grant:<loot_table_id>}</pre>
 * When found, we roll that loot table against every online team member who
 * completed the quest via {@link LootPoolGrantPower#fireLootPoolGrant}. The
 * grantId is composed from the quest's hex id so dedup tracking lines up with
 * the player's existing grant-equipment attachment.
 *
 * <h3>Event API (typed against {@code ftb-quests-neoforge-2101.1.25})</h3>
 * FTBQ fires completion through the Architectury event
 * {@link ObjectCompletedEvent}. Its {@link ObjectCompletedEvent#QUEST} field is
 * the per-quest variant — an {@code Event<EventActor<ObjectCompletedEvent.QuestEvent>>}
 * whose listener returns an {@link EventResult}. The event is <b>team-based</b>:
 * there is no single {@code getPlayer()}; instead
 * {@code getOnlineMembers()} (inherited from {@code ObjectProgressEvent})
 * yields the {@link ServerPlayer}s on the completing team who are online.
 *
 * <p>Why tag-marker (not only a registered RewardType): the tag route goes
 * through this stable completion event and needs nothing from the reward GUI,
 * so it is the robust soft-dep path. The first-class reward type
 * ({@link #registerRewardType()}) is wired separately and shares the same
 * grant pipeline.
 */
public final class FtbQuestsCompat {

    private FtbQuestsCompat() {}

    /** Tag prefix authors put on a quest to wire it to a loot pool. */
    public static final String TAG_PREFIX = "neoorigins_loot_pool_grant:";

    public static void register() {
        try {
            ObjectCompletedEvent.QUEST.register(FtbQuestsCompat::onQuestCompleted);
            NeoOrigins.LOGGER.info("[Compat] FTB Quests loot_pool_grant tag-marker listener active "
                + "(use tag '{}<table_id>' on a quest to grant)", TAG_PREFIX);
        } catch (Throwable t) {
            NeoOrigins.LOGGER.warn("[Compat] FTB Quests detected but the quest-completed hook "
                + "could not be wired ({}) — pack-side tag-marker rewards will be inert. "
                + "loot_pool_grant still works as a normal active power.", t.toString());
        }
    }

    // ── Event handler ──────────────────────────────────────────────────

    private static EventResult onQuestCompleted(ObjectCompletedEvent.QuestEvent event) {
        try {
            Quest quest = event.getQuest();
            if (quest == null) return EventResult.pass();

            Set<String> tags = quest.getTags();
            if (tags == null || tags.isEmpty()) return EventResult.pass();

            String questId = quest.getCodeString();

            for (String tag : tags) {
                if (tag == null || !tag.startsWith(TAG_PREFIX)) continue;
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
                for (ServerPlayer sp : event.getOnlineMembers()) {
                    if (sp == null) continue;
                    LootPoolGrantPower.fireLootPoolGrant(sp, tableId, grantId);
                }
            }
        } catch (Throwable t) {
            // Best-effort; never throw out of an FTBQ event listener.
            NeoOrigins.LOGGER.debug("[Compat][FTBQ] quest-completed handler errored: {}", t.toString());
        }
        return EventResult.pass();
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
}
