package com.cyberday1.neoorigins.power.capability;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Runtime side of {@code neoorigins:ignore_fluid}: maps a live {@link FluidState}
 * to the capability tags that would suppress it, and answers "should this entity
 * pretend this fluid is not there?".
 *
 * <p><b>Why the split.</b> {@code IgnoreFluidPower.capabilities} must stay
 * registry-free: it runs during datapack load and login sync, and an unknown or
 * modded fluid id has to degrade to a no-op instead of throwing. So the power
 * emits the author's ids verbatim as {@code ignore_fluid:<id>} strings and the
 * reverse resolution (fluid instance → candidate tag strings) happens here,
 * game-side, where the registries are populated and frozen.
 *
 * <p><b>Fluid types, not fluids.</b> Vanilla water is two fluids —
 * {@code minecraft:water} and {@code minecraft:flowing_water} — sharing one
 * {@link net.neoforged.neoforge.fluids.FluidType}. Ignoring only the still one
 * would leave every poured bucket working, so the cache groups by fluid type:
 * naming any fluid of a type ignores the whole type. This is also what makes
 * modded fluids work, since NeoForge routes all physics through the type.
 *
 * <p><b>Tags are not cached.</b> Fluid tags rebind on datapack reload, so the
 * tag arm reads {@code Holder#tags()} live. It is only reached for a player who
 * already answered yes to the {@link #MARKER} gate.
 */
public final class IgnoreFluidCapabilities {

    private IgnoreFluidCapabilities() {}

    /**
     * Cheap "does this player ignore <em>any</em> fluid at all" gate. Emitted
     * alongside the per-fluid tags so the hot mixins can bail with a single
     * capability lookup instead of resolving the fluid first.
     */
    public static final String MARKER = "ignore_fluid";

    /** Prefix of the per-fluid capability tags, e.g. {@code ignore_fluid:minecraft:lava}. */
    public static final String PREFIX = "ignore_fluid:";

    private static final String[] NO_TAGS = new String[0];

    /**
     * Fluid → every {@code ignore_fluid:<id>} string that should match it (one per
     * fluid sharing its {@link net.neoforged.neoforge.fluids.FluidType}). Built
     * once, lazily, off the frozen fluid registry. Volatile + benign-race: two
     * threads may both build it, but the result is identical and immutable.
     */
    private static volatile Map<Fluid, String[]> byFluid;

    /**
     * True if {@code entity} is a player who currently ignores {@code state}'s fluid.
     *
     * <p>Side-agnostic: {@link PowerCapabilities} resolves against the server-side
     * power service or the synced client capability set as appropriate, so
     * client-predicted physics agrees with the server and does not rubber-band.
     */
    public static boolean ignores(Entity entity, FluidState state) {
        if (state == null || state.isEmpty()) return false;
        if (!(entity instanceof Player player)) return false;
        // Single cheap lookup rejects every player without the power.
        if (!PowerCapabilities.hasActive(player, MARKER)) return false;

        Fluid fluid = state.getType();
        for (String tag : capabilityTagsFor(fluid)) {
            if (PowerCapabilities.hasActive(player, tag)) return true;
        }
        return matchesFluidTag(player, fluid);
    }

    /** {@code "#c:milk"}-style entries, resolved live because tags rebind on reload. */
    private static boolean matchesFluidTag(Player player, Fluid fluid) {
        Iterator<TagKey<Fluid>> tags;
        try {
            tags = fluid.builtInRegistryHolder().tags().iterator();
        } catch (RuntimeException e) {
            // Tags not bound yet (early world load) — nothing to match, not a crash.
            return false;
        }
        while (tags.hasNext()) {
            if (PowerCapabilities.hasActive(player, PREFIX + "#" + tags.next().location())) {
                return true;
            }
        }
        return false;
    }

    private static String[] capabilityTagsFor(Fluid fluid) {
        Map<Fluid, String[]> map = byFluid;
        if (map == null) {
            map = build();
            // A failed build is NOT cached — retry next call rather than pinning
            // an empty map for the rest of the session.
            if (map == null) return NO_TAGS;
            byFluid = map;
        }
        String[] tags = map.get(fluid);
        return tags == null ? NO_TAGS : tags;
    }

    private static Map<Fluid, String[]> build() {
        Map<Object, List<String>> perType = new IdentityHashMap<>();
        Map<Fluid, String[]> out = new IdentityHashMap<>();
        try {
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
                if (key == null) continue;
                perType.computeIfAbsent(fluid.getFluidType(), t -> new ArrayList<>())
                    .add(PREFIX + key);
            }
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                List<String> tags = perType.get(fluid.getFluidType());
                if (tags != null && !tags.isEmpty()) {
                    out.put(fluid, tags.toArray(new String[0]));
                }
            }
        } catch (RuntimeException e) {
            // Registry unavailable (harness / very early boot): behave as "ignores
            // nothing" rather than taking the game down, and leave the cache unset
            // so a later call can still build it.
            return null;
        }
        return out;
    }
}
