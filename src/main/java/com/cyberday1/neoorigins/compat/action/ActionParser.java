package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.compat.condition.TargetCondition;
import com.cyberday1.neoorigins.compat.condition.TargetConditionParser;
import com.cyberday1.neoorigins.compat.registry.ActionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
        "neoorigins:activate_power",
        "neoorigins:actor_action", "neoorigins:add_to_set", "neoorigins:add_velocity",
        "neoorigins:add_xp", "neoorigins:and", "neoorigins:apply_effect",
        "neoorigins:area_of_effect", "neoorigins:block_action_at", "neoorigins:block_target_action",
        "neoorigins:cancel_event", "neoorigins:cast_spell", "neoorigins:cast_iron_spell",
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
        "neoorigins:modify_food", "neoorigins:modify_inventory",
        "neoorigins:modify_temperature", "neoorigins:mount",
        "neoorigins:nothing", "neoorigins:offset", "neoorigins:open_layer_picker",
        "neoorigins:passenger_action",
        "neoorigins:play_sound", "neoorigins:pull_entities", "neoorigins:random_teleport",
        "neoorigins:raycast", "neoorigins:remove_from_set", "neoorigins:revoke_power",
        "neoorigins:riding_action", "neoorigins:selector_action", "neoorigins:spawn_particles",
        "neoorigins:set_block", "neoorigins:set_fall_distance", "neoorigins:set_on_fire",
        "neoorigins:set_resource", "neoorigins:spawn_black_hole", "neoorigins:spawn_effect_cloud",
        "neoorigins:spawn_entity", "neoorigins:spawn_lingering_area",
        "neoorigins:spawn_projectile", "neoorigins:spawn_projectile_rain",
        "neoorigins:spawn_telegraph", "neoorigins:spawn_tornado",
        "neoorigins:swap_positions", "neoorigins:swap_with_entity", "neoorigins:swing_hand",
        "neoorigins:tame_target",
        "neoorigins:target_action", "neoorigins:teleport_target_to_self",
        "neoorigins:teleport_to_marker", "neoorigins:teleport_to_target",
        "neoorigins:throw_target", "neoorigins:toggle",
        "neoorigins:trigger_cooldown", "neoorigins:kubejs_callback");

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
            // Generic namespace fallback — any non-canonical prefix rewrites to
            // neoorigins:<leaf>. Lets `medievalorigins:execute_command`,
            // `apugli:foo` etc. dispatch to the neoorigins:* handler without
            // per-mod alias tables. Earlier versions whitelisted
            // origins/apace/apoli only, silently no-oping actions from other
            // Apoli-derivative namespaces.
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
     * Parse an action field that may be absent, a single object, or an array of
     * action objects. Absent or non-object/array → {@link EntityAction#noop()};
     * an array runs its elements sequentially (Apoli all-of).
     *
     * <p>This is the native-power CODEC counterpart to the Route-B loader's
     * identical helper, so {@code neoorigins:*} powers accept the same
     * array-or-object action shape that compat-translated powers already do.
     * Without it, a bare JSON array in an action field silently no-ops.
     */
    public static EntityAction parseField(JsonObject parent, String field, String contextId) {
        return com.cyberday1.neoorigins.compat.util.JsonHelpers.parseArrayOrSingle(
            parent, field, contextId,
            EntityAction.noop(),
            ActionParser::parse,
            list -> {
                if (list.isEmpty()) return EntityAction.noop();
                return player -> { for (EntityAction a : list) a.execute(player); };
            });
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
        // Docs and Apoli both call the nested object "item_action"; "action" is
        // the original (undocumented) key this parser shipped with. Accept both,
        // documented name first, so doc-following pack authors stop getting a
        // silent no-op (repo audit 2026-06-12).
        JsonObject actionObj = null;
        if (json.has("item_action") && json.get("item_action").isJsonObject()) {
            actionObj = json.getAsJsonObject("item_action");
        } else if (json.has("action") && json.get("action").isJsonObject()) {
            actionObj = json.getAsJsonObject("action");
        }
        ItemAction action = actionObj != null ? ItemActionParser.parse(actionObj) : ItemAction.noop();
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
     *
     * <p>{@code slot} restricts processing to a single inventory slot —
     * an equipment name ({@code mainhand}, {@code offhand}, {@code head},
     * {@code chest}, {@code legs}, {@code feet}) or a raw inventory index.
     * Before the 2026-06-12 audit this documented field was silently
     * ignored, which made slot-scoped {@code consume} destroy matching
     * items inventory-wide.
     */
    static EntityAction parseModifyInventory(JsonObject json) {
        JsonObject itemCondJson = com.cyberday1.neoorigins.compat.util.JsonHelpers.getOrNull(json, "item_condition");
        var itemCond = itemCondJson != null
            ? com.cyberday1.neoorigins.compat.condition.ItemConditionParser.parse(itemCondJson)
            : com.cyberday1.neoorigins.compat.condition.ItemCondition.alwaysTrue();
        JsonObject itemActionJson = com.cyberday1.neoorigins.compat.util.JsonHelpers.getOrNull(json, "item_action");
        ItemAction itemAction = itemActionJson != null
            ? ItemActionParser.parse(itemActionJson) : ItemAction.noop();
        String processMode = json.has("process_mode") ? json.get("process_mode").getAsString() : "items";
        int limit = json.has("limit") ? json.get("limit").getAsInt() : 0;
        boolean countByItems = !"stacks".equalsIgnoreCase(processMode);
        String slotName = json.has("slot") ? json.get("slot").getAsString() : null;
        if (slotName != null && !isKnownInventorySlot(slotName)) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_inventory: unknown slot '{}' — no-op", slotName);
            return EntityAction.noop();
        }
        String slot = slotName;
        // inventory_type is honoured loosely — vanilla only has one player
        // inventory; modded sub-inventories aren't reachable from here.
        // Pack authors generally pass "inventory" anyway, which is correct.
        return player -> {
            int applied = 0;
            var inv = player.getInventory();
            int start = 0;
            int total = inv.getContainerSize();
            if (slot != null) {
                // mainhand depends on the selected hotbar slot, so the index
                // is resolved per execution rather than at parse time.
                int idx = resolveInventorySlot(inv, slot);
                if (idx < 0) return;
                start = idx;
                total = idx + 1;
            }
            for (int i = start; i < total; i++) {
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

    /** Parse-time validation for {@code modify_inventory.slot} — equipment names or a raw index. */
    private static boolean isKnownInventorySlot(String name) {
        switch (name.toLowerCase()) {
            case "mainhand", "offhand", "head", "chest", "legs", "feet":
                return true;
            default:
                try {
                    return Integer.parseInt(name) >= 0;
                } catch (NumberFormatException e) {
                    return false;
                }
        }
    }

    /**
     * Resolve a {@code modify_inventory.slot} name to a player-inventory
     * container index. Vanilla layout: 0-35 main inventory (0-8 hotbar),
     * 36-39 armor (feet, legs, chest, head), 40 offhand.
     */
    private static int resolveInventorySlot(net.minecraft.world.entity.player.Inventory inv, String name) {
        return switch (name.toLowerCase()) {
            case "mainhand" -> inv.selected;
            case "offhand"  -> 40;
            case "feet"     -> 36;
            case "legs"     -> 37;
            case "chest"    -> 38;
            case "head"     -> 39;
            default -> {
                int idx = Integer.parseInt(name); // validated at parse time
                yield idx < inv.getContainerSize() ? idx : -1;
            }
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
        // Each of these may be a single object or an array (run sequentially).
        EntityAction blockAction = parseField(json, "block_action", contextId);
        EntityAction bientityAction = parseField(json, "bientity_action", contextId);
        EntityAction missAction = parseField(json, "miss_action", contextId);
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
        final EntityAction beforeAction = parseField(json, "before_action", contextId);
        final String commandAtHit = forceParticleVisibility(
            json.has("command_at_hit") ? json.get("command_at_hit").getAsString() : null);
        return player -> {
            beforeAction.execute(player);
            Vec3 from = player.getEyePosition(1.0F);
            Vec3 look = player.getViewVector(1.0F);
            Vec3 to = from.add(look.x * distance, look.y * distance, look.z * distance);

            // Run the along-ray command (if any) before bail-out so it fires
            // regardless of whether a block / entity is hit. Pack authors who
            // want hit-gated trails use block_action with execute_command.
            if (commandAlongRay != null && !commandAlongRay.isEmpty() && player.getServer() != null
                    && !commandBlocked(commandAlongRay, contextId + " command_along_ray")) {
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
     * to {@code pos}). Used by raycast's {@code command_at_hit}. Suppressed
     * output + permission level 2, matching command_along_ray.
     */
    private static void runCommandAt(net.minecraft.server.level.ServerPlayer player, String command,
                                     Vec3 pos, String contextId) {
        if (command == null || command.isEmpty() || player.getServer() == null) return;
        if (commandBlocked(command, contextId + " command_at_hit")) return;
        var src = player.createCommandSourceStack()
            .withPosition(pos)
            .withSuppressedOutput()
            .withPermission(2);
        try {
            player.getServer().getCommands().performPrefixedCommand(src, command);
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
        // block_use / bonemeal dispatches publish a BlockInteractContext, so a
        // block_action execute_command on action_on_block_use resolves `~ ~ ~`
        // to the clicked block (the Apoli block-action semantics — Chaotic
        // Chemist's mechanical-chemistry triggers a function at the mixer).
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.BlockInteractContext bic) {
            return bic.pos();
        }
        return null;
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

        // `shape` is a bare string in Apoli's schema (sphere/cube), but community
        // packs write the cone form as an object — Fairytale's breath attack is
        //   "shape": { "type": "origins:cone", "angle": 60 }
        // and getAsString() threw on it, no-opping the whole area_of_effect.
        // `angle` is the full apex width in degrees, centred on the caster's look.
        String shape = "sphere";
        double coneCos = Double.NaN;
        if (json.has("shape")) {
            JsonElement sh = json.get("shape");
            if (sh.isJsonObject()) {
                JsonObject so = sh.getAsJsonObject();
                String st = so.has("type") ? so.get("type").getAsString() : "";
                shape = st.contains(":") ? st.substring(st.indexOf(':') + 1) : st;
                if (shape.equals("cone")) {
                    double angle = so.has("angle") ? so.get("angle").getAsDouble() : 90.0;
                    coneCos = Math.cos(Math.toRadians(Math.min(360.0, Math.max(0.0, angle)) / 2.0));
                }
            } else if (sh.isJsonPrimitive()) {
                shape = sh.getAsString();
            }
        }

        // Apoli's canonical per-target action is `bientity_action`, taking the
        // (actor, target) pair — every third-party AoE in the wild uses it, and
        // reading only the older `entity_action` form meant they compiled to a
        // silent no-op with nothing recorded. Both forms are read; bientity_action
        // wins when a pack somehow writes both.
        JsonObject biJson = null;
        if (json.has("bientity_action")) {
            JsonElement ba = json.get("bientity_action");
            if (ba.isJsonObject()) {
                biJson = ba.getAsJsonObject();
            } else if (ba.isJsonArray()) {
                biJson = new JsonObject();
                biJson.addProperty("type", "neoorigins:and");
                biJson.add("actions", ba.getAsJsonArray());
            }
        }
        BiEntityAction biAction = biJson != null
            ? BiEntityActionParser.parse(biJson, contextId) : null;

        // include_source is the legacy spelling, include_target the Apoli one.
        // The defaults differ and both are kept: the legacy entity_action form
        // has always included the caster, while Apoli's bientity_action form
        // defaults to excluding it — which is what a cone breath attack wants,
        // since including the caster means damaging yourself with your own dash.
        boolean includeSelf;
        if (json.has("include_source")) {
            includeSelf = json.get("include_source").getAsBoolean();
        } else if (json.has("include_target")) {
            includeSelf = json.get("include_target").getAsBoolean();
        } else {
            includeSelf = biAction == null;
        }

        // entity_action may be a single object OR a JSON array. A bare array is
        // wrapped in a synthetic neoorigins:and so both the EntityAction (player)
        // and TargetAction (mob) paths compose the elements through the existing,
        // well-tested `and` handling. Previously a bare array threw inside
        // getAsJsonObject and the whole AoE silently no-opped.
        JsonObject innerJson = null;
        if (json.has("entity_action")) {
            JsonElement ea = json.get("entity_action");
            if (ea.isJsonObject()) {
                innerJson = ea.getAsJsonObject();
            } else if (ea.isJsonArray()) {
                innerJson = new JsonObject();
                innerJson.addProperty("type", "neoorigins:and");
                innerJson.add("actions", ea.getAsJsonArray());
            }
        }
        EntityAction action = innerJson != null ? parse(innerJson, contextId) : EntityAction.noop();
        TargetAction targetAction = innerJson != null ? TargetActionParser.parse(innerJson, contextId) : null;
        // entity_condition is parsed two ways, mirroring the action above:
        //   - as a player-typed EntityCondition (legacy: only player targets are
        //     gated by it), and
        //   - as an entity-general TargetCondition when the verb is generalizable
        //     (entity_type/#tag, target_group, health, has_effect, and/or/not, ...).
        // When a TargetCondition form exists it filters BOTH players and mobs — so
        // an aura can finally restrict who it affects ("players only", a #tag group,
        // a specific mob). When it doesn't (player-only condition verb), the legacy
        // behaviour stands: players are gated, mobs bypass the condition as before.
        JsonObject condJson = com.cyberday1.neoorigins.compat.util.JsonHelpers.getOrNull(json, "entity_condition");
        EntityCondition targetCondition = condJson != null
            ? ConditionParser.parse(condJson, contextId)
            : EntityCondition.alwaysTrue();
        TargetCondition targetCond = condJson != null
            ? TargetConditionParser.parse(condJson, contextId) : null;

        // bientity_condition — the Apoli filter, evaluated as (actor, target).
        // Fairytale's breath cone uses it to spare players. A filter that cannot
        // be compiled fails CLOSED (the entity fan-out is skipped and recorded):
        // an uncompiled exclusion silently becomes "hits everyone", and hitting
        // bystanders is worse than the power not firing.
        JsonObject biCondJson = com.cyberday1.neoorigins.compat.util.JsonHelpers
            .getOrNull(json, "bientity_condition");
        java.util.function.BiPredicate<net.minecraft.server.level.ServerPlayer,
                net.minecraft.world.entity.Entity> biCond = null;
        boolean biCondBroken = false;
        if (biCondJson != null) {
            biCond = TargetConditionParser.parseBiEntity(biCondJson, contextId);
            if (biCond == null) {
                biCondBroken = true;
                com.cyberday1.neoorigins.compat.CompatWarningCollector.recordUnsupportedAction(
                    "neoorigins:area_of_effect", contextId,
                    "bientity_condition cannot be evaluated against a non-player entity — entity fan-out skipped");
            }
        }

        // Apoli block-action form: `block_action` fans the inner action out over
        // BLOCK POSITIONS in radius (centered on the context block pos when one
        // resolves, else the source's feet), publishing each pos as a
        // RaycastBlockContext so block verbs (grow/bonemeal, transform_block,
        // offset, execute_command) self-resolve. This is the Fairy Origin
        // verdant_touch chain — block_action_at > area_of_effect{block_action:
        // offset{bonemeal}} — which previously no-opped (entity-only fan-out).
        JsonObject blockInnerJson = null;
        if (json.has("block_action")) {
            JsonElement ba = json.get("block_action");
            if (ba.isJsonObject()) {
                blockInnerJson = ba.getAsJsonObject();
            } else if (ba.isJsonArray()) {
                blockInnerJson = new JsonObject();
                blockInnerJson.addProperty("type", "neoorigins:and");
                blockInnerJson.add("actions", ba.getAsJsonArray());
            }
        }
        EntityAction blockAction = blockInnerJson != null ? parse(blockInnerJson, contextId) : null;
        java.util.function.BiPredicate<net.minecraft.server.level.ServerPlayer, net.minecraft.core.BlockPos> blockCond =
            (blockAction != null && json.has("block_condition") && json.get("block_condition").isJsonObject())
                ? com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader
                    .compileBlockPredicate(json.getAsJsonObject("block_condition"), contextId)
                : null;

        final float  finalRadius       = radius;
        final boolean finalIncludeSelf = includeSelf;
        final String  finalShape       = shape;
        final double  finalConeCos     = coneCos;
        final BiEntityAction finalBiAction = biAction;
        final var finalBiCond          = biCond;
        final boolean finalBiCondBroken = biCondBroken;
        final EntityAction finalAction = action;
        final TargetAction finalTargetAction = targetAction;
        final EntityCondition finalCond = targetCondition;
        final TargetCondition finalTargetCond = targetCond;
        final EntityAction finalBlockAction = blockAction;
        final var finalBlockCond = blockCond;

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

            var candidates = finalBiCondBroken
                ? java.util.List.<net.minecraft.world.entity.LivingEntity>of()
                : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb);
            for (var entity : candidates) {
                // Shape gate (both players and mobs). "cube" is already bounded
                // by the AABB; sphere and cone additionally gate on distance,
                // and cone on the angle off the caster's look vector.
                if (!"cube".equalsIgnoreCase(finalShape)
                        && entity.position().distanceToSqr(srcPos) > r2) continue;
                if (!Double.isNaN(finalConeCos) && entity != source) {
                    var to = entity.position().subtract(srcPos);
                    double len = to.length();
                    if (len > 1.0e-4
                            && source.getLookAngle().dot(to.scale(1.0 / len)) < finalConeCos) continue;
                }
                // include_source gate.
                if (entity == source && !finalIncludeSelf) continue;

                boolean isPlayer = entity instanceof net.minecraft.server.level.ServerPlayer;
                // entity_condition gate. When the condition is entity-general
                // (TargetCondition present), it filters BOTH players and mobs —
                // this is what lets an aura target "players only", a #tag group,
                // etc. Otherwise it stays player-typed: only player targets are
                // gated and mobs bypass it, exactly as in the legacy fan-out.
                if (finalBiCond != null && !finalBiCond.test(source, entity)) continue;
                if (finalTargetCond != null) {
                    if (!finalTargetCond.test(entity, source)) continue;
                } else if (isPlayer && !finalCond.test((net.minecraft.server.level.ServerPlayer) entity)) {
                    continue;
                }

                if (!isPlayer) {
                    // Friendly-fire filter applies ONLY to non-player mob targets —
                    // each category is independently configurable via [friendly_fire]
                    // in neoorigins/gameplay.toml. Defaults: pets/minions/villagers/iron
                    // golems protected; passive animals (sheep, cow, pig, ...) NOT
                    // protected so active combat AOEs (Hiveling Sting, Inferno Burst,
                    // ...) can actually hit livestock.
                    if (entity == source) continue;
                    if (GameplayConfig.ffProtectOwnedPets()
                            && entity instanceof net.minecraft.world.entity.TamableAnimal tame
                            && tame.getOwnerUUID() != null
                            && tame.getOwnerUUID().equals(casterUuid)) continue;
                    if (GameplayConfig.ffProtectMinions()
                            && com.cyberday1.neoorigins.service.MinionTracker.isTrackedMinionOf(entity, casterUuid)) continue;
                    if (GameplayConfig.ffProtectAnimals()
                            && entity instanceof net.minecraft.world.entity.animal.Animal) continue;
                    if (GameplayConfig.ffProtectVillagers()
                            && entity instanceof net.minecraft.world.entity.npc.AbstractVillager) continue;
                    if (GameplayConfig.ffProtectIronGolems()
                            && entity instanceof net.minecraft.world.entity.animal.IronGolem) continue;
                }

                if (finalBiAction != null) {
                    // Apoli bientity_action — the (actor, target) pair form.
                    finalBiAction.execute(source, entity);
                } else if (finalTargetAction != null) {
                    // Generalizable verb — runs on players and mobs alike.
                    finalTargetAction.execute(entity, source);
                } else if (isPlayer) {
                    // Player-only verb — legacy behaviour: players only, skip mobs.
                    finalAction.execute((net.minecraft.server.level.ServerPlayer) entity);
                }
            }

            // Block fan-out (Apoli area_of_effect `block_action` form).
            if (finalBlockAction != null) {
                net.minecraft.core.BlockPos center = extractCommandBlockPos(ctx);
                if (center == null) center = net.minecraft.core.BlockPos.containing(srcPos);
                // Cap the block sweep radius: a pack-default radius of 16 is
                // sane for the entity path but 33^3 block positions per fire is
                // where we draw the line anyway; Apoli packs use small radii
                // (Fairy verdant_touch: 2).
                int br = (int) Math.ceil(Math.min(finalRadius, 16.0f));
                double br2 = (double) finalRadius * finalRadius;
                boolean sphere = "sphere".equalsIgnoreCase(finalShape);
                for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                        center.offset(-br, -br, -br), center.offset(br, br, br))) {
                    if (sphere && pos.distSqr(center) > br2) continue;
                    if (finalBlockCond != null && !finalBlockCond.test(source, pos)) continue;
                    Object prevCtx = com.cyberday1.neoorigins.service.ActionContextHolder.set(
                        new RaycastBlockContext(pos.immutable()));
                    try {
                        finalBlockAction.execute(source);
                    } finally {
                        com.cyberday1.neoorigins.service.ActionContextHolder.restore(prevCtx);
                    }
                }
            }
        };
    }

    // ---- Phase 0: filled stubs ----


    // ---- Phase 0/1: new verbs (for active_ability consolidation) ----

    /** Parse {@code neoorigins:spawn_lingering_area}. See the 26.1 variant for field docs. */
    static EntityAction parseSpawnLingeringArea(JsonObject json, String contextId) {
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 3.0f;
        final int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 100;
        final int intervalTicks = json.has("interval_ticks") ? json.get("interval_ticks").getAsInt() : 20;
        final String effectType = json.has("effect_type") ? json.get("effect_type").getAsString() : "";
        // Accept a single object or an array (run sequentially) for the interval
        // action. parseField returns a shared noop when absent — harmless to run
        // each interval, so no null-guard is needed downstream.
        final EntityAction intervalAction = parseField(json, "entity_action", contextId);
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
    static EntityAction parseSpawnBlackHole(JsonObject json, String contextId) {
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
    static EntityAction parseSpawnTornado(JsonObject json, String contextId) {
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 5.0f;
        final int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 100;
        final float pullStrength = json.has("pull_strength") ? json.get("pull_strength").getAsFloat() : 1.0f;
        final float liftStrength = json.has("lift_strength") ? json.get("lift_strength").getAsFloat() : 0.5f;
        final float spinStrength = json.has("spin_strength") ? json.get("spin_strength").getAsFloat() : 0.5f;
        final float damagePerInterval = json.has("damage_per_interval") ? json.get("damage_per_interval").getAsFloat() : 2.0f;
        final int damageIntervalTicks = json.has("damage_interval_ticks") ? json.get("damage_interval_ticks").getAsInt() : 10;
        // Blocks per tick the funnel drifts forward in the cast direction (0 = stationary).
        final float moveSpeed = json.has("move_speed") ? json.get("move_speed").getAsFloat() : 0.2f;
        // When true the funnel falls under gravity until its base hits the ground
        // (so a tornado spawned mid-air drops while drifting forward).
        final boolean gravity = json.has("gravity") && json.get("gravity").getAsBoolean();
        final String effectType = json.has("effect_type") ? json.get("effect_type").getAsString() : "";

        // Composable per-caught-entity effect. When present it REPLACES the
        // built-in interval damage: on each damage-interval tick, for every
        // entity inside the funnel's inner radius we run this action — an
        // EntityAction for player targets, a TargetAction for mobs (caster as
        // actor) — the same fan-out the sword rain's impact_action uses.
        JsonObject impactInner = null;
        if (json.has("impact_action")) {
            JsonElement ia = json.get("impact_action");
            if (ia.isJsonObject()) {
                impactInner = ia.getAsJsonObject();
            } else if (ia.isJsonArray()) {
                impactInner = new JsonObject();
                impactInner.addProperty("type", "neoorigins:and");
                impactInner.add("actions", ia.getAsJsonArray());
            }
        }
        final boolean hasImpact = impactInner != null;
        final EntityAction impactEA = hasImpact ? parse(impactInner, contextId) : null;
        final TargetAction impactTA = hasImpact ? TargetActionParser.parse(impactInner, contextId) : null;

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
            // Drift forward along the caster's horizontal facing.
            entity.setMoveDirection(
                net.minecraft.world.phys.Vec3.directionFromRotation(0f, player.getYRot()), moveSpeed);
            entity.setGravity(gravity);
            if (hasImpact) {
                entity.setImpactCallback((lvl, target, caster) -> {
                    if (target instanceof net.minecraft.server.level.ServerPlayer sp) {
                        impactEA.execute(sp);
                    } else if (impactTA != null && target instanceof net.minecraft.world.entity.LivingEntity le) {
                        impactTA.execute(le, caster);
                    }
                });
            }
            entity.setCaster(player.getUUID());
            sl.addFreshEntity(entity);
        };
    }

    static EntityAction parseSpawnProjectileRain(JsonObject json, String contextId) {
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 6.0f;
        final int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 70;
        // `count` is the canonical name; `sword_count` kept as a back-compat alias
        // for packs written before the action was generalised away from swords.
        final int swordCount = json.has("count") ? json.get("count").getAsInt()
            : json.has("sword_count") ? json.get("sword_count").getAsInt() : 16;
        // `damage_per_impact` canonical; `damage_per_sword` kept as back-compat alias.
        final float damagePerSword = json.has("damage_per_impact") ? json.get("damage_per_impact").getAsFloat()
            : json.has("damage_per_sword") ? json.get("damage_per_sword").getAsFloat() : 4.0f;
        final float knockup = json.has("knockup") ? json.get("knockup").getAsFloat() : 0.5f;
        final float impactRadius = json.has("impact_radius") ? json.get("impact_radius").getAsFloat() : 2.0f;
        // Lead-in ticks where the ground "sword shadow" telegraph shows before
        // any blade falls (the rain is centered on the marked landing spot).
        final int telegraphTicks = json.has("telegraph_ticks") ? json.get("telegraph_ticks").getAsInt() : 14;
        // Fraction of the caster's attack-damage attribute (which folds in the
        // held weapon) added on top of damage_per_sword, captured at cast time so
        // a sharper sword makes the storm hit harder. 0 (default) = flat damage.
        final float weaponDamageScale = json.has("weapon_damage_scale") ? json.get("weapon_damage_scale").getAsFloat() : 0f;
        // "self" (rain around the caster), "look" (rain on whatever the caster
        // is aiming at), or "impact" (rain at a projectile hit point, when cast
        // from an on-hit context).
        final String origin = json.has("origin") ? json.get("origin").getAsString() : "self";
        final String effectType = json.has("effect_type") ? json.get("effect_type").getAsString() : "";
        // Which baked model the falling projectiles use (renderer model registry).
        final String model = json.has("model") ? json.get("model").getAsString() : "sword";

        // Composable per-blade effect. When present it REPLACES the built-in
        // damage: at each blade's landing point we run this action against
        // entities within impact_radius — TargetAction for mobs (caster as actor),
        // EntityAction for player targets — the same fan-out area_of_effect uses.
        JsonObject impactInner = null;
        if (json.has("impact_action")) {
            JsonElement ia = json.get("impact_action");
            if (ia.isJsonObject()) {
                impactInner = ia.getAsJsonObject();
            } else if (ia.isJsonArray()) {
                impactInner = new JsonObject();
                impactInner.addProperty("type", "neoorigins:and");
                impactInner.add("actions", ia.getAsJsonArray());
            }
        }
        final boolean hasImpact = impactInner != null;
        final EntityAction impactEA = hasImpact ? parse(impactInner, contextId) : null;
        final TargetAction impactTA = hasImpact ? TargetActionParser.parse(impactInner, contextId) : null;

        // Real-projectile mode: when `projectile` names a registered entity type,
        // each blade spawns that ACTUAL entity high above its scatter point and
        // lets it fall under real physics, instead of drawing the choreographed
        // baked-mesh blade. The rain still owns the scatter pattern + staggered
        // launch schedule + telegraph; the spawned entity owns its own flight and
        // hit. Unknown / unset id keeps the classic fake-blade visual.
        final String projectileId = json.has("projectile") ? json.get("projectile").getAsString() : null;
        net.minecraft.world.entity.EntityType<?> projType = null;
        if (projectileId != null && !projectileId.isBlank()) {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(projectileId);
            if (rl != null) {
                projType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            }
            if (projType == null) {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "[CompatB] spawn_projectile_rain: unknown projectile entity '{}' — keeping baked-blade visual", projectileId);
            }
        }
        final net.minecraft.world.entity.EntityType<?> projectileType = projType;
        // Launch speed of the falling entity (gravity then accelerates it).
        final float projectileSpeed = json.has("projectile_speed") ? json.get("projectile_speed").getAsFloat() : 1.0f;
        // Blocks above each scatter point the entity spawns from.
        final float spawnHeight = json.has("spawn_height") ? json.get("spawn_height").getAsFloat() : 18.0f;
        // SNBT merged onto each spawned entity (e.g. "{pickup:1b}" for arrows).
        final String projTag = json.has("tag") ? json.get("tag").getAsString() : null;

        // On-hit for real projectiles: run the impact_action as the same radius
        // fan-out the fake blades use, centered on the projectile's impact point
        // (read off the ProjectileHitContext the impact event installs).
        final EntityAction onHitFanout = !hasImpact ? null : (EntityAction) (hitCaster -> {
            if (!(hitCaster.level() instanceof ServerLevel lvl)) return;
            Object c = com.cyberday1.neoorigins.service.ActionContextHolder.get();
            net.minecraft.world.phys.Vec3 hitPos;
            if (c instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc) {
                var l = phc.result().getLocation();
                hitPos = new net.minecraft.world.phys.Vec3(l.x, l.y, l.z);
            } else {
                hitPos = hitCaster.position();
            }
            float r = impactRadius;
            var box = new net.minecraft.world.phys.AABB(
                hitPos.x - r, hitPos.y - 1.5, hitPos.z - r, hitPos.x + r, hitPos.y + 2.5, hitPos.z + r);
            java.util.UUID casterId = hitCaster.getUUID();
            for (net.minecraft.world.entity.LivingEntity target :
                    lvl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
                if (target.getUUID().equals(casterId)) continue;
                double dx = target.getX() - hitPos.x;
                double dz = target.getZ() - hitPos.z;
                if (dx * dx + dz * dz > r * r) continue;
                if (target instanceof net.minecraft.server.level.ServerPlayer tp) {
                    impactEA.execute(tp);
                } else if (impactTA != null) {
                    impactTA.execute(target, hitCaster);
                }
            }
        });

        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var entity = com.cyberday1.neoorigins.content.ModEntities.PROJECTILE_RAIN.get().create(sl);
            if (entity == null) return;

            net.minecraft.world.phys.Vec3 center = resolveProjectileRainCenter(player, origin);
            entity.setPos(center.x, center.y, center.z);
            entity.setRange(radius);
            entity.setEffectType(effectType);
            entity.setMaxLifetime(durationTicks);
            entity.setSwordCount(swordCount);
            entity.setDamagePerSword(damagePerSword);
            entity.setKnockup(knockup);
            entity.setImpactRadius(impactRadius);
            entity.setTelegraphTicks(telegraphTicks);
            entity.setModel(model);
            entity.setFollowTerrain(!json.has("follow_terrain") || json.get("follow_terrain").getAsBoolean());
            if (weaponDamageScale > 0f) {
                double atk = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                entity.setWeaponDamageBonus((float) (weaponDamageScale * atk));
            }
            if (hasImpact) {
                entity.setImpactCallback((lvl, pos, r, caster) -> {
                    var box = new net.minecraft.world.phys.AABB(
                        pos.x - r, pos.y - 1.5, pos.z - r, pos.x + r, pos.y + 2.5, pos.z + r);
                    java.util.UUID casterId = caster != null ? caster.getUUID() : null;
                    for (net.minecraft.world.entity.LivingEntity target :
                            lvl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
                        if (casterId != null && target.getUUID().equals(casterId)) continue;
                        double dx = target.getX() - pos.x;
                        double dz = target.getZ() - pos.z;
                        if (dx * dx + dz * dz > r * r) continue;
                        if (target instanceof net.minecraft.server.level.ServerPlayer sp) {
                            impactEA.execute(sp);
                        } else if (impactTA != null) {
                            impactTA.execute(target, caster);
                        }
                    }
                });
            }
            if (projectileType != null) {
                // Swap the fake blades for real falling entities. Client skips the
                // baked-mesh blade render (shouldRenderBlades=false); the launcher
                // fires one real entity per scatter point on its scheduled tick.
                entity.setRenderBlades(false);
                entity.setLaunchCallback((lvl, groundPos, caster) -> {
                    var proj = projectileType.create(lvl);
                    if (proj == null) return;
                    proj.setPos(groundPos.x, groundPos.y + spawnHeight, groundPos.z);
                    if (projTag != null) {
                        com.cyberday1.neoorigins.compat.action.BuiltinActions.applyEntityTag(proj, projTag);
                    }
                    if (proj instanceof net.minecraft.world.entity.projectile.Projectile p) {
                        if (caster != null) p.setOwner(caster);
                        p.shoot(0.0, -1.0, 0.0, projectileSpeed, 0.0f);
                    } else {
                        proj.setDeltaMovement(0, -projectileSpeed, 0);
                    }
                    lvl.addFreshEntity(proj);
                    // Caster-owned projectiles only: the on-hit registry dispatch
                    // keys off a ServerPlayer owner (see CombatPowerEvents).
                    if (onHitFanout != null && caster != null
                            && proj instanceof net.minecraft.world.entity.projectile.Projectile) {
                        com.cyberday1.neoorigins.service.ProjectileActionRegistry.register(
                            proj.getUUID(), onHitFanout, caster.tickCount);
                    }
                });
            }
            entity.setCaster(player.getUUID());
            sl.addFreshEntity(entity);
        };
    }

    private static net.minecraft.world.phys.Vec3 resolveProjectileRainCenter(
            net.minecraft.server.level.ServerPlayer player, String origin) {
        Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
        if (("impact".equals(origin) || "self".equals(origin))
                && ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc) {
            var pos = phc.result().getLocation();
            return new net.minecraft.world.phys.Vec3(pos.x, pos.y, pos.z);
        }
        if ("look".equals(origin)) {
            final double reach = 32.0;
            var from = player.getEyePosition();
            var look = player.getLookAngle();
            var to = from.add(look.scale(reach));
            var aabb = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
            var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, from, to, aabb,
                e -> e instanceof net.minecraft.world.entity.LivingEntity && e != player,
                reach * reach);
            if (entityHit != null) {
                var e = entityHit.getEntity();
                return new net.minecraft.world.phys.Vec3(e.getX(), e.getY(), e.getZ());
            }
            var clip = player.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            var loc = clip.getLocation();
            return new net.minecraft.world.phys.Vec3(loc.x, loc.y, loc.z);
        }
        return new net.minecraft.world.phys.Vec3(player.getX(), player.getY(), player.getZ());
    }

    static EntityAction parseSpawnTelegraph(JsonObject json, String contextId) {
        // Wind-up duration over which the contracting reticle plays.
        final int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 20;
        final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 3.0f;
        // "self", "look", or "impact" — same semantics as spawn_sword_rain.
        final String origin = json.has("origin") ? json.get("origin").getAsString() : "self";
        final String effectType = json.has("effect_type") ? json.get("effect_type").getAsString() : "";

        // Composable payoff run once at the marker center when the wind-up ends.
        // When present we run it against entities within radius — TargetAction for
        // mobs (caster as actor), EntityAction for player targets — the same
        // fan-out spawn_sword_rain's impact_action uses. Omit for a pure dodge cue.
        JsonObject expireInner = null;
        if (json.has("on_expire")) {
            JsonElement oe = json.get("on_expire");
            if (oe.isJsonObject()) {
                expireInner = oe.getAsJsonObject();
            } else if (oe.isJsonArray()) {
                expireInner = new JsonObject();
                expireInner.addProperty("type", "neoorigins:and");
                expireInner.add("actions", oe.getAsJsonArray());
            }
        }
        final boolean hasExpire = expireInner != null;
        final EntityAction expireEA = hasExpire ? parse(expireInner, contextId) : null;
        final TargetAction expireTA = hasExpire ? TargetActionParser.parse(expireInner, contextId) : null;

        return player -> {
            if (!(player.level() instanceof ServerLevel sl)) return;
            var entity = com.cyberday1.neoorigins.content.ModEntities.TELEGRAPH.get().create(sl);
            if (entity == null) return;

            net.minecraft.world.phys.Vec3 center = resolveProjectileRainCenter(player, origin);
            entity.setPos(center.x, center.y, center.z);
            entity.setRange(radius);
            entity.setEffectType(effectType);
            entity.setMaxLifetime(durationTicks);
            if (hasExpire) {
                entity.setOnExpire((lvl, pos, r, caster) -> {
                    var box = new net.minecraft.world.phys.AABB(
                        pos.x - r, pos.y - 1.5, pos.z - r, pos.x + r, pos.y + 2.5, pos.z + r);
                    java.util.UUID casterId = caster != null ? caster.getUUID() : null;
                    for (net.minecraft.world.entity.LivingEntity target :
                            lvl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
                        if (casterId != null && target.getUUID().equals(casterId)) continue;
                        double dx = target.getX() - pos.x;
                        double dz = target.getZ() - pos.z;
                        if (dx * dx + dz * dz > r * r) continue;
                        if (target instanceof net.minecraft.server.level.ServerPlayer sp) {
                            expireEA.execute(sp);
                        } else if (expireTA != null) {
                            expireTA.execute(target, caster);
                        }
                    }
                });
            }
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
        // block_use / bonemeal dispatches publish a BlockInteractContext (pos only;
        // no level). Use the actor's level (the interacting player shares the block's
        // level) so the block-target verbs (strip/till/path/grow/transform_block)
        // also self-resolve when run as a block_use entity_action — e.g. a
        // right-click "grow" power that bonemeals the clicked crop/grass.
        if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.BlockInteractContext bic
            && fallbackLevel != null) {
            return new BlockTarget(fallbackLevel, bic.pos());
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
            if (AdminConfig.isDebugCompatActions()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[NeoOrigins Compat Debug] Action '" + finalType + "' in " + finalContextId + " is unsupported (no-op)")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        };
    }
}
