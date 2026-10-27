package com.cyberday1.neoorigins.compat.ftbquests;

import com.cyberday1.neoorigins.NeoOrigins;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;

/**
 * Registers {@link NeoOriginsLootPoolReward} with FTB Quests' {@code RewardType}
 * registry so it appears as a first-class "NeoOrigins: grant loot pool" reward
 * in the quest editor.
 *
 * <p>Every symbol here resolves against the compile-only FTBQ dependency, so
 * this class is only ever classloaded behind the {@code ftbquests} mod-list
 * gate in {@link com.cyberday1.neoorigins.compat.FtbQuestsCompat}. Registration
 * runs from NeoOrigins' common-setup phase, after FTBQ's own types are in the
 * static {@code RewardTypes.TYPES} map, so our entry persists alongside them.
 * FTBQ 26.1 derives the editor name from the type id
 * ({@code ftbquests.reward.neoorigins.loot_pool}); there is no setter any more.
 */
public final class FtbQuestsRewardRegistration {

    private FtbQuestsRewardRegistration() {}

    public static void register() {
        try {
            NeoOriginsLootPoolReward.TYPE = RewardTypes.register(
                NeoOriginsLootPoolReward.TYPE_ID,
                NeoOriginsLootPoolReward::new,
                NeoOriginsLootPoolReward::defaultIcon);
            NeoOrigins.LOGGER.info("[Compat] FTB Quests reward type '{}' registered (grant loot pool)",
                NeoOriginsLootPoolReward.TYPE_ID);
        } catch (Throwable t) {
            // Never let an FTBQ API shift hard-fail NeoOrigins startup — the
            // tag-marker grant path remains available regardless.
            NeoOrigins.LOGGER.warn(
                "[Compat] FTB Quests reward-type registration failed ({}); "
                + "loot-pool grants still work via the quest tag-marker path.",
                t.toString());
        }
    }
}
