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
        // Canonicalize: bare names default to neoorigins:; legacy origins:/apace:
        // prefixes get a one-shot [2.0-legacy] warning then are rewritten to
        // neoorigins: for dispatch. Canonical switch arms below are neoorigins:*.
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
                case "neoorigins:if_else"                       -> parseIfElse(json, contextId);
                case "neoorigins:if_else_list"                  -> parseIfElseList(json, contextId);
                case "neoorigins:chance"                        -> parseChance(json, contextId);
                case "neoorigins:delay"                         -> parseDelay(json, contextId);
                case "neoorigins:execute_command"               -> parseExecuteCommand(json);
                case "neoorigins:apply_effect"                  -> parseApplyEffect(json);
                case "neoorigins:clear_effect"                  -> parseClearEffect(json);
                case "neoorigins:heal"                          -> parseHeal(json);
                case "neoorigins:play_sound"                    -> parsePlaySound(json);
                case "neoorigins:add_velocity"                  -> parseAddVelocity(json);
                case "neoorigins:set_on_fire"                   -> parseSetOnFire(json);
                case "neoorigins:exhaust"                       -> parseExhaust(json);
                case "neoorigins:change_resource"               -> parseChangeResource(json);
                case "neoorigins:nothing"                       -> EntityAction.noop();

                // ---- Phase 2: New actions ----
                case "neoorigins:damage"                        -> parseDamage(json);
                case "neoorigins:feed"                          -> parseFeed(json);
                case "neoorigins:trigger_cooldown"              -> parseTriggerCooldown(json);
                case "neoorigins:gain_air"                      -> parseGainAir(json);
                case "neoorigins:spawn_entity"                  -> parseSpawnEntity(json);
                case "neoorigins:set_fall_distance"             -> parseSetFallDistance(json);
                case "neoorigins:extinguish"                    -> player -> player.clearFire();
                case "neoorigins:dismount"                      -> player -> player.stopRiding();
                case "neoorigins:give"                          -> parseGive(json);
                case "neoorigins:explode"                       -> parseExplode(json);
                case "neoorigins:launch"                        -> parseLaunch(json);
                case "neoorigins:set_block"                     -> parseSetBlock(json);
                case "neoorigins:area_of_effect"                -> parseAreaOfEffect(json, contextId);
                case "neoorigins:modify_food"                   -> parseModifyFood(json);
                case "neoorigins:grant_power"                   -> parseGrantPower(json);
                case "neoorigins:revoke_power"                  -> parseRevokePower(json);
                case "neoorigins:emit_game_event"               -> parseEmitGameEvent(json);
                case "neoorigins:swing_hand"                    -> player -> player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

                // ---- Phase 0/1: new actions for consolidation (active_ability) ----
                case "neoorigins:spawn_projectile"              -> parseSpawnProjectile(json, contextId);
                case "neoorigins:chain_to_nearest"              -> parseChainToNearest(json, contextId);
                case "neoorigins:pull_entities"                 -> parsePullEntities(json, contextId);
                case "neoorigins:swap_with_entity"              -> parseSwapWithEntity(json, contextId);
                case "neoorigins:teleport_to_marker"            -> parseTeleportToMarker(json);

                // ---- Phase 6.5: context-aware verbs (read from ActionContextHolder) ----
                case "neoorigins:damage_attacker"               -> parseDamageAttacker(json);
                case "neoorigins:ignite_attacker"               -> parseIgniteAttacker(json);
                case "neoorigins:effect_on_attacker"            -> parseEffectOnAttacker(json);
                case "neoorigins:random_teleport"               -> parseRandomTeleport(json);
                case "neoorigins:cancel_event"                  -> parseCancelEvent();

                // ---- Entity-set verbs (mutate a named UUID set on the actor) ----
                case "neoorigins:add_to_set"                    -> parseAddToSet(json, contextId);
                case "neoorigins:remove_from_set"               -> parseRemoveFromSet(json, contextId);

                // ---- Toggle verb (flip a toggle-power's boolean state) ----
                case "neoorigins:toggle"                        -> parseToggle(json);

                // ---- Lingering-area VFX (spawns LingeringAreaEntity) ----
                case "neoorigins:spawn_lingering_area"          -> parseSpawnLingeringArea(json, contextId);
                case "neoorigins:spawn_black_hole"              -> parseSpawnBlackHole(json, contextId);
                case "neoorigins:spawn_tornado"                 -> parseSpawnTornado(json, contextId);

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
            // Without hurtMarked=true the client keeps simulating its own
            // physics locally and the server's velocity change is lost at the
            // next movement packet. Active launch / dash / wind_charge all
            // depend on this flag to actually move the player.
            player.hurtMarked = true;
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
        // AoE: run entity_action against every ServerPlayer within the radius.
        // For the common {@code apply_effect} inner action, ALSO apply the same
        // MobEffect to non-player {@link net.minecraft.world.entity.LivingEntity}
        // mobs in radius — otherwise powers like Fire Mage's Inferno Burst
        // (Instant Damage AoE) have no combat impact because mobs are never
        // the target of an EntityAction.
        //
        // [LOSSY] Other actions (damage, launch, set_block, etc.) still only
        // affect ServerPlayer targets — broadening the full compat layer to
        // LivingEntity is a bigger refactor.
        float radius = json.has("radius") ? json.get("radius").getAsFloat() : 16.0f;
        String shape = json.has("shape") ? json.get("shape").getAsString() : "sphere";
        boolean includeSelf = !json.has("include_source") || json.get("include_source").getAsBoolean();

        JsonObject innerJson = json.has("entity_action") ? json.getAsJsonObject("entity_action") : null;
        EntityAction action = innerJson != null ? parse(innerJson, contextId) : EntityAction.noop();
        EntityCondition targetCondition = json.has("entity_condition")
            ? ConditionParser.parse(json.getAsJsonObject("entity_condition"), contextId)
            : EntityCondition.alwaysTrue();

        // Pre-resolve apply_effect shape so the hot path doesn't re-parse JSON.
        final String innerType = innerJson != null && innerJson.has("type")
            ? innerJson.get("type").getAsString() : "";
        final boolean innerIsApplyEffect =
            "origins:apply_effect".equals(innerType)
            || "apace:apply_effect".equals(innerType);
        final boolean innerIsDamage =
            "origins:damage".equals(innerType)
            || "apace:damage".equals(innerType);
        final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> applyEffectHolder;
        final int applyEffectDuration;
        final int applyEffectAmplifier;
        if (innerIsApplyEffect) {
            String eid = innerJson.has("effect") ? innerJson.get("effect").getAsString()
                        : innerJson.has("effect_id") ? innerJson.get("effect_id").getAsString() : null;
            var eidOpt = eid == null
                ? java.util.Optional.<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>>empty()
                : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .get(net.minecraft.resources.Identifier.parse(eid));
            applyEffectHolder = eidOpt.orElse(null);
            applyEffectDuration = innerJson.has("duration") ? innerJson.get("duration").getAsInt() : 200;
            applyEffectAmplifier = innerJson.has("amplifier") ? innerJson.get("amplifier").getAsInt() : 0;
        } else {
            applyEffectHolder = null;
            applyEffectDuration = 0;
            applyEffectAmplifier = 0;
        }
        // origins:damage mob-fan-out
        final float damageAmount;
        final String damageSourceName;
        if (innerIsDamage) {
            damageAmount = innerJson.has("amount") ? innerJson.get("amount").getAsFloat() : 1.0f;
            damageSourceName = innerJson.has("source") && innerJson.get("source").isJsonObject()
                && innerJson.getAsJsonObject("source").has("name")
                ? innerJson.getAsJsonObject("source").get("name").getAsString()
                : "generic";
        } else {
            damageAmount = 0f;
            damageSourceName = "";
        }

        final float  finalRadius       = radius;
        final boolean finalIncludeSelf = includeSelf;
        final String  finalShape       = shape;
        final EntityAction finalAction = action;
        final EntityCondition finalCond = targetCondition;

        return source -> {
            var level = source.level();
            double r = finalRadius;
            // Center at impact point when invoked from a spawn_projectile on_hit_action —
            // the projectile-impact dispatcher installs a ProjectileHitContext on the
            // ActionContextHolder whose result.getLocation() is the real impact point.
            // Otherwise center on the source (player) as before.
            net.minecraft.world.phys.Vec3 srcPos;
            net.minecraft.world.phys.AABB aabb;
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc) {
                srcPos = phc.result().getLocation();
                aabb = new net.minecraft.world.phys.AABB(srcPos.subtract(r, r, r), srcPos.add(r, r, r));
            } else {
                srcPos = source.position();
                aabb = source.getBoundingBox().inflate(r);
            }
            double r2 = r * r;

            var playerCandidates = level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, aabb);
            for (var target : playerCandidates) {
                if (target == source && !finalIncludeSelf) continue;
                if ("sphere".equalsIgnoreCase(finalShape)
                        && target.position().distanceToSqr(srcPos) > r2) continue;
                if (!finalCond.test(target)) continue;
                finalAction.execute(target);
            }

            if (applyEffectHolder != null || innerIsDamage) {
                var mobCandidates = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb);
                for (var mob : mobCandidates) {
                    if (mob instanceof net.minecraft.server.level.ServerPlayer) continue;
                    if (mob == source) continue;
                    if ("sphere".equalsIgnoreCase(finalShape)
                            && mob.position().distanceToSqr(srcPos) > r2) continue;
                    if (applyEffectHolder != null) {
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            applyEffectHolder, applyEffectDuration, applyEffectAmplifier));
                    }
                    if (innerIsDamage && damageAmount > 0f) {
                        var dmgSrc = switch (damageSourceName) {
                            case "fire", "on_fire", "in_fire" -> mob.level().damageSources().onFire();
                            case "lava"   -> mob.level().damageSources().lava();
                            case "magic"  -> mob.level().damageSources().magic();
                            case "drown"  -> mob.level().damageSources().drown();
                            case "freeze" -> mob.level().damageSources().freeze();
                            case "wither" -> mob.level().damageSources().wither();
                            default       -> source instanceof net.minecraft.server.level.ServerPlayer sp
                                ? mob.level().damageSources().playerAttack(sp)
                                : mob.level().damageSources().generic();
                        };
                        mob.hurt(dmgSrc, damageAmount);
                    }
                }
            }
        };
    }

    // ---- Phase 0: filled stubs ----

    private static EntityAction parseModifyFood(JsonObject json) {
        // Apoli modify_food only applies in the context of an `action_on_item_use`
        // style hook where a food item is being consumed. In our action context we
        // have no item-stack reference, so the best we can do is apply a one-shot
        // food/saturation adjustment to the player right now.
        int foodDelta = json.has("food") ? json.get("food").getAsInt()
                      : json.has("food_component_food") ? json.get("food_component_food").getAsInt()
                      : 0;
        float satDelta = json.has("saturation") ? json.get("saturation").getAsFloat()
                       : json.has("food_component_saturation") ? json.get("food_component_saturation").getAsFloat()
                       : 0.0f;
        return player -> {
            var food = player.getFoodData();
            int newFood = Math.max(0, Math.min(20, food.getFoodLevel() + foodDelta));
            float newSat = Math.max(0f, Math.min(newFood, food.getSaturationLevel() + satDelta));
            food.setFoodLevel(newFood);
            food.setSaturation(newSat);
        };
    }

    private static EntityAction parseGrantPower(JsonObject json) {
        String powerId = json.has("power") ? json.get("power").getAsString()
                       : json.has("power_id") ? json.get("power_id").getAsString() : null;
        if (powerId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] grant_power: missing power id — action will no-op");
            return EntityAction.noop();
        }
        final Identifier pid = Identifier.parse(powerId);
        return player -> {
            var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            if (data.hasDynamicGrant(pid)) return;
            var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(pid);
            if (holder == null) {
                NeoOrigins.LOGGER.warn("[CompatB] grant_power: unknown power '{}'", pid);
                return;
            }
            // Skip onGranted callback if already granted via an origin (avoid double-grant);
            // we still record the dynamic flag so revoke can clean up if the origin changes.
            boolean fromOrigin = false;
            for (var entry : data.getOrigins().entrySet()) {
                var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(entry.getValue());
                if (origin != null && origin.powers().contains(pid)) { fromOrigin = true; break; }
            }
            if (fromOrigin) {
                data.addDynamicGrant(pid);
                return;
            }
            if (data.addDynamicGrant(pid)) {
                holder.onGranted(player);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new com.cyberday1.neoorigins.api.event.PowerGrantedEvent(player, pid));
                com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncToPlayer(player);
            }
        };
    }

    private static EntityAction parseRevokePower(JsonObject json) {
        String powerId = json.has("power") ? json.get("power").getAsString()
                       : json.has("power_id") ? json.get("power_id").getAsString() : null;
        if (powerId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] revoke_power: missing power id — action will no-op");
            return EntityAction.noop();
        }
        final Identifier pid = Identifier.parse(powerId);
        return player -> {
            var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            if (!data.hasDynamicGrant(pid)) return;
            var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(pid);
            if (data.removeDynamicGrant(pid) && holder != null) {
                // Only call onRevoked if the power isn't still granted by an origin.
                boolean stillGranted = false;
                for (var entry : data.getOrigins().entrySet()) {
                    var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(entry.getValue());
                    if (origin != null && origin.powers().contains(pid)) { stillGranted = true; break; }
                }
                if (!stillGranted) {
                    holder.onRevoked(player);
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new com.cyberday1.neoorigins.api.event.PowerRevokedEvent(player, pid));
                }
                com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncToPlayer(player);
            }
        };
    }

    private static EntityAction parseEmitGameEvent(JsonObject json) {
        String eventId = json.has("event") ? json.get("event").getAsString()
                       : json.has("game_event") ? json.get("game_event").getAsString() : null;
        if (eventId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] emit_game_event: missing event id — action will no-op");
            return EntityAction.noop();
        }
        Identifier eid = Identifier.parse(eventId);
        var evOpt = BuiltInRegistries.GAME_EVENT.get(eid);
        if (evOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] emit_game_event: unknown event '{}' — action will no-op", eid);
            return EntityAction.noop();
        }
        final var gameEvent = evOpt.get();
        return player -> player.level().gameEvent(player, gameEvent, player.position());
    }

    // ---- Phase 0/1: new verbs (for active_ability consolidation) ----

    private static EntityAction parseSpawnProjectile(JsonObject json, String contextId) {
        String entityId = json.has("entity_type") ? json.get("entity_type").getAsString()
                        : json.has("projectile") ? json.get("projectile").getAsString() : null;
        if (entityId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] spawn_projectile: missing entity_type/projectile — no-op");
            return EntityAction.noop();
        }
        Identifier eid = Identifier.parse(entityId);
        var entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.get(eid);
        if (entityTypeOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] spawn_projectile: unknown entity '{}' — no-op", eid);
            return EntityAction.noop();
        }
        final EntityType<?> entityType = entityTypeOpt.get().value();
        final float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.5f;
        final float inaccuracy = json.has("inaccuracy") ? json.get("inaccuracy").getAsFloat() : 0f;
        final float verticalOffset = json.has("vertical_offset") ? json.get("vertical_offset").getAsFloat() : 0f;
        // Optional on_hit_action: stored on ProjectileActionRegistry keyed by the
        // spawned projectile's UUID. Fires from CombatPowerEvents.onProjectileImpact
        // with the ProjectileHitContext installed so area_of_effect can center on
        // the impact point rather than the (by-then-stale) player position.
        final EntityAction onHitAction = json.has("on_hit_action") && json.get("on_hit_action").isJsonObject()
            ? parse(json.getAsJsonObject("on_hit_action"), contextId)
            : null;
        // Optional effect_type: when spawning a MagicOrbProjectile (or any entity
        // with a compatible effect_type field), set the synched data so the
        // client-side renderer picks the right color palette.
        final String effectType = json.has("effect_type")
            ? json.get("effect_type").getAsString() : null;
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var entity = entityType.create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (entity == null) return;
            entity.setPos(player.getX(), player.getEyeY() + verticalOffset, player.getZ());
            if (entity instanceof com.cyberday1.neoorigins.content.MagicOrbProjectile orb && effectType != null) {
                orb.setEffectType(effectType);
            }
            if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                proj.setOwner(player);
                proj.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, speed, inaccuracy);
            } else {
                var look = player.getLookAngle();
                entity.setDeltaMovement(look.x * speed, look.y * speed, look.z * speed);
            }
            sl.addFreshEntity(entity);
            if (onHitAction != null) {
                com.cyberday1.neoorigins.service.ProjectileActionRegistry.register(
                    entity.getUUID(), onHitAction, player.tickCount);
            }
        };
    }

    /**
     * Parse {@code neoorigins:spawn_lingering_area} — spawn a
     * {@link com.cyberday1.neoorigins.content.LingeringAreaEntity} at the
     * actor's current position that ticks a stored action at intervals.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code radius} (float, default 3.0) — in-blocks radius, synched to client</li>
     *   <li>{@code duration_ticks} (int, default 100) — how long the area persists</li>
     *   <li>{@code interval_ticks} (int, default 20) — how often the action fires</li>
     *   <li>{@code effect_type} (string, default "") — color key for renderer hook</li>
     *   <li>{@code entity_action} (object, optional) — the action run each interval against the caster</li>
     *   <li>{@code particle_type} (string, default "minecraft:witch") — particle emitted each tick</li>
     * </ul>
     */
    private static EntityAction parseSpawnLingeringArea(JsonObject json, String contextId) {
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 3.0f;
        final int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 100;
        final int intervalTicks = json.has("interval_ticks") ? json.get("interval_ticks").getAsInt() : 20;
        final String effectType = json.has("effect_type") ? json.get("effect_type").getAsString() : "";
        final EntityAction intervalAction = json.has("entity_action") && json.get("entity_action").isJsonObject()
            ? parse(json.getAsJsonObject("entity_action"), contextId)
            : null;
        final String particleId = json.has("particle_type")
            ? json.get("particle_type").getAsString() : "minecraft:witch";
        final Identifier pid = Identifier.parse(particleId);
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var particleTypeOpt = BuiltInRegistries.PARTICLE_TYPE.get(pid);
            var particle = particleTypeOpt.isPresent()
                && particleTypeOpt.get().value() instanceof net.minecraft.core.particles.SimpleParticleType simple
                    ? simple
                    : net.minecraft.core.particles.ParticleTypes.WITCH;
            var entity = com.cyberday1.neoorigins.content.ModEntities.LINGERING_AREA.get()
                .create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (entity == null) return;
            // Position the area at the impact point when invoked from an on_hit_action,
            // otherwise at the caster's feet.
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc) {
                var pos = phc.result().getLocation();
                entity.setPos(pos.x, pos.y, pos.z);
            } else {
                entity.setPos(player.getX(), player.getY(), player.getZ());
            }
            entity.setRange(radius);
            entity.setEffectType(effectType);
            entity.setMaxLifetime(durationTicks);
            entity.setIntervalTicks(intervalTicks);
            entity.setIntervalAction(intervalAction);
            entity.setParticleType(particle);
            entity.setCaster(player.getUUID());
            sl.addFreshEntity(entity);
        };
    }

    /**
     * Parse {@code neoorigins:spawn_black_hole} — spawn a
     * {@link com.cyberday1.neoorigins.content.BlackHoleVfxEntity} that pulls
     * nearby entities toward its center and damages anything in the inner
     * radius on an interval.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code radius} (float, default 6.0) — outer pull radius (inner damage is 30% of this)</li>
     *   <li>{@code duration_ticks} (int, default 100) — lifetime in ticks</li>
     *   <li>{@code pull_strength} (float, default 1.5) — inward force multiplier</li>
     *   <li>{@code damage_per_tick} (float, default 2.0) — damage applied every 10 ticks in inner radius</li>
     *   <li>{@code effect_type} (string, default "") — color key for renderer hook</li>
     * </ul>
     *
     * <p>Spawns at the impact point when invoked from an on_hit_action; at
     * the caster's position otherwise.
     */
    private static EntityAction parseSpawnBlackHole(JsonObject json, String contextId) {
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 6.0f;
        final int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 100;
        final float pullStrength = json.has("pull_strength") ? json.get("pull_strength").getAsFloat() : 1.5f;
        final float damagePerTick = json.has("damage_per_tick") ? json.get("damage_per_tick").getAsFloat() : 2.0f;
        final String effectType = json.has("effect_type") ? json.get("effect_type").getAsString() : "";
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var entity = com.cyberday1.neoorigins.content.ModEntities.BLACK_HOLE.get()
                .create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (entity == null) return;
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc) {
                var pos = phc.result().getLocation();
                entity.setPos(pos.x, pos.y, pos.z);
            } else {
                entity.setPos(player.getX(), player.getY(), player.getZ());
            }
            entity.setRange(radius);
            entity.setEffectType(effectType);
            entity.setMaxLifetime(durationTicks);
            entity.setPullStrength(pullStrength);
            entity.setDamagePerTick(damagePerTick);
            entity.setCaster(player.getUUID());
            sl.addFreshEntity(entity);
        };
    }

    /**
     * Parse {@code neoorigins:spawn_tornado} — spawn a
     * {@link com.cyberday1.neoorigins.content.TornadoVfxEntity} that pulls,
     * lifts, and spins entities, with optional damage on an interval.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code radius} (float, default 5.0) — horizontal influence radius</li>
     *   <li>{@code duration_ticks} (int, default 100) — lifetime in ticks</li>
     *   <li>{@code pull_strength} (float, default 1.0) — inward force</li>
     *   <li>{@code lift_strength} (float, default 0.5) — upward force</li>
     *   <li>{@code spin_strength} (float, default 0.5) — tangential force</li>
     *   <li>{@code damage_per_interval} (float, default 2.0) — damage every N ticks (0 disables)</li>
     *   <li>{@code damage_interval_ticks} (int, default 10) — how often damage fires</li>
     *   <li>{@code effect_type} (string, default "") — color key for future renderer hooks</li>
     * </ul>
     */
    private static EntityAction parseSpawnTornado(JsonObject json, String contextId) {
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 5.0f;
        final int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 100;
        final float pullStrength = json.has("pull_strength") ? json.get("pull_strength").getAsFloat() : 1.0f;
        final float liftStrength = json.has("lift_strength") ? json.get("lift_strength").getAsFloat() : 0.5f;
        final float spinStrength = json.has("spin_strength") ? json.get("spin_strength").getAsFloat() : 0.5f;
        final float damagePerInterval = json.has("damage_per_interval") ? json.get("damage_per_interval").getAsFloat() : 2.0f;
        final int damageIntervalTicks = json.has("damage_interval_ticks") ? json.get("damage_interval_ticks").getAsInt() : 10;
        final String effectType = json.has("effect_type") ? json.get("effect_type").getAsString() : "";
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var entity = com.cyberday1.neoorigins.content.ModEntities.TORNADO.get()
                .create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (entity == null) return;
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc) {
                var pos = phc.result().getLocation();
                entity.setPos(pos.x, pos.y, pos.z);
            } else {
                entity.setPos(player.getX(), player.getY(), player.getZ());
            }
            entity.setRange(radius);
            entity.setEffectType(effectType);
            entity.setMaxLifetime(durationTicks);
            entity.setPullStrength(pullStrength);
            entity.setLiftStrength(liftStrength);
            entity.setSpinStrength(spinStrength);
            entity.setDamagePerInterval(damagePerInterval);
            entity.setDamageIntervalTicks(damageIntervalTicks);
            entity.setCaster(player.getUUID());
            sl.addFreshEntity(entity);
        };
    }

    private static EntityAction parseChainToNearest(JsonObject json, String contextId) {
        // Pull the player toward the nearest entity matching `entity_condition` (default: any living).
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 16f;
        final float speed  = json.has("speed")  ? json.get("speed").getAsFloat()  : 1.0f;
        EntityCondition playerCond = json.has("target_condition")
            ? ConditionParser.parse(json.getAsJsonObject("target_condition"), contextId)
            : EntityCondition.alwaysTrue();
        final EntityCondition targetCond = playerCond;
        return player -> {
            var level = player.level();
            var aabb = player.getBoundingBox().inflate(radius);
            var candidates = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb,
                e -> e != player && e.isAlive());
            net.minecraft.world.entity.LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            var origin = player.position();
            for (var e : candidates) {
                if (e instanceof net.minecraft.server.level.ServerPlayer sp && !targetCond.test(sp)) continue;
                double d = e.position().distanceToSqr(origin);
                if (d < bestDist) { bestDist = d; best = e; }
            }
            if (best == null) return;
            var dir = best.position().subtract(origin).normalize();
            player.setDeltaMovement(dir.x * speed, dir.y * speed + 0.1, dir.z * speed);
            player.hurtMarked = true;
        };
    }

    private static EntityAction parsePullEntities(JsonObject json, String contextId) {
        // Pull nearby entities toward the caster.
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 8f;
        final float strength = json.has("strength") ? json.get("strength").getAsFloat() : 0.5f;
        final boolean includePlayers = !json.has("include_players") || json.get("include_players").getAsBoolean();
        EntityCondition targetCond = json.has("entity_condition")
            ? ConditionParser.parse(json.getAsJsonObject("entity_condition"), contextId)
            : EntityCondition.alwaysTrue();
        final EntityCondition fCond = targetCond;
        return player -> {
            var level = player.level();
            var aabb = player.getBoundingBox().inflate(radius);
            var candidates = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb,
                e -> e != player && e.isAlive());
            var origin = player.position();
            for (var e : candidates) {
                if (!includePlayers && e instanceof net.minecraft.world.entity.player.Player) continue;
                if (e instanceof net.minecraft.server.level.ServerPlayer sp && !fCond.test(sp)) continue;
                var dir = origin.subtract(e.position()).normalize();
                e.push(dir.x * strength, dir.y * strength + 0.1, dir.z * strength);
                e.hurtMarked = true;
            }
        };
    }

    private static EntityAction parseSwapWithEntity(JsonObject json, String contextId) {
        // Swap positions with the nearest matching entity in radius.
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 16f;
        EntityCondition tgtCond = json.has("target_condition")
            ? ConditionParser.parse(json.getAsJsonObject("target_condition"), contextId)
            : EntityCondition.alwaysTrue();
        final EntityCondition fCond = tgtCond;
        return player -> {
            var level = player.level();
            var aabb = player.getBoundingBox().inflate(radius);
            var candidates = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb,
                e -> e != player && e.isAlive());
            net.minecraft.world.entity.LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            var origin = player.position();
            for (var e : candidates) {
                if (e instanceof net.minecraft.server.level.ServerPlayer sp && !fCond.test(sp)) continue;
                double d = e.position().distanceToSqr(origin);
                if (d < bestDist) { bestDist = d; best = e; }
            }
            if (best == null) return;
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            float pyaw = player.getYRot(), ppitch = player.getXRot();
            player.teleportTo(best.getX(), best.getY(), best.getZ());
            player.setYRot(best.getYRot());
            player.setXRot(best.getXRot());
            best.teleportTo(px, py, pz);
            best.setYRot(pyaw);
            best.setXRot(ppitch);
        };
    }

    private static EntityAction parseTeleportToMarker(JsonObject json) {
        // Teleport to a named marker stored on the player. Markers are keyed by a string
        // and stored as session state on the player's PlayerOriginData shadowOrbs list analogue;
        // for now, support absolute coordinates under "position" or named offset "dx"/"dy"/"dz".
        final double dx = json.has("dx") ? json.get("dx").getAsDouble() : 0;
        final double dy = json.has("dy") ? json.get("dy").getAsDouble() : 0;
        final double dz = json.has("dz") ? json.get("dz").getAsDouble() : 0;
        final boolean absolute = json.has("position");
        final double px = absolute ? json.getAsJsonObject("position").get("x").getAsDouble() : 0;
        final double py = absolute ? json.getAsJsonObject("position").get("y").getAsDouble() : 0;
        final double pz = absolute ? json.getAsJsonObject("position").get("z").getAsDouble() : 0;
        return player -> {
            if (absolute) {
                player.teleportTo(px, py, pz);
            } else {
                player.teleportTo(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
            }
        };
    }

    // ---- Phase 6.5: context-aware verbs ----
    //
    // These verbs read the currently-dispatched event context from
    // ActionContextHolder. EventPowerIndex.dispatch publishes the context
    // while running handlers for a given event, so at action-execution time
    // the holder carries the right record (HitTakenContext, FoodContext, ...).

    /**
     * Hurt the attacker recorded in the current HIT_TAKEN context.
     *
     * <p>Damage amount resolution order:
     * <ol>
     *   <li>If {@code amount_ratio} is present, damage = {@code htc.amount() * amount_ratio}
     *       (minimum 0.5 so very-small incoming hits still draw some blood).</li>
     *   <li>Otherwise use the fixed {@code amount} field (default 2.0).</li>
     * </ol>
     * The ratio path is how {@code thorns_aura}'s alias reflects a fraction of
     * the incoming damage instead of a flat number.
     */
    private static EntityAction parseDamageAttacker(JsonObject json) {
        final float amount = json.has("amount") ? json.get("amount").getAsFloat() : 2.0f;
        final boolean useRatio = json.has("amount_ratio");
        final float ratio = useRatio ? json.get("amount_ratio").getAsFloat() : 0f;
        final String srcName = json.has("source") && json.get("source").isJsonObject()
            && json.getAsJsonObject("source").has("name")
            ? json.getAsJsonObject("source").get("name").getAsString()
            : "magic";
        return player -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc)) return;
            var attacker = htc.source().getEntity();
            if (!(attacker instanceof net.minecraft.world.entity.LivingEntity le)) return;
            var ds = switch (srcName) {
                case "fire", "on_fire", "in_fire" -> player.level().damageSources().onFire();
                case "lava"   -> player.level().damageSources().lava();
                case "magic"  -> player.level().damageSources().magic();
                case "generic" -> player.level().damageSources().generic();
                default       -> player.level().damageSources().magic();
            };
            float dmg = useRatio ? Math.max(0.5f, htc.amount() * ratio) : amount;
            if (!Float.isFinite(dmg)) dmg = Float.MAX_VALUE;
            le.hurt(ds, dmg);
        };
    }

    /** Set the current HIT_TAKEN attacker on fire. */
    private static EntityAction parseIgniteAttacker(JsonObject json) {
        final int ticks = json.has("ticks") ? json.get("ticks").getAsInt() : 60;
        return player -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc)) return;
            var attacker = htc.source().getEntity();
            if (attacker == null) return;
            attacker.setRemainingFireTicks(ticks);
        };
    }

    /** Apply a mob effect to the current HIT_TAKEN attacker. */
    private static EntityAction parseEffectOnAttacker(JsonObject json) {
        String effectId = json.has("effect") ? json.get("effect").getAsString() : null;
        if (effectId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] effect_on_attacker: missing effect id — no-op");
            return EntityAction.noop();
        }
        final int duration = json.has("duration") ? json.get("duration").getAsInt() : 100;
        final int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;
        var effectHolder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId)).orElse(null);
        if (effectHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] effect_on_attacker: unknown effect '{}' — no-op", effectId);
            return EntityAction.noop();
        }
        return player -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            if (!(ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc)) return;
            var attacker = htc.source().getEntity();
            if (!(attacker instanceof net.minecraft.world.entity.LivingEntity le)) return;
            le.addEffect(new MobEffectInstance(effectHolder, duration, amplifier));
        };
    }

    /** Random-teleport the player within a bounded box. */
    private static EntityAction parseRandomTeleport(JsonObject json) {
        final double hRange = json.has("horizontal_range") ? json.get("horizontal_range").getAsDouble()
                            : json.has("range") ? json.get("range").getAsDouble() : 16.0;
        final double vRange = json.has("vertical_range") ? json.get("vertical_range").getAsDouble() : 8.0;
        final int attempts = json.has("attempts") ? json.get("attempts").getAsInt() : 16;
        return player -> {
            if (!(player.level() instanceof ServerLevel level)) return;
            var rng = player.getRandom();
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            for (int i = 0; i < attempts; i++) {
                double tx = px + (rng.nextDouble() - 0.5) * hRange * 2;
                double ty = py + (rng.nextDouble() - 0.5) * vRange * 2;
                double tz = pz + (rng.nextDouble() - 0.5) * hRange * 2;
                ty = Math.max(level.getMinY(), Math.min(level.getMaxY() - 2, ty));
                BlockPos target = BlockPos.containing(tx, ty, tz);
                if (level.getBlockState(target).isAir() && level.getBlockState(target.above()).isAir()) {
                    player.teleportTo(tx, ty, tz);
                    return;
                }
            }
        };
    }

    /** Cancel the current dispatch if its context is an ICancellableEvent. */
    private static EntityAction parseCancelEvent() {
        return player -> {
            Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            // Some contexts wrap the cancellable event — unwrap those first.
            if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext fc
                && fc.event() != null) {
                fc.event().setCanceled(true);
                return;
            }
            if (ctx instanceof net.neoforged.bus.api.ICancellableEvent ce) {
                ce.setCanceled(true);
            }
        };
    }

    /** Flip (or set, if `value` is given) the toggle state for the named power id. */
    private static EntityAction parseToggle(JsonObject json) {
        String powerId = json.has("power") ? json.get("power").getAsString() : null;
        if (powerId == null || powerId.isBlank()) {
            return failNoop("neoorigins:toggle", "root", "missing 'power' field");
        }
        final Boolean explicit = json.has("value") ? json.get("value").getAsBoolean() : null;
        final String key = powerId;
        return player -> {
            var state = player.getData(com.cyberday1.neoorigins.compat.CompatAttachments.toggleState());
            if (explicit != null) state.set(key, explicit);
            else state.toggle(key, false);
        };
    }

    /**
     * Extract the bientity "target" entity from the current dispatch context.
     * Returns null outside any bientity-relevant context, causing entity-set mutators
     * to no-op silently. Mirrors {@code ConditionParser.extractTarget} — any context
     * shape that carries a target LivingEntity is honoured.
     */
    private static net.minecraft.world.entity.LivingEntity extractBientityTarget(Object ctx) {
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

    /**
     * Add the current bientity target's UUID to the actor player's named entity-set.
     * No-op if no bientity context is active or the {@code set} field is missing.
     */
    private static EntityAction parseAddToSet(JsonObject json, String contextId) {
        String setName = json.has("set") ? json.get("set").getAsString() : null;
        if (setName == null || setName.isBlank()) {
            return failNoop("neoorigins:add_to_set", contextId, "missing required field 'set'");
        }
        final String key = setName;
        return player -> {
            var le = extractBientityTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return;
            var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            data.addToEntitySet(player, key, le.getUUID());
        };
    }

    /**
     * Remove the current bientity target's UUID from the actor player's named entity-set.
     * No-op if no bientity context is active or the {@code set} field is missing.
     */
    private static EntityAction parseRemoveFromSet(JsonObject json, String contextId) {
        String setName = json.has("set") ? json.get("set").getAsString() : null;
        if (setName == null || setName.isBlank()) {
            return failNoop("neoorigins:remove_from_set", contextId, "missing required field 'set'");
        }
        final String key = setName;
        return player -> {
            var le = extractBientityTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
            if (le == null) return;
            var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
            data.removeFromEntitySet(player, key, le.getUUID());
        };
    }

    private static EntityAction failNoop(String type, String contextId, String detail) {
        NeoOrigins.LOGGER.warn("[CompatB] action '{}' in {} defaulted to no-op: {}",
            type, contextId, detail);
        return CompatPolicy.NOOP_ACTION;
    }
}
