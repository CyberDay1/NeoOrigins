package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.EffectImmunityPower;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec.Kind;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static, class-load-time field-spec table for built-in power types — the power
 * analogue of {@link com.cyberday1.neoorigins.compat.action.BuiltinActions}, and
 * the second half of the registry refactor (powers, after the action/condition
 * verbs).
 *
 * <p><b>What this is NOT.</b> Powers already deserialize through their own
 * {@code Codec<Config>} via {@code PowerDataManager}; this table does
 * <em>not</em> touch that parse path, any power {@code Codec}, or any
 * {@code Config} record. It is <b>metadata only</b>: the per-power
 * {@link FieldSpec} list that drives the JSON schema, the doc reference tables,
 * and both editors' forms — data that historically lived in four hand-maintained
 * side-tables ({@code power.schema.json}, {@code field_docs.json},
 * {@code EnumHints}, {@code NeoOriginsConfig} ranges) that silently drift.
 * Consolidating it here, beside a drift audit, is the permanent fix.
 *
 * <p><b>Why static, not the live registry?</b> Same reason as
 * {@code BuiltinActions}: {@link PowerTypes#get} reads the
 * {@code neoorigins:power_type} registry, which only populates after
 * {@code NewRegistryEvent} fires — i.e. never in the headless harnesses
 * ({@code compatTest}, {@code goldenMaster}, {@code schemaFormCheck}). This
 * table is available the moment the class loads, with or without a running
 * NeoForge, so it can back {@link com.cyberday1.neoorigins.power.schemaform.FormModel}
 * and the {@code auditPowerFieldSpecs} drift guard headlessly. Each descriptor
 * also carries its power's {@code Class<?>} so the audit can resolve the
 * {@code Config} record via {@link com.cyberday1.neoorigins.power.schemaform.PowerConfigClassResolver}
 * without the live registry.
 *
 * <p>Migration is power-by-power (mirrors the verb migration): each entry added
 * here lets {@code FormModel.forPower} prefer the declared spec over the
 * schema-branch / codec-reflection fallback, gated on the golden master and
 * {@code SchemaFormCheck} staying green.
 */
public final class BuiltinPowers {

    private BuiltinPowers() {}

    /**
     * One built-in power's declarative metadata.
     *
     * @param id         canonical {@code neoorigins:<type>} id (registry key).
     * @param powerClass the concrete {@code PowerType<C>} class — carried so the
     *                   drift audit can resolve the {@code Config} record headlessly
     *                   (the live registry is empty in the harnesses).
     * @param fields     declared config fields, in author-facing order. Empty for
     *                   marker-only powers (no config to author).
     */
    public record PowerSpec(Identifier id, Class<?> powerClass, List<FieldSpec> fields) {
        public PowerSpec {
            fields = List.copyOf(fields);
        }
    }

    /** Insertion-ordered so registration/audit output is deterministic. */
    private static final Map<Identifier, PowerSpec> DESCRIPTORS = new LinkedHashMap<>();
    /** Canonical {@code "neoorigins:<type>"} string → descriptor, for fast lookup. */
    private static final Map<String, PowerSpec> BY_KEY = new java.util.HashMap<>();

    /**
     * Schema {@code pattern} for resource-location-shaped STRING fields
     * ({@code namespace:path}). Restores the regex hint the hand-written schema
     * validated these fields against; surfaced by the web editor as a format hint.
     */
    private static final String RESOURCE_LOCATION_PATTERN = "^[a-z0-9_.-]+:[a-z0-9_./\\-]+$";

    private static void define(String path, Class<?> powerClass, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        PowerSpec spec = new PowerSpec(id, powerClass, fields);
        DESCRIPTORS.put(id, spec);
        BY_KEY.put(id.toString(), spec);
    }

    static {
        // ── Group M — marker-only powers ────────────────────────────────────
        // No config fields: the empty form is correct (nothing to author). These
        // are the lowest-risk entries — registering them only makes the drift
        // audit count them; FormModel still resolves an empty field list either
        // way (declared-empty here, or codec-reflection-empty before), so this is
        // behavior-neutral. The marker set is exactly SchemaFormCheck
        // .auditPowerFormCoverage's "marker-only" list; each Config is
        // `record Config(String type)` (type is internal plumbing, not a field).
        define("cobweb_affinity",          CobwebAffinityPower.class,         List.of());
        define("ender_gaze_immunity",      EnderGazeImmunityPower.class,      List.of());
        define("flight",                   FlightPower.class,                 List.of());
        define("ignore_water",             IgnoreWaterPower.class,            List.of());
        define("natural_glide",            NaturalGlidePower.class,           List.of());
        define("no_natural_regen",         NoNaturalRegenPower.class,         List.of());
        define("no_projectile_divergence", NoProjectileDivergencePower.class, List.of());
        define("underwater_mining_speed",  UnderwaterMiningSpeedPower.class,  List.of());
        define("wall_climbing",            WallClimbingPower.class,           List.of());
        define("water_breathing",          WaterBreathingPower.class,         List.of());

        // ── Group R — record-reflectable single-knob powers ─────────────────
        // Plain-Config powers whose one user-facing field reflection already
        // sees by name+kind, but whose codec `optionalFieldOf` default and field
        // doc previously lived in the side-tables (field_docs.json). Declaring
        // the FieldSpec here carries name + optionality + default + doc in one
        // place; required=false mirrors the codec's optionalFieldOf, and the
        // collapsed field_docs.json entry is now sourced from the spec (see
        // SchemaFormCheck.auditFieldDocs / FormModel.enrich). Behavior-neutral:
        // the power still deserializes through its own Codec<Config>, untouched.
        define("break_speed_modifier", BreakSpeedModifierPower.class, List.of(
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(2.0).doc("Mining-speed multiplier; stacks multiplicatively with other instances; default 2.0.")));
        define("crop_harvest_bonus", CropHarvestBonusPower.class, List.of(
            new FieldSpec("extra_drops", Kind.INTEGER, false)
                .def(1).doc("Extra copies of the block's own loot when breaking crops/logs (default 1).")));
        define("dodge_chance", DodgeChancePower.class, List.of(
            new FieldSpec("chance", Kind.NUMBER, false)
                .def(0.15).doc("Probability 0.0-1.0 to fully cancel an incoming damage event (default 0.15).")));
        define("entity_group", EntityGroupPower.class, List.of(
            new FieldSpec("group", Kind.STRING, false)
                .def("undefined").doc("Mob classification: undead, arthropod, water, or undefined (changes effect/enchant interactions).")));
        define("hide_hud_bar", HideHudBarPower.class, List.of(
            new FieldSpec("bar", Kind.STRING, false)
                .def("hunger").doc("Which HUD bar to hide: hunger/food or air/oxygen/breath (default hunger).")));
        define("item_magnetism", ItemMagnetismPower.class, List.of(
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(4.0).doc("Blocks around the player within which dropped items are pulled in (default 4.0).")));
        define("lava_vision", LavaVisionPower.class, List.of(
            new FieldSpec("strength", Kind.NUMBER, false)
                .def(3.0).doc("Lava fog distance multiplier; higher sees farther in lava (default 3.0).")));
        define("more_smoker_xp", MoreSmokerXpPower.class, List.of(
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(2.0).doc("Multiplier for smoker-cooking XP (default 2.0).")));
        define("sneaky", SneakyPower.class, List.of(
            new FieldSpec("detection_multiplier", Kind.NUMBER, false)
                .def(0.3).doc("Mob detection range multiplier; lower = sneakier (default 0.3).")));
        define("tamed_potion_diffusal", TamedPotionDiffusalPower.class, List.of(
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(16.0).doc("Block radius for sharing positive potion effects with tamed animals.")));
        define("tree_felling", TreeFellingPower.class, List.of(
            new FieldSpec("max_blocks", Kind.INTEGER, false)
                .def(64).doc("Maximum connected log blocks broken in one chop (default 64).")));
        define("twin_breeding", TwinBreedingPower.class, List.of(
            new FieldSpec("chance", Kind.NUMBER, false)
                .def(1.0).doc("Probability 0.0-1.0 of an extra baby when animals breed (default 1.0).")));

        // ── Group R (cont.) — multi-knob record-reflectable powers ──────────
        // Same contract as the single-knob block above, just with several
        // fields. Each FieldSpec mirrors the codec's `optionalFieldOf` default
        // (required=false) or its bare `fieldOf` (required=true); docs are the
        // collapsed field_docs.json entries. None of these had a structured
        // power.schema.json branch (their fields were reflection + field_docs
        // only), so registering them carries name+optionality+default+doc in one
        // place and the field_docs.json entry can be dropped. Behavior-neutral:
        // the power still deserializes through its own Codec<Config>, untouched.
        define("bare_hand_tool", BareHandToolPower.class, List.of(
            new FieldSpec("tool", Kind.STRING, false)
                .def("minecraft:stone_pickaxe").doc("Vanilla tool item id the empty hand emulates for tier/break speed; default stone_pickaxe.")));
        define("breath_out_of_fluid", BreathOutOfFluidPower.class, List.of(
            new FieldSpec("fluid", Kind.STRING, false)
                .def("water").doc("Fluid the player must stay in to breathe; drying on land drains air."),
            new FieldSpec("drain_rate", Kind.INTEGER, false)
                .def(40).doc("Ticks between each air drain while out of the fluid (20 = 1s).")));
        define("burn", BurnPower.class, List.of(
            new FieldSpec("interval", Kind.INTEGER, false)
                .def(20).doc("Ticks between each fire application; <=0 disables (default 20)."),
            new FieldSpec("burn_duration", Kind.INTEGER, false)
                .def(100).doc("Ticks of fire set on the player each application (20 = 1s; default 100).")));
        define("command_pack", CommandPackPower.class, List.of(
            new FieldSpec("range", Kind.NUMBER, false)
                .def(32.0).doc("Max block distance of the look-targeted entity your tamed mobs attack (default 32)."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(40).doc("Ticks before this ability can be triggered again (default 40).")));
        define("craft_amount_bonus", CraftAmountBonusPower.class, List.of(
            new FieldSpec("output_item", Kind.STRING, false)
                .def("minecraft:oak_planks").doc("Item id whose crafting triggers the bonus (default oak_planks)."),
            new FieldSpec("bonus_count", Kind.INTEGER, false)
                .def(4).doc("Extra copies of the output added per craft (default 4; skipped if <=0).")));
        define("crop_growth_accelerator", CropGrowthAcceleratorPower.class, List.of(
            new FieldSpec("radius", Kind.INTEGER, false)
                .def(4).doc("Cubic block radius around the player scanned for crops (default 4)."),
            new FieldSpec("tick_interval", Kind.INTEGER, false)
                .def(40).doc("Ticks between growth passes; <=0 disables (default 40)."),
            new FieldSpec("growths_per_interval", Kind.INTEGER, false)
                .def(1).doc("Number of random nearby crops bone-mealed each interval (default 1).")));
        define("elytra_boost", ElytraBoostPower.class, List.of(
            new FieldSpec("strength", Kind.NUMBER, false)
                .def(1.5).doc("Multiplier on the forward impulse applied while elytra gliding (default 1.5)."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(40).doc("Ticks before the boost can be triggered again (default 40).")));
        define("exhaustion_filter", ExhaustionFilterPower.class, List.of(
            new FieldSpec("sources", Kind.ARRAY, false)
                .doc("Exhaustion sources to suppress, e.g. sprint, mining (default [sprint]).")));
        define("extra_inventory", ExtraInventoryPower.class, List.of(
            new FieldSpec("size", Kind.INTEGER, false)
                .def(9).doc("Slot count, rounded to a multiple of 9 (min 9, max 54; default 9)."),
            new FieldSpec("drop_on_death", Kind.BOOLEAN, false)
                .def(false).doc("If true the extra inventory drops on death instead of persisting (default false).")));
        define("fortune_when_effect", FortuneWhenEffectPower.class, List.of(
            new FieldSpec("effect", Kind.STRING, false)
                .def("minecraft:luck").doc("Mob effect that must be active on the player for the Fortune bonus."),
            new FieldSpec("level", Kind.INTEGER, false)
                .def(2).doc("Virtual Fortune level rolled via the vanilla ore-drops formula."),
            new FieldSpec("target", Kind.STRING, false)
                .def("#c:ores").doc("Block tag the bonus applies to (default #c:ores; ancient debris excluded).")));
        define("horde_regen", HordeRegenPower.class, List.of(
            new FieldSpec("heal_amount", Kind.NUMBER, false)
                .def(1.0).doc("Health restored to each eligible tamed mob per interval (default 1.0)."),
            new FieldSpec("interval_ticks", Kind.INTEGER, false)
                .def(120).doc("Ticks between each horde-healing pass (default 120 = 6s)."),
            new FieldSpec("combat_cooldown_ticks", Kind.INTEGER, false)
                .def(100).doc("Ticks a tamed mob must avoid damage before it can heal (default 100).")));
        define("invulnerability", InvulnerabilityPower.class, List.of(
            new FieldSpec("damage_types", Kind.ARRAY, false)
                .doc("Damage-type ids (e.g. minecraft:fall) whose damage is cancelled."),
            new FieldSpec("damage_tags", Kind.ARRAY, false)
                .doc("Damage-type tag ids (e.g. minecraft:is_fire) whose damage is cancelled."),
            new FieldSpec("msg_ids", Kind.ARRAY, false)
                .doc("Vanilla damage msgId strings (e.g. inFire, fall) whose damage is cancelled.")));

        // ── Group R (cont.) — batch B ───────────────────────────────────────
        // More primitive/enum/list records. A bare `fieldOf` (no default) is a
        // hard-required field → required=true (e.g. light_level_effect.effect,
        // low_hp_threshold.effects); `Optional<>`-typed components stay
        // required=false with no default (modify_food_nutrition.food_item/tag,
        // no_slowdown.block_tag). enhanced_vision / keep_inventory / mount each
        // keep their power.schema.json branch, which the spec stays consistent
        // with (every schema field appears here); the field_docs.json entries
        // collapse onto the specs.
        define("enhanced_vision", EnhancedVisionPower.class, List.of(
            new FieldSpec("exposure", Kind.NUMBER, false)
                .def(0.7).range(0.0, 1.0).doc("Brightness 0-1 in darkness; client currently uses ~0.7 (default 0.7).")));
        define("keep_inventory", KeepInventoryPower.class, List.of(
            new FieldSpec("slots", Kind.ARRAY, false)
                .doc("Slot categories kept: hotbar, main, armor, offhand, or * for all (default *)."),
            new FieldSpec("items", Kind.ARRAY, false)
                .doc("Item ids to keep on death; empty (with empty tags) keeps everything."),
            new FieldSpec("tags", Kind.ARRAY, false)
                .doc("Item tag ids whose matching items are kept on death.")));
        define("less_item_use_slowdown", LessItemUseSlowdownPower.class, List.of(
            new FieldSpec("item_type", Kind.STRING, false)
                .def("any").doc("Item being used that gets reduced slowdown: any, bow, shield, or an id substring."),
            new FieldSpec("speed_multiplier", Kind.NUMBER, false)
                .def(0.5).doc("Movement-speed bonus while using the item, as a fraction of base (default 0.5).")));
        define("light_level_effect", LightLevelEffectPower.class, List.of(
            new FieldSpec("max_light_level", Kind.INTEGER, false)
                .def(4).doc("Highest light level at which the effect still applies (default 4)."),
            new FieldSpec("effect", Kind.STRING, true)
                .doc("Mob effect id applied at/below the light threshold (required)."),
            new FieldSpec("amplifier", Kind.INTEGER, false)
                .def(0).doc("Effect amplifier level, 0 = level I (default 0)."),
            new FieldSpec("ambient", Kind.BOOLEAN, false)
                .def(true).doc("Whether the applied effect is ambient (fainter particles; default true)."),
            new FieldSpec("show_particles", Kind.BOOLEAN, false)
                .def(false).doc("Whether the effect's ambient particles are shown (default false)."),
            new FieldSpec("show_icon", Kind.BOOLEAN, false)
                .def(false).doc("Whether the effect's HUD icon is shown (default false).")));
        define("low_hp_threshold", LowHPThresholdPower.class, List.of(
            new FieldSpec("threshold", Kind.NUMBER, false)
                .def(0.5).doc("Health fraction below which the effects activate (0.5 = 50%; default 0.5)."),
            new FieldSpec("effects", Kind.ARRAY, true)
                .doc("List of {effect, amplifier} entries applied while below the threshold.")));
        define("mobs_ignore_player", MobsIgnorePlayerPower.class, List.of(
            new FieldSpec("entity_types", Kind.ARRAY, false)
                .doc("Entity ids or #tags of mobs that never aggro or target this player. Empty/omitted = matches every mob."),
            new FieldSpec("passive", Kind.BOOLEAN, false)
                .def(false).doc("When true, the ignore is unconditional — even hitting the mob does not provoke retaliation. Default false (the mob may target back briefly after being hit).")));
        define("modify_food_nutrition", ModifyFoodNutritionPower.class, List.of(
            new FieldSpec("nutrition", Kind.INTEGER, false)
                .def(1).doc("Hunger points matching food gives, saturation scaled with it (default 1)."),
            new FieldSpec("food_item", Kind.STRING, false)
                .doc("Optional item id; only this food's nutrition is overridden."),
            new FieldSpec("food_tag", Kind.STRING, false)
                .doc("Optional item tag; only matching foods are overridden.")));
        define("mount", MountPower.class, List.of(
            new FieldSpec("range", Kind.NUMBER, false)
                .def(5.0).doc("Max distance in blocks to raycast for the entity to mount (default 5)."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(100).doc("Ticks before the mount ability can be reused (100 = 5s)."),
            new FieldSpec("hunger_cost", Kind.INTEGER, false)
                .def(0).doc("Hunger consumed each time an entity is mounted."),
            new FieldSpec("allow_players", Kind.BOOLEAN, false)
                .def(true).doc("Whether this power can mount other players (default true)."),
            new FieldSpec("allow_mobs", Kind.BOOLEAN, false)
                .def(true).doc("Whether this power can mount mobs (default true)."),
            new FieldSpec("block_bosses", Kind.BOOLEAN, false)
                .def(true).doc("Prevent mounting boss mobs like the Ender Dragon or Wither (default true)."),
            new FieldSpec("mount_position", Kind.STRING, false)
                .def("centered").doc("Where the rider sits: 'centered' (on top) or 'shoulder' (offset to one side).")));
        define("no_mob_spawns_nearby", NoMobSpawnsNearbyPower.class, List.of(
            new FieldSpec("radius", Kind.INTEGER, false)
                .def(24).doc("Block radius around the player where natural spawning is suppressed (default 24). Vanilla already blocks MONSTER-category natural spawns within 24 blocks of any player, so use a value above 24 to extend the safe zone meaningfully."),
            new FieldSpec("categories", Kind.ARRAY, false)
                .doc("Mob groups blocked: monster, creature, ambient, water_creature, or all (default monster).")));
        define("no_slowdown", NoSlowdownPower.class, List.of(
            new FieldSpec("block_tag", Kind.STRING, false)
                .doc("Block tag to limit slowdown immunity to; omit for immunity to all slowdown.")));

        // ── Group R (cont.) — batch C ───────────────────────────────────────
        // Final batch of primitive/enum/list records. summon_minion and
        // tame_mob each kept a power.schema.json branch that only restated
        // their plain config + the universal name/description/hidden wrapper
        // fields; the spec fully represents the real config, so those branches
        // collapse here too (see batch B). split_max_h_p / levels_per_h_p /
        // max_bonus_h_p use the camel→snake form the reflection fallback and
        // field_docs already used (HP → h_p), so the editor field name is
        // byte-identical to today. mob equipment slots (head/chest/...) are
        // Optional<String> in the codec → required=false, no default.
        define("modify_lava_speed", ModifyLavaSpeedPower.class, List.of(
            new FieldSpec("operation", Kind.STRING, false)
                .def("addition").doc("How value combines with the vanilla lava factor: addition, multiply_base, multiply_total."),
            new FieldSpec("value", Kind.NUMBER, true)
                .doc("Amount applied to lava movement speed (addition ~0.4 ≈ walking pace in lava).")));
        define("overlay", OverlayPower.class, List.of(
            new FieldSpec("texture", Kind.STRING, true)
                .doc("Resource location of the full-screen texture rendered over the view."),
            new FieldSpec("strength", Kind.NUMBER, false)
                .def(1.0).doc("Overlay opacity, 0.0 invisible to 1.0 opaque (default 1.0).")));
        define("phantom_form", PhantomFormPower.class, List.of(
            new FieldSpec("invisibility", Kind.BOOLEAN, false)
                .def(true).doc("If true the player gains Invisibility while in phantom form (default true)."),
            new FieldSpec("no_gravity", Kind.BOOLEAN, false)
                .def(true).doc("If true gravity is disabled so the player can free-fly (default true).")));
        define("projectile_immunity", ProjectileImmunityPower.class, List.of(
            new FieldSpec("projectile_types", Kind.ARRAY, false)
                .doc("Projectiles blocked: arrow, fireball, trident, all, or an entity id (default arrow)."),
            new FieldSpec("chance", Kind.NUMBER, false)
                .def(1.0).range(0.0, 1.0).doc("Probability 0.0-1.0 an incoming matching projectile is negated (default 1.0)."),
            new FieldSpec("teleport", Kind.BOOLEAN, false)
                .def(false).doc("If true a successful dodge triggers a short random teleport (default false)."),
            new FieldSpec("teleport_range", Kind.INTEGER, false)
                .def(16).doc("Max teleport distance in blocks on a dodge when enabled (default 16).")));
        define("quality_equipment", QualityEquipmentPower.class, List.of(
            new FieldSpec("bonus_mining_speed", Kind.NUMBER, false)
                .def(0.25).doc("Fraction of base mining speed added to crafted tools (default 0.25)."),
            new FieldSpec("bonus_attack_damage", Kind.NUMBER, false)
                .def(0.20).doc("Fraction of base attack damage added to crafted weapons (default 0.20)."),
            new FieldSpec("bonus_armor_toughness", Kind.NUMBER, false)
                .def(1.0).doc("Flat armor toughness added to crafted/smithed armor (default 1.0)."),
            new FieldSpec("durability_multiplier", Kind.NUMBER, false)
                .def(0.10).doc("Fraction of base durability added to crafted items (default 0.10).")));
        define("rare_wandering_loot", RareWanderingLootPower.class, List.of(
            new FieldSpec("master_slots", Kind.INTEGER, false)
                .def(3).doc("Random master-tier villager trades added to wandering traders (default 3)."),
            new FieldSpec("treasure_chance", Kind.NUMBER, false)
                .def(0.25).doc("Probability 0.0-1.0 a rare treasure trade is also offered (default 0.25).")));
        define("scare_entities", ScareEntitiesPower.class, List.of(
            new FieldSpec("entity_types", Kind.ARRAY, false)
                .doc("Entity ids or #tags that flee the player within 8 blocks.")));
        define("shader", ShaderPower.class, List.of(
            new FieldSpec("shader", Kind.STRING, true)
                .doc("Resource location of the post shader to apply (e.g. minecraft:spider).")));
        define("shadow_orb", ShadowOrbPower.class, List.of(
            new FieldSpec("max_orbs", Kind.INTEGER, false)
                .def(4).doc("Max simultaneous orbs; placing past this removes the oldest (default 4)."),
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(28.0).doc("Block radius around each orb given Darkness+Blindness (default 28)."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(100).doc("Ticks before the orb ability can be reused (20 = 1s; default 100)."),
            new FieldSpec("tick_interval", Kind.INTEGER, false)
                .def(20).doc("Ticks between each darkness pulse from orbs (20 = 1s; default 20)."),
            new FieldSpec("hunger_cost", Kind.INTEGER, false)
                .def(0).doc("Hunger points consumed per activation (default 0).")));
        define("slime_death_save", SlimeDeathSavePower.class, List.of(
            new FieldSpec("moisture_threshold", Kind.NUMBER, false)
                .def(0.75).doc("Min slime moisture (0-1) to split instead of dying (default 0.75)."),
            new FieldSpec("teleport_distance", Kind.INTEGER, false)
                .def(50).doc("Blocks teleported in a random direction on split (default 50)."),
            new FieldSpec("teleport_y_range", Kind.INTEGER, false)
                .def(10).doc("Max +/- Y offset applied to the split teleport (default 10)."),
            new FieldSpec("split_max_h_p", Kind.NUMBER, false)
                .def(4.0).doc("Max HP set right after splitting, in half-hearts (default 4.0)."),
            new FieldSpec("recovery_ticks", Kind.INTEGER, false)
                .def(2400).doc("Ticks for reduced max HP to ease back to normal (20 = 1s; default 2400).")));
        define("slime_level_hp", SlimeLevelHPPower.class, List.of(
            new FieldSpec("levels_per_h_p", Kind.INTEGER, false)
                .def(10).doc("Experience levels needed per +1 max HP half-heart (default 10)."),
            new FieldSpec("max_bonus_h_p", Kind.INTEGER, false)
                .def(20).doc("Cap on total bonus max HP granted, in half-hearts (default 20).")));
        define("slime_moisture", SlimeMoisturePower.class, List.of(
            new FieldSpec("drain_per_tick", Kind.NUMBER, false)
                .def(0.0004).doc("Moisture lost per tick when not in water/rain (default 0.0004)."),
            new FieldSpec("dry_biome_drain_multiplier", Kind.NUMBER, false)
                .def(3.0).doc("Drain multiplier in desert/savanna/badlands (default 3.0)."),
            new FieldSpec("fire_drain_multiplier", Kind.NUMBER, false)
                .def(10.0).doc("Drain multiplier while the player is on fire (default 10.0)."),
            new FieldSpec("water_refill_per_tick", Kind.NUMBER, false)
                .def(0.005).doc("Moisture regained per tick while in water or rain (default 0.005)."),
            new FieldSpec("regen_threshold", Kind.NUMBER, false)
                .def(0.75).doc("Moisture (0-1) above which the player gets Regeneration (default 0.75)."),
            new FieldSpec("armor_penalty_threshold", Kind.NUMBER, false)
                .def(0.10).doc("Moisture (0-1) below which the player loses 4 armor (default 0.10)."),
            new FieldSpec("dot_threshold", Kind.NUMBER, false)
                .def(0.0).doc("Moisture (0-1) at/below which damage-over-time starts (default 0.0)."),
            new FieldSpec("dot_damage", Kind.NUMBER, false)
                .def(1.0).doc("Magic damage dealt per interval when moisture is too low (default 1.0)."),
            new FieldSpec("dot_interval", Kind.INTEGER, false)
                .def(40).doc("Ticks between drying-out damage ticks (20 = 1s; default 40).")));
        define("stealth", StealthPower.class, List.of(
            new FieldSpec("activation_ticks", Kind.INTEGER, false)
                .def(200).doc("Ticks of continuous sneaking before invisibility kicks in (200 = 10s).")));
        define("summon_minion", SummonMinionPower.class, List.of(
            new FieldSpec("mob_type", Kind.STRING, true)
                .doc("Entity type id to summon, e.g. minecraft:zombie.")
                .pattern(RESOURCE_LOCATION_PATTERN),
            new FieldSpec("max_count", Kind.INTEGER, false)
                .def(3).doc("Max minions of this type alive at once before summoning is blocked."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(200).doc("Ticks before the summon ability can be reused (200 = 10s)."),
            new FieldSpec("hunger_cost", Kind.INTEGER, false)
                .def(4).doc("Hunger consumed each time a minion is summoned."),
            new FieldSpec("despawn_ticks", Kind.INTEGER, false)
                .def(18000).doc("Ticks a minion lasts before despawning (18000 = 15 min)."),
            new FieldSpec("death_damage", Kind.NUMBER, false)
                .def(1.0).doc("Damage dealt to the player when a minion dies (half-hearts)."),
            new FieldSpec("head", Kind.STRING, false)
                .doc("Item id for the summoned mob's helmet slot (defaults to iron helmet)."),
            new FieldSpec("chest", Kind.STRING, false)
                .doc("Item id for the summoned mob's chest slot (optional)."),
            new FieldSpec("legs", Kind.STRING, false)
                .doc("Item id for the summoned mob's leggings slot (optional)."),
            new FieldSpec("feet", Kind.STRING, false)
                .doc("Item id for the summoned mob's boots slot (optional)."),
            new FieldSpec("mainhand", Kind.STRING, false)
                .doc("Item id placed in the summoned mob's main hand (optional)."),
            new FieldSpec("offhand", Kind.STRING, false)
                .doc("Item id placed in the summoned mob's off hand (optional).")));
        define("tame_mob", TameMobPower.class, List.of(
            new FieldSpec("range", Kind.NUMBER, false)
                .def(16.0).doc("Max distance in blocks to raycast for the mob to tame (default 16)."),
            new FieldSpec("max_tamed", Kind.INTEGER, false)
                .def(4).doc("Max tamed mobs alive at once before taming is blocked."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(200).doc("Ticks before the tame ability can be reused (200 = 10s)."),
            new FieldSpec("hunger_cost", Kind.INTEGER, false)
                .def(3).doc("Hunger consumed each time a mob is tamed."),
            new FieldSpec("despawn_ticks", Kind.INTEGER, false)
                .def(36000).doc("Ticks a tamed mob lasts before despawning (36000 = 30 min)."),
            new FieldSpec("death_damage", Kind.NUMBER, false)
                .def(0.5).doc("Damage dealt to the player when a tamed mob dies (half-hearts)."),
            new FieldSpec("hostile_only", Kind.BOOLEAN, false)
                .def(true).doc("When true (default), only mobs implementing Enemy (zombies, skeletons, creepers, ...) can be tamed. Set false to allow taming any non-player Mob (animals, golems, villagers).")));
        define("tamed_animal_boost", TamedAnimalBoostPower.class, List.of(
            new FieldSpec("health_bonus", Kind.NUMBER, false)
                .def(4.0).doc("Flat max-health added to your tamed animals (half-hearts; default 4)."),
            new FieldSpec("speed_bonus", Kind.NUMBER, false)
                .def(0.1).doc("Flat movement-speed added to your tamed animals (default 0.1)."),
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(32.0).doc("Block radius around the player scanned for tamed animals (default 32).")));
        define("thorns_on_hit", ThornsOnHitPower.class, List.of(
            new FieldSpec("damage", Kind.NUMBER, false)
                .def(2.0).doc("Damage reflected to a melee attacker when you are hit (half-hearts; default 2)."),
            new FieldSpec("fire_ticks", Kind.INTEGER, false)
                .def(0).doc("Ticks the attacker is set on fire on reflect (0 = none; 20 = 1s).")));
        define("trade_availability", TradeAvailabilityPower.class, List.of(
            new FieldSpec("scan_interval", Kind.INTEGER, false)
                .def(40).doc("Ticks between villager-trade refresh scans (40 = 2s)."),
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(8.0).doc("Block radius around the player whose villager trades refresh (default 8).")));
        define("walk_on_fluid", WalkOnFluidPower.class, List.of(
            new FieldSpec("fluid", Kind.STRING, false)
                .def("both").doc("Which fluid surface you can walk on: water, lava, or both (default both).")));
        define("wraith_phase", WraithPhasePower.class, List.of(
            new FieldSpec("blocked_blocks", Kind.ARRAY, false)
                .doc("Block ids that cannot be phased through (default obsidian/bedrock)."),
            new FieldSpec("exhaustion_per_tick", Kind.NUMBER, false)
                .def(0.15).doc("Food exhaustion added each tick while phasing in solids (default 0.15)."),
            new FieldSpec("always_on", Kind.BOOLEAN, false)
                .def(false).doc("If true phasing is always-active instead of a toggle key (default false).")));

        // ── Group R (cont.) — batch D ───────────────────────────────────────
        // Last two plain-record powers. action_on_hit's `action` is described
        // as an enum-ish string but has no EnumHints vocabulary, so it stays
        // Kind.STRING (free text) — promoting it to a dropdown would not be
        // behavior-neutral. Its effect/target_group/target_type/damage_type are
        // Optional<> → required=false. size_scaling keeps no schema branch (its
        // scale/modify_reach branch is collapsed like the other registered
        // powers, leaving only common wrapper fields the spec subsumes).
        define("action_on_hit", ActionOnHitPower.class, List.of(
            new FieldSpec("action", Kind.STRING, false)
                .def("restore_health").doc("Effect on dealing damage: restore_health, restore_hunger, grant_effect, or target_effect."),
            new FieldSpec("amount", Kind.NUMBER, false)
                .def(2.0).doc("Half-hearts healed (restore_health) or food points fed (restore_hunger); default 2.0."),
            new FieldSpec("effect", Kind.STRING, false)
                .doc("Mob effect id applied to self (grant_effect) or the victim (target_effect)."),
            new FieldSpec("duration", Kind.INTEGER, false)
                .def(100).doc("Effect duration in ticks for grant/target_effect (20 = 1s); default 100."),
            new FieldSpec("amplifier", Kind.INTEGER, false)
                .def(0).doc("Potion amplifier for grant_effect/target_effect (0 = level I); default 0."),
            new FieldSpec("min_damage", Kind.NUMBER, false)
                .def(0.0).doc("Minimum incoming damage required before the action triggers; default 0.0."),
            new FieldSpec("chance", Kind.NUMBER, false)
                .def(1.0).doc("Probability 0.0-1.0 the action triggers per qualifying hit; default 1.0."),
            new FieldSpec("target_group", Kind.STRING, false)
                .doc("Optional entity-group filter (undead, arthropod, ...) restricting which victims trigger it."),
            new FieldSpec("target_type", Kind.STRING, false)
                .doc("Optional specific entity-type id filter for the victim."),
            new FieldSpec("damage_type", Kind.STRING, false)
                .doc("Optional damage-type id filter; the action only fires for matching sources.")));
        define("size_scaling", SizeScalingPower.class, List.of(
            new FieldSpec("scale", Kind.NUMBER, false)
                .def(1.0).doc("Target scale multiplier; 0.5 = half, 2.0 = double (default 1.0)."),
            new FieldSpec("modify_reach", Kind.BOOLEAN, false)
                .def(true).doc("If true, reach scales with body size too (default true). For independent reach use attribute_modifier.")));

        // ── Group N — batch 1 (Active* primitive RecordCodecBuilder powers) ──
        // These Active* powers share the AbstractActivePower contract but, unlike
        // active_ability, deserialize through a plain RecordCodecBuilder of
        // primitives — every field is `optionalFieldOf` (required=false + the
        // codec default) and every JSON key equals its record component's
        // snake-case, so the spec is a faithful, behavior-neutral mirror. None
        // has a power.schema.json branch (only a `type`-enum entry), so there is
        // nothing to collapse; the field_docs.json entries fold onto the specs.
        // `type` is internal plumbing (not authored), so it is omitted.
        define("active_bolt", ActiveBoltPower.class, List.of(
            new FieldSpec("speed", Kind.NUMBER, false)
                .def(1.2).doc("Velocity of the launched wind charge along the look direction; default 1.2."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(80).doc("Cooldown between casts in ticks (20 = 1s); default 80.")));
        define("active_dash", ActiveDashPower.class, List.of(
            new FieldSpec("power", Kind.NUMBER, false)
                .def(1.5).doc("Magnitude of the dash velocity vector; default 1.5."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(40).doc("Cooldown between dashes in ticks (20 = 1s); default 40."),
            new FieldSpec("allow_vertical", Kind.BOOLEAN, false)
                .def(false).doc("If true the dash follows look pitch; if false it stays horizontal; default false."),
            new FieldSpec("set_velocity", Kind.BOOLEAN, false)
                .def(false).doc("If true replace the player's velocity; if false add to it; default false.")));
        define("active_fireball", ActiveFireballPower.class, List.of(
            new FieldSpec("speed", Kind.NUMBER, false)
                .def(1.5).doc("Velocity applied to each of the spread small fireballs; default 1.5."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(100).doc("Cooldown between casts in ticks (20 = 1s); default 100.")));
        define("active_phase", ActivePhasePower.class, List.of(
            new FieldSpec("max_depth", Kind.INTEGER, false)
                .def(16).doc("Maximum wall thickness in blocks the phase can pass through; default 16."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(40).doc("Cooldown between phases in ticks (20 = 1s); default 40."),
            new FieldSpec("hunger_cost", Kind.INTEGER, false)
                .def(0).doc("Food/exhaustion points consumed on a successful phase; default 0.")));
        define("active_place_block", ActivePlaceBlockPower.class, List.of(
            new FieldSpec("block_id", Kind.STRING, false)
                .def("minecraft:glowstone").doc("Block id placed at the targeted face; default minecraft:glowstone."),
            new FieldSpec("max_distance", Kind.NUMBER, false)
                .def(5.0).doc("Maximum ray-pick reach in blocks for the placement surface; default 5.0."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(100).doc("Cooldown between placements in ticks (20 = 1s); default 100."),
            new FieldSpec("hunger_cost", Kind.INTEGER, false)
                .def(0).doc("Food/exhaustion points consumed on a successful placement; default 0.")));
        define("active_recall", ActiveRecallPower.class, List.of(
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(600).doc("Cooldown in ticks (20 = 1s) between recalls to bed/spawn; default 600.")));

        // ── Group N — batch 2 (more Active* powers + two clean JsonOps powers) ─
        // active_swap/teleport/ground_slam/tidal_wave are the same all-optional
        // RecordCodecBuilder shape as batch 1 (no schema branch to collapse).
        // model_color and breath_in_fluid are hand-rolled JsonOps decoders with
        // NO schema branch whose every authored field maps 1:1 to a record
        // component (snake-case), so the spec is behavior-neutral:
        //   • model_color.condition is Optional<EntityCondition> in the codec →
        //     a nested condition REF (.ref("#"), required=false), mirroring how
        //     the action combinators model an embedded condition.
        //   • breath_in_fluid's codec ALSO accepts two alias input keys
        //     (air_loss_per_second, drain_rate) that are not record components —
        //     reflection never surfaced them as form fields and they keep their
        //     field_docs.json entries as documented JSON aliases; the spec only
        //     declares the two real components (fluid, drain_interval_ticks),
        //     matching exactly what the form rendered before.
        define("active_swap", ActiveSwapPower.class, List.of(
            new FieldSpec("range", Kind.NUMBER, false)
                .def(20.0).doc("Max distance in blocks to find the looked-at entity to swap with; default 20.0."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(80).doc("Cooldown between swaps in ticks (20 = 1s); default 80.")));
        define("active_teleport", ActiveTeleportPower.class, List.of(
            new FieldSpec("range", Kind.NUMBER, false)
                .def(32.0).doc("Maximum teleport distance in blocks; default 32.0."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(60).doc("Cooldown between teleports in ticks (20 = 1s); default 60."),
            new FieldSpec("mode", Kind.STRING, false)
                .def("target").doc("\"target\" teleports to the looked-at spot, \"random\" to a nearby safe spot; default target."),
            new FieldSpec("hunger_cost", Kind.INTEGER, false)
                .def(0).doc("Food/exhaustion points consumed on a successful teleport; default 0.")));
        define("ground_slam", ActiveGroundSlamPower.class, List.of(
            new FieldSpec("damage", Kind.NUMBER, false)
                .def(6.0).doc("Damage dealt to each entity caught in the slam (default 6.0)."),
            new FieldSpec("knockback_strength", Kind.NUMBER, false)
                .def(1.5).doc("Outward knockback pushing hit entities away (default 1.5)."),
            new FieldSpec("radius", Kind.NUMBER, false)
                .def(6.0).doc("Blocks around the player whose entities are hit (default 6.0)."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(120).doc("Ticks before the slam can be reused (default 120 = 6s).")));
        define("tidal_wave", ActiveTidalWavePower.class, List.of(
            new FieldSpec("damage", Kind.NUMBER, false)
                .def(4.0).doc("Damage dealt to each entity in the cone (half-hearts; default 4)."),
            new FieldSpec("knockback_strength", Kind.NUMBER, false)
                .def(2.0).doc("Forward push applied to hit entities (default 2)."),
            new FieldSpec("range", Kind.NUMBER, false)
                .def(8.0).doc("Max reach of the water cone in blocks (default 8)."),
            new FieldSpec("cone_angle", Kind.NUMBER, false)
                .def(60.0).doc("Total width in degrees of the forward damage cone (default 60)."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(100).doc("Ticks before the wave can be reused (100 = 5s)."),
            new FieldSpec("hunger_cost", Kind.INTEGER, false)
                .def(0).doc("Hunger consumed per use (default 0).")));
        define("model_color", ModelColorPower.class, List.of(
            new FieldSpec("red", Kind.NUMBER, false)
                .def(1.0).range(0.0, 1.0).doc("Red channel of the player model tint, 0.0 to 1.0 (default 1.0)."),
            new FieldSpec("green", Kind.NUMBER, false)
                .def(1.0).range(0.0, 1.0).doc("Green channel of the player model tint, 0.0 to 1.0 (default 1.0)."),
            new FieldSpec("blue", Kind.NUMBER, false)
                .def(1.0).range(0.0, 1.0).doc("Blue channel of the player model tint, 0.0 to 1.0 (default 1.0)."),
            new FieldSpec("alpha", Kind.NUMBER, false)
                .def(1.0).range(0.0, 1.0).doc("Player model tint opacity, 0.0 transparent to 1.0 opaque (default 1.0)."),
            new FieldSpec("condition", Kind.REF, false).ref("#")
                .doc("Only applies the tint while this DSL condition passes (optional).")));
        define("breath_in_fluid", BreathInFluidPower.class, List.of(
            new FieldSpec("fluid", Kind.STRING, false)
                .def("water").doc("Fluid that suffocates the player, \"water\" or \"lava\"; default water."),
            new FieldSpec("drain_interval_ticks", Kind.INTEGER, false)
                .def(20).doc("Ticks between each 1-point air drain while submerged; higher = slower drain (20 = 1s). Aliases: drain_rate, air_loss_per_second.")));

        // ── Group N — batch 3 (attribute_modifier: schema-branch collapse) ────
        // A hand-rolled JsonOps decoder that, unlike its siblings, agrees with
        // its power.schema.json branch on required-ness: the codec hard-fails
        // without `attribute` and `amount` (required=true), exactly the schema's
        // `required: [type, attribute, amount]`. Every other field defaults
        // (required=false). Each authored JSON key maps 1:1 to a record
        // component, so the spec is behavior-neutral and the schema branch
        // collapses (its name/description/hidden wrapper fields are subsumed by
        // FormModel's common handling). Field kinds mirror the collapsed branch
        // byte-for-byte: `operation` is the curated ENUM (also EnumHints-backed);
        // `condition` is the schema's MIXED (oneOf string|condition-REF);
        // equipment/location conditions are nested OBJECTs.
        define("attribute_modifier", AttributeModifierPower.class, List.of(
            new FieldSpec("attribute", Kind.STRING, true)
                .doc("Attribute id to modify, e.g. minecraft:generic.movement_speed or minecraft:block_interaction_range / entity_interaction_range for reach; required.")
                .pattern(RESOURCE_LOCATION_PATTERN),
            new FieldSpec("amount", Kind.NUMBER, true)
                .doc("Numeric value applied to the attribute under the chosen operation; required."),
            new FieldSpec("operation", Kind.ENUM, false)
                .options("add_value", "add_multiplied_base", "add_multiplied_total")
                .doc("How amount applies: add_value, add_multiplied_base, or add_multiplied_total."),
            new FieldSpec("condition", Kind.MIXED, false)
                .doc("Optional gate: a named-condition string (in_water/on_land/in_lava) or a DSL condition object; active only while it passes."),
            new FieldSpec("equipment_condition", Kind.OBJECT, false)
                .doc("Optional gate matching a worn item by id/tag in a slot; active only while worn."),
            new FieldSpec("location_condition", Kind.OBJECT, false)
                .doc("Optional gate on dimension/biome/structure; active only while there.")));

        // ── Group A — batch 1 (key-aliased event hook: schema-branch collapse) ─
        // action_on_event is the first power whose JSON key set does NOT line up
        // with its Config record components 1:1, so it exercises the FieldSpec
        // key-alias (`.boundTo`): the codec reads its `EntityAction action`
        // component from the `entity_action` JSON key (see ActionOnEventPower
        // .Config.CODEC.decode), so the spec keeps the author-facing JSON name
        // `entity_action` and binds it to the `action` component for the drift
        // audit. Every other component's JSON key already equals camel→snake of
        // the component (condition, modifier, block_condition, effect,
        // effect_tag, immunity_ticks), so those need no alias. Required-ness is
        // read straight off the codec: `event` is the only field whose absence
        // hard-fails (valueOf on "" throws → required=true, matching the
        // schema's `required: [type, event]`); condition/action/modifier default
        // to always-true/noop/identity and the rest are `Optional<>` or default
        // to 0 → required=false. The codec accepts the FULL EventPowerIndex.Event
        // vocabulary case-insensitively, so the `event` options below are the
        // complete enum (lowercased) — the old schema branch's `event` enum had
        // drifted, omitting climb/craft_item/smelt_item/enchant_item/anvil_repair
        // /bonemeal/breed/tame/food_finished/advancement_earned/trade_completed
        // /villager_interact/mod_trade_price/mod_craft_amount/mod_fall_damage; the
        // spec now matches the codec. The power.schema.json branch and the
        // field_docs.json entry collapse onto this spec (behavior-neutral: the
        // power still deserializes through its own Codec<Config>, untouched).
        define("action_on_event", ActionOnEventPower.class, List.of(
            new FieldSpec("event", Kind.ENUM, true)
                .options("attack", "hit_taken", "kill", "death", "block_break", "block_place",
                    "item_use", "respawn", "tick", "dimension_change", "climb", "jump",
                    "projectile_hit", "craft_item", "smelt_item", "enchant_item", "anvil_repair",
                    "bonemeal", "breed", "tame", "food_eaten", "food_finished", "advancement_earned",
                    "trade_completed", "villager_interact", "gained", "lost", "chosen", "wake_up",
                    "land", "block_use", "entity_use", "item_pickup", "item_use_finish",
                    "effect_applied", "mod_exhaustion", "mod_natural_regen", "mod_trade_price",
                    "mod_craft_amount", "mod_enchant_level", "mod_harvest_drops", "mod_teleport_range",
                    "mod_fall_damage", "mod_knockback", "mod_potion_duration", "mod_anvil_cost",
                    "mod_crafted_food_saturation", "mod_bonemeal_extra")
                .doc("Case-insensitive event key that triggers this hook (e.g. food_eaten, block_break, mod_exhaustion). See docs/EVENTS.md."),
            new FieldSpec("condition", Kind.REF, false).ref("condition.schema.json")
                .doc("Optional DSL condition gating the action/modifier; both only run while it passes (default always)."),
            new FieldSpec("entity_action", Kind.REF, false).boundTo("action").ref("action.schema.json")
                .doc("EntityAction run on the player as a side-effect when the configured event fires."),
            new FieldSpec("modifier", Kind.MIXED, false)
                .doc("FloatModifier (or array) chained onto the event's value (e.g. exhaustion, regen)."),
            new FieldSpec("block_condition", Kind.OBJECT, false)
                .doc("Block-position filter for block events (block_break, block_place, block_use). Ignored on other events."),
            new FieldSpec("effect", Kind.STRING, false)
                .doc("EFFECT_APPLIED filter: only fire for this exact mob-effect id (e.g. 'spore:mycelium_ef'). Ignored on other events."),
            new FieldSpec("effect_tag", Kind.STRING, false)
                .doc("EFFECT_APPLIED filter: only fire for effects in this tag (e.g. '#minecraft:harmful'). Combine with 'effect' (OR-matched). Ignored on other events."),
            new FieldSpec("immunity_ticks", Kind.INTEGER, false)
                .def(0).range(0.0, null)
                .doc("EFFECT_APPLIED only: after a successful cleanse (action cancels the event), grant this many ticks of full immunity to the same effect id before re-rolling. 20 = 1s; default 0 (no grace).")));

        // ── Group A — batch 2 (required↔optional flips: schema disagreed) ─────
        // These three powers' hand-rolled codecs treat as OPTIONAL a field the
        // power.schema.json branch marked required — registering as the codec
        // (required=false, with the codec's default) collapses the branch and
        // corrects the drift. Every JSON key already equals camel→snake of its
        // record component, so no key-alias is needed.
        //   • mob_behavior: schema required [type, aggression], but the codec
        //     defaults aggression to NEUTRAL when absent (o.has("aggression") ?
        //     … : NEUTRAL) — not required. Every other field defaults too
        //     (retaliate=true, anger_linger_ticks=200, aggro_range=16.0,
        //     call_for_help=false); target_type is Optional<>. None required.
        //   • modify_damage: schema required [type, multiplier], but the codec
        //     defaults multiplier to 1.0f when absent — not required. direction
        //     defaults to IN; damage_type/target_group/condition are Optional<>.
        //   • entity_set: schema required [type, name], but `name` is
        //     optionalFieldOf("name", "") — not required.
        define("mob_behavior", MobBehaviorPower.class, List.of(
            new FieldSpec("aggression", Kind.ENUM, false)
                .options("neutral", "hostile", "conditional").def("neutral")
                .doc("neutral = vanilla AI unchanged (only retaliate applies); hostile = always target the target type on sight; conditional = target a player only while every hostile_when condition holds. Default neutral."),
            new FieldSpec("hostile_when", Kind.ARRAY, false)
                .doc("List of conditions (AND-ed) tested against the PROSPECTIVE PLAYER TARGET (piglin-style, e.g. 'player not wearing gold'), not the mob. Only used when aggression = conditional; empty list behaves like hostile."),
            new FieldSpec("retaliate", Kind.BOOLEAN, false)
                .def(true).doc("Also fight back against whatever damaged the mob (vanilla hurt-by-target). Default true."),
            new FieldSpec("anger_linger_ticks", Kind.INTEGER, false)
                .def(200).range(0.0, null).doc("Keep the target this many ticks after the trigger stops holding, so the mob calms down gradually instead of instantly (default 200 = 10s)."),
            new FieldSpec("aggro_range", Kind.NUMBER, false)
                .def(16.0).range(0.0, null).doc("Max distance to acquire a target (default 16.0)."),
            new FieldSpec("target_type", Kind.STRING, false)
                .doc("Entity type id to be hostile toward; omitted = players. Conditions only apply to player targets."),
            new FieldSpec("call_for_help", Kind.BOOLEAN, false)
                .def(false).doc("When retaliating, alert nearby same-type mobs (vanilla pack aggro). Default false.")));
        define("modify_damage", ModifyDamagePower.class, List.of(
            new FieldSpec("direction", Kind.ENUM, false)
                .options("in", "out").def("in")
                .doc("\"in\" scales damage taken, \"out\" scales damage dealt (default in)."),
            new FieldSpec("damage_type", Kind.STRING, false)
                .doc("Either a vanilla msg ID (e.g. 'onFire') or a tag prefixed with #."),
            new FieldSpec("multiplier", Kind.NUMBER, false)
                .def(1.0).doc("Factor applied to the damage; 0.5 halves, 2.0 doubles (default 1.0)."),
            new FieldSpec("target_group", Kind.STRING, false)
                .doc("Optional entity-group filter limiting which targets/attackers apply."),
            new FieldSpec("condition", Kind.REF, false).ref("condition.schema.json")
                .doc("Only applies the multiplier while this DSL condition passes (optional).")));
        define("entity_set", EntitySetPower.class, List.of(
            new FieldSpec("name", Kind.STRING, false)
                .def("").doc("Named UUID set this power declares the player a member of; namespace it (e.g. 'mypack:kill_streak') to avoid collisions. Read/mutated by the in_set / add_to_set / remove_from_set verbs.")));

        // ── Group B — phantom / renamed fields (schema named what the codec lacks) ─
        // For each of these the power.schema.json branch named a field the codec
        // does NOT read (a phantom), or named a real field under the wrong JSON
        // key. Register to match the codec — using the FieldSpec key-alias
        // (.boundTo) where the codec reads a component under a non-camel→snake
        // JSON key — then collapse the branch and drop the phantom field_docs.
        //   • toggle: the codec reads its `defaultValue` component from the
        //     `default` JSON key (Codec.BOOL.optionalFieldOf("default", false)),
        //     NOT `default_value` as the schema claimed; and it has NO `key`
        //     field at all (the schema's `key` enum was a phantom). The spec's
        //     JSON name is `default`, bound to the `defaultValue` component.
        //   • active_ability: the codec reads its `action` component from the
        //     `entity_action` JSON key (bound), defaulting to noop → the schema's
        //     `required: [type, entity_action]` was wrong (not required). It also
        //     reads resource_cost / resource_cost_amount the schema omitted, and
        //     has NO `key` field (schema phantom). cooldown_ticks / hunger_cost
        //     default; condition defaults to alwaysTrue.
        //   • edible_item: the codec has NO item_condition / fast / meat fields
        //     (all three were schema phantoms) and DOES read consume_sound
        //     (Optional<>) which the schema omitted. Every real field defaults.
        //   • loot_pool_grant: the codec reads its `cooldownTicks` component from
        //     the `cooldown` JSON key (Codec.INT.optionalFieldOf("cooldown", 0)),
        //     bound here. grant_id / loot_table are bare fieldOf → required=true
        //     (matching the schema's required list); rolls / bonus_rolls / active
        //     default.
        define("toggle", TogglePower.class, List.of(
            new FieldSpec("default", Kind.BOOLEAN, false).boundTo("defaultValue")
                .def(false).doc("Initial boolean value before the toggle is first flipped (default false). 'true' declares 'on until flipped off' without a GAINED hook.")));
        define("active_ability", ActiveAbilityPower.class, List.of(
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(60).range(0.0, null).doc("Cooldown between uses in ticks (20 = 1s); default 60."),
            new FieldSpec("hunger_cost", Kind.INTEGER, false)
                .def(0).range(0.0, null).doc("Food/exhaustion points consumed per successful activation; default 0."),
            new FieldSpec("resource_cost", Kind.STRING, false)
                .def("").doc("Optional resource power id whose value is spent on activation; empty = no resource cost."),
            new FieldSpec("resource_cost_amount", Kind.INTEGER, false)
                .def(0).doc("Amount of the resource_cost resource consumed per activation; default 0."),
            new FieldSpec("entity_action", Kind.REF, false).boundTo("action").ref("action.schema.json")
                .doc("EntityAction tree executed on the player when the keybind fires (defaults to noop)."),
            new FieldSpec("condition", Kind.REF, false).ref("condition.schema.json")
                .doc("Optional DSL condition gating the ability; it only fires while this passes (default always).")));
        define("edible_item", EdibleItemPower.class, List.of(
            new FieldSpec("items", Kind.ARRAY, false)
                .doc("List of item ids that become edible (matches items OR tags)."),
            new FieldSpec("tags", Kind.ARRAY, false)
                .doc("List of item-tag ids that become edible (matches items OR tags)."),
            new FieldSpec("nutrition", Kind.INTEGER, false)
                .def(4).range(0.0, null).doc("Hunger points restored when consumed (default 4)."),
            new FieldSpec("saturation", Kind.NUMBER, false)
                .def(0.3).doc("Saturation modifier applied on consumption (default 0.3)."),
            new FieldSpec("always_edible", Kind.BOOLEAN, false)
                .def(true).doc("If true the item can be eaten even at full hunger (default true)."),
            new FieldSpec("consume_sound", Kind.STRING, false)
                .doc("Optional sound-event id played on consumption; omit to use the default eat sound.")));
        define("loot_pool_grant", LootPoolGrantPower.class, List.of(
            new FieldSpec("grant_id", Kind.STRING, true)
                .doc("Unique id tracked so the bundle is granted only once per player. Dedups the whole pool, not individual rolled stacks. Shares the starting_equipment grant attachment so a /origin reset clears both."),
            new FieldSpec("loot_table", Kind.STRING, true)
                .doc("ResourceLocation of the vanilla loot table to roll on activation (e.g. 'neoorigins:rewards/wood_starter'). Reuses the full vanilla loot infrastructure (weighted entries, conditions, functions, modifiers).")
                .pattern(RESOURCE_LOCATION_PATTERN),
            new FieldSpec("rolls", Kind.INTEGER, false)
                .def(1).range(0.0, null).doc("Number of times the table is rolled per activation; default 1. Combined with bonus_rolls; if both are zero the harness lints the power as a no-op."),
            new FieldSpec("bonus_rolls", Kind.INTEGER, false)
                .def(0).range(0.0, null).doc("Extra rolls added to `rolls` per activation; default 0. Mirrors vanilla loot-pool naming."),
            new FieldSpec("active", Kind.STRING, false)
                .def("").doc("Optional display-only translation key advertising which keybind slot the power expects (e.g. 'key.use_skill_1'). Actual dispatch is via the skill-slot system."),
            new FieldSpec("cooldown", Kind.INTEGER, false).boundTo("cooldownTicks")
                .def(0).range(0.0, null).doc("Cooldown in ticks between activations (20 = 1s); default 0.")));

        // ── Group C — shape-mismatch (nested object/array): schema STRUCTURE drifted ─
        // For these the power.schema.json branch's nested shape (objects/arrays)
        // disagreed with the Config record. Register to the codec, then collapse
        // the branch (the structure it named was a phantom) and drop the matching
        // field_docs entries (docs now live on the spec).
        //   • restrict_armor: the codec reads a SINGLE `restrictions` component —
        //     a List<SlotRestriction{slot,item,tag}> via
        //     SlotRestriction.CODEC.listOf().optionalFieldOf("restrictions",
        //     List.of()) — so it is NOT required (empty list when absent). The old
        //     schema branch instead named four per-slot OBJECT fields
        //     (head/chest/legs/feet) that the codec never reads (phantoms); the
        //     real per-slot data lives inside each array entry's `slot` string.
        //     One ARRAY spec matches the codec; the phantom branch collapses.
        define("restrict_armor", RestrictArmorPower.class, List.of(
            new FieldSpec("restrictions", Kind.ARRAY, false)
                .doc("List of slot restrictions, each {slot, item?, tag?}: when a matching item (by id and/or #tag) is equipped in that EquipmentSlot (head/chest/legs/feet/mainhand/offhand) it is ejected back to the inventory. An entry with neither item nor tag bars the whole slot. Empty list (default) restricts nothing.")));

        //   • modify_player_spawn: the codec reads exactly two fields — a NESTED
        //     `location` LocationCondition object (fieldOf, no default → the only
        //     hard-fail, so required=true) and a top-level `override_bed` boolean
        //     (optionalFieldOf default false → not required). The old schema branch
        //     named four flat phantoms (dimension/biome/spawn_strategy/bed_override)
        //     the codec never reads: dimension/biome live INSIDE the nested
        //     `location` object (LocationCondition's own fields), spawn_strategy
        //     doesn't exist, and the bool is keyed `override_bed`, not
        //     `bed_override`. The branch collapses onto these two specs.
        define("modify_player_spawn", ModifyPlayerSpawnPower.class, List.of(
            new FieldSpec("location", Kind.OBJECT, true)
                .doc("Nested LocationCondition object resolving the respawn target (its own dimension/biome/biome_tag/biomes/structure/structure_tag/etc. fields). Required — the power has no respawn target without it."),
            new FieldSpec("override_bed", Kind.BOOLEAN, false)
                .def(false).doc("When true also overrides the player's bed/respawn-anchor spawn point, not just the post-death respawn position (default false).")));

        //   • persistent_effect: a hand-rolled Codec whose Config record carries
        //     only FOUR user-facing components — effects (List<EffectSpec>, built
        //     from the `effects` array OR a single root-level effect shorthand;
        //     empty when absent → not required), condition (EntityCondition,
        //     defaults alwaysTrue → REF, not required), toggleable (bool default
        //     true) and defaultOff→`default_off` (bool default false). The old
        //     schema branch ALSO listed effect/amplifier/show_icon/show_particles
        //     /ambient (root-level shorthand + cascade-override hooks the codec
        //     folds INTO each EffectSpec, NOT separate Config components) and a
        //     `duration` field the codec never reads (phantom — effects apply at
        //     INFINITE_DURATION). Those non-components can't be FieldSpecs (the
        //     drift audit resolves each spec name to a record component), so the
        //     spec is the four real components and the branch collapses. (This
        //     also moves SchemaFormCheck's hardcoded structured sample off
        //     persistent_effect onto the still-branched `particle`.)
        define("persistent_effect", PersistentEffectPower.class, List.of(
            new FieldSpec("effects", Kind.ARRAY, false)
                .doc("List of effect entries, each {effect|id, amplifier?, ambient?, show_particles?, show_icon?}, applied at INFINITE_DURATION while active. A single effect may instead be authored as the root-level shorthand (effect/amplifier/... at the power root); root-level ambient/show_particles/show_icon/amplifier also cascade as defaults onto every entry that omits them. Empty list = no effect."),
            new FieldSpec("condition", Kind.REF, false).ref("condition.schema.json")
                .doc("Optional EntityCondition gating the effects: they apply only while it passes and are cleared the moment it stops (default always-true)."),
            new FieldSpec("toggleable", Kind.BOOLEAN, false)
                .def(true).doc("When true the power binds a keybind that flips the effects on/off; off clears them (default true)."),
            new FieldSpec("default_off", Kind.BOOLEAN, false)
                .def(false).doc("Toggleable powers only: when true the effects START disabled so the player must opt in via the keybind (default false).")));

        // effect_immunity lives in the compat package (com.cyberday1.neoorigins.compat),
        // not power.builtin, so SchemaFormCheck's power.builtin class-scan never flagged
        // it — it was registered in PowerTypes but missing here. Flat single-field codec
        // (Codec.STRING.listOf optionalFieldOf "effects" default []), so it registers
        // cleanly as one ARRAY field.
        define("effect_immunity", EffectImmunityPower.class, List.of(
            new FieldSpec("effects", Kind.ARRAY, false)
                .doc("Mob effect IDs (e.g. minecraft:poison) whose application to the player is cancelled. Empty list = no immunity.")));

        // ── Group D — key-alias batch 2 (condition_passive / prevent_death) ──────
        // Both hand-rolled codecs read their `EntityAction action` component from
        // the `entity_action` JSON key (ConditionPassivePower / PreventDeathPower
        // .Config.CODEC.decode read `obj.getAsJsonObject("entity_action")` into
        // `action`), the exact key-alias action_on_event already exercises — so the
        // spec keeps the author-facing JSON name `entity_action` and `.boundTo`s it
        // to the `action` component. Required-ness is read straight off each codec:
        // every other field defaults, so nothing hard-fails on absence (required=
        // false throughout).
        //   • condition_passive: the codec reads interval (default 20), condition
        //     (default alwaysTrue), entity_action→action (default noop) and
        //     else_action→elseAction (default noop). else_action already camel→
        //     snakes to its component, so only entity_action needs the alias. The
        //     old schema branch named `interval_ticks` — a PHANTOM the codec never
        //     reads (only `interval`) — and omitted `else_action` entirely; both
        //     corrected here. The power.schema.json branch + field_docs entry
        //     collapse onto this spec (the power still deserializes via its own
        //     Codec<Config>, untouched).
        define("condition_passive", ConditionPassivePower.class, List.of(
            new FieldSpec("interval", Kind.INTEGER, false)
                .def(20).range(1.0, null)
                .doc("Ticks between condition checks (min 1; default 20 = 1s)."),
            new FieldSpec("condition", Kind.REF, false).ref("condition.schema.json")
                .doc("Optional EntityCondition gating the action: entity_action runs each interval only while it passes; else_action runs while it does not (default always-true)."),
            new FieldSpec("entity_action", Kind.REF, false).boundTo("action").ref("action.schema.json")
                .doc("EntityAction run on the player each interval while the condition passes (defaults to noop)."),
            new FieldSpec("else_action", Kind.REF, false).ref("action.schema.json")
                .doc("EntityAction run on the player each interval while the condition does NOT pass (defaults to noop).")));

        //   • prevent_death: NO schema branch (already on the permissive fallback),
        //     so this is a pure register-to-codec + field_docs collapse. The codec
        //     reads damage_types (Optional<String>), invert (default false),
        //     set_health (default 1.0, clamped min 1), cooldown_ticks (default 0),
        //     entity_action→action (default noop) and condition (Optional<
        //     EntityCondition>). _power_id→powerId is injected internal plumbing
        //     (audit INTERNAL), not authored. Only entity_action needs the alias;
        //     every other JSON key already camel→snakes to its component. Nothing
        //     required.
        define("prevent_death", PreventDeathPower.class, List.of(
            new FieldSpec("damage_types", Kind.STRING, false)
                .doc("Comma-separated damage-type filter (msgIds, registry keys, or #tags); only matching killing blows are prevented. Omit to prevent every lethal hit."),
            new FieldSpec("invert", Kind.BOOLEAN, false)
                .def(false).doc("When true treat damage_types as a blacklist: prevent all deaths EXCEPT the listed types. Meaningless (ignored) without damage_types. Default false."),
            new FieldSpec("set_health", Kind.NUMBER, false)
                .def(1.0).range(1.0, null)
                .doc("Health the player is left at after a save; clamped to at least 1 so they don't re-die next tick (default 1.0)."),
            new FieldSpec("cooldown_ticks", Kind.INTEGER, false)
                .def(0).range(0.0, null)
                .doc("Ticks the power goes inert after a save (20 = 1s); default 0 = no cooldown (Origins behaviour, every lethal hit saved)."),
            new FieldSpec("entity_action", Kind.REF, false).boundTo("action").ref("action.schema.json")
                .doc("Optional EntityAction run on the player each time a lethal hit is prevented (defaults to noop)."),
            new FieldSpec("condition", Kind.REF, false).ref("condition.schema.json")
                .doc("Optional EntityCondition gating the save: the power only prevents death while it passes (default always-true).")));

        // ── Group E — reflection-fallback enum + condition doc adds ──────────────
        //   • tick_action: NO schema branch (permissive fallback). The codec reads
        //     interval (default 20) and action_type→actionType. action_type is a
        //     real Java ActionType enum, so the reflection extractor already sees
        //     it as ENUM — but with UPPERCASE constant names (TELEPORT_ON_DAMAGE/
        //     NONE), whereas the codec's xmap serializes LOWERCASE tokens. The spec
        //     declares the correct lowercase vocabulary directly, and an EnumHints
        //     `*|action_type` entry pins the same tokens for the reflection path the
        //     editor still uses for any not-yet-registered enum. field_docs entry
        //     collapses onto the spec. type is internal.
        define("tick_action", TickActionPower.class, List.of(
            new FieldSpec("interval", Kind.INTEGER, false)
                .def(20).range(1.0, null)
                .doc("Ticks between each action run (20 = 1s; default 20)."),
            new FieldSpec("action_type", Kind.ENUM, false)
                .options("teleport_on_damage", "none").def("none")
                .doc("Periodic action dispatched by OriginEventHandler: teleport_on_damage or none (default none).")));

        //   • conditional: NO schema branch (permissive fallback). The codec reads
        //     condition (a Condition enum: climbing/in_water/on_ground/always,
        //     default always) and inner_power→innerPower (ResourceLocation.fieldOf,
        //     NO default → the only hard-fail, so required=true). condition gets a
        //     spec doc (the existing field_docs only documented inner_power, leaning
        //     on the `*` wildcard for condition); both move onto the spec and the
        //     field_docs entry collapses. type is internal.
        define("conditional", ConditionalPower.class, List.of(
            new FieldSpec("condition", Kind.ENUM, false)
                .options("climbing", "in_water", "on_ground", "always").def("always")
                .doc("Built-in player-state gate the inner power is active under: climbing, in_water, on_ground, or always (default always). Unknown values fall back to always."),
            new FieldSpec("inner_power", Kind.STRING, true)
                .doc("Required resource id of another power that is only active while this condition holds (e.g. 'neoorigins:flight').")));

        //   • prevent_action: HAS a schema branch (collapsed here). The codec reads
        //     action (Action enum, REQUIRED — fieldOf, no default → hard-fails on
        //     absence), active_when (ActiveWhen enum, default always), and the four
        //     ARMOR_EQUIP per-slot booleans head/chest/legs/feet (each default
        //     false). type is the internal discriminator (omitted). Both enums
        //     serialize LOWERCASE (their xmaps .toLowerCase on write / .toUpperCase
        //     on read), so the spec declares the lowercase vocabulary directly —
        //     the old schema branch listed UPPERCASE tokens AND only 8 of the 13
        //     Action constants (it dropped eye_damage, water_damage, sleep, elytra,
        //     none); the full set is declared here. Unknown action/active_when
        //     tokens fall back to none/always respectively at decode. field_docs
        //     entry collapses onto the spec.
        define("prevent_action", PreventActionPower.class, List.of(
            new FieldSpec("action", Kind.ENUM, true)
                .options("fall_damage", "fire", "drown", "freeze", "sprint_food",
                         "armor_equip", "chestplate_equip", "eye_damage", "water_damage",
                         "swim", "sleep", "elytra", "none")
                .doc("Required. The action this power blocks: fall_damage, fire, drown, freeze, sprint_food, armor_equip (per-slot via head/chest/legs/feet), chestplate_equip (legacy alias = armor_equip chest), eye_damage, water_damage, swim, sleep, elytra, or none. Unknown values fall back to none."),
            new FieldSpec("active_when", Kind.ENUM, false)
                .options("always", "not_sneaking", "sneaking", "not_on_ground", "on_ground")
                .def("always")
                .doc("Player-state gate the prevention applies under: always, not_sneaking, sneaking, not_on_ground, or on_ground (default always). E.g. not_sneaking lets a crouching Avian still take fall damage. Unknown values fall back to always."),
            new FieldSpec("head", Kind.BOOLEAN, false)
                .def(false)
                .doc("ARMOR_EQUIP only: when true, block equipping items in the helmet slot. Ignored for other actions (default false)."),
            new FieldSpec("chest", Kind.BOOLEAN, false)
                .def(false)
                .doc("ARMOR_EQUIP only: when true, block equipping items in the chestplate slot. Ignored for other actions (default false)."),
            new FieldSpec("legs", Kind.BOOLEAN, false)
                .def(false)
                .doc("ARMOR_EQUIP only: when true, block equipping items in the leggings slot. Ignored for other actions (default false)."),
            new FieldSpec("feet", Kind.BOOLEAN, false)
                .def(false)
                .doc("ARMOR_EQUIP only: when true, block equipping items in the boots slot. Ignored for other actions (default false).")));

        // ── neoorigins:resource — registered via nested-object (children) support ─
        // Its Codec reads a NESTED `hud_render` JSON object — { label, color,
        // should_render } — whose values are stored on the FLATTENED Config
        // components label/color/hidden. A VIRTUAL OBJECT wrapper (no backing
        // component of its own) carries those three as children, each .boundTo
        // its flat component (should_render→hidden, stored INVERTED). See
        // FieldSpec.virtualObject and the drift-audit "virtual wrapper" path.
        define("resource", ResourcePower.class, List.of(
            new FieldSpec("min", Kind.INTEGER, false).def(0).doc("Lower bound of the resource bar."),
            new FieldSpec("max", Kind.INTEGER, false).def(100).doc("Upper bound of the resource bar."),
            new FieldSpec("start_value", Kind.INTEGER, false).doc("Initial value on grant; defaults to max."),
            new FieldSpec("regen_rate", Kind.INTEGER, false).def(0).doc("Amount added each regen interval (0 = no regen)."),
            new FieldSpec("regen_interval", Kind.INTEGER, false).def(20).range(1.0, null).doc("Ticks between regen applications (min 1)."),
            new FieldSpec("regen_condition", Kind.REF, false).ref("#").doc("Optional EntityCondition gating regen; defaults always-true."),
            new FieldSpec("min_action", Kind.REF, false).ref("#").doc("Optional EntityAction fired when the value reaches min."),
            new FieldSpec("max_action", Kind.REF, false).ref("#").doc("Optional EntityAction fired when the value reaches max."),
            new FieldSpec("hud_render", Kind.OBJECT, false).virtualObject(
                new FieldSpec("label", Kind.STRING, false).boundTo("label").def("Resource").doc("Bar label shown on the HUD."),
                new FieldSpec("color", Kind.STRING, false).boundTo("color").def("#55AAFF").doc("Bar color (hex, e.g. #55AAFF)."),
                new FieldSpec("should_render", Kind.BOOLEAN, false).boundTo("hidden").def(true).doc("When false, hides the bar (stored inverted as hidden=true)."))
                .doc("Nested HUD-render block; its keys map to flat label/color/hidden components.")));
    }

    /** Descriptor for the given canonical {@code "neoorigins:<type>"} id, or {@code null}. */
    public static PowerSpec get(String canonicalType) {
        return BY_KEY.get(canonicalType);
    }

    /** Descriptor for the given power id, or {@code null} if not registered here. */
    public static PowerSpec get(Identifier id) {
        return DESCRIPTORS.get(id);
    }

    /** True when a field-spec descriptor exists for {@code id}. */
    public static boolean isRegistered(Identifier id) {
        return DESCRIPTORS.containsKey(id);
    }

    /**
     * Declared {@link FieldSpec}s for {@code id}, or {@code null} when no
     * descriptor is registered. A registered marker-only power returns an
     * empty list (distinct from {@code null} = "not declared here, fall back").
     */
    public static List<FieldSpec> fieldsFor(Identifier id) {
        PowerSpec s = DESCRIPTORS.get(id);
        return s == null ? null : s.fields();
    }

    /** {@link FieldSpec}s for {@code id}, or {@code null} — string-keyed overload. */
    public static List<FieldSpec> fieldsFor(String canonicalType) {
        PowerSpec s = BY_KEY.get(canonicalType);
        return s == null ? null : s.fields();
    }

    /** All built-in power descriptors, in registration order. */
    public static Map<Identifier, PowerSpec> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }

    /** Canonical {@code neoorigins:<type>} id strings for every descriptor. */
    public static java.util.Set<String> ids() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (Identifier rl : DESCRIPTORS.keySet()) ids.add(rl.toString());
        return ids;
    }
}
