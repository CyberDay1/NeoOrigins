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
