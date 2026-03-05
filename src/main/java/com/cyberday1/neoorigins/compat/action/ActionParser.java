package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

public final class ActionParser {

    private ActionParser() {}

    public static EntityAction parse(JsonObject json, String contextId) {
        if (json == null) return EntityAction.noop();
        String type = json.has("type") ? json.get("type").getAsString() : "";
        try {
            return switch (type) {
                case "origins:and", "apace:and"                             -> parseAnd(json, contextId);
                case "origins:if_else", "apace:if_else"                     -> parseIfElse(json, contextId);
                case "origins:if_else_list", "apace:if_else_list"           -> parseIfElseList(json, contextId);
                case "origins:chance", "apace:chance"                       -> parseChance(json, contextId);
                case "origins:delay", "apace:delay"                         -> parseDelay(json, contextId);
                case "origins:execute_command", "apace:execute_command"     -> parseExecuteCommand(json);
                case "origins:apply_effect", "apace:apply_effect"           -> parseApplyEffect(json);
                case "origins:clear_effect", "apace:clear_effect"           -> parseClearEffect(json);
                case "origins:heal", "apace:heal"                           -> parseHeal(json);
                case "origins:play_sound", "apace:play_sound"               -> parsePlaySound(json);
                case "origins:add_velocity", "apace:add_velocity"           -> parseAddVelocity(json);
                case "origins:set_on_fire", "apace:set_on_fire"             -> parseSetOnFire(json);
                case "origins:exhaust", "apace:exhaust"                     -> parseExhaust(json);
                case "origins:change_resource", "apace:change_resource"     -> parseChangeResource(json);
                case "origins:nothing", "apace:nothing"                     -> EntityAction.noop();
                default -> {
                    NeoOrigins.LOGGER.debug(
                        "OriginsCompat: unsupported action type '{}' in {} — no-op", type, contextId);
                    yield EntityAction.noop();
                }
            };
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("OriginsCompat: failed to parse action '{}' in {}: {}",
                type, contextId, e.getMessage());
            return EntityAction.noop();
        }
    }

    private static EntityAction parseAnd(JsonObject json, String ctx) {
        JsonArray arr = json.has("actions") ? json.getAsJsonArray("actions") : new JsonArray();
        List<EntityAction> actions = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) actions.add(parse(el.getAsJsonObject(), ctx));
        }
        return player -> { for (EntityAction a : actions) a.execute(player); };
    }

    private static EntityAction parseIfElse(JsonObject json, String ctx) {
        EntityCondition cond = json.has("condition")
            ? ConditionParser.parse(json.getAsJsonObject("condition"), ctx)
            : EntityCondition.alwaysTrue();
        EntityAction ifAction   = json.has("if_action")
            ? parse(json.getAsJsonObject("if_action"), ctx) : EntityAction.noop();
        EntityAction elseAction = json.has("else_action")
            ? parse(json.getAsJsonObject("else_action"), ctx) : EntityAction.noop();
        return player -> {
            if (cond.test(player)) ifAction.execute(player);
            else elseAction.execute(player);
        };
    }

    private static EntityAction parseIfElseList(JsonObject json, String ctx) {
        JsonArray arr = json.has("actions") ? json.getAsJsonArray("actions") : new JsonArray();
        record Branch(EntityCondition cond, EntityAction action) {}
        List<Branch> branches = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            EntityCondition cond = obj.has("condition")
                ? ConditionParser.parse(obj.getAsJsonObject("condition"), ctx)
                : EntityCondition.alwaysTrue();
            EntityAction act = obj.has("action")
                ? parse(obj.getAsJsonObject("action"), ctx) : EntityAction.noop();
            branches.add(new Branch(cond, act));
        }
        return player -> {
            for (var branch : branches) {
                if (branch.cond().test(player)) {
                    branch.action().execute(player);
                    return;
                }
            }
        };
    }

    private static EntityAction parseChance(JsonObject json, String ctx) {
        float chance = json.has("chance") ? json.get("chance").getAsFloat() : 0.5f;
        EntityAction action = json.has("action")
            ? parse(json.getAsJsonObject("action"), ctx) : EntityAction.noop();
        return player -> {
            if (player.getRandom().nextFloat() < chance) action.execute(player);
        };
    }

    private static EntityAction parseDelay(JsonObject json, String ctx) {
        int ticks = json.has("ticks") ? json.get("ticks").getAsInt() : 1;
        EntityAction action = json.has("action")
            ? parse(json.getAsJsonObject("action"), ctx) : EntityAction.noop();
        return player -> {
            if (player.level().getServer() != null) {
                long target = player.level().getServer().getTickCount() + ticks;
                CompatTickScheduler.schedule(target, player, action::execute);
            }
        };
    }

    private static EntityAction parseExecuteCommand(JsonObject json) {
        String command = json.has("command") ? json.get("command").getAsString() : "";
        return player -> {
            if (player.level().getServer() == null || command.isBlank()) return;
            try {
                // Use the player's own source stack — permission level matches their op level.
                player.level().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack(), command
                );
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn("OriginsCompat: execute_command failed: {}", e.getMessage());
            }
        };
    }

    private static EntityAction parseApplyEffect(JsonObject json) {
        // Origins apply_effect has two formats:
        //   1. "effect": "minecraft:speed", "duration": 200, "amplifier": 0   (flat)
        //   2. "effects": [{"effect": "minecraft:speed", "duration": 200, ...}]  (array)
        // Resolve to the first effect in either case.
        String effectId = null;
        int  duration  = 200;
        int  amplifier = 0;
        boolean ambient   = false;
        boolean particles = true;
        boolean icon      = true;

        if (json.has("effects") && json.get("effects").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("effects");
            if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                JsonObject eff = arr.get(0).getAsJsonObject();
                effectId  = resolveEffectId(eff);
                duration  = eff.has("duration")       ? eff.get("duration").getAsInt()       : duration;
                amplifier = eff.has("amplifier")      ? eff.get("amplifier").getAsInt()      : amplifier;
                ambient   = eff.has("is_ambient")     && eff.get("is_ambient").getAsBoolean();
                particles = !eff.has("show_particles") || eff.get("show_particles").getAsBoolean();
                icon      = !eff.has("show_icon")      || eff.get("show_icon").getAsBoolean();
            }
        } else {
            effectId  = resolveEffectId(json);
            duration  = json.has("duration")       ? json.get("duration").getAsInt()       : duration;
            amplifier = json.has("amplifier")      ? json.get("amplifier").getAsInt()      : amplifier;
            ambient   = json.has("is_ambient")     && json.get("is_ambient").getAsBoolean();
            particles = !json.has("show_particles") || json.get("show_particles").getAsBoolean();
            icon      = !json.has("show_icon")      || json.get("show_icon").getAsBoolean();
        }

        if (effectId == null) return EntityAction.noop();
        Identifier effId = Identifier.parse(effectId);
        final int  fDur  = duration, fAmp = amplifier;
        final boolean fAmb = ambient, fPart = particles, fIcon = icon;
        return player -> BuiltInRegistries.MOB_EFFECT.get(effId).ifPresent(holder ->
            player.addEffect(new MobEffectInstance(holder, fDur, fAmp, fAmb, fPart, fIcon))
        );
    }

    /** Resolves the effect ID string from a JSON object that may use "effect" or "id" keys. */
    private static String resolveEffectId(JsonObject obj) {
        if (obj.has("effect") && obj.get("effect").isJsonPrimitive()) {
            return obj.get("effect").getAsString();
        }
        if (obj.has("id") && obj.get("id").isJsonPrimitive()) {
            return obj.get("id").getAsString();
        }
        return null;
    }

    private static EntityAction parseClearEffect(JsonObject json) {
        String effectId = json.has("effect") ? json.get("effect").getAsString() : null;
        if (effectId == null) {
            return player -> player.removeAllEffects();
        }
        Identifier effId = Identifier.parse(effectId);
        return player -> BuiltInRegistries.MOB_EFFECT.get(effId).ifPresent(player::removeEffect);
    }

    private static EntityAction parseHeal(JsonObject json) {
        float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
        return player -> player.heal(amount);
    }

    private static EntityAction parsePlaySound(JsonObject json) {
        String soundId = json.has("sound") ? json.get("sound").getAsString() : null;
        if (soundId == null) return EntityAction.noop();
        float volume = json.has("volume") ? json.get("volume").getAsFloat() : 1.0f;
        float pitch  = json.has("pitch")  ? json.get("pitch").getAsFloat()  : 1.0f;
        Identifier sId = Identifier.parse(soundId);
        return player -> BuiltInRegistries.SOUND_EVENT.get(sId).ifPresent(h ->
            player.playSound(h.value(), volume, pitch)
        );
    }

    private static EntityAction parseAddVelocity(JsonObject json) {
        double x   = json.has("x") ? json.get("x").getAsDouble() : 0;
        double y   = json.has("y") ? json.get("y").getAsDouble() : 0;
        double z   = json.has("z") ? json.get("z").getAsDouble() : 0;
        boolean set = json.has("set") && json.get("set").getAsBoolean();
        return player -> {
            if (set) player.setDeltaMovement(x, y, z);
            else     player.push(x, y, z);
        };
    }

    private static EntityAction parseSetOnFire(JsonObject json) {
        int ticks = json.has("ticks") ? json.get("ticks").getAsInt() : 20;
        return player -> player.setRemainingFireTicks(ticks);
    }

    private static EntityAction parseExhaust(JsonObject json) {
        float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
        return player -> player.getFoodData().addExhaustion(amount);
    }

    private static EntityAction parseChangeResource(JsonObject json) {
        // origins:change_resource — modifies the integer value of an origins:resource power
        String resourceId = json.has("resource") ? json.get("resource").getAsString() : null;
        if (resourceId == null) return EntityAction.noop();

        // Determine operation and amount
        String operation = json.has("operation") ? json.get("operation").getAsString() : "add";
        int change = json.has("change") ? json.get("change").getAsInt() : 0;

        // We need the min/max bounds — they're defined in the resource power itself.
        // Without them, clamp to [Integer.MIN_VALUE, Integer.MAX_VALUE] (effectively unclamped).
        // The resource power's own onTick will fire min/max actions when values go out of bounds.
        final String key = resourceId;
        return switch (operation) {
            case "add"   -> player -> player.getData(CompatAttachments.resourceState()).clampedAdd(key, change, Integer.MIN_VALUE, Integer.MAX_VALUE);
            case "set"   -> player -> player.getData(CompatAttachments.resourceState()).set(key, change);
            default      -> player -> player.getData(CompatAttachments.resourceState()).clampedAdd(key, change, Integer.MIN_VALUE, Integer.MAX_VALUE);
        };
    }
}
