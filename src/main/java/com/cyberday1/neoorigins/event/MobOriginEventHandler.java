package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.mob_origin.MobOrigin;
import com.cyberday1.neoorigins.api.mob_origin.SpawnRules;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.attachment.MobOriginData;
import com.cyberday1.neoorigins.data.MobOriginDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.MobOriginService;
import com.cyberday1.neoorigins.service.MobOriginSpawnEggService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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

        // Spawn-egg marker tag wins over the SpawnRules roll. Vanilla spawn eggs
        // and mob spawners both propagate ENTITY_DATA NBT (including the Tags
        // list) onto the spawned entity before finalizeSpawn fires, so the egg
        // can pin which origin attaches without going through the weighted roll.
        ResourceLocation eggOrigin = MobOriginSpawnEggService.findMarkerTag(mob);
        if (eggOrigin != null) {
            MobOriginSpawnEggService.stripMarkerTag(mob);
            if (MobOriginDataManager.INSTANCE.hasMobOrigin(eggOrigin)) {
                data.setOriginId(eggOrigin);
                MobOriginService.applyMobOriginPowers(mob, null, eggOrigin);
                NeoOriginsNetwork.syncMobOriginToTrackers(mob, Optional.of(eggOrigin));
                NeoOrigins.LOGGER.debug("[mob-origin] {} → {} (from spawn egg)",
                    mob.getType(), eggOrigin);
            } else {
                NeoOrigins.LOGGER.warn("[mob-origin] spawn-egg marker references unknown origin {}",
                    eggOrigin);
            }
            return;
        }

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

    /**
     * In-hand egg use path. Vanilla's {@code EntityType.updateCustomEntityTag}
     * (the spawn-egg NBT-to-entity copy) is gated by
     * {@code Player.canUseGameMasterBlocks()}, which requires creative AND
     * permission level ≥ 2. In survival the gate drops the marker tag
     * silently → the FinalizeSpawn handler sees no marker → no origin attaches.
     * Spawners are fine because they apply the NBT through a different path,
     * but we have to route around the gate for the in-hand case.
     *
     * <p>Approach: catch the right-click on a marked spawn egg, read the
     * marker off the stack directly, then call the consumer-taking overload
     * of {@code EntityType.spawn} so we can add the marker tag to the entity
     * BEFORE {@code finalizeSpawn} fires. That re-enters the same code path
     * the spawner uses, so the existing origin-attachment logic stays the
     * single source of truth.
     */
    @SubscribeEvent
    public static void onSpawnEggRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SpawnEggItem)) return;

        CustomData entityData = stack.get(DataComponents.ENTITY_DATA);
        if (entityData == null) return;
        var nbt = entityData.copyTag();
        ResourceLocation eggOrigin = MobOriginSpawnEggService.markerFromNbt(nbt);
        if (eggOrigin == null) return; // not one of ours; let vanilla handle it
        if (!MobOriginDataManager.INSTANCE.hasMobOrigin(eggOrigin)) return;

        String typeIdStr = nbt.getString("id");
        ResourceLocation typeId = ResourceLocation.tryParse(typeIdStr);
        if (typeId == null) return;
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (entityType == null) return;

        BlockPos spawnPos = event.getPos().relative(event.getFace());
        String markerTag = MobOriginSpawnEggService.MARKER_PREFIX + eggOrigin.toString();
        // Consumer-overload runs BEFORE finalizeSpawn → marker tag is on the
        // mob when our FinalizeSpawn handler reads it.
        entityType.spawn(sl,
            mob -> mob.addTag(markerTag),
            spawnPos, MobSpawnType.SPAWN_EGG, true, false);

        if (!sp.getAbilities().instabuild) stack.shrink(1);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }
}
