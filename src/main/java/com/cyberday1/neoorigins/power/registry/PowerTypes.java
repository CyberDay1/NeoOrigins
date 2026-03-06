package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.EffectImmunityPower;
import com.cyberday1.neoorigins.power.builtin.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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

    public static final DeferredHolder<PowerType<?>, AttributeModifierPower> ATTRIBUTE_MODIFIER =
        POWER_TYPES.register("attribute_modifier", AttributeModifierPower::new);

    public static final DeferredHolder<PowerType<?>, StatusEffectPower> STATUS_EFFECT =
        POWER_TYPES.register("status_effect", StatusEffectPower::new);

    public static final DeferredHolder<PowerType<?>, PreventActionPower> PREVENT_ACTION =
        POWER_TYPES.register("prevent_action", PreventActionPower::new);

    public static final DeferredHolder<PowerType<?>, ModifyDamagePower> MODIFY_DAMAGE =
        POWER_TYPES.register("modify_damage", ModifyDamagePower::new);

    public static final DeferredHolder<PowerType<?>, FlightPower> FLIGHT =
        POWER_TYPES.register("flight", FlightPower::new);

    public static final DeferredHolder<PowerType<?>, NightVisionPower> NIGHT_VISION =
        POWER_TYPES.register("night_vision", NightVisionPower::new);

    public static final DeferredHolder<PowerType<?>, WaterBreathingPower> WATER_BREATHING =
        POWER_TYPES.register("water_breathing", WaterBreathingPower::new);

    public static final DeferredHolder<PowerType<?>, NoSlowdownPower> NO_SLOWDOWN =
        POWER_TYPES.register("no_slowdown", NoSlowdownPower::new);

    public static final DeferredHolder<PowerType<?>, WallClimbingPower> WALL_CLIMBING =
        POWER_TYPES.register("wall_climbing", WallClimbingPower::new);

    public static final DeferredHolder<PowerType<?>, ElytraBoostPower> ELYTRA_BOOST =
        POWER_TYPES.register("elytra_boost", ElytraBoostPower::new);

    public static final DeferredHolder<PowerType<?>, ScareEntitiesPower> SCARE_ENTITIES =
        POWER_TYPES.register("scare_entities", ScareEntitiesPower::new);

    public static final DeferredHolder<PowerType<?>, TickActionPower> TICK_ACTION =
        POWER_TYPES.register("tick_action", TickActionPower::new);

    public static final DeferredHolder<PowerType<?>, ConditionalPower> CONDITIONAL =
        POWER_TYPES.register("conditional", ConditionalPower::new);

    public static final DeferredHolder<PowerType<?>, PhantomFormPower> PHANTOM_FORM =
        POWER_TYPES.register("phantom_form", PhantomFormPower::new);

    public static final DeferredHolder<PowerType<?>, EffectImmunityPower> EFFECT_IMMUNITY =
        POWER_TYPES.register("effect_immunity", EffectImmunityPower::new);

    // ---- New built-in power types ----

    public static final DeferredHolder<PowerType<?>, GlowPower> GLOW =
        POWER_TYPES.register("glow", GlowPower::new);

    public static final DeferredHolder<PowerType<?>, DamageInDaylightPower> DAMAGE_IN_DAYLIGHT =
        POWER_TYPES.register("damage_in_daylight", DamageInDaylightPower::new);

    public static final DeferredHolder<PowerType<?>, KnockbackModifierPower> KNOCKBACK_MODIFIER =
        POWER_TYPES.register("knockback_modifier", KnockbackModifierPower::new);

    public static final DeferredHolder<PowerType<?>, XpGainModifierPower> XP_GAIN_MODIFIER =
        POWER_TYPES.register("xp_gain_modifier", XpGainModifierPower::new);

    public static final DeferredHolder<PowerType<?>, UnderwaterMiningSpeedPower> UNDERWATER_MINING_SPEED =
        POWER_TYPES.register("underwater_mining_speed", UnderwaterMiningSpeedPower::new);

    public static final DeferredHolder<PowerType<?>, HungerDrainModifierPower> HUNGER_DRAIN_MODIFIER =
        POWER_TYPES.register("hunger_drain_modifier", HungerDrainModifierPower::new);

    public static final DeferredHolder<PowerType<?>, NaturalRegenModifierPower> NATURAL_REGEN_MODIFIER =
        POWER_TYPES.register("natural_regen_modifier", NaturalRegenModifierPower::new);

    public static final DeferredHolder<PowerType<?>, FoodRestrictionPower> FOOD_RESTRICTION =
        POWER_TYPES.register("food_restriction", FoodRestrictionPower::new);

    public static final DeferredHolder<PowerType<?>, ItemMagnetismPower> ITEM_MAGNETISM =
        POWER_TYPES.register("item_magnetism", ItemMagnetismPower::new);

    public static final DeferredHolder<PowerType<?>, BreakSpeedModifierPower> BREAK_SPEED_MODIFIER =
        POWER_TYPES.register("break_speed_modifier", BreakSpeedModifierPower::new);

    public static final DeferredHolder<PowerType<?>, ThornsAuraPower> THORNS_AURA =
        POWER_TYPES.register("thorns_aura", ThornsAuraPower::new);

    public static final DeferredHolder<PowerType<?>, ProjectileImmunityPower> PROJECTILE_IMMUNITY =
        POWER_TYPES.register("projectile_immunity", ProjectileImmunityPower::new);

    public static final DeferredHolder<PowerType<?>, ActionOnKillPower> ACTION_ON_KILL =
        POWER_TYPES.register("action_on_kill", ActionOnKillPower::new);

    public static final DeferredHolder<PowerType<?>, ActionOnHitTakenPower> ACTION_ON_HIT_TAKEN =
        POWER_TYPES.register("action_on_hit_taken", ActionOnHitTakenPower::new);

    public static final DeferredHolder<PowerType<?>, RegenInFluidPower> REGEN_IN_FLUID =
        POWER_TYPES.register("regen_in_fluid", RegenInFluidPower::new);

    public static final DeferredHolder<PowerType<?>, BreathInFluidPower> BREATH_IN_FLUID =
        POWER_TYPES.register("breath_in_fluid", BreathInFluidPower::new);

    public static final DeferredHolder<PowerType<?>, BiomeBuffPower> BIOME_BUFF =
        POWER_TYPES.register("biome_buff", BiomeBuffPower::new);

    public static final DeferredHolder<PowerType<?>, DamageInBiomePower> DAMAGE_IN_BIOME =
        POWER_TYPES.register("damage_in_biome", DamageInBiomePower::new);

    public static final DeferredHolder<PowerType<?>, BurnAtHealthThresholdPower> BURN_AT_HEALTH_THRESHOLD =
        POWER_TYPES.register("burn_at_health_threshold", BurnAtHealthThresholdPower::new);

    public static final DeferredHolder<PowerType<?>, MobsIgnorePlayerPower> MOBS_IGNORE_PLAYER =
        POWER_TYPES.register("mobs_ignore_player", MobsIgnorePlayerPower::new);

    public static final DeferredHolder<PowerType<?>, NoMobSpawnsNearbyPower> NO_MOB_SPAWNS_NEARBY =
        POWER_TYPES.register("no_mob_spawns_nearby", NoMobSpawnsNearbyPower::new);

    public static final DeferredHolder<PowerType<?>, EntityGroupPower> ENTITY_GROUP =
        POWER_TYPES.register("entity_group", EntityGroupPower::new);

    public static final DeferredHolder<PowerType<?>, ActiveTeleportPower> ACTIVE_TELEPORT =
        POWER_TYPES.register("active_teleport", ActiveTeleportPower::new);

    public static final DeferredHolder<PowerType<?>, ActiveDashPower> ACTIVE_DASH =
        POWER_TYPES.register("active_dash", ActiveDashPower::new);

    public static final DeferredHolder<PowerType<?>, ActiveLaunchPower> ACTIVE_LAUNCH =
        POWER_TYPES.register("active_launch", ActiveLaunchPower::new);

    public static final DeferredHolder<PowerType<?>, ActiveRecallPower> ACTIVE_RECALL =
        POWER_TYPES.register("active_recall", ActiveRecallPower::new);

    public static final DeferredHolder<PowerType<?>, ActiveSwapPower> ACTIVE_SWAP =
        POWER_TYPES.register("active_swap", ActiveSwapPower::new);

    public static final DeferredHolder<PowerType<?>, SizeScalingPower> SIZE_SCALING =
        POWER_TYPES.register("size_scaling", SizeScalingPower::new);

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(PowerTypes::onNewRegistry);
        POWER_TYPES.register(modEventBus);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        event.create(new RegistryBuilder<>(REGISTRY_KEY));
    }

    public static PowerType<?> get(Identifier id) {
        for (var holder : POWER_TYPES.getEntries()) {
            // DeferredHolder.getId() returns Identifier in NeoForge 21.1
            if (holder.getId().equals(id)) {
                return holder.get();
            }
        }
        return null;
    }
}
