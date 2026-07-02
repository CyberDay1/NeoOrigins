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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.SmallFireball;
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
public class OriginsCompatPowerLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {

    public static final OriginsCompatPowerLoader INSTANCE = new OriginsCompatPowerLoader();

    /** Edge-detection tracker for Route B resource min/max actions. */
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

    /** Power types that Route B handles (Route A SKIPs these). */
    private static final Set<String> ROUTE_B_TYPES = Set.of(
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
        "origins:cooldown",             "apace:cooldown"
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
    public static boolean compilesForTest(ResourceLocation id, String type, JsonObject json) {
        return INSTANCE.parseRouteB(id, type, json) != null;
    }

    private static final FileToIdConverter FILE_CONVERTER  = FileToIdConverter.json("origins/powers");
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("powers");

    // ---- SimplePreparableReloadListener ----

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        scanConverter(FILE_CONVERTER,  rm, map);
        scanConverter(COMPAT_CONVERTER, rm, map);
        return map;
    }

    private void scanConverter(FileToIdConverter converter, ResourceManager rm,
                                Map<ResourceLocation, JsonElement> map) {
        for (var entry : converter.listMatchingResources(rm).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation id     = converter.fileToId(fileId);
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
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager rm, ProfilerFiller profiler) {
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
        com.cyberday1.neoorigins.service.InlineRecipeRegistry.resetPending();
        com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.clear();

        // Rewrite apoli:/apugli: power types to the canonical origins: namespace
        // before expansion + dispatch, so packs that use the Apoli namespace are
        // recognized by ROUTE_B_TYPES (and apoli:multiple is expanded).
        for (JsonElement el : data.values()) {
            if (el.isJsonObject()) OriginsFormatDetector.canonicalizePowerType(el.getAsJsonObject());
        }

        // Inline-expand any origins:multiple entries so sub-power JSONs are accessible.
        Map<ResourceLocation, JsonObject> expanded = inlineExpand(data);

        Map<ResourceLocation, PowerHolder<?>> injected = new HashMap<>();
        // Track new synthetic IDs to add to MULTIPLE_EXPANSION_MAP
        Map<ResourceLocation, List<ResourceLocation>> newExpansions = new HashMap<>();

        for (var entry : expanded.entrySet()) {
            ResourceLocation id   = entry.getKey();
            JsonObject json = entry.getValue();
            // Canonicalize again to cover synthetic sub-powers emitted by
            // multiple-expansion (their nested types never pass the pre-loop).
            String type = OriginsFormatDetector.canonicalizePowerType(json);

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
                // Recover the parent from the authoritative parentage recorded
                // during expansion — the synthetic id joins parent + "_" + subkey
                // (Apoli convention), so the parent is not recoverable from the
                // id string alone.
                ResourceLocation parentId = syntheticParentage.get(id);
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
            List<ResourceLocation> existing = OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP
                .getOrDefault(entry.getKey(), List.of());
            List<ResourceLocation> merged = new ArrayList<>(existing);
            for (ResourceLocation newId : entry.getValue()) {
                if (!merged.contains(newId)) merged.add(newId);
            }
            OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.put(entry.getKey(),
                Collections.unmodifiableList(merged));
        }

        // Inject synthetic powers for well-known Origins built-in power IDs.
        // These are referenced by addon packs as bare IDs (e.g. "origins:elytra")
        // with no JSON file — the original Origins mod hardcodes them.
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
    private void registerNativeActiveHotkeys() {
        for (var entry : PowerDataManager.INSTANCE.getPowers().entrySet()) {
            ResourceLocation id = entry.getKey();
            PowerHolder<?> holder = entry.getValue();
            if (!holder.isActive()) continue;
            com.google.gson.JsonObject raw = PowerDataManager.INSTANCE.getRawPowerJson(id);
            if (raw == null || !raw.has("key")) continue;
            KeySpec ks = classifyKey(raw, null);
            if (ks.key() == null || ks.slotKey()) continue;
            final ResourceLocation pid = id;
            if (ks.vanillaInputKey()) {
                // Native active power bound to a vanilla input key (e.g. key.jump for
                // a double-jump). Polled each tick by PowerKeybindRegistry from the
                // server-side input state; registerVanilla records the key tag so the
                // origin info screen shows e.g. "[Jump]" instead of a tag-less passive.
                com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.registerVanillaNative(pid, ks.key(), ks.continuous());
                com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.registerVanilla(pid, ks.key());
                NeoOrigins.LOGGER.debug("[CompatB] native active {} bound to vanilla key '{}'", pid, ks.key());
                continue;
            }
            EntityAction openAction = player -> {
                PowerHolder<?> h = PowerDataManager.INSTANCE.getPower(pid);
                if (h != null && h.isActive()) h.onActivated(player);
            };
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.register(ks.key(),
                new com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.Binding(
                    pid, openAction, null, 0, ks.continuous(), null));
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.markNativeHotkeyPower(pid);
            NeoOrigins.LOGGER.debug("[CompatB] native active {} bound to named hotkey '{}'", pid, ks.key());
        }
    }

    /**
     * Inline-expand origins:multiple entries in the raw data map.
     * Returns a flat map of id -> JsonObject covering both direct powers and sub-powers.
     * Does NOT call OriginsMultipleExpander (avoids touching its state twice).
     */
    /**
     * Registers synthetic PowerHolders for well-known Origins built-in power IDs
     * that addon packs reference by ID without providing a JSON file.
     */
    private void injectWellKnownPowers(Map<ResourceLocation, PowerHolder<?>> injected) {
        // Map of origins:id -> NeoOrigins JSON equivalent
        Map<String, java.util.function.Supplier<com.google.gson.JsonObject>> WELL_KNOWN = Map.ofEntries(
            Map.entry("origins:elytra",              () -> json("neoorigins:natural_glide")),
            Map.entry("origins:fire_immunity",       () -> json("neoorigins:prevent_action", "action", "fire")),
            Map.entry("origins:fresh_air",           () -> freshAirJson()),
            Map.entry("origins:like_water",          () -> json("neoorigins:ignore_water")),
            Map.entry("origins:aquatic",             () -> json("neoorigins:dries_out")),
            Map.entry("origins:water_vision",        () -> json("neoorigins:lava_vision")),
            Map.entry("origins:aqua_affinity",       () -> json("neoorigins:underwater_mining_speed")),
            Map.entry("origins:conduit_power_on_land", () -> json("neoorigins:conduit_power")),
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
            // origins:phasing is Apoli's noclip-through-walls (gravity intact,
            // sneak-to-phase-down) — it does NOT grant flight. phantom_form
            // (origins:phantomize, line above) DOES arm creative flight, so it
            // was the wrong target: it let phasing players jump-fly. wraith_phase
            // is the velocity-driven, flight-free noclip purpose-built for this.
            Map.entry("origins:phasing",              () -> json("neoorigins:wraith_phase")),
            Map.entry("origins:burn_in_daylight",     () -> json("neoorigins:condition_passive")),
            Map.entry("origins:damage_from_potions",  () -> json("neoorigins:effect_immunity")),
            Map.entry("origins:more_kinetic_damage",  () -> json("neoorigins:attribute_modifier", "attribute", "minecraft:generic.safe_fall_distance", "amount", -2.0, "operation", "add_value")),
            Map.entry("origins:throw_ender_pearl",    () -> json("neoorigins:active_ability")),
            Map.entry("origins:pumpkin_hate",         () -> json("neoorigins:restrict_armor", "armor_class", "pumpkin")),
            Map.entry("origins:hotblooded",           () -> json("neoorigins:effect_immunity")),
            Map.entry("origins:water_vulnerability",  () -> json("neoorigins:condition_passive")),
            Map.entry("origins:flame_particles",      () -> json("neoorigins:particle", "particle", "minecraft:flame")),
            Map.entry("origins:nether_spawn",         () -> json("neoorigins:spawn_location"))
        );

        for (var entry : WELL_KNOWN.entrySet()) {
            ResourceLocation id = ResourceLocation.parse(entry.getKey());
            // Skip if already loaded via JSON or Route B
            if (PowerDataManager.INSTANCE.hasPower(id) || injected.containsKey(id)) continue;
            try {
                com.google.gson.JsonObject powerJson = entry.getValue().get();
                String typeStr = powerJson.get("type").getAsString();
                ResourceLocation typeId = ResourceLocation.parse(typeStr);
                com.cyberday1.neoorigins.api.power.PowerType<?> powerType =
                    com.cyberday1.neoorigins.power.registry.PowerTypes.get(typeId);
                if (powerType != null) {
                    // Route A — parse via native codec
                    injectViaNativeCodec(id, powerType, powerJson, injected);
                    NeoOrigins.LOGGER.debug("[CompatSynth] Registered well-known power {} -> {}", id, typeStr);
                } else {
                    // Fall back to Route B parsing
                    CompatPower.Config config = parseRouteB(id, typeStr, powerJson);
                    if (config != null) {
                        injected.put(id, new PowerHolder<>(id, CompatPower.INSTANCE, config, net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty()));
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
            ResourceLocation id, com.cyberday1.neoorigins.api.power.PowerType<C> type,
            com.google.gson.JsonObject json, Map<ResourceLocation, PowerHolder<?>> target) {
        com.google.gson.JsonObject configJson = json.deepCopy();
        configJson.addProperty("_power_id", id.toString());
        type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, configJson)
            .resultOrPartial(err -> NeoOrigins.LOGGER.warn("[CompatSynth] codec error for {}: {}", id, err))
            .ifPresent(config -> target.put(id, new PowerHolder<>(id, type, config, net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty())));
    }

    private static com.google.gson.JsonObject json(String type) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("type", type);
        return o;
    }

    private static com.google.gson.JsonObject json(String type, String k1, String v1) {
        com.google.gson.JsonObject o = json(type);
        o.addProperty(k1, v1);
        return o;
    }

    private static com.google.gson.JsonObject json(String type, String k1, String v1, String k2, String v2) {
        com.google.gson.JsonObject o = json(type);
        o.addProperty(k1, v1); o.addProperty(k2, v2);
        return o;
    }

    private static com.google.gson.JsonObject json(String type, String k1, double v1) {
        com.google.gson.JsonObject o = json(type);
        o.addProperty(k1, v1);
        return o;
    }

    private static com.google.gson.JsonObject json(String type, String k1, double v1, String k2, double v2, String k3, double v3, String k4, double v4) {
        com.google.gson.JsonObject o = json(type);
        o.addProperty(k1, v1); o.addProperty(k2, v2);
        o.addProperty(k3, v3); o.addProperty(k4, v4);
        return o;
    }

    private static com.google.gson.JsonObject json(String type, String k1, String v1, String k2, double v2, String k3, String v3) {
        com.google.gson.JsonObject o = json(type);
        o.addProperty(k1, v1); o.addProperty(k2, v2); o.addProperty(k3, v3);
        return o;
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

    /**
     * Child synthetic id -> immediate parent id, recorded during {@link #inlineExpand}
     * so synthetic sub-powers can be tied back to their parent without parsing the id
     * string. The synthetic id now joins parent + "_" + subkey (Apoli convention), so
     * the separator is ambiguous and the parent can no longer be recovered by splitting.
     */
    private final Map<ResourceLocation, ResourceLocation> syntheticParentage = new HashMap<>();

    /**
     * Canonical synthetic id -> its pre-2.2.8 slash-form id, recorded during
     * {@link #expandMultiple} so a nested sub-power can chain its parent's legacy
     * form. Feeds {@link CompatAttachments#registerLegacySyntheticId} so datapacks
     * that still reference bars/toggles by the old "parent/subkey" id keep working.
     */
    private final Map<ResourceLocation, String> syntheticLegacyIds = new HashMap<>();

    private Map<ResourceLocation, JsonObject> inlineExpand(Map<ResourceLocation, JsonElement> data) {
        syntheticParentage.clear();
        syntheticLegacyIds.clear();
        CompatAttachments.clearLegacySyntheticIds();
        Map<ResourceLocation, JsonObject> result = new HashMap<>();
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

    private void expandMultiple(ResourceLocation parentId, JsonObject json, Map<ResourceLocation, JsonObject> out) {
        expandMultiple(parentId, json, out, readHiddenFlag(json));
    }

    private void expandMultiple(ResourceLocation parentId, JsonObject json,
                                Map<ResourceLocation, JsonObject> out, boolean parentHidden) {
        for (var subEntry : json.entrySet()) {
            if (MULTIPLE_META_KEYS.contains(subEntry.getKey())) continue;
            if (!subEntry.getValue().isJsonObject()) continue;
            JsonObject subJson = subEntry.getValue().getAsJsonObject();
            ResourceLocation syntheticId = ResourceLocation.fromNamespaceAndPath(
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
    private static JsonObject resolveSelfReferences(JsonObject json, ResourceLocation parentId) {
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

    private CompatPower.Config parseRouteB(ResourceLocation id, String type, JsonObject json) {
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
            default -> null;
        };
    }

    private static boolean isModifyDamageTakenType(String type) {
        return "origins:modify_damage_taken".equals(type) || "apace:modify_damage_taken".equals(type)
            || "origins:modify_damage_dealt".equals(type) || "apace:modify_damage_dealt".equals(type);
    }

    private CompatPower.Config parseConditionedModifyDamageTaken(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();

        // Extract the multiplier from the Origins modifier(s). All operations
        // collapse to (1 + value) — same lossy mapping as Route A's translateModifyDamage.
        // parseModifierList accepts both singular "modifier" and plural "modifiers";
        // parseSingleModifier accepts both "value" and "amount" per entry. Mirrors
        // the precedent set by parseModifyFood / parseNumericModifier so real
        // Apoli packs (which commonly emit `modifiers`/`amount`) don't silently no-op.
        DamageMath dmgMath = collapseDamageMath(parseModifierList(json, "modifier"));

        // Optional damage type filter — msgId-based, mirrors native ModifyDamagePower.
        String damageTypeFilter = null;
        ResourceLocation damageTypeKeyFilter = null;
        DamageAmountFilter amountFilter = null;
        if (json.has("damage_condition") && json.get("damage_condition").isJsonObject()) {
            JsonObject dc = json.getAsJsonObject("damage_condition");
            String dcType = dc.has("type") ? dc.get("type").getAsString() : "";
            if (("origins:name".equals(dcType) || "apace:name".equals(dcType)) && dc.has("name")) {
                damageTypeFilter = dc.get("name").getAsString();
            } else if (("origins:type".equals(dcType) || "apace:type".equals(dcType)) && dc.has("damage_type")) {
                damageTypeKeyFilter = ResourceLocation.parse(dc.get("damage_type").getAsString());
            } else if ("origins:amount".equals(dcType) || "apace:amount".equals(dcType)) {
                amountFilter = parseDamageAmountFilter(dc);
            }
        }

        EntityCondition condition = parseConditionField(json, "condition", idStr);

        final DamageMath finalMath    = dmgMath;
        final String finalDmgFilter  = damageTypeFilter;
        final ResourceLocation finalDmgTypeKey = damageTypeKeyFilter;
        final DamageAmountFilter finalAmountFilter = amountFilter;

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
                    if (typeKey == null || !typeKey.location().equals(finalDmgTypeKey)) return;
                }
                // origins:amount damage_condition — gate on the incoming damage value.
                if (finalAmountFilter != null && !finalAmountFilter.test(event.getAmount())) return;
                // Overflow-safe multiply + Apoli total-clamp (set/max/min_total),
                // identical math to native ModifyDamagePower.Config.apply.
                float scaled = finalMath.apply(event.getAmount());
                event.setAmount(scaled);
                // A 0-result effectively cancels the hit; callers commonly rely on that.
                if (scaled <= 0.0f) event.setCanceled(true);
            })
            .build();
    }

    private CompatPower.Config parseConditionedModifyDamageDealt(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();

        // See parseConditionedModifyDamageTaken — same singular/plural and value/amount
        // tolerance for symmetry with Apoli.
        DamageMath dmgMath = collapseDamageMath(parseModifierList(json, "modifier"));

        String damageTypeFilter = null;
        ResourceLocation damageTypeKeyFilter = null;
        DamageAmountFilter amountFilter = null;
        if (json.has("damage_condition") && json.get("damage_condition").isJsonObject()) {
            JsonObject dc = json.getAsJsonObject("damage_condition");
            String dcType = dc.has("type") ? dc.get("type").getAsString() : "";
            if (("origins:name".equals(dcType) || "apace:name".equals(dcType)) && dc.has("name")) {
                damageTypeFilter = dc.get("name").getAsString();
            } else if (("origins:type".equals(dcType) || "apace:type".equals(dcType)) && dc.has("damage_type")) {
                damageTypeKeyFilter = ResourceLocation.parse(dc.get("damage_type").getAsString());
            } else if ("origins:amount".equals(dcType) || "apace:amount".equals(dcType)) {
                amountFilter = parseDamageAmountFilter(dc);
            }
        }

        EntityCondition condition = parseConditionField(json, "condition", idStr);

        final DamageMath finalMath = dmgMath;
        final String finalDmgFilter = damageTypeFilter;
        final ResourceLocation finalDmgTypeKey = damageTypeKeyFilter;
        final DamageAmountFilter finalAmountFilter = amountFilter;

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
                    if (typeKey == null || !typeKey.location().equals(finalDmgTypeKey)) return;
                }
                // origins:amount damage_condition — gate on the damage value itself
                // (the most recent damage being dealt to the target).
                if (finalAmountFilter != null && !finalAmountFilter.test(event.getAmount())) return;
                float scaled = finalMath.apply(event.getAmount());
                event.setAmount(scaled);
                if (scaled <= 0.0f) event.setCanceled(true);
            })
            .build();
    }

    /**
     * An {@code origins:amount} damage_condition: a comparison + threshold tested
     * against the damage value of the current hit. Mirrors Apoli's
     * {@code AmountCondition} (DamageCondition registry), where {@code comparison}
     * is one of {@code <,<=,==,!=,>=,>} and {@code compare_to} is the threshold.
     */
    private record DamageAmountFilter(com.cyberday1.neoorigins.compat.condition.ComparisonType comparison,
                                      double compareTo) {
        boolean test(float amount) { return comparison.test(amount, compareTo); }
    }

    /** Parse an {@code origins:amount} damage_condition object into a {@link DamageAmountFilter}. */
    private DamageAmountFilter parseDamageAmountFilter(JsonObject dc) {
        String comp = dc.has("comparison") ? dc.get("comparison").getAsString() : ">=";
        double compareTo = dc.has("compare_to") ? dc.get("compare_to").getAsDouble() : 0.0;
        return new DamageAmountFilter(
            com.cyberday1.neoorigins.compat.condition.ComparisonType.fromString(comp), compareTo);
    }

    /**
     * Classified activation key for an Apoli "active" power (active_self, toggle, launch).
     * A power's {@code "key"} is either a string or {@code {"key": ..., "continuous": ...}}.
     * The key then falls into one of three buckets:
     *   - slotKey: one of the 6 hardcoded skill slots (primary/secondary active, toolbar, pick)
     *   - vanillaInputKey: a movement/use/attack key the server can poll directly
     *   - namedHotkey: a pack-declared translation key (must be registered into PowerKeybindRegistry)
     */
    private record KeySpec(String key, boolean continuous, boolean slotKey,
                           boolean vanillaInputKey, boolean toolbarKey) {
        boolean namedHotkey() { return !slotKey && !vanillaInputKey; }
    }

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

    private static KeySpec classifyKey(JsonObject json, String defaultKey) {
        String key = defaultKey;
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
        if (key == null) return new KeySpec(null, continuous, false, false, false);
        // The two toolbar (creative-hotbar save/load) keys are a special case.
        // Apoli packs bind MANY condition-gated active_self powers to the same
        // toolbar key (the Seer progression rituals are six powers all on
        // saveToolbarActivator), expecting every one to be evaluated on each
        // press — exactly the multi-binding fan-out the named-hotkey dispatch
        // path provides. The skill-slot model can only host a single power per
        // slot AND the client never sends an activation for the vanilla toolbar
        // keys, so routing these to a skill slot silently drops them. Treat them
        // as a dedicated bucket that flows through PowerKeybindRegistry instead.
        boolean toolbarKey = key.contains("loadToolbarActivator")
            || key.contains("saveToolbarActivator");
        boolean slotKey = !toolbarKey && (key.contains("primary_active")
            || key.contains("secondary_active") || key.contains("pickItem"));
        boolean vanillaInputKey = switch (key) {
            case "key.sneak", "key.use", "key.attack", "key.jump", "key.sprint",
                 "key.forward", "key.back", "key.left", "key.right" -> true;
            default -> false;
        };
        return new KeySpec(key, continuous, slotKey, vanillaInputKey, toolbarKey);
    }

    private CompatPower.Config parseActiveSelf(ResourceLocation id, JsonObject json) {
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
        KeySpec ks = classifyKey(json, "key.origins.primary_active");
        String key = ks.key();
        boolean continuous = ks.continuous();

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
        boolean isSlotKey = ks.slotKey();
        // Continuous slot powers DON'T use onActivated — they need every-tick
        // execution which onActivated (single-fire per keypress) can't provide.
        if (isSlotKey && !continuous) {
            return withCooldownBar(CompatPower.Config.builder()
                .cooldownTicks(cooldown)
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

        // Non-slot keys come in two flavors:
        //   1. Vanilla input keys (sneak/use/attack/jump/movement) — polled from
        //      onTick because the server knows the input state directly.
        //   2. Pack-declared translation keys (e.g. "deanos_origins.key.origins.2")
        //      — registered into PowerKeybindRegistry and fired by client press
        //      via ActivatePowerByKeyPayload. The CompatPower itself becomes a
        //      no-op so it doesn't tick uselessly.
        final String finalKey = key;
        final String finalIdStr = idStr;
        final boolean isContinuous = continuous;

        boolean isVanillaInputKey = ks.vanillaInputKey();

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
                    case "key.sprint"  -> player.isSprinting();
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
                        // Defensive: isVanillaInputKey above already excludes
                        // anything that would land here, so this is unreachable
                        // unless someone adds a new vanilla key without updating
                        // both sites.
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
                    } else if (failAction != null) {
                        // Edge-detect the failed press so feedback fires once per
                        // press (or once per false→true condition flip mid-hold),
                        // not every tick the key is held.
                        PlayerOriginData data = player.getData(OriginAttachments.originData());
                        String failEdgeKey = idStr + ":failedge";
                        boolean wasFailingLastTick = data.getCustomFloat(failEdgeKey, 0) > 0;
                        data.setCustomFloat(failEdgeKey, pressed ? 1.0F : 0.0F);
                        if (pressed && !wasFailingLastTick) failAction.execute(player);
                    }
                } else {
                    // Edge detection: fire once on press
                    String edgeKey = idStr + ":keypress";
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    boolean wasPressedLastTick = data.getCustomFloat(edgeKey, 0) > 0;
                    data.setCustomFloat(edgeKey, pressed ? 1.0F : 0.0F);
                    if (pressed && !wasPressedLastTick) {
                        if (!condition.test(player)) {
                            if (failAction != null) failAction.execute(player);
                            return;
                        }
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
    private static String prettyLabel(ResourceLocation id) {
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
            base.hotkeyless());
    }

    private CompatPower.Config parseActionOverTime(ResourceLocation id, JsonObject json) {
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
        // The set of gamemodes this action would force on the player (e.g.
        // {ADVENTURE} for the seer pocket). Used at the rising edge so we never
        // "remember" a value the action itself just set — otherwise the restore
        // on the falling edge would push the player back into the forced mode
        // and fight the pack's own cleanup (the seer pack already runs
        // `gamemode survival` on projection; a remembered ADVENTURE would undo it).
        final java.util.Set<net.minecraft.world.level.GameType> forcedGameTypes =
            forcedGameTypes(json);
        boolean gateForcesGamemode =
            !hasFallingAction(json)
            && (json.has("condition") || json.has("entity_condition"))
            && !forcedGameTypes.isEmpty();
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
                        if (gateForcesGamemode) rememberGamemode(player, idStr, forcedGameTypes);
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

    /**
     * The set of {@link net.minecraft.world.level.GameType}s this action_over_time's
     * repeating {@code entity_action} would force on the player. Walks the (possibly
     * nested {@code and}/array) action tree, rewrites each {@code command} for legacy
     * verbs, and for every line that ultimately runs {@code gamemode <mode>} parses
     * the target mode. An empty set means the action forces no gamemode (the safety
     * net stays disengaged). Mirrors {@link #entityActionForcesGamemode}'s walk.
     */
    private static java.util.Set<net.minecraft.world.level.GameType> forcedGameTypes(JsonObject json) {
        java.util.EnumSet<net.minecraft.world.level.GameType> out =
            java.util.EnumSet.noneOf(net.minecraft.world.level.GameType.class);
        if (!json.has("entity_action") || !json.get("entity_action").isJsonObject()) return out;
        java.util.List<String> commands = new java.util.ArrayList<>();
        collectActionCommands(json.getAsJsonObject("entity_action"), commands, 0);
        for (String cmd : commands) {
            net.minecraft.world.level.GameType gt = forcedGameType(LegacyCommandRewriter.rewrite(cmd));
            if (gt != null) out.add(gt);
        }
        return out;
    }

    /**
     * Parse the target gamemode of a (rewritten) command line that runs
     * {@code gamemode <mode> ...} — directly or as the trailing {@code run gamemode}
     * of an {@code execute} chain. Returns null if the line doesn't run gamemode or
     * the mode token is unrecognised.
     */
    private static net.minecraft.world.level.GameType forcedGameType(String cmd) {
        if (cmd == null) return null;
        String c = cmd.trim();
        if (c.startsWith("/")) c = c.substring(1).trim();
        String tail;
        if (c.startsWith("gamemode ")) {
            tail = c.substring("gamemode ".length()).trim();
        } else {
            int run = c.indexOf(" run ");
            if (!c.startsWith("execute ") || run < 0) return null;
            String afterRun = c.substring(run + 5).trim();
            if (!afterRun.startsWith("gamemode ")) return null;
            tail = afterRun.substring("gamemode ".length()).trim();
        }
        int sp = tail.indexOf(' ');
        String mode = (sp >= 0 ? tail.substring(0, sp) : tail).trim().toLowerCase(java.util.Locale.ROOT);
        return switch (mode) {
            case "survival", "s", "0"   -> net.minecraft.world.level.GameType.SURVIVAL;
            case "creative", "c", "1"   -> net.minecraft.world.level.GameType.CREATIVE;
            case "adventure", "a", "2"  -> net.minecraft.world.level.GameType.ADVENTURE;
            case "spectator", "sp", "3" -> net.minecraft.world.level.GameType.SPECTATOR;
            default -> null;
        };
    }

    /**
     * Remember a player's current gamemode for {@code powerId} at the rising edge
     * of the gate, so {@link #restoreGamemode} can put them back when the gate
     * falls. Refreshed every rising edge ({@code put}, not {@code putIfAbsent})
     * so a stale value from a prior projection/respawn cycle can never poison a
     * later restore.
     *
     * <p>Crucially, if the player is ALREADY in the gamemode this power forces
     * (e.g. they re-entered the pocket while still ADVENTURE from a previous
     * cycle, or a respawn left them there), that value is untrustworthy as the
     * "real previous mode" — remembering it would make the falling-edge restore
     * shove them back into the forced mode and undo the pack's own cleanup. In
     * that case fall back to the server's default gamemode (normally survival),
     * which is the mode a sealed-pocket player is meant to return to.
     */
    private static void rememberGamemode(ServerPlayer player, String powerId,
            java.util.Set<net.minecraft.world.level.GameType> forcedGameTypes) {
        String key = player.getUUID() + ":" + powerId;
        net.minecraft.world.level.GameType current = player.gameMode.getGameModeForPlayer();
        net.minecraft.world.level.GameType toRemember = current;
        if (forcedGameTypes.contains(current)) {
            var server = player.level().getServer();
            net.minecraft.world.level.GameType fallback =
                server != null ? server.getDefaultGameType()
                               : net.minecraft.world.level.GameType.SURVIVAL;
            // If even the server default is one the action forces, prefer plain
            // SURVIVAL so the player is never permanently locked out of building.
            toRemember = forcedGameTypes.contains(fallback)
                ? net.minecraft.world.level.GameType.SURVIVAL : fallback;
        }
        GAMEMODE_REVERT.put(key, toRemember);
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

    private CompatPower.Config parseActionOnCallback(ResourceLocation id, JsonObject json) {
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

        // Upstream Origins has two distinct grant-side triggers that we must keep
        // separate:
        //   entity_action_gained / added_action — fire on EVERY grant (login,
        //     respawn, origin-swap). Mapped straight to onGranted.
        //   entity_action_chosen — fires ONLY when the player selects the origin
        //     from the picker. Routed through the CHOSEN event (EventPowerIndex)
        //     rather than onGranted: NeoOriginsNetwork DEFERS the CHOSEN dispatch
        //     until every layer has been picked (firstTimeAllFilled). Wiring it to
        //     onGranted instead fires it during applyOriginPowers — immediately on
        //     grant, mid-walkthrough — so a chosen action that relocates the player
        //     across dimensions (e.g. Seer's pocket-dimension teleport) tears down
        //     the picker via the vanilla respawn screen-swap before the class layer
        //     is ever shown (the "class skip" bug). The deferred CHOSEN path is the
        //     only correct home for it.
        EntityAction gainedAction = EntityAction.noop();
        if (json.has("entity_action_gained")) {
            gainedAction = parseActionField(json, "entity_action_gained", idStr);
        }
        if (json.has("added_action")) {
            gainedAction = mergeActions(gainedAction,
                parseActionField(json, "added_action", idStr));
        }

        EntityAction chosenAction = json.has("entity_action_chosen")
            ? parseActionField(json, "entity_action_chosen", idStr)
            : EntityAction.noop();

        EntityAction finalGained = gainedAction;
        EntityAction finalChosen = chosenAction;
        EntityAction finalRemoved = removedAction;
        boolean hasChosen = chosenAction != EntityAction.noop();
        ResourceLocation powerId = id;

        return CompatPower.Config.builder()
            .onGranted(player -> {
                finalGained.execute(player);
                if (hasChosen) registerChosenHandler(player, powerId, finalChosen);
            })
            .onRevoked(player -> {
                finalRemoved.execute(player);
                if (hasChosen) unregisterChosenHandler(player, powerId);
            })
            .onRespawn(respawnAction::execute)
            .build();
    }

    /** Per-player CHOSEN-handler tokens for action_on_callback powers, keyed by
     *  power id so each instance registers + unregisters exactly one handler.
     *  Mirrors {@link #FALL_DAMAGE_TOKENS}: idempotent re-grant (login/respawn/
     *  origin-swap all re-call onGranted) drops the prior token first. */
    private static final java.util.Map<java.util.UUID,
        java.util.Map<String, com.cyberday1.neoorigins.service.EventPowerIndex.Token>>
        CHOSEN_ACTION_TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Register {@code chosenAction} on the CHOSEN event. The deferred dispatch in
     *  {@link com.cyberday1.neoorigins.network.NeoOriginsNetwork} fires CHOSEN once
     *  per picked origin (in layer order), so the handler runs only when the
     *  dispatched origin actually owns this power — otherwise a non-idempotent
     *  chosen action (e.g. "give N starter items") would fire once per layer. */
    private static void registerChosenHandler(ServerPlayer player, ResourceLocation powerId,
                                              EntityAction chosenAction) {
        String key = powerId.toString();
        var perPower = CHOSEN_ACTION_TOKENS.get(player.getUUID());
        if (perPower != null) {
            var existing = perPower.remove(key);
            if (existing != null) {
                com.cyberday1.neoorigins.service.EventPowerIndex.unregister(existing);
            }
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.Handler handler = (sp, ctx) -> {
            if (ctx instanceof ResourceLocation chosenOrigin
                    && !originOwnsPower(chosenOrigin, powerId)) {
                return;
            }
            chosenAction.execute(sp);
        };
        var tok = com.cyberday1.neoorigins.service.EventPowerIndex.register(
            player, com.cyberday1.neoorigins.service.EventPowerIndex.Event.CHOSEN, handler);
        CHOSEN_ACTION_TOKENS.computeIfAbsent(player.getUUID(),
            k -> new java.util.concurrent.ConcurrentHashMap<>()).put(key, tok);
    }

    private static void unregisterChosenHandler(ServerPlayer player, ResourceLocation powerId) {
        var perPower = CHOSEN_ACTION_TOKENS.get(player.getUUID());
        if (perPower == null) return;
        var tok = perPower.remove(powerId.toString());
        if (tok != null) {
            com.cyberday1.neoorigins.service.EventPowerIndex.unregister(tok);
        }
        if (perPower.isEmpty()) CHOSEN_ACTION_TOKENS.remove(player.getUUID());
    }

    /** True if {@code originId}'s effective power set (base + all tier overlays)
     *  contains {@code powerId}. Used to scope a CHOSEN handler to the origin that
     *  actually granted the power. */
    private static boolean originOwnsPower(ResourceLocation originId, ResourceLocation powerId) {
        var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(originId);
        return origin != null && origin.powersForTier(3).contains(powerId);
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

    private CompatPower.Config parseResource(ResourceLocation id, JsonObject json) {
        String key       = id.toString();
        String idStr     = key;
        int min          = json.has("min")         ? json.get("min").getAsInt()         : 0;
        int max          = json.has("max")         ? json.get("max").getAsInt()         : 100;
        int startValue   = json.has("start_value") ? json.get("start_value").getAsInt() : min;
        int interval     = Math.max(1, json.has("interval") ? json.get("interval").getAsInt() : 20);
        int offset       = (idStr.hashCode() & Integer.MAX_VALUE) % interval;

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
        // Apoli hud_render.sprite_location overrides which sheet the bar/icon render
        // against (community packs ship restyled bars at the same coordinates). null
        // == use our vendored default resource_bar.png. The referenced texture is
        // normally provided by the source mod/datapack — we pass the id through verbatim.
        String spriteLocation = null;
        // Apoli hud_render.condition: the bar renders only while this condition
        // holds (contextual bars). null == always render. Evaluated server-side.
        EntityCondition renderCondition = null;
        // Boolean toggles (min=0, max=1) are internal state, not player-facing bars.
        if (min == 0 && max == 1) hidden = true;
        if (json.has("hud_render") && json.get("hud_render").isJsonObject()) {
            JsonObject hud = json.getAsJsonObject("hud_render");
            if (hud.has("sprite_location")) {
                spriteLocation = hud.get("sprite_location").getAsString();
            }
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
                player.getData(CompatAttachments.resourceState()).set(key, startValue);
                CompatAttachments.syncResourcesToClient(player);
            })
            .onRevoked(player -> {
                player.getData(CompatAttachments.resourceState()).remove(key);
                CompatAttachments.unregisterResourceMeta(key);
                CompatAttachments.unregisterResourceRenderCondition(key);
                PREV_RENDER_CONDITIONS.remove(player.getUUID() + ":rcond:" + key);
                CompatAttachments.syncResourcesToClient(player);
            })
            .onTick(player -> {
                var state = player.getData(CompatAttachments.resourceState());
                if (player.level().getServer() != null && (player.level().getServer().getTickCount() + offset) % interval == 0) {
                    tickAction.execute(player);
                }
                // Edge-triggered min/max actions — only fire on the transition,
                // not every tick while sitting at the boundary. Use the static
                // PREV_RESOURCE_VALUES map for edge detection. On first tick
                // (prev == null), assume previous was startValue so boundary
                // actions fire if the resource was persisted at min/max.
                int cur = state.get(key, startValue);
                String edgeKey = player.getUUID() + ":" + key;
                Integer prev = PREV_RESOURCE_VALUES.put(edgeKey, cur);
                int prevVal = prev != null ? prev : startValue;
                if (cur != prevVal) {
                    if (cur <= min && prevVal > min) minAction.execute(player);
                    if (cur >= max && prevVal < max) maxAction.execute(player);
                }
                // Sync to client every 10 ticks when dirty — value-only payload;
                // metadata was already pushed by the full sync at grant/login.
                if (state.isDirty() && player.tickCount % 10 == 0) {
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
    private CompatPower.Config parseCooldown(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseToggle(ResourceLocation id, JsonObject json) {
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
        // If it does, register it so a client press routes here; otherwise it defaults to
        // the primary-active skill slot via onActivated.
        KeySpec ks = classifyKey(json, "key.origins.primary_active");
        if (ks.namedHotkey()) {
            int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.register(ks.key(),
                new com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.Binding(
                    id, toggleAction, condition, cooldown, ks.continuous(), failAction));
            return builder.cooldownTicks(cooldown).build();
        }
        return builder.onActivated(player -> {
            // Previously the slot path ignored a declared `condition` entirely
            // (only the hotkey path enforced it) — enforce it here for parity.
            if (!condition.test(player)) {
                if (failAction != null) failAction.execute(player);
                return;
            }
            toggleAction.execute(player);
        }).build();
    }

    private CompatPower.Config parseConditionedAttribute(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        // Attribute can be at top level or nested inside "modifier" object
        String attrStr = null;
        if (json.has("attribute")) {
            attrStr = json.get("attribute").getAsString();
        } else if (json.has("modifier") && json.get("modifier").isJsonObject()
                   && json.getAsJsonObject("modifier").has("attribute")) {
            attrStr = json.getAsJsonObject("modifier").get("attribute").getAsString();
        }
        if (attrStr == null) {
            CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                "missing 'attribute' field in JSON");
            return null;
        }

        // Fall-damage isn't a vanilla attribute, so a conditioned_attribute that
        // targets it (e.g. attribute "apoli:fall_damage" / "fall_damage") would
        // fail the registry lookup below and get SILENTLY DROPPED. Route it to the
        // dedicated fall-damage handler instead — it reads the same modifier/
        // modifiers + condition off this object and scales fall damage via the
        // native MOD_FALL_DAMAGE seam. (path-only match: namespace varies across
        // packs, but the leaf is consistently "fall_damage".)
        String attrLeaf = attrStr.contains(":") ? attrStr.substring(attrStr.indexOf(':') + 1) : attrStr;
        if (attrLeaf.equals("fall_damage") || attrLeaf.equals("generic.fall_damage")) {
            // The modifier may be nested inside the "modifier" object alongside the
            // attribute key — hoist it to the top-level "modifier" the fall-damage
            // parser expects, without mutating the original (defensive copy).
            JsonObject fwd = json.deepCopy();
            if (!fwd.has("modifier") && !fwd.has("modifiers")
                    && json.has("modifier") && json.get("modifier").isJsonObject()) {
                fwd.add("modifier", json.getAsJsonObject("modifier"));
            }
            CompatTranslationLog.pass(id,
                "origins:conditioned_attribute (fall_damage) -> modify_fall_damage");
            return parseModifyFallDamage(id, fwd);
        }

        ResourceLocation rawAttrIdent = ResourceLocation.parse(attrStr);
        // Try the raw attribute name first. If that fails and the name has a
        // "generic." prefix (used in MC ≤1.21.1), try without it (MC 1.21.2+
        // dropped the prefix). This lets the same pack work on both versions.
        var attrOpt = BuiltInRegistries.ATTRIBUTE.getOptional(rawAttrIdent);
        ResourceLocation attrIdent = rawAttrIdent;
        if (attrOpt.isEmpty() && rawAttrIdent.getPath().startsWith("generic.")) {
            attrIdent = ResourceLocation.fromNamespaceAndPath(rawAttrIdent.getNamespace(),
                rawAttrIdent.getPath().substring("generic.".length()));
            attrOpt = BuiltInRegistries.ATTRIBUTE.getOptional(attrIdent);
        }
        // Also try adding "generic." prefix if not present (26.1 pack on 1.21.1)
        if (attrOpt.isEmpty() && !rawAttrIdent.getPath().startsWith("generic.")) {
            attrIdent = ResourceLocation.fromNamespaceAndPath(rawAttrIdent.getNamespace(),
                "generic." + rawAttrIdent.getPath());
            attrOpt = BuiltInRegistries.ATTRIBUTE.getOptional(attrIdent);
        }
        if (attrOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: unknown attribute '{}' (raw: '{}') — power will no-op",
                idStr, attrIdent, rawAttrIdent);
            // Surface the bad attribute id in the compat log too — pack
            // authors usually only check that file when debugging, not the
            // main game log. Without the id they only see "no handler
            // produced a config" which is unactionable.
            CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                "unknown attribute '" + rawAttrIdent + "' — pack-side fix: confirm attribute exists in 1.21.1");
            return null;
        }
        var attrHolder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attrOpt.get());

        JsonObject modObj = json.has("modifier") ? json.getAsJsonObject("modifier") : json;
        double value = modObj.has("value")  ? modObj.get("value").getAsDouble()
                     : modObj.has("amount") ? modObj.get("amount").getAsDouble() : 0.0;
        String op = modObj.has("operation") ? modObj.get("operation").getAsString() : "add_value";
        // Apoli clamp/set ops (min/max/set) have no vanilla AttributeModifier
        // equivalent — applying them as add_value corrupts the attribute (a cap
        // becomes a flat bonus). Skip the power rather than mis-apply it.
        if (!OriginsOperationMapper.isRepresentable(op)) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: attribute operation '{}' (clamp/set) has no vanilla "
                + "equivalent — power will no-op", idStr, op);
            CompatTranslationLog.skip(id, "origins:conditioned_attribute",
                "operation '" + op + "' (clamp/set) cannot be represented as a vanilla attribute modifier");
            return null;
        }
        AttributeModifier.Operation operation = switch (OriginsOperationMapper.mapOperation(op)) {
            case "add_multiplied_base"  -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default                     -> AttributeModifier.Operation.ADD_VALUE;
        };

        EntityCondition condition = parseConditionField(json, "condition", idStr);

        // Stable modifier ID derived from the power ID.
        String safeKey = id.getPath().replace('/', '_');
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath("neoorigins", "condattr_" + safeKey);

        return CompatPower.Config.builder()
            .onTick(player -> {
                AttributeInstance inst = player.getAttribute(attrHolder);
                if (inst == null) return;
                boolean shouldHave = condition.test(player);
                boolean has = inst.getModifier(modifierId) != null;
                if (shouldHave && !has) {
                    inst.addPermanentModifier(new AttributeModifier(modifierId, value, operation));
                } else if (!shouldHave && has) {
                    inst.removeModifier(modifierId);
                }
            })
            .onRevoked(player -> {
                AttributeInstance inst = player.getAttribute(attrHolder);
                if (inst != null) inst.removeModifier(modifierId);
            })
            .build();
    }

    /** origins:shaking — makes the player model shake (like zombie-to-drowned conversion).
     *  Implemented via freeze ticks which trigger the same visual. */
    private CompatPower.Config parseShaking(ResourceLocation id, JsonObject json) {
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
    // Keyed by player UUID → effect ResourceLocation → amplifier delta.
    // Checked by CombatPowerEvents.onMobEffectAdded to boost amplifiers.
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID,
        java.util.Map<ResourceLocation, Integer>> EFFECT_AMP_MODIFIERS = new java.util.concurrent.ConcurrentHashMap<>();

    public static int getAmplifierBoost(java.util.UUID playerId, ResourceLocation effectId) {
        var map = EFFECT_AMP_MODIFIERS.get(playerId);
        return map == null ? 0 : map.getOrDefault(effectId, 0);
    }

    static void addAmplifierModifier(java.util.UUID playerId, ResourceLocation effectId, int delta) {
        EFFECT_AMP_MODIFIERS.computeIfAbsent(playerId, k -> new java.util.concurrent.ConcurrentHashMap<>())
            .merge(effectId, delta, Integer::sum);
    }

    static void removeAmplifierModifier(java.util.UUID playerId, ResourceLocation effectId, int delta) {
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
    private CompatPower.Config parseModifyEffectAmplifier(ResourceLocation id, JsonObject json) {
        String effectStr = json.has("status_effect") ? json.get("status_effect").getAsString() : null;
        if (effectStr == null) return null;
        ResourceLocation effectId = ResourceLocation.parse(effectStr);

        JsonObject modObj = json.has("modifier") && json.get("modifier").isJsonObject()
            ? json.getAsJsonObject("modifier") : json;
        int value = modObj.has("value") ? modObj.get("value").getAsInt() : 1;

        return CompatPower.Config.builder()
            .onGranted(player -> addAmplifierModifier(player.getUUID(), effectId, value))
            .onRevoked(player -> removeAmplifierModifier(player.getUUID(), effectId, value))
            .build();
    }

    /** origins:modify_falling — slow fall with optional no fall damage. */
    private CompatPower.Config parseModifyFalling(ResourceLocation id, JsonObject json) {
        float velocity = json.has("velocity") ? json.get("velocity").getAsFloat() : 0.1f;
        boolean takeFallDamage = !json.has("take_fall_damage") || json.get("take_fall_damage").getAsBoolean();

        String safeKey = id.getPath().replace('/', '_');
        ResourceLocation gravModId = ResourceLocation.fromNamespaceAndPath("neoorigins", "modfalling_grav_" + safeKey);
        ResourceLocation fallModId = ResourceLocation.fromNamespaceAndPath("neoorigins", "modfalling_fall_" + safeKey);

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
    private CompatPower.Config parseModifyFallDamage(ResourceLocation id, JsonObject json) {
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
            .onGranted(player -> {
                // Idempotent re-grant: drop any prior handler for this power id
                // before re-registering, so login/respawn/origin-swap can't stack
                // multiple multipliers (the ActionOnEventPower leak-guard).
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
            })
            .onRevoked(player -> {
                var perPower = FALL_DAMAGE_TOKENS.get(player.getUUID());
                if (perPower != null) {
                    var tok = perPower.remove(idStr);
                    if (tok != null) {
                        com.cyberday1.neoorigins.service.EventPowerIndex.unregister(tok);
                    }
                    if (perPower.isEmpty()) FALL_DAMAGE_TOKENS.remove(player.getUUID());
                }
            })
            .build();
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
    private CompatPower.Config parseModifyVelocity(ResourceLocation id, JsonObject json) {
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
    private CompatPower.Config parseOverlay(ResourceLocation id, JsonObject json) {
        return CompatPower.Config.builder().build();
    }

    private CompatPower.Config parseConditionedStatusEffect(ResourceLocation id, JsonObject json) {
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
        if (effectId == null) return null;

        CompatPolicy.resetFailClosedCount();
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        if (CompatPolicy.failClosedCount() > 0) {
            NeoOrigins.LOGGER.warn("[CompatB] conditioned_status_effect {} has unsupported condition(s) — refusing to compile", idStr);
            return null;
        }

        // Cache mob effect holder at parse time
        ResourceLocation effectIdent = ResourceLocation.parse(effectId);
        var effectOpt = BuiltInRegistries.MOB_EFFECT.getOptional(effectIdent);
        if (effectOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: unknown mob effect '{}' — power will no-op", idStr, effectIdent);
            return null;
        }
        var effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effectOpt.get());

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

    private CompatPower.Config parseSelfActionWhenHit(ResourceLocation id, JsonObject json) {
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
    private CompatPower.Config parseSelfActionOnHit(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseDamageOverTime(ResourceLocation id, JsonObject json) {
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

    /**
     * v2.1.4 — Route B for {@code origins:exhaust}.
     *
     * Previously translateExhaust mapped to a single set-op food modifier,
     * which {@link ModifyFoodRegistry} applied every food refill — multiplying
     * intended drain by hundreds of ticks per second. The correct shape is a
     * periodic {@link ServerPlayer#causeFoodExhaustion(float)} call.
     */
    private CompatPower.Config parseExhaust(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseFireProjectile(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        String entityTypeStr = json.has("entity_type") ? json.get("entity_type").getAsString() : "minecraft:arrow";
        float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.5f;
        float divergence = json.has("divergence") ? json.get("divergence").getAsFloat() : 1.0f;
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 0;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;

        return CompatPower.Config.builder()
            .cooldownTicks(cooldown)
            .onActivated(player -> {
                if (cooldown > 0) {
                    PlayerOriginData data = player.getData(OriginAttachments.originData());
                    if (data.isOnCooldown(player, idStr)) return;
                    data.setCooldown(idStr, player.tickCount, cooldown);
                }
                if (!(player.level() instanceof ServerLevel sl)) return;
                Vec3 look = player.getLookAngle();
                for (int i = 0; i < count; i++) {
                    if ("minecraft:small_fireball".equals(entityTypeStr)) {
                        SmallFireball fb = new SmallFireball(sl, player, look.scale(speed));
                        fb.setPos(player.getX(), player.getEyeY(), player.getZ());
                        sl.addFreshEntity(fb);
                    } else {
                        // Default: use execute_command to summon the entity type
                        // This handles snowball, arrow, and any other projectile type
                        String cmd = String.format(
                            "summon %s ~ ~1.5 ~ {Motion:[%fd,%fd,%fd]}",
                            entityTypeStr,
                            look.x * speed, look.y * speed, look.z * speed
                        );
                        sl.getServer().getCommands().performPrefixedCommand(
                            player.createCommandSourceStack().withSuppressedOutput(), cmd
                        );
                    }
                }
            })
            .build();
    }

    private CompatPower.Config parseTargetActionOnHit(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        // [LOSSY] target_action_on_hit fires on kill, not on every hit (no hit event for target entity)
        EntityAction action = parseActionField(json, "entity_action", idStr);
        return CompatPower.Config.builder()
            .onKill(action::execute)
            .build();
    }

    private CompatPower.Config parseSelfActionOnKill(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        EntityAction action = parseActionField(json, "entity_action", idStr);
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        return CompatPower.Config.builder()
            .onKill(player -> {
                if (condition.test(player)) action.execute(player);
            })
            .build();
    }

    private CompatPower.Config parseLaunch(ResourceLocation id, JsonObject json) {
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

        // launch can be bound to a pack-declared hotkey; register it if so. Cooldown is
        // enforced by PowerKeybindRegistry.dispatch for the named-key path, so the action
        // itself stays cooldown-free there to avoid double-gating.
        KeySpec ks = classifyKey(json, "key.origins.primary_active");
        if (ks.namedHotkey()) {
            com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.register(ks.key(),
                new com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry.Binding(
                    id, launchAction, condition, cooldown, ks.continuous(), failAction));
            return builder.build();
        }
        return builder
            .onActivated(player -> {
                // Previously the slot path ignored a declared `condition` entirely
                // (only the hotkey path enforced it) — enforce it here for parity.
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

    private CompatPower.Config parseEntityGlow(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parsePreventDeath(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parseActionOnLand(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parsePreventItemUse(ResourceLocation id, JsonObject json) {
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
            condition, itemPred, null, null);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parseRestrictArmor(ResourceLocation id, JsonObject json) {
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

    private CompatPower.Config parsePreventSleep(ResourceLocation id, JsonObject json) {
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
            condition, null, blockCond, null);

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

    private CompatPower.Config parsePreventBlockUse(ResourceLocation id, JsonObject json) {
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
            condition, null, blockPred, null);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
            .build();
    }

    private CompatPower.Config parsePreventEntityUse(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        var data = CompatPlayerState.EventPowerData.noCondition(
            idStr, CompatPlayerState.EventType.PREVENT_ENTITY_USE);

        return CompatPower.Config.builder()
            .onGranted(player -> CompatPlayerState.register(player, data))
            .onRevoked(player -> CompatPlayerState.unregister(player, data))
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
    private CompatPower.Config parseNumericModifier(ResourceLocation id, JsonObject json,
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

    /**
     * {@code modify_crafting} — overrides the result of a specific recipe
     * for players holding this power. Apoli authors use it to swap potion
     * outputs, give Origins-exclusive equipment, etc.
     *
     * <p>Apoli shape:
     * <pre>{@code
     * { "type": "origins:modify_crafting",
     *   "recipe": "ns:my_recipe",
     *   "result": { "item": "minecraft:potion",
     *               "tag": "{Potion:\"minecraft:water_breathing\"}" }
     * }
     * }</pre>
     *
     * <p>Implementation: registers a {@link ModifyCraftingRegistry.Entry}
     * keyed by player + recipe id. {@code CompatEventPowers.onItemCrafted}
     * matches the crafted recipe against the registry and swaps the result
     * stack at craft time.
     */
    private CompatPower.Config parseModifyCrafting(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        if (!json.has("recipe") || !json.has("result")) {
            NeoOrigins.LOGGER.warn("[CompatB] modify_crafting '{}' missing 'recipe' or 'result' — skipped", id);
            return null;
        }
        ResourceLocation recipeId = ResourceLocation.tryParse(json.get("recipe").getAsString());
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
        ResourceLocation resultItem = ResourceLocation.tryParse(resultItemStr);
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

    /**
     * {@code prevent_sprinting} — clamps {@link net.minecraft.world.entity.Entity#setSprinting(boolean)}
     * back to false every tick the holder's optional {@code condition} is met.
     *
     * <p>Implemented as a per-tick handler rather than via an event because the
     * sprint flag is set by client input and synced via packet; the cheapest
     * server-side veto is to re-clear it once per tick. This causes one frame
     * of visible sprint before the client gets the corrective sync, which is
     * acceptable parity with Apoli's behaviour on Fabric.
     */
    private CompatPower.Config parsePreventSprinting(ResourceLocation id, JsonObject json) {
        String idStr = id.toString();
        EntityCondition condition = parseConditionField(json, "condition", idStr);
        return CompatPower.Config.builder()
            .onTick(player -> {
                if (!player.isSprinting()) return;
                if (condition.test(player)) player.setSprinting(false);
            })
            .build();
    }

    private CompatPower.Config parseModifyFood(ResourceLocation id, JsonObject json) {
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
    /**
     * Resolved damage arithmetic from a list of Apoli modifier entries: a
     * multiplier plus optional Apoli total-clamp ops. Mirrors
     * {@link com.cyberday1.neoorigins.power.builtin.ModifyDamagePower.Config#apply}
     * so Route A (native power) and Route B (this compiled lambda) clamp damage
     * identically. {@code max_total} is an upper CAP, {@code min_total} a lower
     * FLOOR (Apoli's confusing naming), {@code set_total} replaces outright.
     */
    private record DamageMath(float multiplier, Float setTotal, Float maxTotal, Float minTotal) {
        float apply(float amount) {
            float v = amount * multiplier;
            if (setTotal != null) v = setTotal;
            if (maxTotal != null) v = Math.min(v, maxTotal);
            if (minTotal != null) v = Math.max(v, minTotal);
            if (!Float.isFinite(v)) v = Float.MAX_VALUE;
            return v;
        }
    }

    private static DamageMath collapseDamageMath(java.util.List<OriginsModifierMath.Modifier> mods) {
        if (mods == null || mods.isEmpty()) return new DamageMath(1.0f, null, null, null);
        double additive = 0.0;
        Double setTotal = null;
        Float maxTotal = null;
        Float minTotal = null;
        for (OriginsModifierMath.Modifier m : mods) {
            String op = m.operation() == null ? "addition" : m.operation();
            switch (op) {
                case "set_total", "set" -> setTotal = m.value();
                case "max_total"        -> maxTotal = (float) m.value();
                case "min_total"        -> minTotal = (float) m.value();
                default                 -> additive += m.value();
            }
        }
        // No set_total: damage is (1 + Σ additive) ×. set_total replaces post-scale.
        float multiplier = (float) (1.0 + additive);
        Float setTotalF = setTotal != null ? (float) (double) setTotal : null;
        return new DamageMath(multiplier, setTotalF, maxTotal, minTotal);
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
                && condJson.has("conditions") && condJson.get("conditions").isJsonArray()) {
            boolean isAnd = bareType.equals("and") || bareType.equals("all_of");
            java.util.List<java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos>> subs =
                new java.util.ArrayList<>();
            for (JsonElement el : condJson.getAsJsonArray("conditions")) {
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

        String blockId = condJson.has("block") ? condJson.get("block").getAsString() : null;
        if (blockId == null) blockId = condJson.has("id") ? condJson.get("id").getAsString() : null;
        if (blockId != null) {
            ResourceLocation bid = ResourceLocation.parse(blockId);
            return (player, pos) -> {
                var block = player.level().getBlockState(pos).getBlock();
                return BuiltInRegistries.BLOCK.getKey(block).equals(bid);
            };
        }

        String tag = condJson.has("tag") ? condJson.get("tag").getAsString() : null;
        if (tag != null) {
            var tagKey = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse(tag));
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

    private CompatPower.Config parseModifyJump(ResourceLocation id, JsonObject json) {
        // Extract the jump modifier value
        double value = 0.0;
        if (json.has("modifier") && json.get("modifier").isJsonObject()) {
            JsonObject mod = json.getAsJsonObject("modifier");
            value = mod.has("value") ? mod.get("value").getAsDouble()
                  : mod.has("amount") ? mod.get("amount").getAsDouble() : 0.0;
        }

        // Cache attribute holder at parse time
        ResourceLocation jumpAttrId = ResourceLocation.parse("minecraft:generic.jump_strength");
        var jumpOpt = BuiltInRegistries.ATTRIBUTE.getOptional(jumpAttrId);
        if (jumpOpt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: jump_strength attribute '{}' not found — modify_jump power will no-op",
                id, jumpAttrId);
            return null;
        }
        var jumpHolder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(jumpOpt.get());

        String safeKey = id.getPath().replace('/', '_');
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath("neoorigins", "modjump_" + safeKey);
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
    private CompatPower.Config parseConditionedRestrictArmor(ResourceLocation id, JsonObject json) {
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
    private CompatPower.Config parseModifyHarvest(ResourceLocation id, JsonObject json) {
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
                    ResourceLocation.parse(tag));
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
     *   <li>A string id pointing to an existing registered recipe — the live
     *       recipe is wrapped in an {@code OriginGatedRecipe(has_power)} in
     *       place so only holders of this power can craft it.</li>
     *   <li>An inline recipe JSON object (full {@code type} + {@code ingredients}
     *       + {@code result} shape). The inline recipe is registered via
     *       {@link com.cyberday1.neoorigins.service.InlineRecipeRegistry}
     *       under a synthesized id and likewise wrapped with a {@code has_power}
     *       craft gate.</li>
     * </ul>
     *
     * <p>In both cases {@code onGranted} still calls {@code awardRecipes} so the
     * recipe is visible in the holder's recipe book; the gate enforces the craft
     * restriction at the crafting table (see {@link OriginGatedRecipe}).
     */
    private CompatPower.Config parseRecipe(ResourceLocation id, JsonObject json) {
        if (!json.has("recipe")) {
            NeoOrigins.LOGGER.debug("[CompatB] {}: origins:recipe missing 'recipe' field", id);
            return null;
        }
        JsonElement recipeEl = json.get("recipe");
        final ResourceLocation recipeLoc;
        if (recipeEl.isJsonPrimitive()) {
            recipeLoc = ResourceLocation.tryParse(recipeEl.getAsString());
            if (recipeLoc == null) {
                NeoOrigins.LOGGER.warn("[CompatB] {}: origins:recipe has malformed recipe id '{}'",
                    id, recipeEl.getAsString());
                return null;
            }
            // Gate the referenced recipe so only holders of this power can craft
            // it — InlineRecipeRegistry wraps the live recipe in an
            // OriginGatedRecipe(has_power) after the datapack reload completes.
            com.cyberday1.neoorigins.service.InlineRecipeRegistry.registerRefGate(recipeLoc, id);
        } else if (recipeEl.isJsonObject()) {
            // Inline recipe: register under a synthesized id and treat as
            // if the pack had shipped a separate recipe data file pointed to
            // by that id. InlineRecipeRegistry handles the actual injection
            // into RecipeManager once the datapack reload completes, wrapping
            // it in an OriginGatedRecipe(has_power) keyed to this power.
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
                var server = player.getServer();
                if (server == null) return;
                var recipeManager = server.getRecipeManager();
                var recipe = recipeManager.byKey(recipeLoc);
                if (recipe.isPresent()) {
                    player.awardRecipes(java.util.List.of(recipe.get()));
                }
                // If recipe is empty (inline recipe injection hasn't run yet on
                // this server start), the OnDatapackSyncEvent path will inject
                // it shortly. Re-grant on next login or via /reload picks it up.
            })
            .onRevoked(player -> {
                var server = player.getServer();
                if (server == null) return;
                var recipeManager = server.getRecipeManager();
                var recipe = recipeManager.byKey(recipeLoc);
                if (recipe.isPresent()) {
                    player.resetRecipes(java.util.List.of(recipe.get()));
                }
            })
            .build();
    }

    private CompatPower.Config parsePreventGameEvent(ResourceLocation id, JsonObject json) {
        String eventId = json.has("event") ? json.get("event").getAsString() : null;
        if (eventId == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: prevent_game_event missing 'event' field", id);
            return null;
        }
        ResourceLocation eventLoc = ResourceLocation.parse(eventId);
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
    private CompatPower.Config parseFreeze(ResourceLocation id, JsonObject json) {
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
}
