package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class ConditionParser {

    private ConditionParser() {}

    public static EntityCondition parse(JsonObject json, String contextId) {
        if (json == null) return EntityCondition.alwaysTrue();
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
                case "origins:resource", "apace:resource"               -> parseResource(json);
                case "origins:power_active", "apace:power_active"       -> parsePowerActive(json);
                case "origins:on_block", "apace:on_block"               -> parseOnBlock(json);
                // Unknown / complex conditions: fail open (return true)
                default -> {
                    NeoOrigins.LOGGER.debug(
                        "OriginsCompat: unsupported condition type '{}' in {} — defaulting to true",
                        type, contextId);
                    yield EntityCondition.alwaysTrue();
                }
            };
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("OriginsCompat: failed to parse condition '{}' in {}: {}",
                type, contextId, e.getMessage());
            return EntityCondition.alwaysTrue();
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
        EntityCondition inner = json.has("condition")
            ? parse(json.getAsJsonObject("condition"), ctx)
            : EntityCondition.alwaysTrue();
        return player -> !inner.test(player);
    }

    private static EntityCondition parseHealth(JsonObject json) {
        String comp    = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target  = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> comparison.test(player.getHealth(), target);
    }

    private static EntityCondition parseResource(JsonObject json) {
        String powerId = json.has("resource") ? json.get("resource").getAsString() : null;
        if (powerId == null) return EntityCondition.alwaysTrue();
        String comp   = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target    = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType comparison = ComparisonType.fromString(comp);
        return player -> {
            int cur = player.getData(CompatAttachments.resourceState()).get(powerId, 0);
            return comparison.test(cur, target);
        };
    }

    private static EntityCondition parsePowerActive(JsonObject json) {
        String powerId = json.has("power") ? json.get("power").getAsString() : null;
        if (powerId == null) return EntityCondition.alwaysFalse();
        return player -> player.getData(CompatAttachments.toggleState()).isActive(powerId, false);
    }

    private static EntityCondition parseOnBlock(JsonObject json) {
        // Only support the simple block id sub-condition; unknown sub-conditions fail open.
        if (!json.has("block_condition")) return EntityCondition.alwaysTrue();
        JsonObject blockCond = json.getAsJsonObject("block_condition");
        String blockId = blockCond.has("id") ? blockCond.get("id").getAsString() : null;
        if (blockId == null) return EntityCondition.alwaysTrue();
        Identifier bid = Identifier.parse(blockId);
        return player -> {
            if (!player.onGround()) return false;
            BlockPos below = player.blockPosition().below();
            Block block = player.level().getBlockState(below).getBlock();
            return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
        };
    }
}
