package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.compat.registry.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
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
        "neoorigins:area_of_effect", "neoorigins:block_action_at", "neoorigins:block_target_action",
        "neoorigins:cancel_event",
        "neoorigins:chain_to_nearest", "neoorigins:chance", "neoorigins:change_resource",
        "neoorigins:choice", "neoorigins:clear_effect", "neoorigins:crafting_table",
        "neoorigins:damage", "neoorigins:damage_attacker", "neoorigins:dash",
        "neoorigins:delay", "neoorigins:dismount", "neoorigins:drop_inventory",
        "neoorigins:drop_items", "neoorigins:dye",
        "neoorigins:force_drop", "neoorigins:shear", "neoorigins:steal_item",
        "neoorigins:strip", "neoorigins:till", "neoorigins:path", "neoorigins:grow",
        "neoorigins:transform_block",
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
        "neoorigins:riding_action", "neoorigins:selector_action", "neoorigins:spawn_particles",
        "neoorigins:set_block", "neoorigins:set_fall_distance", "neoorigins:set_on_fire",
        "neoorigins:set_resource", "neoorigins:spawn_black_hole", "neoorigins:spawn_effect_cloud",
        "neoorigins:spawn_entity", "neoorigins:spawn_lingering_area",
        "neoorigins:spawn_projectile", "neoorigins:spawn_tornado",
        "neoorigins:swap_positions", "neoorigins:swap_with_entity", "neoorigins:swing_hand",
        "neoorigins:target_action", "neoorigins:teleport_target_to_self",
        "neoorigins:teleport_to_marker", "neoorigins:teleport_to_target",
        "neoorigins:throw_target", "neoorigins:toggle",
        "neoorigins:trigger_cooldown");

    public static EntityAction parse(JsonObject json, String contextId) {
        if (json == null) {
            return failNoop("root", contextId, "missing action object");
        }
        String type = json.has("type") ? json.get("type").getAsString() : "";
        // Canonicalize: bare names default to neoorigins:; legacy origins:/apace:/apoli:
        // prefixes (the Origins/Apoli ecosystem aliases — these verbs share schemas)
        // get a one-shot [2.0-legacy] warning then are rewritten to neoorigins: for
        // dispatch. Canonical switch arms below are neoorigins:*. Without apoli: here,
        // packs that nest apoli:-namespaced verbs inside origins: powers (e.g. deanos
        // apoli:and / apoli:raycast / apoli:change_resource) fell through to no-op
        // even though the identical neoorigins: handler exists.
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "neoorigins:" + type;
        } else if (!type.isEmpty() && !type.startsWith("neoorigins:")) {
            // Generic namespace fallback for any non-canonical prefix
            // (origins:, apace:, apoli:, apugli:, medievalorigins:, ...) —
            // rewrite to neoorigins:<leaf> and dispatch. Earlier versions
            // whitelisted origins/apace/apoli only, silently no-opping actions
            // from other Apoli-derivative namespaces.
            String canonical = "neoorigins:" + type.substring(type.indexOf(':') + 1);
            com.cyberday1.neoorigins.compat.LegacyVerbWarning.warn(type, canonical);
            type = canonical;
        }
        try {
            // Registry-refactor migration (D1): verbs that have moved to a
            // registered descriptor dispatch here; the switch below holds only
            // the not-yet-migrated arms. Behaviour is identical — the factory is
            // the lift-and-shift of the old case body.
            // Registry-refactor migration complete (D1): every built-in action verb
            // is now a registered descriptor (see BuiltinActions). The former
            // type-switch is retired — dispatch is a single descriptor lookup, and
            // an unknown verb falls through to the unsupported-action no-op (which
            // records a CompatWarningCollector entry, preserving the old default
            // arm's behaviour). Addon-contributed verbs resolve through the same
            // BuiltinActions.get path once their descriptors are registered.
            ActionType descriptor = BuiltinActions.get(type);
            if (descriptor != null) {
                return descriptor.factory().create(json, contextId);
            }
            return failNoop(type, contextId, "unsupported action type");
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
    static EntityAction parseEquippedItemAction(JsonObject json) {
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
    static EntityAction parseModifyInventory(JsonObject json) {
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
    static EntityAction parseRaycast(JsonObject json, String contextId) {
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
        String commandAlongRay = forceParticleVisibility(
            json.has("command_along_ray") ? json.get("command_along_ray").getAsString() : null);
        double commandStep = json.has("command_step") ? json.get("command_step").getAsDouble() : 1.0;
        if (commandStep <= 0) commandStep = 1.0; // guard against infinite loops on bad config
        final double finalStep = commandStep;
        // {@code before_action}: an entity_action run once, up-front, before the
        // ray is cast — deanos spells use it to consume the offhand reagent (the
        // ender pearl in Teleport) regardless of what the ray hits.
        // {@code command_at_hit}: the command executed at the impact point when a
        // block or entity is hit — this is the spell payload ("tp @s ~ ~ ~",
        // "/Explosion @s ..."). Both sit alongside the already-supported
        // command_along_ray / command_step deanos raycast extensions.
        final EntityAction beforeAction = json.has("before_action") && json.get("before_action").isJsonObject()
            ? parse(json.getAsJsonObject("before_action"), contextId) : EntityAction.noop();
        final String commandAtHit = forceParticleVisibility(
            json.has("command_at_hit") ? json.get("command_at_hit").getAsString() : null);
        return player -> {
            beforeAction.execute(player);
            Vec3 from = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 to = from.add(look.x * distance, look.y * distance, look.z * distance);

            // Run the along-ray command (if any) before bail-out so it fires
            // regardless of whether a block / entity is hit. Pack authors who
            // want hit-gated trails use block_action with execute_command.
            if (commandAlongRay != null && !commandAlongRay.isEmpty()
                    && player.level().getServer() != null
                    && player.level() instanceof ServerLevel serverLevel
                    && !commandBlocked(commandAlongRay, contextId + " command_along_ray")) {
                int steps = (int) Math.floor(distance / finalStep);
                var server = player.level().getServer();
                var commands = server.getCommands();
                for (int i = 1; i <= steps; i++) {
                    Vec3 stepPos = from.add(look.x * finalStep * i, look.y * finalStep * i, look.z * finalStep * i);
                    var src = server.createCommandSourceStack()
                        .withSuppressedOutput()
                        .withEntity(player)
                        .withPosition(stepPos)
                        .withRotation(player.getRotationVector())
                        .withLevel(serverLevel);
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
                    try {
                        bientityAction.execute(player);
                        runCommandAt(player, commandAtHit, le.position(), contextId);
                    }
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
                    try {
                        blockAction.execute(player);
                        // command_at_hit runs at the precise impact point (not the
                        // block centre) so "tp @s ~ ~ ~" lands exactly where you aimed.
                        runCommandAt(player, commandAtHit, hit.getLocation(), contextId);
                    }
                    finally { com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev); }
                    return;
                }
            }

            missAction.execute(player);
        };
    }

    /**
     * Origins packs commonly use {@code "command_along_ray": "/particle <id> ~ ~ ~"}
     * (and the same for {@code command_at_hit}) expecting a visible trail / burst.
     * Run verbatim through the command dispatcher, vanilla spawns the particle in
     * NORMAL mode: it is culled by the client's particle video setting and only sent
     * to players within 32 blocks. Deano's mage Flame trail rendered nothing because
     * of this. When the command is a bare {@code /particle <id> <x> <y> <z>} with no
     * explicit delta/speed/count, append {@code 0 0 0 0 1 force} so exactly one
     * forced particle renders regardless of client settings or distance. Commands
     * that already specify those args, multi-token particle ids (block/item/dust),
     * or non-particle commands are returned untouched.
     */
    static String forceParticleVisibility(String command) {
        if (command == null) return null;
        String body = command.startsWith("/") ? command.substring(1) : command;
        String[] parts = body.trim().split("\\s+");
        // particle <id> <x> <y> <z> == exactly 5 tokens, nothing trailing.
        if (parts.length == 5 && parts[0].equals("particle")) {
            return command + " 0 0 0 0 1 force";
        }
        return command;
    }

    /**
     * Run a single command at a fixed world position with the player as the
     * command entity (so {@code @s} resolves to the caster and {@code ~ ~ ~}
     * to {@code pos}). Used by raycast's {@code command_at_hit}. Built the same
     * way as command_along_ray on this branch (server source stack, suppressed
     * output, entity/position/rotation/level set explicitly).
     */
    private static void runCommandAt(net.minecraft.server.level.ServerPlayer player, String command,
                                     Vec3 pos, String contextId) {
        if (command == null || command.isEmpty()) return;
        if (commandBlocked(command, contextId + " command_at_hit")) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        var server = serverLevel.getServer();
        if (server == null) return;
        var src = server.createCommandSourceStack()
            .withSuppressedOutput()
            .withEntity(player)
            .withPosition(pos)
            .withRotation(player.getRotationVector())
            .withLevel(serverLevel);
        try {
            server.getCommands().performPrefixedCommand(src, command);
        } catch (Exception e) {
            com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                "[CompatB] raycast {} command_at_hit failed: {}", contextId, e.getMessage());
        }
    }

    /**
     * Shared blacklist check for the raycast command extensions. Logs and
     * returns true when the command's root is on the command-power blacklist.
     */
    private static boolean commandBlocked(String command, String contextId) {
        if (com.cyberday1.neoorigins.command.CommandPowerGuard.isBlocked(command)) {
            com.cyberday1.neoorigins.command.CommandPowerGuard.warnBlocked(command, contextId);
            return true;
        }
        return false;
    }

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

    // ---- Phase 2: New action parsers ----

    static EntityAction parseAreaOfEffect(JsonObject json, String contextId) {
        // AoE: scan every LivingEntity within radius once and dispatch the inner
        // action per entity. The inner action is parsed two ways:
        //   - as an EntityAction (player-typed) for the legacy player path, and
        //   - as a TargetAction (LivingEntity + actor) when the verb is
        //     generalizable (apply_effect, damage, heal, swap_positions,
        //     teleport_to_target, shear, dye, ...).
        // When a TargetAction form exists it runs on BOTH players and mobs
        // (source = the caster). For overlapping verbs the observable outcome on
        // an in-radius entity is identical to before; for the dual-actor verbs
        // this is the new capability (previously only apply_effect/damage leaves
        // were fanned out to mobs via a recursive hack).
        //
        // When no TargetAction form exists (player-only verbs like launch /
        // set_block), the legacy behaviour is kept: run the EntityAction only on
        // ServerPlayer targets and skip mobs.
        float radius = json.has("radius") ? json.get("radius").getAsFloat() : 16.0f;
        String shape = json.has("shape") ? json.get("shape").getAsString() : "sphere";
        boolean includeSelf = !json.has("include_source") || json.get("include_source").getAsBoolean();

        JsonObject innerJson = json.has("entity_action") ? json.getAsJsonObject("entity_action") : null;
        EntityAction action = innerJson != null ? parse(innerJson, contextId) : EntityAction.noop();
        TargetAction targetAction = innerJson != null ? TargetActionParser.parse(innerJson, contextId) : null;
        EntityCondition targetCondition = json.has("entity_condition")
            ? ConditionParser.parse(json.getAsJsonObject("entity_condition"), contextId)
            : EntityCondition.alwaysTrue();

        final float  finalRadius       = radius;
        final boolean finalIncludeSelf = includeSelf;
        final String  finalShape       = shape;
        final EntityAction finalAction = action;
        final TargetAction finalTargetAction = targetAction;
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
            java.util.UUID casterUuid = source.getUUID();

            var candidates = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb);
            for (var entity : candidates) {
                // Shape gate (both players and mobs).
                if ("sphere".equalsIgnoreCase(finalShape)
                        && entity.position().distanceToSqr(srcPos) > r2) continue;
                // include_source gate.
                if (entity == source && !finalIncludeSelf) continue;

                boolean isPlayer = entity instanceof net.minecraft.server.level.ServerPlayer;
                // entity_condition gate. EntityCondition is player-typed, so — as
                // before — it can only be tested against ServerPlayer targets. Mobs
                // were never condition-gated in the legacy fan-out, so they keep
                // bypassing it here.
                if (isPlayer && !finalCond.test((net.minecraft.server.level.ServerPlayer) entity)) continue;

                if (!isPlayer) {
                    // Friendly-fire filter applies ONLY to non-player mob targets —
                    // each category is independently configurable via [friendly_fire]
                    // in neoorigins-common.toml. Defaults: pets/minions/villagers/iron
                    // golems protected; passive animals (sheep, cow, pig, ...) NOT
                    // protected so active combat AOEs (Hiveling Sting, Inferno Burst,
                    // ...) can actually hit livestock.
                    if (entity == source) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectOwnedPets()
                            && entity instanceof net.minecraft.world.entity.TamableAnimal tame
                            && tame.getOwnerReference() != null
                            && tame.getOwnerReference().getUUID().equals(casterUuid)) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectMinions()
                            && com.cyberday1.neoorigins.service.MinionTracker.isTrackedMinionOf(entity, casterUuid)) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectAnimals()
                            && entity instanceof net.minecraft.world.entity.animal.Animal) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectVillagers()
                            && entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager) continue;
                    if (com.cyberday1.neoorigins.NeoOriginsConfig.ffProtectIronGolems()
                            && entity instanceof net.minecraft.world.entity.animal.golem.IronGolem) continue;
                }

                if (finalTargetAction != null) {
                    // Generalizable verb — runs on players and mobs alike.
                    finalTargetAction.execute(entity, source);
                } else if (isPlayer) {
                    // Player-only verb — legacy behaviour: players only, skip mobs.
                    finalAction.execute((net.minecraft.server.level.ServerPlayer) entity);
                }
            }
        };
    }

    // ---- Phase 0: filled stubs ----

    // ---- Phase 0/1: new verbs (for active_ability consolidation) ----

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
    static EntityAction parseSpawnLingeringArea(JsonObject json, String contextId) {
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
    static EntityAction parseSpawnBlackHole(JsonObject json, String contextId) {
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
    static EntityAction parseSpawnTornado(JsonObject json, String contextId) {
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

    static EntityAction parseChainToNearest(JsonObject json, String contextId) {
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

    static EntityAction parsePullEntities(JsonObject json, String contextId) {
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

    static net.minecraft.core.BlockPos extractCommandBlockPos(Object ctx) {
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

    /**
     * The resolved block target of the active dispatch context — the
     * {@link ServerLevel} and {@link BlockPos} of the impacted block. The actor
     * is supplied separately (the {@code EntityAction}/{@code BlockTargetAction}
     * arg), so this carries only level+pos. Package-private record so the
     * block-target verbs in {@link BuiltinActions} and the
     * {@link BlockTargetActionParser} share one resolved shape.
     */
    record BlockTarget(ServerLevel level, BlockPos pos) {}

    /**
     * Resolve the impacted block of the active dispatch context, mirroring
     * {@link #extractBientityTarget} for blocks. Recognizes:
     * <ul>
     *   <li>the dedicated {@link com.cyberday1.neoorigins.service.EventPowerIndex.BlockHitContext}
     *       installed on projectile block impact;</li>
     *   <li>a {@link com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext}
     *       whose ray-trace result is a block hit (so block-target verbs work as a
     *       projectile {@code on_hit_action} without extra plumbing);</li>
     *   <li>the synthetic {@link RaycastBlockContext} published by {@code raycast}'s
     *       block hit / {@code block_action_at} — level is taken from {@code fallbackLevel}
     *       (the actor's level), since that context carries only the pos.</li>
     * </ul>
     * Returns {@code null} when no block context resolves.
     */
    static BlockTarget extractBlockTarget(Object ctx, ServerLevel fallbackLevel) {
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.BlockHitContext bhc) {
            return new BlockTarget(bhc.level(), bhc.pos());
        }
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc
            && phc.result() instanceof net.minecraft.world.phys.BlockHitResult bhr
            && phc.projectile().level() instanceof ServerLevel sl) {
            return new BlockTarget(sl, bhr.getBlockPos());
        }
        if (ctx instanceof RaycastBlockContext rbc && fallbackLevel != null) {
            return new BlockTarget(fallbackLevel, rbc.pos());
        }
        return null;
    }

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
