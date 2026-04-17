package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class ActionParser {

    private ActionParser() {}

    public static EntityAction parse(JsonObject json, String contextId) {
        if (json == null) {
            return failNoop("root", contextId, "missing action object");
        }
        String type = json.has("type") ? json.get("type").getAsString() : "";
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "origins:" + type;
        }
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

                // ---- Phase 2: New actions ----
                case "origins:damage", "apace:damage"                       -> parseDamage(json);
                case "origins:feed", "apace:feed"                           -> parseFeed(json);
                case "origins:trigger_cooldown", "apace:trigger_cooldown"   -> parseTriggerCooldown(json);
                case "origins:gain_air", "apace:gain_air"                   -> parseGainAir(json);
                case "origins:spawn_entity", "apace:spawn_entity"           -> parseSpawnEntity(json);
                case "origins:set_fall_distance", "apace:set_fall_distance" -> parseSetFallDistance(json);
                case "origins:extinguish", "apace:extinguish"               -> player -> player.clearFire();
                case "origins:dismount", "apace:dismount"                   -> player -> player.stopRiding();
                case "origins:give", "apace:give"                           -> parseGive(json);
                case "origins:explode", "apace:explode"                     -> parseExplode(json);
                case "origins:launch", "apace:launch"                       -> parseLaunch(json);
                case "origins:set_block", "apace:set_block"                 -> parseSetBlock(json);
                case "origins:area_of_effect", "apace:area_of_effect"       -> parseAreaOfEffect(json, contextId);
                case "origins:modify_food", "apace:modify_food"             -> EntityAction.noop();
                case "origins:revoke_power", "origins:grant_power",
                     "apace:revoke_power", "apace:grant_power"             -> EntityAction.noop();
                case "origins:emit_game_event", "apace:emit_game_event"     -> EntityAction.noop();
                case "origins:swing_hand", "apace:swing_hand"               -> player -> player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

                default -> failNoop(type, contextId, "unsupported action type");
            };
        } catch (Exception e) {
            return failNoop(type, contextId, "parse error: " + e.getMessage());
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
        EntityCondition cond = json.has("condition") && json.get("condition").isJsonObject()
            ? ConditionParser.parse(json.getAsJsonObject("condition"), ctx)
            : CompatPolicy.FALSE_CONDITION;
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
            EntityCondition cond = obj.has("condition") && obj.get("condition").isJsonObject()
                ? ConditionParser.parse(obj.getAsJsonObject("condition"), ctx)
                : CompatPolicy.FALSE_CONDITION;
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
                // Upstream Origins runs execute_command at server-level permissions so
                // addon packs can invoke /function, /effect, /give, etc. for non-op
                // players. Permission level 2 matches vanilla's function-permission-level
                // default — the same level datapack advancement rewards run at.
                // Run as the server, but positioned at + targeting the player.
                // This sidesteps any MC-version-specific permission-level API
                // differences while matching upstream Origins' behaviour of
                // running execute_command at server authority.
                var serverSource = player.level().getServer().createCommandSourceStack()
                    .withSuppressedOutput()
                    .withEntity(player)
                    .withPosition(player.position())
                    .withRotation(player.getRotationVector())
                    .withLevel((net.minecraft.server.level.ServerLevel) player.level());
                player.level().getServer().getCommands().performPrefixedCommand(
                    serverSource, command
                );
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn("[CompatB] execute_command failed: {}", e.getMessage());
            }
        };
    }

    private static EntityAction parseApplyEffect(JsonObject json) {
        String effectId = null;
        int duration = 200;
        int amplifier = 0;
        boolean ambient = false;
        boolean particles = true;
        boolean icon = true;

        if (json.has("effects") && json.get("effects").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("effects");
            if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                JsonObject eff = arr.get(0).getAsJsonObject();
                effectId = resolveEffectId(eff);
                duration = eff.has("duration") ? eff.get("duration").getAsInt() : duration;
                amplifier = eff.has("amplifier") ? eff.get("amplifier").getAsInt() : amplifier;
                ambient = eff.has("is_ambient") && eff.get("is_ambient").getAsBoolean();
                particles = !eff.has("show_particles") || eff.get("show_particles").getAsBoolean();
                icon = !eff.has("show_icon") || eff.get("show_icon").getAsBoolean();
            }
        } else {
            effectId = resolveEffectId(json);
            duration = json.has("duration") ? json.get("duration").getAsInt() : duration;
            amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : amplifier;
            ambient = json.has("is_ambient") && json.get("is_ambient").getAsBoolean();
            particles = !json.has("show_particles") || json.get("show_particles").getAsBoolean();
            icon = !json.has("show_icon") || json.get("show_icon").getAsBoolean();
        }

        if (effectId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] apply_effect: missing effect id — action will no-op");
            return EntityAction.noop();
        }
        // Cache mob effect holder at parse time — registry is static
        var effectHolder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId)).orElse(null);
        if (effectHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] apply_effect: unknown effect '{}' — action will no-op", effectId);
            return EntityAction.noop();
        }
        final int fDur = duration;
        final int fAmp = amplifier;
        final boolean fAmb = ambient;
        final boolean fPart = particles;
        final boolean fIcon = icon;
        return player -> player.addEffect(new MobEffectInstance(effectHolder, fDur, fAmp, fAmb, fPart, fIcon));
    }

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
        var effectHolder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId)).orElse(null);
        if (effectHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] clear_effect: unknown effect '{}' — action will no-op", effectId);
            return EntityAction.noop();
        }
        return player -> player.removeEffect(effectHolder);
    }

    private static EntityAction parseHeal(JsonObject json) {
        float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
        return player -> player.heal(amount);
    }

    private static EntityAction parsePlaySound(JsonObject json) {
        String soundId = json.has("sound") ? json.get("sound").getAsString() : null;
        if (soundId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] play_sound: missing sound id — action will no-op");
            return EntityAction.noop();
        }
        float volume = json.has("volume") ? json.get("volume").getAsFloat() : 1.0f;
        float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 1.0f;
        var soundHolder = BuiltInRegistries.SOUND_EVENT.get(Identifier.parse(soundId)).orElse(null);
        if (soundHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] play_sound: unknown sound '{}' — action will no-op", soundId);
            return EntityAction.noop();
        }
        var sound = soundHolder.value();
        return player -> player.playSound(sound, volume, pitch);
    }

    private static EntityAction parseAddVelocity(JsonObject json) {
        double x = json.has("x") ? json.get("x").getAsDouble() : 0;
        double y = json.has("y") ? json.get("y").getAsDouble() : 0;
        double z = json.has("z") ? json.get("z").getAsDouble() : 0;
        boolean set = json.has("set") && json.get("set").getAsBoolean();
        return player -> {
            if (set) player.setDeltaMovement(x, y, z);
            else player.push(x, y, z);
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
        String resourceId = json.has("resource") ? json.get("resource").getAsString() : null;
        if (resourceId == null) return EntityAction.noop();

        String operation = json.has("operation") ? json.get("operation").getAsString() : "add";
        int change = json.has("change") ? json.get("change").getAsInt() : 0;

        final String key = resourceId;
        return switch (operation) {
            case "add" -> player -> player.getData(CompatAttachments.resourceState()).clampedAdd(key, change, Integer.MIN_VALUE, Integer.MAX_VALUE);
            case "set" -> player -> player.getData(CompatAttachments.resourceState()).set(key, change);
            default -> player -> player.getData(CompatAttachments.resourceState()).clampedAdd(key, change, Integer.MIN_VALUE, Integer.MAX_VALUE);
        };
    }

    // ---- Phase 2: New action parsers ----

    private static EntityAction parseDamage(JsonObject json) {
        float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
        // Determine damage source type
        String sourceType = "";
        if (json.has("source") && json.get("source").isJsonObject()) {
            JsonObject src = json.getAsJsonObject("source");
            sourceType = src.has("name") ? src.get("name").getAsString() : "";
        }
        final String fSrc = sourceType;
        return player -> {
            var dmgSrc = switch (fSrc) {
                case "fire", "on_fire", "in_fire" -> player.level().damageSources().onFire();
                case "lava"         -> player.level().damageSources().lava();
                case "magic"        -> player.level().damageSources().magic();
                case "starve"       -> player.level().damageSources().starve();
                case "drown"        -> player.level().damageSources().drown();
                case "freeze"       -> player.level().damageSources().freeze();
                case "wither"       -> player.level().damageSources().wither();
                default             -> player.level().damageSources().generic();
            };
            player.hurt(dmgSrc, amount);
        };
    }

    private static EntityAction parseFeed(JsonObject json) {
        int food = json.has("food") ? json.get("food").getAsInt() : 1;
        float saturation = json.has("saturation") ? json.get("saturation").getAsFloat() : 0.0f;
        return player -> player.getFoodData().eat(food, saturation);
    }

    private static EntityAction parseTriggerCooldown(JsonObject json) {
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 20;
        String powerId = json.has("power") ? json.get("power").getAsString() : null;
        if (powerId == null) return EntityAction.noop();
        return player -> {
            var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            data.setCooldown(powerId, player.tickCount, cooldown);
        };
    }

    private static EntityAction parseGainAir(JsonObject json) {
        int amount = json.has("amount") ? json.get("amount").getAsInt() : 10;
        return player -> player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + amount));
    }

    private static EntityAction parseSpawnEntity(JsonObject json) {
        String entityId = json.has("entity_type") ? json.get("entity_type").getAsString() : null;
        if (entityId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] spawn_entity: missing entity_type — action will no-op");
            return EntityAction.noop();
        }
        Identifier eid = Identifier.parse(entityId);
        var entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.get(eid);
        if (entityTypeOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] spawn_entity: unknown entity type '{}' — action will no-op", eid);
            return EntityAction.noop();
        }
        final EntityType<?> entityType = entityTypeOpt.get().value();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var entity = entityType.create(sl, EntitySpawnReason.COMMAND);
            if (entity == null) return;
            entity.setPos(player.getX(), player.getY(), player.getZ());
            sl.addFreshEntity(entity);
        };
    }

    private static EntityAction parseSetFallDistance(JsonObject json) {
        float distance = json.has("fall_distance") ? json.get("fall_distance").getAsFloat() : 0.0f;
        return player -> player.fallDistance = distance;
    }

    private static EntityAction parseGive(JsonObject json) {
        // Give an item stack to the player
        JsonObject stack = json.has("stack") ? json.getAsJsonObject("stack") : json;
        String itemId = stack.has("item") ? stack.get("item").getAsString() : null;
        if (itemId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] give: missing item id — action will no-op");
            return EntityAction.noop();
        }
        int count = stack.has("count") ? stack.get("count").getAsInt() : 1;
        Identifier iid = Identifier.parse(itemId);
        return player -> {
            var itemOpt = BuiltInRegistries.ITEM.get(iid);
            if (itemOpt.isEmpty()) return;
            ItemStack itemStack = new ItemStack(itemOpt.get(), count);
            if (!player.getInventory().add(itemStack)) {
                // Drop on ground if inventory full
                ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), itemStack);
                player.level().addFreshEntity(drop);
            }
        };
    }

    private static EntityAction parseExplode(JsonObject json) {
        float power = json.has("power") ? json.get("power").getAsFloat() : 3.0f;
        boolean destructive = json.has("destruction_type")
            && !"none".equals(json.get("destruction_type").getAsString());
        boolean fire = json.has("create_fire") && json.get("create_fire").getAsBoolean();
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var blockInteraction = destructive
                ? Level.ExplosionInteraction.BLOCK
                : Level.ExplosionInteraction.NONE;
            sl.explode(player, player.getX(), player.getY(), player.getZ(), power,
                fire, blockInteraction);
        };
    }

    private static EntityAction parseLaunch(JsonObject json) {
        float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.0f;
        return player -> {
            player.push(0, speed, 0);
            player.hurtMarked = true;
        };
    }

    private static EntityAction parseSetBlock(JsonObject json) {
        String blockId = json.has("block") ? json.get("block").getAsString() : null;
        if (blockId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] set_block: missing block id — action will no-op");
            return EntityAction.noop();
        }
        Identifier bid = Identifier.parse(blockId);
        return player -> {
            var blockOpt = BuiltInRegistries.BLOCK.get(bid);
            if (blockOpt.isEmpty()) return;
            Block block = blockOpt.get().value();
            BlockPos pos = player.blockPosition();
            player.level().setBlock(pos, block.defaultBlockState(), 3);
        };
    }

    private static EntityAction parseAreaOfEffect(JsonObject json, String contextId) {
        // Simplified AoE: execute an action on the player (ignoring entity targeting)
        float radius = json.has("radius") ? json.get("radius").getAsFloat() : 5.0f;
        EntityAction action = json.has("entity_action")
            ? parse(json.getAsJsonObject("entity_action"), contextId) : EntityAction.noop();
        // [LOSSY] AoE only affects the player, not nearby entities
        return action;
    }

    private static EntityAction failNoop(String type, String contextId, String detail) {
        NeoOrigins.LOGGER.warn("[CompatB] action '{}' in {} defaulted to no-op: {}",
            type, contextId, detail);
        return CompatPolicy.NOOP_ACTION;
    }
}
