package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the ownership test behind the "Toggle Night Vision" keybind.
 *
 * <p>The night-vision flag is consumed in exactly one place — {@code
 * PersistentEffectPower.isNightVisionSuppressed} — and only for a
 * {@code minecraft:night_vision} effect spec. Without this test the keybind
 * flipped a value nothing would ever read and told the player, in green, that it
 * had worked. 49 of the 78 built-in origins own no night-vision spec at any tier.
 */
class NightVisionOwnershipTest {

    /** A keyed holder with no registry behind it — {@code unwrapKey} is all the gate reads. */
    private static Holder<MobEffect> effect(String path) {
        HolderOwner<MobEffect> owner = new HolderOwner<>() {};
        return Holder.Reference.createStandAlone(owner,
            ResourceKey.create(Registries.MOB_EFFECT, Identifier.withDefaultNamespace(path)));
    }

    private static PowerHolder<?> persistentEffect(String id, String... effectPaths) {
        List<PersistentEffectPower.EffectSpec> specs = new java.util.ArrayList<>();
        for (String path : effectPaths) {
            specs.add(new PersistentEffectPower.EffectSpec(effect(path), 0, true, false, true));
        }
        PersistentEffectPower.Config config = new PersistentEffectPower.Config(
            specs, EntityCondition.alwaysTrue(), false, false,
            "neoorigins:persistent_effect", "", false);
        return new PowerHolder<>(Identifier.fromNamespaceAndPath("neoorigins", id),
            new PersistentEffectPower(), config, Component.empty(), Component.empty());
    }

    /** Stands in for any non-persistent_effect power the origin also carries. */
    private record Cfg() implements PowerConfiguration {}

    private static PowerHolder<?> otherType(String id) {
        PowerType<Cfg> type = new PowerType<>() {
            @Override public Codec<Cfg> codec() { return MapCodec.unit(Cfg::new).codec(); }
        };
        return new PowerHolder<>(Identifier.fromNamespaceAndPath("neoorigins", id),
            type, new Cfg(), Component.empty(), Component.empty());
    }

    @Test
    void anOriginWithANightVisionPowerOwnsTheToggle() {
        assertTrue(PersistentEffectPower.grantsNightVision(List.of(
            otherType("water_breathing_marker"),
            persistentEffect("siren_night_vision", "night_vision"))));
    }

    /**
     * The reported case: most built-in origins land here, and used to be told the
     * toggle had worked.
     */
    @Test
    void anOriginWithNoNightVisionAnywhereDoesNot() {
        assertFalse(PersistentEffectPower.grantsNightVision(List.of(
            otherType("aquatic_depth_strider"),
            persistentEffect("merling_water_breathing", "water_breathing"))));
    }

    /**
     * Night vision is usually bundled with other effects rather than granted
     * alone — Merling's tier-2 conduit is water_breathing + night_vision + haste.
     * The scan has to look at every spec on the power, not just the first.
     */
    @Test
    void nightVisionIsFoundBesideOtherEffectsOnTheSamePower() {
        assertTrue(PersistentEffectPower.grantsNightVision(List.of(
            persistentEffect("merling_ascended_conduit",
                "water_breathing", "night_vision", "haste"))));
    }

    @Test
    void anEmptyPowerSetOwnsNothing() {
        assertFalse(PersistentEffectPower.grantsNightVision(List.of()));
    }

    /** A persistent_effect that the config kill-switch collapsed to no effects. */
    @Test
    void aDisabledNightVisionPowerNoLongerCounts() {
        assertFalse(PersistentEffectPower.grantsNightVision(List.of(
            persistentEffect("caveborn_night_vision"))));
    }
}
