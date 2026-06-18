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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Active power that summons a mob near the player. Summoned mobs are tracked
 * by MinionTracker with caps, despawn timers, and death-damage feedback.
 *
 * <p>Equipment can be configured per slot via JSON. All equipment drop chances
 * are set to 0 — summoned mobs never drop loot.
 */
public class SummonMinionPower extends AbstractActivePower<SummonMinionPower.Config> {

    /** A single enchantment to roll onto a summoned mob's equipment piece. */
    public record EnchantEntry(String id, int level) {}

    /** Equipment item for a slot: an item id plus optional enchantments. The
     *  string form {@code "minecraft:iron_helmet"} decodes to an EquipItem with
     *  an empty enchantment list; the object form
     *  {@code {"item": "...", "enchantments": [...]}} carries enchantments. */
    public record EquipItem(String itemId, List<EnchantEntry> enchantments) {}

    /** An attribute modifier applied to the summoned mob at spawn. */
    public record AttrEntry(String attribute, double amount, AttributeModifier.Operation operation) {}

    public record Config(
        String mobType,
        int maxCount,
        int quantity,
        int cooldownTicks,
        int hungerCost,
        int despawnTicks,
        float deathDamage,
        Optional<EquipItem> head,
        Optional<EquipItem> chest,
        Optional<EquipItem> legs,
        Optional<EquipItem> feet,
        Optional<EquipItem> mainhand,
        Optional<EquipItem> offhand,
        Optional<String> mount,
        List<AttrEntry> attributes,
        String type,
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
                    optEquip(obj, "head"), optEquip(obj, "chest"), optEquip(obj, "legs"),
                    optEquip(obj, "feet"), optEquip(obj, "mainhand"), optEquip(obj, "offhand"),
                    optString(obj, "mount"), parseAttributes(obj),
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

        /** Equipment slot field. Accepts the bare-string form
         *  ({@code "minecraft:iron_helmet"}) or the object form
         *  ({@code {"item": "...", "enchantments": [{"id": "...", "level": N}]}}). */
        private static Optional<EquipItem> optEquip(JsonObject obj, String field) {
            if (!obj.has(field)) return Optional.empty();
            JsonElement el = obj.get(field);
            if (el.isJsonPrimitive()) {
                return Optional.of(new EquipItem(el.getAsString(), List.of()));
            }
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("item")) return Optional.empty();
                String itemId = o.get("item").getAsString();
                List<EnchantEntry> enchants = new ArrayList<>();
                if (o.has("enchantments") && o.get("enchantments").isJsonArray()) {
                    for (JsonElement ee : o.getAsJsonArray("enchantments")) {
                        if (!ee.isJsonObject()) continue;
                        JsonObject eo = ee.getAsJsonObject();
                        if (!eo.has("id")) continue;
                        int lvl = eo.has("level") ? eo.get("level").getAsInt() : 1;
                        enchants.add(new EnchantEntry(eo.get("id").getAsString(), lvl));
                    }
                }
                return Optional.of(new EquipItem(itemId, List.copyOf(enchants)));
            }
            return Optional.empty();
        }

        /** Parse the optional {@code attributes} array of modifiers applied to
         *  the summoned mob. Each entry needs {@code attribute} + {@code amount};
         *  {@code operation} defaults to {@code add_value}. */
        private static List<AttrEntry> parseAttributes(JsonObject obj) {
            if (!obj.has("attributes") || !obj.get("attributes").isJsonArray()) return List.of();
            List<AttrEntry> out = new ArrayList<>();
            for (JsonElement ae : obj.getAsJsonArray("attributes")) {
                if (!ae.isJsonObject()) continue;
                JsonObject ao = ae.getAsJsonObject();
                if (!ao.has("attribute") || !ao.has("amount")) continue;
                AttributeModifier.Operation op = ao.has("operation")
                    ? parseOp(ao.get("operation").getAsString())
                    : AttributeModifier.Operation.ADD_VALUE;
                out.add(new AttrEntry(ao.get("attribute").getAsString(),
                    ao.get("amount").getAsDouble(), op));
            }
            return List.copyOf(out);
        }

        private static AttributeModifier.Operation parseOp(String s) {
            return switch (s) {
                case "add_value" -> AttributeModifier.Operation.ADD_VALUE;
                case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> AttributeModifier.Operation.ADD_VALUE;
            };
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
        var entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(config.mobType()));
        if (entityTypeOpt.isEmpty()) return false;
        EntityType<?> entityType = entityTypeOpt.get().value();

        // Resolve optional mount type (e.g. piglin riding a hoglin). If the id
        // is configured but unresolvable we simply skip the mount rather than
        // failing the whole summon.
        EntityType<?> mountType = null;
        if (config.mount().isPresent()) {
            mountType = BuiltInRegistries.ENTITY_TYPE
                .get(Identifier.parse(config.mount().get())).map(net.minecraft.core.Holder::value).orElse(null);
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
                rider.startRiding(mount, true, true);
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
        Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof LivingEntity living)) return null;

        living.setPos(spawnPos.x + dx, spawnPos.y, spawnPos.z + dz);

        if (living instanceof Mob mob) {
            mob.setPersistenceRequired();
            rewriteAiForSummoner(mob, player);
            pacifyBrainMob(mob);

            if (applyEquipment) {
                // Apply configured equipment (or default helmet for sun protection)
                equipSlot(level, mob, EquipmentSlot.HEAD, config.head(), Items.IRON_HELMET.getDefaultInstance());
                equipSlot(level, mob, EquipmentSlot.CHEST, config.chest(), null);
                equipSlot(level, mob, EquipmentSlot.LEGS, config.legs(), null);
                equipSlot(level, mob, EquipmentSlot.FEET, config.feet(), null);
                equipSlot(level, mob, EquipmentSlot.MAINHAND, config.mainhand(), null);
                equipSlot(level, mob, EquipmentSlot.OFFHAND, config.offhand(), null);

                // Apply configured attribute modifiers (max health, damage, ...)
                // then top the mob up so a raised max_health spawns at full HP.
                if (!config.attributes().isEmpty()) {
                    applyAttributes(mob, config.attributes());
                    mob.setHealth(mob.getMaxHealth());
                }
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
     * install owner-aware replacements: the minion fights back against
     * anything that hits it (except the summoner) and defends the summoner
     * against their current attacker. Mirrors TameMobPower's rewrite.
     */
    private static void rewriteAiForSummoner(Mob mob, ServerPlayer summoner) {
        mob.targetSelector.getAvailableGoals().clear();

        if (mob instanceof PathfinderMob pathfinder) {
            mob.targetSelector.addGoal(1, new TameMobPower.OwnerAwareHurtByTargetGoal(pathfinder, summoner));
        }

        mob.targetSelector.addGoal(2, new SummonerCombatTargetGoal(mob, summoner));

        stripDistractionGoals(mob);
    }

    /**
     * Remove goals that pull a combat pet away from its owner's fight: vanilla
     * player-avoidance ({@link AvoidEntityGoal}) and — for summoned bees — the
     * pollinate / fly-to-flower wander goals. The bee goals are private inner
     * classes of {@code Bee}, so they're matched by simple class name rather
     * than type. {@code BeeAttackGoal} is intentionally left in place so the
     * bee still fights.
     */
    static void stripDistractionGoals(Mob mob) {
        mob.goalSelector.getAvailableGoals().removeIf(g -> {
            if (g.getGoal() instanceof AvoidEntityGoal) return true;
            String name = g.getGoal().getClass().getSimpleName();
            return name.equals("BeePollinateGoal") || name.equals("BeeGoToKnownFlowerGoal");
        });
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

    private static void equipSlot(ServerLevel level, Mob mob, EquipmentSlot slot,
                                  Optional<EquipItem> configItem, ItemStack fallback) {
        if (configItem.isPresent()) {
            EquipItem equip = configItem.get();
            var itemOpt = BuiltInRegistries.ITEM.get(Identifier.parse(equip.itemId()));
            if (itemOpt.isPresent()) {
                ItemStack stack = new ItemStack(itemOpt.get().value());
                if (!equip.enchantments().isEmpty()) {
                    var enchLookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    for (EnchantEntry ench : equip.enchantments()) {
                        ResourceKey<Enchantment> key = ResourceKey.create(
                            Registries.ENCHANTMENT, Identifier.parse(ench.id()));
                        enchLookup.get(key).ifPresent(h -> mutable.set(h, ench.level()));
                    }
                    stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
                }
                mob.setItemSlot(slot, stack);
            }
        } else if (fallback != null && mob.getItemBySlot(slot).isEmpty()) {
            mob.setItemSlot(slot, fallback.copy());
        }
    }

    /** Apply the configured attribute modifiers to a freshly summoned mob.
     *  Attribute ids resolve with the same generic./player. prefix tolerance the
     *  {@code attribute_modifier} power uses, so pack JSON is portable across
     *  the 1.21.1 and 26.1 builds. */
    private static void applyAttributes(Mob mob, List<AttrEntry> attributes) {
        int idx = 0;
        for (AttrEntry attr : attributes) {
            Holder<Attribute> holder = resolveAttribute(Identifier.parse(attr.attribute()));
            if (holder == null) continue;
            AttributeInstance instance = mob.getAttribute(holder);
            if (instance == null) continue;
            Identifier modId = Identifier.fromNamespaceAndPath(
                "neoorigins", "summon_minion_attr_" + (idx++));
            instance.addPermanentModifier(new AttributeModifier(modId, attr.amount(), attr.operation()));
        }
    }

    /** Resolve an attribute with generic./player. prefix tolerance (see
     *  AttributeModifierPower#resolveAttribute). Returns {@code null} if the id
     *  matches no registered attribute under any prefix combination. */
    private static Holder<Attribute> resolveAttribute(Identifier raw) {
        var holder = BuiltInRegistries.ATTRIBUTE.get(raw);
        if (holder.isPresent()) return holder.get();

        Identifier withGeneric = Identifier.fromNamespaceAndPath(
            raw.getNamespace(), "generic." + raw.getPath());
        holder = BuiltInRegistries.ATTRIBUTE.get(withGeneric);
        if (holder.isPresent()) return holder.get();

        Identifier withPlayer = Identifier.fromNamespaceAndPath(
            raw.getNamespace(), "player." + raw.getPath());
        holder = BuiltInRegistries.ATTRIBUTE.get(withPlayer);
        if (holder.isPresent()) return holder.get();

        String path = raw.getPath();
        if (path.startsWith("generic.") || path.startsWith("player.")) {
            Identifier stripped = Identifier.fromNamespaceAndPath(
                raw.getNamespace(), path.substring(path.indexOf('.') + 1));
            holder = BuiltInRegistries.ATTRIBUTE.get(stripped);
            if (holder.isPresent()) return holder.get();
        }
        return null;
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        MinionTracker.clearAll(player.getUUID());
    }
}
