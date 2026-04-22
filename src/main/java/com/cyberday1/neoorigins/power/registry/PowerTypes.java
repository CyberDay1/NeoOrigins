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
    // status_effect, stacking_status_effects, night_vision, glow retired in 2.0;
    // their type IDs now alias to persistent_effect via LegacyPowerTypeAliases.
    public static final DeferredHolder<PowerType<?>, WaterBreathingPower>      WATER_BREATHING      = reg("water_breathing",      new WaterBreathingPower());
    public static final DeferredHolder<PowerType<?>, EffectImmunityPower>      EFFECT_IMMUNITY      = reg("effect_immunity",      new EffectImmunityPower());
    public static final DeferredHolder<PowerType<?>, EntityGroupPower>         ENTITY_GROUP         = reg("entity_group",         new EntityGroupPower());
    public static final DeferredHolder<PowerType<?>, EntitySetPower>           ENTITY_SET           = reg("entity_set",           new EntitySetPower());
    public static final DeferredHolder<PowerType<?>, EnhancedVisionPower>      ENHANCED_VISION      = reg("enhanced_vision",      new EnhancedVisionPower());

    // --- Passive: movement & environment ---
    public static final DeferredHolder<PowerType<?>, PreventActionPower>       PREVENT_ACTION       = reg("prevent_action",       new PreventActionPower());
    public static final DeferredHolder<PowerType<?>, FlightPower>              FLIGHT               = reg("flight",               new FlightPower());
    public static final DeferredHolder<PowerType<?>, NoSlowdownPower>          NO_SLOWDOWN          = reg("no_slowdown",          new NoSlowdownPower());
    public static final DeferredHolder<PowerType<?>, WallClimbingPower>        WALL_CLIMBING        = reg("wall_climbing",        new WallClimbingPower());
    public static final DeferredHolder<PowerType<?>, ElytraBoostPower>         ELYTRA_BOOST         = reg("elytra_boost",         new ElytraBoostPower());
    public static final DeferredHolder<PowerType<?>, SizeScalingPower>         SIZE_SCALING         = reg("size_scaling",         new SizeScalingPower());
    public static final DeferredHolder<PowerType<?>, ItemMagnetismPower>       ITEM_MAGNETISM       = reg("item_magnetism",       new ItemMagnetismPower());
    // food_restriction retired in 2.0; aliased to action_on_event.
    public static final DeferredHolder<PowerType<?>, BreakSpeedModifierPower>  BREAK_SPEED_MODIFIER = reg("break_speed_modifier", new BreakSpeedModifierPower());
    public static final DeferredHolder<PowerType<?>, UnderwaterMiningSpeedPower> UNDERWATER_MINING_SPEED = reg("underwater_mining_speed", new UnderwaterMiningSpeedPower());
    // biome_buff, damage_in_biome/daylight/water, burn_at_health_threshold,
    // regen_in_fluid retired in 2.0; aliased to condition_passive.
    public static final DeferredHolder<PowerType<?>, BreathInFluidPower>       BREATH_IN_FLUID      = reg("breath_in_fluid",      new BreathInFluidPower());
    public static final DeferredHolder<PowerType<?>, MobsIgnorePlayerPower>    MOBS_IGNORE_PLAYER   = reg("mobs_ignore_player",   new MobsIgnorePlayerPower());
    public static final DeferredHolder<PowerType<?>, NoMobSpawnsNearbyPower>   NO_MOB_SPAWNS_NEARBY = reg("no_mob_spawns_nearby", new NoMobSpawnsNearbyPower());

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

    // --- Tick-driven & conditional ---
    // 2.0 Phase 4 consolidation target: condition_passive collapses biome_buff,
    // damage_in_biome/daylight/water, burn_at_health_threshold, mobs_ignore_player,
    // no_mob_spawns_nearby, item_magnetism — and supersedes tick_action.
    public static final DeferredHolder<PowerType<?>, ConditionPassivePower>    CONDITION_PASSIVE    = reg("condition_passive",    new ConditionPassivePower());
    public static final DeferredHolder<PowerType<?>, TickActionPower>          TICK_ACTION          = reg("tick_action",          new TickActionPower());
    public static final DeferredHolder<PowerType<?>, ConditionalPower>         CONDITIONAL          = reg("conditional",          new ConditionalPower());
    public static final DeferredHolder<PowerType<?>, PhantomFormPower>         PHANTOM_FORM         = reg("phantom_form",         new PhantomFormPower());
    public static final DeferredHolder<PowerType<?>, TogglePower>              TOGGLE               = reg("toggle",               new TogglePower());
    public static final DeferredHolder<PowerType<?>, RestrictArmorPower>       RESTRICT_ARMOR       = reg("restrict_armor",       new RestrictArmorPower());
    public static final DeferredHolder<PowerType<?>, KeepInventoryPower>       KEEP_INVENTORY       = reg("keep_inventory",       new KeepInventoryPower());
    public static final DeferredHolder<PowerType<?>, ModifyPlayerSpawnPower>   MODIFY_PLAYER_SPAWN  = reg("modify_player_spawn",  new ModifyPlayerSpawnPower());
    public static final DeferredHolder<PowerType<?>, EdibleItemPower>          EDIBLE_ITEM          = reg("edible_item",          new EdibleItemPower());

    // --- Origins Classes power types ---
    public static final DeferredHolder<PowerType<?>, ExhaustionFilterPower>      EXHAUSTION_FILTER      = reg("exhaustion_filter",      new ExhaustionFilterPower());
    // better_bone_meal, more_animal_loot, longer_potions, better_enchanting,
    // efficient_repairs, better_crafted_food, teleport_range_modifier,
    // food_restriction retired in 2.0; aliased to action_on_event.
    public static final DeferredHolder<PowerType<?>, TwinBreedingPower>          TWIN_BREEDING          = reg("twin_breeding",          new TwinBreedingPower());
    public static final DeferredHolder<PowerType<?>, LessItemUseSlowdownPower>   LESS_ITEM_USE_SLOWDOWN = reg("less_item_use_slowdown", new LessItemUseSlowdownPower());
    public static final DeferredHolder<PowerType<?>, NoProjectileDivergencePower> NO_PROJECTILE_DIVERGENCE = reg("no_projectile_divergence", new NoProjectileDivergencePower());
    public static final DeferredHolder<PowerType<?>, QualityEquipmentPower>      QUALITY_EQUIPMENT      = reg("quality_equipment",      new QualityEquipmentPower());
    public static final DeferredHolder<PowerType<?>, MoreSmokerXpPower>          MORE_SMOKER_XP         = reg("more_smoker_xp",         new MoreSmokerXpPower());
    public static final DeferredHolder<PowerType<?>, TradeAvailabilityPower>     TRADE_AVAILABILITY     = reg("trade_availability",     new TradeAvailabilityPower());
    public static final DeferredHolder<PowerType<?>, RareWanderingLootPower>     RARE_WANDERING_LOOT    = reg("rare_wandering_loot",    new RareWanderingLootPower());
    public static final DeferredHolder<PowerType<?>, SneakyPower>                SNEAKY                 = reg("sneaky",                 new SneakyPower());
    public static final DeferredHolder<PowerType<?>, StealthPower>               STEALTH                = reg("stealth",                new StealthPower());
    public static final DeferredHolder<PowerType<?>, TreeFellingPower>           TREE_FELLING           = reg("tree_felling",           new TreeFellingPower());
    public static final DeferredHolder<PowerType<?>, CraftAmountBonusPower>      CRAFT_AMOUNT_BONUS     = reg("craft_amount_bonus",     new CraftAmountBonusPower());
    public static final DeferredHolder<PowerType<?>, TamedAnimalBoostPower>      TAMED_ANIMAL_BOOST     = reg("tamed_animal_boost",     new TamedAnimalBoostPower());
    public static final DeferredHolder<PowerType<?>, TamedPotionDiffusalPower>   TAMED_POTION_DIFFUSAL  = reg("tamed_potion_diffusal",  new TamedPotionDiffusalPower());

    // --- Minion summoning ---
    public static final DeferredHolder<PowerType<?>, SummonMinionPower> SUMMON_MINION = reg("summon_minion", new SummonMinionPower());

    // --- Taming & pack control ---
    public static final DeferredHolder<PowerType<?>, TameMobPower>     TAME_MOB     = reg("tame_mob",      new TameMobPower());
    public static final DeferredHolder<PowerType<?>, CommandPackPower> COMMAND_PACK = reg("command_pack",  new CommandPackPower());
    public static final DeferredHolder<PowerType<?>, HordeRegenPower>  HORDE_REGEN  = reg("horde_regen",   new HordeRegenPower());

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
    public static final DeferredHolder<PowerType<?>, ActiveGravityWellPower> GRAVITY_WELL  = reg("gravity_well",   new ActiveGravityWellPower());
    // repulse retired in 2.0; aliased to active_ability.

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(PowerTypes::onNewRegistry);
        POWER_TYPES.register(modEventBus);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        event.create(new RegistryBuilder<>(REGISTRY_KEY));
    }

    public static PowerType<?> get(Identifier id) {
        for (var holder : POWER_TYPES.getEntries())
            if (holder.getId().equals(id)) return holder.get();
        return null;
    }

    /** Reverse lookup: given a PowerType instance, return its registered identifier (or null). */
    public static Identifier getId(PowerType<?> type) {
        if (type == null) return null;
        for (var holder : POWER_TYPES.getEntries()) {
            if (holder.get() == type) return holder.getId();
        }
        return null;
    }
}
