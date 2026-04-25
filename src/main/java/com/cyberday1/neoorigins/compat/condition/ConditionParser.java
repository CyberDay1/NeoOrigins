package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.FluidPredicate;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class ConditionParser {

    private ConditionParser() {}

    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

    public static EntityCondition parse(JsonObject json, String contextId) {
        if (json == null) {
            return failClosed("root", contextId, "missing condition object");
        }
        String type = json.has("type") ? json.get("type").getAsString() : "";
        // Canonicalize: bare names default to neoorigins:; legacy origins:/apace:
        // prefixes get a one-shot [2.0-legacy] warning then are rewritten to
        // neoorigins: for dispatch. The canonical switch arms below only need
        // to list neoorigins:* forms.
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "neoorigins:" + type;
        } else if (type.startsWith("origins:") || type.startsWith("apace:")) {
            String canonical = "neoorigins:" + type.substring(type.indexOf(':') + 1);
            com.cyberday1.neoorigins.compat.LegacyVerbWarning.warn(type, canonical);
            type = canonical;
        }
        try {
            return switch (type) {
                case "neoorigins:and"                           -> parseAnd(json, contextId);
                case "neoorigins:or"                            -> parseOr(json, contextId);
                case "neoorigins:not"                           -> parseNot(json, contextId);
                case "neoorigins:constant"                      ->
                    json.has("value") && json.get("value").getAsBoolean()
                        ? EntityCondition.alwaysTrue() : EntityCondition.alwaysFalse();
                case "neoorigins:sneaking"                      -> p -> p.isShiftKeyDown();
                case "neoorigins:sprinting"                     -> p -> p.isSprinting();
                case "neoorigins:on_ground"                     -> p -> p.onGround();
                case "neoorigins:in_water"                      -> p -> p.isInWater();
                case "neoorigins:swimming"                      -> p -> p.isSwimming();
                case "neoorigins:submerged_in_water"            -> p -> p.isUnderWater();
                case "neoorigins:fall_flying"                   -> p -> p.isFallFlying();
                case "neoorigins:invisible"                     -> p -> p.isInvisible();
                case "neoorigins:moving"                        -> p -> {
                    var dm = p.getDeltaMovement();
                    return dm.x != 0 || dm.z != 0;
                };
                case "neoorigins:in_rain"                       -> p -> {
                    if (!(p.level() instanceof ServerLevel sl)) return false;
                    return sl.isRainingAt(p.blockPosition());
                };
                case "neoorigins:daytime"                       ->
                    p -> p.level().getDefaultClockTime() % 24000L < 13000L;
                case "neoorigins:exposed_to_sky"                -> p -> {
                    if (!(p.level() instanceof ServerLevel sl)) return false;
                    return sl.canSeeSky(p.blockPosition());
                };
                case "neoorigins:exposed_to_sun"                -> p -> {
                    if (!(p.level() instanceof ServerLevel sl)) return false;
                    // Vanilla daytime is 0–12000 (sunrise to sunset). The prior
                    // impl gated on 6000–12000, which skipped morning hours and
                    // silently made sun-damage origins (Abyssal Surface Burn,
                    // Enderian, Cinderborn daylight variants) fail to damage
                    // until around noon.
                    long time = sl.getDefaultClockTime() % 24000L;
                    if (time >= 12000L
                        || !sl.canSeeSky(p.blockPosition())
                        || sl.isRaining()) return false;
                    // Helmet protection — mirrors vanilla zombie/skeleton sun-burn
                    // logic. A damageable helmet absorbs the burn at the cost of
                    // its own durability; once the helmet breaks the player burns
                    // again. 7% per-evaluation gate (≈30% slower than the 2.0.1
                    // baseline of 10%); a typical iron helmet now lasts ~40 min
                    // of continuous sun, more with Unbreaking — hurtAndBreak's
                    // internal Unbreaking roll stacks on top of our gate.
                    // Side-effect inside a predicate is unusual but the
                    // alternative — a parallel ticking handler that re-derives
                    // exposed-to-sun state — duplicates this whole check.
                    ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
                    if (!head.isEmpty() && head.isDamageableItem()) {
                        if (p.getRandom().nextFloat() < 0.07f) {
                            head.hurtAndBreak(1, p, EquipmentSlot.HEAD);
                        }
                        return false;
                    }
                    return true;
                };
                case "neoorigins:health"                        -> parseHealth(json);
                case "neoorigins:resource"                      -> parseResource(json, contextId);
                case "neoorigins:power_active"                  -> parsePowerActive(json, contextId);
                case "neoorigins:on_block"                      -> parseOnBlock(json, contextId);

                // ---- Phase 1: New conditions ----
                case "neoorigins:dimension"                     -> parseDimension(json);
                case "neoorigins:biome"                         -> parseBiome(json);
                case "neoorigins:in_tag"                        -> parseInTag(json);
                case "neoorigins:food_level", "neoorigins:food" -> parseFoodLevel(json);
                case "neoorigins:submerged_in"                  -> parseSubmergedIn(json);
                case "neoorigins:on_fire", "neoorigins:fire"    -> p -> p.isOnFire();
                case "neoorigins:equipped_item"                 -> parseEquippedItem(json, contextId);
                case "neoorigins:relative_health"               -> parseRelativeHealth(json);
                case "neoorigins:fall_distance"                 -> parseFallDistance(json);
                case "neoorigins:enchantment"                   -> parseEnchantment(json);
                case "neoorigins:block"                         -> parseBlockCondition(json, contextId);
                case "neoorigins:light_level"                   -> parseLightLevel(json);
                case "neoorigins:nbt"                           -> parseNbt(json);
                case "neoorigins:scoreboard"                    -> parseScoreboard(json);
                case "neoorigins:command"                       -> parseCommand(json);
                case "neoorigins:passenger",
                     "neoorigins:riding"                        -> p -> p.isPassenger();
                case "neoorigins:entity_type"                   -> parseEntityType(json);
                case "neoorigins:fluid_height"                  -> parseFluidHeight(json);
                case "neoorigins:in_block",
                     "neoorigins:in_block_anywhere"             -> parseInBlock(json, contextId);
                case "neoorigins:brightness"                    -> parseLightLevel(json);
                case "neoorigins:height"                        -> parseHeight(json);
                case "neoorigins:block_collision"               -> EntityCondition.alwaysTrue();
                case "neoorigins:temperature"                   -> parseTemperature(json);
                case "neoorigins:armor_value"                   -> parseArmorValue(json);
                case "neoorigins:amount"                        -> parseAmount(json);
                case "neoorigins:using_item"                    -> p -> p.isUsingItem();
                case "neoorigins:ticking"                       -> p -> !p.isRemoved();
                case "neoorigins:exists"                        -> p -> p != null && !p.isRemoved();
                case "neoorigins:living"                        -> p -> p.isAlive();
                case "neoorigins:creative_flying"               -> p -> p.getAbilities().flying;
                case "neoorigins:power_type"                    -> parsePowerType(json, contextId);
                case "neoorigins:predicate"                     -> parsePredicate(json, contextId);

                // ---- Phase 0 consolidation: new verbs ----
                case "neoorigins:time_of_day"                   -> parseTimeOfDay(json);
                case "neoorigins:weather"                       -> parseWeather(json);
                case "neoorigins:xp_level"                      -> parseXpLevel(json);
                case "neoorigins:xp_points"                     -> parseXpPoints(json);
                case "neoorigins:moon_phase"                    -> parseMoonPhase(json);

                // ---- Phase 6.5: context-aware conditions (read from ActionContextHolder) ----
                case "neoorigins:hit_taken_amount"              -> parseHitTakenAmount(json);
                case "neoorigins:food_item_in_tag"              -> parseFoodItemInTag(json);
                case "neoorigins:food_item_id"                  -> parseFoodItemId(json);
                case "neoorigins:no_minions_alive"              -> {
                    // True when the player has no tracked minions of the
                    // given {@code key} (e.g. "tamer:tamed"). Used by Monster
                    // Tamer's Lone Weakness to only penalise the player when
                    // they're fighting without their pack.
                    final String minionKey = json.has("key") ? json.get("key").getAsString() : "tamer:tamed";
                    yield p -> com.cyberday1.neoorigins.service.MinionTracker.countAlive(p.getUUID(), minionKey) == 0;
                }

                // ---- Bientity conditions (read target from current dispatch context) ----
                case "neoorigins:distance"                      -> parseDistance(json);
                case "neoorigins:can_see"                       -> parseCanSee();
                case "neoorigins:equal"                         -> parseEqual();
                case "neoorigins:target_type"                   -> parseTargetType(json);
                case "neoorigins:target_group"                  -> parseTargetGroup(json);
                case "neoorigins:in_set"                        -> parseInSet(json, contextId);

                // ---- Damage conditions (read DamageSource from HitTakenContext) ----
                case "neoorigins:from_fire"                     -> parseFromFire();
                case "neoorigins:from_projectile"               -> parseFromProjectile();
                case "neoorigins:from_explosion"                -> parseFromExplosion();
                case "neoorigins:damage_type"                   -> parseDamageType(json);
                case "neoorigins:damage_tag"                    -> parseDamageTag(json);
                case "neoorigins:damage_name",
                     "neoorigins:name"                          -> parseDamageName(json);

                // ---- Phase 8: condition expansion (2026-04-24) ----
                case "neoorigins:night"                         ->
                    p -> p.level().getDefaultClockTime() % 24000L >= 13000L;
                case "neoorigins:thundering"                    -> p -> {
                    if (!(p.level() instanceof ServerLevel sl)) return false;
                    return sl.isThundering() && sl.isRainingAt(p.blockPosition());
                };
                case "neoorigins:has_effect"                    -> parseHasEffect(json);
                case "neoorigins:climbing"                      -> p -> p.onClimbable();
                case "neoorigins:near_block"                    -> parseNearBlock(json, contextId);
                case "neoorigins:out_of_combat"                 -> parseOutOfCombat(json);

                default -> failClosed(type, contextId, "unsupported condition type");
            };
        } catch (Exception e) {
            return failClosed(type, contextId, "parse error: " + e.getMessage());
        }
    }

    private static EntityCondition parseAnd(JsonObject json, String ctx) {
        JsonArray arr = json.has("conditions") ? json.getAsJsonArray("conditions") : new JsonArray();
        List<EntityCondition> list = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) list.add(parse(el.getAsJsonObject(), ctx));
        }
        return player -> {
            for (EntityCondition c : list) if (!c.test(player)) return false;
            return true;
        };
    }

    private static EntityCondition parseOr(JsonObject json, String ctx) {
        JsonArray arr = json.has("conditions") ? json.getAsJsonArray("conditions") : new JsonArray();
        List<EntityCondition> list = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) list.add(parse(el.getAsJsonObject(), ctx));
        }
        return player -> {
            for (EntityCondition c : list) if (c.test(player)) return true;
            return false;
        };
    }

    private static EntityCondition parseNot(JsonObject json, String ctx) {
        if (!json.has("condition") || !json.get("condition").isJsonObject()) {
            return failClosed("origins:not", ctx, "missing required field 'condition'");
        }
        EntityCondition inner = parse(json.getAsJsonObject("condition"), ctx);
        return player -> !inner.test(player);
    }

    private static EntityCondition parseHealth(JsonObject json) {
        String comp    = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target  = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getHealth(), target);
    }

    private static EntityCondition parseResource(JsonObject json, String contextId) {
        String powerId = json.has("resource") ? json.get("resource").getAsString() : null;
        if (powerId == null || powerId.isBlank()) {
            return failClosed("origins:resource", contextId, "missing required field 'resource'");
        }
        String comp   = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target    = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            int cur = player.getData(CompatAttachments.resourceState()).get(powerId, 0);
            return comparison.test(cur, target);
        };
    }

    private static EntityCondition parsePowerActive(JsonObject json, String contextId) {
        String powerId = json.has("power") ? json.get("power").getAsString() : null;
        if (powerId == null || powerId.isBlank()) {
            return failClosed("origins:power_active", contextId, "missing required field 'power'");
        }
        return player -> player.getData(CompatAttachments.toggleState()).isActive(powerId, false);
    }

    private static EntityCondition parseOnBlock(JsonObject json, String contextId) {
        if (!json.has("block_condition") || !json.get("block_condition").isJsonObject()) {
            return failClosed("origins:on_block", contextId, "missing required field 'block_condition'");
        }
        JsonObject blockCond = json.getAsJsonObject("block_condition");
        String blockId = blockCond.has("id") ? blockCond.get("id").getAsString() : null;
        if (blockId == null || blockId.isBlank()) {
            return failClosed("origins:on_block", contextId, "missing required field 'block_condition.id'");
        }
        Identifier bid = Identifier.parse(blockId);
        return player -> {
            if (!player.onGround()) return false;
            BlockPos below = player.blockPosition().below();
            Block block = player.level().getBlockState(below).getBlock();
            return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
        };
    }

    // ---- Phase 1: New condition parsers ----

    private static EntityCondition parseDimension(JsonObject json) {
        String dimension = json.has("dimension") ? json.get("dimension").getAsString() : null;
        if (dimension == null) return EntityCondition.alwaysTrue();
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
        return player -> player.level().dimension().equals(dimKey);
    }

    private static EntityCondition parseBiome(JsonObject json) {
        // Can check either "biome" (exact id) or "condition" (sub-condition on biome)
        String biomeId = json.has("biome") ? json.get("biome").getAsString() : null;
        if (biomeId != null) {
            ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, Identifier.parse(biomeId));
            return player -> {
                var biomeHolder = player.level().getBiome(player.blockPosition());
                return biomeHolder.is(biomeKey);
            };
        }
        // Check for tag-based biome condition
        String tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (tag != null) {
            TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, Identifier.parse(tag));
            return player -> player.level().getBiome(player.blockPosition()).is(biomeTag);
        }
        return EntityCondition.alwaysTrue();
    }

    private static EntityCondition parseInTag(JsonObject json) {
        String tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (tag == null) return EntityCondition.alwaysTrue();
        // in_tag is typically a biome tag check
        TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, Identifier.parse(tag));
        return player -> player.level().getBiome(player.blockPosition()).is(biomeTag);
    }

    private static EntityCondition parseFoodLevel(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getFoodData().getFoodLevel(), target);
    }

    private static EntityCondition parseSubmergedIn(JsonObject json) {
        String fluid = json.has("fluid") ? json.get("fluid").getAsString() : "";
        return switch (fluid) {
            case "minecraft:water" -> p -> p.isUnderWater();
            // "Submerged in lava" means eyes-in-lava, not just feet touching it.
            // isInLava() returns true on any overlap; isEyeInFluid(LAVA) is the
            // actual "submerged" predicate and matches the water branch above.
            case "minecraft:lava"  -> p -> p.isEyeInFluid(net.minecraft.tags.FluidTags.LAVA);
            default -> p -> p.isUnderWater() || p.isEyeInFluid(net.minecraft.tags.FluidTags.LAVA);
        };
    }

    private static EntityCondition parseEquippedItem(JsonObject json, String contextId) {
        String slot = json.has("equipment_slot") ? json.get("equipment_slot").getAsString() : "mainhand";
        EquipmentSlot eqSlot = mapEquipmentSlot(slot);

        // Check for item_condition sub-object
        JsonObject itemCond = json.has("item_condition") ? json.getAsJsonObject("item_condition") : null;
        if (itemCond == null) return EntityCondition.alwaysTrue();

        // Simplified item condition: check item id or tag
        String itemId = itemCond.has("id") ? itemCond.get("id").getAsString() : null;
        String itemTag = itemCond.has("tag") ? itemCond.get("tag").getAsString() : null;
        String itemType = itemCond.has("type") ? itemCond.get("type").getAsString() : "";

        // Handle ingredient-style item condition
        if (itemCond.has("ingredient") && itemCond.get("ingredient").isJsonObject()) {
            JsonObject ing = itemCond.getAsJsonObject("ingredient");
            if (ing.has("item")) itemId = ing.get("item").getAsString();
            else if (ing.has("tag")) itemTag = ing.get("tag").getAsString();
        }

        final String fItemId = itemId;
        final String fItemTag = itemTag;

        if (fItemId != null) {
            Identifier targetItem = Identifier.parse(fItemId);
            return player -> {
                ItemStack stack = player.getItemBySlot(eqSlot);
                return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(targetItem);
            };
        }
        if (fItemTag != null) {
            var itemTagKey = TagKey.create(Registries.ITEM, Identifier.parse(fItemTag));
            return player -> {
                ItemStack stack = player.getItemBySlot(eqSlot);
                return stack.is(itemTagKey);
            };
        }

        // Nested condition type check (e.g., origins:empty for checking empty slot)
        if ("origins:empty".equals(itemType) || "apace:empty".equals(itemType)) {
            return player -> player.getItemBySlot(eqSlot).isEmpty();
        }

        return EntityCondition.alwaysTrue();
    }

    private static EquipmentSlot mapEquipmentSlot(String slot) {
        return switch (slot.toLowerCase()) {
            case "head"     -> EquipmentSlot.HEAD;
            case "chest"    -> EquipmentSlot.CHEST;
            case "legs"     -> EquipmentSlot.LEGS;
            case "feet"     -> EquipmentSlot.FEET;
            case "offhand"  -> EquipmentSlot.OFFHAND;
            default         -> EquipmentSlot.MAINHAND;
        };
    }

    private static EntityCondition parseRelativeHealth(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            double ratio = player.getMaxHealth() > 0
                ? player.getHealth() / player.getMaxHealth() : 0.0;
            return comparison.test(ratio, target);
        };
    }

    private static EntityCondition parseFallDistance(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.fallDistance, target);
    }

    private static EntityCondition parseEnchantment(JsonObject json) {
        String enchantId = json.has("enchantment") ? json.get("enchantment").getAsString() : null;
        if (enchantId == null) return EntityCondition.alwaysTrue();
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 1;
        ComparisonType comparison = ComparisonType.fromString(comp);
        Identifier eid = Identifier.parse(enchantId);
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            var enchReg = sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var enchHolder = enchReg.get(eid).orElse(null);
            if (enchHolder == null) return false;
            int maxLevel = 0;
            for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                int lvl = stack.getEnchantmentLevel(enchHolder);
                if (lvl > maxLevel) maxLevel = lvl;
            }
            return comparison.test(maxLevel, target);
        };
    }

    private static EntityCondition parseBlockCondition(JsonObject json, String contextId) {
        // Block condition at player position
        JsonObject blockCond = json.has("block_condition") ? json.getAsJsonObject("block_condition") : json;
        String blockId = blockCond.has("block") ? blockCond.get("block").getAsString() : null;
        if (blockId == null) blockId = blockCond.has("id") ? blockCond.get("id").getAsString() : null;
        if (blockId != null) {
            Identifier bid = Identifier.parse(blockId);
            return player -> {
                Block block = player.level().getBlockState(player.blockPosition()).getBlock();
                return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
            };
        }
        String blockTag = blockCond.has("tag") ? blockCond.get("tag").getAsString() : null;
        if (blockTag != null) {
            var tagKey = TagKey.create(Registries.BLOCK, Identifier.parse(blockTag));
            return player -> player.level().getBlockState(player.blockPosition()).is(tagKey);
        }
        return EntityCondition.alwaysTrue();
    }

    private static EntityCondition parseLightLevel(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        String lightType = json.has("light_type") ? json.get("light_type").getAsString() : "";
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            BlockPos pos = player.blockPosition();
            int light = switch (lightType) {
                case "sky"   -> player.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
                case "block" -> player.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
                default      -> player.level().getMaxLocalRawBrightness(pos);
            };
            return comparison.test(light, target);
        };
    }

    private static EntityCondition parseNbt(JsonObject json) {
        String nbtPath = json.has("nbt") ? json.get("nbt").getAsString() : null;
        if (nbtPath == null) return EntityCondition.alwaysTrue();
        // Simplified: check if the player's persisted data contains the key
        return player -> {
            CompoundTag tag = player.getPersistentData();
            return tag.contains(nbtPath);
        };
    }

    private static EntityCondition parseScoreboard(JsonObject json) {
        String objective = json.has("objective") ? json.get("objective").getAsString() : null;
        if (objective == null) return EntityCondition.alwaysFalse();
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            if (player.level().getServer() == null) return false;
            var scoreboard = player.level().getServer().getScoreboard();
            var obj = scoreboard.getObjective(objective);
            if (obj == null) return false;
            var scores = scoreboard.listPlayerScores(obj);
            for (var score : scores) {
                if (score.owner().equals(player.getScoreboardName())) {
                    return comparison.test(score.value(), target);
                }
            }
            return false;
        };
    }

    private static EntityCondition parseCommand(JsonObject json) {
        String command = json.has("command") ? json.get("command").getAsString() : "";
        if (command.isBlank()) return EntityCondition.alwaysFalse();
        return player -> {
            if (player.level().getServer() == null) return false;
            try {
                // performPrefixedCommand returns void; wrap to detect success via no exception
                player.level().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withSuppressedOutput(), command
                );
                return true;
            } catch (Exception e) {
                return false;
            }
        };
    }

    private static EntityCondition parseFluidHeight(JsonObject json) {
        String fluid = json.has("fluid") ? json.get("fluid").getAsString() : "";
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            double height = switch (fluid) {
                case "minecraft:water" -> player.getFluidHeight(net.minecraft.tags.FluidTags.WATER);
                case "minecraft:lava"  -> player.getFluidHeight(net.minecraft.tags.FluidTags.LAVA);
                default -> 0.0;
            };
            return comparison.test(height, target);
        };
    }

    private static EntityCondition parseInBlock(JsonObject json, String contextId) {
        JsonObject blockCond = json.has("block_condition") ? json.getAsJsonObject("block_condition") : null;
        if (blockCond == null) return EntityCondition.alwaysTrue();
        String blockId = blockCond.has("block") ? blockCond.get("block").getAsString() : null;
        if (blockId == null) blockId = blockCond.has("id") ? blockCond.get("id").getAsString() : null;
        if (blockId != null) {
            Identifier bid = Identifier.parse(blockId);
            return player -> {
                Block block = player.level().getBlockState(player.blockPosition()).getBlock();
                return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
            };
        }
        return EntityCondition.alwaysTrue();
    }

    private static EntityCondition parseHeight(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getY(), target);
    }

    private static EntityCondition parseTemperature(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            float temp = player.level().getBiome(player.blockPosition()).value().getBaseTemperature();
            return comparison.test(temp, target);
        };
    }

    private static EntityCondition parseArmorValue(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getArmorValue(), target);
    }

    private static EntityCondition parseAmount(JsonObject json) {
        // Generic numeric comparison wrapper — just delegates to comparison fields
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        // amount condition is context-dependent; when standalone, check health as default
        return player -> comparison.test(player.getHealth(), target);
    }

    private static EntityCondition parseEntityType(JsonObject json) {
        // For a player context, player entity type is always minecraft:player.
        // A JSON author using this against the player really only has one meaningful match.
        String expected = json.has("entity_type") ? json.get("entity_type").getAsString()
                        : json.has("type_id") ? json.get("type_id").getAsString() : "";
        if (expected.isEmpty()) return EntityCondition.alwaysTrue();
        final String target = expected;
        return p -> {
            Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(p.getType());
            return typeId != null && typeId.toString().equals(target);
        };
    }

    private static EntityCondition parsePowerType(JsonObject json, String contextId) {
        // Predicate: "does the player have any granted power whose type matches this id?"
        // Delegates to ActiveOriginService for the lookup.
        String expected = json.has("power_type") ? json.get("power_type").getAsString()
                        : json.has("id") ? json.get("id").getAsString() : null;
        if (expected == null || expected.isBlank()) {
            return failClosed("origins:power_type", contextId, "missing 'power_type' field");
        }
        final String target = expected.indexOf(':') < 0 ? "origins:" + expected : expected;
        return p -> {
            var data = p.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            for (var originEntry : data.getOrigins().entrySet()) {
                var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(originEntry.getValue());
                if (origin == null) continue;
                for (Identifier powerId : origin.powers()) {
                    var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder == null) continue;
                    Identifier typeId = com.cyberday1.neoorigins.power.registry.PowerTypes.getId(holder.type());
                    if (typeId != null && typeId.toString().equals(target)) return true;
                }
            }
            return false;
        };
    }

    // ---- origins:predicate (Apoli meta-wrapper around vanilla MC predicates) ----

    private static EntityCondition parsePredicate(JsonObject json, String contextId) {
        String predicateType = json.has("predicate_type") ? json.get("predicate_type").getAsString() : null;
        JsonElement predicateJson = json.has("predicate") ? json.get("predicate") : null;
        if (predicateType == null || predicateJson == null) {
            return failClosed("origins:predicate", contextId,
                "missing required field 'predicate_type' or 'predicate'");
        }
        return switch (predicateType) {
            case "biome"             -> parseBiomePredicate(predicateJson, contextId);
            case "block_state"       -> parseBlockStatePredicate(predicateJson, contextId);
            case "entity_properties" -> parseEntityPropertiesPredicate(predicateJson, contextId);
            case "fluid_state"       -> parseFluidStatePredicate(predicateJson, contextId);
            case "item"              -> parseItemPredicate(predicateJson, contextId);
            case "location"          -> parseLocationPredicate(predicateJson, contextId);
            case "damage"            -> failClosed("origins:predicate", contextId,
                "predicate_type 'damage' requires damage-source context (use action-on-hit hooks)");
            default                  -> failClosed("origins:predicate", contextId,
                "unknown predicate_type '" + predicateType + "'");
        };
    }

    private static EntityCondition parseBiomePredicate(JsonElement predicateJson, String contextId) {
        if (!predicateJson.isJsonObject()) {
            return failClosed("origins:predicate/biome", contextId, "predicate must be a JSON object");
        }
        JsonObject obj = predicateJson.getAsJsonObject();
        List<ResourceKey<Biome>> biomeKeys = new ArrayList<>();
        if (obj.has("biomes") && obj.get("biomes").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("biomes")) {
                biomeKeys.add(ResourceKey.create(Registries.BIOME, Identifier.parse(el.getAsString())));
            }
        }
        TagKey<Biome> tagKey = null;
        if (obj.has("tag")) {
            tagKey = TagKey.create(Registries.BIOME, Identifier.parse(obj.get("tag").getAsString()));
        }
        if (biomeKeys.isEmpty() && tagKey == null) {
            return failClosed("origins:predicate/biome", contextId, "expected 'biomes' list or 'tag'");
        }
        final TagKey<Biome> fTagKey = tagKey;
        return player -> {
            Holder<Biome> holder = player.level().getBiome(player.blockPosition());
            if (fTagKey != null && holder.is(fTagKey)) return true;
            for (ResourceKey<Biome> key : biomeKeys) {
                if (holder.is(key)) return true;
            }
            return false;
        };
    }

    private static EntityCondition parseBlockStatePredicate(JsonElement predicateJson, String contextId) {
        DataResult<BlockPredicate> result = BlockPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/block_state", contextId,
                result.error().get().message());
        }
        BlockPredicate pred = result.result().orElseThrow();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            return pred.matches(sl, player.blockPosition());
        };
    }

    private static EntityCondition parseEntityPropertiesPredicate(JsonElement predicateJson, String contextId) {
        DataResult<EntityPredicate> result = EntityPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/entity_properties", contextId,
                result.error().get().message());
        }
        EntityPredicate pred = result.result().orElseThrow();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            return pred.matches(sl, player.position(), player);
        };
    }

    private static EntityCondition parseFluidStatePredicate(JsonElement predicateJson, String contextId) {
        DataResult<FluidPredicate> result = FluidPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/fluid_state", contextId,
                result.error().get().message());
        }
        FluidPredicate pred = result.result().orElseThrow();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            return pred.matches(sl, player.blockPosition());
        };
    }

    private static EntityCondition parseItemPredicate(JsonElement predicateJson, String contextId) {
        DataResult<ItemPredicate> result = ItemPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/item", contextId,
                result.error().get().message());
        }
        ItemPredicate pred = result.result().orElseThrow();
        return player -> pred.test(player.getItemBySlot(EquipmentSlot.MAINHAND));
    }

    private static EntityCondition parseLocationPredicate(JsonElement predicateJson, String contextId) {
        DataResult<LocationPredicate> result = LocationPredicate.CODEC.parse(JsonOps.INSTANCE, predicateJson);
        if (result.error().isPresent()) {
            return failClosed("origins:predicate/location", contextId,
                result.error().get().message());
        }
        LocationPredicate pred = result.result().orElseThrow();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            return pred.matches(sl, player.getX(), player.getY(), player.getZ());
        };
    }

    private static EntityCondition parseTimeOfDay(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        long target = json.has("compare_to") ? json.get("compare_to").getAsLong() : 0L;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> comparison.test(p.level().getDefaultClockTime() % 24000L, target);
    }

    private static EntityCondition parseWeather(JsonObject json) {
        String state = json.has("state") ? json.get("state").getAsString().toLowerCase()
                     : json.has("value") ? json.get("value").getAsString().toLowerCase() : "clear";
        return p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            return switch (state) {
                case "clear" -> !sl.isRaining() && !sl.isThundering();
                case "rain", "raining" -> sl.isRaining() && !sl.isThundering();
                case "thunder", "thundering" -> sl.isThundering();
                default -> false;
            };
        };
    }

    private static EntityCondition parseXpLevel(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> comparison.test(p.experienceLevel, target);
    }

    private static EntityCondition parseXpPoints(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> comparison.test(p.totalExperience, target);
    }

    private static EntityCondition parseMoonPhase(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : "==";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> {
            if (!(p.level() instanceof ServerLevel sl)) return false;
            // MC's moon phase is an 8-phase cycle derived from the world clock.
            int phase = (int) ((sl.getDefaultClockTime() / 24000L) % 8L);
            if (phase < 0) phase += 8;
            return comparison.test(phase, target);
        };
    }

    /**
     * Context-aware condition that checks whether the current FOOD_EATEN
     * event's held {@link ItemStack} is in the named item tag. Requires an
     * active {@link com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext}
     * in the {@link com.cyberday1.neoorigins.service.ActionContextHolder};
     * evaluates to false outside that context. Used by the
     * {@code food_restriction} alias to re-express its item-tag filter.
     */
    private static EntityCondition parseFoodItemInTag(JsonObject json) {
        String tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (tag == null) return CompatPolicy.FALSE_CONDITION;
        TagKey<net.minecraft.world.item.Item> itemTag =
            TagKey.create(Registries.ITEM, Identifier.parse(tag));
        return p -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext fc)) {
                return false;
            }
            return fc.stack().is(itemTag);
        };
    }

    /**
     * Sibling of {@link #parseFoodItemInTag} that matches a single item ID
     * exactly instead of a tag. Used by the aquatic-origin "fish diet" power
     * to give per-item food bonuses (raw cod → cooked cod values, raw salmon
     * → cooked salmon values) without needing one tag per fish item.
     */
    private static EntityCondition parseFoodItemId(JsonObject json) {
        String idStr = json.has("id") ? json.get("id").getAsString() : null;
        if (idStr == null) return CompatPolicy.FALSE_CONDITION;
        Identifier itemId = Identifier.parse(idStr);
        // 26.1: BuiltInRegistries.ITEM.get returns Optional<Holder<Item>> —
        // unwrap with .value() to compare against ItemStack.getItem().
        var itemHolderOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        if (itemHolderOpt.isEmpty()) return CompatPolicy.FALSE_CONDITION;
        net.minecraft.world.item.Item targetItem = itemHolderOpt.get().value();
        return p -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext fc)) {
                return false;
            }
            return fc.stack().getItem() == targetItem;
        };
    }

    /**
     * Context-aware condition that compares the current HIT_TAKEN event's
     * {@code amount} field against a threshold. Requires an active
     * {@link com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext}
     * in the {@link com.cyberday1.neoorigins.service.ActionContextHolder} —
     * evaluates to false outside that context. Used by the
     * {@code action_on_hit_taken} alias to re-express {@code min_damage}
     * gating.
     */
    private static EntityCondition parseHitTakenAmount(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc)) {
                return false;
            }
            return comparison.test(htc.amount(), target);
        };
    }

    // ---- Bientity helpers ----

    /**
     * Extract the "target" LivingEntity from the current dispatch context.
     * Returns null outside any bientity-relevant context, causing bientity
     * conditions to fail closed.
     */
    private static net.minecraft.world.entity.LivingEntity extractTarget(Object ctx) {
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc) {
            var e = htc.source().getEntity();
            return e instanceof net.minecraft.world.entity.LivingEntity le ? le : null;
        }
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.KillContext kc) {
            return kc.killed();
        }
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.EntityInteractContext eic) {
            return eic.target();
        }
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc) {
            if (phc.result() instanceof net.minecraft.world.phys.EntityHitResult ehr
                && ehr.getEntity() instanceof net.minecraft.world.entity.LivingEntity le) {
                return le;
            }
        }
        return null;
    }

    /** Extract the DamageSource from HitTakenContext; null outside a hit-taken dispatch. */
    private static net.minecraft.world.damagesource.DamageSource extractDamageSource(Object ctx) {
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc) {
            return htc.source();
        }
        return null;
    }

    private static EntityCondition parseDistance(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : "<=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            return comparison.test(p.distanceTo(le), target);
        };
    }

    private static EntityCondition parseCanSee() {
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            return p.hasLineOfSight(le);
        };
    }

    private static EntityCondition parseEqual() {
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return le != null && le.getUUID().equals(p.getUUID());
        };
    }

    private static EntityCondition parseTargetType(JsonObject json) {
        String et = json.has("entity_type") ? json.get("entity_type").getAsString() : null;
        if (et == null || et.isBlank()) return CompatPolicy.FALSE_CONDITION;
        final String target = et;
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            if (target.startsWith("#")) {
                TagKey<net.minecraft.world.entity.EntityType<?>> tag = TagKey.create(
                    Registries.ENTITY_TYPE, Identifier.parse(target.substring(1)));
                return le.getType().getTags().anyMatch(t -> t.equals(tag));
            }
            Identifier expected = Identifier.parse(target);
            Identifier actual = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
            return expected.equals(actual);
        };
    }

    private static EntityCondition parseTargetGroup(JsonObject json) {
        String group = json.has("group") ? json.get("group").getAsString() : null;
        if (group == null || group.isBlank()) return CompatPolicy.FALSE_CONDITION;
        TagKey<net.minecraft.world.entity.EntityType<?>> tag = TagKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("minecraft", group));
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            return le.getType().getTags().anyMatch(t -> t.equals(tag));
        };
    }

    /**
     * Bientity condition: true iff the current target's UUID is in the actor's named
     * entity-set. The {@code set} field is used verbatim as the key — pack authors
     * are expected to namespace it (e.g. {@code "mypack:kill_streak"}) to avoid collision.
     * Fails closed outside a bientity context.
     */
    private static EntityCondition parseInSet(JsonObject json, String contextId) {
        String setName = json.has("set") ? json.get("set").getAsString() : null;
        if (setName == null || setName.isBlank()) {
            return failClosed("origins:in_set", contextId, "missing required field 'set'");
        }
        final String key = setName;
        return p -> {
            var le = extractTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return false;
            var data = p.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            return data.getEntitySet(key).contains(le.getUUID());
        };
    }

    // ---- Damage helpers ----

    private static EntityCondition parseFromFire() {
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
        };
    }

    private static EntityCondition parseFromProjectile() {
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE);
        };
    }

    private static EntityCondition parseFromExplosion() {
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
        };
    }

    private static EntityCondition parseDamageType(JsonObject json) {
        String id = json.has("damage_type") ? json.get("damage_type").getAsString() : null;
        if (id == null || id.isBlank()) return CompatPolicy.FALSE_CONDITION;
        final ResourceKey<net.minecraft.world.damagesource.DamageType> key =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.parse(id));
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(key);
        };
    }

    private static EntityCondition parseDamageTag(JsonObject json) {
        String tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (tag == null || tag.isBlank()) return CompatPolicy.FALSE_CONDITION;
        final TagKey<net.minecraft.world.damagesource.DamageType> key =
            TagKey.create(Registries.DAMAGE_TYPE, Identifier.parse(tag.startsWith("#") ? tag.substring(1) : tag));
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && src.is(key);
        };
    }

    private static EntityCondition parseDamageName(JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : null;
        if (name == null || name.isBlank()) return CompatPolicy.FALSE_CONDITION;
        final String expected = name;
        return p -> {
            var src = extractDamageSource(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            return src != null && expected.equalsIgnoreCase(src.getMsgId());
        };
    }

    /**
     * has_effect: true when the player has the specified MobEffect active.
     * <pre>{ "type": "neoorigins:has_effect", "effect": "minecraft:luck" }</pre>
     * Useful for gating passives on consumable-applied buffs (mirrors the
     * FortuneWhenEffectPower gate pattern for DSL authors).
     */
    private static EntityCondition parseHasEffect(JsonObject json) {
        if (!json.has("effect")) return CompatPolicy.FALSE_CONDITION;
        Identifier id = Identifier.parse(json.get("effect").getAsString());
        return p -> {
            var effect = BuiltInRegistries.MOB_EFFECT.getOptional(id);
            if (effect.isEmpty()) return false;
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect.get());
            return p.hasEffect(holder);
        };
    }

    /**
     * near_block: true when any matching block is within {@code radius} blocks
     * (default 4, capped at 8) of the player. Accepts any combination of:
     * <ul>
     *   <li>{@code block} — single block ID</li>
     *   <li>{@code blocks} — list of block IDs</li>
     *   <li>{@code tag} — single block tag (with or without leading {@code #})</li>
     *   <li>{@code tags} — list of block tags</li>
     * </ul>
     * A block matches if it's in ANY of the provided blocks/tags (logical OR).
     *
     * <pre>{ "type": "neoorigins:near_block", "block": "minecraft:lava", "radius": 3 }</pre>
     * <pre>{ "type": "neoorigins:near_block", "tags": ["minecraft:campfires", "#c:fire"], "radius": 5 }</pre>
     *
     * Scans a cubic AABB; intended for ambient proximity buffs (campfire
     * warmth, lava-side speed, etc.). Capped at radius 8 to avoid overly
     * expensive per-tick scans.
     */
    private static EntityCondition parseNearBlock(JsonObject json, String contextId) {
        int radius = Math.min(8, Math.max(1,
            json.has("radius") ? json.get("radius").getAsInt() : 4));
        List<Identifier> blockIds = new ArrayList<>();
        List<TagKey<Block>> tags = new ArrayList<>();

        if (json.has("block")) {
            blockIds.add(Identifier.parse(json.get("block").getAsString()));
        }
        if (json.has("blocks") && json.get("blocks").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("blocks")) {
                if (el.isJsonPrimitive()) blockIds.add(Identifier.parse(el.getAsString()));
            }
        }
        if (json.has("tag")) {
            tags.add(parseBlockTag(json.get("tag").getAsString()));
        }
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("tags")) {
                if (el.isJsonPrimitive()) tags.add(parseBlockTag(el.getAsString()));
            }
        }

        if (blockIds.isEmpty() && tags.isEmpty()) return failClosed("neoorigins:near_block",
            contextId, "requires 'block'/'blocks' or 'tag'/'tags'");

        final int r = radius;
        final List<Identifier> finalBlockIds = List.copyOf(blockIds);
        final List<TagKey<Block>> finalTags = List.copyOf(tags);
        return p -> {
            BlockPos origin = p.blockPosition();
            Level level = p.level();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(pos);
                        for (Identifier id : finalBlockIds) {
                            var block = BuiltInRegistries.BLOCK.getOptional(id);
                            if (block.isPresent() && state.is(block.get())) return true;
                        }
                        for (TagKey<Block> tag : finalTags) {
                            if (state.is(tag)) return true;
                        }
                    }
                }
            }
            return false;
        };
    }

    private static TagKey<Block> parseBlockTag(String raw) {
        if (raw.startsWith("#")) raw = raw.substring(1);
        return TagKey.create(Registries.BLOCK, Identifier.parse(raw));
    }

    /**
     * out_of_combat: true when {@code ticks} or more have elapsed since the
     * player last took damage. Default threshold 100 ticks (5 s).
     * <pre>{ "type": "neoorigins:out_of_combat" }</pre>
     * <pre>{ "type": "neoorigins:out_of_combat", "ticks": 200 }</pre>
     * Backed by {@link com.cyberday1.neoorigins.service.CombatTracker} which
     * timestamps damage hits via {@code CombatPowerEvents.onLivingDamage}
     * and is forgotten on logout.
     */
    private static EntityCondition parseOutOfCombat(JsonObject json) {
        int threshold = json.has("ticks") ? Math.max(0, json.get("ticks").getAsInt()) : 100;
        return p -> {
            if (!(p instanceof net.minecraft.server.level.ServerPlayer sp)) return true;
            return com.cyberday1.neoorigins.service.CombatTracker.ticksSinceLastDamage(sp) >= threshold;
        };
    }

    private static EntityCondition failClosed(String type, String contextId, String detail) {
        NeoOrigins.LOGGER.warn("[CompatB] condition '{}' in {} failed closed: {}",
            type, contextId, detail);
        return CompatPolicy.FALSE_CONDITION;
    }
}
