package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.mob_origin.MobOrigin;
import com.cyberday1.neoorigins.api.mob_origin.SpawnRules;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.attachment.MobOriginData;
import com.cyberday1.neoorigins.data.MobOriginDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.MobOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Spawn-time mob-origin assignment (Phase 2).
 *
 * <p>Hooks {@link FinalizeSpawnEvent} — NOT {@code EntityJoinLevelEvent} as
 * the original plan text said: only {@code FinalizeSpawnEvent} carries the
 * {@code MobSpawnType} the {@link SpawnRules} {@code spawn_reasons} filter
 * needs, and it fires for fresh spawns only (disk-loaded entities keep their
 * persisted {@link MobOriginData}). Mirrors {@code WorldPowerEvents
 * .onFinalizeSpawn}.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class MobOriginEventHandler {

    private MobOriginEventHandler() {}

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Mob mob = event.getEntity();

        MobOriginData data = mob.getData(EntityAttachments.mobOriginData());
        if (data.hasOrigin()) return; // already assigned (or restored from disk)

        List<MobOrigin> candidates = MobOriginDataManager.INSTANCE.candidatesFor(mob.getType());
        if (candidates.isEmpty()) return;
        // Deterministic order so the first matching origin is stable.
        candidates.sort(Comparator.comparing(mo -> mo.id().toString()));

        var reason = event.getSpawnType();
        BlockPos pos = mob.blockPosition();
        RandomSource rng = sl.getRandom();

        for (MobOrigin mo : candidates) {
            SpawnRules r = mo.spawnRules();
            if (r.weight() <= 0.0) continue;                       // opt-in only
            if (!r.allowsReason(reason)) continue;
            if (r.yRange().isPresent() && !r.yRange().get().contains(pos.getY())) continue;
            if (r.lightRange().isPresent()
                && !r.lightRange().get().contains(sl.getBrightness(LightLayer.BLOCK, pos))) continue;
            if (!r.timeOfDay().matches(sl)) continue;
            if (r.location().isPresent() && !r.location().get().test(sl, pos)) continue;

            String mutex = r.mutexGroup().orElse(null);
            if (mutex != null && data.hasMutexGroup(mutex)) continue;

            // Weight roll (>= 1.0 always applies).
            if (r.weight() < 1.0 && rng.nextDouble() >= r.weight()) continue;

            data.setOriginId(mo.id());
            if (mutex != null) data.markMutexGroup(mutex);
            MobOriginService.applyMobOriginPowers(mob, null, mo.id());
            NeoOriginsNetwork.syncMobOriginToTrackers(mob, Optional.of(mo.id()));
            NeoOrigins.LOGGER.debug("[mob-origin] {} → {} (reason {})",
                mob.getType(), mo.id(), reason);
            break; // one origin per mob
        }
    }
}
