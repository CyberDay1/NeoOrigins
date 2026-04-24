package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Registry of old NeoOrigins power-type IDs → new (generic) type ID + optional
 * field remap. Applied by PowerDataManager before {@code PowerTypes.get(typeId)}.
 *
 * <p>Part of the 2.0 power-type consolidation: as 88 PowerType classes collapse
 * into ~25 generic composable types, legacy JSON payloads authored against the
 * old class IDs must continue to load. Deprecation warnings fire once per unique
 * old type ID per boot so pack authors see the migration path in logs.
 *
 * <p>Planned retirement: remove after 2 major versions (i.e. NeoOrigins 4.0).
 */
public final class LegacyPowerTypeAliases {

    private LegacyPowerTypeAliases() {}

    /** Mutates the JSON in place to conform to the new type's schema. */
    @FunctionalInterface
    public interface FieldRemapper extends BiConsumer<JsonObject, ResourceLocation> {}

    private record Alias(ResourceLocation newType, FieldRemapper remap) {}

    private static final Map<ResourceLocation, Alias> ALIASES = new HashMap<>();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();

    /** Register an alias. Should be called during mod construction / registry setup. */
    public static void register(ResourceLocation oldType, ResourceLocation newType, FieldRemapper remap) {
        ALIASES.put(oldType, new Alias(newType, remap != null ? remap : (j, id) -> {}));
    }

    public static void register(ResourceLocation oldType, ResourceLocation newType) {
        register(oldType, newType, null);
    }

    /**
     * If {@code typeId} is an alias, rewrite it to the new type, remap fields in
     * {@code json}, and return the new type ID. Otherwise returns {@code typeId}
     * unchanged.
     *
     * <p>Fires one deprecation warning per unique old type per boot.
     */
    public static ResourceLocation apply(ResourceLocation typeId, JsonObject json, ResourceLocation powerId) {
        Alias alias = ALIASES.get(typeId);
        if (alias == null) return typeId;
        // Never override an extant legacy type — only kick in once the legacy
        // Java class is deleted. This lets us register aliases upfront without
        // them stealing traffic from still-registered classes.
        if (PowerTypes.get(typeId) != null) return typeId;
        if (WARNED.add(typeId)) {
            NeoOrigins.LOGGER.warn(
                "[2.0-legacy] power type '{}' is deprecated — remap to '{}' (first seen on power '{}')",
                typeId, alias.newType, powerId);
        }
        try {
            alias.remap.accept(json, powerId);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[2.0-legacy] field remap for '{}' on power '{}' failed: {}",
                typeId, powerId, e.getMessage());
        }
        // Ensure the JSON's own `type` field reflects the new type for downstream parsers.
        json.addProperty("type", alias.newType.toString());
        return alias.newType;
    }

    /** For testing / diagnostics: clear the warned-set so the next apply re-logs. */
    public static void resetWarnings() {
        WARNED.clear();
    }

    /**
     * Dev-only helper that runs the alias remap regardless of the dormancy
     * guard. Used by the {@code smokeTestAliases} gradle task to validate
     * remap lambdas against the internal JSON pack without needing to delete
     * the legacy Java classes first. Not used in production paths.
     *
     * <p>Returns the new type id if {@code typeId} is an alias (and mutates
     * {@code json} accordingly), otherwise returns {@code typeId} unchanged.
     */
    public static ResourceLocation simulateApply(ResourceLocation typeId, JsonObject json, ResourceLocation powerId) {
        Alias alias = ALIASES.get(typeId);
        if (alias == null) return typeId;
        alias.remap.accept(json, powerId);
        json.addProperty("type", alias.newType.toString());
        return alias.newType;
    }

    /** Count of registered aliases — used by startup diagnostics. */
    public static int size() {
        return ALIASES.size();
    }

    // ── Bootstrap ──────────────────────────────────────────────────────────
    //
    // One working alias as an infrastructure test. Phase 1+ will register many more.
    // `neoorigins:active_teleport` → `neoorigins:active_ability` with a marker field
    // that tells the generic active_ability power which legacy handler to run.
    // The actual `neoorigins:active_ability` type doesn't exist yet — it's a Phase 1
    // deliverable — so this registration is commented out until then.

    public static void bootstrap() {
        registerActiveAbilityAliases();
        registerPersistentEffectAliases();
        registerAttributeModifierAliases();
        registerConditionPassiveAliases();
        registerModifierHookAliases();
        registerCrossModCompatAliases();
        NeoOrigins.LOGGER.debug("[2.0-legacy] power-type alias table initialised ({} entries)", size());
    }

    // ── Phase 4: condition_passive aliases ─────────────────────────────────
    //
    // Tick-driven condition + action pairs. Four of the ten originally-scoped
    // legacy types stay standalone because their runtime model is not a
    // tick-based condition check:
    //   - mobs_ignore_player     (LivingChangeTargetEvent interceptor)
    //   - no_mob_spawns_nearby   (MobSpawnEvent.FinalizeSpawn interceptor)
    //   - item_magnetism         (item-entity pull — no DSL verb yet)
    //   - breath_in_fluid        (air-supply drain — no DSL verb yet)
    // The other six (incl. regen_in_fluid reassigned from Phase 2) alias cleanly.

    private static final ResourceLocation ID_CONDITION_PASSIVE =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "condition_passive");

    private static void registerConditionPassiveAliases() {
        // biome_buff: apply mob effect while in a biome tag.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "biome_buff"),
                 ID_CONDITION_PASSIVE, (json, powerId) -> {
                    String biomeTag = json.has("biome_tag") ? json.get("biome_tag").getAsString() : "";
                    String effect = json.has("effect") ? json.get("effect").getAsString() : "minecraft:regeneration";
                    int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;

                    json.add("condition", biomeTagCondition(biomeTag));
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "neoorigins:apply_effect");
                    action.addProperty("effect", effect);
                    action.addProperty("duration", 300);
                    action.addProperty("amplifier", amplifier);
                    json.add("entity_action", action);
                    json.addProperty("interval", 20);
                    json.remove("biome_tag");
                    json.remove("effect");
                    json.remove("amplifier");
                });

        // damage_in_biome: damage while in a biome tag OR any of a biome list.
        // Accepts either {biome_tag: "#..."} or {biomes: ["id1", "id2"]} —
        // Water Mage's Dehydration wants multiple specific biomes (desert,
        // badlands variants) that don't share a single vanilla tag.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "damage_in_biome"),
                 ID_CONDITION_PASSIVE, (json, powerId) -> {
                    String biomeTag = json.has("biome_tag") ? json.get("biome_tag").getAsString() : "";
                    float dps = json.has("damage_per_second") ? json.get("damage_per_second").getAsFloat() : 1.0f;
                    String damageType = json.has("damage_type") ? json.get("damage_type").getAsString() : "generic";

                    com.google.gson.JsonObject condition;
                    if (json.has("biomes") && json.get("biomes").isJsonArray()) {
                        com.google.gson.JsonArray biomes = json.getAsJsonArray("biomes");
                        com.google.gson.JsonArray subConditions = new com.google.gson.JsonArray();
                        for (com.google.gson.JsonElement el : biomes) {
                            if (!el.isJsonPrimitive()) continue;
                            com.google.gson.JsonObject bc = new com.google.gson.JsonObject();
                            bc.addProperty("type", "neoorigins:biome");
                            bc.addProperty("biome", el.getAsString());
                            subConditions.add(bc);
                        }
                        if (subConditions.size() == 1) {
                            condition = subConditions.get(0).getAsJsonObject();
                        } else {
                            condition = new com.google.gson.JsonObject();
                            condition.addProperty("type", "neoorigins:or");
                            condition.add("conditions", subConditions);
                        }
                    } else {
                        condition = biomeTagCondition(biomeTag);
                    }

                    json.add("condition", condition);
                    json.add("entity_action", damageAction(damageType, dps));
                    json.addProperty("interval", 20);
                    json.remove("biome_tag");
                    json.remove("biomes");
                    json.remove("damage_per_second");
                    json.remove("damage_type");
                });

        // damage_in_daylight: while exposed_to_sun AND not in water, apply any
        // combination of damage and/or ignition per JSON knobs.
        //
        //   damage_per_second  (float,   default 1.0)  — damage each interval.
        //                                                 Set to 0 to disable.
        //   ignite             (boolean, default false) — also set the player on fire.
        //   fire_ticks         (int,     default 40)   — burn duration when ignite=true.
        //
        // Damage and ignition can be combined (both apply each tick). Because the
        // condition does NOT require "not on fire", damage continues while the
        // player is burning from a prior ignite, giving a proper continuous-burn
        // feel — the vanilla fire tick deals its own damage, and the authored
        // damage_per_second stacks on top.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "damage_in_daylight"),
                 ID_CONDITION_PASSIVE, (json, powerId) -> {
                    float dps = json.has("damage_per_second") ? json.get("damage_per_second").getAsFloat() : 1.0f;
                    boolean ignite = json.has("ignite") && json.get("ignite").getAsBoolean();
                    int fireTicks = json.has("fire_ticks") ? json.get("fire_ticks").getAsInt() : 40;

                    com.google.gson.JsonObject inSun = new com.google.gson.JsonObject();
                    inSun.addProperty("type", "neoorigins:exposed_to_sun");
                    com.google.gson.JsonObject notWet = notCondition(simpleCondition("neoorigins:in_water"));
                    json.add("condition", andConditions(inSun, notWet));

                    com.google.gson.JsonArray actions = new com.google.gson.JsonArray();
                    if (dps > 0) {
                        actions.add(damageAction("in_fire", dps));
                    }
                    if (ignite) {
                        com.google.gson.JsonObject fire = new com.google.gson.JsonObject();
                        fire.addProperty("type", "neoorigins:set_on_fire");
                        fire.addProperty("ticks", fireTicks);
                        actions.add(fire);
                    }

                    if (actions.size() == 1) {
                        json.add("entity_action", actions.get(0).getAsJsonObject());
                    } else if (actions.size() > 1) {
                        com.google.gson.JsonObject and = new com.google.gson.JsonObject();
                        and.addProperty("type", "neoorigins:and");
                        and.add("actions", actions);
                        json.add("entity_action", and);
                    } else {
                        // Both disabled — harmless no-op so the power still loads.
                        com.google.gson.JsonObject nothing = new com.google.gson.JsonObject();
                        nothing.addProperty("type", "neoorigins:nothing");
                        json.add("entity_action", nothing);
                    }

                    json.addProperty("interval", 20);
                    json.remove("damage_per_second");
                    json.remove("ignite");
                    json.remove("fire_ticks");
                });

        // damage_in_water: damage while in water, or while exposed to rain
        // (when include_rain is true). Compose via origins:or + origins:and.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "damage_in_water"),
                 ID_CONDITION_PASSIVE, (json, powerId) -> {
                    float dps = json.has("damage_per_second") ? json.get("damage_per_second").getAsFloat() : 1.0f;
                    boolean includeRain = !json.has("include_rain") || json.get("include_rain").getAsBoolean();

                    com.google.gson.JsonObject inWater = simpleCondition("neoorigins:in_water");
                    if (includeRain) {
                        com.google.gson.JsonObject inRain = simpleCondition("neoorigins:in_rain");
                        json.add("condition", orConditions(inWater, inRain));
                    } else {
                        json.add("condition", inWater);
                    }
                    json.add("entity_action", damageAction("magic", dps));
                    json.addProperty("interval", 20);
                    json.remove("damage_per_second");
                    json.remove("include_rain");
                });

        // burn_at_health_threshold: set on fire while relative_health <= threshold.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "burn_at_health_threshold"),
                 ID_CONDITION_PASSIVE, (json, powerId) -> {
                    float threshold = json.has("threshold_percent") ? json.get("threshold_percent").getAsFloat() : 0.25f;
                    int fireTicks = json.has("fire_ticks") ? json.get("fire_ticks").getAsInt() : 60;

                    com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
                    cond.addProperty("type", "neoorigins:relative_health");
                    cond.addProperty("comparison", "<=");
                    cond.addProperty("compare_to", threshold);
                    json.add("condition", cond);

                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "neoorigins:set_on_fire");
                    action.addProperty("ticks", fireTicks);
                    json.add("entity_action", action);
                    json.addProperty("interval", 20);
                    json.remove("threshold_percent");
                    json.remove("fire_ticks");
                });

        // regen_in_fluid: heal while in water (or lava, if fluid=lava). Reassigned
        // here from Phase 2 because this is a tick-condition pair, not a MobEffect.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "regen_in_fluid"),
                 ID_CONDITION_PASSIVE, (json, powerId) -> {
                    String fluid = json.has("fluid") ? json.get("fluid").getAsString() : "water";
                    float amount = json.has("amount_per_second") ? json.get("amount_per_second").getAsFloat() : 1.0f;

                    com.google.gson.JsonObject cond;
                    if ("lava".equalsIgnoreCase(fluid)) {
                        cond = new com.google.gson.JsonObject();
                        cond.addProperty("type", "neoorigins:submerged_in");
                        cond.addProperty("fluid", "minecraft:lava");
                    } else {
                        cond = simpleCondition("neoorigins:in_water");
                    }
                    json.add("condition", cond);

                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "neoorigins:heal");
                    action.addProperty("amount", amount);
                    json.add("entity_action", action);
                    json.addProperty("interval", 20);
                    json.remove("fluid");
                    json.remove("amount_per_second");
                });
    }

    private static com.google.gson.JsonObject simpleCondition(String type) {
        com.google.gson.JsonObject c = new com.google.gson.JsonObject();
        c.addProperty("type", type);
        return c;
    }

    private static com.google.gson.JsonObject biomeTagCondition(String tag) {
        com.google.gson.JsonObject c = new com.google.gson.JsonObject();
        c.addProperty("type", "neoorigins:biome");
        c.addProperty("tag", tag);
        return c;
    }

    private static com.google.gson.JsonObject notCondition(com.google.gson.JsonObject inner) {
        com.google.gson.JsonObject c = new com.google.gson.JsonObject();
        c.addProperty("type", "neoorigins:not");
        c.add("condition", inner);
        return c;
    }

    private static com.google.gson.JsonObject andConditions(com.google.gson.JsonObject... conds) {
        com.google.gson.JsonObject c = new com.google.gson.JsonObject();
        c.addProperty("type", "neoorigins:and");
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (var cond : conds) arr.add(cond);
        c.add("conditions", arr);
        return c;
    }

    private static com.google.gson.JsonObject orConditions(com.google.gson.JsonObject... conds) {
        com.google.gson.JsonObject c = new com.google.gson.JsonObject();
        c.addProperty("type", "neoorigins:or");
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (var cond : conds) arr.add(cond);
        c.add("conditions", arr);
        return c;
    }

    private static com.google.gson.JsonObject damageAction(String sourceName, float amount) {
        com.google.gson.JsonObject c = new com.google.gson.JsonObject();
        c.addProperty("type", "neoorigins:damage");
        c.addProperty("amount", amount);
        com.google.gson.JsonObject src = new com.google.gson.JsonObject();
        src.addProperty("name", sourceName);
        c.add("source", src);
        return c;
    }

    // ── Phase 3: attribute_modifier condition aliases ──────────────────────
    //
    // The architectural Phase 3 work — extending attribute_modifier with a
    // condition field for edge-triggered add/remove — landed earlier as part
    // of the v1.12 line. Of the ten legacy types originally tagged for Phase
    // 3, six already moved to action_on_event under Phase 6 (hunger_drain,
    // natural_regen, knockback, longer_potions, teleport_range_modifier,
    // food_restriction), two are deliberately skipped because their
    // PlayerEvent.BreakSpeed hooks only fire client-side in current NeoForge
    // (break_speed_modifier, underwater_mining_speed), and NoSlowdownPower
    // is a currently-unwired data holder pending a slowdown-source DSL.
    //
    // That leaves less_item_use_slowdown, which aliases cleanly to
    // attribute_modifier with a using_item condition.

    private static final ResourceLocation ID_ATTRIBUTE_MODIFIER =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "attribute_modifier");

    private static void registerAttributeModifierAliases() {
        // less_item_use_slowdown: +speed_multiplier to movement_speed while
        // isUsingItem(). The legacy class also has an item_type filter
        // (bow/shield/any) — the alias drops it because no item-type
        // condition exists yet. Packs filtering by item_type should keep
        // the legacy class until a future DSL verb lands.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "less_item_use_slowdown"),
                 ID_ATTRIBUTE_MODIFIER, (json, powerId) -> {
                    float mult = json.has("speed_multiplier") ? json.get("speed_multiplier").getAsFloat() : 0.5f;
                    boolean filteringByItem = json.has("item_type")
                        && !"any".equalsIgnoreCase(json.get("item_type").getAsString());

                    json.addProperty("attribute", "minecraft:movement_speed");
                    json.addProperty("amount", mult);
                    json.addProperty("operation", "add_multiplied_base");

                    com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
                    cond.addProperty("type", "neoorigins:using_item");
                    json.add("condition", cond);

                    if (filteringByItem) {
                        json.addProperty("_migration_note",
                            "less_item_use_slowdown alias applies to any item-use — legacy item_type filter was dropped");
                    }
                    json.remove("speed_multiplier");
                    json.remove("item_type");
                });
    }

    // ── Phase 2: persistent-effect aliases ─────────────────────────────────
    //
    // Collapses the 5 legacy types that are semantically "apply one or more
    // mob effects while some condition holds" into neoorigins:persistent_effect.
    // The three legacy types that do NOT fit this shape (breath_in_fluid,
    // regen_in_fluid, effect_immunity) stay on their own classes and will
    // migrate in Phase 4 / Phase 6 under different generics.

    private static final ResourceLocation ID_PERSISTENT_EFFECT =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "persistent_effect");

    private static void registerPersistentEffectAliases() {
        // status_effect: single MobEffect with amplifier/ambient/show_particles —
        // already compatible with persistent_effect's top-level fallback parse.
        // Default toggleable stays true, matching AbstractTogglePower semantics.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "status_effect"),
                 ID_PERSISTENT_EFFECT);

        // stacking_status_effects: list of effects, passive (not toggleable).
        // Effects list passes through verbatim; force toggleable off since
        // the default on persistent_effect is true.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "stacking_status_effects"),
                 ID_PERSISTENT_EFFECT, (json, powerId) -> {
                    json.addProperty("toggleable", false);
                });

        // night_vision: always-on, no HUD icon, no toggle. The toggle UX lives
        // on neoorigins:enhanced_vision; basic night_vision is meant to be
        // permanently on for origins that have it.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "night_vision"),
                 ID_PERSISTENT_EFFECT, (json, powerId) -> {
                    writeSingleEffect(json, "minecraft:night_vision", false);
                    json.addProperty("toggleable", false);
                });

        // glow: toggle, no effect config in legacy JSON.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "glow"),
                 ID_PERSISTENT_EFFECT, (json, powerId) -> {
                    writeSingleEffect(json, "minecraft:glowing", true);
                });

        // water_breathing: apply water_breathing while in water. Icon hidden
        // to match legacy "no HUD indicator" behaviour (original just refilled
        // air supply directly). Not toggleable.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "water_breathing"),
                 ID_PERSISTENT_EFFECT, (json, powerId) -> {
                    writeSingleEffect(json, "minecraft:water_breathing", false);
                    json.addProperty("toggleable", false);
                    com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
                    cond.addProperty("type", "neoorigins:in_water");
                    json.add("condition", cond);
                });
    }

    /** Build an effects: [ { effect, show_icon } ] array on the given JSON. */
    private static void writeSingleEffect(JsonObject json, String effectId, boolean showIcon) {
        com.google.gson.JsonObject spec = new com.google.gson.JsonObject();
        spec.addProperty("effect", effectId);
        spec.addProperty("amplifier", 0);
        spec.addProperty("ambient", true);
        spec.addProperty("show_particles", false);
        spec.addProperty("show_icon", showIcon);
        com.google.gson.JsonArray effects = new com.google.gson.JsonArray();
        effects.add(spec);
        json.add("effects", effects);
    }

    // ── Phase 6: Origins-Classes modifier hook aliases ─────────────────────
    //
    // Each legacy modifier power collapses into action_on_event with an event
    // key + modifier block. Dormant until the legacy Java class is removed
    // (PowerTypes.get(typeId) != null guard in apply()).

    private static final ResourceLocation ID_ACTION_ON_EVENT =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "action_on_event");

    private static void registerModifierHookAliases() {
        // hunger_drain_modifier → action_on_event { event: mod_exhaustion }
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "hunger_drain_modifier"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float m = json.has("multiplier") ? json.get("multiplier").getAsFloat() : 1.0f;
                    json.addProperty("event", "mod_exhaustion");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "multiplication");
                    mod.addProperty("value", m);
                    json.add("modifier", mod);
                    json.remove("multiplier");
                });

        // natural_regen_modifier → action_on_event { event: mod_natural_regen }
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "natural_regen_modifier"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float m = json.has("multiplier") ? json.get("multiplier").getAsFloat() : 1.0f;
                    json.addProperty("event", "mod_natural_regen");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "multiplication");
                    mod.addProperty("value", m);
                    json.add("modifier", mod);
                    json.remove("multiplier");
                });

        // knockback_modifier → action_on_event { event: mod_knockback }
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "knockback_modifier"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float m = json.has("multiplier") ? json.get("multiplier").getAsFloat() : 1.0f;
                    json.addProperty("event", "mod_knockback");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "multiplication");
                    mod.addProperty("value", m);
                    json.add("modifier", mod);
                    json.remove("multiplier");
                });

        // longer_potions → action_on_event { event: mod_potion_duration }
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "longer_potions"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float m = json.has("duration_multiplier") ? json.get("duration_multiplier").getAsFloat() : 1.0f;
                    json.addProperty("event", "mod_potion_duration");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "multiplication");
                    mod.addProperty("value", m);
                    json.add("modifier", mod);
                    json.remove("duration_multiplier");
                });

        // more_animal_loot → action_on_event { event: mod_harvest_drops }
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "more_animal_loot"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float m = json.has("multiplier") ? json.get("multiplier").getAsFloat() : 1.0f;
                    json.addProperty("event", "mod_harvest_drops");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "multiplication");
                    mod.addProperty("value", m);
                    json.add("modifier", mod);
                    json.remove("multiplier");
                });

        // efficient_repairs → action_on_event { event: mod_anvil_cost }
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "efficient_repairs"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float m = json.has("cost_multiplier") ? json.get("cost_multiplier").getAsFloat() : 1.0f;
                    json.addProperty("event", "mod_anvil_cost");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "multiplication");
                    mod.addProperty("value", m);
                    json.add("modifier", mod);
                    json.remove("cost_multiplier");
                });

        // better_enchanting → action_on_event { event: mod_enchant_level }
        // BetterEnchanting is additive (+N levels) — alias emits add_base_early.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "better_enchanting"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    int lv = json.has("bonus_levels") ? json.get("bonus_levels").getAsInt() : 5;
                    json.addProperty("event", "mod_enchant_level");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "add_base");
                    mod.addProperty("value", (float) lv);
                    json.add("modifier", mod);
                    json.remove("bonus_levels");
                });

        // better_crafted_food → action_on_event { event: mod_crafted_food_saturation }
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "better_crafted_food"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float bonus = json.has("saturation_bonus") ? json.get("saturation_bonus").getAsFloat() : 0.5f;
                    json.addProperty("event", "mod_crafted_food_saturation");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "add_base");
                    mod.addProperty("value", bonus);
                    json.add("modifier", mod);
                    json.remove("saturation_bonus");
                });

        // better_bone_meal → action_on_event { event: mod_bonemeal_extra }
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "better_bone_meal"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    int extra = json.has("extra_applications") ? json.get("extra_applications").getAsInt() : 1;
                    json.addProperty("event", "mod_bonemeal_extra");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "add_base");
                    mod.addProperty("value", (float) extra);
                    json.add("modifier", mod);
                    json.remove("extra_applications");
                });

        // teleport_range_modifier → action_on_event { event: mod_teleport_range }
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "teleport_range_modifier"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float m = json.has("multiplier") ? json.get("multiplier").getAsFloat() : 2.0f;
                    json.addProperty("event", "mod_teleport_range");
                    com.google.gson.JsonObject mod = new com.google.gson.JsonObject();
                    mod.addProperty("operation", "multiplication");
                    mod.addProperty("value", m);
                    json.add("modifier", mod);
                    json.remove("multiplier");
                });

        // action_on_hit_taken → action_on_event { event: hit_taken, entity_action: ... }
        // Subactions: teleport → neoorigins:random_teleport,
        //             ignite_attacker → neoorigins:ignite_attacker,
        //             effect_on_attacker → neoorigins:effect_on_attacker.
        // `chance` wraps the chosen action in origins:chance.
        // `min_damage` wraps the action in origins:if_else gated by the
        // neoorigins:hit_taken_amount context-aware condition.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "action_on_hit_taken"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    String act = json.has("action") ? json.get("action").getAsString() : "teleport";
                    float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1.0f;
                    float minDamage = json.has("min_damage") ? json.get("min_damage").getAsFloat() : 0f;
                    com.google.gson.JsonObject inner = new com.google.gson.JsonObject();
                    switch (act) {
                        case "ignite_attacker" -> {
                            inner.addProperty("type", "neoorigins:ignite_attacker");
                            inner.addProperty("ticks", json.has("duration")
                                ? json.get("duration").getAsInt() : 60);
                        }
                        case "effect_on_attacker" -> {
                            inner.addProperty("type", "neoorigins:effect_on_attacker");
                            if (json.has("effect"))
                                inner.addProperty("effect", json.get("effect").getAsString());
                            inner.addProperty("duration",
                                json.has("duration") ? json.get("duration").getAsInt() : 100);
                            inner.addProperty("amplifier",
                                json.has("amplifier") ? json.get("amplifier").getAsInt() : 0);
                        }
                        default -> {          // "teleport" + any unknown → random teleport
                            inner.addProperty("type", "neoorigins:random_teleport");
                            inner.addProperty("horizontal_range", 16.0);
                            inner.addProperty("vertical_range", 8.0);
                        }
                    }
                    com.google.gson.JsonObject root;
                    if (chance < 1.0f) {
                        root = new com.google.gson.JsonObject();
                        root.addProperty("type", "neoorigins:chance");
                        root.addProperty("chance", chance);
                        root.add("action", inner);
                    } else {
                        root = inner;
                    }
                    if (minDamage > 0f) {
                        com.google.gson.JsonObject gate = new com.google.gson.JsonObject();
                        gate.addProperty("type", "neoorigins:if_else");
                        com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
                        cond.addProperty("type", "neoorigins:hit_taken_amount");
                        cond.addProperty("comparison", ">=");
                        cond.addProperty("compare_to", minDamage);
                        gate.add("condition", cond);
                        gate.add("if_action", root);
                        root = gate;
                    }
                    json.addProperty("event", "hit_taken");
                    json.add("entity_action", root);
                    json.remove("action");
                    json.remove("min_damage");
                    json.remove("chance");
                    json.remove("effect");
                    json.remove("duration");
                    json.remove("amplifier");
                });

        // thorns_aura → action_on_event { event: hit_taken, entity_action: damage_attacker }
        // return_ratio maps to damage_attacker.amount_ratio which reads the
        // current HitTakenContext.amount — faithful reflection of the legacy
        // "return N% of incoming damage" behaviour.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "thorns_aura"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float ratio = json.has("return_ratio") ? json.get("return_ratio").getAsFloat() : 0.25f;
                    com.google.gson.JsonObject entityAction = new com.google.gson.JsonObject();
                    entityAction.addProperty("type", "neoorigins:damage_attacker");
                    entityAction.addProperty("amount_ratio", ratio);
                    com.google.gson.JsonObject src = new com.google.gson.JsonObject();
                    src.addProperty("name", "magic");
                    entityAction.add("source", src);
                    json.addProperty("event", "hit_taken");
                    json.add("entity_action", entityAction);
                    json.remove("return_ratio");
                });

        // food_restriction → action_on_event { event: food_eaten, entity_action: if_else(...) }
        // mode=blacklist: cancel if held item is in item_tag.
        // mode=whitelist: cancel if held item is NOT in item_tag.
        // Uses neoorigins:food_item_in_tag — a context-aware condition that
        // reads FoodContext.stack from the ActionContextHolder.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "food_restriction"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    String tag = json.has("item_tag") ? json.get("item_tag").getAsString() : "";
                    boolean whitelist = json.has("mode")
                        && "whitelist".equalsIgnoreCase(json.get("mode").getAsString());

                    com.google.gson.JsonObject itemInTag = new com.google.gson.JsonObject();
                    itemInTag.addProperty("type", "neoorigins:food_item_in_tag");
                    itemInTag.addProperty("tag", tag);

                    com.google.gson.JsonObject matchCond;
                    if (whitelist) {
                        // cancel when item NOT in tag → wrap in origins:not
                        matchCond = new com.google.gson.JsonObject();
                        matchCond.addProperty("type", "neoorigins:not");
                        matchCond.add("condition", itemInTag);
                    } else {
                        matchCond = itemInTag;
                    }

                    com.google.gson.JsonObject cancel = new com.google.gson.JsonObject();
                    cancel.addProperty("type", "neoorigins:cancel_event");
                    com.google.gson.JsonObject gate = new com.google.gson.JsonObject();
                    gate.addProperty("type", "neoorigins:if_else");
                    gate.add("condition", matchCond);
                    gate.add("if_action", cancel);

                    json.addProperty("event", "food_eaten");
                    json.add("entity_action", gate);
                    json.remove("item_tag");
                    json.remove("mode");
                });

        // action_on_kill → action_on_event { event: kill, entity_action: ... }
        // Maps the 3 subactions (restore_health, restore_hunger, grant_effect)
        // to their ActionParser equivalents (origins:heal, origins:feed, origins:apply_effect).
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "action_on_kill"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    String act = json.has("action") ? json.get("action").getAsString() : "restore_health";
                    float amount = json.has("amount") ? json.get("amount").getAsFloat() : 4.0f;
                    com.google.gson.JsonObject entityAction = new com.google.gson.JsonObject();
                    switch (act) {
                        case "restore_hunger" -> {
                            entityAction.addProperty("type", "neoorigins:feed");
                            entityAction.addProperty("food", (int) amount);
                        }
                        case "grant_effect" -> {
                            entityAction.addProperty("type", "neoorigins:apply_effect");
                            if (json.has("effect"))
                                entityAction.addProperty("effect", json.get("effect").getAsString());
                            entityAction.addProperty("duration",
                                json.has("duration") ? json.get("duration").getAsInt() : 200);
                            entityAction.addProperty("amplifier",
                                json.has("amplifier") ? json.get("amplifier").getAsInt() : 0);
                        }
                        default -> {          // "restore_health" + any unknown → heal
                            entityAction.addProperty("type", "neoorigins:heal");
                            entityAction.addProperty("amount", amount);
                        }
                    }
                    json.addProperty("event", "kill");
                    json.add("entity_action", entityAction);
                    json.remove("action");
                    json.remove("amount");
                    json.remove("effect");
                    json.remove("duration");
                    json.remove("amplifier");
                });
    }

    // ── Phase 1: active-ability aliases ────────────────────────────────────
    //
    // Only registers aliases for legacy active types whose behaviour maps
    // cleanly onto the ActionParser DSL. The following legacy classes stay
    // standalone because the DSL can't express their runtime model:
    //   - active_teleport   (no look-direction teleport verb)
    //   - active_recall     (stateful saved position)
    //   - active_place_block (no raycast-and-place verb)
    //   - shadow_orb        (stateful orb with tick loop)
    //   - ground_slam       (AoE on mobs — area_of_effect is players-only)
    //   - tidal_wave        (cone shape — not modelled in area_of_effect)
    //   - gravity_well      (stateful projectile + vortex)
    //   - active_phase      (movement state toggle — not an active ability)
    // Packs using these keep legacy behaviour through the deprecation window.
    // Phase 7 may add raycast/cone/mob-AoE verbs and shrink this list.

    private static final ResourceLocation ID_ACTIVE_ABILITY =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "active_ability");

    private static void registerActiveAbilityAliases() {
        // active_launch: vertical push of `power` blocks/tick upward.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "active_launch"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float y = json.has("power") ? json.get("power").getAsFloat() : 1.5f;
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "neoorigins:add_velocity");
                    action.addProperty("y", y);
                    json.add("entity_action", action);
                    json.remove("power");
                });

        // active_dash: forward velocity in player look direction — translated to
        // add_velocity with the MC-side decision to use `set=false` and approximate
        // look projection via launch-style vertical component removed at runtime.
        // Lossy translation: dash forward uses a dedicated handler in the legacy
        // class; the JSON shape here lands as a horizontal impulse. Packs needing
        // precise behaviour should keep the legacy type until Phase 7.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "active_dash"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float strength = json.has("strength") ? json.get("strength").getAsFloat() : 1.2f;
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "neoorigins:pull_entities");
                    action.addProperty("radius", 0f);
                    action.addProperty("strength", -strength); // push outward = negative pull
                    json.add("entity_action", action);
                    json.remove("strength");
                });

        // active_repulse: AoE outward push on nearby entities.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "repulse"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float radius = json.has("radius") ? json.get("radius").getAsFloat() : 6f;
                    float strength = json.has("strength") ? json.get("strength").getAsFloat() : 1.0f;
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "neoorigins:pull_entities");
                    action.addProperty("radius", radius);
                    action.addProperty("strength", -strength);
                    action.addProperty("include_players", true);
                    json.add("entity_action", action);
                    json.remove("radius");
                    json.remove("strength");
                });

        // active_aoe_effect: applies a status effect to nearby entities in a sphere.
        // The caster is excluded by default — an "offensive" AoE that also harms
        // self is almost never what pack authors want (fire_mage Inferno Burst
        // killed the caster with Instant Damage before this change). Pack
        // authors who explicitly want self-application can set
        // "include_source": true.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "active_aoe_effect"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float radius = json.has("radius") ? json.get("radius").getAsFloat() : 8f;
                    String effect = json.has("effect") ? json.get("effect").getAsString() : "minecraft:weakness";
                    // Accept both "duration" and "duration_ticks" for pack-author
                    // convenience — packs in the repo mix the two.
                    int duration = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt()
                                 : json.has("duration")       ? json.get("duration").getAsInt()
                                 : 200;
                    int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;
                    boolean includeSource = json.has("include_source") && json.get("include_source").getAsBoolean();

                    com.google.gson.JsonObject effectAction = new com.google.gson.JsonObject();
                    effectAction.addProperty("type", "neoorigins:apply_effect");
                    effectAction.addProperty("effect", effect);
                    effectAction.addProperty("duration", duration);
                    effectAction.addProperty("amplifier", amplifier);

                    com.google.gson.JsonObject aoeAction = new com.google.gson.JsonObject();
                    aoeAction.addProperty("type", "neoorigins:area_of_effect");
                    aoeAction.addProperty("radius", radius);
                    aoeAction.addProperty("include_source", includeSource);
                    aoeAction.add("entity_action", effectAction);

                    json.add("entity_action", aoeAction);
                    json.remove("radius");
                    json.remove("effect");
                    json.remove("duration");
                    json.remove("duration_ticks");
                    json.remove("amplifier");
                    json.remove("include_source");
                });

        // active_swap: swap positions with a targeted entity. Legacy picks the
        // entity in the look direction at `range`; the DSL swap_with_entity
        // swaps with the NEAREST in a radius. Semantics differ slightly — packs
        // that must target a specific entity in the crosshair should keep the
        // legacy class until a raycast-based DSL verb exists.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "active_swap"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float range = json.has("range") ? json.get("range").getAsFloat() : 20f;
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "neoorigins:swap_with_entity");
                    action.addProperty("radius", range);
                    json.add("entity_action", action);
                    json.remove("range");
                });

        // active_fireball: legacy shoots 3–4 small fireballs with a random
        // spread. Alias collapses to a single projectile — the multi-shot
        // spread is a lossy simplification. Packs that want the shotgun
        // behaviour should stay on the legacy class.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "active_fireball"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.5f;
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "neoorigins:spawn_projectile");
                    action.addProperty("entity_type", "minecraft:small_fireball");
                    action.addProperty("speed", speed);
                    json.add("entity_action", action);
                    json.remove("speed");
                    json.addProperty("_migration_note",
                        "active_fireball alias shoots a single fireball — legacy fired 3-4 with spread");
                });

        // active_bolt: single wind charge in look direction — clean alias.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "active_bolt"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.2f;
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "neoorigins:spawn_projectile");
                    action.addProperty("entity_type", "minecraft:wind_charge");
                    action.addProperty("speed", speed);
                    json.add("entity_action", action);
                    json.remove("speed");
                });

        // healing_mist: AoE heal on the caster + nearby players. area_of_effect
        // is players-only, matching the legacy Player.class filter exactly.
        register(ResourceLocation.fromNamespaceAndPath("neoorigins", "healing_mist"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float amount = json.has("heal_amount") ? json.get("heal_amount").getAsFloat() : 6f;
                    float radius = json.has("radius") ? json.get("radius").getAsFloat() : 8f;
                    boolean healSelf = !json.has("heal_self") || json.get("heal_self").getAsBoolean();

                    com.google.gson.JsonObject healAction = new com.google.gson.JsonObject();
                    healAction.addProperty("type", "neoorigins:heal");
                    healAction.addProperty("amount", amount);

                    com.google.gson.JsonObject aoeAction = new com.google.gson.JsonObject();
                    aoeAction.addProperty("type", "neoorigins:area_of_effect");
                    aoeAction.addProperty("radius", radius);
                    aoeAction.addProperty("include_source", healSelf);
                    aoeAction.add("entity_action", healAction);

                    json.add("entity_action", aoeAction);
                    json.remove("heal_amount");
                    json.remove("radius");
                    json.remove("heal_self");
                });
    }

    // ── Cross-mod compat aliases ───────────────────────────────────────────
    //
    // Apugli (abandoned at 2.11.0+1.20.4) and legacy Apoli prefixes that
    // pack authors still ship against on 1.21.1 / 26.1 as extracted datapacks.
    // The translator handles origins:/apace: edible_item; this section
    // handles the apugli:/apoli: prefixes which bypass the translator
    // (isOriginsFormat only matches origins:/apace:).
    private static void registerCrossModCompatAliases() {
        ResourceLocation edibleItem = ResourceLocation.fromNamespaceAndPath("neoorigins", "edible_item");
        FieldRemapper remapEdibleItem = (json, powerId) -> {
            // Hoist singular item → items[], tag → tags[], and flatten
            // food_component.{nutrition,saturation_modifier,always_edible} up.
            if (json.has("item") && json.get("item").isJsonPrimitive()) {
                com.google.gson.JsonArray items = new com.google.gson.JsonArray();
                items.add(json.get("item").getAsString());
                json.add("items", items);
                json.remove("item");
            }
            if (json.has("tag") && json.get("tag").isJsonPrimitive()) {
                com.google.gson.JsonArray tags = new com.google.gson.JsonArray();
                tags.add(json.get("tag").getAsString());
                json.add("tags", tags);
                json.remove("tag");
            }
            if (json.has("food_component") && json.get("food_component").isJsonObject()) {
                com.google.gson.JsonObject fc = json.getAsJsonObject("food_component");
                if (fc.has("nutrition"))           json.addProperty("nutrition", fc.get("nutrition").getAsInt());
                if (fc.has("saturation_modifier")) json.addProperty("saturation", fc.get("saturation_modifier").getAsFloat());
                else if (fc.has("saturation"))     json.addProperty("saturation", fc.get("saturation").getAsFloat());
                if (fc.has("always_edible"))       json.addProperty("always_edible", fc.get("always_edible").getAsBoolean());
                json.remove("food_component");
            }
            // effect / action / return_stack dropped (see translator comment).
        };
        register(ResourceLocation.fromNamespaceAndPath("apugli", "edible_item"), edibleItem, remapEdibleItem);
        register(ResourceLocation.fromNamespaceAndPath("apoli",  "edible_item"), edibleItem, remapEdibleItem);

        // Apugli action_on_jump / action_on_target_death already have direct
        // equivalents in our action_on_event dispatch (JUMP / KILL) — a thin
        // event-key injection is all the remap needs. Apugli passes the
        // entity_action verbatim, which matches our action_on_event contract.
        register(ResourceLocation.fromNamespaceAndPath("apugli", "action_on_jump"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> json.addProperty("event", "jump"));
        register(ResourceLocation.fromNamespaceAndPath("apugli", "action_on_target_death"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> json.addProperty("event", "kill"));
    }
}
