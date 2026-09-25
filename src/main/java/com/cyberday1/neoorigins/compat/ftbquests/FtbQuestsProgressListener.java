package com.cyberday1.neoorigins.compat.ftbquests;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.FtbQuestsCompat;
import dev.ftb.mods.ftbquests.api.event.progress.ProgressType;
import dev.ftb.mods.ftbquests.api.neoforge.FTBQuestsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Grants a completed quest's {@code neoorigins_loot_pool_grant:} tags, via the
 * NeoForge-bus progress event FTBQ 26.1.2.x posts. Typed, so only classloaded
 * behind the {@code ftbquests} gate in {@link FtbQuestsCompat}.
 */
public final class FtbQuestsProgressListener {

    private FtbQuestsProgressListener() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FTBQuestsEvent.QuestProgress.class, FtbQuestsProgressListener::onQuestProgress);
    }

    static void onQuestProgress(FTBQuestsEvent.QuestProgress event) {
        var data = event.getEventData();
        if (data.type() != ProgressType.COMPLETED) return;
        var progress = data.progressData();
        var quest = progress.object();
        try {
            FtbQuestsCompat.grantForTags(quest.getCodeString(), quest.getTags(), progress.onlineMembers());
        } catch (RuntimeException e) {
            // Never throw into FTBQ's own completion handling.
            NeoOrigins.LOGGER.warn("[Compat][FTBQ] quest '{}' grant failed: {}", quest.getCodeString(), e.toString());
        }
    }
}
