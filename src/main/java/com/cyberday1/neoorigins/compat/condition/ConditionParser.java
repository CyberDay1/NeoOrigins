package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

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
        // Normalize bare type names (pre-namespace Origins JSON and loose community packs).
        // `"type": "and"` → `"type": "origins:and"`.
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "origins:" + type;
        }
        try {
            return switch (type) {
                case "origins:and", "apace:and"                         -> parseAnd(json, contextId);
                case "origins:or", "apace:or"                           -> parseOr(json, contextId);
                case "origins:not", "apace:not"                         -> parseNot(json, contextId);
                case "origins:constant", "apace:constant"               ->
                    json.has("value") && json.get("value").getAsBoolean()
                        ? EntityCondition.alwaysTrue() : EntityCondition.alwaysFalse();
                case "origins:sneaking", "apace:sneaking"               -> p -> p.isShiftKeyDown();
                case "origins:sprinting", "apace:sprinting"             -> p -> p.isSprinting();
                case "origins:on_ground", "apace:on_ground"             -> p -> p.onGround();
                case "origins:in_water", "apace:in_water"               -> p -> p.isInWater();
                case "origins:swimming", "apace:swimming"               -> p -> p.isSwimming();
                case "origins:submerged_in_water", "apace:submerged_in_water" -> p -> p.isUnderWater();
                case "origins:fall_flying", "apace:fall_flying"         -> p -> p.isFallFlying();
                case "origins:invisible", "apace:invisible"             -> p -> p.isInvisible();
                case "origins:moving", "apace:moving"                   -> p -> {
                    var dm = p.getDeltaMovement();
                    return dm.x != 0 || dm.z != 0;
                };
                case "origins:in_rain", "apace:in_rain"                 -> p -> {
                    if (!(p.level() instanceof ServerLevel sl)) return false;
                    return sl.isRainingAt(p.blockPosition());
                };
                case "origins:daytime", "apace:daytime"                 ->
                    p -> p.level().getDayTime() % 24000L < 13000L;
                case "origins:exposed_to_sky", "apace:exposed_to_sky"   -> p -> {
                    if (!(p.level() instanceof ServerLevel sl)) return false;
                    return sl.canSeeSky(p.blockPosition());
                };
                case "origins:exposed_to_sun", "apace:exposed_to_sun"   -> p -> {
                    if (!(p.level() instanceof ServerLevel sl)) return false;
                    long time = sl.getDayTime() % 24000L;
                    return time > 6000L && time < 12000L
                        && sl.canSeeSky(p.blockPosition()) && !sl.isRaining();
                };
                case "origins:health", "apace:health"                   -> parseHealth(json);
                case "origins:resource", "apace:resource"               -> parseResource(json, contextId);
                case "origins:power_active", "apace:power_active"       -> parsePowerActive(json, contextId);
                case "origins:on_block", "apace:on_block"               -> parseOnBlock(json, contextId);

                // ---- Phase 1: New conditions ----
                case "origins:dimension", "apace:dimension"             -> parseDimension(json);
                case "origins:biome", "apace:biome"                     -> parseBiome(json);
                case "origins:in_tag", "apace:in_tag"                   -> parseInTag(json);
                case "origins:food_level", "origins:food", "apace:food_level", "apace:food"
                                                                        -> parseFoodLevel(json);
                case "origins:submerged_in", "apace:submerged_in"       -> parseSubmergedIn(json);
                case "origins:on_fire", "origins:fire", "apace:on_fire", "apace:fire"
                                                                        -> p -> p.isOnFire();
                case "origins:equipped_item", "apace:equipped_item"     -> parseEquippedItem(json, contextId);
                case "origins:relative_health", "apace:relative_health" -> parseRelativeHealth(json);
                case "origins:fall_distance", "apace:fall_distance"     -> parseFallDistance(json);
                case "origins:enchantment", "apace:enchantment"         -> parseEnchantment(json);
                case "origins:block", "apace:block"                     -> parseBlockCondition(json, contextId);
                case "origins:light_level", "apace:light_level"         -> parseLightLevel(json);
                case "origins:nbt", "apace:nbt"                         -> parseNbt(json);
                case "origins:scoreboard", "apace:scoreboard"           -> parseScoreboard(json);
                case "origins:command", "apace:command"                 -> parseCommand(json);
                case "origins:passenger", "origins:riding", "apace:passenger", "apace:riding"
                                                                        -> p -> p.isPassenger();
                case "origins:entity_type", "apace:entity_type"         -> EntityCondition.alwaysTrue();
                case "origins:fluid_height", "apace:fluid_height"       -> parseFluidHeight(json);
                case "origins:in_block", "origins:in_block_anywhere",
                     "apace:in_block", "apace:in_block_anywhere"       -> parseInBlock(json, contextId);
                case "origins:brightness", "apace:brightness"           -> parseLightLevel(json);
                case "origins:height", "apace:height"                   -> parseHeight(json);
                case "origins:block_collision", "apace:block_collision" -> EntityCondition.alwaysTrue();
                case "origins:temperature", "apace:temperature"         -> parseTemperature(json);
                case "origins:armor_value", "apace:armor_value"         -> parseArmorValue(json);
                case "origins:amount", "apace:amount"                   -> parseAmount(json);
                case "origins:using_item", "apace:using_item"           -> p -> p.isUsingItem();
                case "origins:ticking", "apace:ticking"                 -> EntityCondition.alwaysTrue();
                case "origins:exists", "apace:exists"                   -> EntityCondition.alwaysTrue();
                case "origins:living", "apace:living"                   -> EntityCondition.alwaysTrue();
                case "origins:creative_flying", "apace:creative_flying" -> p -> p.getAbilities().flying;
                case "origins:power_type", "apace:power_type"           -> EntityCondition.alwaysTrue();

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
        ResourceLocation bid = ResourceLocation.parse(blockId);
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
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
        return player -> player.level().dimension().equals(dimKey);
    }

    private static EntityCondition parseBiome(JsonObject json) {
        // Can check either "biome" (exact id) or "condition" (sub-condition on biome)
        String biomeId = json.has("biome") ? json.get("biome").getAsString() : null;
        if (biomeId != null) {
            ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId));
            return player -> {
                var biomeHolder = player.level().getBiome(player.blockPosition());
                return biomeHolder.is(biomeKey);
            };
        }
        // Check for tag-based biome condition
        String tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (tag != null) {
            TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, ResourceLocation.parse(tag));
            return player -> player.level().getBiome(player.blockPosition()).is(biomeTag);
        }
        return EntityCondition.alwaysTrue();
    }

    private static EntityCondition parseInTag(JsonObject json) {
        String tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (tag == null) return EntityCondition.alwaysTrue();
        // in_tag is typically a biome tag check
        TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, ResourceLocation.parse(tag));
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
            case "minecraft:lava"  -> p -> p.isInLava();
            default -> p -> p.isUnderWater() || p.isInLava();
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
            ResourceLocation targetItem = ResourceLocation.parse(fItemId);
            return player -> {
                ItemStack stack = player.getItemBySlot(eqSlot);
                return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(targetItem);
            };
        }
        if (fItemTag != null) {
            var itemTagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(fItemTag));
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
        ResourceLocation eid = ResourceLocation.parse(enchantId);
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return false;
            var enchReg = sl.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var enchOpt = enchReg.getOptional(eid);
            if (enchOpt.isEmpty()) return false;
            var enchHolder = enchReg.wrapAsHolder(enchOpt.get());
            int maxLevel = 0;
            for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                int lvl = stack.getEnchantments().getLevel(enchHolder);
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
            ResourceLocation bid = ResourceLocation.parse(blockId);
            return player -> {
                Block block = player.level().getBlockState(player.blockPosition()).getBlock();
                return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
            };
        }
        String blockTag = blockCond.has("tag") ? blockCond.get("tag").getAsString() : null;
        if (blockTag != null) {
            var tagKey = TagKey.create(Registries.BLOCK, ResourceLocation.parse(blockTag));
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
            ResourceLocation bid = ResourceLocation.parse(blockId);
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

    private static EntityCondition failClosed(String type, String contextId, String detail) {
        NeoOrigins.LOGGER.warn("[CompatB] condition '{}' in {} failed closed: {}",
            type, contextId, detail);
        return CompatPolicy.FALSE_CONDITION;
    }
}
