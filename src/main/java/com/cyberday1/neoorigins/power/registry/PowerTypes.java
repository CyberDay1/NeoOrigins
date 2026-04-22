package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.EffectImmunityPower;
import com.cyberday1.neoorigins.power.builtin.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

public class PowerTypes {

    public static final ResourceKey<Registry<PowerType<?>>> REGISTRY_KEY =
        ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "power_type"));

    public static final DeferredRegister<PowerType<?>> POWER_TYPES =
        DeferredRegister.create(REGISTRY_KEY, NeoOrigins.MOD_ID);

    private static <T extends PowerType<?>> DeferredHolder<PowerType<?>, T> reg(String id, T instance) {
        return POWER_TYPES.register(id, () -> instance);
    }

    // --- Passive: attribute & status ---
    public static final DeferredHolder<PowerType<?>, AttributeModifierPower>   ATTRIBUTE_MODIFIER   = reg("attribute_modifier",   new AttributeModifierPower());
    public static final DeferredHolder<PowerType<?>, StatusEffectPower>        STATUS_EFFECT        = reg("status_effect",        new StatusEffectPower());
    public static final DeferredHolder<PowerType<?>, NightVisionPower>         NIGHT_VISION         = reg("night_vision",         new NightVisionPower());
    public static final DeferredHolder<PowerType<?>, GlowPower>                GLOW                 = reg("glow",                 new GlowPower());
    public static final DeferredHolder<PowerType<?>, WaterBreathingPower>      WATER_BREATHING      = reg("water_breathing",      new WaterBreathingPower());
    public static final DeferredHolder<PowerType<?>, EffectImmunityPower>      EFFECT_IMMUNITY      = reg("effect_immunity",      new EffectImmunityPower());
    public static final DeferredHolder<PowerType<?>, EntityGroupPower>         ENTITY_GROUP         = reg("entity_group",         new EntityGroupPower());

    // --- Passive: movement & environment ---
    public static final DeferredHolder<PowerType<?>, PreventActionPower>       PREVENT_ACTION       = reg("prevent_action",       new PreventActionPower());
    public static final DeferredHolder<PowerType<?>, FlightPower>              FLIGHT               = reg("flight",               new FlightPower());
    public static final DeferredHolder<PowerType<?>, NoSlowdownPower>          NO_SLOWDOWN          = reg("no_slowdown",          new NoSlowdownPower());
    public static final DeferredHolder<PowerType<?>, WallClimbingPower>        WALL_CLIMBING        = reg("wall_climbing",        new WallClimbingPower());
    public static final DeferredHolder<PowerType<?>, ElytraBoostPower>         ELYTRA_BOOST         = reg("elytra_boost",         new ElytraBoostPower());
    public static final DeferredHolder<PowerType<?>, SizeScalingPower>         SIZE_SCALING         = reg("size_scaling",         new SizeScalingPower());
    public static final DeferredHolder<PowerType<?>, ItemMagnetismPower>       ITEM_MAGNETISM       = reg("item_magnetism",       new ItemMagnetismPower());
    public static final DeferredHolder<PowerType<?>, FoodRestrictionPower>     FOOD_RESTRICTION     = reg("food_restriction",     new FoodRestrictionPower());
    public static final DeferredHolder<PowerType<?>, BreakSpeedModifierPower>  BREAK_SPEED_MODIFIER = reg("break_speed_modifier", new BreakSpeedModifierPower());
    public static final DeferredHolder<PowerType<?>, UnderwaterMiningSpeedPower> UNDERWATER_MINING_SPEED = reg("underwater_mining_speed", new UnderwaterMiningSpeedPower());
    public static final DeferredHolder<PowerType<?>, BiomeBuffPower>           BIOME_BUFF           = reg("biome_buff",           new BiomeBuffPower());
    public static final DeferredHolder<PowerType<?>, DamageInBiomePower>       DAMAGE_IN_BIOME      = reg("damage_in_biome",      new DamageInBiomePower());
    public static final DeferredHolder<PowerType<?>, DamageInDaylightPower>    DAMAGE_IN_DAYLIGHT   = reg("damage_in_daylight",   new DamageInDaylightPower());
    public static final DeferredHolder<PowerType<?>, DamageInWaterPower>      DAMAGE_IN_WATER      = reg("damage_in_water",      new DamageInWaterPower());
    public static final DeferredHolder<PowerType<?>, BurnAtHealthThresholdPower> BURN_AT_HEALTH_THRESHOLD = reg("burn_at_health_threshold", new BurnAtHealthThresholdPower());
    public static final DeferredHolder<PowerType<?>, RegenInFluidPower>        REGEN_IN_FLUID       = reg("regen_in_fluid",       new RegenInFluidPower());
    public static final DeferredHolder<PowerType<?>, BreathInFluidPower>       BREATH_IN_FLUID      = reg("breath_in_fluid",      new BreathInFluidPower());
    public static final DeferredHolder<PowerType<?>, MobsIgnorePlayerPower>    MOBS_IGNORE_PLAYER   = reg("mobs_ignore_player",   new MobsIgnorePlayerPower());
    public static final DeferredHolder<PowerType<?>, NoMobSpawnsNearbyPower>   NO_MOB_SPAWNS_NEARBY = reg("no_mob_spawns_nearby", new NoMobSpawnsNearbyPower());

    // --- Passive: combat ---
    public static final DeferredHolder<PowerType<?>, ModifyDamagePower>        MODIFY_DAMAGE        = reg("modify_damage",        new ModifyDamagePower());
    public static final DeferredHolder<PowerType<?>, KnockbackModifierPower>   KNOCKBACK_MODIFIER   = reg("knockback_modifier",   new KnockbackModifierPower());
    public static final DeferredHolder<PowerType<?>, ThornsAuraPower>          THORNS_AURA          = reg("thorns_aura",          new ThornsAuraPower());
    public static final DeferredHolder<PowerType<?>, ProjectileImmunityPower>  PROJECTILE_IMMUNITY  = reg("projectile_immunity",  new ProjectileImmunityPower());
    public static final DeferredHolder<PowerType<?>, ScareEntitiesPower>       SCARE_ENTITIES       = reg("scare_entities",       new ScareEntitiesPower());
    public static final DeferredHolder<PowerType<?>, ActionOnKillPower>        ACTION_ON_KILL       = reg("action_on_kill",       new ActionOnKillPower());
    public static final DeferredHolder<PowerType<?>, ActionOnHitTakenPower>    ACTION_ON_HIT_TAKEN  = reg("action_on_hit_taken",  new ActionOnHitTakenPower());
    public static final DeferredHolder<PowerType<?>, ActionOnHitPower>         ACTION_ON_HIT        = reg("action_on_hit",        new ActionOnHitPower());

    // --- Passive: scalars & misc ---
    public static final DeferredHolder<PowerType<?>, HungerDrainModifierPower> HUNGER_DRAIN_MODIFIER = reg("hunger_drain_modifier", new HungerDrainModifierPower());
    public static final DeferredHolder<PowerType<?>, NaturalRegenModifierPower> NATURAL_REGEN_MODIFIER = reg("natural_regen_modifier", new NaturalRegenModifierPower());
    public static final DeferredHolder<PowerType<?>, NoNaturalRegenPower>      NO_NATURAL_REGEN     = reg("no_natural_regen",     new NoNaturalRegenPower());
    public static final DeferredHolder<PowerType<?>, CropGrowthAcceleratorPower> CROP_GROWTH_ACCELERATOR = reg("crop_growth_accelerator", new CropGrowthAcceleratorPower());
    public static final DeferredHolder<PowerType<?>, CropHarvestBonusPower>    CROP_HARVEST_BONUS   = reg("crop_harvest_bonus",   new CropHarvestBonusPower());
    public static final DeferredHolder<PowerType<?>, StartingEquipmentPower>   STARTING_EQUIPMENT   = reg("starting_equipment",   new StartingEquipmentPower());

    // --- Tick-driven & conditional ---
    public static final DeferredHolder<PowerType<?>, TickActionPower>          TICK_ACTION          = reg("tick_action",          new TickActionPower());
    public static final DeferredHolder<PowerType<?>, ConditionalPower>         CONDITIONAL          = reg("conditional",          new ConditionalPower());
    public static final DeferredHolder<PowerType<?>, PhantomFormPower>         PHANTOM_FORM         = reg("phantom_form",         new PhantomFormPower());

    // --- Origins Classes power types ---
    public static final DeferredHolder<PowerType<?>, ExhaustionFilterPower>      EXHAUSTION_FILTER      = reg("exhaustion_filter",      new ExhaustionFilterPower());
    public static final DeferredHolder<PowerType<?>, BetterBoneMealPower>        BETTER_BONE_MEAL       = reg("better_bone_meal",       new BetterBoneMealPower());
    public static final DeferredHolder<PowerType<?>, MoreAnimalLootPower>        MORE_ANIMAL_LOOT       = reg("more_animal_loot",       new MoreAnimalLootPower());
    public static final DeferredHolder<PowerType<?>, TwinBreedingPower>          TWIN_BREEDING          = reg("twin_breeding",          new TwinBreedingPower());
    public static final DeferredHolder<PowerType<?>, LessItemUseSlowdownPower>   LESS_ITEM_USE_SLOWDOWN = reg("less_item_use_slowdown", new LessItemUseSlowdownPower());
    public static final DeferredHolder<PowerType<?>, NoProjectileDivergencePower> NO_PROJECTILE_DIVERGENCE = reg("no_projectile_divergence", new NoProjectileDivergencePower());
    public static final DeferredHolder<PowerType<?>, LongerPotionsPower>         LONGER_POTIONS         = reg("longer_potions",         new LongerPotionsPower());
    public static final DeferredHolder<PowerType<?>, BetterEnchantingPower>      BETTER_ENCHANTING      = reg("better_enchanting",      new BetterEnchantingPower());
    public static final DeferredHolder<PowerType<?>, EfficientRepairsPower>      EFFICIENT_REPAIRS      = reg("efficient_repairs",      new EfficientRepairsPower());
    public static final DeferredHolder<PowerType<?>, QualityEquipmentPower>      QUALITY_EQUIPMENT      = reg("quality_equipment",      new QualityEquipmentPower());
    public static final DeferredHolder<PowerType<?>, BetterCraftedFoodPower>     BETTER_CRAFTED_FOOD    = reg("better_crafted_food",    new BetterCraftedFoodPower());
    public static final DeferredHolder<PowerType<?>, MoreSmokerXpPower>          MORE_SMOKER_XP         = reg("more_smoker_xp",         new MoreSmokerXpPower());
    public static final DeferredHolder<PowerType<?>, TradeAvailabilityPower>     TRADE_AVAILABILITY     = reg("trade_availability",     new TradeAvailabilityPower());
    public static final DeferredHolder<PowerType<?>, RareWanderingLootPower>     RARE_WANDERING_LOOT    = reg("rare_wandering_loot",    new RareWanderingLootPower());
    public static final DeferredHolder<PowerType<?>, SneakyPower>                SNEAKY                 = reg("sneaky",                 new SneakyPower());
    public static final DeferredHolder<PowerType<?>, StealthPower>               STEALTH                = reg("stealth",                new StealthPower());
    public static final DeferredHolder<PowerType<?>, TreeFellingPower>           TREE_FELLING           = reg("tree_felling",           new TreeFellingPower());
    public static final DeferredHolder<PowerType<?>, CraftAmountBonusPower>      CRAFT_AMOUNT_BONUS     = reg("craft_amount_bonus",     new CraftAmountBonusPower());
    public static final DeferredHolder<PowerType<?>, TamedAnimalBoostPower>      TAMED_ANIMAL_BOOST     = reg("tamed_animal_boost",     new TamedAnimalBoostPower());
    public static final DeferredHolder<PowerType<?>, TamedPotionDiffusalPower>   TAMED_POTION_DIFFUSAL  = reg("tamed_potion_diffusal",  new TamedPotionDiffusalPower());
    public static final DeferredHolder<PowerType<?>, TeleportRangeModifierPower> TELEPORT_RANGE_MODIFIER = reg("teleport_range_modifier", new TeleportRangeModifierPower());

    // --- Minion summoning ---
    public static final DeferredHolder<PowerType<?>, SummonMinionPower> SUMMON_MINION = reg("summon_minion", new SummonMinionPower());

    // --- Taming & pack control ---
    public static final DeferredHolder<PowerType<?>, TameMobPower>     TAME_MOB     = reg("tame_mob",      new TameMobPower());
    public static final DeferredHolder<PowerType<?>, CommandPackPower> COMMAND_PACK = reg("command_pack",  new CommandPackPower());
    public static final DeferredHolder<PowerType<?>, HordeRegenPower>  HORDE_REGEN  = reg("horde_regen",   new HordeRegenPower());

    // --- Active abilities ---
    public static final DeferredHolder<PowerType<?>, ActiveTeleportPower>      ACTIVE_TELEPORT      = reg("active_teleport",      new ActiveTeleportPower());
    public static final DeferredHolder<PowerType<?>, ActiveDashPower>          ACTIVE_DASH          = reg("active_dash",          new ActiveDashPower());
    public static final DeferredHolder<PowerType<?>, ActiveLaunchPower>        ACTIVE_LAUNCH        = reg("active_launch",        new ActiveLaunchPower());
    public static final DeferredHolder<PowerType<?>, ActiveRecallPower>        ACTIVE_RECALL        = reg("active_recall",        new ActiveRecallPower());
    public static final DeferredHolder<PowerType<?>, ActiveSwapPower>          ACTIVE_SWAP          = reg("active_swap",          new ActiveSwapPower());
    public static final DeferredHolder<PowerType<?>, ActiveFireballPower>      ACTIVE_FIREBALL      = reg("active_fireball",      new ActiveFireballPower());
    public static final DeferredHolder<PowerType<?>, ActiveBoltPower>          ACTIVE_BOLT          = reg("active_bolt",          new ActiveBoltPower());
    public static final DeferredHolder<PowerType<?>, ActivePhasePower>         ACTIVE_PHASE         = reg("active_phase",         new ActivePhasePower());
    public static final DeferredHolder<PowerType<?>, ActiveAoEEffectPower>     ACTIVE_AOE_EFFECT    = reg("active_aoe_effect",    new ActiveAoEEffectPower());
    public static final DeferredHolder<PowerType<?>, ActivePlaceBlockPower>    ACTIVE_PLACE_BLOCK   = reg("active_place_block",   new ActivePlaceBlockPower());
    public static final DeferredHolder<PowerType<?>, ShadowOrbPower>           SHADOW_ORB           = reg("shadow_orb",           new ShadowOrbPower());

    // --- Elemental mage abilities ---
    public static final DeferredHolder<PowerType<?>, ActiveGroundSlamPower>   GROUND_SLAM   = reg("ground_slam",    new ActiveGroundSlamPower());
    public static final DeferredHolder<PowerType<?>, ActiveTidalWavePower>   TIDAL_WAVE    = reg("tidal_wave",     new ActiveTidalWavePower());
    public static final DeferredHolder<PowerType<?>, ActiveHealingMistPower> HEALING_MIST  = reg("healing_mist",   new ActiveHealingMistPower());
    public static final DeferredHolder<PowerType<?>, ActiveGravityWellPower> GRAVITY_WELL  = reg("gravity_well",   new ActiveGravityWellPower());
    public static final DeferredHolder<PowerType<?>, ActiveRepulsePower>     REPULSE       = reg("repulse",        new ActiveRepulsePower());

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(PowerTypes::onNewRegistry);
        POWER_TYPES.register(modEventBus);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        event.create(new RegistryBuilder<>(REGISTRY_KEY));
    }

    public static PowerType<?> get(ResourceLocation id) {
        for (var holder : POWER_TYPES.getEntries())
            if (holder.getId().equals(id)) return holder.get();
        return null;
    }
}
