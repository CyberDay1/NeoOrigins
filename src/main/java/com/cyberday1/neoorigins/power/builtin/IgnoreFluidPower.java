package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.capability.IgnoreFluidCapabilities;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Makes the player <em>totally</em> ignore one or more fluids — the generalised,
 * modded-fluid-aware successor to {@link IgnoreWaterPower} (which stays for the
 * datapacks that already use it).
 *
 * <p>Rather than cancelling each individual fluid effect, this power suppresses
 * fluid <b>detection</b>: the mixins hand back an empty {@code FluidState} for
 * ignored fluids inside {@code Entity.updateFluidHeightAndDoFluidPushing},
 * {@code Entity.updateFluidOnEyes} and {@code Camera.getFluidInCamera}, and
 * cancel {@code BlockStateBase.entityInside} for the fluid's own block. Because
 * every downstream behaviour (buoyancy, drag, current push, drowning,
 * {@code isInWater}/{@code isInLava}, lava burn, swim pose, fog and the
 * underwater overlay) reads those same values, they all fall away together.
 *
 * <p><b>Sync.</b> Behaviour reaches the mixins purely through capability tags,
 * which are already synced to the client as plain strings: a marker tag
 * {@code ignore_fluid} plus one {@code ignore_fluid:<id>} per configured entry.
 * The tags are built by pure string manipulation — no registry lookups here — so
 * an unknown or modded id can never throw during datapack load or login sync; it
 * simply never matches a real fluid at runtime. Resolution of a live
 * {@code FluidState} to its candidate tags happens game-side in
 * {@link IgnoreFluidCapabilities}.
 *
 * <pre>{@code
 * { "type": "neoorigins:ignore_fluid", "fluid": "minecraft:lava" }
 * { "type": "neoorigins:ignore_fluid", "fluids": ["minecraft:water", "minecraft:lava"] }
 * { "type": "neoorigins:ignore_fluid", "fluid": "#c:milk" }
 * }</pre>
 */
public class IgnoreFluidPower extends PowerType<IgnoreFluidPower.Config> {

    /**
     * Default when the author names no fluid at all. A marker-only
     * {@code {"type":"neoorigins:ignore_fluid"}} that silently did nothing would
     * be a footgun, so it means "the two vanilla fluids" — mirroring
     * {@code walk_on_fluid}'s {@code "both"} default.
     */
    public static final List<String> DEFAULT_FLUIDS = List.of("minecraft:water", "minecraft:lava");

    /** Accepts either {@code "minecraft:lava"} or {@code ["a", "b"]} under one key. */
    private static final Codec<List<String>> STRING_OR_LIST =
        Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(
            either -> either.map(List::of, List::copyOf),
            list -> list.size() == 1 ? Either.left(list.get(0)) : Either.right(list));

    public record Config(List<String> fluid, List<String> fluids, String type)
            implements PowerConfiguration {

        public Config {
            fluid = fluid == null ? List.of() : List.copyOf(fluid);
            fluids = fluids == null ? List.of() : List.copyOf(fluids);
        }

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            STRING_OR_LIST.optionalFieldOf("fluid", List.of()).forGetter(Config::fluid),
            STRING_OR_LIST.optionalFieldOf("fluids", List.of()).forGetter(Config::fluids),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));

        /**
         * The author's entries, normalised to canonical {@code namespace:path} (or
         * {@code #namespace:path} for a tag) form, de-duplicated and order-preserving.
         * Entries that are not shaped like a resource location are dropped rather
         * than raising — an unknown id must degrade to a no-op, never break a load.
         */
        public List<String> resolvedEntries() {
            List<String> raw = new ArrayList<>(fluid);
            raw.addAll(fluids);
            if (raw.isEmpty()) raw = new ArrayList<>(DEFAULT_FLUIDS);

            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (String entry : raw) {
                String normalised = normalise(entry);
                if (normalised != null) out.add(normalised);
            }
            return List.copyOf(out);
        }
    }

    /**
     * {@code "  Lava "} → {@code "minecraft:lava"}, {@code "#c:milk"} → {@code "#c:milk"}.
     * Returns {@code null} for anything that cannot be a resource location, so the
     * caller drops it silently.
     */
    private static String normalise(String entry) {
        if (entry == null) return null;
        String s = entry.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        boolean tag = s.startsWith("#");
        String body = tag ? s.substring(1) : s;
        if (body.isEmpty()) return null;

        int colon = body.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : body.substring(0, colon);
        String path = colon < 0 ? body : body.substring(colon + 1);
        if (namespace.isEmpty() || path.isEmpty()) return null;
        if (!isValidNamespace(namespace) || !isValidPath(path)) return null;

        return (tag ? "#" : "") + namespace + ":" + path;
    }

    private static boolean isValidNamespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = c == '_' || c == '-' || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '.';
            if (!ok) return false;
        }
        return true;
    }

    private static boolean isValidPath(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = c == '_' || c == '-' || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '.' || c == '/';
            if (!ok) return false;
        }
        return true;
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        // Cheap gate the hot mixins test first, so a player without this power
        // never pays for the per-fluid resolution below.
        caps.add(IgnoreFluidCapabilities.MARKER);
        for (String entry : config.resolvedEntries()) {
            caps.add(IgnoreFluidCapabilities.PREFIX + entry);
        }
        return Set.copyOf(caps);
    }
}
