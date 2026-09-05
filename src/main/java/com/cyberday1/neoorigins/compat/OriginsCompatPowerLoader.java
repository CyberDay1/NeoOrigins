package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.util.*;

/**
 * Route B compatibility loader.
 *
 * Runs AFTER PowerDataManager (native Route A). Scans the same power paths,
 * identifies Origins power types that Route A skipped, compiles them into
 * CompatPower.Config lambdas using the action/condition engine, and injects
 * them into PowerDataManager via injectExternalPowers().
 */
public class OriginsCompatPowerLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final OriginsCompatPowerLoader INSTANCE = new OriginsCompatPowerLoader();

    private static final java.util.Map<String, Integer> PREV_RESOURCE_VALUES = new java.util.concurrent.ConcurrentHashMap<>();
    /** Edge-detection tracker for action_over_time rising/falling_action transitions.
     *  Key: {@code <playerUUID>:<powerId>}. Value: previous tick's condition result. */
    private static final java.util.Map<String, Boolean> PREV_AOT_CONDITIONS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Edge-detection tracker for resource hud_render.condition show/hide transitions.
     *  Key: {@code <playerUUID>:rcond:<resourceKey>}. Value: previous tick's condition result. */
    private static final java.util.Map<String, Boolean> PREV_RENDER_CONDITIONS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Edge-detection tracker for synthetic cooldown bars (Route B active_self hud_render).
     *  Key: {@code <playerUUID>:cdbar:<powerId>}. Value: previous tick's bar value. */
    private static final java.util.Map<String, Integer> PREV_COOLDOWN_BAR = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Safety-net tracker for the gamemode auto-revert (see {@link #parseActionOverTime}).
     * Records the player's gamemode at the moment a gamemode-forcing, condition-gated
     * action_over_time's gate FIRST becomes satisfied, so it can be restored when the
     * gate transitions back to unsatisfied. Only populated for the narrow case of a
     * repeating action that forces {@code gamemode} and the pack defines no
     * {@code falling_action} of its own.
     * Key: {@code <playerUUID>:<powerId>}. Value: remembered {@link net.minecraft.world.level.GameType}. */
    private static final java.util.Map<String, net.minecraft.world.level.GameType> GAMEMODE_REVERT =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Power types that Route B handles unconditionally (Route A SKIPs these).
     *
     * <p>Public because it is the Route B half of the authorable legacy power-type
     * surface: Route B builds a {@link CompatPower.Config} directly, so these ids
     * never become a native power type and there is no {@code PowerTypes}
     * registration to discover them through.
     * {@link OriginsFormatDetector#legacyPowerTypeSurface()} unions this with
     * {@link #CONDITIONED_ROUTE_B_TYPES} and the Route A set to build the schema's
     * {@code type} enum, and {@code PowerEnumCheck} asserts both directions against
     * the {@link #parseRouteB} case labels — so a new case that is not listed here,
     * or a line here with no case, is a build failure.
     */
    public static final Set<String> ROUTE_B_TYPES = Set.of(
        "origins:active_self",           "apace:active_self",
        "origins:action_over_time",      "apace:action_over_time",
        "origins:action_on_callback",    "apace:action_on_callback",
        "origins:resource",              "apace:resource",
        "origins:toggle",                "apace:toggle",
        "origins:conditioned_attribute", "apace:conditioned_attribute",
        "origins:conditioned_status_effect", "apace:conditioned_status_effect",
        "origins:action_on_being_hit",   "apace:action_on_being_hit",
        "origins:self_action_when_hit",  "apace:self_action_when_hit",
        "origins:self_action_on_hit",    "apace:self_action_on_hit",
        "neoorigins:self_action_on_hit",
        "origins:action_on_hit",         "apace:action_on_hit",
        "origins:damage_over_time",      "apace:damage_over_time",
        "origins:action_on_kill",        "apace:action_on_kill",
        // Phase 3: New Route B types
        "origins:fire_projectile",       "apace:fire_projectile",
        "origins:target_action_on_hit",  "apace:target_action_on_hit",
        "origins:self_action_on_kill",   "apace:self_action_on_kill",
        "origins:launch",               "apace:launch",
        "origins:entity_glow",          "apace:entity_glow",
        "origins:self_glow",            "apace:self_glow",
        "origins:prevent_death",        "apace:prevent_death",
        "origins:action_when_hit",      "apace:action_when_hit",
        "origins:action_when_damage_taken", "apace:action_when_damage_taken",
        "origins:attacker_action_when_hit", "apace:attacker_action_when_hit",
        "origins:action_on_land",       "apace:action_on_land",
        // Phase 5: Event-based powers (loaded here, events handled by CompatEventPowers)
        "origins:prevent_item_use",     "apace:prevent_item_use",
        "origins:restrict_armor",       "apace:restrict_armor",
        "origins:prevent_sleep",        "apace:prevent_sleep",
        "origins:prevent_block_use",    "apace:prevent_block_use",
        "origins:prevent_entity_use",   "apace:prevent_entity_use",
        "origins:modify_food",          "apace:modify_food",
        "origins:modify_jump",          "apace:modify_jump",
        "origins:prevent_sprinting",    "apace:prevent_sprinting",
        "origins:modify_crafting",      "apace:modify_crafting",
        "origins:modify_lava_speed",    "apace:modify_lava_speed",
        "origins:modify_xp_gain",       "apace:modify_xp_gain",
        "origins:shaking",              "apace:shaking",
        "apoli:overlay",
        "origins:modify_status_effect_amplifier", "apace:modify_status_effect_amplifier",
        "origins:modify_status_effect_duration", "apace:modify_status_effect_duration",
        "origins:modify_healing",       "apace:modify_healing",
        "origins:action_on_death",      "apace:action_on_death",
        "origins:modify_falling",       "apace:modify_falling",
        "origins:modify_fall_damage",   "apace:modify_fall_damage",
        "origins:modify_velocity",      "apace:modify_velocity",
        // Phase 8: Origins++ compat
        "origins:conditioned_restrict_armor", "apace:conditioned_restrict_armor",
        "origins:freeze",               "apace:freeze",
        "origins:modify_harvest",       "apace:modify_harvest",
        "origins:recipe",               "apace:recipe",
        "origins:prevent_game_event",   "apace:prevent_game_event",
        // v2.1.4: translateExhaust returned a single set-op modifier (~268x
        // food drain per tick); Route B handles it correctly as a periodic
        // causeFoodExhaustion(amount) call.
        "origins:exhaust",              "apace:exhaust",
        // Apoli cooldown power: a countdown resource armed by trigger_cooldown
        // and read back via resource conditions / hud_render (previously
        // silently dropped — the Chaotic Chemist immunity-shot pattern).
        "origins:cooldown",             "apace:cooldown",
        // Issue #110 follow-up (Origins++ 2.4)
        "origins:swimming",              "apace:swimming",
        "origins:action_on_being_used",  "apace:action_on_being_used",
        "origins:prevent_block_selection", "apace:prevent_block_selection"
    );

    private static final Set<String> MULTIPLE_META_KEYS = OriginsMultipleExpander.META_KEYS;

    /** Check if a raw type string (before canonicalization) is handled by Route B. */
    public static boolean isRouteBType(String rawType) {
        return ROUTE_B_TYPES.contains(rawType);
    }

    /**
     * Headless test hook: compile a Route B power and return whether a Config was
     * produced (non-null = the power LOADS; null = it would be dropped). Only the
     * server-free handlers (no registry/level access at parse time) are safe to
     * call this way — used by {@link com.cyberday1.neoorigins.dev.CompatTestHarness}
     * to prove modify_fall_damage powers are no longer silently dropped.
     */
    public static boolean compilesForTest(Identifier id, String type, JsonObject json) {
        return INSTANCE.parseRouteB(id, type, json) != null;
    }

    private static final FileToIdConverter FILE_CONVERTER  = FileToIdConverter.json("origins/powers");
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("powers");

    // ---- SimplePreparableReloadListener ----

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        scanConverter(FILE_CONVERTER,  rm, map);
        scanConverter(COMPAT_CONVERTER, rm, map);
        return map;
    }

    private void scanConverter(FileToIdConverter converter, ResourceManager rm,
                                Map<Identifier, JsonElement> map) {
        for (var entry : converter.listMatchingResources(rm).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id     = converter.fileToId(fileId);
            if (map.containsKey(id)) continue;
            if (converter == COMPAT_CONVERTER && NeoOrigins.MOD_ID.equals(id.getNamespace())) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("[CompatB] Error reading {}", fileId, e);
            }
        }
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager rm, ProfilerFiller profiler) {
        // Open a warning-collection session so parser failures dedupe into a
        // single summary block instead of one WARN per occurrence. Closed
        // (and the summary emitted) at the bottom of this method, with a
        // try/finally fallback so a mid-loop throw can't leak the session.
        CompatWarningCollector.beginSession();
        try {

        // Clear event power state from the previous reload cycle
        CompatPlayerState.clearAll();
        ModifyCraftingRegistry.clearAll();
        ModifyFoodRegistry.clearAll();
        NumericModifierRegistry.clearAll();
        CompatAttachments.clearResourceMeta();
        CompatAttachments.clearCooldownDurations();
        ResourceBackingRouter.clearWarnings();
        com.cyberday1.neoorigins.service.InlineRecipeRegistry.resetPending();
        com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.clear();

        // Rewrite apoli:/apugli: power types to the canonical origins: namespace
        // before expansion + dispatch, so packs that use the Apoli namespace are
        // recognized by ROUTE_B_TYPES (and apoli:multiple is expanded).
        for (JsonElement el : data.values()) {
            if (el.isJsonObject()) {
                OriginsFormatDetector.canonicalizePowerType(el.getAsJsonObject());
                // Route B's half of the neoorigins:-spelled-legacy-type salvage.
                // This loader re-reads the resources itself, so PowerDataManager's
                // call cannot fix these up for us.
                OriginsFormatDetector.salvageLegacyPowerSpelling(el.getAsJsonObject());
            }
        }

        // Inline-expand any origins:multiple entries so sub-power JSONs are accessible.
        Map<Identifier, JsonObject> expanded = inlineExpand(data);

        Map<Identifier, PowerHolder<?>> injected = new HashMap<>();
        // Track new synthetic IDs to add to MULTIPLE_EXPANSION_MAP
        Map<Identifier, List<Identifier>> newExpansions = new HashMap<>();

        for (var entry : expanded.entrySet()) {
            Identifier id   = entry.getKey();
            JsonObject json = entry.getValue();
            // Cover sub-powers emitted by multiple-expansion, which may still
            // carry apoli:/apugli: types.
            String type = OriginsFormatDetector.canonicalizePowerType(json);
            type = OriginsFormatDetector.salvageLegacyPowerSpelling(json);

            // modify_damage_taken/dealt are Route A types normally, but when a
            // condition is present we fall through to Route B so the condition
            // can gate the damage modifier — native ModifyDamagePower has no
            // condition support.
            boolean conditionedModifyDamage = isModifyDamageTakenType(type) && json.has("condition");
            if (!ROUTE_B_TYPES.contains(type) && !conditionedModifyDamage) continue;
            // Route A already loaded this ID — skip
            if (PowerDataManager.INSTANCE.hasPower(id)) continue;

            try {
                CompatPower.Config config = parseRouteB(id, type, json);
                if (config == null) {
                    CompatTranslationLog.skip(id, type, "Route B: no handler produced a config");
                    continue;
                }
                Component powerName = extractComponent(json, "name");
                Component powerDesc = extractComponent(json, "description");
                boolean powerHidden = readHiddenFlag(json);
                injected.put(id, new PowerHolder<>(id, CompatPower.INSTANCE, config, powerName, powerDesc, powerHidden));
                CompatTranslationLog.pass(id, type + " -> Route B compiled");
                NeoOrigins.LOGGER.debug("[CompatB] loaded {} ({})", id, type);

                // If this is a synthetic sub-power, update the expansion map.
                // Parent is looked up from the authoritative map recorded at expansion
                // time — the synthetic id join is now "_" (Apoli convention) and can no
                // longer be recovered by splitting the id string.
                Identifier parentId = syntheticParentage.get(id);
                if (parentId != null) {
                    newExpansions.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
                }
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                CompatWarningCollector.recordPowerCompileFailure(id.toString(), type, reason);
                CompatTranslationLog.fail(id, type + ": " + reason);
            }
        }

        // Merge new Route B synthetic IDs into OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP
        // so OriginDataManager includes them in origin power lists.
        for (var entry : newExpansions.entrySet()) {
            List<Identifier> existing = OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP
                .getOrDefault(entry.getKey(), List.of());
            List<Identifier> merged = new ArrayList<>(existing);
            for (Identifier newId : entry.getValue()) {
                if (!merged.contains(newId)) merged.add(newId);
            }
            OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.put(entry.getKey(),
                Collections.unmodifiableList(merged));
        }

        // Inject synthetic powers for well-known Origins built-in power IDs.
        injectWellKnownPowers(injected);

        PowerDataManager.INSTANCE.injectExternalPowers(injected);
        // Bind native (Route A) active powers that declare a pack named-hotkey
        // (e.g. origins:inventory -> neoorigins:extra_inventory) to that key
        // instead of letting them occupy a skill slot. Runs after both routes
        // have populated PowerDataManager and after the reload-start clear().
        registerNativeActiveHotkeys();
        // Flush the deduplicated parser-warning summary (if anything was
        // collected) before the final injection-count line so the summary
        // appears immediately above it in the log.
        CompatWarningCollector.emitSummaryAndEndSession();
        NeoOrigins.LOGGER.info("[CompatB] Injected {} Route B powers", injected.size());
        com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.logSummary();
        } finally {
            // Defensive: if anything above threw, the session is still open
            // and would silently swallow warnings for the rest of the JVM.
            // Close it (no-op if already closed by the normal path).
            if (CompatWarningCollector.isSessionActive()) {
                CompatWarningCollector.emitSummaryAndEndSession();
            }
        }
    }

    /**
     * Bind native active powers that declare a pack-defined named hotkey to that
     * key. A native active power (e.g. {@code neoorigins:extra_inventory}, the
     * translation target of {@code origins:inventory}) normally occupies a skill
     * slot via {@link com.cyberday1.neoorigins.api.power.PowerHolder#occupiesHotkeySlot()}.
     * When its (post-translation) JSON carries a {@code "key"} that is neither a
     * skill slot nor a vanilla input key, we register a {@link PowerKeybindRegistry}
     * binding whose action triggers the holder's own activation, and mark the power
     * so {@link com.cyberday1.neoorigins.service.ActiveOriginService} keeps it out of
     * the skill-slot roster. The holder's own condition gate still applies inside
     * {@code onActivated}, so no condition is duplicated onto the binding.
     */
    /**
     * NeoOrigins shorthand: a numeric {@code "key": N} targets named-hotkey pool
     * slot N directly (canonical translation key {@code key.neoorigins.hotkey.N},
     * 1-indexed). Deterministic — the client pins that declared key to pool slot
     * N-1 (see {@code HotkeyAssignments.set}) rather than the sorted-order fallback
     * used for string keys. A string {@code key} passes through unchanged. Returns
     * {@code fallback} when the element is null/absent.
     */
    private static String normalizeKeyToken(JsonElement el, String fallback) {
        if (el == null || el.isJsonNull()) return fallback;
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return "key.neoorigins.hotkey." + el.getAsInt();
        }
        return el.getAsString();
    }

    private void registerNativeActiveHotkeys() {
        for (var entry : PowerDataManager.INSTANCE.getPowers().entrySet()) {
            Identifier id = entry.getKey();
            PowerHolder<?> holder = entry.getValue();
            if (!holder.isActive()) continue;
            JsonObject raw = PowerDataManager.INSTANCE.getRawPowerJson(id);
            if (raw == null || !raw.has("key")) continue;

            // Classify the declared key the same way parseActiveSelf does
            // (inline — this branch has no KeySpec/classifyKey helper).
            String key = null;
            boolean continuous = false;
            var keyEl = raw.get("key");
            if (keyEl.isJsonPrimitive()) {
                key = normalizeKeyToken(keyEl, key);
            } else if (keyEl.isJsonObject()) {
                var keyObj = keyEl.getAsJsonObject();
                key = keyObj.has("key") ? normalizeKeyToken(keyObj.get("key"), key) : null;
                continuous = keyObj.has("continuous") && keyObj.get("continuous").getAsBoolean();
            }
            if (key == null) continue;

            // Toolbar (creative save/load hotbar) keys must NOT be treated as
            // skill slots — they have no client-side activation and would be
            // silently dropped. Route them through the named-hotkey path instead
            // (see parseActiveSelf for the full rationale / Seer ritual case).
            boolean isToolbarKey = key.contains("loadToolbarActivator")
                || key.contains("saveToolbarActivator");
            boolean isSlotKey = !isToolbarKey && (key.contains("primary_active")
                || key.contains("secondary_active") || key.contains("pickItem"));
            boolean isVanillaInputKey = switch (key) {
                case "key.sneak", "key.use", "key.attack", "key.jump", "key.sprint",
                     "key.forward", "key.back", "key.left", "key.right" -> true;
                default -> false;
            };
            if (isSlotKey) continue;

            final Identifier pid = id;
            if (isVanillaInputKey) {
                // Native active power bound to a vanilla input key (e.g. key.jump for
                // a double-jump). Polled each tick by PowerKeybindRegistry from the
                // server-side input state; registerVanilla records the key tag so the
                // origin info screen shows e.g. "[Jump]" instead of a tag-less passive.
                com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.registerVanillaNative(pid, key, continuous);
                com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.registerVanilla(pid, key);
                NeoOrigins.LOGGER.debug("[CompatB] native active {} bound to vanilla key '{}'", pid, key);
                continue;
            }
            final String namedKey = key;
            // PowerKeybindRegistry.dispatch runs a continuous binding every held
            // tick and a non-continuous one once per press. The action itself
            // cannot tell the two apart when it runs, but continuous is known
            // here, so bake the choice in: only the once-per-press binding may
            // announce a suppression refusal.
            final boolean announceRefusal = !continuous;
            EntityAction openAction = player -> {
                PowerHolder<?> h = PowerDataManager.INSTANCE.getPower(pid);
                if (h == null || !h.isActive()) return;
                if (announceRefusal) h.onActivatedByKeypress(player); else h.onActivated(player);
            };
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.register(namedKey,
                new com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.Binding(
                    pid, openAction, null, 0, continuous, null));
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.markNativeHotkeyPower(pid);
            NeoOrigins.LOGGER.debug("[CompatB] native active {} bound to named hotkey '{}'", pid, namedKey);
        }
    }

    /**
     * Synthetic JSON for well-known Origins built-in power IDs that addon packs
     * reference by ID without providing a file. Static so {@code PowerEnumCheck}
     * can invoke each supplier and assert the emitted target actually exists.
     */
    public static final Map<String, java.util.function.Supplier<JsonObject>> WELL_KNOWN = Map.ofEntries(
            Map.entry("origins:elytra",              () -> json("neoorigins:natural_glide")),
            Map.entry("origins:fire_immunity",       () -> json("neoorigins:prevent_action", "action", "fire")),
            Map.entry("origins:fresh_air",           OriginsCompatPowerLoader::freshAirJson),
            Map.entry("origins:like_water",          () -> json("neoorigins:ignore_water")),
            Map.entry("origins:aquatic",             () -> json("neoorigins:entity_group", "group", "water")),
            Map.entry("origins:water_vision",        () -> waterVisionJson()),
            Map.entry("origins:aqua_affinity",       () -> json("neoorigins:underwater_mining_speed")),
            Map.entry("origins:conduit_power_on_land", () -> conduitPowerJson()),
            Map.entry("origins:air_from_potions",    () -> json("neoorigins:water_breathing")),
            Map.entry("origins:water_breathing",     () -> json("neoorigins:water_breathing")),
            Map.entry("origins:swim_speed",          () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:water_movement_efficiency", "amount", 0.5, "operation", "add_value")),
            Map.entry("origins:night_vision",        () -> json("neoorigins:night_vision")),
            Map.entry("origins:slow_falling",        () -> json("neoorigins:prevent_action", "action", "fall_damage")),
            Map.entry("origins:climbing",            () -> json("neoorigins:wall_climbing")),
            Map.entry("origins:shulker_inventory",   () -> json("neoorigins:extra_inventory")),
            Map.entry("origins:phantomize",          () -> json("neoorigins:phantom_form")),
            Map.entry("origins:translucent",         () -> json("neoorigins:model_color", "red", 1.0, "green", 1.0, "blue", 1.0, "alpha", 0.5)),
            // ── Felvaxian / common addon references ──
            Map.entry("origins:carnivore",           () -> json("neoorigins:food_restriction", "mode", "whitelist", "item_tag", "neoorigins:meat_foods")),
            Map.entry("origins:cat_vision",           () -> json("neoorigins:night_vision")),
            Map.entry("origins:fall_immunity",        () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:generic.safe_fall_distance", "amount", 1000.0, "operation", "add_value")),
            // strong_ankles: Feline "you take less fall damage" — real Origins
            // halves it (apoli:modify_fall_damage multiply_base_additive -0.5).
            // Route it through the native mod_fall_damage seam (same one
            // action_on_event/modify_fall_damage consume) so base*(1-0.5)=base*0.5.
            Map.entry("origins:strong_ankles",        () -> strongAnklesJson()),
            Map.entry("origins:launch_into_air",      () -> json("neoorigins:active_ability")),
            Map.entry("origins:light_armor",          () -> json("neoorigins:restrict_armor", "armor_class", "heavy")),
            Map.entry("origins:nine_lives",           () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:generic.max_health", "amount", -2.0, "operation", "add_value")),
            Map.entry("origins:no_shield",            () -> json("neoorigins:prevent_action", "action", "shield")),
            Map.entry("origins:vegetarian",           () -> json("neoorigins:food_restriction", "mode", "blacklist", "item_tag", "neoorigins:meat_foods")),
            Map.entry("origins:weak_arms",            () -> json("neoorigins:break_speed_modifier", "multiplier", 0.5)),
            // ── Rock/earthen addon references (e.g. "wou" rock_human) ──
            // strong_arms: real Origins lets bare hands mine tool-required
            // blocks as the proper tool would. bare_hand_tool maps exactly —
            // point it at a netherite pickaxe (stone/ore tier + speed).
            Map.entry("origins:strong_arms",          () -> json("neoorigins:bare_hand_tool", "tool", "minecraft:netherite_pickaxe")),
            // strong_arms_break_speed: the companion break-speed boost. The
            // break_speed_modifier codec field is "multiplier" (values >1 speed
            // up, <1 slow down — cf. weak_arms above at 0.5).
            Map.entry("origins:strong_arms_break_speed", () -> json("neoorigins:break_speed_modifier", "multiplier", 5.0)),
            Map.entry("origins:natural_armor",        () -> naturalArmorJson()),
            Map.entry("origins:more_exhaustion",      () -> moreExhaustionJson()),
            Map.entry("origins:master_of_webs",       () -> json("neoorigins:wall_climbing")),
            Map.entry("origins:arthropod",            () -> json("neoorigins:entity_group", "group", "arthropod")),
            Map.entry("origins:fragile",              () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:generic.max_health", "amount", -6.0, "operation", "add_value")),
            Map.entry("origins:phasing",              () -> json("neoorigins:phantom_form")),
            Map.entry("origins:burn_in_daylight",     () -> json("neoorigins:condition_passive")),
            Map.entry("origins:damage_from_potions",  () -> json("neoorigins:effect_immunity")),
            Map.entry("origins:more_kinetic_damage",  () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:generic.safe_fall_distance", "amount", -2.0, "operation", "add_value")),
            Map.entry("origins:throw_ender_pearl",    () -> json("neoorigins:active_ability")),
            Map.entry("origins:pumpkin_hate",         () -> json("neoorigins:restrict_armor", "armor_class", "pumpkin")),
            Map.entry("origins:hotblooded",           () -> json("neoorigins:effect_immunity")),
            Map.entry("origins:water_vulnerability",  () -> json("neoorigins:condition_passive")),
            Map.entry("origins:flame_particles",      () -> json("neoorigins:particle", "particle", "minecraft:flame")),
            Map.entry("origins:nether_spawn",         () -> netherSpawnJson())
        );

    /**
     * Registers synthetic PowerHolders for the {@link #WELL_KNOWN} ids that the
     * pack did not already supply.
     */
    private void injectWellKnownPowers(Map<Identifier, PowerHolder<?>> injected) {
        for (var entry : WELL_KNOWN.entrySet()) {
            Identifier id = Identifier.parse(entry.getKey());
            if (PowerDataManager.INSTANCE.hasPower(id) || injected.containsKey(id)) continue;
            try {
                JsonObject powerJson = entry.getValue().get();
                String typeStr = powerJson.get("type").getAsString();
                Identifier typeId = Identifier.parse(typeStr);
                com.cyberday1.neoorigins.api.power.PowerType<?> powerType =
                    com.cyberday1.neoorigins.power.registry.PowerTypes.get(typeId);
                if (powerType != null) {
                    injectViaNativeCodec(id, powerType, powerJson, injected);
                    NeoOrigins.LOGGER.debug("[CompatSynth] Registered well-known power {} -> {}", id, typeStr);
                } else {
                    CompatPower.Config config = parseRouteB(id, typeStr, powerJson);
                    if (config != null) {
                        injected.put(id, new PowerHolder<>(id, CompatPower.INSTANCE, config,
                            net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty()));
                        NeoOrigins.LOGGER.debug("[CompatSynth] Registered well-known power {} -> {} (Route B)", id, typeStr);
                    }
                }
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn("[CompatSynth] Failed to register well-known power {}: {}", id, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <C extends com.cyberday1.neoorigins.api.power.PowerConfiguration> void injectViaNativeCodec(
            Identifier id, com.cyberday1.neoorigins.api.power.PowerType<C> type,
            JsonObject json, Map<Identifier, PowerHolder<?>> target) {
        JsonObject configJson = json.deepCopy();
        configJson.addProperty("_power_id", id.toString());
        type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, configJson)
            .resultOrPartial(err -> NeoOrigins.LOGGER.warn("[CompatSynth] codec error for {}: {}", id, err))
            .ifPresent(config -> target.put(id, new PowerHolder<>(id, type, config,
                net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty())));
    }

    private static JsonObject json(String type) {
        JsonObject o = new JsonObject(); o.addProperty("type", type); return o;
    }
    private static JsonObject json(String type, String k1, String v1) {
        JsonObject o = json(type); o.addProperty(k1, v1); return o;
    }
    private static JsonObject json(String type, String k1, double v1, String k2, double v2, String k3, double v3, String k4, double v4) {
        JsonObject o = json(type); o.addProperty(k1, v1); o.addProperty(k2, v2); o.addProperty(k3, v3); o.addProperty(k4, v4); return o;
    }
    private static JsonObject json(String type, String k1, String v1, String k2, double v2, String k3, String v3) {
        JsonObject o = json(type); o.addProperty(k1, v1); o.addProperty(k2, v2); o.addProperty(k3, v3); return o;
    }

    private static JsonObject json(String type, String k1, String v1, String k2, String v2) {
        JsonObject o = json(type); o.addProperty(k1, v1); o.addProperty(k2, v2); return o;
    }
    private static JsonObject json(String type, String k1, double v1) {
        JsonObject o = json(type); o.addProperty(k1, v1); return o;
    }

    /**
     * Generates an Origins-format {@code prevent_sleep} JSON with a height
     * block_condition. Vanilla Origins' fresh_air prevents sleep below Y 86.
     * Uses Origins type so it falls through to Route B's parsePreventSleep.
     */
    private static com.google.gson.JsonObject freshAirJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("type", "origins:prevent_sleep");
        o.addProperty("set_spawn_point", true);
        com.google.gson.JsonObject blockCond = new com.google.gson.JsonObject();
        blockCond.addProperty("type", "origins:height");
        blockCond.addProperty("comparison", "<");
        blockCond.addProperty("compare_to", 86);
        o.add("block_condition", blockCond);
        return o;
    }

    /**
     * origins:conduit_power_on_land — the conduit effect set granted anywhere.
     * Mirrors the repo's own merling_ascended_conduit.json.
     */
    private static com.google.gson.JsonObject conduitPowerJson() {
        com.google.gson.JsonObject o = json("neoorigins:persistent_effect");
        o.addProperty("toggleable", false);
        o.addProperty("show_particles", false);
        com.google.gson.JsonArray effects = new com.google.gson.JsonArray();
        for (String id : new String[] {
                "minecraft:water_breathing", "minecraft:night_vision", "minecraft:haste" }) {
            com.google.gson.JsonObject e = new com.google.gson.JsonObject();
            e.addProperty("effect", id);
            e.addProperty("amplifier", 0);
            effects.add(e);
        }
        o.add("effects", effects);
        return o;
    }

    /**
     * origins:nether_spawn — respawn in the Nether. modify_player_spawn requires
     * a nested location object; a flat json() overload cannot express it.
     */
    private static com.google.gson.JsonObject netherSpawnJson() {
        com.google.gson.JsonObject o = json("neoorigins:modify_player_spawn");
        com.google.gson.JsonObject loc = new com.google.gson.JsonObject();
        loc.addProperty("dimension", "minecraft:the_nether");
        o.add("location", loc);
        return o;
    }

    /**
     * origins:water_vision — clear sight underwater. Upstream this is not a
     * power type but a power instance: Origins' own water_vision.json is an
     * {@code origins:toggle_night_vision} gated on being submerged in water.
     *
     * <p>It was previously mapped onto {@code neoorigins:lava_vision}, which
     * keys off the camera's fluid and so never fired in water at all.
     */
    private static com.google.gson.JsonObject waterVisionJson() {
        com.google.gson.JsonObject o = json("neoorigins:night_vision");
        com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
        cond.addProperty("type", "neoorigins:submerged_in");
        cond.addProperty("fluid", "minecraft:water");
        o.add("condition", cond);
        return o;
    }

    /**
     * origins:natural_armor — an always-on Resistance I (amplifier 0) via
     * persistent_effect, mirroring NeoOrigins' own *_natural_armor builtins
     * (e.g. golem_natural_armor). Referenced by ID by rock/earthen addon packs.
     */
    private static com.google.gson.JsonObject naturalArmorJson() {
        com.google.gson.JsonObject o = json("neoorigins:persistent_effect");
        o.addProperty("toggleable", false);
        com.google.gson.JsonObject eff = new com.google.gson.JsonObject();
        eff.addProperty("effect", "minecraft:resistance");
        eff.addProperty("amplifier", 0);
        eff.addProperty("ambient", true);
        eff.addProperty("show_particles", false);
        eff.addProperty("show_icon", false);
        com.google.gson.JsonArray effects = new com.google.gson.JsonArray();
        effects.add(eff);
        o.add("effects", effects);
        return o;
    }

    /**
     * origins:more_exhaustion — the player tires faster. Hooks the
     * {@code mod_exhaustion} value event and multiplies incoming exhaustion,
     * matching real Origins' apoli:modify_exhaustion drawback.
     */
    private static com.google.gson.JsonObject moreExhaustionJson() {
        com.google.gson.JsonObject o = json("neoorigins:action_on_event");
        o.addProperty("event", "mod_exhaustion");
        com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
        mod.addProperty("operation", "multiply_total");
        mod.addProperty("value", 2.0);
        o.add("modifier", mod);
        return o;
    }

    /** origins:strong_ankles (Feline) — halved fall damage, via the native
     *  mod_fall_damage seam. multiply_base_additive -0.5 => base*(1-0.5). */
    private static com.google.gson.JsonObject strongAnklesJson() {
        com.google.gson.JsonObject o = json("neoorigins:action_on_event");
        o.addProperty("event", "mod_fall_damage");
        com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
        mod.addProperty("operation", "multiply_base_additive");
        mod.addProperty("value", -0.5);
        o.add("modifier", mod);
        return o;
    }

    /**
     * Inline-expand origins:multiple entries in the raw data map.
     * Returns a flat map of id → JsonObject covering both direct powers and sub-powers.
     * Does NOT call OriginsMultipleExpander (avoids touching its state twice).
     */
    /**
     * Child synthetic id -> immediate parent id, recorded during {@link #inlineExpand}
     * so synthetic sub-powers can be tied back to their parent without parsing the id
     * string. The synthetic id now joins parent + "_" + subkey (Apoli convention), so
     * the separator is ambiguous and the parent can no longer be recovered by splitting.
     */
    private final Map<Identifier, Identifier> syntheticParentage = new HashMap<>();

    /**
     * Canonical synthetic id -> its pre-2.2.8 slash-form id, recorded during
     * {@link #expandMultiple} so a nested sub-power can chain its parent's legacy
     * form. Feeds {@link CompatAttachments#registerLegacySyntheticId} so datapacks
     * that still reference bars/toggles by the old "parent/subkey" id keep working.
     */
    private final Map<Identifier, String> syntheticLegacyIds = new HashMap<>();

    private Map<Identifier, JsonObject> inlineExpand(Map<Identifier, JsonElement> data) {
        syntheticParentage.clear();
        syntheticLegacyIds.clear();
        CompatAttachments.clearLegacySyntheticIds();
        Map<Identifier, JsonObject> result = new HashMap<>();
        for (var entry : data.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject json = entry.getValue().getAsJsonObject();
            String type = OriginsFormatDetector.getType(json);
            if (OriginsMultipleExpander.isMultipleType(type)) {
                expandMultiple(entry.getKey(), json, result);
            } else {
                result.put(entry.getKey(), json);
            }
        }
        return result;
    }

    private void expandMultiple(Identifier parentId, JsonObject json, Map<Identifier, JsonObject> out) {
        expandMultiple(parentId, json, out, readHiddenFlag(json));
    }

    private void expandMultiple(Identifier parentId, JsonObject json,
                                Map<Identifier, JsonObject> out, boolean parentHidden) {
        for (var subEntry : json.entrySet()) {
            if (MULTIPLE_META_KEYS.contains(subEntry.getKey())) continue;
            if (!subEntry.getValue().isJsonObject()) continue;
            JsonObject subJson = subEntry.getValue().getAsJsonObject();
            Identifier syntheticId = Identifier.fromNamespaceAndPath(
                parentId.getNamespace(), parentId.getPath() + "_" + subEntry.getKey()
            );
            syntheticParentage.put(syntheticId, parentId);
            // Record the pre-2.2.8 slash-form id (chaining the parent's own legacy
            // form for nested multiples) so datapacks that still reference this
            // sub-power by "parent/subkey" resolve to the canonical underscore id.
            String legacyParent = syntheticLegacyIds.getOrDefault(parentId, parentId.toString());
            String legacyId = legacyParent + "/" + subEntry.getKey();
            syntheticLegacyIds.put(syntheticId, legacyId);
            CompatAttachments.registerLegacySyntheticId(legacyId, syntheticId.toString());
            String subType = OriginsFormatDetector.getType(subJson);
            if (OriginsMultipleExpander.isMultipleType(subType)) {
                // A hidden parent hides the whole subtree — carry the flag down.
                expandMultiple(syntheticId, subJson, out, parentHidden || readHiddenFlag(subJson));
            } else {
                // Resolve *:* self-references before storing. In Origins/Apoli,
                // "*:*_subkey" within a multiple means the sibling sub-power
                // whose synthetic ID is "parentId/subkey".
                subJson = resolveSelfReferences(subJson, parentId);
                // Origins lists a hidden origins:multiple as one suppressed entry;
                // we list its synthetic sub-powers instead, so the parent's
                // "hidden" must ride down onto each sub-power or the intent is
                // lost and they surface individually in the info panel (#Deano).
                if (parentHidden && !readHiddenFlag(subJson)) subJson.addProperty("hidden", true);
                out.put(syntheticId, subJson);
            }
        }
    }

    /** True if {@code json} carries a boolean {@code "hidden": true}. */
    private static boolean readHiddenFlag(JsonObject json) {
        return json.has("hidden") && json.get("hidden").isJsonPrimitive()
            && json.get("hidden").getAsJsonPrimitive().isBoolean()
            && json.get("hidden").getAsBoolean();
    }

    /** Delegates to shared self-reference resolver in OriginsMultipleExpander. */
    private static JsonObject resolveSelfReferences(JsonObject json, Identifier parentId) {
        return OriginsMultipleExpander.resolveSelfReferences(json, parentId);
    }

    private static Component extractComponent(JsonObject json, String field) {
        if (!json.has(field)) return Component.empty();
        JsonElement el = json.get(field);
        if (el.isJsonPrimitive()) return Component.translatable(el.getAsString());
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text"))      return Component.literal(obj.get("text").getAsString());
            if (obj.has("translate")) return Component.translatable(obj.get("translate").getAsString());
        }
        return Component.empty();
    }

    // ---- Per-type parsers ----

    private CompatPower.Config parseRouteB(Identifier id, String type, JsonObject json) {
        return switch (type) {
            case "origins:active_self",                "apace:active_self"                -> parseActiveSelf(id, json);
            case "origins:action_over_time",           "apace:action_over_time"           -> parseActionOverTime(id, json);
            case "origins:action_on_callback",         "apace:action_on_callback"         -> parseActionOnCallback(id, json);
            case "origins:resource",                   "apace:resource"                   -> parseResource(id, json);
            case "origins:toggle",                     "apace:toggle"                     -> parseToggle(id, json);
            case "origins:conditioned_attribute",      "apace:conditioned_attribute"      -> parseConditionedAttribute(id, json);
            case "origins:conditioned_status_effect",  "apace:conditioned_status_effect"  -> parseConditionedStatusEffect(id, json);
            case "origins:action_on_being_hit",        "apace:action_on_being_hit",
                 "origins:self_action_when_hit",       "apace:self_action_when_hit",
                 "origins:action_when_hit",            "apace:action_when_hit",
                 "origins:action_when_damage_taken",   "apace:action_when_damage_taken",
                 "origins:attacker_action_when_hit",   "apace:attacker_action_when_hit"   -> parseSelfActionWhenHit(id, json);
            // self_action_on_hit / action_on_hit fire when the HOLDER DEALS damage —
            // a different direction from the "when hit" group above.
            case "origins:self_action_on_hit",         "apace:self_action_on_hit",
                 "neoorigins:self_action_on_hit",
                 "origins:action_on_hit",              "apace:action_on_hit"              -> parseSelfActionOnHit(id, json);
            case "origins:damage_over_time",           "apace:damage_over_time"           -> parseDamageOverTime(id, json);
            // Phase 3: New Route B types
            case "origins:fire_projectile",            "apace:fire_projectile"            -> parseFireProjectile(id, json);
            case "origins:target_action_on_hit",       "apace:target_action_on_hit"       -> parseTargetActionOnHit(id, json);
            case "origins:self_action_on_kill",        "apace:self_action_on_kill",
                 "origins:action_on_kill",              "apace:action_on_kill"             -> parseSelfActionOnKill(id, json);
            case "origins:launch",                     "apace:launch"                     -> parseLaunch(id, json);
            case "origins:entity_glow",                "apace:entity_glow",
                 "origins:self_glow",                  "apace:self_glow"                  -> parseEntityGlow(id, json);
            case "origins:prevent_death",              "apace:prevent_death"              -> parsePreventDeath(id, json);
            case "origins:action_on_land",             "apace:action_on_land"             -> parseActionOnLand(id, json);
            // Phase 5: Event-based powers
            case "origins:prevent_item_use",           "apace:prevent_item_use"           -> parsePreventItemUse(id, json);
            case "origins:restrict_armor",             "apace:restrict_armor"             -> parseRestrictArmor(id, json);
            case "origins:prevent_sleep",              "apace:prevent_sleep"              -> parsePreventSleep(id, json);
            case "origins:prevent_block_use",          "apace:prevent_block_use"          -> parsePreventBlockUse(id, json);
            case "origins:prevent_entity_use",         "apace:prevent_entity_use"         -> parsePreventEntityUse(id, json);
            case "origins:modify_food",                "apace:modify_food"                -> parseModifyFood(id, json);
            case "origins:modify_jump",                "apace:modify_jump"                -> parseModifyJump(id, json);
            case "origins:prevent_sprinting",          "apace:prevent_sprinting"          -> parsePreventSprinting(id, json);
            case "origins:modify_crafting",            "apace:modify_crafting"            -> parseModifyCrafting(id, json);
            case "origins:modify_lava_speed",          "apace:modify_lava_speed"          -> parseNumericModifier(id, json, NumericModifierRegistry.Kind.LAVA_SPEED);
            case "origins:modify_xp_gain",             "apace:modify_xp_gain"             -> parseNumericModifier(id, json, NumericModifierRegistry.Kind.XP_GAIN);
            // Conditioned modify_damage_taken/dealt — only dispatched here when a condition is present.
            case "origins:modify_damage_taken",        "apace:modify_damage_taken"        -> parseConditionedModifyDamageTaken(id, json);
            case "origins:modify_damage_dealt",        "apace:modify_damage_dealt"        -> parseConditionedModifyDamageDealt(id, json);
            case "origins:shaking",                    "apace:shaking"                    -> parseShaking(id, json);
            case "apoli:overlay"                                                          -> parseOverlay(id, json);
            case "origins:modify_status_effect_amplifier", "apace:modify_status_effect_amplifier" -> parseModifyEffectAmplifier(id, json);
            case "origins:modify_status_effect_duration", "apace:modify_status_effect_duration" -> parseModifyEffectDuration(id, json);
            case "origins:modify_healing",             "apace:modify_healing"             -> parseModifyHealing(id, json);
            case "origins:action_on_death",            "apace:action_on_death"            -> parseActionOnDeath(id, json);
            case "origins:modify_falling",             "apace:modify_falling"             -> parseModifyFalling(id, json);
            case "origins:modify_fall_damage",         "apace:modify_fall_damage"         -> parseModifyFallDamage(id, json);
            case "origins:modify_velocity",            "apace:modify_velocity"            -> parseModifyVelocity(id, json);
            // Phase 8: Origins++ compat
            case "origins:conditioned_restrict_armor", "apace:conditioned_restrict_armor" -> parseConditionedRestrictArmor(id, json);
            case "origins:freeze",                     "apace:freeze"                     -> parseFreeze(id, json);
            case "origins:modify_harvest",             "apace:modify_harvest"             -> parseModifyHarvest(id, json);
            case "origins:recipe",                     "apace:recipe"                     -> parseRecipe(id, json);
            case "origins:prevent_game_event",         "apace:prevent_game_event"         -> parsePreventGameEvent(id, json);
            case "origins:exhaust",                    "apace:exhaust"                    -> parseExhaust(id, json);
            case "origins:cooldown",                   "apace:cooldown"                   -> parseCooldown(id, json);
            // Issue #110 follow-up (Origins++ 2.4)
            case "origins:swimming",                   "apace:swimming"                   -> parseSwimming(id, json);
            case "origins:action_on_being_used",       "apace:action_on_being_used"       -> parseActionOnBeingUsed(id, json);
            case "origins:prevent_block_selection",    "apace:prevent_block_selection"    -> parsePreventBlockSelection(id, json);
            default -> null;
        };
    }

    /**
     * The Route B types that are CONDITIONALLY dispatched: {@code parseRouteB} has a
     * case for each, but {@link #apply} only routes them here when the power carries a
     * {@code condition} (native {@code ModifyDamagePower} has no condition support).
     * Without one they are Route A types instead — {@code origins:modify_damage_taken}
     * / {@code origins:modify_damage_dealt} have Route A cases, so those load either
     * way; the {@code apace:} spellings do not, so an UNconditioned
     * {@code apace:modify_damage_taken} is still dropped.
     *
     * <p>They are part of the schema's authorable surface regardless, since a JSON
     * Schema {@code type} enum cannot express "valid only when a sibling key is
     * present". The narrow over-advertisement is the unconditioned {@code apace:}
     * pair; the alternative is rejecting every conditioned file, of which the legacy
     * corpus has plenty.
     */
    public static final Set<String> CONDITIONED_ROUTE_B_TYPES = Set.of(
        "origins:modify_damage_taken", "apace:modify_damage_taken",
        "origins:modify_damage_dealt", "apace:modify_damage_dealt"
    );

    private static boolean isModifyDamageTakenType(String type) {
        return CONDITIONED_ROUTE_B_TYPES.contains(type);
    }

    private CompatPower.Config parseConditionedModifyDamageTaken(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Extract the multiplier from the Origins modifier(s). All operations
        // collapse to (1 + value) — same lossy mapping as Route A's translateModifyDamage.
        // parseModifierList accepts both singular "modifier" and plural "modifiers";
        // parseSingleModifier accepts both "value" and "amount" per entry. Mirrors
        // the precedent set by parseModifyFood / parseNumericModifier so real
        // Apoli packs (which commonly emit `modifiers`/`amount`) don't silently no-op.
        float multiplier = collapseDamageModifiers(parseModifierList(json, "modifier"));

        // Optional damage type filter — msgId-based, mirrors native ModifyDamagePower.
        String damageTypeFilter = null;
        Identifier damageTypeKeyFilter = null;
        if (json.has("damage_condition") && json.get("damage_condition").isJsonObject()) {
            JsonObject dc = json.getAsJsonObject("damage_condition");
            String dcType = dc.has("type") ? dc.get("type").getAsString() : "";
            if (("origins:name".equals(dcType) || "apace:name".equals(dcType)) && dc.has("name")) {
                damageTypeFilter = dc.get("name").getAsString();
            } else if (("origins:type".equals(dcType) || "apace:type".equals(dcType)) && dc.has("damage_type")) {
                damageTypeKeyFilter = Identifier.parse(dc.get("damage_type").getAsString());
            }
        }

        EntityCondition condition = parseConditionField(json, "condition", idStr);

        final float  finalMultiplier = multiplier;
        final String finalDmgFilter  = damageTypeFilter;
        final Identifier finalDmgTypeKey = damageTypeKeyFilter;

        return CompatPower.Config.builder()
            .onIncomingDamage(event -> {
                if (!(event.getEntity() instanceof ServerPlayer sp)) return;
                if (!condition.test(sp)) return;
                if (finalDmgFilter != null
                        && !event.getSource().getMsgId().equalsIgnoreCase(finalDmgFilter)) {
                    return;
                }
                if (finalDmgTypeKey != null) {
                    var typeKey = event.getSource().typeHolder().unwrapKey().orElse(null);
                    if (typeKey == null || !typeKey.identifier().equals(finalDmgTypeKey)) return;
                }
                // Overflow-safe multiply (same clamp used by CombatPowerEvents native path).
                float scaled = event.getAmount() * finalMultiplier;
                if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
                event.setAmount(scaled);
                // A 0-multiplier effectively cancels the hit; callers commonly rely on that.
                if (scaled <= 0.0f) event.setCanceled(true);
            })
            .build();
    }

    private CompatPower.Config parseConditionedModifyDamageDealt(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // See parseConditionedModifyDamageTaken — same singular/plural and value/amount
        // tolerance for symmetry with Apoli.
        float multiplier = collapseDamageModifiers(parseModifierList(json, "modifier"));

        String damageTypeFilter = null;
        Identifier damageTypeKeyFilter = null;
        if (json.has("damage_condition") && json.get("damage_condition").isJsonObject()) {
            JsonObject dc = json.getAsJsonObject("damage_condition");
            String dcType = dc.has("type") ? dc.get("type").getAsString() : "";
            if (("origins:name".equals(dcType) || "apace:name".equals(dcType)) && dc.has("name")) {
                damageTypeFilter = dc.get("name").getAsString();
            } else if (("origins:type".equals(dcType) || "apace:type".equals(dcType)) && dc.has("damage_type")) {
                damageTypeKeyFilter = Identifier.parse(dc.get("damage_type").getAsString());
            }
        }

        EntityCondition condition = parseConditionField(json, "condition", idStr);

        final float finalMultiplier = multiplier;
        final String finalDmgFilter = damageTypeFilter;
        final Identifier finalDmgTypeKey = damageTypeKeyFilter;

        // Outgoing damage: the player is the ATTACKER, not the victim.
        // We hook LivingIncomingDamageEvent and check event.getSource().getEntity().
        return CompatPower.Config.builder()
            .onIncomingDamage(event -> {
                if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
                if (!condition.test(sp)) return;
                if (finalDmgFilter != null
                        && !event.getSource().getMsgId().equalsIgnoreCase(finalDmgFilter)) {
                    return;
                }
                if (finalDmgTypeKey != null) {
                    var typeKey = event.getSource().typeHolder().unwrapKey().orElse(null);
                    if (typeKey == null || !typeKey.identifier().equals(finalDmgTypeKey)) return;
                }
                float scaled = event.getAmount() * finalMultiplier;
                if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
                event.setAmount(scaled);
                if (scaled <= 0.0f) event.setCanceled(true);
            })
            .build();
    }

    private CompatPower.Config parseActiveSelf(Identifier id, JsonObject json) {
        String idStr = id.toString();
        boolean hasAction = json.has("entity_action") || json.has("action");
        if (!hasAction) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: active_self power missing 'entity_action' or 'action' field — power will not be registered", id);
            return null;
        }

        EntityAction action = json.has("entity_action")
            ? parseActionField(json, "entity_action", idStr)
            : parseActionField(json, "action", idStr);
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;

        // Key can be a string ("key.origins.primary_active") or an object
        // ({"key": "key.origins.primary_active", "continuous": true}).
        String key = "key.origins.primary_active";
        boolean continuous = false;
        if (json.has("key")) {
            var keyEl = json.get("key");
            if (keyEl.isJsonPrimitive()) {
                key = normalizeKeyToken(keyEl, key);
            } else if (keyEl.isJsonObject()) {
                var keyObj = keyEl.getAsJsonObject();
                key = keyObj.has("key") ? normalizeKeyToken(keyObj.get("key"), key) : key;
                continuous = keyObj.has("continuous") && keyObj.get("continuous").getAsBoolean();
            }
        }

        // Parse the optional condition gate
        EntityCondition condition = parseConditionField(json, "condition", idStr);

        // fail_action (NeoOrigins extension, not Apoli): runs when the player
        // attempts to activate the power but `condition` fails — pack-author
        // feedback ("you can't use this here") instead of a silent no-op.
        // Deliberately NOT fired on cooldown blocks: the HUD already shows those.
        EntityAction failAction = json.has("fail_action")
            ? parseActionField(json, "fail_action", idStr) : null;

        // Apoli hud_render on an active power = a cooldown progress bar. Expose it
        // as a synthetic resource bar so it renders in the resource HUD (named-hotkey
        // actives get no ability-cluster cell, and pack authors place the bar via
        // bar_index/sprite_location). Driven server-side from this power's own
        // cooldown; `inverted` flips fill direction (drains as it recharges).
        // null hooks when there's nothing to show (no cooldown or no hud_render).
        final java.util.function.Consumer<ServerPlayer> barGranted;
        final java.util.function.Consumer<ServerPlayer> barTick;
        final java.util.function.Consumer<ServerPlayer> barRevoked;
        if (cooldown > 0 && json.has("hud_render") && json.get("hud_render").isJsonObject()) {
            JsonObject hud = json.getAsJsonObject("hud_render");
            String spriteLocation = hud.has("sprite_location") ? hud.get("sprite_location").getAsString() : null;
            boolean barHidden = hud.has("should_render") && !hud.get("should_render").getAsBoolean();
            int barIndex  = hud.has("bar_index")  ? hud.get("bar_index").getAsInt()  : 0;
            int iconIndex = hud.has("icon_index") ? hud.get("icon_index").getAsInt() : 0;
            final boolean inverted = hud.has("inverted") && hud.get("inverted").getAsBoolean();
            final int barMax = cooldown;
            CompatAttachments.registerResourceMeta(idStr,
                new CompatAttachments.ResourceMeta(0, barMax, prettyLabel(id), 0xFF55AAFF,
                    barHidden, barIndex, iconIndex, spriteLocation));
            barGranted = player -> {
                int rem = player.getData(OriginAttachments.originData()).remainingCooldown(idStr, player.tickCount);
                player.getData(CompatAttachments.resourceState()).set(idStr, inverted ? rem : (barMax - rem));
                CompatAttachments.syncResourcesToClient(player);
            };
            barTick = player -> {
                var state = player.getData(CompatAttachments.resourceState());
                int rem = player.getData(OriginAttachments.originData()).remainingCooldown(idStr, player.tickCount);
                int val = inverted ? rem : (barMax - rem);
                String pk = player.getUUID() + ":cdbar:" + idStr;
                Integer prev = PREV_COOLDOWN_BAR.put(pk, val);
                if (prev == null || prev != val) state.set(idStr, val);
                if (state.isDirty() && player.tickCount % 10 == 0) {
                    state.clearDirty();
                    CompatAttachments.syncResourceValuesToClient(player);
                }
            };
            barRevoked = player -> {
                player.getData(CompatAttachments.resourceState()).remove(idStr);
                CompatAttachments.unregisterResourceMeta(idStr);
                PREV_COOLDOWN_BAR.remove(player.getUUID() + ":cdbar:" + idStr);
                CompatAttachments.syncResourcesToClient(player);
            };
        } else {
            barGranted = null;
            barTick = null;
            barRevoked = null;
        }

        // disable_hotkey (NeoOrigins extension): the power is activatable but
        // binds no key — no skill slot, no named hotkey, no vanilla-input poll.
        // It can only be triggered programmatically via the activate_power
        // action. Same activation contract (condition / cooldown / fail_action)
        // as the slot path; CompatPower.occupiesHotkeySlot() keeps it out of the
        // skill-slot list while isActivePower() stays true so activate_power can
        // reach it.
        boolean disableHotkey = json.has("disable_hotkey") && json.get("disable_hotkey").getAsBoolean();
        if (disableHotkey) {
            return withCooldownBar(CompatPower.Config.builder()
                .cooldownTicks(cooldown)
                .hotkeyless(true)
                .onActivated((ServerPlayer player) -> {
                    if (!condition.test(player)) {
                        if (failAction != null) failAction.execute(player);
                        return;
                    }
                    if (cooldown > 0) {
                        PlayerOriginData data = player.getData(OriginAttachments.originData());
                        if (data.isOnCooldown(player, idStr)) return;
                        data.setCooldown(idStr, player.tickCount, cooldown);
                    }
                    action.execute(player);
                })
                .build(), barGranted, barTick, barRevoked);
        }

        // Skill-slot keys: primary_active, secondary_active, and the two toolbar
        // keys (loadToolbarActivator, saveToolbarActivator) which have no server-side
        // input state and must be mapped to skill slots to be usable.
        // The two toolbar (creative-hotbar save/load) keys are a special case.
        // Apoli packs bind MANY condition-gated active_self powers to the same
        // toolbar key (the Seer progression rituals are six powers all on
        // saveToolbarActivator), expecting every one to be evaluated on each
        // press — exactly the multi-binding fan-out the named-hotkey dispatch
        // path provides. A skill slot can only host a single power per slot AND
        // the client never sends an activation for the vanilla toolbar keys, so
        // routing these to a skill slot silently drops them. Treat them as a
        // dedicated bucket that flows through PowerKeybindRegistry instead.
        boolean isToolbarKey = key.contains("loadToolbarActivator")
            || key.contains("saveToolbarActivator");
        boolean isSlotKey = !isToolbarKey && (key.contains("primary_active")
            || key.contains("secondary_active") || key.contains("pickItem"));
        // Continuous slot powers DON'T use onActivated — they need every-tick
        // execution which onActivated (single-fire per keypress) can't provide.
        if (isSlotKey && !continuous) {
            return withCooldownBar(CompatPower.Config.builder()
                .cooldownTicks(cooldown)
                .onActivated((ServerPlayer player) -> {
                    if (!condition.test(player)) return;
                    if (cooldown > 0) {
                        PlayerOriginData data = player.getData(OriginAttachments.originData());
                        if (data.isOnCooldown(player, idStr)) return;
                        data.setCooldown(idStr, player.tickCount, cooldown);
                    }
                    action.execute(player);
                })
                .build(), barGranted, barTick, barRevoked);
        }

        // Non-slot keys come in two flavors:
        //   1. Vanilla input keys (sneak/use/attack/jump/movement) — polled from
        //      onTick because the server knows the input state directly.
        //   2. Pack-declared translation keys (e.g. "deanos_origins.key.origins.2")
        //      — registered into PowerKeybindRegistry and fired by client press
        //      via ActivatePowerByKeyPayload. The CompatPower itself becomes a
        //      marker (no onActivated, no onTick) so it doesn't tick uselessly.
        final String finalKey = key;
        final String finalIdStr = idStr;
        final boolean isContinuous = continuous;

        boolean isVanillaInputKey = switch (key) {
            case "key.sneak", "key.use", "key.attack", "key.jump", "key.sprint",
                 "key.forward", "key.back", "key.left", "key.right" -> true;
            default -> false;
        };

        if (!isVanillaInputKey) {
            // Hotkey path: register into PowerKeybindRegistry so a client press
            // routes here. The power itself is a marker (no onActivated, no
            // onTick) so it doesn't double-fire from the slot system.
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.register(finalKey,
                new com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.Binding(
                    id, action, condition, cooldown, isContinuous, failAction));
            return withCooldownBar(CompatPower.Config.builder()
                .cooldownTicks(cooldown)
                .build(), barGranted, barTick, barRevoked);
        }

        // Vanilla-input-key active: polled below from onTick. Record the binding
        // so the origin info screen can show its key tag (e.g. "[Right Click]")
        // instead of rendering the power as a tag-less passive.
        com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.registerVanilla(id, finalKey);

        return withCooldownBar(CompatPower.Config.builder()
            .onTick(player -> {
                boolean pressed = switch (finalKey) {
                    case "key.sneak"   -> player.isShiftKeyDown();
                    // key.use = vanilla right-click. isUsingItem() only covers item-use
                    // ANIMATIONS (food/bow/shield) and is never true for a tap right-click
                    // on a plain/empty hand — which is how Apoli spell active_self powers
                    // are cast. Read the right-click tick stamped by CompatEventPowers.
                    case "key.use"     -> CompatPlayerState.isUseKeyDown(player);
                    // Real held key state (client-streamed) OR the swing flag, so it
                    // fires both while mining/hitting AND while merely holding attack
                    // with nothing under the crosshair (player.swinging only streams
                    // during an actual swing animation).
                    case "key.attack"  -> CompatPlayerState.isAttackKeyHeld(player) || player.swinging;
                    // Real airborne jump-press signal (client-sent AirJumpPayload),
                    // not the old "airborne and rising" heuristic that fired during
                    // the natural first jump and missed a deliberate re-press while
                    // falling — the double-jump gesture. See CompatPlayerState.
                    case "key.jump"    -> CompatPlayerState.isJumpKeyDown(player);
                    case "key.forward" -> player.zza > 0;
                    case "key.back"    -> player.zza < 0;
                    case "key.left"    -> player.xxa > 0;
                    case "key.right"   -> player.xxa < 0;
                    default -> {
                        if (player.tickCount == 1) {
                            NeoOrigins.LOGGER.warn("[CompatB] active_self key '{}' has no server-side input state — power {} will not fire", finalKey, finalIdStr);
                        }
                        yield false;
                    }
                };
                if (isContinuous) {
                    // Honour a declared cooldown even on a continuous (held-key)
                    // power: previously this fired EVERY tick while held, ignoring
                    // `cooldown` entirely. Gating here throttles to once per
                    // `cooldown` ticks. It also breaks the swing_hand self-feedback
                    // loop on key.attack: the action's own swing_hand re-arms
                    // `player.swinging` (the held signal), so without a gate it kept
                    // firing after release until the resource drained. Because the
                    // cooldown (e.g. 10) outlasts the ~6-tick swing animation, the
                    // re-armed swing lapses before the next cooldown window opens,
                    // so release actually stops it.
                    if (pressed && condition.test(player)) {
                        if (cooldown > 0) {
                            PlayerOriginData data = player.getData(OriginAttachments.originData());
                            if (data.isOnCooldown(player, idStr)) return;
                            data.setCooldown(idStr, player.tickCount, cooldown);
                        }
                        action.execute(player);
                    }
                } else {
                    // Edge detection: fire once on press
                    String edgeKey = idStr + ":keypress";
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    boolean wasPressedLastTick = data.getCustomFloat(edgeKey, 0) > 0;
                    data.setCustomFloat(edgeKey, pressed ? 1.0F : 0.0F);
                    if (pressed && !wasPressedLastTick && condition.test(player)) {
                        if (cooldown > 0) {
                            if (data.isOnCooldown(player, idStr)) return;
                            data.setCooldown(idStr, player.tickCount, cooldown);
                        }
                        action.execute(player);
                    }
                }
            })
            .build(), barGranted, barTick, barRevoked);
    }

    /** Human-readable label from a power id's last path segment (Title Case). */
    private static String prettyLabel(Identifier id) {
        String path = id.getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) path = path.substring(lastSlash + 1);
        String label = path.replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : label.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    /** Compose two consumers; null-safe (returns the non-null one, or null if both null). */
    private static <T> java.util.function.Consumer<T> chain(java.util.function.Consumer<T> a,
                                                            java.util.function.Consumer<T> b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.andThen(b);
    }

    /**
     * Attach synthetic cooldown-bar hooks (granted/tick/revoked) to an existing
     * Route B Config without disturbing its own lifecycle lambdas. Returns the
     * base unchanged when there's no bar to drive (barTick == null).
     */
    private static CompatPower.Config withCooldownBar(CompatPower.Config base,
                                                      java.util.function.Consumer<ServerPlayer> barGranted,
                                                      java.util.function.Consumer<ServerPlayer> barTick,
                                                      java.util.function.Consumer<ServerPlayer> barRevoked) {
        if (barTick == null) return base;
        return new CompatPower.Config(
            chain(base.onGranted(), barGranted),
            chain(base.onRevoked(), barRevoked),
            chain(base.onTick(), barTick),
            base.onActivated(),
            base.onRespawn(),
            base.onHit(),
            base.onKill(),
            base.onIncomingDamage(),
            base.onDealDamage(),
            base.cooldownTicks(),
            base.hotkeyless(),
            base.capabilities(),
            base.capabilityCondition());
    }

    private CompatPower.Config parseActionOverTime(Identifier id, JsonObject json) {
        String idStr = id.toString();
        int interval = Math.max(1, json.has("interval") ? json.get("interval").getAsInt() : 1);

        EntityAction action = parseActionField(json, "entity_action", idStr);
        // Apoli action_over_time also supports edge-triggered actions:
        //   rising_action  — fires once when the condition transitions false→true
        //   falling_action — fires once when the condition transitions true→false
        // Both matter for "release to commit" patterns (e.g. a charge power that
        // commits on release) and one-shot setup/teardown when entering/leaving
        // a state. Without these, packs that depend on edge transitions silently
        // no-op their release/setup logic.
        boolean hasEdgeActions = json.has("rising_action") || json.has("falling_action");
        EntityAction risingAction  = json.has("rising_action")
            ? parseActionField(json, "rising_action", idStr)  : EntityAction.noop();
        EntityAction fallingAction = json.has("falling_action")
            ? parseActionField(json, "falling_action", idStr) : EntityAction.noop();
        // Apoli/MoR/Mido pack convention is `condition` (player-side gate);
        // apace and a few origins-classes packs use `entity_condition`.
        // Accept both — without this, packs like MoR Pixie's flight resource
        // drain gate and Mido moisture ticks silently dropped their
        // condition, defaulting to alwaysTrue and firing every interval.
        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = json.has("condition")
            ? parseConditionField(json, "condition", idStr)
            : parseConditionField(json, "entity_condition", idStr);
        // If any condition in the tree used an unsupported type, refuse to compile
        // this power. Running an action_over_time unconditionally (because its gate
        // condition silently failed) causes disasters like entity-per-tick spawns.
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] action_over_time {} has unsupported condition(s) — refusing to compile to prevent unconditional execution", idStr);
            return null;
        }

        // Stagger by ID hash so not all action_over_time powers run on the same tick.
        int offset = (idStr.hashCode() & Integer.MAX_VALUE) % interval;

        // ── Gamemode auto-revert safety net ──────────────────────────────────
        // A condition-gated repeating action that forces `gamemode` (the seer
        // pocket-dimension pattern: every tick run `gamemode adventure @s` while
        // in the pocket dim) but defines NO falling_action of its own will leave
        // the player stuck in that gamemode if they leave the gated state by any
        // path other than the pack's own one-shot revert. We narrowly remember
        // the player's gamemode when the gate first becomes satisfied and restore
        // it when the gate transitions back to unsatisfied — ONLY for this case.
        //
        // Conditions for the net to engage (kept deliberately tight):
        //   • the repeating entity_action issues a `gamemode` command,
        //   • the power has a real gate condition (not always-true), and
        //   • the pack defined NO falling_action (if it did, we trust it).
        // It never touches one-shot actions, packs with their own cleanup, or a
        // player's manual /gamemode (we only restore the value we ourselves
        // recorded at the rising edge of THIS power's gate).
        boolean gateForcesGamemode =
            !hasFallingAction(json)
            && (json.has("condition") || json.has("entity_condition"))
            && entityActionForcesGamemode(json);
        // When engaged, we must observe both edges of the gate, so route through
        // the edge-tracking path even if the pack declared no rising/falling action.
        boolean trackEdges = hasEdgeActions || gateForcesGamemode;
        if (gateForcesGamemode) {
            NeoOrigins.LOGGER.debug("[CompatB] action_over_time {} forces gamemode without a falling_action — enabling gamemode auto-revert safety net", idStr);
        }

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (player.level().getServer() == null) return;
                long tick = player.level().getServer().getTickCount();
                // Edge transitions are only tracked when the power declares
                // rising_action/falling_action — powers without them keep the
                // cheaper interval-gated condition evaluation below. The gamemode
                // safety net also opts into edge tracking (trackEdges).
                if (trackEdges) {
                    boolean cur = condition.test(player);
                    String edgeKey = player.getUUID() + ":" + idStr;
                    // Default-prev = false matches Apoli: rising_action fires on
                    // grant if the condition is already true (a transition from
                    // "no prior state" = false).
                    Boolean prev = PREV_AOT_CONDITIONS.put(edgeKey, cur);
                    boolean prevVal = prev != null && prev;
                    if (cur && !prevVal) {
                        if (gateForcesGamemode) rememberGamemode(player, idStr);
                        risingAction.execute(player);
                    } else if (!cur && prevVal) {
                        fallingAction.execute(player);
                        if (gateForcesGamemode) restoreGamemode(player, idStr);
                    }
                    if ((tick + offset) % interval == 0 && cur) {
                        action.execute(player);
                    }
                    return;
                }
                if ((tick + offset) % interval == 0 && condition.test(player)) {
                    action.execute(player);
                }
            })
            .build();
    }

    /** True if the power declares its own {@code falling_action} (then we leave cleanup to the pack). */
    private static boolean hasFallingAction(JsonObject json) {
        return json.has("falling_action");
    }

    /**
     * Whether this action_over_time's repeating {@code entity_action} issues a
     * {@code gamemode} command. Walks the (possibly nested {@code and}/array)
     * action tree collecting {@code command} strings and checks whether any —
     * after legacy rewriting — runs {@code gamemode} (either directly or as the
     * run-verb of an {@code execute ... run gamemode ...}). Intentionally
     * conservative: only string commands, only the {@code gamemode} verb.
     */
    private static boolean entityActionForcesGamemode(JsonObject json) {
        if (!json.has("entity_action") || !json.get("entity_action").isJsonObject()) return false;
        java.util.List<String> commands = new java.util.ArrayList<>();
        collectActionCommands(json.getAsJsonObject("entity_action"), commands, 0);
        for (String cmd : commands) {
            if (commandRunsGamemode(LegacyCommandRewriter.rewrite(cmd))) return true;
        }
        return false;
    }

    /** Recursively gather {@code command} strings from an Apoli action JSON tree (depth-guarded). */
    private static void collectActionCommands(JsonObject action, java.util.List<String> out, int depth) {
        if (action == null || depth > 16) return;
        if (action.has("command") && action.get("command").isJsonPrimitive()) {
            out.add(action.get("command").getAsString());
        }
        // and/chain wrappers nest sub-actions under "actions"; some forks use "action".
        if (action.has("actions") && action.get("actions").isJsonArray()) {
            for (JsonElement el : action.getAsJsonArray("actions")) {
                if (el.isJsonObject()) collectActionCommands(el.getAsJsonObject(), out, depth + 1);
            }
        }
        if (action.has("action") && action.get("action").isJsonObject()) {
            collectActionCommands(action.getAsJsonObject("action"), out, depth + 1);
        }
    }

    /**
     * Whether a (rewritten) command line ultimately runs the {@code gamemode}
     * command — directly, or as the trailing {@code run gamemode ...} of an
     * {@code execute} chain (the seer pack's
     * {@code execute if entity @s[...] run gamemode adventure @s} form).
     */
    private static boolean commandRunsGamemode(String cmd) {
        if (cmd == null) return false;
        String c = cmd.trim();
        if (c.startsWith("/")) c = c.substring(1).trim();
        if (c.startsWith("gamemode ")) return true;
        int run = c.indexOf(" run ");
        if (c.startsWith("execute ") && run >= 0) {
            String tail = c.substring(run + 5).trim();
            return tail.startsWith("gamemode ");
        }
        return false;
    }

    /** Remember a player's current gamemode for {@code powerId} (rising edge), if not already stored. */
    private static void rememberGamemode(ServerPlayer player, String powerId) {
        String key = player.getUUID() + ":" + powerId;
        GAMEMODE_REVERT.putIfAbsent(key, player.gameMode.getGameModeForPlayer());
    }

    /**
     * Restore the gamemode remembered at the rising edge (falling edge) and clear
     * the record. No-op if nothing was remembered, or if the player has since
     * landed back on the remembered gamemode on their own.
     */
    private static void restoreGamemode(ServerPlayer player, String powerId) {
        String key = player.getUUID() + ":" + powerId;
        net.minecraft.world.level.GameType remembered = GAMEMODE_REVERT.remove(key);
        if (remembered == null) return;
        if (player.gameMode.getGameModeForPlayer() != remembered) {
            player.setGameMode(remembered);
        }
    }

    private CompatPower.Config parseActionOnCallback(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Respawn: upstream Apoli uses `respawn_entity_action`; we historically supported
        // our own `respawn_action`. Accept both; merge if present.
        EntityAction respawnAction = EntityAction.noop();
        if (json.has("respawn_entity_action")) {
            respawnAction = parseActionField(json, "respawn_entity_action", idStr);
        }
        if (json.has("respawn_action")) {
            respawnAction = mergeActions(respawnAction,
                parseActionField(json, "respawn_action", idStr));
        }

        // Removal: upstream Apoli uses `entity_action_lost` (and some forks use
        // `entity_action_removed`). We historically supported our own `removed_action`.
        // Accept all three; merge if multiple are present.
        EntityAction removedAction = EntityAction.noop();
        if (json.has("entity_action_lost")) {
            removedAction = parseActionField(json, "entity_action_lost", idStr);
        }
        if (json.has("entity_action_removed")) {
            removedAction = mergeActions(removedAction,
                parseActionField(json, "entity_action_removed", idStr));
        }
        if (json.has("removed_action")) {
            removedAction = mergeActions(removedAction,
                parseActionField(json, "removed_action", idStr));
        }

        // Upstream Origins has separate triggers for "gained" (every grant, including
        // login) and "chosen" (only when the player selects from the GUI). We merge
        // both into onGranted — the distinction is lost, but most addon packs use
        // entity_action_chosen for one-time setup (e.g. granting starter items via
        // /function) and the commands are typically idempotent.
        EntityAction addedAction = EntityAction.noop();
        if (json.has("entity_action_chosen")) {
            addedAction = parseActionField(json, "entity_action_chosen", idStr);
        }
        if (json.has("entity_action_gained")) {
            addedAction = mergeActions(addedAction,
                parseActionField(json, "entity_action_gained", idStr));
        }
        if (json.has("added_action")) {
            addedAction = mergeActions(addedAction,
                parseActionField(json, "added_action", idStr));
        }

        EntityAction finalAdded = addedAction;
        return CompatPower.Config.builder()
            .onGranted(finalAdded::execute)
            .onRevoked(removedAction::execute)
            .onRespawn(respawnAction::execute)
            .build();
    }

    /** Combine two entity actions into one that runs both sequentially. */
    private static EntityAction mergeActions(EntityAction first, EntityAction second) {
        if (first == EntityAction.noop()) return second;
        if (second == EntityAction.noop()) return first;
        return player -> { first.execute(player); second.execute(player); };
    }

    // ---- Array-or-object field helpers ------------------------------------
    // Apoli/Origins lets every action/condition slot hold EITHER a single
    // object OR an array of objects; an array is an implicit "do all of these"
    // (all-of). The old getAsJsonObject(field) casts threw a ClassCastException
    // on the array form, failing the whole power. These helpers accept both:
    // a single object parses via the existing path; an array is aggregated the
    // same way the native neoorigins:and verbs do — sequential execution for
    // actions, all-must-pass for conditions.

    /**
     * Parse an action field that may be absent, a single object, or an array.
     * Returns {@link EntityAction#noop()} when the field is absent or holds a
     * non-object/array element; an array is run sequentially (Apoli all-of).
     */
    private static EntityAction parseActionField(JsonObject parent, String field, String idStr) {
        // Delegates to the shared helper so Route-B (translated) and native
        // neoorigins:* power CODECs accept identical array-or-object action shapes.
        return ActionParser.parseField(parent, field, idStr);
    }

    /**
     * Parse a condition field that may be absent, a single object, or an array.
     * Returns {@link EntityCondition#alwaysTrue()} when absent; an array is
     * combined as logical AND (Apoli all-of: every element must pass).
     */
    private static EntityCondition parseConditionField(JsonObject parent, String field, String idStr) {
        // Delegates to the shared helper (see parseActionField).
        return ConditionParser.parseField(parent, field, idStr);
    }

    /**
     * Parse a bientity-action field that may be absent, a single object, or an
     * array. Returns {@link com.cyberday1.neoorigins.compat.action.BiEntityAction#noop()}
     * when absent; an array runs sequentially (Apoli all-of).
     */
    private static com.cyberday1.neoorigins.compat.action.BiEntityAction parseBiEntityActionField(
            JsonObject parent, String field, String idStr) {
        if (!parent.has(field)) return com.cyberday1.neoorigins.compat.action.BiEntityAction.noop();
        JsonElement el = parent.get(field);
        if (el.isJsonObject()) {
            return com.cyberday1.neoorigins.compat.action.BiEntityActionParser.parse(el.getAsJsonObject(), idStr);
        }
        if (el.isJsonArray()) {
            java.util.List<com.cyberday1.neoorigins.compat.action.BiEntityAction> list = new java.util.ArrayList<>();
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    list.add(com.cyberday1.neoorigins.compat.action.BiEntityActionParser.parse(item.getAsJsonObject(), idStr));
                }
            }
            return (actor, target) -> {
                for (var a : list) a.execute(actor, target);
            };
        }
        return com.cyberday1.neoorigins.compat.action.BiEntityAction.noop();
    }

    private CompatPower.Config parseResource(Identifier id, JsonObject json) {
        String key       = id.toString();
        String idStr     = key;
        int min          = json.has("min")         ? json.get("min").getAsInt()         : 0;
        int max          = json.has("max")         ? json.get("max").getAsInt()         : 100;
        int startValue   = json.has("start_value") ? json.get("start_value").getAsInt() : min;
        int interval     = Math.max(1, json.has("interval") ? json.get("interval").getAsInt() : 20);
        int offset       = (idStr.hashCode() & Integer.MAX_VALUE) % interval;
        // NeoOrigins extension, also honoured on the compat path: bind the bar's
        // value to an external pool (today only "irons_spellbooks:mana"). A backed
        // resource reads/writes the pool instead of the internal ResourceState.
        final String backing = json.has("backing") ? json.get("backing").getAsString() : "";

        EntityAction minAction  = parseActionField(json, "min_action",     idStr);
        EntityAction maxAction  = parseActionField(json, "max_action",     idStr);
        EntityAction tickAction = parseActionField(json, "entity_action",  idStr);

        // HUD display metadata — parse from hud_render block or fall back to defaults.
        String label = "Resource";
        int color = 0xFF55AAFF;
        boolean hidden = json.has("hidden") && json.get("hidden").getAsBoolean();
        // Apoli hud_render sprite indices into resource_bar.png; -1 == unset
        // (HUD then draws a color-tinted fill inside the frame instead).
        int barIndex = -1;
        int iconIndex = -1;
        // Pack-declared sprite sheet (community restyles ship their own); null == use default.
        String spriteLocation = null;
        // Apoli hud_render.condition: the bar renders only while this condition
        // holds (contextual bars). null == always render. Evaluated server-side.
        EntityCondition renderCondition = null;
        // Boolean toggles (min=0, max=1) are internal state, not player-facing bars.
        if (min == 0 && max == 1) hidden = true;
        if (json.has("hud_render") && json.get("hud_render").isJsonObject()) {
            com.google.gson.JsonObject hud = json.getAsJsonObject("hud_render");
            // Origins compat: should_render=false hides the bar
            if (hud.has("should_render") && !hud.get("should_render").getAsBoolean()) {
                hidden = true;
            }
            // Condition-gated hud_render: the bar is contextual — it renders only
            // while this condition holds. We evaluate it server-side and include the
            // bar in the sync only when it passes; the onTick below drives a full
            // re-sync on condition edges so it appears/disappears live. An
            // unsupported condition degrades to always-show rather than hiding the
            // bar forever (the old behaviour, which silently dropped 50+ bars from
            // packs like Deano's that gate nearly every bar on `resource > 0`).
            if (hud.has("condition")) {
                CompatPolicy.resetFailClosedCount();
                EntityCondition rc = parseConditionField(hud, "condition", idStr);
                if (CompatPolicy.failClosedCount() == 0) {
                    renderCondition = rc;
                } else {
                    NeoOrigins.LOGGER.warn("[CompatB] resource {} hud_render.condition uses an unsupported type — bar will render unconditionally", idStr);
                }
            }
            // Apoli sprite-sheet indices: bar_index picks the fill row,
            // icon_index picks the icon column in resource_bar.png. Apoli
            // defaults both to 0, so a hud_render block (even without explicit
            // indices) renders with the real Apoli texture — only resources
            // with NO hud_render keep -1 and fall back to a color-tinted fill.
            barIndex  = hud.has("bar_index")  ? hud.get("bar_index").getAsInt()  : 0;
            iconIndex = hud.has("icon_index") ? hud.get("icon_index").getAsInt() : 0;
            // sprite_location: pack points the bar at its own restyled sheet
            // (shipped by the source mod/datapack at the same coordinates).
            if (hud.has("sprite_location")) {
                spriteLocation = hud.get("sprite_location").getAsString();
            }
        }
        // Derive a human-readable label from the power ID path segment.
        String path = id.getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) path = path.substring(lastSlash + 1);
        label = path.replace('_', ' ');
        // Capitalize first letter of each word
        StringBuilder sb = new StringBuilder();
        for (String word : label.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        label = sb.toString();

        CompatAttachments.registerResourceMeta(key,
            new CompatAttachments.ResourceMeta(min, max, label, color, hidden, barIndex, iconIndex, spriteLocation));
        if (renderCondition != null) {
            CompatAttachments.registerResourceRenderCondition(key, renderCondition);
        } else {
            CompatAttachments.unregisterResourceRenderCondition(key);
        }
        final EntityCondition fRenderCondition = renderCondition;

        return CompatPower.Config.builder()
            .onGranted(player -> {
                CompatAttachments.registerResourceBacking(key, backing);
                // Backed bars have no internal store to seed; the pool is authoritative.
                if (!CompatAttachments.isManaBacked(key)) {
                    player.getData(CompatAttachments.resourceState()).set(key, startValue);
                }
                CompatAttachments.syncResourcesToClient(player);
            })
            .onRevoked(player -> {
                player.getData(CompatAttachments.resourceState()).remove(key);
                CompatAttachments.unregisterResourceMeta(key);
                CompatAttachments.unregisterResourceRenderCondition(key);
                CompatAttachments.unregisterResourceBacking(key);
                PREV_RENDER_CONDITIONS.remove(player.getUUID() + ":rcond:" + key);
                CompatAttachments.syncResourcesToClient(player);
            })
            .onTick(player -> {
                boolean manaBacked = CompatAttachments.isManaBacked(key);
                var state = player.getData(CompatAttachments.resourceState());
                if (player.level().getServer() != null && (player.level().getServer().getTickCount() + offset) % interval == 0) {
                    tickAction.execute(player);
                }
                // Edge-triggered min/max actions — only fire on the transition,
                // not every tick while sitting at the boundary.
                int cur = manaBacked
                    ? ResourceBackingRouter.read(player, key, min)
                    : state.get(key, startValue);
                String edgeKey = player.getUUID() + ":" + key;
                Integer prev = PREV_RESOURCE_VALUES.put(edgeKey, cur);
                int prevVal = prev != null ? prev : startValue;
                if (cur != prevVal) {
                    if (cur <= min && prevVal > min) minAction.execute(player);
                    if (cur >= max && prevVal < max) maxAction.execute(player);
                }
                // Sync to client every 10 ticks. A backed bar's pool changes out
                // from under us (regen/casting), so sync unconditionally on the
                // cadence; an internal bar syncs only when its dirty flag trips.
                if (manaBacked) {
                    if (player.tickCount % 10 == 0) {
                        CompatAttachments.syncResourceValuesToClient(player);
                    }
                } else if (state.isDirty() && player.tickCount % 10 == 0) {
                    state.clearDirty();
                    CompatAttachments.syncResourceValuesToClient(player);
                }
                // Render-condition edge: when the hud_render.condition flips, push a
                // full sync so the bar entry is created/removed client-side (the
                // value-only payload can neither create nor drop entries).
                if (fRenderCondition != null) {
                    boolean show = fRenderCondition.test(player);
                    String showKey = player.getUUID() + ":rcond:" + key;
                    Boolean prevShow = PREV_RENDER_CONDITIONS.put(showKey, show);
                    if (prevShow == null || prevShow.booleanValue() != show) {
                        CompatAttachments.syncResourcesToClient(player);
                    }
                }
            })
            .build();
    }

    /**
     * Apoli {@code origins:cooldown} — a countdown resource: 0 == ready,
     * &gt;0 == ticks remaining. {@code trigger_cooldown} (BuiltinActions) arms
     * it by setting the duration registered here; this power's onTick
     * decrements it back to 0. Reading it as a resource (conditions,
     * hud_render, change_resource) sees the remaining ticks — the Apoli
     * semantics the Chaotic Chemist immunity-shot pattern relies on
     * ({@code trigger_cooldown "*:*_timer"} then gate on
     * {@code resource *:*_timer > 0}). Previously in SKIP_TYPES with no Route
     * B handler, so these powers were silently dropped.
     */
    private CompatPower.Config parseCooldown(Identifier id, JsonObject json) {
        String key   = id.toString();
        String idStr = key;
        int cooldown = Math.max(1, json.has("cooldown") ? json.get("cooldown").getAsInt() : 1);

        // HUD display metadata — same parse as parseResource: should_render=false
        // hides the bar; a hud_render block defaults the Apoli sprite indices to
        // 0; sprite_location and condition pass through.
        boolean hidden = json.has("hidden") && json.get("hidden").getAsBoolean();
        int barIndex = -1;
        int iconIndex = -1;
        String spriteLocation = null;
        EntityCondition renderCondition = null;
        if (json.has("hud_render") && json.get("hud_render").isJsonObject()) {
            JsonObject hud = json.getAsJsonObject("hud_render");
            if (hud.has("sprite_location")) {
                spriteLocation = hud.get("sprite_location").getAsString();
            }
            if (hud.has("should_render") && !hud.get("should_render").getAsBoolean()) {
                hidden = true;
            }
            if (hud.has("condition")) {
                CompatPolicy.resetFailClosedCount();
                EntityCondition rc = parseConditionField(hud, "condition", idStr);
                if (CompatPolicy.failClosedCount() == 0) {
                    renderCondition = rc;
                } else {
                    NeoOrigins.LOGGER.warn("[CompatB] cooldown {} hud_render.condition uses an unsupported type — bar will render unconditionally", idStr);
                }
            }
            barIndex  = hud.has("bar_index")  ? hud.get("bar_index").getAsInt()  : 0;
            iconIndex = hud.has("icon_index") ? hud.get("icon_index").getAsInt() : 0;
        }
        // Derive a human-readable label from the power ID path segment.
        String path = id.getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) path = path.substring(lastSlash + 1);
        StringBuilder sb = new StringBuilder();
        for (String word : path.replace('_', ' ').split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        String label = sb.toString();

        CompatAttachments.registerResourceMeta(key,
            new CompatAttachments.ResourceMeta(0, cooldown, label, 0xFF55AAFF, hidden, barIndex, iconIndex, spriteLocation));
        CompatAttachments.registerCooldownDuration(key, cooldown);
        if (renderCondition != null) {
            CompatAttachments.registerResourceRenderCondition(key, renderCondition);
        } else {
            CompatAttachments.unregisterResourceRenderCondition(key);
        }
        final EntityCondition fRenderCondition = renderCondition;

        return CompatPower.Config.builder()
            .onGranted(player -> {
                var state = player.getData(CompatAttachments.resourceState());
                // Start ready (0) — but keep a persisted mid-countdown value so a
                // relog doesn't reset a running cooldown.
                if (!state.has(key)) state.set(key, 0);
                CompatAttachments.syncResourcesToClient(player);
            })
            .onRevoked(player -> {
                player.getData(CompatAttachments.resourceState()).remove(key);
                CompatAttachments.unregisterResourceMeta(key);
                CompatAttachments.unregisterCooldownDuration(key);
                CompatAttachments.unregisterResourceRenderCondition(key);
                PREV_RENDER_CONDITIONS.remove(player.getUUID() + ":rcond:" + key);
                CompatAttachments.syncResourcesToClient(player);
            })
            .onTick(player -> {
                var state = player.getData(CompatAttachments.resourceState());
                int cur = state.get(key, 0);
                if (cur > 0) state.set(key, cur - 1);
                // Sync to client every 10 ticks when dirty — value-only payload.
                if (state.isDirty() && player.tickCount % 10 == 0) {
                    state.clearDirty();
                    CompatAttachments.syncResourceValuesToClient(player);
                }
                if (fRenderCondition != null) {
                    boolean show = fRenderCondition.test(player);
                    String showKey = player.getUUID() + ":rcond:" + key;
                    Boolean prevShow = PREV_RENDER_CONDITIONS.put(showKey, show);
                    if (prevShow == null || prevShow.booleanValue() != show) {
                        CompatAttachments.syncResourcesToClient(player);
                    }
                }
            })
            .build();
    }

    private CompatPower.Config parseToggle(Identifier id, JsonObject json) {
        String stateKey = id.toString();
        boolean defaultActive = !json.has("active") || json.get("active").getAsBoolean();

        EntityAction activeAction   = parseActionField(json, "active_action",   stateKey);
        EntityAction inactiveAction = parseActionField(json, "inactive_action", stateKey);

        // The toggle behavior itself: flip the stored state, run the matching action.
        EntityAction toggleAction = player -> {
            boolean next = player.getData(CompatAttachments.toggleState()).toggle(stateKey, defaultActive);
            if (next) activeAction.execute(player);
            else inactiveAction.execute(player);
        };

        var builder = CompatPower.Config.builder()
            .onGranted(player -> player.getData(CompatAttachments.toggleState()).set(stateKey, defaultActive));

        // Activation gate + fail feedback (fail_action is a NeoOrigins extension):
        // condition blocks the flip; fail_action runs on a blocked attempt.
        EntityCondition condition = parseConditionField(json, "condition", stateKey);
        EntityAction failAction = json.has("fail_action")
            ? parseActionField(json, "fail_action", stateKey) : null;

        // A toggle can declare a pack-defined hotkey ("key": "...") just like active_self.
        // A named hotkey (neither a skill slot nor a vanilla input key) routes the press
        // through PowerKeybindRegistry; otherwise it defaults to the primary-active skill
        // slot via onActivated.
        String key = "key.origins.primary_active";
        boolean continuous = false;
        if (json.has("key")) {
            var keyEl = json.get("key");
            if (keyEl.isJsonPrimitive()) {
                key = normalizeKeyToken(keyEl, key);
            } else if (keyEl.isJsonObject()) {
                var keyObj = keyEl.getAsJsonObject();
                key = keyObj.has("key") ? normalizeKeyToken(keyObj.get("key"), key) : key;
                continuous = keyObj.has("continuous") && keyObj.get("continuous").getAsBoolean();
            }
        }
        // The two toolbar (creative-hotbar save/load) keys are a special case.
        // Apoli packs bind MANY condition-gated active_self powers to the same
        // toolbar key (the Seer progression rituals are six powers all on
        // saveToolbarActivator), expecting every one to be evaluated on each
        // press — exactly the multi-binding fan-out the named-hotkey dispatch
        // path provides. A skill slot can only host a single power per slot AND
        // the client never sends an activation for the vanilla toolbar keys, so
        // routing these to a skill slot silently drops them. Treat them as a
        // dedicated bucket that flows through PowerKeybindRegistry instead.
        boolean isToolbarKey = key.contains("loadToolbarActivator")
            || key.contains("saveToolbarActivator");
        boolean isSlotKey = !isToolbarKey && (key.contains("primary_active")
            || key.contains("secondary_active") || key.contains("pickItem"));
        boolean isVanillaInputKey = switch (key) {
            case "key.sneak", "key.use", "key.attack", "key.jump", "key.sprint",
                 "key.forward", "key.back", "key.left", "key.right" -> true;
            default -> false;
        };
        if (!isSlotKey && !isVanillaInputKey) {
            int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.register(key,
                new com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.Binding(
                    id, toggleAction, condition, cooldown, continuous, failAction));
            return builder.cooldownTicks(cooldown).build();
        }
        return builder.onActivated(player -> {
            if (!condition.test(player)) {
                if (failAction != null) failAction.execute(player);
                return;
            }
            toggleAction.execute(player);
        }).build();
    }

    /** One resolved attribute-modifier entry of a conditioned_attribute power. */
    private record CondAttrEntry(
        net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
        Identifier modifierId, double value, AttributeModifier.Operation operation) {}

    /**
     * Collect the attribute-modifier specs of an Apoli {@code conditioned_attribute}
     * power in canonical form: one JsonObject per modifier, each carrying its own
     * {@code attribute} (inherited from the top level when the entry omits it).
     * Accepts every shape real packs use:
     * <ul>
     *   <li>plural {@code "modifiers"} as an ARRAY of entries (Origins++ dark_boost)</li>
     *   <li>plural {@code "modifiers"} as a single OBJECT (Origins++ pluck)</li>
     *   <li>singular {@code "modifier"} object (attribute inside it or at top level)</li>
     *   <li>flat top-level {@code attribute}/{@code operation}/{@code value}</li>
     * </ul>
     * Package-visible for unit tests.
     */
    static List<JsonObject> collectAttributeModifierSpecs(JsonObject json) {
        List<JsonObject> specs = new ArrayList<>();
        String topAttr = json.has("attribute") && json.get("attribute").isJsonPrimitive()
            ? json.get("attribute").getAsString() : null;
        JsonElement plural = json.get("modifiers");
        if (plural != null && plural.isJsonArray()) {
            for (JsonElement el : plural.getAsJsonArray()) {
                if (el.isJsonObject()) specs.add(inheritAttribute(el.getAsJsonObject(), topAttr));
            }
        } else if (plural != null && plural.isJsonObject()) {
            specs.add(inheritAttribute(plural.getAsJsonObject(), topAttr));
        } else if (json.has("modifier") && json.get("modifier").isJsonObject()) {
            specs.add(inheritAttribute(json.getAsJsonObject("modifier"), topAttr));
        } else if (topAttr != null) {
            // Flat singular form: operation/value live directly on the power object.
            JsonObject spec = new JsonObject();
            for (String key : List.of("attribute", "operation", "value", "amount")) {
                if (json.has(key)) spec.add(key, json.get(key));
            }
            specs.add(spec);
        }
        return specs;
    }

    private static JsonObject inheritAttribute(JsonObject spec, String topAttr) {
        JsonObject copy = spec.deepCopy();
        if (!copy.has("attribute") && topAttr != null) copy.addProperty("attribute", topAttr);
        return copy;
    }

    /**
     * Resolve an attribute id against the registry, tolerating the 1.21.1 ⇄
     * 1.21.2+ {@code generic.} prefix divergence in either direction so the
     * same pack works on both versions.
     */
    private static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>
            resolveAttributeHolder(Identifier rawAttrIdent) {
        var holder = BuiltInRegistries.ATTRIBUTE.get(rawAttrIdent).orElse(null);
        if (holder == null && rawAttrIdent.getPath().startsWith("generic.")) {
            holder = BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath(
                rawAttrIdent.getNamespace(), rawAttrIdent.getPath().substring("generic.".length()))).orElse(null);
        }
        if (holder == null && !rawAttrIdent.getPath().startsWith("generic.")) {
            holder = BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath(
                rawAttrIdent.getNamespace(), "generic." + rawAttrIdent.getPath())).orElse(null);
        }
        return holder;
    }

    private CompatPower.Config parseConditionedAttribute(Identifier id, JsonObject json) {
        String idStr = id.toString();
        List<JsonObject> specs = collectAttributeModifierSpecs(json);
        if (specs.isEmpty()) {
            CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                "missing 'attribute'/'modifier'/'modifiers' in JSON");
            return null;
        }

        // Split fall_damage entries from real attribute entries. Fall-damage isn't
        // a vanilla attribute, so an entry targeting it (attribute leaf
        // "fall_damage", namespace varies across packs) would fail the registry
        // lookup and get SILENTLY DROPPED — route those to the native
        // MOD_FALL_DAMAGE seam instead, under the same shared condition.
        List<JsonObject> fallSpecs = new ArrayList<>();
        List<CondAttrEntry> entries = new ArrayList<>();
        String safeKey = id.getPath().replace('/', '_');
        for (int i = 0; i < specs.size(); i++) {
            JsonObject spec = specs.get(i);
            String attrStr = spec.has("attribute") ? spec.get("attribute").getAsString() : null;
            if (attrStr == null) {
                NeoOrigins.LOGGER.warn("[CompatB] {}: modifiers[{}] has no 'attribute' — entry dropped", idStr, i);
                continue;
            }
            String attrLeaf = attrStr.contains(":") ? attrStr.substring(attrStr.indexOf(':') + 1) : attrStr;
            if (attrLeaf.equals("fall_damage") || attrLeaf.equals("generic.fall_damage")) {
                fallSpecs.add(spec);
                continue;
            }
            Identifier rawAttrIdent = Identifier.parse(attrStr);
            var attrHolder = resolveAttributeHolder(rawAttrIdent);
            if (attrHolder == null) {
                // Surface the bad attribute id in the compat log too — pack
                // authors usually only check that file when debugging; without
                // the id they only see an unactionable generic skip.
                NeoOrigins.LOGGER.warn("[CompatB] {}: unknown attribute '{}' — entry dropped", idStr, rawAttrIdent);
                CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                    "unknown attribute '" + rawAttrIdent + "' — pack-side fix: confirm attribute exists in this MC version");
                continue;
            }
            double value = spec.has("value")  ? spec.get("value").getAsDouble()
                         : spec.has("amount") ? spec.get("amount").getAsDouble() : 0.0;
            String op = spec.has("operation") ? spec.get("operation").getAsString() : "add_value";
            // Apoli clamp/set ops (min/max/set) have no vanilla AttributeModifier
            // equivalent — applying them as add_value corrupts the attribute (a
            // cap becomes a flat bonus). Drop the entry rather than mis-apply it.
            if (!OriginsOperationMapper.isRepresentable(op)) {
                NeoOrigins.LOGGER.warn("[CompatB] {}: attribute operation '{}' (clamp/set) has no vanilla "
                    + "equivalent — entry dropped", idStr, op);
                CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                    "operation '" + op + "' (clamp/set) cannot be represented as a vanilla attribute modifier");
                continue;
            }
            AttributeModifier.Operation operation = switch (OriginsOperationMapper.mapOperation(op)) {
                case "add_multiplied_base"  -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default                     -> AttributeModifier.Operation.ADD_VALUE;
            };
            // Stable per-entry modifier ID; the first entry keeps the historical
            // un-suffixed id so worlds saved before the plural support don't leak
            // a stale permanent modifier on upgrade.
            Identifier modifierId = Identifier.fromNamespaceAndPath("neoorigins",
                i == 0 ? "condattr_" + safeKey : "condattr_" + safeKey + "_" + i);
            entries.add(new CondAttrEntry(attrHolder, modifierId, value, operation));
        }

        if (entries.isEmpty() && fallSpecs.isEmpty()) {
            return null; // per-entry skip reasons already logged above
        }

        // Pure fall-damage power (the historical singular case and its array
        // form): delegate wholly to the fall-damage parser under the same condition.
        if (entries.isEmpty()) {
            JsonObject fwd = new JsonObject();
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            fallSpecs.forEach(arr::add);
            fwd.add("modifiers", arr);
            if (json.has("condition")) fwd.add("condition", json.get("condition"));
            CompatTranslationLog.pass(id,
                "origins:conditioned_attribute (fall_damage) -> modify_fall_damage");
            return parseModifyFallDamage(id, fwd);
        }

        EntityCondition condition = parseConditionField(json, "condition", idStr);
        // Apoli semantics: update_health (default TRUE) preserves the health
        // FRACTION when a max-health modifier flips on/off.
        boolean updateHealth = !json.has("update_health") || json.get("update_health").getAsBoolean();
        List<CondAttrEntry> attrEntries = List.copyOf(entries);

        // Rare mixed case: fall_damage entries alongside real attributes —
        // register the fall-damage handler from the same power id.
        com.cyberday1.neoorigins.compat.modifier.FloatModifier parsedFallModifier = null;
        if (!fallSpecs.isEmpty()) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            fallSpecs.forEach(arr::add);
            parsedFallModifier = com.cyberday1.neoorigins.compat.modifier.ModifierParser.parseList(arr, idStr);
        }
        final com.cyberday1.neoorigins.compat.modifier.FloatModifier fallModifier = parsedFallModifier;

        var builder = CompatPower.Config.builder()
            .onTick(player -> {
                boolean shouldHave = condition.test(player);
                float oldMax = player.getMaxHealth();
                boolean changed = false;
                for (CondAttrEntry e : attrEntries) {
                    AttributeInstance inst = player.getAttribute(e.attribute());
                    if (inst == null) continue;
                    boolean has = inst.getModifier(e.modifierId()) != null;
                    if (shouldHave && !has) {
                        inst.addPermanentModifier(new AttributeModifier(e.modifierId(), e.value(), e.operation()));
                        changed = true;
                    } else if (!shouldHave && has) {
                        inst.removeModifier(e.modifierId());
                        changed = true;
                    }
                }
                if (changed && updateHealth) {
                    float newMax = player.getMaxHealth();
                    if (newMax != oldMax && oldMax > 0.0f) {
                        player.setHealth(player.getHealth() * newMax / oldMax);
                    }
                }
            })
            .onRevoked(player -> {
                float oldMax = player.getMaxHealth();
                boolean removed = false;
                for (CondAttrEntry e : attrEntries) {
                    AttributeInstance inst = player.getAttribute(e.attribute());
                    if (inst != null && inst.getModifier(e.modifierId()) != null) {
                        inst.removeModifier(e.modifierId());
                        removed = true;
                    }
                }
                if (removed && updateHealth) {
                    float newMax = player.getMaxHealth();
                    if (newMax != oldMax && oldMax > 0.0f) {
                        player.setHealth(player.getHealth() * newMax / oldMax);
                    }
                }
                if (fallModifier != null) unregisterFallDamageHandler(player, idStr);
            });
        if (fallModifier != null) {
            builder.onGranted(player -> registerFallDamageHandler(player, idStr, fallModifier, condition));
        }
        return builder.build();
    }

    /**
     * {@code origins:swimming} — the holder can enter the swimming state in any
     * medium (Apoli forces the swim pose + touchingWater from sprint state alone,
     * which routes travel() through water physics — real swim up/down in lava).
     * Implemented as the condition-gated {@code "forced_swimming"} capability
     * consumed by EntityForcedSwimmingMixin on both logical sides, so the same
     * mechanism covers lava-gated (fluid_height/in_block) and resource-gated
     * (Corrupted Wither skirmish) packs without a per-medium special case.
     */
    private CompatPower.Config parseSwimming(Identifier id, JsonObject json) {
        String idStr = id.toString();
        // Fail closed on unsupported conditions: an unconditioned forced-swim
        // would apply water physics everywhere (walking, falling), which is far
        // worse than the power missing.
        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] swimming {} has unsupported condition(s) — refusing to compile", idStr);
            CompatTranslationLog.skip(id, "origins:swimming", "unsupported condition — refusing to force-swim unconditionally");
            return null;
        }
        return CompatPower.Config.builder()
            .capability("forced_swimming", condition::test)
            .build();
    }

    // ── action_on_being_used registry ───────────────────────────────────
    // Keyed by HOLDER UUID → power id → entry. Consulted by
    // CompatEventPowers.onEntityInteract when another player right-clicks the
    // holder: bientity actor = the interacting player, target = the holder.

    /** One registered action_on_being_used power on a holder. */
    public record BeingUsedEntry(
        com.cyberday1.neoorigins.compat.action.BiEntityAction action,
        java.util.function.BiPredicate<ServerPlayer, ServerPlayer> condition) {}

    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID,
        java.util.Map<String, BeingUsedEntry>> BEING_USED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Entries registered on a holder (empty map when none). */
    public static java.util.Map<String, BeingUsedEntry> getBeingUsedEntries(java.util.UUID holderId) {
        return BEING_USED.getOrDefault(holderId, Map.of());
    }

    /**
     * Narrow bientity_condition support for the (actor=user, target=holder)
     * player pair: {@code target_condition}/{@code actor_condition} wrap the
     * full compat entity-condition engine; {@code and}/{@code or} combine;
     * {@code inverted} flips at any level. Unknown types warn once and pass
     * (fail-open) so the wrapped action still runs — the Apoli surface here is
     * small and the only in-corpus user (giant/mount) is covered exactly.
     */
    private static java.util.function.BiPredicate<ServerPlayer, ServerPlayer>
            parseBiEntityConditionNarrow(JsonObject json, String idStr) {
        if (json == null) return (a, t) -> true;
        String type = json.has("type") ? json.get("type").getAsString() : "";
        String leaf = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
        java.util.function.BiPredicate<ServerPlayer, ServerPlayer> base = switch (leaf) {
            case "target_condition" -> {
                EntityCondition c = parseConditionField(json, "condition", idStr);
                yield (a, t) -> c.test(t);
            }
            case "actor_condition" -> {
                EntityCondition c = parseConditionField(json, "condition", idStr);
                yield (a, t) -> c.test(a);
            }
            case "and", "or" -> {
                java.util.List<java.util.function.BiPredicate<ServerPlayer, ServerPlayer>> parts = new ArrayList<>();
                if (json.has("conditions") && json.get("conditions").isJsonArray()) {
                    for (JsonElement el : json.getAsJsonArray("conditions")) {
                        if (el.isJsonObject()) parts.add(parseBiEntityConditionNarrow(el.getAsJsonObject(), idStr));
                    }
                }
                boolean isAnd = leaf.equals("and");
                yield (a, t) -> {
                    for (var p : parts) {
                        if (p.test(a, t) != isAnd) return !isAnd;
                    }
                    return isAnd;
                };
            }
            case "constant" -> {
                boolean v = json.has("value") && json.get("value").getAsBoolean();
                yield (a, t) -> v;
            }
            default -> {
                NeoOrigins.LOGGER.warn("[CompatB] {}: unsupported bientity_condition type '{}' — treating as always-true",
                    idStr, type);
                yield (a, t) -> true;
            }
        };
        return inverted ? base.negate() : base;
    }

    /**
     * {@code origins:action_on_being_used} — another player right-clicks the
     * holder and a bientity action runs (actor = the user, target = the holder).
     * Registered per holder in {@link #BEING_USED}; dispatched from
     * CompatEventPowers.onEntityInteract (MAIN_HAND only, event consumed on
     * success — Apoli returns ActionResult.SUCCESS). The Origins++ giant
     * "mount" pattern: bientity_action origins:mount + target passenger check.
     */
    private CompatPower.Config parseActionOnBeingUsed(Identifier id, JsonObject json) {
        String idStr = id.toString();
        if (!json.has("bientity_action")) {
            CompatTranslationLog.skip(id, "origins:action_on_being_used", "missing 'bientity_action'");
            return null;
        }
        com.cyberday1.neoorigins.compat.action.BiEntityAction action =
            parseBiEntityActionField(json, "bientity_action", idStr);
        java.util.function.BiPredicate<ServerPlayer, ServerPlayer> condition =
            json.has("bientity_condition") && json.get("bientity_condition").isJsonObject()
                ? parseBiEntityConditionNarrow(json.getAsJsonObject("bientity_condition"), idStr)
                : (a, t) -> true;
        if (json.has("item_condition")) {
            // Apoli also gates on the used item; not wired into the interact hook
            // yet, so surface the gap instead of silently ignoring it.
            NeoOrigins.LOGGER.warn("[CompatB] {}: action_on_being_used 'item_condition' is not supported — ignored", idStr);
        }
        BeingUsedEntry entry = new BeingUsedEntry(action, condition);
        return CompatPower.Config.builder()
            .onGranted(player -> BEING_USED.computeIfAbsent(player.getUUID(),
                k -> new java.util.concurrent.ConcurrentHashMap<>()).put(idStr, entry))
            .onRevoked(player -> {
                var perHolder = BEING_USED.get(player.getUUID());
                if (perHolder != null) {
                    perHolder.remove(idStr);
                    if (perHolder.isEmpty()) BEING_USED.remove(player.getUUID());
                }
            })
            .build();
    }

    /**
     * {@code origins:prevent_block_selection} — NARROW mapping: only the
     * "crosshair passes through cobwebs" pattern (block_condition targeting the
     * {@code origins:cobwebs} tag or the cobweb block itself, e.g. Broodmother
     * punch_through). Emits the condition-gated
     * {@code "cobweb_selection_passthrough"} capability consumed by
     * BlockStateBaseCobwebShapeMixin. Anything broader (arbitrary block
     * conditions would need per-block power evaluation inside the hot getShape
     * path) keeps skipping.
     */
    private CompatPower.Config parsePreventBlockSelection(Identifier id, JsonObject json) {
        String idStr = id.toString();
        JsonObject bc = json.has("block_condition") && json.get("block_condition").isJsonObject()
            ? json.getAsJsonObject("block_condition") : null;
        boolean cobwebs = false;
        if (bc != null) {
            String t = bc.has("type") ? bc.get("type").getAsString() : "";
            String leaf = t.contains(":") ? t.substring(t.indexOf(':') + 1) : t;
            if (leaf.equals("in_tag") && bc.has("tag")
                    && "origins:cobwebs".equals(bc.get("tag").getAsString())) {
                cobwebs = true;
            } else if (leaf.equals("block") && bc.has("block")
                    && "minecraft:cobweb".equals(LegacyBlockIds.remap(bc.get("block").getAsString()))) {
                cobwebs = true;
            }
        }
        if (!cobwebs) {
            CompatTranslationLog.skip(id, "origins:prevent_block_selection",
                "only the cobweb pattern maps (block_condition must target origins:cobwebs / minecraft:cobweb)");
            return null;
        }
        // Fail closed on unsupported conditions — an always-on passthrough would
        // make the holder's own webs permanently untargetable.
        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] prevent_block_selection {} has unsupported condition(s) — refusing to compile", idStr);
            CompatTranslationLog.skip(id, "origins:prevent_block_selection", "unsupported condition");
            return null;
        }
        return CompatPower.Config.builder()
            .capability("cobweb_selection_passthrough", condition::test)
            .build();
    }

    /** origins:shaking — makes the player model shake (like zombie-to-drowned conversion).
     *  Implemented via freeze ticks which trigger the same visual. */
    private CompatPower.Config parseShaking(Identifier id, JsonObject json) {
        EntityCondition condition = parseConditionField(json, "condition", id.toString());
        return CompatPower.Config.builder()
            .onTick(player -> {
                // Set freeze ticks just above the threshold to trigger shaking
                // without actually freezing the player. 1 tick above threshold
                // shows the animation without dealing damage.
                if (condition.test(player)) {
                    if (player.getTicksFrozen() < 2) player.setTicksFrozen(2);
                } else {
                    if (player.getTicksFrozen() > 0) player.setTicksFrozen(0);
                }
            })
            .onRevoked(player -> player.setTicksFrozen(0))
            .build();
    }

    // ── Effect amplifier modifier registry ─────────────────────────────
    // Keyed by player UUID → effect Identifier → amplifier delta.
    // Checked by CombatPowerEvents.onMobEffectAdded to boost amplifiers.
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID,
        java.util.Map<Identifier, Integer>> EFFECT_AMP_MODIFIERS = new java.util.concurrent.ConcurrentHashMap<>();

    public static int getAmplifierBoost(java.util.UUID playerId, Identifier effectId) {
        var map = EFFECT_AMP_MODIFIERS.get(playerId);
        return map == null ? 0 : map.getOrDefault(effectId, 0);
    }

    static void addAmplifierModifier(java.util.UUID playerId, Identifier effectId, int delta) {
        EFFECT_AMP_MODIFIERS.computeIfAbsent(playerId, k -> new java.util.concurrent.ConcurrentHashMap<>())
            .merge(effectId, delta, Integer::sum);
    }

    static void removeAmplifierModifier(java.util.UUID playerId, Identifier effectId, int delta) {
        var map = EFFECT_AMP_MODIFIERS.get(playerId);
        if (map == null) return;
        map.merge(effectId, -delta, Integer::sum);
        if (map.getOrDefault(effectId, 0) == 0) map.remove(effectId);
        if (map.isEmpty()) EFFECT_AMP_MODIFIERS.remove(playerId);
    }

    public static void clearAmplifierModifiers(java.util.UUID playerId) {
        EFFECT_AMP_MODIFIERS.remove(playerId);
    }

    /** origins:modify_status_effect_amplifier — boosts the amplifier of a specific
     *  effect whenever it's applied to the player. */
    private CompatPower.Config parseModifyEffectAmplifier(Identifier id, JsonObject json) {
        String effectStr = json.has("status_effect") ? json.get("status_effect").getAsString() : null;
        if (effectStr == null) return null;
        Identifier effectId = Identifier.parse(effectStr);

        JsonObject modObj = json.has("modifier") && json.get("modifier").isJsonObject()
            ? json.getAsJsonObject("modifier") : json;
        int value = modObj.has("value") ? modObj.get("value").getAsInt() : 1;

        return CompatPower.Config.builder()
            .onGranted(player -> addAmplifierModifier(player.getUUID(), effectId, value))
            .onRevoked(player -> removeAmplifierModifier(player.getUUID(), effectId, value))
            .build();
    }

    /** origins:modify_falling — slow fall with optional no fall damage. */
    private CompatPower.Config parseModifyFalling(Identifier id, JsonObject json) {
        float velocity = json.has("velocity") ? json.get("velocity").getAsFloat() : 0.1f;
        boolean takeFallDamage = !json.has("take_fall_damage") || json.get("take_fall_damage").getAsBoolean();

        String safeKey = id.getPath().replace('/', '_');
        Identifier gravModId = Identifier.fromNamespaceAndPath("neoorigins", "modfalling_grav_" + safeKey);
        Identifier fallModId = Identifier.fromNamespaceAndPath("neoorigins", "modfalling_fall_" + safeKey);

        // velocity 0.1 means slow fall — reduce gravity. Lower velocity = less gravity.
        // Vanilla gravity is 0.08; slow_falling effect sets it to 0.01.
        // We scale: velocity=0.01 → -0.9875 mult, velocity=0.1 → -0.875 mult
        double gravMult = -(1.0 - (velocity / 0.08));

        return CompatPower.Config.builder()
            .onGranted(player -> {
                var gravAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
                if (gravAttr != null && gravAttr.getModifier(gravModId) == null) {
                    gravAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        gravModId, gravMult, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                if (!takeFallDamage) {
                    var fallAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE);
                    if (fallAttr != null && fallAttr.getModifier(fallModId) == null) {
                        fallAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            fallModId, 1000.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                    }
                }
            })
            .onRevoked(player -> {
                var gravAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
                if (gravAttr != null) gravAttr.removeModifier(gravModId);
                var fallAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE);
                if (fallAttr != null) fallAttr.removeModifier(fallModId);
            })
            .build();
    }

    /** Per-player MOD_FALL_DAMAGE modifier tokens, keyed by power id so each
     *  modify_fall_damage instance registers + unregisters its own handler.
     *  Mirrors ActionOnEventPower's per-config token bookkeeping (idempotent
     *  re-grant: login/respawn/origin-swap all re-call onGranted). */
    private static final java.util.Map<java.util.UUID,
        java.util.Map<String, com.cyberday1.neoorigins.service.EventPowerIndex.Token>>
        FALL_DAMAGE_TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * {@code apoli:modify_fall_damage} — scales incoming fall damage, optionally
     * gated by a condition. Apoli carries the scale as a {@code modifier} object
     * or {@code modifiers} array of {@code {operation, value}} entries (e.g.
     * {@code {"operation":"multiply_base_additive","value":-0.5}} = "fall damage
     * * (1 + -0.5)" = halved).
     *
     * <p>Reuses the EXISTING native fall-damage plumbing rather than adding a new
     * field: {@link com.cyberday1.neoorigins.event.MovementPowerEvents#onLivingFall}
     * already chains {@code EventPowerIndex.dispatchModifier(sp, MOD_FALL_DAMAGE,
     * event, currentMultiplier)} onto the {@code LivingFallEvent} damage multiplier
     * (the same seam {@code neoorigins:action_on_event event=mod_fall_damage}
     * consumes). On grant we register a {@link
     * com.cyberday1.neoorigins.service.EventPowerIndex.ModifierHandler} there; on
     * revoke we remove it — exactly the {@link
     * com.cyberday1.neoorigins.power.builtin.ActionOnEventPower} pattern.
     *
     * <p>The operation+value → multiplier math goes through {@link
     * com.cyberday1.neoorigins.compat.modifier.ModifierParser#parseList}, the same
     * chokepoint native {@code action_on_event} uses, so the semantics stay
     * consistent (e.g. {@code multiply_base_additive -0.5}: {@code base -> base +
     * base*-0.5 = base*0.5}; {@code addition -1.0}: subtracts a flat 1.0;
     * {@code set_total 0}: hard-zeroes the multiplier = no fall damage).
     */
    private CompatPower.Config parseModifyFallDamage(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Apoli singular "modifier" or plural "modifiers"; ModifierParser.parseList
        // accepts a single object or an array. Mirror parseNumericModifier's
        // tolerance: prefer "modifier", fall back to "modifiers".
        JsonElement modEl = json.has("modifier")  ? json.get("modifier")
                          : json.has("modifiers") ? json.get("modifiers")
                          : null;
        if (modEl == null) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_fall_damage '{}' missing modifier/modifiers — skipped", id);
            CompatTranslationLog.skip(id, "origins:modify_fall_damage", "missing 'modifier'/'modifiers'");
            return null;
        }
        final com.cyberday1.neoorigins.compat.modifier.FloatModifier modifier =
            com.cyberday1.neoorigins.compat.modifier.ModifierParser.parseList(modEl, idStr);

        // Gating condition: present on the power (standalone) or carried over from
        // a conditioned_attribute wrapper (parseConditionedAttribute forwards the
        // whole object here). Absent ⇒ alwaysTrue (unconditional scale).
        final EntityCondition condition = parseConditionField(json, "condition", idStr);

        return CompatPower.Config.builder()
            .onGranted(player -> registerFallDamageHandler(player, idStr, modifier, condition))
            .onRevoked(player -> unregisterFallDamageHandler(player, idStr))
            .build();
    }

    /**
     * Register a condition-gated fall-damage scaler on the MOD_FALL_DAMAGE seam,
     * keyed per power id in {@link #FALL_DAMAGE_TOKENS}. Idempotent re-grant:
     * drops any prior handler for this power id before re-registering, so
     * login/respawn/origin-swap can't stack multiple multipliers (the
     * ActionOnEventPower leak-guard). Shared by parseModifyFallDamage and the
     * fall_damage entries of parseConditionedAttribute.
     */
    private static void registerFallDamageHandler(ServerPlayer player, String idStr,
            com.cyberday1.neoorigins.compat.modifier.FloatModifier modifier, EntityCondition condition) {
        var perPower = FALL_DAMAGE_TOKENS.get(player.getUUID());
        if (perPower != null) {
            var existing = perPower.remove(idStr);
            if (existing != null) {
                com.cyberday1.neoorigins.service.EventPowerIndex.unregister(existing);
            }
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.ModifierHandler handler =
            (sp, ctx, base) -> {
                try {
                    if (!condition.test(sp)) return base;
                    return modifier.apply(base);
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn("[CompatB] modify_fall_damage handler error ({}): {}",
                        idStr, e.getMessage());
                    return base;
                }
            };
        var tok = com.cyberday1.neoorigins.service.EventPowerIndex.registerModifier(
            player, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_FALL_DAMAGE, handler);
        FALL_DAMAGE_TOKENS.computeIfAbsent(player.getUUID(),
            k -> new java.util.concurrent.ConcurrentHashMap<>()).put(idStr, tok);
    }

    private static void unregisterFallDamageHandler(ServerPlayer player, String idStr) {
        var perPower = FALL_DAMAGE_TOKENS.get(player.getUUID());
        if (perPower != null) {
            var tok = perPower.remove(idStr);
            if (tok != null) {
                com.cyberday1.neoorigins.service.EventPowerIndex.unregister(tok);
            }
            if (perPower.isEmpty()) FALL_DAMAGE_TOKENS.remove(player.getUUID());
        }
    }

    /**
     * Per-player {@link com.cyberday1.neoorigins.service.EventPowerIndex} tokens
     * for the legacy modifier-seam compat powers, keyed by
     * {@code EVENT|powerId} so one power may hold handlers on several seams and
     * each unregisters only its own. Same bookkeeping as {@link
     * #FALL_DAMAGE_TOKENS}; see {@link #registerLegacyModifier} for the
     * idempotent-re-grant rationale.
     */
    private static final java.util.Map<java.util.UUID,
        java.util.Map<String, com.cyberday1.neoorigins.service.EventPowerIndex.Token>>
        LEGACY_MODIFIER_TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Register a condition-gated numeric modifier on one of the EventPowerIndex
     * modifier seams. Idempotent re-grant: any prior handler for the same
     * (event, power id) is dropped first, so login / respawn / origin-swap
     * cannot stack duplicates (the ActionOnEventPower leak-guard).
     *
     * <p>The op→number math goes through {@link OriginsModifierMath}, NOT
     * {@link com.cyberday1.neoorigins.compat.modifier.ModifierParser}. That is
     * deliberate: these two power types are 1.16–1.18-vintage Origins, whose
     * modifier objects use the attribute-style operation names
     * {@code multiply_base} / {@code multiply_total} / {@code addition}.
     * ModifierParser only cases the modern Apoli names
     * ({@code multiply_base_additive} and friends) and returns identity for
     * anything else, so routing these through it would make every real-world
     * use of both types a silent no-op.
     */
    private static void registerLegacyModifier(ServerPlayer player, String idStr,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event event,
            java.util.List<OriginsModifierMath.Modifier> mods, EntityCondition condition) {
        String key = event.name() + '|' + idStr;
        var perPower = LEGACY_MODIFIER_TOKENS.get(player.getUUID());
        if (perPower != null) {
            var existing = perPower.remove(key);
            if (existing != null) {
                com.cyberday1.neoorigins.service.EventPowerIndex.unregister(existing);
            }
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.ModifierHandler handler =
            (sp, ctx, base) -> {
                try {
                    if (!condition.test(sp)) return base;
                    double out = OriginsModifierMath.apply(base, mods);
                    // Defence-in-depth: a pack that writes a non-finite value must
                    // not be able to poison a heal amount or an effect duration.
                    if (!Double.isFinite(out)) return base;
                    return (float) out;
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn("[CompatB] {} handler error ({}): {}",
                        event, idStr, e.getMessage());
                    return base;
                }
            };
        var tok = com.cyberday1.neoorigins.service.EventPowerIndex.registerModifier(
            player, event, handler);
        LEGACY_MODIFIER_TOKENS.computeIfAbsent(player.getUUID(),
            k -> new java.util.concurrent.ConcurrentHashMap<>()).put(key, tok);
    }

    private static void unregisterLegacyModifier(ServerPlayer player, String idStr,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event event) {
        var perPower = LEGACY_MODIFIER_TOKENS.get(player.getUUID());
        if (perPower != null) {
            var tok = perPower.remove(event.name() + '|' + idStr);
            if (tok != null) {
                com.cyberday1.neoorigins.service.EventPowerIndex.unregister(tok);
            }
            if (perPower.isEmpty()) LEGACY_MODIFIER_TOKENS.remove(player.getUUID());
        }
    }

    /**
     * {@code origins:modify_healing} — scales health the holder regains.
     *
     * <p>Rides the existing MOD_NATURAL_REGEN seam, which
     * {@link com.cyberday1.neoorigins.event.WorldPowerEvents#onLivingHeal}
     * dispatches from {@code LivingHealEvent} with the heal amount as the base.
     * That event covers <em>all</em> healing (natural regeneration, potions,
     * golden apples, {@code /heal}), which is exactly Apoli's
     * {@code modify_healing} contract — the seam's enum comment calls it
     * "natural heal" for its original caller, but the hook itself is not
     * restricted to natural regen.
     */
    private CompatPower.Config parseModifyHealing(Identifier id, JsonObject json) {
        String idStr = id.toString();
        java.util.List<OriginsModifierMath.Modifier> mods = parseModifierList(json, "modifier");
        if (mods.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_healing '{}' missing modifier/modifiers — skipped", id);
            CompatTranslationLog.skip(id, "origins:modify_healing", "missing 'modifier'/'modifiers'");
            return null;
        }
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        return CompatPower.Config.builder()
            .onGranted(player -> registerLegacyModifier(player, idStr,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_NATURAL_REGEN, mods, condition))
            .onRevoked(player -> unregisterLegacyModifier(player, idStr,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_NATURAL_REGEN))
            .build();
    }

    /**
     * {@code origins:modify_status_effect_duration} — scales the duration of
     * mob effects applied to the holder.
     *
     * <p>Rides the MOD_POTION_DURATION seam that
     * {@link com.cyberday1.neoorigins.event.CombatPowerEvents} dispatches from
     * {@code MobEffectEvent.Added}. Note the base there is {@code 1.0f} — the
     * seam collects a <em>multiplier</em> which the dispatch site then applies
     * to the instance's duration — so {@code multiply_total 0.5} yields 1.5x
     * and reads as "effects last 50% longer", matching how packs describe it.
     * The scale applies to every effect added to the holder; no per-effect
     * filter is offered because the corpus does not use one and the field name
     * Apoli would spell it with is unverified here.
     */
    private CompatPower.Config parseModifyEffectDuration(Identifier id, JsonObject json) {
        String idStr = id.toString();
        java.util.List<OriginsModifierMath.Modifier> mods = parseModifierList(json, "modifier");
        if (mods.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_status_effect_duration '{}' missing modifier/modifiers — skipped", id);
            CompatTranslationLog.skip(id, "origins:modify_status_effect_duration", "missing 'modifier'/'modifiers'");
            return null;
        }
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        return CompatPower.Config.builder()
            .onGranted(player -> registerLegacyModifier(player, idStr,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_POTION_DURATION, mods, condition))
            .onRevoked(player -> unregisterLegacyModifier(player, idStr,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_POTION_DURATION))
            .build();
    }

    /** Per-player DEATH handler tokens for {@code action_on_death}, keyed by power id. */
    private static final java.util.Map<java.util.UUID,
        java.util.Map<String, com.cyberday1.neoorigins.service.EventPowerIndex.Token>>
        DEATH_ACTION_TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * {@code origins:action_on_death} — fires when the HOLDER dies.
     * {@code entity_action} runs on the dying holder; {@code bientity_action}
     * runs with (actor = the holder, target = the killer), Apoli's pairing.
     *
     * <p>Rides the existing DEATH seam rather than adding a {@code CompatPower}
     * hook: {@code CombatPowerEvents.onLivingDeath} already dispatches
     * {@code Event.DEATH} with the cancellable {@code LivingDeathEvent} itself
     * as the context, which is what carries the {@code DamageSource} the killer
     * is read from. Registration follows {@link #registerFallDamageHandler}'s
     * per-power token bookkeeping so a re-grant cannot stack handlers.
     *
     * <p>The bientity half is skipped when the death had no living attacker
     * (fall, drowning, {@code /kill}) — there is no target to pair with, and
     * running the action against the holder would invert its meaning.
     */
    private CompatPower.Config parseActionOnDeath(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = parseActionField(json, "entity_action", idStr);
        com.cyberday1.neoorigins.compat.action.BiEntityAction biAction =
            parseBiEntityActionField(json, "bientity_action", idStr);
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        return CompatPower.Config.builder()
            .onGranted(player -> registerDeathActionHandler(player, idStr, action, biAction, condition))
            .onRevoked(player -> unregisterDeathActionHandler(player, idStr))
            .build();
    }

    private static void registerDeathActionHandler(ServerPlayer player, String idStr,
            EntityAction action, com.cyberday1.neoorigins.compat.action.BiEntityAction biAction,
            EntityCondition condition) {
        var perPower = DEATH_ACTION_TOKENS.get(player.getUUID());
        if (perPower != null) {
            var existing = perPower.remove(idStr);
            if (existing != null) {
                com.cyberday1.neoorigins.service.EventPowerIndex.unregister(existing);
            }
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.Handler handler = (sp, ctx) -> {
            try {
                if (!condition.test(sp)) return;
                action.execute(sp);
                if (biAction == com.cyberday1.neoorigins.compat.action.BiEntityAction.NOOP) return;
                if (ctx instanceof net.neoforged.neoforge.event.entity.living.LivingDeathEvent de
                        && de.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity killer
                        && killer != sp) {
                    biAction.execute(sp, killer);
                }
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn("[CompatB] action_on_death handler error ({}): {}",
                    idStr, e.getMessage());
            }
        };
        var tok = com.cyberday1.neoorigins.service.EventPowerIndex.register(
            player, com.cyberday1.neoorigins.service.EventPowerIndex.Event.DEATH, handler);
        DEATH_ACTION_TOKENS.computeIfAbsent(player.getUUID(),
            k -> new java.util.concurrent.ConcurrentHashMap<>()).put(idStr, tok);
    }

    private static void unregisterDeathActionHandler(ServerPlayer player, String idStr) {
        var perPower = DEATH_ACTION_TOKENS.get(player.getUUID());
        if (perPower != null) {
            var tok = perPower.remove(idStr);
            if (tok != null) {
                com.cyberday1.neoorigins.service.EventPowerIndex.unregister(tok);
            }
            if (perPower.isEmpty()) DEATH_ACTION_TOKENS.remove(player.getUUID());
        }
    }

    /**
     * {@code apoli:modify_velocity} — per-axis value-modifier applied to the
     * player's movement. Apoli applies this inside its {@code Entity.move}
     * mixin, transforming the movement vector each step on the {@code axes}
     * the power enables (default: all three).
     *
     * <p>This is a server-side approximation: each tick we read the player's
     * delta movement, run the parsed modifiers through {@link OriginsModifierMath}
     * on every enabled axis, write it back and flag {@code hurtMarked} so the
     * change is synced to the client (the same mechanism {@link #parseLaunch}
     * uses). A pixel-perfect port would need a shared {@code Entity.move} mixin
     * plus client-synced power data; this loads + functions for the common
     * speed/restriction cases without that subsystem.
     */
    private CompatPower.Config parseModifyVelocity(Identifier id, JsonObject json) {
        String idStr = id.toString();
        java.util.List<OriginsModifierMath.Modifier> mods = parseModifierList(json, "modifier");
        if (mods.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_velocity '{}' missing modifier/modifiers — skipped", id);
            return null;
        }

        // The condition gates whether the velocity transform applies this tick.
        // CRITICAL: many real packs use modify_velocity to zero velocity (a "stop"
        // effect) gated on a resource — applying it unconditionally would freeze
        // the player. If the condition can't be parsed, fail closed (no-op power)
        // rather than risk applying it always.
        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_velocity {} has unsupported condition(s) — refusing to compile", idStr);
            return null;
        }

        // Apoli "axes" is an axis-set; default is all three. Accept the array
        // form (["x","z"]) and treat a missing field as "all axes".
        boolean applyX = true, applyY = true, applyZ = true;
        if (json.has("axes") && json.get("axes").isJsonArray()) {
            applyX = applyY = applyZ = false;
            for (JsonElement el : json.getAsJsonArray("axes")) {
                switch (el.getAsString().toLowerCase(java.util.Locale.ROOT)) {
                    case "x" -> applyX = true;
                    case "y" -> applyY = true;
                    case "z" -> applyZ = true;
                    default -> { /* ignore unknown axis token */ }
                }
            }
        }
        final boolean fx = applyX, fy = applyY, fz = applyZ;

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (!condition.test(player)) return;
                Vec3 v = player.getDeltaMovement();
                double nx = fx ? OriginsModifierMath.apply(v.x, mods) : v.x;
                double ny = fy ? OriginsModifierMath.apply(v.y, mods) : v.y;
                double nz = fz ? OriginsModifierMath.apply(v.z, mods) : v.z;
                if (nx != v.x || ny != v.y || nz != v.z) {
                    player.setDeltaMovement(nx, ny, nz);
                    player.hurtMarked = true; // sync the velocity change to the client
                }
            })
            .build();
    }

    /** apoli:overlay — screen overlay effect. Parsed as a no-op since overlay rendering
     *  requires client-side hooks not yet ported. The power loads successfully so it
     *  doesn't count against the compat power-ratio threshold. */
    private CompatPower.Config parseOverlay(Identifier id, JsonObject json) {
        return CompatPower.Config.builder().build();
    }

    private CompatPower.Config parseConditionedStatusEffect(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Resolve effect — try "effects" array or singular "effect" field.
        String effectId = null;
        int    amplifier  = 0;
        boolean ambient   = false;
        boolean particles = true;

        if (json.has("effects") && json.get("effects").isJsonArray()) {
            var arr = json.getAsJsonArray("effects");
            if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                JsonObject eff = arr.get(0).getAsJsonObject();
                if (eff.has("effect"))        effectId  = eff.get("effect").getAsString();
                if (eff.has("amplifier"))     amplifier = eff.get("amplifier").getAsInt();
                if (eff.has("ambient"))       ambient   = eff.get("ambient").getAsBoolean();
                if (eff.has("show_particles"))particles = eff.get("show_particles").getAsBoolean();
            }
        } else if (json.has("effect")) {
            effectId  = json.get("effect").getAsString();
            if (json.has("amplifier"))     amplifier = json.get("amplifier").getAsInt();
            if (json.has("ambient"))       ambient   = json.get("ambient").getAsBoolean();
            if (json.has("show_particles"))particles = json.get("show_particles").getAsBoolean();
        }
        if (effectId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: missing effect id — power will no-op", idStr);
            return null;
        }

        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] conditioned_status_effect {} has unsupported condition(s) — refusing to compile", idStr);
            return null;
        }

        // Cache mob effect holder at parse time — registry is static
        var effectHolder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId)).orElse(null);
        if (effectHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: unknown mob effect '{}' — power will no-op", idStr, effectId);
            return null;
        }

        int  finalAmp       = amplifier;
        boolean finalAmb    = ambient;
        boolean finalPart   = particles;

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (!condition.test(player)) {
                    // Remove the effect when the condition is no longer met,
                    // so conditioned effects toggle off cleanly instead of
                    // lingering for their remaining duration.
                    player.removeEffect(effectHolder);
                    return;
                }
                var existing = player.getEffect(effectHolder);
                // Re-apply at 200t duration if missing or about to expire (<100t).
                if (existing == null || existing.getDuration() < 100) {
                    player.addEffect(new MobEffectInstance(
                        effectHolder, 200, finalAmp, finalAmb, finalPart, true));
                }
            })
            .onRevoked(player -> player.removeEffect(effectHolder))
            .build();
    }

    private CompatPower.Config parseSelfActionWhenHit(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = parseActionField(json, "entity_action", idStr);
        // bientity_action: (actor=player, target=attacker). The attacker is
        // resolved from HitTakenContext, which CombatPowerEvents publishes to
        // ActionContextHolder around the onHit dispatch.
        com.cyberday1.neoorigins.compat.action.BiEntityAction biAction =
            parseBiEntityActionField(json, "bientity_action", idStr);
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        return CompatPower.Config.builder()
            .cooldownTicks(cooldown)
            .onHit(player -> {
                if (!condition.test(player)) return;
                if (cooldown > 0) {
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    if (data.isOnCooldown(player, idStr)) return;
                    data.setCooldown(idStr, player.tickCount, cooldown);
                }
                action.execute(player);
                if (biAction != com.cyberday1.neoorigins.compat.action.BiEntityAction.NOOP) {
                    Object ctx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
                    if (ctx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext hitCtx
                            && hitCtx.source().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                            && attacker != player) {
                        biAction.execute(player, attacker);
                    }
                }
            })
            .build();
    }

    /**
     * Apoli {@code self_action_on_hit} / {@code action_on_hit} — fires when the
     * HOLDER deals damage to a living entity. {@code entity_action} runs on the
     * holder; {@code bientity_action} runs with (actor=holder, target=victim).
     * Dispatched from CombatPowerEvents' player-as-attacker block via the
     * CompatPower {@code onDealDamage} hook, which passes the victim directly.
     */
    private CompatPower.Config parseSelfActionOnHit(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = parseActionField(json, "entity_action", idStr);
        com.cyberday1.neoorigins.compat.action.BiEntityAction biAction =
            parseBiEntityActionField(json, "bientity_action", idStr);
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        return CompatPower.Config.builder()
            .cooldownTicks(cooldown)
            .onDealDamage((player, target) -> {
                if (!condition.test(player)) return;
                if (cooldown > 0) {
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    if (data.isOnCooldown(player, idStr)) return;
                    data.setCooldown(idStr, player.tickCount, cooldown);
                }
                action.execute(player);
                if (biAction != com.cyberday1.neoorigins.compat.action.BiEntityAction.NOOP) {
                    biAction.execute(player, target);
                }
            })
            .build();
    }

    private CompatPower.Config parseDamageOverTime(Identifier id, JsonObject json) {
        String idStr = id.toString();
        int interval = Math.max(1, json.has("interval") ? json.get("interval").getAsInt() : 20);
        float damage  = json.has("damage")            ? json.get("damage").getAsFloat()
                      : json.has("damage_per_second") ? json.get("damage_per_second").getAsFloat() : 1.0f;

        // Determine if the damage source should bypass armor.
        boolean unblockable = false;
        if (json.has("source") && json.get("source").isJsonObject()) {
            JsonObject src = json.getAsJsonObject("source");
            unblockable = (src.has("unblockable") && src.get("unblockable").getAsBoolean())
                       || (src.has("bypasses_armor") && src.get("bypasses_armor").getAsBoolean());
        }

        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] damage_over_time {} has unsupported condition(s) — refusing to compile", idStr);
            return null;
        }

        int offset          = (idStr.hashCode() & Integer.MAX_VALUE) % interval;
        float finalDamage   = damage;
        boolean finalUnblock = unblockable;

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (player.level().getServer() == null) return;
                long tick = player.level().getServer().getTickCount();
                if ((tick + offset) % interval == 0 && condition.test(player)) {
                    var dmgSrc = finalUnblock
                        ? player.level().damageSources().magic()
                        : player.level().damageSources().generic();
                    player.hurt(dmgSrc, finalDamage);
                }
            })
            .build();
    }

    private CompatPower.Config parseExhaust(Identifier id, JsonObject json) {
        String idStr = id.toString();
        int interval = Math.max(1, json.has("interval") ? json.get("interval").getAsInt() : 20);
        float amount = json.has("exhaustion") ? json.get("exhaustion").getAsFloat()
                     : json.has("amount")     ? json.get("amount").getAsFloat()
                     : 0.1f;
        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] exhaust {} has unsupported condition(s) — refusing to compile", idStr);
            return null;
        }
        int offset = (idStr.hashCode() & Integer.MAX_VALUE) % interval;
        float finalAmount = amount;
        return CompatPower.Config.builder()
            .onTick(player -> {
                if (player.level().getServer() == null) return;
                long tick = player.level().getServer().getTickCount();
                if ((tick + offset) % interval == 0 && condition.test(player)) {
                    player.causeFoodExhaustion(finalAmount);
                }
            })
            .build();
    }

    // ---- Phase 3: New Route B type parsers ----

    private CompatPower.Config parseFireProjectile(Identifier id, JsonObject json) {
        String idStr = id.toString();
        String rawEntityTypeStr = json.has("entity_type") ? json.get("entity_type").getAsString() : "minecraft:arrow";
        // Legacy code-registered Origins entities (origins:enderian_pearl) remap
        // to their vanilla equivalent; the pearl keeps Origins' no-fall-damage /
        // no-endermite landing via the flag + CompatEventPowers.onEnderPearlTeleport.
        String entityTypeStr = LegacyEntityIds.remap(rawEntityTypeStr);
        boolean enderianPearl = !entityTypeStr.equals(rawEntityTypeStr)
            && "minecraft:ender_pearl".equals(entityTypeStr);
        float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.5f;
        float divergence = json.has("divergence") ? json.get("divergence").getAsFloat() : 1.0f;
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;

        // Apoli's `tag`: SNBT merged into the projectile before spawning. Parse
        // at load time; legacy code-registered block ids inside get remapped.
        // 26.1: TagParser.parseTag is gone — use parseCompoundFully.
        CompoundTag parsedTag = null;
        if (json.has("tag")) {
            try {
                parsedTag = TagParser.parseCompoundFully(json.get("tag").getAsString());
                LegacyBlockIds.remapNbt(parsedTag);
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn("[CompatB] fire_projectile {}: unparseable tag '{}' — ignoring ({})",
                    idStr, json.get("tag").getAsString(), e.getMessage());
            }
        }
        CompoundTag tag = parsedTag;

        EntityType<?> resolvedType = Identifier.tryParse(entityTypeStr) != null
            ? BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entityTypeStr)).orElse(null)
            : null;
        if (resolvedType == null) {
            NeoOrigins.LOGGER.warn("[CompatB] fire_projectile {}: unknown entity type '{}' — refusing to compile",
                idStr, entityTypeStr);
            return null;
        }
        EntityType<?> entityType = resolvedType;

        // Activation gate — e.g. the Origins++ shifter pearl is only usable in
        // Enderian form (a resource condition). Previously ignored, which let the
        // projectile fire regardless of form.
        EntityCondition condition = parseConditionField(json, "condition", idStr);

        return CompatPower.Config.builder()
            .cooldownTicks(cooldown)
            .onActivated(player -> {
                if (!condition.test(player)) return;
                if (cooldown > 0) {
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    if (data.isOnCooldown(player, idStr)) return;
                    data.setCooldown(idStr, player.tickCount, cooldown);
                }
                if (!(player.level() instanceof ServerLevel sl)) return;
                Vec3 look = player.getLookAngle();
                for (int i = 0; i < count; i++) {
                    net.minecraft.world.entity.Entity entity;
                    if ("minecraft:small_fireball".equals(entityTypeStr)) {
                        // Constructor already aims the fireball (acceleration power).
                        entity = new SmallFireball(sl, player, look.scale(speed));
                        entity.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                    } else {
                        entity = entityType.create(sl, EntitySpawnReason.TRIGGERED);
                        if (entity == null) continue;
                        entity.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                        if (entity instanceof Projectile projectile) {
                            projectile.setOwner(player);
                            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, speed, divergence);
                        } else {
                            entity.setDeltaMovement(look.scale(speed));
                        }
                    }
                    if (tag != null) {
                        // Apoli merge order: current data <- pack tag, then reload.
                        // 26.1: entity save/load flows through ValueOutput/ValueInput.
                        var provider = sl.registryAccess();
                        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                            net.minecraft.util.ProblemReporter.DISCARDING, provider);
                        entity.saveWithoutId(out);
                        CompoundTag merged = out.buildResult();
                        merged.merge(tag);
                        entity.load(net.minecraft.world.level.storage.TagValueInput.create(
                            net.minecraft.util.ProblemReporter.DISCARDING, provider, merged));
                    }
                    if (enderianPearl) {
                        entity.getPersistentData().putBoolean(LegacyEntityIds.ENDERIAN_PEARL_FLAG, true);
                    }
                    sl.addFreshEntity(entity);
                }
            })
            .build();
    }

    private CompatPower.Config parseTargetActionOnHit(Identifier id, JsonObject json) {
        String idStr = id.toString();
        // [LOSSY] target_action_on_hit fires on kill, not on every hit (no hit event for target entity)
        EntityAction action = parseActionField(json, "entity_action", idStr);
        return CompatPower.Config.builder()
            .onKill(action::execute)
            .build();
    }

    private CompatPower.Config parseSelfActionOnKill(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = parseActionField(json, "entity_action", idStr);
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        return CompatPower.Config.builder()
            .onKill(player -> {
                if (condition.test(player)) action.execute(player);
            })
            .build();
    }

    private CompatPower.Config parseLaunch(Identifier id, JsonObject json) {
        String idStr = id.toString();
        float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.0f;
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;

        EntityAction launchAction = player -> {
            player.push(0, speed, 0);
            player.hurtMarked = true;
        };

        var builder = CompatPower.Config.builder().cooldownTicks(cooldown);

        // Activation gate + fail feedback (fail_action is a NeoOrigins extension).
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        EntityAction failAction = json.has("fail_action")
            ? parseActionField(json, "fail_action", idStr) : null;

        // launch can be bound to a pack-declared named hotkey; register it if so. Cooldown
        // is enforced by PowerKeybindRegistry.dispatch for the named-key path, so the action
        // itself stays cooldown-free there to avoid double-gating.
        String key = "key.origins.primary_active";
        boolean continuous = false;
        if (json.has("key")) {
            var keyEl = json.get("key");
            if (keyEl.isJsonPrimitive()) {
                key = normalizeKeyToken(keyEl, key);
            } else if (keyEl.isJsonObject()) {
                var keyObj = keyEl.getAsJsonObject();
                key = keyObj.has("key") ? normalizeKeyToken(keyObj.get("key"), key) : key;
                continuous = keyObj.has("continuous") && keyObj.get("continuous").getAsBoolean();
            }
        }
        // The two toolbar (creative-hotbar save/load) keys are a special case.
        // Apoli packs bind MANY condition-gated active_self powers to the same
        // toolbar key (the Seer progression rituals are six powers all on
        // saveToolbarActivator), expecting every one to be evaluated on each
        // press — exactly the multi-binding fan-out the named-hotkey dispatch
        // path provides. A skill slot can only host a single power per slot AND
        // the client never sends an activation for the vanilla toolbar keys, so
        // routing these to a skill slot silently drops them. Treat them as a
        // dedicated bucket that flows through PowerKeybindRegistry instead.
        boolean isToolbarKey = key.contains("loadToolbarActivator")
            || key.contains("saveToolbarActivator");
        boolean isSlotKey = !isToolbarKey && (key.contains("primary_active")
            || key.contains("secondary_active") || key.contains("pickItem"));
        boolean isVanillaInputKey = switch (key) {
            case "key.sneak", "key.use", "key.attack", "key.jump", "key.sprint",
                 "key.forward", "key.back", "key.left", "key.right" -> true;
            default -> false;
        };
        if (!isSlotKey && !isVanillaInputKey) {
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.register(key,
                new com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.Binding(
                    id, launchAction, condition, cooldown, continuous, failAction));
            return builder.build();
        }
        return builder
            .onActivated(player -> {
                // Enforce the declared condition on the slot path too (parity with the hotkey path).
                if (!condition.test(player)) {
                    if (failAction != null) failAction.execute(player);
                    return;
                }
                if (cooldown > 0) {
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    if (data.isOnCooldown(player, idStr)) return;
                    data.setCooldown(idStr, player.tickCount, cooldown);
                }
                launchAction.execute(player);
            })
            .build();
    }

    private CompatPower.Config parseEntityGlow(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = parseConditionField(json, "condition", idStr);

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (condition.test(player)) {
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, false));
                }
            })
            .build();
    }

    private CompatPower.Config parsePreventDeath(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = parseActionField(json, "entity_action", idStr);

        // Prevent death by clamping health at 1hp each tick when it would drop below
        return CompatPower.Config.builder()
            .onTick(player -> {
                if (player.getHealth() <= 0.0f && player.isAlive()) {
                    player.setHealth(1.0f);
                    action.execute(player);
                }
            })
            .onHit(player -> {
                // After being hit, clamp health to at least 1hp
                if (player.getHealth() <= 0.5f) {
                    player.setHealth(1.0f);
                    action.execute(player);
                }
            })
            .build();
    }

    private CompatPower.Config parseActionOnLand(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = parseActionField(json, "entity_action", idStr);

        // Detect ground transition: was airborne, now grounded
        String airborneKey = idStr + "/_airborne";
        return CompatPower.Config.builder()
            .onTick(player -> {
                var state = player.getData(CompatAttachments.resourceState());
                boolean wasAirborne = state.get(airborneKey, 0) == 1;
                boolean isGrounded = player.onGround();
                if (wasAirborne && isGrounded) {
                    action.execute(player);
                }
                state.set(airborneKey, isGrounded ? 0 : 1);
            })
            .build();
    }

    // ---- Phase 5: Event-based power parsers ----
    // All conditions are pre-compiled here at load time, not at event time.

    private CompatPower.Config parsePreventItemUse(Identifier id, JsonObject json) {
        String idStr = id.toString();
        // The power-level `condition` is the HOLDER gate (e.g. mainhand empty +
        // offhand holds a spell item). `item_condition` is the TARGET gate (which
        // item is being used). Both must be honoured, or the prevention fires
        // unconditionally — which is exactly the Mage "blocks randomly" bug.
        EntityCondition condition = json.has("condition")
            ? parseConditionField(json, "condition", idStr) : null;
        var itemPred = json.has("item_condition")
            ? compileItemPredicate(json.getAsJsonObject("item_condition")) : null;
        var data = new CompatPlayerState.EventPowerData(
            idStr, CompatPlayerState.EventType.PREVENT_ITEM_USE,
            condition, itemPred, null, null, null);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parseRestrictArmor(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Compile per-slot predicates
        java.util.function.Predicate<ItemStack> globalPred = json.has("item_condition")
            ? compileItemPredicate(json.getAsJsonObject("item_condition")) : null;
        java.util.function.Predicate<ItemStack> headPred = json.has("head")
            ? compileItemPredicate(json.getAsJsonObject("head")) : null;
        java.util.function.Predicate<ItemStack> chestPred = json.has("chest")
            ? compileItemPredicate(json.getAsJsonObject("chest")) : null;
        java.util.function.Predicate<ItemStack> legsPred = json.has("legs")
            ? compileItemPredicate(json.getAsJsonObject("legs")) : null;
        java.util.function.Predicate<ItemStack> feetPred = json.has("feet")
            ? compileItemPredicate(json.getAsJsonObject("feet")) : null;

        CompatPlayerState.ArmorPredicate armorPred = (stack, slot) -> {
            var slotPred = switch (slot) {
                case HEAD  -> headPred;
                case CHEST -> chestPred;
                case LEGS  -> legsPred;
                case FEET  -> feetPred;
                default    -> null;
            };
            if (slotPred != null) return slotPred.test(stack);
            if (globalPred != null) return globalPred.test(stack);
            return true; // No condition = restrict all armor
        };

        var data = CompatPlayerState.EventPowerData.withArmorPredicate(idStr, armorPred);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parsePreventSleep(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = json.has("condition")
            ? parseConditionField(json, "condition", idStr) : null;
        // Origins prevent_sleep supports block_condition to gate on the bed's
        // position (e.g. height < 70 = can't sleep below Y 70).
        java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos> blockCond = null;
        if (json.has("block_condition") && json.get("block_condition").isJsonObject()) {
            blockCond = compileSleepBlockCondition(json.getAsJsonObject("block_condition"));
        }
        var data = new CompatPlayerState.EventPowerData(
            idStr, CompatPlayerState.EventType.PREVENT_SLEEP,
            condition, null, blockCond, null, null);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    /**
     * Compiles a block_condition for prevent_sleep. Supports height checks
     * (the bed's Y coordinate) and delegates to compileBlockPredicate for
     * block-type checks.
     */
    private static java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos> compileSleepBlockCondition(JsonObject condJson) {
        String type = condJson.has("type") ? condJson.get("type").getAsString() : "";
        String bareType = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        if ("height".equals(bareType)) {
            String comp = condJson.has("comparison") ? condJson.get("comparison").getAsString() : ">=";
            int target = condJson.has("compare_to") ? condJson.get("compare_to").getAsInt() : 0;
            var comparison = com.cyberday1.neoorigins.compat.condition.ComparisonType.fromString(comp);
            return (player, pos) -> comparison.test(pos.getY(), target);
        }
        // Fall back to standard block predicate (block type / tag checks)
        return compileBlockPredicate(condJson);
    }

    private CompatPower.Config parsePreventBlockUse(Identifier id, JsonObject json) {
        String idStr = id.toString();
        // Power-level `condition` = HOLDER gate; `block_condition` = TARGET (block)
        // gate. Dropping the holder gate made block-use prevention fire whenever
        // the power was granted (the Mage "can't place blocks" bug).
        EntityCondition condition = json.has("condition")
            ? parseConditionField(json, "condition", idStr) : null;
        var blockPred = json.has("block_condition")
            ? compileBlockPredicate(json.getAsJsonObject("block_condition")) : null;
        var data = new CompatPlayerState.EventPowerData(
            idStr, CompatPlayerState.EventType.PREVENT_BLOCK_USE,
            condition, null, blockPred, null, null);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    /**
     * {@code origins:prevent_entity_use} — the power-level {@code condition} is
     * the HOLDER gate, {@code item_condition} gates the held stack, and
     * {@code entity_condition} / {@code bientity_condition} gate the TARGET (the
     * entity being interacted with; for bientity, actor = the holder, target =
     * that entity). All of them are optional; all absent = prevent every entity
     * interaction, which is the correct reading of a bare Apoli
     * prevent_entity_use.
     *
     * <p>Dropping the target gates made this cancel EVERY entity interaction as
     * soon as the power was granted — villager trading, saddling, leads, boats,
     * sitting (issue #118: a Red Riding Hood origin narrowed to minecraft:wolf
     * blocked everything). The bug scaled with how carefully the author narrowed
     * the condition, so the conditions are now compiled at load time and
     * evaluated against {@code event.getTarget()} in
     * {@link CompatEventPowers#onEntityInteract}.
     *
     * <p>Fail direction is CLOSED, mirroring action_over_time and swimming: an
     * unsupported or unparseable condition means we cannot tell WHICH entities
     * to block, and for a prevention "unknown" degrading to "block everything"
     * is exactly the reported bug. The power is refused instead, so the author
     * gets a warning and an inert power rather than a silently over-blocking one.
     */
    private CompatPower.Config parsePreventEntityUse(Identifier id, JsonObject json) {
        String idStr = id.toString();
        CompatPolicy.resetFailClosedCount();

        EntityCondition condition = json.has("condition")
            ? parseConditionField(json, "condition", idStr) : null;
        // Snapshot the holder gate's fail-closed signal immediately: the bientity
        // parser resets the counter for its own actor_condition sub-parse.
        boolean unparseable = CompatPolicy.failClosedCount() > 0;

        var itemPred = json.has("item_condition") && json.get("item_condition").isJsonObject()
            ? compileItemPredicate(json.getAsJsonObject("item_condition")) : null;

        java.util.function.BiPredicate<ServerPlayer, net.minecraft.world.entity.Entity> targetPred = null;

        // entity_condition — tested against the entity being used.
        if (json.has("entity_condition") && json.get("entity_condition").isJsonObject()) {
            var tc = com.cyberday1.neoorigins.compat.condition.TargetConditionParser
                .parse(json.getAsJsonObject("entity_condition"), idStr);
            if (tc == null) {
                unparseable = true;
            } else {
                targetPred = com.cyberday1.neoorigins.compat.condition.TargetConditionParser
                    .asTargetPredicate(tc);
            }
        }

        // bientity_condition — actor = the holder, target = the entity being used.
        if (json.has("bientity_condition") && json.get("bientity_condition").isJsonObject()) {
            var bp = com.cyberday1.neoorigins.compat.condition.TargetConditionParser
                .parseBiEntity(json.getAsJsonObject("bientity_condition"), idStr);
            if (bp == null) {
                unparseable = true;
            } else if (targetPred == null) {
                targetPred = bp;
            } else {
                targetPred = targetPred.and(bp);
            }
        }

        if (unparseable) {
            NeoOrigins.LOGGER.warn("[CompatB] prevent_entity_use {} has unsupported condition(s) — refusing to compile to prevent unconditional blocking", idStr);
            CompatTranslationLog.skip(id, "origins:prevent_entity_use",
                "unsupported condition — refusing to block every entity interaction");
            return null;
        }

        var data = CompatPlayerState.EventPowerData.forPreventEntityUse(
            idStr, condition, itemPred, targetPred);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parseModifyFood(Identifier id, JsonObject json) {
        String idStr = id.toString();

        // Parse item_condition filter (optional — null means all food)
        java.util.function.Predicate<ItemStack> itemPred = null;
        if (json.has("item_condition") && json.get("item_condition").isJsonObject()) {
            itemPred = compileItemPredicate(json.getAsJsonObject("item_condition"));
        }

        // Parse food_modifier (single object or array)
        var foodMods = parseModifierList(json, "food_modifier");
        // Parse saturation_modifier (single object or array)
        var satMods = parseModifierList(json, "saturation_modifier");

        var entry = new ModifyFoodRegistry.Entry(idStr, itemPred, foodMods, satMods);
        return CompatPower.Config.builder()
            .onGranted(player -> ModifyFoodRegistry.register(player, entry))
            .onRevoked(player -> ModifyFoodRegistry.unregister(player, idStr))
            .build();
    }

    /** Parse an Apoli modifier or modifiers array into a list. */
    private static java.util.List<OriginsModifierMath.Modifier> parseModifierList(JsonObject json, String key) {
        java.util.List<OriginsModifierMath.Modifier> result = new java.util.ArrayList<>();
        if (json.has(key)) {
            var el = json.get(key);
            if (el.isJsonArray()) {
                for (var item : el.getAsJsonArray()) {
                    if (item.isJsonObject()) result.add(parseSingleModifier(item.getAsJsonObject()));
                }
            } else if (el.isJsonObject()) {
                result.add(parseSingleModifier(el.getAsJsonObject()));
            }
        }
        // Also check for plural "food_modifiers" / "saturation_modifiers"
        String plural = key + "s";
        if (json.has(plural)) {
            var el = json.get(plural);
            if (el.isJsonArray()) {
                for (var item : el.getAsJsonArray()) {
                    if (item.isJsonObject()) result.add(parseSingleModifier(item.getAsJsonObject()));
                }
            }
        }
        return result;
    }

    private static OriginsModifierMath.Modifier parseSingleModifier(JsonObject mod) {
        String operation = mod.has("operation") ? mod.get("operation").getAsString() : "addition";
        double value = mod.has("value") ? mod.get("value").getAsDouble()
                     : mod.has("amount") ? mod.get("amount").getAsDouble() : 0.0;
        return new OriginsModifierMath.Modifier(operation, value);
    }

    /**
     * Collapse a list of Apoli-shape modifier entries into a single damage
     * multiplier, preserving the same lossy per-entry mapping the singular
     * pre-v2.1.6 path used: {@code addition}/{@code multiply_*} contribute
     * additively to {@code 1 + Σvalue}; {@code set_total} overrides to
     * {@code max(0, 1 + value)} (clamped non-negative, last-write-wins among
     * multiple set_total entries — matches the single-modifier behavior).
     * Empty list ⇒ 1.0 (no-op), matching the previous "missing modifier"
     * fall-through that left {@code multiplier = 1.0f}.
     */
    private static float collapseDamageModifiers(java.util.List<OriginsModifierMath.Modifier> mods) {
        if (mods == null || mods.isEmpty()) return 1.0f;
        double additive = 0.0;
        Double setTotal = null;
        for (OriginsModifierMath.Modifier m : mods) {
            String op = m.operation() == null ? "addition" : m.operation();
            if ("set_total".equals(op)) {
                setTotal = m.value();
            } else {
                additive += m.value();
            }
        }
        double result = setTotal != null ? Math.max(0.0, 1.0 + setTotal) : (1.0 + additive);
        return (float) result;
    }

    // ---- Compile-time predicate builders for event powers ----

    /**
     * Compile an item condition JSON into a Predicate&lt;ItemStack&gt; at load time.
     *
     * <p>Delegates to the shared {@link com.cyberday1.neoorigins.compat.condition.ItemConditionParser}
     * so event-power filters get the full item-condition vocabulary
     * (and/or/not combinators, nbt legacy-view subtree, enchantment,
     * ingredient, amount, name, food, empty, untyped id/item/tag, the
     * universal {@code inverted} flag) instead of the previous ad-hoc
     * ingredient/id/tag/empty/food-only compiler — whose match-everything
     * fallthrough turned e.g. an {@code origins:and} filter on a restriction
     * power into "restrict ALL items". Unsupported leaf types still resolve
     * to match-all (fail-closed for restrictions) with a deduped warning
     * from the parser. The {@code origins:food} arm (the seer's no-eating
     * restriction vs. block placement) now lives in the parser as
     * {@code neoorigins:food}.
     */
    private static java.util.function.Predicate<ItemStack> compileItemPredicate(JsonObject condJson) {
        if (condJson == null) return null;
        var cond = com.cyberday1.neoorigins.compat.condition.ItemConditionParser.parse(condJson);
        return cond::test;
    }

    /** Compile a block condition JSON into a BiPredicate at load time. */
    public static java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos> compileBlockPredicate(
            JsonObject condJson) {
        return compileBlockPredicate(condJson, "block_condition");
    }

    /**
     * Compile an Apoli block condition into a positional predicate.
     * {@code contextId} identifies the owning power/site for diagnostics.
     * An unsupported leaf no longer falls through SILENTLY to match-all — it
     * still matches all (fail-open keeps legacy powers firing) but records a
     * deduplicated warning so pack debugging isn't a guessing game.
     */
    public static java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos> compileBlockPredicate(
            JsonObject condJson, String contextId) {
        if (condJson == null) return null;
        var base = compileBlockPredicateInner(condJson, contextId);
        // Apoli's generic `"inverted": true` flag applies to any condition node.
        if (condJson.has("inverted") && condJson.get("inverted").getAsBoolean()) {
            final var fBase = base;
            return (player, pos) -> !fBase.test(player, pos);
        }
        return base;
    }

    private static java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos> compileBlockPredicateInner(
            JsonObject condJson, String contextId) {
        // Boolean combinators: { "type": "neoorigins:or", "conditions": [...] }
        // (and/or, with the Apoli 2.9+ all_of/any_of aliases). Recurse so nested
        // and typed-leaf block conditions are honoured against `pos` instead of
        // falling through to the match-all base case below.
        String type = condJson.has("type") ? condJson.get("type").getAsString() : "";
        String bareType = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        if ((bareType.equals("and") || bareType.equals("or")
                || bareType.equals("all_of") || bareType.equals("any_of"))
                && condJson.has("conditions")) {
            boolean isAnd = bareType.equals("and") || bareType.equals("all_of");
            java.util.List<java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos>> subs =
                new java.util.ArrayList<>();
            for (JsonElement el : com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(condJson, "conditions")) {
                if (!el.isJsonObject()) continue;
                var sub = compileBlockPredicate(el.getAsJsonObject(), contextId);
                if (sub != null) subs.add(sub);
            }
            return (player, pos) -> {
                for (var c : subs) {
                    boolean r = c.test(player, pos);
                    if (isAnd && !r) return false;
                    if (!isAnd && r) return true;
                }
                return isAnd;
            };
        }

        // offset — evaluate the nested condition at pos + (x, y, z). The Apoli
        // structural wrapper (Chaotic Chemist checks "basin two blocks below
        // the mixer": offset{y:-2, condition: block create:basin}).
        if (bareType.equals("offset")) {
            final int ox = condJson.has("x") ? condJson.get("x").getAsInt() : 0;
            final int oy = condJson.has("y") ? condJson.get("y").getAsInt() : 0;
            final int oz = condJson.has("z") ? condJson.get("z").getAsInt() : 0;
            var inner = (condJson.has("condition") && condJson.get("condition").isJsonObject())
                ? compileBlockPredicate(condJson.getAsJsonObject("condition"), contextId)
                : null;
            if (inner == null) {
                CompatWarningCollector.recordUnsupportedCondition(
                    "offset", contextId, "block_condition offset has no nested `condition` — matches all blocks");
                return (player, pos) -> true;
            }
            final var fInner = inner;
            return (player, pos) -> fInner.test(player, pos.offset(ox, oy, oz));
        }

        // block_state — one blockstate property, shared with the in_block
        // compiler so the matching rules stay identical while the two keep
        // their opposite fail directions. Origins++ Giant reads the bed's
        // `facing` to work out which half of it the player is lying on.
        if (bareType.equals("block_state")) {
            var statePred = com.cyberday1.neoorigins.compat.condition.ConditionParser
                .compileBlockStateProperty(condJson);
            if (statePred == null) {
                CompatWarningCollector.recordUnsupportedCondition(
                    type, contextId, "block_state names no `property` — matches ALL blocks");
                return (player, pos) -> true;
            }
            return (player, pos) -> statePred.test(player.level().getBlockState(pos));
        }

        // height — the checked block's own Y level.
        if (bareType.equals("height")) {
            var comparison = com.cyberday1.neoorigins.compat.condition.ComparisonType.fromString(
                condJson.has("comparison") ? condJson.get("comparison").getAsString() : ">=");
            double compareTo = condJson.has("compare_to") ? condJson.get("compare_to").getAsDouble() : 0.0;
            return (player, pos) -> comparison.test(pos.getY(), compareTo);
        }

        // adjacent — count the face neighbours matching `adjacent_condition`,
        // then compare (default >= 1). Origins++ Glacier refuses to sleep
        // unless at most two neighbouring blocks are snow or ice.
        if (bareType.equals("adjacent")) {
            var inner = condJson.has("adjacent_condition") && condJson.get("adjacent_condition").isJsonObject()
                ? compileBlockPredicate(condJson.getAsJsonObject("adjacent_condition"), contextId)
                : null;
            if (inner == null) {
                CompatWarningCollector.recordUnsupportedCondition(
                    type, contextId, "adjacent has no nested `adjacent_condition` — matches ALL blocks");
                return (player, pos) -> true;
            }
            var comparison = com.cyberday1.neoorigins.compat.condition.ComparisonType.fromString(
                condJson.has("comparison") ? condJson.get("comparison").getAsString() : ">=");
            double compareTo = condJson.has("compare_to") ? condJson.get("compare_to").getAsDouble() : 1.0;
            final var fInner = inner;
            return (player, pos) -> {
                int count = 0;
                for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
                    if (fInner.test(player, pos.relative(face))) count++;
                }
                return comparison.test(count, compareTo);
            };
        }

        String blockId = condJson.has("block") ? condJson.get("block").getAsString() : null;
        if (blockId == null) blockId = condJson.has("id") ? condJson.get("id").getAsString() : null;
        if (blockId != null) {
            Identifier bid = Identifier.parse(blockId);
            return (player, pos) -> {
                var block = player.level().getBlockState(pos).getBlock();
                return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
            };
        }

        String tag = condJson.has("tag") ? condJson.get("tag").getAsString() : null;
        if (tag != null) {
            var tagKey = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.BLOCK, Identifier.parse(tag));
            return (player, pos) -> player.level().getBlockState(pos).is(tagKey);
        }

        // Unsupported leaf — fail OPEN (match all) so legacy powers keep firing,
        // but say so out loud: deduplicated into the reload summary during a
        // compat session, one-line WARN otherwise. Was a silent match-all.
        CompatWarningCollector.recordUnsupportedCondition(
            type.isEmpty() ? "(untyped block_condition)" : type, contextId,
            "unsupported block_condition leaf — matches ALL blocks");
        return (player, pos) -> true;
    }

    private CompatPower.Config parseModifyJump(Identifier id, JsonObject json) {
        // Extract the jump modifier value
        double value = 0.0;
        if (json.has("modifier") && json.get("modifier").isJsonObject()) {
            JsonObject mod = json.getAsJsonObject("modifier");
            value = mod.has("value") ? mod.get("value").getAsDouble()
                  : mod.has("amount") ? mod.get("amount").getAsDouble() : 0.0;
        }

        // Cache attribute holder at parse time — registry is static
        var jumpHolder = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse("minecraft:jump_strength")).orElse(null);
        if (jumpHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: 'minecraft:jump_strength' attribute not found — modify_jump will no-op", id);
            return null;
        }

        String safeKey = id.getPath().replace('/', '_');
        Identifier modifierId = Identifier.fromNamespaceAndPath("neoorigins", "modjump_" + safeKey);
        double finalValue = value;
        String idStr = id.toString();

        // Parse optional condition gate — if present, modifier is applied/removed
        // each tick based on condition state (e.g. only while sneaking).
        EntityCondition condition = json.has("condition")
            ? parseConditionField(json, "condition", idStr)
            : null;

        // Parse optional entity_action to fire on jump. Previously this was
        // parsed and stored in a local but never invoked — the jump-velocity
        // boost (and any other configured action) silently no-op'd. Now
        // registered with JumpActionRegistry on grant and unregistered on
        // revoke; JumpEventHandler fires it from LivingJumpEvent.
        EntityAction jumpAction = parseActionField(json, "entity_action", idStr);
        boolean hasJumpAction = jumpAction != EntityAction.NOOP;

        if (condition != null) {
            // Conditioned: toggle modifier based on condition each tick
            return CompatPower.Config.builder()
                .onTick(player -> {
                    AttributeInstance inst = player.getAttribute(jumpHolder);
                    if (inst == null) return;
                    if (condition.test(player)) {
                        if (inst.getModifier(modifierId) == null) {
                            inst.addTransientModifier(new AttributeModifier(
                                modifierId, finalValue, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                        }
                    } else {
                        inst.removeModifier(modifierId);
                    }
                })
                .onGranted(player -> {
                    if (hasJumpAction) com.cyberday1.neoorigins.service.JumpActionRegistry
                        .register(player, idStr, jumpAction, condition);
                })
                .onRevoked(player -> {
                    AttributeInstance inst = player.getAttribute(jumpHolder);
                    if (inst != null) inst.removeModifier(modifierId);
                    if (hasJumpAction) com.cyberday1.neoorigins.service.JumpActionRegistry
                        .unregister(player, idStr);
                })
                .build();
        }

        // Unconditional: apply on grant, remove on revoke
        return CompatPower.Config.builder()
            .onGranted(player -> {
                AttributeInstance inst = player.getAttribute(jumpHolder);
                if (inst != null && inst.getModifier(modifierId) == null) {
                    inst.addPermanentModifier(new AttributeModifier(
                        modifierId, finalValue, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }
                if (hasJumpAction) com.cyberday1.neoorigins.service.JumpActionRegistry
                    .register(player, idStr, jumpAction, null);
            })
            .onRevoked(player -> {
                AttributeInstance inst = player.getAttribute(jumpHolder);
                if (inst != null) inst.removeModifier(modifierId);
                if (hasJumpAction) com.cyberday1.neoorigins.service.JumpActionRegistry
                    .unregister(player, idStr);
            })
            .build();
    }

    // ── Phase 8: Origins++ compat parsers ─────────────────────────────────

    /**
     * {@code origins:conditioned_restrict_armor} — same as restrict_armor
     * but with a condition gate. Registers/unregisters the armor restriction
     * based on condition state each tick.
     */
    private CompatPower.Config parseConditionedRestrictArmor(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = parseConditionField(json, "condition", idStr);

        // Build the same slot/item predicates as restrict_armor
        var data = buildRestrictArmorData(idStr, json);
        if (data == null) return null;

        // Track active state per-player to edge-trigger register/unregister
        Map<UUID, Boolean> activeState = new java.util.concurrent.ConcurrentHashMap<>();

        return CompatPower.Config.builder()
            .onTick(player -> {
                boolean shouldBeActive = condition.test(player);
                boolean wasActive = activeState.getOrDefault(player.getUUID(), false);
                if (shouldBeActive && !wasActive) {
                    CompatPlayerState.register(player, data);
                    activeState.put(player.getUUID(), true);
                } else if (!shouldBeActive && wasActive) {
                    CompatPlayerState.unregister(player, data);
                    activeState.put(player.getUUID(), false);
                }
            })
            .onRevoked(player -> {
                if (activeState.remove(player.getUUID()) == Boolean.TRUE) {
                    CompatPlayerState.unregister(player, data);
                }
            })
            .build();
    }

    /** Shared helper to build EventPowerData for restrict_armor without registering it. */
    private CompatPlayerState.EventPowerData buildRestrictArmorData(String idStr, JsonObject json) {
        // Compile per-slot predicates (same logic as parseRestrictArmor)
        java.util.function.Predicate<ItemStack> globalPred = json.has("item_condition")
            ? compileItemPredicate(json.getAsJsonObject("item_condition")) : null;
        java.util.function.Predicate<ItemStack> headPred = json.has("head")
            ? compileItemPredicate(json.getAsJsonObject("head")) : null;
        java.util.function.Predicate<ItemStack> chestPred = json.has("chest")
            ? compileItemPredicate(json.getAsJsonObject("chest")) : null;
        java.util.function.Predicate<ItemStack> legsPred = json.has("legs")
            ? compileItemPredicate(json.getAsJsonObject("legs")) : null;
        java.util.function.Predicate<ItemStack> feetPred = json.has("feet")
            ? compileItemPredicate(json.getAsJsonObject("feet")) : null;

        CompatPlayerState.ArmorPredicate armorPred = (stack, slot) -> {
            var slotPred = switch (slot) {
                case HEAD  -> headPred;
                case CHEST -> chestPred;
                case LEGS  -> legsPred;
                case FEET  -> feetPred;
                default    -> null;
            };
            if (slotPred != null) return slotPred.test(stack);
            if (globalPred != null) return globalPred.test(stack);
            return true; // No condition = restrict all armor
        };

        return CompatPlayerState.EventPowerData.withArmorPredicate(idStr, armorPred);
    }

    /**
     * {@code origins:modify_harvest} — allows the player to harvest blocks
     * matching a block_condition even without the correct tool. Used by
     * Origins++ for "Strong Arms" (mine stone without pickaxe).
     */
    private CompatPower.Config parseModifyHarvest(Identifier id, JsonObject json) {
        boolean allow = !json.has("allow") || json.get("allow").getAsBoolean();
        if (!allow) {
            // "allow: false" (disabling harvest) is rare and not yet supported
            NeoOrigins.LOGGER.debug("[CompatB] {}: modify_harvest with allow=false is not supported, skipping", id);
            return null;
        }

        // Extract block tag from block_condition
        net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> blockTag = null;
        if (json.has("block_condition") && json.get("block_condition").isJsonObject()) {
            JsonObject bc = json.getAsJsonObject("block_condition");
            String bcType = bc.has("type") ? bc.get("type").getAsString() : "";
            String tag = bc.has("tag") ? bc.get("tag").getAsString() : null;
            if (tag != null && (bcType.contains("in_tag") || bcType.contains("block"))) {
                if (tag.startsWith("#")) tag = tag.substring(1);
                blockTag = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.BLOCK,
                    Identifier.parse(tag));
            }
        }

        if (blockTag == null) {
            NeoOrigins.LOGGER.debug("[CompatB] {}: modify_harvest has no block tag — allowing all blocks", id);
        }

        final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> finalTag = blockTag;

        return CompatPower.Config.builder()
            .onGranted(player -> {
                if (finalTag != null) {
                    CompatEventPowers.registerHarvestTag(player, finalTag);
                }
            })
            .onRevoked(player -> {
                if (finalTag != null) {
                    CompatEventPowers.unregisterHarvestTag(player, finalTag);
                }
            })
            .build();
    }

    /**
     * {@code origins:recipe} — unlocks a recipe for the player when the
     * power is granted. The {@code recipe} field may be either:
     * <ul>
     *   <li>A string id pointing to an existing registered recipe — the
     *       original behavior, the power just calls {@code awardRecipes}.</li>
     *   <li>An inline recipe JSON object (full {@code type} + {@code ingredients}
     *       + {@code result} shape). The inline recipe is registered via
     *       {@link com.cyberday1.neoorigins.service.InlineRecipeRegistry}
     *       under a synthesized id, then the power gates {@code awardRecipes}
     *       on that id. The injected recipe is wrapped in an
     *       {@code OriginGatedRecipe(has_power)} so only holders of this power
     *       can craft it (recipe-book visibility plus the craft-time gate).</li>
     * </ul>
     */
    private CompatPower.Config parseRecipe(Identifier id, JsonObject json) {
        if (!json.has("recipe")) {
            NeoOrigins.LOGGER.debug("[CompatB] {}: origins:recipe missing 'recipe' field", id);
            return null;
        }
        JsonElement recipeEl = json.get("recipe");
        final Identifier recipeLoc;
        if (recipeEl.isJsonPrimitive()) {
            recipeLoc = Identifier.tryParse(recipeEl.getAsString());
            if (recipeLoc == null) {
                NeoOrigins.LOGGER.warn("[CompatB] {}: origins:recipe has malformed recipe id '{}'",
                    id, recipeEl.getAsString());
                return null;
            }
            // Gate the referenced recipe so only holders of this power can craft
            // it. Wraps the live recipe in an OriginGatedRecipe(has_power) after
            // the reload completes (via InlineRecipeRegistry + the 26.1
            // RecipeManager replace mixin).
            com.cyberday1.neoorigins.service.InlineRecipeRegistry.registerRefGate(recipeLoc, id);
        } else if (recipeEl.isJsonObject()) {
            // Inline recipe: register under a synthesized id and treat as
            // if the pack had shipped a separate recipe data file pointed to
            // by that id. InlineRecipeRegistry handles the actual injection
            // into RecipeManager once the datapack reload completes, wrapping
            // it in an OriginGatedRecipe(has_power) on both branches.
            recipeLoc = com.cyberday1.neoorigins.service.InlineRecipeRegistry.syntheticId(id);
            com.cyberday1.neoorigins.service.InlineRecipeRegistry.register(recipeLoc, recipeEl.getAsJsonObject());
            com.cyberday1.neoorigins.service.InlineRecipeRegistry.registerInlinePower(recipeLoc, id);
        } else {
            NeoOrigins.LOGGER.warn("[CompatB] {}: origins:recipe 'recipe' field must be string id or inline object — got {}",
                id, recipeEl);
            return null;
        }

        return CompatPower.Config.builder()
            .onGranted(player -> {
                var server = player.level().getServer();
                if (server == null) return;
                var recipeManager = server.getRecipeManager();
                var recipe = recipeManager.byKey(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, recipeLoc));
                if (recipe.isPresent()) {
                    player.awardRecipes(java.util.List.of(recipe.get()));
                }
                // If recipe is empty (inline recipe injection hasn't run yet on
                // this server start), the OnDatapackSyncEvent path will inject
                // it shortly. Re-grant on next login or via /reload picks it up.
            })
            .onRevoked(player -> {
                var server = player.level().getServer();
                if (server == null) return;
                var recipeManager = server.getRecipeManager();
                var recipe = recipeManager.byKey(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, recipeLoc));
                if (recipe.isPresent()) {
                    player.resetRecipes(java.util.List.of(recipe.get()));
                }
            })
            .build();
    }

    private CompatPower.Config parsePreventGameEvent(Identifier id, JsonObject json) {
        String eventId = json.has("event") ? json.get("event").getAsString() : null;
        if (eventId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: prevent_game_event missing 'event' field", id);
            return null;
        }
        Identifier eventLoc = Identifier.parse(eventId);
        return CompatPower.Config.builder()
            .onGranted(player -> CompatEventPowers.registerBlockedGameEvent(player, eventLoc))
            .onRevoked(player -> CompatEventPowers.unregisterBlockedGameEvent(player, eventLoc))
            .build();
    }

    /**
     * {@code origins:freeze} — applies freeze ticks to the player each tick,
     * making them visually frozen and applying freeze damage. Same visual as
     * powder snow but permanent while the power is active.
     */
    private CompatPower.Config parseFreeze(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = parseConditionField(json, "condition", idStr);

        return CompatPower.Config.builder()
            .onTick(player -> {
                if (condition.test(player)) {
                    // Keep freeze ticks at full so the player stays frozen
                    int required = player.getTicksRequiredToFreeze();
                    if (player.getTicksFrozen() < required + 2) {
                        player.setTicksFrozen(required + 2);
                    }
                }
            })
            .onRevoked(player -> player.setTicksFrozen(0))
            .build();
    }

    /**
     * Shared parser for {@code modify_lava_speed} and {@code modify_xp_gain}
     * — both shape-identical Apoli verbs of the form
     * {@code { "modifier": { "operation": ..., "value": ... } }}.
     *
     * <p>Accepts all four Apoli-author conventions, matching the precedent
     * set by {@link #parseModifyFood} and {@link #parseConditionedModifyDamageTaken}:
     * <ul>
     *   <li>singular {@code "modifier"} object or plural {@code "modifiers"} array</li>
     *   <li>per-modifier value field {@code "value"} or {@code "amount"}</li>
     * </ul>
     * The schema-vs-parser drift here previously caused real Apoli packs (which
     * commonly emit {@code amount} and {@code modifiers}) to silently no-op.
     *
     * <p>Registers the resolved numeric entries against the player's UUID so
     * the consumer (mixin for lava-speed, event handler for xp-gain) can
     * apply them.
     */
    private CompatPower.Config parseNumericModifier(Identifier id, JsonObject json,
                                                     NumericModifierRegistry.Kind kind) {
        String idStr = id.toString();
        // parseModifierList accepts both singular "modifier" and plural "modifiers",
        // and parseSingleModifier accepts both "value" and "amount" inside each entry.
        java.util.List<OriginsModifierMath.Modifier> mods = parseModifierList(json, "modifier");
        if (mods.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] {} '{}' missing modifier/modifiers — skipped", kind, id);
            return null;
        }
        return CompatPower.Config.builder()
            .onGranted(player -> NumericModifierRegistry.register(player, kind, idStr, mods))
            .onRevoked(player -> NumericModifierRegistry.unregister(player, kind, idStr))
            .build();
    }

    private CompatPower.Config parseModifyCrafting(Identifier id, JsonObject json) {
        String idStr = id.toString();
        if (!json.has("recipe") || !json.has("result")) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_crafting '{}' missing 'recipe' or 'result' — skipped", id);
            return null;
        }
        Identifier recipeId = Identifier.tryParse(json.get("recipe").getAsString());
        if (recipeId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_crafting '{}' has malformed recipe id — skipped", id);
            return null;
        }
        JsonObject result = json.getAsJsonObject("result");
        String resultItemStr = result.has("item") ? result.get("item").getAsString() : null;
        if (resultItemStr == null) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_crafting '{}' result missing 'item' — skipped", id);
            return null;
        }
        Identifier resultItem = Identifier.tryParse(resultItemStr);
        if (resultItem == null) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_crafting '{}' result item '{}' is malformed — skipped", id, resultItemStr);
            return null;
        }
        int resultCount = result.has("amount") ? result.get("amount").getAsInt()
                        : result.has("count")  ? result.get("count").getAsInt()
                        : 1;
        String resultTag = result.has("tag") ? result.get("tag").getAsString() : "";

        var entry = new ModifyCraftingRegistry.Entry(idStr, recipeId, resultItem, resultCount, resultTag);
        return CompatPower.Config.builder()
            .onGranted(player -> ModifyCraftingRegistry.register(player, entry))
            .onRevoked(player -> ModifyCraftingRegistry.unregister(player, idStr))
            .build();
    }

    private CompatPower.Config parsePreventSprinting(Identifier id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        return CompatPower.Config.builder()
            .onTick(player -> {
                if (!player.isSprinting()) return;
                if (condition.test(player)) player.setSprinting(false);
            })
            .build();
    }
}
