package com.cyberday1.neoorigins.compat.ftbquests;

import com.cyberday1.neoorigins.NeoOrigins;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import net.minecraft.network.chat.Component;

/**
 * Registers {@link NeoOriginsLootPoolReward} with FTB Quests' {@code RewardType}
 * registry so it appears as a first-class "NeoOrigins: grant loot pool" reward
 * in the quest editor.
 *
 * <p>Every symbol here resolves against the compile-only FTBQ dependency, so
 * this class is only ever classloaded behind the {@code ftbquests} mod-list
 * gate in {@link com.cyberday1.neoorigins.compat.FtbQuestsCompat}. Registration
 * runs from NeoOrigins' common-setup phase, which fires after FTBQ's mod
 * constructor has already called {@code RewardTypes.init()} — so the static
 * {@code RewardTypes.TYPES} map exists and our entry persists alongside the
 * built-in types.
 */
public final class FtbQuestsRewardRegistration {

    private FtbQuestsRewardRegistration() {}

    public static void register() {
        try {
            NeoOriginsLootPoolReward.TYPE = RewardTypes.register(
                NeoOriginsLootPoolReward.TYPE_ID,
                NeoOriginsLootPoolReward::new,
                NeoOriginsLootPoolReward::defaultIcon);
            NeoOriginsLootPoolReward.TYPE.setDisplayName(
                Component.translatable("reward.neoorigins.loot_pool"));
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
