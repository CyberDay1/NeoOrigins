package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.compat.registry.ActionType;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ActionParser {

    private ActionParser() {}

    /**
     * Canonical {@code neoorigins:} ids this parser's {@code switch} accepts —
     * the single source the 2.1 creator's action picker reads. Kept honest by
     * {@code SchemaFormCheck}, which re-derives the case labels from this
     * file's source and fails the build if this set drifts from the switch.
     */
    public static final java.util.Set<String> KNOWN_TYPES = java.util.Set.of(
        "neoorigins:actor_action", "neoorigins:add_to_set", "neoorigins:add_velocity",
        "neoorigins:add_xp", "neoorigins:and", "neoorigins:apply_effect",
        "neoorigins:area_of_effect", "neoorigins:block_action_at", "neoorigins:cancel_event",
        "neoorigins:chain_to_nearest", "neoorigins:chance", "neoorigins:change_resource",
        "neoorigins:choice", "neoorigins:clear_effect", "neoorigins:crafting_table",
        "neoorigins:damage", "neoorigins:damage_attacker", "neoorigins:dash",
        "neoorigins:delay", "neoorigins:dismount", "neoorigins:drop_items",
        "neoorigins:effect_on_attacker", "neoorigins:emit_game_event",
        "neoorigins:execute_command", "neoorigins:exhaust",
        "neoorigins:explode", "neoorigins:extinguish", "neoorigins:feed",
        "neoorigins:gain_air", "neoorigins:give", "neoorigins:grant_power",
        "neoorigins:heal", "neoorigins:if_else", "neoorigins:if_else_list",
        "neoorigins:ignite_attacker", "neoorigins:invert", "neoorigins:launch",
        "neoorigins:modify_food", "neoorigins:mount",
        "neoorigins:nothing", "neoorigins:offset", "neoorigins:passenger_action",
        "neoorigins:play_sound", "neoorigins:pull_entities", "neoorigins:random_teleport",
        "neoorigins:remove_from_set", "neoorigins:revoke_power",
        "neoorigins:set_block", "neoorigins:set_fall_distance", "neoorigins:set_on_fire",
        "neoorigins:set_resource", "neoorigins:spawn_black_hole", "neoorigins:spawn_effect_cloud",
        "neoorigins:spawn_entity", "neoorigins:spawn_lingering_area",
        "neoorigins:spawn_projectile", "neoorigins:spawn_tornado",
        "neoorigins:swap_with_entity", "neoorigins:swing_hand", "neoorigins:target_action",
        "neoorigins:teleport_to_marker", "neoorigins:throw_target", "neoorigins:toggle",
        "neoorigins:trigger_cooldown");

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
            // Registry-refactor migration (D1): verbs that have moved to a
            // registered descriptor dispatch here; the switch below holds only
            // the not-yet-migrated arms. Behaviour is identical — the factory is
            // the lift-and-shift of the old case body.
            ActionType descriptor = BuiltinActions.get(type);
            if (descriptor != null) {
                return descriptor.factory().create(json, contextId);
            }
            return switch (type) {
                case "neoorigins:and"                           -> parseAnd(json, contextId);
                case "neoorigins:if_else"                       -> parseIfElse(json, contextId);
                case "neoorigins:if_else_list"                  -> parseIfElseList(json, contextId);
                case "neoorigins:chance"                        -> parseChance(json, contextId);
                case "neoorigins:delay"                         -> parseDelay(json, contextId);
                case "neoorigins:execute_command"               -> parseExecuteCommand(json);
                case "neoorigins:apply_effect"                  -> parseApplyEffect(json);
                case "neoorigins:change_resource",
                     "neoorigins:modify_resource"               -> parseChangeResource(json);
                case "neoorigins:set_resource"                  -> parseSetResource(json);

                // ---- Phase 2: New actions ----
                case "neoorigins:spawn_entity"                  -> parseSpawnEntity(json);
                case "neoorigins:area_of_effect"                -> parseAreaOfEffect(json, contextId);
                case "neoorigins:drop_items"                    -> parseDropItems(json);

                // ---- Phase 0/1: new actions for consolidation (active_ability) ----
                case "neoorigins:spawn_projectile",
                     "neoorigins:fire_projectile"               -> parseSpawnProjectile(json, contextId);
                case "neoorigins:chain_to_nearest"              -> parseChainToNearest(json, contextId);
                case "neoorigins:pull_entities"                 -> parsePullEntities(json, contextId);
                case "neoorigins:throw_target"                  -> parseThrowTarget(json);
                case "neoorigins:swap_with_entity"              -> parseSwapWithEntity(json, contextId);

                // ---- Phase 6.5: context-aware verbs (read from ActionContextHolder) ----
                case "neoorigins:damage_attacker"               -> parseDamageAttacker(json);
                case "neoorigins:ignite_attacker"               -> parseIgniteAttacker(json);
                case "neoorigins:effect_on_attacker"            -> parseEffectOnAttacker(json);

                // ---- Entity-set verbs (mutate a named UUID set on the actor) ----
                case "neoorigins:add_to_set"                    -> parseAddToSet(json, contextId);
                case "neoorigins:remove_from_set"               -> parseRemoveFromSet(json, contextId);

                // ---- Toggle verb (flip a toggle-power's boolean state) ----
                case "neoorigins:toggle"                        -> parseToggle(json);

                // ---- Lingering-area VFX (spawns LingeringAreaEntity) ----
                case "neoorigins:spawn_lingering_area"          -> parseSpawnLingeringArea(json, contextId);
                case "neoorigins:spawn_black_hole"              -> parseSpawnBlackHole(json, contextId);
                case "neoorigins:spawn_tornado"                 -> parseSpawnTornado(json, contextId);

                // ---- Bientity→entity unwrappers ----
                // In Apoli these operate on (actor, target) pairs; in our model
                // the dispatch target is already the correct entity, so we just
                // unwrap the inner "action" field.
                case "neoorigins:target_action"                 -> parseTargetAction(json, contextId);
                case "neoorigins:actor_action"                  -> parseActorAction(json, contextId);

                // ---- Block-position delegate ----
                case "neoorigins:block_action_at"               -> parseBlockActionAt(json, contextId);

                // ---- Origins++ compat expansion ----
                case "neoorigins:choice"                        -> parseChoice(json, contextId);
                case "neoorigins:passenger_action"              -> parsePassengerAction(json, contextId);
                case "neoorigins:spawn_effect_cloud"            -> parseSpawnEffectCloud(json);
                case "neoorigins:offset"                        -> parseOffset(json, contextId);
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
                // If the dispatching event context carries a BlockPos
                // (BLOCK_BREAK, BLOCK_PLACE, BLOCK_USE), reposition the
                // command source at that block's centre so pack authors can
                // write `~ ~ ~` and have it mean "this block" — Apoli's
                // standard `block_action`-style pattern. Without this,
                // `~ ~ ~` is the player's position which is several blocks
                // off from the broken block.
                net.minecraft.core.BlockPos blockPos = extractCommandBlockPos(
                    com.cyberday1.neoorigins.service.ActionContextHolder.get());
                var pos = blockPos != null
                    ? net.minecraft.world.phys.Vec3.atCenterOf(blockPos)
                    : player.position();
                var serverSource = player.level().getServer().createCommandSourceStack()
                    .withSuppressedOutput()
                    .withEntity(player)
                    .withPosition(pos)
                    .withRotation(player.getRotationVector())
                    .withLevel((net.minecraft.server.level.ServerLevel) player.level());
                String finalCmd = com.cyberday1.neoorigins.compat.LegacyCommandRewriter.rewrite(command);
                player.level().getServer().getCommands().performPrefixedCommand(
                    serverSource, finalCmd
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

    private static EntityAction parseChangeResource(JsonObject json) {
        String resourceId = json.has("resource") ? json.get("resource").getAsString() : null;
        if (resourceId == null) return EntityAction.noop();

        String operation = json.has("operation") ? json.get("operation").getAsString() : "add";
        int change = json.has("change") ? json.get("change").getAsInt() : 0;

        final String key = resourceId;
        return switch (operation) {
            case "add" -> player -> {
                var meta = CompatAttachments.getResourceMeta(key);
                int lo = meta != null ? meta.min() : Integer.MIN_VALUE;
                int hi = meta != null ? meta.max() : Integer.MAX_VALUE;
                player.getData(CompatAttachments.resourceState()).clampedAdd(key, change, lo, hi);
            };
            case "set" -> player -> player.getData(CompatAttachments.resourceState()).set(key, change);
            default -> player -> {
                var meta = CompatAttachments.getResourceMeta(key);
                int lo = meta != null ? meta.min() : Integer.MIN_VALUE;
                int hi = meta != null ? meta.max() : Integer.MAX_VALUE;
                player.getData(CompatAttachments.resourceState()).clampedAdd(key, change, lo, hi);
            };
        };
    }

    private static EntityAction parseSetResource(JsonObject json) {
        String resourceId = json.has("resource") ? json.get("resource").getAsString() : null;
        if (resourceId == null) return EntityAction.noop();
        int value = json.has("value") ? json.get("value").getAsInt()
                   : json.has("change") ? json.get("change").getAsInt() : 0;
        final String key = resourceId;
        return player -> player.getData(CompatAttachments.resourceState()).set(key, value);
    }

    // ---- Phase 2: New action parsers ----

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
        // v2.1.6: optional `quantity` (integer >=1, default 1) — when >1 spawn N copies
        // with a small horizontal jitter (+/-0.5 block) so they don't stack at the
        // exact same point. Non-positive or non-integer values WARN and clamp to 1
        // (parser-canonical tolerance: bad data must not no-op a power that worked).
        int q = 1;
        if (json.has("quantity")) {
            JsonElement qEl = json.get("quantity");
            if (qEl.isJsonPrimitive() && qEl.getAsJsonPrimitive().isNumber()) {
                int requested = qEl.getAsInt();
                if (requested < 1) {
                    NeoOrigins.LOGGER.warn("[CompatB] spawn_entity: 'quantity' must be >=1 (got {}), clamping to 1", requested);
                } else {
                    q = requested;
                }
            } else {
                NeoOrigins.LOGGER.warn("[CompatB] spawn_entity: 'quantity' must be an integer (got {}), clamping to 1", qEl);
            }
        }
        final int quantity = q;
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            for (int i = 0; i < quantity; i++) {
                var entity = entityType.create(sl, EntitySpawnReason.COMMAND);
                if (entity == null) continue;
                double dx = 0.0, dz = 0.0;
                if (quantity > 1) {
                    // +/-0.5 block horizontal jitter so a stack of N entities
                    // doesn't visually merge into a single sprite at the spawn
                    // point. RandomSource.nextDouble() is [0,1) -> shift to [-0.5,0.5).
                    var rng = sl.getRandom();
                    dx = rng.nextDouble() - 0.5;
                    dz = rng.nextDouble() - 0.5;
                }
                entity.setPos(player.getX() + dx, player.getY(), player.getZ() + dz);
                sl.addFreshEntity(entity);
            }
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
        final boolean applyEffectAmbient;
        final boolean applyEffectParticles;
        final boolean applyEffectIcon;
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
            applyEffectAmbient = innerJson.has("is_ambient") && innerJson.get("is_ambient").getAsBoolean();
            applyEffectParticles = !innerJson.has("show_particles") || innerJson.get("show_particles").getAsBoolean();
            applyEffectIcon = !innerJson.has("show_icon") || innerJson.get("show_icon").getAsBoolean();
        } else {
            applyEffectHolder = null;
            applyEffectDuration = 0;
            applyEffectAmplifier = 0;
            applyEffectAmbient = false;
            applyEffectParticles = true;
            applyEffectIcon = true;
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
                java.util.UUID casterUuid = source.getUUID();
                var mobCandidates = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb);
                for (var mob : mobCandidates) {
                    if (mob instanceof net.minecraft.server.level.ServerPlayer) continue;
                    if (mob == source) continue;
                    if ("sphere".equalsIgnoreCase(finalShape)
                            && mob.position().distanceToSqr(srcPos) > r2) continue;
                    // Friendly-fire filter — each category is independently
                    // configurable via [friendly_fire] in neoorigins-common.toml.
                    // Defaults: pets/minions/villagers/iron golems protected;
                    // passive animals (sheep, cow, pig, ...) NOT protected so
                    // active combat AOEs (Hiveling Sting, Inferno Burst, ...)
                    // can actually hit livestock.
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectOwnedPets()
                            && mob instanceof net.minecraft.world.entity.TamableAnimal tame
                            && tame.getOwnerReference() != null
                            && tame.getOwnerReference().getUUID().equals(casterUuid)) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectMinions()
                            && com.cyberday1.neoorigins.service.MinionTracker.isTrackedMinionOf(mob, casterUuid)) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectAnimals()
                            && mob instanceof net.minecraft.world.entity.animal.Animal) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectVillagers()
                            && mob instanceof net.minecraft.world.entity.npc.villager.AbstractVillager) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectIronGolems()
                            && mob instanceof net.minecraft.world.entity.animal.golem.IronGolem) continue;
                    if (applyEffectHolder != null) {
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            applyEffectHolder, applyEffectDuration, applyEffectAmplifier,
                            applyEffectAmbient, applyEffectParticles, applyEffectIcon));
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
        // Apoli uses "divergence"; NeoOrigins uses "inaccuracy" — accept both.
        final float inaccuracy = json.has("inaccuracy") ? json.get("inaccuracy").getAsFloat()
            : json.has("divergence") ? json.get("divergence").getAsFloat() : 0f;
        final float verticalOffset = json.has("vertical_offset") ? json.get("vertical_offset").getAsFloat() : 0f;
        // Optional on_hit_action: stored on ProjectileActionRegistry keyed by the
        // spawned projectile's UUID. Fires from CombatPowerEvents.onProjectileImpact
        // with the ProjectileHitContext installed so area_of_effect can center on
        // the impact point rather than the (by-then-stale) player position.
        // Apoli uses "projectile_action"; NeoOrigins uses "on_hit_action" — accept both.
        JsonObject hitActionJson = json.has("on_hit_action") ? json.getAsJsonObject("on_hit_action")
            : json.has("projectile_action") ? json.getAsJsonObject("projectile_action") : null;
        final EntityAction onHitAction = hitActionJson != null
            ? parse(hitActionJson, contextId) : null;
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

    /**
     * Hurls the entity the caster is looking at away from the caster + upward.
     * Horizontal direction is the XZ vector from caster to target (so a target
     * directly overhead still gets thrown sideways via the caster's look-yaw
     * fallback). Force is split into a horizontal magnitude and a separate
     * vertical "lift" so packs can tune throw arc independently of distance.
     */
    private static EntityAction parseThrowTarget(JsonObject json) {
        final float force         = json.has("force")         ? json.get("force").getAsFloat()         : 1.5f;
        final float verticalLift  = json.has("vertical_lift") ? json.get("vertical_lift").getAsFloat() : 0.5f;
        final float maxDistance   = json.has("max_distance")  ? json.get("max_distance").getAsFloat()  : 5.0f;
        return player -> {
            var eye  = player.getEyePosition();
            var look = player.getLookAngle();
            var end  = eye.add(look.scale(maxDistance));
            // Inflate the AABB along the look ray + a small lateral pad so a
            // partially-occluded target still resolves under the crosshair.
            var searchBox = player.getBoundingBox().expandTowards(look.scale(maxDistance)).inflate(1.0);
            // 26.1 ProjectileUtil.getEntityHitResult signature is
            // (Entity, Vec3, Vec3, AABB, Predicate, double) — the shooter
            // entity replaces the Level argument and the trailing double is a
            // squared-distance filter. 1.21.1 had (Level, Entity, ...) without
            // the distance arg. Pass maxDistance² so the filter rejects hits
            // beyond the configured raycast length.
            var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, searchBox,
                e -> e != player && e.isAlive() && e instanceof net.minecraft.world.entity.LivingEntity,
                (double) maxDistance * maxDistance);
            if (hit == null) return;
            if (!(hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity target)) return;
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz < 1.0e-4) {
                // Target sitting on top of caster — fall back to caster's
                // facing yaw so the throw still has a horizontal direction.
                dx = look.x;
                dz = look.z;
                horiz = Math.sqrt(dx * dx + dz * dz);
                if (horiz < 1.0e-4) { dx = 1.0; dz = 0.0; horiz = 1.0; }
            }
            double ux = dx / horiz;
            double uz = dz / horiz;
            target.push(ux * force, verticalLift, uz * force);
            target.hurtMarked = true;
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
    /** Flip (or set, if `value` is given) the toggle state for the named power id. */
    private static EntityAction parseToggle(JsonObject json) {
        String powerId = json.has("power") ? json.get("power").getAsString() : null;
        if (powerId == null || powerId.isBlank()) {
            return failNoop("neoorigins:toggle", "root", "missing 'power' field");
        }
        final Boolean explicit = json.has("value") ? json.get("value").getAsBoolean() : null;
        final String key = powerId;
        return player -> {
            if (explicit != null) com.cyberday1.neoorigins.compat.Toggles.setOn(player, key, explicit);
            else com.cyberday1.neoorigins.compat.Toggles.flip(player, key);
        };
    }

    /**
     * Drop one or more item stacks at the current dispatch position. Inline
     * alternative to authoring a vanilla loot table — pack authors who want
     * "5% chance to drop a diamond when you break stone" don't need a
     * separate {@code data/.../loot_table/...json} file.
     *
     * <p>Two modes via the {@code mode} field:
     * <ul>
     *   <li>{@code "each"} (default) — every entry rolls independently. Each
     *       entry's {@code chance} is consulted; multiple items can drop in
     *       one trigger.</li>
     *   <li>{@code "one_of"} — exactly one entry is picked, weighted by each
     *       entry's {@code weight} field (default 1). Mirrors a single
     *       vanilla loot pool with {@code rolls: 1}. Optional top-level
     *       {@code rolls} repeats the pick with replacement (default 1).
     *       Per-entry {@code chance} is ignored in this mode.</li>
     * </ul>
     *
     * <p>Per-entry fields: {@code item} (required), {@code count} (int or
     * {@code [min, max]} range, default 1), {@code chance} (float 0-1,
     * default 1.0, "each" mode only), {@code weight} (int, default 1,
     * "one_of" mode only). Drop position uses
     * {@link #extractCommandBlockPos} — block-event dispatches drop at the
     * broken/placed/used block, other dispatches drop at the player.
     */
    private static EntityAction parseDropItems(JsonObject json) {
        if (!json.has("items") || !json.get("items").isJsonArray()) return EntityAction.noop();
        record Drop(net.minecraft.core.Holder<net.minecraft.world.item.Item> item,
                    int countMin, int countMax, float chance, int weight) {}
        List<Drop> drops = new ArrayList<>();
        for (JsonElement el : json.getAsJsonArray("items")) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("item")) continue;
            Identifier itemId = Identifier.parse(obj.get("item").getAsString());
            var resolved = BuiltInRegistries.ITEM.get(itemId).orElse(null);
            if (resolved == null) {
                NeoOrigins.LOGGER.warn("[CompatB] drop_items: unknown item '{}' — skipped", itemId);
                continue;
            }
            int countMin = 1, countMax = 1;
            if (obj.has("count")) {
                JsonElement cEl = obj.get("count");
                if (cEl.isJsonArray() && cEl.getAsJsonArray().size() >= 2) {
                    countMin = cEl.getAsJsonArray().get(0).getAsInt();
                    countMax = cEl.getAsJsonArray().get(1).getAsInt();
                    if (countMax < countMin) { int t = countMin; countMin = countMax; countMax = t; }
                } else if (cEl.isJsonPrimitive()) {
                    countMin = countMax = cEl.getAsInt();
                }
            }
            float chance = obj.has("chance") ? obj.get("chance").getAsFloat() : 1.0f;
            int weight = Math.max(1, obj.has("weight") ? obj.get("weight").getAsInt() : 1);
            drops.add(new Drop(resolved, countMin, countMax, chance, weight));
        }
        if (drops.isEmpty()) return EntityAction.noop();

        String mode = json.has("mode") ? json.get("mode").getAsString().toLowerCase(java.util.Locale.ROOT) : "each";
        int rolls = Math.max(1, json.has("rolls") ? json.get("rolls").getAsInt() : 1);
        boolean oneOf = "one_of".equals(mode) || "single".equals(mode) || "weighted".equals(mode);
        int totalWeight = 0;
        for (Drop d : drops) totalWeight += d.weight();
        final int finalTotalWeight = totalWeight;
        final int finalRolls = rolls;
        return player -> {
            BlockPos blockPos = extractCommandBlockPos(
                com.cyberday1.neoorigins.service.ActionContextHolder.get());
            Vec3 spawn = blockPos != null
                ? Vec3.atCenterOf(blockPos)
                : player.position().add(0, 0.5, 0);
            var random = player.getRandom();

            if (oneOf) {
                for (int r = 0; r < finalRolls; r++) {
                    int roll = random.nextInt(finalTotalWeight);
                    int cum = 0;
                    for (Drop d : drops) {
                        cum += d.weight();
                        if (roll < cum) {
                            int count = d.countMin() == d.countMax() ? d.countMin()
                                : d.countMin() + random.nextInt(d.countMax() - d.countMin() + 1);
                            if (count > 0) {
                                ItemEntity entity = new ItemEntity(player.level(),
                                    spawn.x, spawn.y, spawn.z, new ItemStack(d.item(), count));
                                entity.setDefaultPickUpDelay();
                                player.level().addFreshEntity(entity);
                            }
                            break;
                        }
                    }
                }
            } else {
                for (Drop d : drops) {
                    if (d.chance() < 1.0f && random.nextFloat() >= d.chance()) continue;
                    int count = d.countMin() == d.countMax() ? d.countMin()
                        : d.countMin() + random.nextInt(d.countMax() - d.countMin() + 1);
                    if (count <= 0) continue;
                    ItemEntity entity = new ItemEntity(player.level(),
                        spawn.x, spawn.y, spawn.z, new ItemStack(d.item(), count));
                    entity.setDefaultPickUpDelay();
                    player.level().addFreshEntity(entity);
                }
            }
        };
    }

    /**
     * Pull a BlockPos out of the current dispatch context if it's a
     * block-shaped event. Returns null otherwise — callers fall back to
     * the player position (entity-shaped events, raw onTick, etc.).
     */
    /**
     * Synthetic context published by {@link #parseBlockActionAt} and raycast
     * block-hit dispatch so nested actions resolve {@code ~ ~ ~} to the
     * target block rather than the player position.
     */
    public record RaycastBlockContext(BlockPos pos) {}

    private static net.minecraft.core.BlockPos extractCommandBlockPos(Object ctx) {
        if (ctx instanceof RaycastBlockContext rbc) {
            return rbc.pos();
        }
        if (ctx instanceof net.neoforged.neoforge.event.level.BlockEvent be) {
            return be.getPos();
        }
        if (ctx instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock rcb) {
            return rcb.getPos();
        }
        return null;
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

    /**
     * Bientity unwrapper: in Apoli {@code target_action} extracts the target
     * from a (actor, target) pair and runs {@code action} on it. In our model
     * the dispatch target is already the correct entity when called from
     * {@code area_of_effect}'s bientity_action path, so we just delegate
     * to the inner action.
     */
    private static EntityAction parseTargetAction(JsonObject json, String contextId) {
        EntityAction inner = json.has("action") && json.get("action").isJsonObject()
            ? parse(json.getAsJsonObject("action"), contextId) : EntityAction.noop();
        return inner;
    }

    /**
     * Bientity unwrapper: {@code actor_action} runs the inner {@code action}
     * on the actor (source player). In our AoE dispatch the source is the
     * player who invoked the power — we just run the action on them.
     */
    private static EntityAction parseActorAction(JsonObject json, String contextId) {
        EntityAction inner = json.has("action") && json.get("action").isJsonObject()
            ? parse(json.getAsJsonObject("action"), contextId) : EntityAction.noop();
        return inner;
    }

    /**
     * Runs a {@code block_action} at the entity's current block position.
     * Publishes the block pos to {@link com.cyberday1.neoorigins.service.ActionContextHolder}
     * so nested actions like {@code execute_command} can resolve {@code ~ ~ ~}
     * to the block centre.
     */
    private static EntityAction parseBlockActionAt(JsonObject json, String contextId) {
        EntityAction blockAction = json.has("block_action") && json.get("block_action").isJsonObject()
            ? parse(json.getAsJsonObject("block_action"), contextId) : EntityAction.noop();
        return player -> {
            BlockPos pos = player.blockPosition();
            Object prev = com.cyberday1.neoorigins.service.ActionContextHolder.set(
                new RaycastBlockContext(pos));
            try {
                blockAction.execute(player);
            } finally {
                com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev);
            }
        };
    }

    // ── Origins++ compat actions ────────────────────────────────────────

    /** origins:choice — randomly select and execute one action from a list based on weights. */
    private static EntityAction parseChoice(JsonObject json, String ctx) {
        if (!json.has("actions") || !json.get("actions").isJsonArray()) return EntityAction.noop();
        JsonArray arr = json.getAsJsonArray("actions");
        List<EntityAction> actions = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int totalWeight = 0;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject entry = el.getAsJsonObject();
            EntityAction action = entry.has("action") && entry.get("action").isJsonObject()
                ? parse(entry.getAsJsonObject("action"), ctx) : EntityAction.noop();
            int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
            actions.add(action);
            weights.add(weight);
            totalWeight += weight;
        }
        if (actions.isEmpty()) return EntityAction.noop();
        final int fTotal = totalWeight;
        final List<EntityAction> fActions = List.copyOf(actions);
        final List<Integer> fWeights = List.copyOf(weights);
        return player -> {
            int roll = player.getRandom().nextInt(fTotal);
            int cumulative = 0;
            for (int i = 0; i < fActions.size(); i++) {
                cumulative += fWeights.get(i);
                if (roll < cumulative) { fActions.get(i).execute(player); return; }
            }
            fActions.getLast().execute(player);
        };
    }

    /** origins:mount — make the player ride the nearest entity of the given type. */
    /** origins:passenger_action — execute an action on all passengers of the player. */
    private static EntityAction parsePassengerAction(JsonObject json, String ctx) {
        EntityAction inner = json.has("action") && json.get("action").isJsonObject()
            ? parse(json.getAsJsonObject("action"), ctx) : EntityAction.noop();
        // Also check "entity_action" field (Apoli uses both names)
        if (inner == EntityAction.noop() && json.has("entity_action") && json.get("entity_action").isJsonObject()) {
            inner = parse(json.getAsJsonObject("entity_action"), ctx);
        }
        final EntityAction fInner = inner;
        return player -> {
            for (var passenger : player.getPassengers()) {
                if (passenger instanceof net.minecraft.server.level.ServerPlayer sp) {
                    fInner.execute(sp);
                }
            }
        };
    }

    /** origins:spawn_effect_cloud — spawn an AreaEffectCloud at the player's position. */
    private static EntityAction parseSpawnEffectCloud(JsonObject json) {
        // Effect can be a string ("minecraft:wither") or an object ({ "effect": "...", "duration": N })
        String effectId = null;
        int duration = json.has("duration") ? json.get("duration").getAsInt() : 200;
        int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;
        if (json.has("effect")) {
            JsonElement effectEl = json.get("effect");
            if (effectEl.isJsonPrimitive()) {
                effectId = effectEl.getAsString();
            } else if (effectEl.isJsonObject()) {
                JsonObject effectObj = effectEl.getAsJsonObject();
                effectId = effectObj.has("effect") ? effectObj.get("effect").getAsString() : null;
                if (effectObj.has("duration")) duration = effectObj.get("duration").getAsInt();
                if (effectObj.has("amplifier")) amplifier = effectObj.get("amplifier").getAsInt();
            }
        }
        float radius = json.has("radius") ? json.get("radius").getAsFloat() : 3.0f;
        int waitTime = json.has("wait_time") ? json.get("wait_time").getAsInt() : 10;
        final String fEffectId = effectId;
        final int fDuration = duration;
        final int fAmplifier = amplifier;
        return player -> {
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
            var cloud = new net.minecraft.world.entity.AreaEffectCloud(sl, player.getX(), player.getY(), player.getZ());
            cloud.setRadius(radius);
            cloud.setDuration(fDuration);
            cloud.setWaitTime(waitTime);
            cloud.setOwner(player);
            if (fEffectId != null) {
                var eff = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(Identifier.parse(fEffectId));
                if (eff.isPresent()) {
                    var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(eff.get());
                    cloud.addEffect(new net.minecraft.world.effect.MobEffectInstance(holder, fDuration, fAmplifier));
                }
            }
            sl.addFreshEntity(cloud);
        };
    }

    /** origins:offset — apply an action at an offset position from the player. */
    private static EntityAction parseOffset(JsonObject json, String ctx) {
        EntityAction inner = json.has("action") && json.get("action").isJsonObject()
            ? parse(json.getAsJsonObject("action"), ctx) : EntityAction.noop();
        double x = json.has("x") ? json.get("x").getAsDouble() : 0;
        double y = json.has("y") ? json.get("y").getAsDouble() : 0;
        double z = json.has("z") ? json.get("z").getAsDouble() : 0;
        // For block_action_at offsets, the "action" is typically a block action.
        // We execute the inner entity action at the offset position by temporarily moving the player.
        // In practice most uses just want to place/break a block at an offset — we handle it
        // by running the inner action as-is (it reads player.blockPosition() or similar).
        final EntityAction fInner = inner;
        return fInner; // Pass through — offset is architectural in Apoli, our actions already read position from context
    }

    /** origins:add_xp — grant experience points or levels. */
    private static EntityAction failNoop(String type, String contextId, String detail) {
        com.cyberday1.neoorigins.compat.CompatWarningCollector
            .recordUnsupportedAction(type, contextId, detail);
        final String finalType = type;
        final String finalContextId = contextId;
        return player -> {
            if (com.cyberday1.neoorigins.NeoOriginsConfig.isDebugCompatActions()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[NeoOrigins Compat Debug] Action '" + finalType + "' in " + finalContextId + " is unsupported (no-op)")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        };
    }
}
