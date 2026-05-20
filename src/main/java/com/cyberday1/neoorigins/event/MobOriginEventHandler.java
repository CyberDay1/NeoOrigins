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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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

        // Spawn-egg marker tag wins over the SpawnRules roll. Vanilla spawn eggs
        // and mob spawners both propagate ENTITY_DATA NBT (including the Tags
        // list) onto the spawned entity before finalizeSpawn fires, so the egg
        // can pin which origin attaches without going through the weighted roll.
        Identifier eggOrigin = MobOriginSpawnEggService.findMarkerTag(mob);
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
     * In-hand egg use path. The marker-tag mechanism in
     * {@link MobOriginSpawnEggService} works for vanilla mob spawners (they
     * apply ENTITY_DATA at construction time, before {@code finalizeSpawn}),
     * but NOT for vanilla's in-hand spawn-egg flow on either branch. Two
     * obstacles compounded the symptom:
     *
     * <ol>
     *   <li>Vanilla {@code EntityType.updateCustomEntityTag} (the NBT→entity
     *       copy) is gated by {@code Player.canUseGameMasterBlocks()} =
     *       creative AND permission level ≥ 2. Survival drops the marker
     *       silently.</li>
     *   <li>Even when the gate passes (creative + op), the NBT-apply happens
     *       in a consumer that runs <em>after</em> {@code mob.finalizeSpawn}
     *       inside {@code EntityType.create(level, consumer, …)}. By the time
     *       the marker lands on the entity, our {@code FinalizeSpawnEvent}
     *       handler has already inspected an unmarked mob.</li>
     * </ol>
     *
     * <p>Approach: catch the right-click on a marked spawn egg, spawn the
     * entity ourselves (without the stack so vanilla's gate-and-late-apply
     * is fully bypassed), then attach the origin directly on the returned
     * Mob. If the FinalizeSpawn SpawnRules roll happened to assign a
     * different origin during {@code spawn()}, we override it cleanly via
     * {@code applyMobOriginPowers(mob, prev, new)}.
     */
    @SubscribeEvent
    public static void onSpawnEggRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SpawnEggItem)) return;

        // 26.1: DataComponents.ENTITY_DATA is TypedEntityData<EntityType<?>>
        // (it carries the entity type alongside the NBT). 1.21.1 stored
        // plain CustomData here.
        TypedEntityData<EntityType<?>> entityData = stack.get(DataComponents.ENTITY_DATA);
        if (entityData == null) return;
        var nbt = entityData.copyTagWithoutId();
        Identifier eggOrigin = MobOriginSpawnEggService.markerFromNbt(nbt);
        if (eggOrigin == null) return; // not one of ours; let vanilla handle it
        if (!MobOriginDataManager.INSTANCE.hasMobOrigin(eggOrigin)) return;

        EntityType<?> entityType = entityData.type();

        BlockPos spawnPos = event.getPos().relative(event.getFace());
        // Pass null stack → vanilla skips both updateCustomEntityTag and
        // custom-name application; we own NBT injection from here on.
        // 26.1 renamed SPAWN_EGG → SPAWN_ITEM_USE (broader: also covers
        // buckets / boats / armor stands / etc spawn-from-item flows).
        net.minecraft.world.entity.Entity entity = entityType.spawn(
            sl, null, sp, spawnPos, EntitySpawnReason.SPAWN_ITEM_USE, true, false);

        if (entity instanceof Mob mob) {
            MobOriginData data = mob.getData(EntityAttachments.mobOriginData());
            Identifier prev = data.getOriginId().orElse(null);
            data.setOriginId(eggOrigin);
            MobOriginService.applyMobOriginPowers(mob, prev, eggOrigin);
            NeoOriginsNetwork.syncMobOriginToTrackers(mob, Optional.of(eggOrigin));
            NeoOrigins.LOGGER.debug("[mob-origin] {} → {} (from in-hand spawn egg)",
                mob.getType(), eggOrigin);
        }

        if (!sp.getAbilities().instabuild) stack.shrink(1);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }
}
