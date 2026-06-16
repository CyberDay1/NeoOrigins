package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.service.MinionTracker;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Active power that summons a mob near the player. Summoned mobs are tracked
 * by MinionTracker with caps, despawn timers, and death-damage feedback.
 *
 * <p>Equipment can be configured per slot via JSON. All equipment drop chances
 * are set to 0 — summoned mobs never drop loot.
 */
public class SummonMinionPower extends AbstractActivePower<SummonMinionPower.Config> {

    public record Config(
        String mobType,
        int maxCount,
        int quantity,
        int cooldownTicks,
        int hungerCost,
        int despawnTicks,
        float deathDamage,
        Optional<String> head,
        Optional<String> chest,
        Optional<String> legs,
        Optional<String> feet,
        Optional<String> mainhand,
        Optional<String> offhand,
        Optional<String> mount,
        String type,
        String cooldownIcon,
        boolean cooldownCountdown,
        boolean alwaysShowIcon
    ) implements AbstractActivePower.Config {
        // Manual JSON codec: adding `quantity` (v2.2.3, tester report — pack
        // authors expected the spawn_entity action's quantity field here too)
        // pushed the record past RecordCodecBuilder's 16-field group limit.
        // Decode mirrors the parser-field rule (json.has/get names ARE the
        // schema surface); encode is a no-op like ActiveAbilityPower — sync
        // payloads carry only type id + display.
        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "summon_minion: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "summon_minion: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                if (!obj.has("mob_type")) {
                    return DataResult.error(() -> "summon_minion: missing required field 'mob_type'");
                }
                String mobType = obj.get("mob_type").getAsString();
                int maxCount = intOr(obj, "max_count", 3);
                int quantity = Math.max(1, intOr(obj, "quantity", 1));
                int cooldown = intOr(obj, "cooldown_ticks", 200);
                int hunger = intOr(obj, "hunger_cost", 4);
                int despawn = intOr(obj, "despawn_ticks", 18000);
                float deathDamage = obj.has("death_damage") ? obj.get("death_damage").getAsFloat() : 1.0f;
                String type = stringOr(obj, "type", "");
                String cooldownIcon = stringOr(obj, "cooldown_icon", "");
                boolean cooldownCountdown = !obj.has("cooldown_countdown") || obj.get("cooldown_countdown").getAsBoolean();
                boolean alwaysShowIcon = obj.has("always_show_icon") && obj.get("always_show_icon").getAsBoolean();
                return DataResult.success(Pair.of(new Config(
                    mobType, maxCount, quantity, cooldown, hunger, despawn, deathDamage,
                    optString(obj, "head"), optString(obj, "chest"), optString(obj, "legs"),
                    optString(obj, "feet"), optString(obj, "mainhand"), optString(obj, "offhand"),
                    optString(obj, "mount"),
                    type, cooldownIcon, cooldownCountdown, alwaysShowIcon), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };

        private static int intOr(JsonObject obj, String field, int def) {
            return obj.has(field) ? obj.get(field).getAsInt() : def;
        }

        private static String stringOr(JsonObject obj, String field, String def) {
            return obj.has(field) ? obj.get(field).getAsString() : def;
        }

        private static Optional<String> optString(JsonObject obj, String field) {
            return obj.has(field) && obj.get(field).isJsonPrimitive()
                ? Optional.of(obj.get(field).getAsString())
                : Optional.empty();
        }
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public String getCooldownKey(Config config) {
        return getClass().getName() + ":" + config.mobType();
    }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        // Check cap
        int alive = MinionTracker.countAlive(player.getUUID(), config.mobType());
        if (alive >= config.maxCount()) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.summon_minion.max_reached").withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Check hunger
        if (player.getFoodData().getFoodLevel() < config.hungerCost()) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.summon_minion.not_enough_hunger").withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Resolve entity type
        var entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(config.mobType()));
        if (entityTypeOpt.isEmpty()) return false;
        EntityType<?> entityType = entityTypeOpt.get();

        // Resolve optional mount type (e.g. piglin riding a hoglin). If the id
        // is configured but unresolvable we simply skip the mount rather than
        // failing the whole summon.
        EntityType<?> mountType = null;
        if (config.mount().isPresent()) {
            mountType = BuiltInRegistries.ENTITY_TYPE
                .getOptional(ResourceLocation.parse(config.mount().get())).orElse(null);
        }

        // Spawn the minions near the player. `quantity` (v2.2.3) asks for N per
        // activation but never exceeds the max_count cap — toSpawn is the
        // remaining headroom, so a quantity-3 power with 2 already alive and
        // max_count 4 summons 2. Hunger is charged once per activation.
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 spawnPos = player.position().add(look.x * 2, 0, look.z * 2);
        int toSpawn = Math.min(config.quantity(), config.maxCount() - alive);

        int spawned = 0;
        for (int i = 0; i < toSpawn; i++) {
            // ±0.5-block horizontal jitter so multiple minions don't stack on
            // the exact same point (matches the spawn_entity action behaviour).
            double dx = 0.0, dz = 0.0;
            if (toSpawn > 1) {
                var rng = level.getRandom();
                dx = rng.nextDouble() - 0.5;
                dz = rng.nextDouble() - 0.5;
            }

            // Spawn an optional mount first so the rider has something to sit on.
            LivingEntity mount = null;
            if (mountType != null) {
                mount = spawnMinion(level, mountType, spawnPos, dx, dz, player, config, false);
            }

            LivingEntity rider = spawnMinion(level, entityType, spawnPos, dx, dz, player, config, true);
            if (rider == null) {
                // mob_type was non-living; discard a stray mount and bail.
                if (mount != null) mount.discard();
                break;
            }

            // Seat the rider on its mount (force=true bypasses the normal
            // can-ride checks so cross-type stacks like piglin-on-hoglin work).
            if (mount != null) {
                rider.startRiding(mount, true);
            }
            spawned++;
        }

        if (spawned == 0) return false;

        // Sound + particle effects at spawn location (once per activation)
        level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
            SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.SOUL,
            spawnPos.x, spawnPos.y + 0.5, spawnPos.z,
            20, 0.5, 0.5, 0.5, 0.02);
        level.sendParticles(ParticleTypes.SMOKE,
            spawnPos.x, spawnPos.y + 0.2, spawnPos.z,
            10, 0.3, 0.3, 0.3, 0.01);

        // Consume hunger (once per activation, regardless of count spawned)
        player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - config.hungerCost());

        return true;
    }

    /**
     * Create, position, configure, register and spawn a single minion of the
     * given type. Shared by the rider (the configured {@code mob_type}, with
     * {@code applyEquipment=true}) and an optional {@code mount} entity (no
     * equipment). Returns the spawned {@link LivingEntity}, or {@code null} if
     * the type produced a non-living entity.
     */
    private static LivingEntity spawnMinion(ServerLevel level, EntityType<?> type, Vec3 spawnPos,
                                            double dx, double dz, ServerPlayer player, Config config,
                                            boolean applyEquipment) {
        Entity entity = type.create(level);
        if (!(entity instanceof LivingEntity living)) return null;

        living.setPos(spawnPos.x + dx, spawnPos.y, spawnPos.z + dz);

        if (living instanceof Mob mob) {
            mob.setPersistenceRequired();
            rewriteAiForSummoner(mob, player);
            pacifyBrainMob(mob);

            if (applyEquipment) {
                // Apply configured equipment (or default helmet for sun protection)
                equipSlot(mob, EquipmentSlot.HEAD, config.head(), Items.IRON_HELMET.getDefaultInstance());
                equipSlot(mob, EquipmentSlot.CHEST, config.chest(), null);
                equipSlot(mob, EquipmentSlot.LEGS, config.legs(), null);
                equipSlot(mob, EquipmentSlot.FEET, config.feet(), null);
                equipSlot(mob, EquipmentSlot.MAINHAND, config.mainhand(), null);
                equipSlot(mob, EquipmentSlot.OFFHAND, config.offhand(), null);
            }

            // Zero all drop chances — summoned mobs never drop loot
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.setDropChance(slot, 0.0f);
            }
        }

        level.addFreshEntity(living);

        // Track the minion (mob_type tag is shared so mounts count against the
        // same cap and despawn/clear alongside their riders).
        MinionTracker.track(player, living, config.mobType(),
            player.tickCount, config.despawnTicks(), config.deathDamage());
        return living;
    }

    /**
     * Calm brain-driven neutral mobs (piglins, hoglins) at spawn so a freshly
     * summoned minion doesn't immediately turn on its summoner. These mobs use
     * the Brain/memory system rather than goal selectors, so the goal-based
     * {@link #rewriteAiForSummoner} and the {@code LivingChangeTargetEvent}
     * interceptor never see them. We clear their anger/target memories and, for
     * piglins, suppress both zombification (in the Nether's absence) and the
     * "no gold armour → hostile" check via immunity flags. The per-tick
     * {@code MinionTracker} pacifier keeps them calm thereafter.
     */
    private static void pacifyBrainMob(Mob mob) {
        if (mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) {
            neutral.stopBeingAngry();
        }
        var brain = mob.getBrain();
        brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
        brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
    }

    /**
     * Strip the default player-targeting goals vanilla mobs ship with and
     * install owner-aware replacements:
     *
     * <ul>
     *   <li><b>OwnerAwareHurtByTargetGoal</b> — forgives accidental owner damage,
     *       retaliates otherwise.</li>
     *   <li><b>SummonerCombatTargetGoal</b> — picks up whoever the summoner is
     *       fighting (either the summoner's {@code lastHurtMob} or
     *       {@code lastHurtByMob}) by direct reference. Range-independent — the
     *       minion acquires the target regardless of how far away it is, then
     *       walks/paths using its own follow-range. Replaces a prior
     *       {@link NearestAttackableTargetGoal} that only scanned 5-16 blocks,
     *       making minions useless except at point-blank range.</li>
     * </ul>
     */
    private static void rewriteAiForSummoner(Mob mob, ServerPlayer summoner) {
        mob.targetSelector.getAvailableGoals().clear();

        if (mob instanceof PathfinderMob pathfinder) {
            mob.targetSelector.addGoal(1, new TameMobPower.OwnerAwareHurtByTargetGoal(pathfinder, summoner));
        }

        mob.targetSelector.addGoal(2, new SummonerCombatTargetGoal(mob, summoner));

        mob.goalSelector.getAvailableGoals().removeIf(g -> g.getGoal() instanceof AvoidEntityGoal);
    }

    /**
     * Target goal that mirrors the summoner's current combat:
     * <ul>
     *   <li>{@code summoner.getLastHurtMob()} — whatever the player just hit</li>
     *   <li>{@code summoner.getLastHurtByMob()} — whatever just hit the player</li>
     * </ul>
     * Both are queried by direct reference, so there's no distance check —
     * the minion picks up the fight even if the target is 50 blocks away.
     * A recency window of 200 ticks (10s) stops old fights from re-triggering.
     */
    public static class SummonerCombatTargetGoal extends TargetGoal {
        private static final int RECENCY_TICKS = 200;

        private final Mob minion;
        private final ServerPlayer summoner;
        private LivingEntity pendingTarget;

        public SummonerCombatTargetGoal(Mob minion, ServerPlayer summoner) {
            super(minion, false, false);
            this.minion = minion;
            this.summoner = summoner;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity candidate = resolve();
            if (candidate == null) return false;
            this.pendingTarget = candidate;
            return true;
        }

        @Override
        public void start() {
            this.minion.setTarget(this.pendingTarget);
            super.start();
        }

        /** Pick whichever of hurt-to / hurt-by is the most recent valid live target. */
        private LivingEntity resolve() {
            int now = summoner.tickCount;

            LivingEntity lastHit = summoner.getLastHurtMob();
            boolean lastHitFresh = lastHit != null && lastHit.isAlive()
                && lastHit != summoner
                && now - summoner.getLastHurtMobTimestamp() < RECENCY_TICKS;

            LivingEntity lastHurtBy = summoner.getLastHurtByMob();
            boolean lastHurtByFresh = lastHurtBy != null && lastHurtBy.isAlive()
                && lastHurtBy != summoner
                && now - summoner.getLastHurtByMobTimestamp() < RECENCY_TICKS;

            // Prefer the more recent one so a fresh hit flips the minion to the new threat.
            if (lastHitFresh && lastHurtByFresh) {
                return (summoner.getLastHurtMobTimestamp() >= summoner.getLastHurtByMobTimestamp())
                    ? lastHit : lastHurtBy;
            }
            if (lastHitFresh) return lastHit;
            if (lastHurtByFresh) return lastHurtBy;
            return null;
        }
    }

    private static void equipSlot(Mob mob, EquipmentSlot slot, Optional<String> configItem, ItemStack fallback) {
        if (configItem.isPresent()) {
            var itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(configItem.get()));
            if (itemOpt.isPresent()) {
                mob.setItemSlot(slot, new ItemStack(itemOpt.get()));
            }
        } else if (fallback != null && mob.getItemBySlot(slot).isEmpty()) {
            mob.setItemSlot(slot, fallback.copy());
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        MinionTracker.clearAll(player.getUUID());
    }
}
