package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

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
    public interface FieldRemapper extends BiConsumer<JsonObject, Identifier> {}

    private record Alias(Identifier newType, FieldRemapper remap) {}

    private static final Map<Identifier, Alias> ALIASES = new HashMap<>();
    private static final Set<Identifier> WARNED = new HashSet<>();

    /** Register an alias. Should be called during mod construction / registry setup. */
    public static void register(Identifier oldType, Identifier newType, FieldRemapper remap) {
        ALIASES.put(oldType, new Alias(newType, remap != null ? remap : (j, id) -> {}));
    }

    public static void register(Identifier oldType, Identifier newType) {
        register(oldType, newType, null);
    }

    /**
     * If {@code typeId} is an alias, rewrite it to the new type, remap fields in
     * {@code json}, and return the new type ID. Otherwise returns {@code typeId}
     * unchanged.
     *
     * <p>Fires one deprecation warning per unique old type per boot.
     */
    public static Identifier apply(Identifier typeId, JsonObject json, Identifier powerId) {
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
        registerModifierHookAliases();
        NeoOrigins.LOGGER.debug("[2.0-legacy] power-type alias table initialised ({} entries)", size());
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

    private static final Identifier ID_ATTRIBUTE_MODIFIER =
        Identifier.fromNamespaceAndPath("neoorigins", "attribute_modifier");

    private static void registerAttributeModifierAliases() {
        // less_item_use_slowdown: +speed_multiplier to movement_speed while
        // isUsingItem(). The legacy class also has an item_type filter
        // (bow/shield/any) — the alias drops it because no item-type
        // condition exists yet. Packs filtering by item_type should keep
        // the legacy class until a future DSL verb lands.
        register(Identifier.fromNamespaceAndPath("neoorigins", "less_item_use_slowdown"),
                 ID_ATTRIBUTE_MODIFIER, (json, powerId) -> {
                    float mult = json.has("speed_multiplier") ? json.get("speed_multiplier").getAsFloat() : 0.5f;
                    boolean filteringByItem = json.has("item_type")
                        && !"any".equalsIgnoreCase(json.get("item_type").getAsString());

                    json.addProperty("attribute", "minecraft:movement_speed");
                    json.addProperty("amount", mult);
                    json.addProperty("operation", "add_multiplied_base");

                    com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
                    cond.addProperty("type", "origins:using_item");
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

    private static final Identifier ID_PERSISTENT_EFFECT =
        Identifier.fromNamespaceAndPath("neoorigins", "persistent_effect");

    private static void registerPersistentEffectAliases() {
        // status_effect: single MobEffect with amplifier/ambient/show_particles —
        // already compatible with persistent_effect's top-level fallback parse.
        // Default toggleable stays true, matching AbstractTogglePower semantics.
        register(Identifier.fromNamespaceAndPath("neoorigins", "status_effect"),
                 ID_PERSISTENT_EFFECT);

        // stacking_status_effects: list of effects, passive (not toggleable).
        // Effects list passes through verbatim; force toggleable off since
        // the default on persistent_effect is true.
        register(Identifier.fromNamespaceAndPath("neoorigins", "stacking_status_effects"),
                 ID_PERSISTENT_EFFECT, (json, powerId) -> {
                    json.addProperty("toggleable", false);
                });

        // night_vision: toggle, no effect config in legacy JSON.
        register(Identifier.fromNamespaceAndPath("neoorigins", "night_vision"),
                 ID_PERSISTENT_EFFECT, (json, powerId) -> {
                    writeSingleEffect(json, "minecraft:night_vision", false);
                });

        // glow: toggle, no effect config in legacy JSON.
        register(Identifier.fromNamespaceAndPath("neoorigins", "glow"),
                 ID_PERSISTENT_EFFECT, (json, powerId) -> {
                    writeSingleEffect(json, "minecraft:glowing", true);
                });

        // water_breathing: apply water_breathing while in water. Icon hidden
        // to match legacy "no HUD indicator" behaviour (original just refilled
        // air supply directly). Not toggleable.
        register(Identifier.fromNamespaceAndPath("neoorigins", "water_breathing"),
                 ID_PERSISTENT_EFFECT, (json, powerId) -> {
                    writeSingleEffect(json, "minecraft:water_breathing", false);
                    json.addProperty("toggleable", false);
                    com.google.gson.JsonObject cond = new com.google.gson.JsonObject();
                    cond.addProperty("type", "origins:in_water");
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

    private static final Identifier ID_ACTION_ON_EVENT =
        Identifier.fromNamespaceAndPath("neoorigins", "action_on_event");

    private static void registerModifierHookAliases() {
        // hunger_drain_modifier → action_on_event { event: mod_exhaustion }
        register(Identifier.fromNamespaceAndPath("neoorigins", "hunger_drain_modifier"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "natural_regen_modifier"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "knockback_modifier"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "longer_potions"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "more_animal_loot"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "efficient_repairs"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "better_enchanting"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "better_crafted_food"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "better_bone_meal"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "teleport_range_modifier"),
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
        // `chance` wraps the chosen action in origins:chance; `min_damage` is
        // not modelled here (would need a context-aware condition) — packs that
        // relied on the threshold should adjust manually post-migration.
        register(Identifier.fromNamespaceAndPath("neoorigins", "action_on_hit_taken"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    String act = json.has("action") ? json.get("action").getAsString() : "teleport";
                    float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1.0f;
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
                        root.addProperty("type", "origins:chance");
                        root.addProperty("chance", chance);
                        root.add("action", inner);
                    } else {
                        root = inner;
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
        // return_ratio becomes a flat amount (default 1.0); packs wanting ratio-of-damage
        // behaviour should keep using the legacy class. The alias gives a sensible
        // constant-damage fallback once the Java class is removed.
        register(Identifier.fromNamespaceAndPath("neoorigins", "thorns_aura"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    float ratio = json.has("return_ratio") ? json.get("return_ratio").getAsFloat() : 0.25f;
                    com.google.gson.JsonObject entityAction = new com.google.gson.JsonObject();
                    entityAction.addProperty("type", "neoorigins:damage_attacker");
                    // Approximate: reflect ~4 base HP × ratio at minimum, capped at 20.
                    entityAction.addProperty("amount", Math.min(20f, Math.max(0.5f, ratio * 4f)));
                    com.google.gson.JsonObject src = new com.google.gson.JsonObject();
                    src.addProperty("name", "magic");
                    entityAction.add("source", src);
                    json.addProperty("event", "hit_taken");
                    json.add("entity_action", entityAction);
                    json.remove("return_ratio");
                });

        // food_restriction → action_on_event { event: food_eaten, condition: item tag, action: cancel }
        // Uses the origins `in_tag` item condition via a condition on the item
        // (not directly supported — context-item conditions aren't wired yet).
        // For now, the alias maps to a cancel-always pairing with a comment.
        // Pack authors using this should keep the legacy class until Phase 6.6.
        register(Identifier.fromNamespaceAndPath("neoorigins", "food_restriction"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    com.google.gson.JsonObject entityAction = new com.google.gson.JsonObject();
                    entityAction.addProperty("type", "neoorigins:cancel_event");
                    json.addProperty("event", "food_eaten");
                    json.add("entity_action", entityAction);
                    // Preserve the tag/mode hints as a comment so authors can
                    // convert by hand — the generic alias can't express
                    // "cancel only when the held item matches tag X" yet.
                    json.addProperty("_migration_note",
                        "food_restriction alias cancels ALL food — re-author with an item-tag condition");
                });

        // action_on_kill → action_on_event { event: kill, entity_action: ... }
        // Maps the 3 subactions (restore_health, restore_hunger, grant_effect)
        // to their ActionParser equivalents (origins:heal, origins:feed, origins:apply_effect).
        register(Identifier.fromNamespaceAndPath("neoorigins", "action_on_kill"),
                 ID_ACTION_ON_EVENT, (json, powerId) -> {
                    String act = json.has("action") ? json.get("action").getAsString() : "restore_health";
                    float amount = json.has("amount") ? json.get("amount").getAsFloat() : 4.0f;
                    com.google.gson.JsonObject entityAction = new com.google.gson.JsonObject();
                    switch (act) {
                        case "restore_hunger" -> {
                            entityAction.addProperty("type", "origins:feed");
                            entityAction.addProperty("food", (int) amount);
                        }
                        case "grant_effect" -> {
                            entityAction.addProperty("type", "origins:apply_effect");
                            if (json.has("effect"))
                                entityAction.addProperty("effect", json.get("effect").getAsString());
                            entityAction.addProperty("duration",
                                json.has("duration") ? json.get("duration").getAsInt() : 200);
                            entityAction.addProperty("amplifier",
                                json.has("amplifier") ? json.get("amplifier").getAsInt() : 0);
                        }
                        default -> {          // "restore_health" + any unknown → heal
                            entityAction.addProperty("type", "origins:heal");
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

    private static final Identifier ID_ACTIVE_ABILITY =
        Identifier.fromNamespaceAndPath("neoorigins", "active_ability");

    private static void registerActiveAbilityAliases() {
        // active_launch: vertical push of `power` blocks/tick upward.
        register(Identifier.fromNamespaceAndPath("neoorigins", "active_launch"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float y = json.has("power") ? json.get("power").getAsFloat() : 1.5f;
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "origins:add_velocity");
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "active_dash"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "repulse"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "active_aoe_effect"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float radius = json.has("radius") ? json.get("radius").getAsFloat() : 8f;
                    String effect = json.has("effect") ? json.get("effect").getAsString() : "minecraft:weakness";
                    int duration = json.has("duration") ? json.get("duration").getAsInt() : 200;
                    int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;

                    com.google.gson.JsonObject effectAction = new com.google.gson.JsonObject();
                    effectAction.addProperty("type", "origins:apply_effect");
                    effectAction.addProperty("effect", effect);
                    effectAction.addProperty("duration", duration);
                    effectAction.addProperty("amplifier", amplifier);

                    com.google.gson.JsonObject aoeAction = new com.google.gson.JsonObject();
                    aoeAction.addProperty("type", "origins:area_of_effect");
                    aoeAction.addProperty("radius", radius);
                    aoeAction.add("entity_action", effectAction);

                    json.add("entity_action", aoeAction);
                    json.remove("radius");
                    json.remove("effect");
                    json.remove("duration");
                    json.remove("amplifier");
                });

        // active_swap: swap positions with a targeted entity. Legacy picks the
        // entity in the look direction at `range`; the DSL swap_with_entity
        // swaps with the NEAREST in a radius. Semantics differ slightly — packs
        // that must target a specific entity in the crosshair should keep the
        // legacy class until a raycast-based DSL verb exists.
        register(Identifier.fromNamespaceAndPath("neoorigins", "active_swap"),
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
        register(Identifier.fromNamespaceAndPath("neoorigins", "active_fireball"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.5f;
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "origins:spawn_projectile");
                    action.addProperty("entity_type", "minecraft:small_fireball");
                    action.addProperty("speed", speed);
                    json.add("entity_action", action);
                    json.remove("speed");
                    json.addProperty("_migration_note",
                        "active_fireball alias shoots a single fireball — legacy fired 3-4 with spread");
                });

        // active_bolt: single wind charge in look direction — clean alias.
        register(Identifier.fromNamespaceAndPath("neoorigins", "active_bolt"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.2f;
                    com.google.gson.JsonObject action = new com.google.gson.JsonObject();
                    action.addProperty("type", "origins:spawn_projectile");
                    action.addProperty("entity_type", "minecraft:wind_charge");
                    action.addProperty("speed", speed);
                    json.add("entity_action", action);
                    json.remove("speed");
                });

        // healing_mist: AoE heal on the caster + nearby players. area_of_effect
        // is players-only, matching the legacy Player.class filter exactly.
        register(Identifier.fromNamespaceAndPath("neoorigins", "healing_mist"),
                 ID_ACTIVE_ABILITY, (json, powerId) -> {
                    float amount = json.has("heal_amount") ? json.get("heal_amount").getAsFloat() : 6f;
                    float radius = json.has("radius") ? json.get("radius").getAsFloat() : 8f;
                    boolean healSelf = !json.has("heal_self") || json.get("heal_self").getAsBoolean();

                    com.google.gson.JsonObject healAction = new com.google.gson.JsonObject();
                    healAction.addProperty("type", "origins:heal");
                    healAction.addProperty("amount", amount);

                    com.google.gson.JsonObject aoeAction = new com.google.gson.JsonObject();
                    aoeAction.addProperty("type", "origins:area_of_effect");
                    aoeAction.addProperty("radius", radius);
                    aoeAction.addProperty("include_source", healSelf);
                    aoeAction.add("entity_action", healAction);

                    json.add("entity_action", aoeAction);
                    json.remove("heal_amount");
                    json.remove("radius");
                    json.remove("heal_self");
                });
    }
}
