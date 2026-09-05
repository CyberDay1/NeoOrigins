package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.EffectImmunityPower;
import com.cyberday1.neoorigins.power.builtin.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

public class PowerTypes {

    public static final ResourceKey<Registry<PowerType<?>>> REGISTRY_KEY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "power_type"));

    public static final DeferredRegister<PowerType<?>> POWER_TYPES =
        DeferredRegister.create(REGISTRY_KEY, NeoOrigins.MOD_ID);

    private static <T extends PowerType<?>> DeferredHolder<PowerType<?>, T> reg(String id, T instance) {
        return POWER_TYPES.register(id, () -> instance);
    }

    // --- Passive: attribute & status ---
    // 2.0 Phase 2 consolidation target: persistent_effect collapses status_effect +
    // stacking_status_effects + night_vision + glow + water_breathing + breath_in_fluid +
    // regen_in_fluid. Legacy types below remain during the deprecation window.
    public static final DeferredHolder<PowerType<?>, PersistentEffectPower>    PERSISTENT_EFFECT    = reg("persistent_effect",    new PersistentEffectPower());
    public static final DeferredHolder<PowerType<?>, AttributeModifierPower>   ATTRIBUTE_MODIFIER   = reg("attribute_modifier",   new AttributeModifierPower());
    public static final DeferredHolder<PowerType<?>, InvisibilityPower>        INVISIBILITY         = reg("invisibility",         new InvisibilityPower());
    // status_effect, stacking_status_effects, night_vision, glow retired in 2.0;
    // their type IDs now alias to persistent_effect via LegacyPowerTypeAliases.
    public static final DeferredHolder<PowerType<?>, WaterBreathingPower>      WATER_BREATHING      = reg("water_breathing",      new WaterBreathingPower());
    public static final DeferredHolder<PowerType<?>, EffectImmunityPower>      EFFECT_IMMUNITY      = reg("effect_immunity",      new EffectImmunityPower());
    public static final DeferredHolder<PowerType<?>, EntityGroupPower>         ENTITY_GROUP         = reg("entity_group",         new EntityGroupPower());
    public static final DeferredHolder<PowerType<?>, EntitySetPower>           ENTITY_SET           = reg("entity_set",           new EntitySetPower());
    public static final DeferredHolder<PowerType<?>, EnhancedVisionPower>      ENHANCED_VISION      = reg("enhanced_vision",      new EnhancedVisionPower());
    public static final DeferredHolder<PowerType<?>, HideHudBarPower>          HIDE_HUD_BAR         = reg("hide_hud_bar",         new HideHudBarPower());
    public static final DeferredHolder<PowerType<?>, CobwebAffinityPower>      COBWEB_AFFINITY      = reg("cobweb_affinity",      new CobwebAffinityPower());
    public static final DeferredHolder<PowerType<?>, EnderGazeImmunityPower>   ENDER_GAZE_IMMUNITY  = reg("ender_gaze_immunity",  new EnderGazeImmunityPower());
    public static final DeferredHolder<PowerType<?>, XenoPassivePower>         XENO_PASSIVE         = reg("xeno_passive",         new XenoPassivePower());

    // --- Passive: movement & environment ---
    public static final DeferredHolder<PowerType<?>, PreventActionPower>       PREVENT_ACTION       = reg("prevent_action",       new PreventActionPower());
    public static final DeferredHolder<PowerType<?>, PreventEntityRenderPower> PREVENT_ENTITY_RENDER = reg("prevent_entity_render", new PreventEntityRenderPower());
    public static final DeferredHolder<PowerType<?>, FlightPower>              FLIGHT               = reg("flight",               new FlightPower());
    public static final DeferredHolder<PowerType<?>, CreativeFlightPower>      CREATIVE_FLIGHT      = reg("creative_flight",      new CreativeFlightPower());
    public static final DeferredHolder<PowerType<?>, NoSlowdownPower>          NO_SLOWDOWN          = reg("no_slowdown",          new NoSlowdownPower());
    public static final DeferredHolder<PowerType<?>, WallClimbingPower>        WALL_CLIMBING        = reg("wall_climbing",        new WallClimbingPower());
    public static final DeferredHolder<PowerType<?>, ElytraBoostPower>         ELYTRA_BOOST         = reg("elytra_boost",         new ElytraBoostPower());
    public static final DeferredHolder<PowerType<?>, NaturalGlidePower>        NATURAL_GLIDE        = reg("natural_glide",        new NaturalGlidePower());
    public static final DeferredHolder<PowerType<?>, ElytraFlightPower>        ELYTRA_FLIGHT        = reg("elytra_flight",        new ElytraFlightPower());
    public static final DeferredHolder<PowerType<?>, SizeScalingPower>         SIZE_SCALING         = reg("size_scaling",         new SizeScalingPower());
    public static final DeferredHolder<PowerType<?>, BounceOnLandPower>        BOUNCE_ON_LAND       = reg("bounce_on_land",       new BounceOnLandPower());
    public static final DeferredHolder<PowerType<?>, ItemMagnetismPower>       ITEM_MAGNETISM       = reg("item_magnetism",       new ItemMagnetismPower());
    // food_restriction retired in 2.0; aliased to action_on_event.
    public static final DeferredHolder<PowerType<?>, BreakSpeedModifierPower>  BREAK_SPEED_MODIFIER = reg("break_speed_modifier", new BreakSpeedModifierPower());
    public static final DeferredHolder<PowerType<?>, UnderwaterMiningSpeedPower> UNDERWATER_MINING_SPEED = reg("underwater_mining_speed", new UnderwaterMiningSpeedPower());
    public static final DeferredHolder<PowerType<?>, BareHandToolPower>        BARE_HAND_TOOL       = reg("bare_hand_tool",       new BareHandToolPower());
    public static final DeferredHolder<PowerType<?>, FortuneWhenEffectPower>   FORTUNE_WHEN_EFFECT  = reg("fortune_when_effect",  new FortuneWhenEffectPower());
    // biome_buff, damage_in_biome/daylight/water, burn_at_health_threshold,
    // regen_in_fluid retired in 2.0; aliased to condition_passive.
    public static final DeferredHolder<PowerType<?>, BreathInFluidPower>       BREATH_IN_FLUID      = reg("breath_in_fluid",      new BreathInFluidPower());
    public static final DeferredHolder<PowerType<?>, BreathOutOfFluidPower>    BREATH_OUT_OF_FLUID  = reg("breath_out_of_fluid",  new BreathOutOfFluidPower());
    public static final DeferredHolder<PowerType<?>, MobsIgnorePlayerPower>    MOBS_IGNORE_PLAYER   = reg("mobs_ignore_player",   new MobsIgnorePlayerPower());
    public static final DeferredHolder<PowerType<?>, MobsTargetPlayerPower>    MOBS_TARGET_PLAYER   = reg("mobs_target_player",   new MobsTargetPlayerPower());
    public static final DeferredHolder<PowerType<?>, NoMobSpawnsNearbyPower>   NO_MOB_SPAWNS_NEARBY = reg("no_mob_spawns_nearby", new NoMobSpawnsNearbyPower());
    public static final DeferredHolder<PowerType<?>, BurnPower>                BURN                 = reg("burn",                new BurnPower());
    public static final DeferredHolder<PowerType<?>, IgnoreWaterPower>         IGNORE_WATER         = reg("ignore_water",        new IgnoreWaterPower());
    public static final DeferredHolder<PowerType<?>, IgnoreFluidPower>         IGNORE_FLUID         = reg("ignore_fluid",        new IgnoreFluidPower());
    public static final DeferredHolder<PowerType<?>, WalkOnFluidPower>         WALK_ON_FLUID        = reg("walk_on_fluid",       new WalkOnFluidPower());
    public static final DeferredHolder<PowerType<?>, ExtraInventoryPower>      EXTRA_INVENTORY      = reg("extra_inventory",     new ExtraInventoryPower());
    public static final DeferredHolder<PowerType<?>, LavaVisionPower>          LAVA_VISION          = reg("lava_vision",         new LavaVisionPower());
    public static final DeferredHolder<PowerType<?>, ModifyLavaSpeedPower>    MODIFY_LAVA_SPEED    = reg("modify_lava_speed",   new ModifyLavaSpeedPower());
    public static final DeferredHolder<PowerType<?>, ModifyFlightSpeedPower>  MODIFY_FLIGHT_SPEED  = reg("modify_flight_speed", new ModifyFlightSpeedPower());
    public static final DeferredHolder<PowerType<?>, OverlayPower>             OVERLAY              = reg("overlay",             new OverlayPower());
    public static final DeferredHolder<PowerType<?>, ModelColorPower>          MODEL_COLOR          = reg("model_color",         new ModelColorPower());
    public static final DeferredHolder<PowerType<?>, EntityModelPower>         ENTITY_MODEL         = reg("entity_model",        new EntityModelPower());
    public static final DeferredHolder<PowerType<?>, ShaderPower>              SHADER               = reg("shader",              new ShaderPower());

    // --- Passive: combat ---
    // 2.0 Phase 6 consolidation target: one power covering ~26 Origins-Classes
    // hook types (crafting/food/xp/bonemeal/breed/trade/...) via action+modifier DSL.
    // Phase 5's separate `event_triggered` type was folded into this one.
    public static final DeferredHolder<PowerType<?>, ActionOnEventPower>       ACTION_ON_EVENT      = reg("action_on_event",      new ActionOnEventPower());
    public static final DeferredHolder<PowerType<?>, ModifyDamagePower>        MODIFY_DAMAGE        = reg("modify_damage",        new ModifyDamagePower());
    public static final DeferredHolder<PowerType<?>, InvulnerabilityPower>     INVULNERABILITY      = reg("invulnerability",      new InvulnerabilityPower());
    // knockback_modifier, thorns_aura, action_on_kill, action_on_hit_taken
    // retired in 2.0; aliased to action_on_event.
    public static final DeferredHolder<PowerType<?>, ProjectileImmunityPower>  PROJECTILE_IMMUNITY  = reg("projectile_immunity",  new ProjectileImmunityPower());
    public static final DeferredHolder<PowerType<?>, ScareEntitiesPower>       SCARE_ENTITIES       = reg("scare_entities",       new ScareEntitiesPower());
    public static final DeferredHolder<PowerType<?>, ActionOnHitPower>         ACTION_ON_HIT        = reg("action_on_hit",        new ActionOnHitPower());

    // --- Passive: scalars & misc ---
    // hunger_drain_modifier, natural_regen_modifier retired in 2.0; aliased to action_on_event.
    public static final DeferredHolder<PowerType<?>, NoNaturalRegenPower>      NO_NATURAL_REGEN     = reg("no_natural_regen",     new NoNaturalRegenPower());
    public static final DeferredHolder<PowerType<?>, CropGrowthAcceleratorPower> CROP_GROWTH_ACCELERATOR = reg("crop_growth_accelerator", new CropGrowthAcceleratorPower());
    public static final DeferredHolder<PowerType<?>, CropHarvestBonusPower>    CROP_HARVEST_BONUS   = reg("crop_harvest_bonus",   new CropHarvestBonusPower());
    public static final DeferredHolder<PowerType<?>, StartingEquipmentPower>   STARTING_EQUIPMENT   = reg("starting_equipment",   new StartingEquipmentPower());
    // v2.1.6 backlog #3 — loot-table-driven active grant (FTBQ soft-compat
    // available via FtbQuestsCompat when ftbquests is on the mod list).
    public static final DeferredHolder<PowerType<?>, LootPoolGrantPower>       LOOT_POOL_GRANT      = reg("loot_pool_grant",      new LootPoolGrantPower());
    // Loot-table-native kill drops: while held, killing a matching mob type has
    // a per-rule chance to also drop an item FROM the mob's loot (real world
    // drops via a global loot modifier keyed on the killer), not an inventory
    // insert. Companion GLM: KillLootDropsLootModifier.
    public static final DeferredHolder<PowerType<?>, KillLootDropsPower>       KILL_LOOT_DROPS      = reg("kill_loot_drops",      new KillLootDropsPower());

    // --- Tick-driven & conditional ---
    // 2.0 Phase 4 consolidation target: condition_passive collapses biome_buff,
    // damage_in_biome/daylight/water, burn_at_health_threshold, mobs_ignore_player,
    // no_mob_spawns_nearby, item_magnetism — and supersedes tick_action.
    public static final DeferredHolder<PowerType<?>, ConditionPassivePower>    CONDITION_PASSIVE    = reg("condition_passive",    new ConditionPassivePower());
    // Unified aura / effect-over-time: one entity_action tree, three modes via
    // the `activation` field (passive | active | toggle). Bridges the gap that
    // previously forced authors to pick active_ability OR condition_passive.
    public static final DeferredHolder<PowerType<?>, EffectOverTimePower>      EFFECT_OVER_TIME     = reg("effect_over_time",     new EffectOverTimePower());
    public static final DeferredHolder<PowerType<?>, MobBehaviorPower>         MOB_BEHAVIOR         = reg("mob_behavior",         new MobBehaviorPower());
    public static final DeferredHolder<PowerType<?>, TickActionPower>          TICK_ACTION          = reg("tick_action",          new TickActionPower());
    public static final DeferredHolder<PowerType<?>, ConditionalPower>         CONDITIONAL          = reg("conditional",          new ConditionalPower());
    public static final DeferredHolder<PowerType<?>, PhantomFormPower>         PHANTOM_FORM         = reg("phantom_form",         new PhantomFormPower());
    public static final DeferredHolder<PowerType<?>, TogglePower>              TOGGLE               = reg("toggle",               new TogglePower());
    public static final DeferredHolder<PowerType<?>, ParticlePower>            PARTICLE             = reg("particle",             new ParticlePower());
    public static final DeferredHolder<PowerType<?>, RestrictArmorPower>       RESTRICT_ARMOR       = reg("restrict_armor",       new RestrictArmorPower());
    public static final DeferredHolder<PowerType<?>, RestrictItemsPower>       RESTRICT_ITEMS       = reg("restrict_items",       new RestrictItemsPower());
    public static final DeferredHolder<PowerType<?>, KeepInventoryPower>       KEEP_INVENTORY       = reg("keep_inventory",       new KeepInventoryPower());
    public static final DeferredHolder<PowerType<?>, ModifyPlayerSpawnPower>   MODIFY_PLAYER_SPAWN  = reg("modify_player_spawn",  new ModifyPlayerSpawnPower());
    public static final DeferredHolder<PowerType<?>, EdibleItemPower>          EDIBLE_ITEM          = reg("edible_item",          new EdibleItemPower());
    // Display-only marker (origins:simple equivalent) — no behavior; carries name+description only.
    public static final DeferredHolder<PowerType<?>, SimplePower>              SIMPLE               = reg("simple",               new SimplePower());

    // --- Origins Classes power types ---
    public static final DeferredHolder<PowerType<?>, ExhaustionFilterPower>      EXHAUSTION_FILTER      = reg("exhaustion_filter",      new ExhaustionFilterPower());
    // better_bone_meal, more_animal_loot, longer_potions, better_enchanting,
    // efficient_repairs, better_crafted_food, teleport_range_modifier,
    // food_restriction retired in 2.0; aliased to action_on_event.
    public static final DeferredHolder<PowerType<?>, TwinBreedingPower>          TWIN_BREEDING          = reg("twin_breeding",          new TwinBreedingPower());
    public static final DeferredHolder<PowerType<?>, LessItemUseSlowdownPower>   LESS_ITEM_USE_SLOWDOWN = reg("less_item_use_slowdown", new LessItemUseSlowdownPower());
    public static final DeferredHolder<PowerType<?>, PreventItemDamagePower>     PREVENT_ITEM_DAMAGE    = reg("prevent_item_damage",    new PreventItemDamagePower());
    public static final DeferredHolder<PowerType<?>, AttractMobsPower>           ATTRACT_MOBS           = reg("attract_mobs",           new AttractMobsPower());
    public static final DeferredHolder<PowerType<?>, NoProjectileDivergencePower> NO_PROJECTILE_DIVERGENCE = reg("no_projectile_divergence", new NoProjectileDivergencePower());
    public static final DeferredHolder<PowerType<?>, QualityEquipmentPower>      QUALITY_EQUIPMENT      = reg("quality_equipment",      new QualityEquipmentPower());
    public static final DeferredHolder<PowerType<?>, MoreSmokerXpPower>          MORE_SMOKER_XP         = reg("more_smoker_xp",         new MoreSmokerXpPower());
    public static final DeferredHolder<PowerType<?>, ModifyFoodNutritionPower>  MODIFY_FOOD_NUTRITION  = reg("modify_food_nutrition",  new ModifyFoodNutritionPower());
    public static final DeferredHolder<PowerType<?>, TradeAvailabilityPower>     TRADE_AVAILABILITY     = reg("trade_availability",     new TradeAvailabilityPower());
    public static final DeferredHolder<PowerType<?>, RareWanderingLootPower>     RARE_WANDERING_LOOT    = reg("rare_wandering_loot",    new RareWanderingLootPower());
    public static final DeferredHolder<PowerType<?>, WraithPhasePower>          WRAITH_PHASE           = reg("wraith_phase",           new WraithPhasePower());
    public static final DeferredHolder<PowerType<?>, SlimeMoisturePower>       SLIME_MOISTURE         = reg("slime_moisture",         new SlimeMoisturePower());
    public static final DeferredHolder<PowerType<?>, com.cyberday1.neoorigins.power.builtin.ResourcePower> RESOURCE = reg("resource", new com.cyberday1.neoorigins.power.builtin.ResourcePower());
    public static final DeferredHolder<PowerType<?>, com.cyberday1.neoorigins.power.builtin.VariablePower> VARIABLE = reg("variable", new com.cyberday1.neoorigins.power.builtin.VariablePower());
    public static final DeferredHolder<PowerType<?>, SlimeDeathSavePower>      SLIME_DEATH_SAVE       = reg("slime_death_save",       new SlimeDeathSavePower());
    public static final DeferredHolder<PowerType<?>, com.cyberday1.neoorigins.power.builtin.PreventDeathPower> PREVENT_DEATH = reg("prevent_death", new com.cyberday1.neoorigins.power.builtin.PreventDeathPower());
    public static final DeferredHolder<PowerType<?>, SlimeLevelHPPower>        SLIME_LEVEL_HP         = reg("slime_level_hp",         new SlimeLevelHPPower());
    public static final DeferredHolder<PowerType<?>, ThornsOnHitPower>         THORNS_ON_HIT          = reg("thorns_on_hit",          new ThornsOnHitPower());
    public static final DeferredHolder<PowerType<?>, LightLevelEffectPower>    LIGHT_LEVEL_EFFECT     = reg("light_level_effect",     new LightLevelEffectPower());
    public static final DeferredHolder<PowerType<?>, LowHPThresholdPower>      LOW_HP_THRESHOLD       = reg("low_hp_threshold",       new LowHPThresholdPower());
    public static final DeferredHolder<PowerType<?>, DodgeChancePower>         DODGE_CHANCE           = reg("dodge_chance",           new DodgeChancePower());
    public static final DeferredHolder<PowerType<?>, SneakyPower>                SNEAKY                 = reg("sneaky",                 new SneakyPower());
    public static final DeferredHolder<PowerType<?>, MuffleSoundPower>           MUFFLE_SOUND           = reg("muffle_sound",           new MuffleSoundPower());
    public static final DeferredHolder<PowerType<?>, StealthPower>               STEALTH                = reg("stealth",                new StealthPower());
    public static final DeferredHolder<PowerType<?>, TreeFellingPower>           TREE_FELLING           = reg("tree_felling",           new TreeFellingPower());
    // Soft-dep on ftbultimine — marker power; the FtbUltimineCompat bridge gates
    // FTB Ultimine vein-mining to active holders. Inert when ftbultimine is absent.
    public static final DeferredHolder<PowerType<?>, UltiminePower>              ULTIMINE               = reg("ultimine",               new UltiminePower());
    public static final DeferredHolder<PowerType<?>, CraftAmountBonusPower>      CRAFT_AMOUNT_BONUS     = reg("craft_amount_bonus",     new CraftAmountBonusPower());
    public static final DeferredHolder<PowerType<?>, TamedAnimalBoostPower>      TAMED_ANIMAL_BOOST     = reg("tamed_animal_boost",     new TamedAnimalBoostPower());
    public static final DeferredHolder<PowerType<?>, TamedPotionDiffusalPower>   TAMED_POTION_DIFFUSAL  = reg("tamed_potion_diffusal",  new TamedPotionDiffusalPower());

    // --- Dragon Survival soft-compat ---
    // Hooks DS's dragon state when `dragonsurvival` is loaded: turns the holder
    // into the configured species and reverts on revoke. Inert (no-op) when DS
    // is absent; dragon origins also gate on `required_mods` so neither the
    // origin nor this power loads without the mod present.
    public static final DeferredHolder<PowerType<?>, BecomeDragonPower>       BECOME_DRAGON        = reg("become_dragon",       new BecomeDragonPower());

    // --- Minion summoning ---
    public static final DeferredHolder<PowerType<?>, SummonMinionPower> SUMMON_MINION = reg("summon_minion", new SummonMinionPower());

    // --- Taming & pack control ---
    public static final DeferredHolder<PowerType<?>, TameMobPower>     TAME_MOB     = reg("tame_mob",      new TameMobPower());
    public static final DeferredHolder<PowerType<?>, CommandPackPower> COMMAND_PACK = reg("command_pack",  new CommandPackPower());
    public static final DeferredHolder<PowerType<?>, HordeRegenPower>  HORDE_REGEN  = reg("horde_regen",   new HordeRegenPower());

    // --- Mounting ---
    public static final DeferredHolder<PowerType<?>, MountPower>       MOUNT        = reg("mount",         new MountPower());

    // --- Active abilities ---
    // 2.0 consolidation: generic action-driven active ability. Legacy types below stay
    // registered during the deprecation window (see LegacyPowerTypeAliases).
    public static final DeferredHolder<PowerType<?>, ActiveAbilityPower>       ACTIVE_ABILITY       = reg("active_ability",       new ActiveAbilityPower());
    public static final DeferredHolder<PowerType<?>, ActiveTeleportPower>      ACTIVE_TELEPORT      = reg("active_teleport",      new ActiveTeleportPower());
    public static final DeferredHolder<PowerType<?>, ActiveDashPower>          ACTIVE_DASH          = reg("active_dash",          new ActiveDashPower());
    // active_launch retired in 2.0; aliased to active_ability.
    public static final DeferredHolder<PowerType<?>, ActiveRecallPower>        ACTIVE_RECALL        = reg("active_recall",        new ActiveRecallPower());
    public static final DeferredHolder<PowerType<?>, ActiveSwapPower>          ACTIVE_SWAP          = reg("active_swap",          new ActiveSwapPower());
    public static final DeferredHolder<PowerType<?>, ActiveFireballPower>      ACTIVE_FIREBALL      = reg("active_fireball",      new ActiveFireballPower());
    public static final DeferredHolder<PowerType<?>, ActiveBoltPower>          ACTIVE_BOLT          = reg("active_bolt",          new ActiveBoltPower());
    public static final DeferredHolder<PowerType<?>, ActivePhasePower>         ACTIVE_PHASE         = reg("active_phase",         new ActivePhasePower());
    // active_aoe_effect retired in 2.0; aliased to active_ability.
    public static final DeferredHolder<PowerType<?>, ActivePlaceBlockPower>    ACTIVE_PLACE_BLOCK   = reg("active_place_block",   new ActivePlaceBlockPower());
    public static final DeferredHolder<PowerType<?>, ShadowOrbPower>           SHADOW_ORB           = reg("shadow_orb",           new ShadowOrbPower());

    // --- Elemental mage abilities ---
    public static final DeferredHolder<PowerType<?>, ActiveGroundSlamPower>   GROUND_SLAM   = reg("ground_slam",    new ActiveGroundSlamPower());
    public static final DeferredHolder<PowerType<?>, ActiveTidalWavePower>   TIDAL_WAVE    = reg("tidal_wave",     new ActiveTidalWavePower());
    // healing_mist retired in 2.0; aliased to active_ability.
    // gravity_well retired in 2.0; the in-tree gravity_mage_well power now uses
    // spawn_projectile + on_hit_action: spawn_black_hole via the VFX pipeline.
    // repulse retired in 2.0; aliased to active_ability.

    /**
     * Paths of every built-in power type, read from {@link #POWER_TYPES}' own
     * entry list. Declared last on purpose: static initializers run in textual
     * order, so every {@code reg(...)} above has already run by this point.
     */
    private static final java.util.Set<String> BUILTIN_PATHS =
        POWER_TYPES.getEntries().stream()
            .map(h -> h.getId().getPath())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /**
     * True when {@code neoorigins:<path>} is one of our built-in power types.
     *
     * <p>{@link #get} deliberately consults {@link #REGISTRY} so that addon-mod
     * types are visible to it. This asks a narrower question — is the name taken
     * by one of OURS — and the {@code neoorigins:} namespace is ours alone, so the
     * DeferredRegister is a complete answer for it.
     *
     * <p>The difference that matters is <b>when</b>. {@code REGISTRY} stays null
     * until {@code NewRegistryEvent} fires, and {@link #get} answers null for
     * everything while it is — so outside a running game a guard written on
     * {@code get} gives whichever answer the initialisation order happens to
     * produce. It reads "no such native type" for every name in the headless
     * harnesses ({@code compatTest}, {@code goldenMaster}, {@code schemaFormCheck}),
     * and under JUnit it depends on which classes ran first. This set is populated
     * the moment the class loads, so it answers the same everywhere.
     */
    public static boolean isBuiltinPath(String path) {
        return BUILTIN_PATHS.contains(path);
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(PowerTypes::onNewRegistry);
        POWER_TYPES.register(modEventBus);
    }

    /**
     * Live reference to the {@code neoorigins:power_type} registry, captured
     * from {@link NewRegistryEvent#create(RegistryBuilder)}. We look up types
     * through this rather than {@link #POWER_TYPES}{@code .getEntries()} so
     * that addon-mod power types — registered via their own DeferredRegister
     * keyed on {@link #REGISTRY_KEY} or via the lower-level RegisterEvent —
     * are visible to {@link #get} and {@link #getId}. Without this, addon
     * power JSONs silently failed to load (issue #40) and their types were
     * effectively unreachable from {@code PowerDataManager}.
     */
    private static net.minecraft.core.Registry<PowerType<?>> REGISTRY;

    private static void onNewRegistry(NewRegistryEvent event) {
        REGISTRY = event.create(new RegistryBuilder<>(REGISTRY_KEY));
    }

    public static PowerType<?> get(Identifier id) {
        if (REGISTRY == null) return null;
        return REGISTRY.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }

    /** Reverse lookup: given a PowerType instance, return its registered identifier (or null). */
    public static Identifier getId(PowerType<?> type) {
        if (type == null || REGISTRY == null) return null;
        return REGISTRY.getKey(type);
    }
}
