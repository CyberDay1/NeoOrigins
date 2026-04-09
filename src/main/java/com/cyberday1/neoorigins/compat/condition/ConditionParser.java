package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class ConditionParser {

    private ConditionParser() {}

    public static EntityCondition parse(JsonObject json, String contextId) {
        if (json == null) {
            return failClosed("root", contextId, "missing condition object");
        }
        String type = json.has("type") ? json.get("type").getAsString() : "";
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

    private static EntityCondition failClosed(String type, String contextId, String detail) {
        NeoOrigins.LOGGER.warn("[CompatB] condition '{}' in {} failed closed: {}",
            type, contextId, detail);
        return CompatPolicy.FALSE_CONDITION;
    }
}
