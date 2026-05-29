package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.compat.registry.ActionType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
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
        "neoorigins:equipped_item_action", "neoorigins:execute_command", "neoorigins:exhaust",
        "neoorigins:explode", "neoorigins:extinguish", "neoorigins:feed",
        "neoorigins:gain_air", "neoorigins:give", "neoorigins:grant_power",
        "neoorigins:heal", "neoorigins:if_else", "neoorigins:if_else_list",
        "neoorigins:ignite_attacker", "neoorigins:invert", "neoorigins:launch",
        "neoorigins:modify_food", "neoorigins:modify_inventory", "neoorigins:mount",
        "neoorigins:nothing", "neoorigins:offset", "neoorigins:passenger_action",
        "neoorigins:play_sound", "neoorigins:pull_entities", "neoorigins:random_teleport",
        "neoorigins:raycast", "neoorigins:remove_from_set", "neoorigins:revoke_power",
        "neoorigins:set_block", "neoorigins:set_fall_distance", "neoorigins:set_on_fire",
        "neoorigins:set_resource", "neoorigins:spawn_black_hole", "neoorigins:spawn_effect_cloud",
        "neoorigins:spawn_entity", "neoorigins:spawn_lingering_area",
        "neoorigins:spawn_projectile", "neoorigins:spawn_tornado",
        "neoorigins:swap_with_entity", "neoorigins:swing_hand", "neoorigins:target_action",
        "neoorigins:teleport_to_marker", "neoorigins:throw_target", "neoorigins:toggle",
        "neoorigins:trigger_cooldown", "neoorigins:kubejs_callback");

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
                case "neoorigins:apply_effect"                  -> parseApplyEffect(json);

                // ---- Phase 2: New actions ----
                case "neoorigins:area_of_effect"                -> parseAreaOfEffect(json, contextId);

                // ---- Item-state actions (interp expansion 2026-04-27) ----
                // Apoli's item-NBT machinery — required by advanced packs
                // (misch transfer rifle, MoR pixie wing toggles, origins-
                // plus-plus inventory mutations). Each operates on stacks
                // already in the player's inventory rather than on the
                // player directly.
                case "neoorigins:equipped_item_action"          -> parseEquippedItemAction(json);
                case "neoorigins:modify_inventory"              -> parseModifyInventory(json);
                case "neoorigins:raycast"                       -> parseRaycast(json, contextId);

                // ---- Phase 0/1: new actions for consolidation (active_ability) ----
                case "neoorigins:spawn_lingering_area"          -> parseSpawnLingeringArea(json, contextId);
                case "neoorigins:spawn_black_hole"              -> parseSpawnBlackHole(json, contextId);
                case "neoorigins:spawn_tornado"                 -> parseSpawnTornado(json, contextId);
                case "neoorigins:chain_to_nearest"              -> parseChainToNearest(json, contextId);
                case "neoorigins:pull_entities"                 -> parsePullEntities(json, contextId);

                default -> failNoop(type, contextId, "unsupported action type");
            };
        } catch (Exception e) {
            return failNoop(type, contextId, "parse error: " + e.getMessage());
        }
    }

    /**
     * {@code equipped_item_action}: read the stack in the named slot and run
     * an item-action against it. Apoli pack authors use this to mutate the
     * held weapon's NBT state — toggle modes, swap CustomModelData, etc. —
     * without needing one-off Java actions per behaviour.
     *
     * <p>Slot defaults to {@code mainhand}. Unknown slot strings warn once
     * and skip. The action is parsed once at load time via
     * {@link com.cyberday1.neoorigins.compat.action.ItemActionParser}; only
     * the slot lookup happens at dispatch.
     */
    private static EntityAction parseEquippedItemAction(JsonObject json) {
        String slotName = json.has("equipment_slot") ? json.get("equipment_slot").getAsString() : "mainhand";
        net.minecraft.world.entity.EquipmentSlot slot;
        try {
            slot = mapEquipmentSlot(slotName);
        } catch (IllegalArgumentException ex) {
            NeoOrigins.LOGGER.warn("[CompatB] equipped_item_action: unknown slot '{}' — no-op", slotName);
            return EntityAction.noop();
        }
        ItemAction action = json.has("action") && json.get("action").isJsonObject()
            ? ItemActionParser.parse(json.getAsJsonObject("action")) : ItemAction.noop();
        return player -> {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) return;
            action.execute(stack);
            // Mark slot dirty so vanilla resyncs the modified stack to the
            // client. Without this, NBT/component changes are server-side
            // only until something else triggers a refresh.
            player.containerMenu.broadcastChanges();
        };
    }

    /**
     * {@code modify_inventory}: walk the player's inventory, filter by an
     * optional item-condition, and run an item-action against each match
     * up to the configured limit. Apoli's catch-all for "find items, do
     * something to them" — used by the misch rifle to consume bullets,
     * by MoR pixie wing toggles to swap CustomModelData on a stack
     * already in inventory, etc.
     *
     * <p>Process modes:
     * <ul>
     *   <li>{@code "items"} (default) — count individual items (a stack of 5
     *       counts as 5 toward the limit)</li>
     *   <li>{@code "stacks"} — count stacks (a stack of 5 counts as 1)</li>
     * </ul>
     *
     * <p>{@code limit: 0} or unset means "no limit — apply to all matches".
     * Pack authors use {@code limit: 1} for "consume one bullet" patterns.
     */
    private static EntityAction parseModifyInventory(JsonObject json) {
        var itemCond = json.has("item_condition") && json.get("item_condition").isJsonObject()
            ? com.cyberday1.neoorigins.compat.condition.ItemConditionParser.parse(json.getAsJsonObject("item_condition"))
            : com.cyberday1.neoorigins.compat.condition.ItemCondition.alwaysTrue();
        ItemAction itemAction = json.has("item_action") && json.get("item_action").isJsonObject()
            ? ItemActionParser.parse(json.getAsJsonObject("item_action")) : ItemAction.noop();
        String processMode = json.has("process_mode") ? json.get("process_mode").getAsString() : "items";
        int limit = json.has("limit") ? json.get("limit").getAsInt() : 0;
        boolean countByItems = !"stacks".equalsIgnoreCase(processMode);
        // inventory_type is honoured loosely — vanilla only has one player
        // inventory; modded sub-inventories aren't reachable from here.
        // Pack authors generally pass "inventory" anyway, which is correct.
        return player -> {
            int applied = 0;
            var inv = player.getInventory();
            int total = inv.getContainerSize();
            for (int i = 0; i < total; i++) {
                if (limit > 0 && applied >= limit) break;
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                if (!itemCond.test(stack)) continue;
                int weight = countByItems ? stack.getCount() : 1;
                itemAction.execute(stack);
                applied += weight;
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
            if (applied > 0) player.containerMenu.broadcastChanges();
        };
    }

    /**
     * {@code raycast}: cast a ray from the player's eyes outward and run
     * an action when something is hit. Apoli action used for "look-and-
     * shoot" mechanics — misch rifle's cane-hit on block, ranged
     * interactions, etc.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code distance} — max ray length in blocks (default 10)</li>
     *   <li>{@code block} — whether to test for block collisions (default true)</li>
     *   <li>{@code entity} — whether to test for entity collisions (default false)</li>
     *   <li>{@code fluid_handling} — {@code none} / {@code source_only} / {@code any}
     *       (default none)</li>
     *   <li>{@code block_action} — entity_action run when a block is hit; the
     *       hit BlockPos is published to ActionContextHolder so
     *       sub-actions like execute_command can resolve {@code ~ ~ ~} to
     *       the block centre</li>
     *   <li>{@code bientity_action} — entity_action run when an entity is hit
     *       (the actor is the player; the hit entity becomes the dispatch
     *       target via ActionContextHolder)</li>
     *   <li>{@code miss_action} — runs when nothing is hit within range</li>
     * </ul>
     */
    private static EntityAction parseRaycast(JsonObject json, String contextId) {
        double distance = json.has("distance") ? json.get("distance").getAsDouble() : 10.0;
        boolean checkBlock = !json.has("block") || json.get("block").getAsBoolean();
        boolean checkEntity = json.has("entity") && json.get("entity").getAsBoolean();
        String fluidHandling = json.has("fluid_handling") ? json.get("fluid_handling").getAsString() : "none";
        net.minecraft.world.level.ClipContext.Fluid fluidMode = switch (fluidHandling.toLowerCase()) {
            case "any"          -> net.minecraft.world.level.ClipContext.Fluid.ANY;
            case "source_only"  -> net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY;
            default             -> net.minecraft.world.level.ClipContext.Fluid.NONE;
        };
        // Apoli {@code shape_type}: {@code visual} (default) uses the visual
        // outline shape — same as vanilla's eye-trace and what
        // PlayerInteractionManager uses for break/place targeting. {@code collider}
        // uses the collision shape, which is tighter than visual for
        // non-cube blocks (stairs, slabs, fences) — pack authors choose
        // collider when they want "what the hitbox sees" semantics.
        String shapeType = json.has("shape_type") ? json.get("shape_type").getAsString() : "visual";
        net.minecraft.world.level.ClipContext.Block blockShape = switch (shapeType.toLowerCase()) {
            case "collider"  -> net.minecraft.world.level.ClipContext.Block.COLLIDER;
            case "visual"    -> net.minecraft.world.level.ClipContext.Block.VISUAL;
            default          -> net.minecraft.world.level.ClipContext.Block.OUTLINE;
        };
        EntityAction blockAction = json.has("block_action") && json.get("block_action").isJsonObject()
            ? parse(json.getAsJsonObject("block_action"), contextId) : EntityAction.noop();
        EntityAction bientityAction = json.has("bientity_action") && json.get("bientity_action").isJsonObject()
            ? parse(json.getAsJsonObject("bientity_action"), contextId) : EntityAction.noop();
        EntityAction missAction = json.has("miss_action") && json.get("miss_action").isJsonObject()
            ? parse(json.getAsJsonObject("miss_action"), contextId) : EntityAction.noop();
        // {@code command_along_ray} + {@code command_step}: execute a command
        // at each {@code command_step}-block increment along the ray. Used by
        // packs for "trail of particles", "place a torch every N blocks",
        // etc. Step defaults to 1 block.
        String commandAlongRay = json.has("command_along_ray") ? json.get("command_along_ray").getAsString() : null;
        double commandStep = json.has("command_step") ? json.get("command_step").getAsDouble() : 1.0;
        if (commandStep <= 0) commandStep = 1.0; // guard against infinite loops on bad config
        final double finalStep = commandStep;
        return player -> {
            Vec3 from = player.getEyePosition(1.0F);
            Vec3 look = player.getViewVector(1.0F);
            Vec3 to = from.add(look.x * distance, look.y * distance, look.z * distance);

            // Run the along-ray command (if any) before bail-out so it fires
            // regardless of whether a block / entity is hit. Pack authors who
            // want hit-gated trails use block_action with execute_command.
            if (commandAlongRay != null && !commandAlongRay.isEmpty() && player.getServer() != null) {
                int steps = (int) Math.floor(distance / finalStep);
                var server = player.getServer();
                var commands = server.getCommands();
                for (int i = 1; i <= steps; i++) {
                    Vec3 stepPos = from.add(look.x * finalStep * i, look.y * finalStep * i, look.z * finalStep * i);
                    var src = player.createCommandSourceStack()
                        .withPosition(stepPos)
                        .withSuppressedOutput()
                        .withPermission(2);
                    try {
                        commands.performPrefixedCommand(src, commandAlongRay);
                    } catch (Exception e) {
                        com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                            "[CompatB] raycast {} command_along_ray step {} failed: {}",
                            contextId, i, e.getMessage());
                        break; // don't repeat the same failure for every remaining step
                    }
                }
            }

            if (checkEntity) {
                // Entity raycast: walk the AABB along the ray and find the
                // closest LivingEntity hit. Cheap server-side approximation
                // — vanilla's ProjectileUtil.getHitResultOnViewVector is
                // expensive and we don't need projectile-level precision.
                var aabb = player.getBoundingBox().expandTowards(look.scale(distance)).inflate(1.0);
                var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                    player, from, to, aabb,
                    e -> e instanceof net.minecraft.world.entity.LivingEntity && e != player,
                    distance * distance);
                if (entityHit != null && entityHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity le) {
                    Object prev = com.cyberday1.neoorigins.service.ActionContextHolder.set(
                        new com.cyberday1.neoorigins.service.EventPowerIndex.EntityInteractContext(le));
                    try { bientityAction.execute(player); }
                    finally { com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev); }
                    return;
                }
            }

            if (checkBlock) {
                var clipCtx = new net.minecraft.world.level.ClipContext(from, to,
                    blockShape, fluidMode, player);
                var hit = player.level().clip(clipCtx);
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    BlockPos pos = hit.getBlockPos();
                    // Publish a synthetic block-event-shaped context so
                    // execute_command's `~ ~ ~` resolves to the hit block.
                    Object prev = com.cyberday1.neoorigins.service.ActionContextHolder.set(
                        new RaycastBlockContext(pos));
                    try { blockAction.execute(player); }
                    finally { com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev); }
                    return;
                }
            }

            missAction.execute(player);
        };
    }

    /**
     * Synthetic context wrapper that {@link #extractCommandBlockPos} can
     * unwrap. Used by raycast when a block hit fires the block_action so
     * sub-actions (execute_command, drop_items) resolve {@code ~ ~ ~} to
     * the hit block. Defined as a simple record so equality / debug-print
     * are sane without extra ceremony.
     */
    public record RaycastBlockContext(BlockPos pos) {}

    private static net.minecraft.world.entity.EquipmentSlot mapEquipmentSlot(String slot) {
        return switch (slot.toLowerCase(java.util.Locale.ROOT)) {
            case "head"    -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chest"   -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "legs"    -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "feet"    -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case "offhand" -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case "mainhand", ""  -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            default -> throw new IllegalArgumentException("unknown slot: " + slot);
        };
    }

    // Package-private so the migrated execute_command / drop_items descriptors in
    // BuiltinActions can resolve the dispatch BlockPos identically (same pattern as
    // failNoop / extractBientityTarget).
    static net.minecraft.core.BlockPos extractCommandBlockPos(Object ctx) {
        if (ctx instanceof net.neoforged.neoforge.event.level.BlockEvent be) {
            return be.getPos();
        }
        if (ctx instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock rcb) {
            return rcb.getPos();
        }
        // Synthetic raycast block hit — published by parseRaycast so sub-
        // actions can use the hit BlockPos as their position origin.
        if (ctx instanceof RaycastBlockContext rbc) {
            return rbc.pos();
        }
        return null;
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

        if (effectId == null) return EntityAction.noop();
        // Cache mob effect holder at parse time — registry is static
        ResourceLocation effId = ResourceLocation.parse(effectId);
        var effectOpt = BuiltInRegistries.MOB_EFFECT.getOptional(effId);
        if (effectOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] apply_effect: unknown mob effect '{}' — action will no-op", effId);
            return EntityAction.noop();
        }
        var effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effectOpt.get());
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

    // ---- Phase 2: New action parsers ----

    private record FanoutEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> holder,
                                int duration, int amplifier, boolean ambient,
                                boolean particles, boolean icon) {}
    private record FanoutDamage(float amount, String sourceName) {}

    /**
     * Walk an AoE inner-action JSON tree and collect every {@code apply_effect} /
     * {@code damage} leaf as a fan-out task. Recurses through {@code and}/{@code or}
     * so wrappers like
     * <pre>{ "type": "neoorigins:and", "actions": [apply_effect, damage] }</pre>
     * still fan out to mobs. Accepts the {@code neoorigins:}, {@code origins:},
     * and {@code apace:} namespaces — without {@code neoorigins:} the four
     * rewritten projectile actives (Kraken Ink Shot, Revenant Void Bolt,
     * Sculkborn Sonic Bolt, Warden Sonic Boom) silently no-op against mobs
     * because their player-side EntityAction has no living-entity targets.
     */
    private static void collectFanoutTasks(JsonObject inner,
                                           java.util.List<FanoutEffect> effects,
                                           java.util.List<FanoutDamage> damages) {
        if (inner == null || !inner.has("type")) return;
        String t = inner.get("type").getAsString();
        switch (t) {
            case "neoorigins:and", "origins:and", "apace:and",
                 "neoorigins:or",  "origins:or",  "apace:or" -> {
                if (inner.has("actions") && inner.get("actions").isJsonArray()) {
                    for (var el : inner.getAsJsonArray("actions")) {
                        if (el.isJsonObject()) collectFanoutTasks(el.getAsJsonObject(), effects, damages);
                    }
                }
            }
            case "neoorigins:apply_effect", "origins:apply_effect", "apace:apply_effect" -> {
                String eid = inner.has("effect") ? inner.get("effect").getAsString()
                           : inner.has("effect_id") ? inner.get("effect_id").getAsString() : null;
                if (eid == null) return;
                var holderOpt = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .getOptional(net.minecraft.resources.ResourceLocation.parse(eid))
                    .map(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT::wrapAsHolder);
                if (holderOpt.isEmpty()) return;
                int dur = inner.has("duration") ? inner.get("duration").getAsInt() : 200;
                int amp = inner.has("amplifier") ? inner.get("amplifier").getAsInt() : 0;
                boolean amb = inner.has("is_ambient") && inner.get("is_ambient").getAsBoolean();
                boolean par = !inner.has("show_particles") || inner.get("show_particles").getAsBoolean();
                boolean ico = !inner.has("show_icon") || inner.get("show_icon").getAsBoolean();
                effects.add(new FanoutEffect(holderOpt.get(), dur, amp, amb, par, ico));
            }
            case "neoorigins:damage", "origins:damage", "apace:damage" -> {
                float amt = inner.has("amount") ? inner.get("amount").getAsFloat() : 1.0f;
                String src = inner.has("source") && inner.get("source").isJsonObject()
                    && inner.getAsJsonObject("source").has("name")
                    ? inner.getAsJsonObject("source").get("name").getAsString()
                    : "generic";
                damages.add(new FanoutDamage(amt, src));
            }
            default -> { /* not a fan-out leaf */ }
        }
    }

    private static EntityAction parseAreaOfEffect(JsonObject json, String contextId) {
        // AoE: run entity_action against every ServerPlayer within the radius,
        // and for any apply_effect / damage leaves found in the inner action tree
        // (recursing through and/or), ALSO apply the leaf to non-player mobs in
        // radius — otherwise powers like Inferno Burst, Kraken Ink Shot, etc.
        // have no combat impact because mobs are never EntityAction targets.
        //
        // [LOSSY] Other action verbs (launch, set_block, ...) still only affect
        // ServerPlayer targets — broadening EntityAction to LivingEntity is a
        // bigger refactor.
        float radius = json.has("radius") ? json.get("radius").getAsFloat() : 16.0f;
        String shape = json.has("shape") ? json.get("shape").getAsString() : "sphere";
        boolean includeSelf = !json.has("include_source") || json.get("include_source").getAsBoolean();

        JsonObject innerJson = json.has("entity_action") ? json.getAsJsonObject("entity_action") : null;
        EntityAction action = innerJson != null ? parse(innerJson, contextId) : EntityAction.noop();
        EntityCondition targetCondition = json.has("entity_condition")
            ? ConditionParser.parse(json.getAsJsonObject("entity_condition"), contextId)
            : EntityCondition.alwaysTrue();

        java.util.List<FanoutEffect> fanoutEffects = new java.util.ArrayList<>();
        java.util.List<FanoutDamage> fanoutDamages = new java.util.ArrayList<>();
        collectFanoutTasks(innerJson, fanoutEffects, fanoutDamages);
        final java.util.List<FanoutEffect> finalEffects = java.util.List.copyOf(fanoutEffects);
        final java.util.List<FanoutDamage> finalDamages = java.util.List.copyOf(fanoutDamages);

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

            if (!finalEffects.isEmpty() || !finalDamages.isEmpty()) {
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
                            && tame.getOwnerUUID() != null
                            && tame.getOwnerUUID().equals(casterUuid)) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectMinions()
                            && com.cyberday1.neoorigins.service.MinionTracker.isTrackedMinionOf(mob, casterUuid)) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectAnimals()
                            && mob instanceof net.minecraft.world.entity.animal.Animal) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectVillagers()
                            && mob instanceof net.minecraft.world.entity.npc.AbstractVillager) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectIronGolems()
                            && mob instanceof net.minecraft.world.entity.animal.IronGolem) continue;
                    for (var fx : finalEffects) {
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            fx.holder(), fx.duration(), fx.amplifier(),
                            fx.ambient(), fx.particles(), fx.icon()));
                    }
                    for (var dmg : finalDamages) {
                        if (dmg.amount() <= 0f) continue;
                        var dmgSrc = switch (dmg.sourceName()) {
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
                        mob.hurt(dmgSrc, dmg.amount());
                    }
                }
            }
        };
    }

    // ---- Phase 0: filled stubs ----


    // ---- Phase 0/1: new verbs (for active_ability consolidation) ----

    /** Parse {@code neoorigins:spawn_lingering_area}. See the 26.1 variant for field docs. */
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
        final ResourceLocation pid = ResourceLocation.parse(particleId);
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var particleTypeOpt = BuiltInRegistries.PARTICLE_TYPE.getOptional(pid);
            var particle = particleTypeOpt.isPresent()
                && particleTypeOpt.get() instanceof net.minecraft.core.particles.SimpleParticleType simple
                    ? simple
                    : net.minecraft.core.particles.ParticleTypes.WITCH;
            var entity = com.cyberday1.neoorigins.content.ModEntities.LINGERING_AREA.get().create(sl);
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
            entity.setIntervalTicks(intervalTicks);
            entity.setIntervalAction(intervalAction);
            entity.setParticleType(particle);
            entity.setCaster(player.getUUID());
            sl.addFreshEntity(entity);
        };
    }

    /**
     * Parse {@code neoorigins:spawn_black_hole}. See 26.1 twin for field docs.
     */
    private static EntityAction parseSpawnBlackHole(JsonObject json, String contextId) {
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 6.0f;
        final int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 100;
        final float pullStrength = json.has("pull_strength") ? json.get("pull_strength").getAsFloat() : 1.5f;
        final float damagePerTick = json.has("damage_per_tick") ? json.get("damage_per_tick").getAsFloat() : 2.0f;
        final String effectType = json.has("effect_type") ? json.get("effect_type").getAsString() : "";
        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var entity = com.cyberday1.neoorigins.content.ModEntities.BLACK_HOLE.get().create(sl);
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
     * Parse {@code neoorigins:spawn_tornado}. See 26.1 twin for field docs.
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
            var entity = com.cyberday1.neoorigins.content.ModEntities.TORNADO.get().create(sl);
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
     * Extract the bientity "target" entity from the current dispatch context.
     * Returns null outside any bientity-relevant context, causing entity-set mutators
     * to no-op silently. Mirrors {@code ConditionParser.extractTarget} — any context
     * shape that carries a target LivingEntity is honoured.
     */
    // Package-private so the migrated add_to_set / remove_from_set descriptors in
    // BuiltinActions can resolve the bientity target identically.
    static net.minecraft.world.entity.LivingEntity extractBientityTarget(Object ctx) {
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

    // Package-private so migrated descriptors in BuiltinActions can reproduce the
    // exact missing-required-field behaviour (records to CompatWarningCollector +
    // debug system message), rather than a bare EntityAction.noop() that would drop
    // the warning side-effect.
    static EntityAction failNoop(String type, String contextId, String detail) {
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
