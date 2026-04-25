package com.cyberday1.neoorigins.api.content.vfx;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public registry of {@code effect_type} → RGB color used by procedural VFX
 * renderers (the magic-orb projectile, lingering-area glow, etc.).
 *
 * <p>Pack authors pick a key in power JSON ({@code "effect_type": "freeze"});
 * the renderer looks it up here. Unregistered keys fall back to {@link #DEFAULT}.
 *
 * <p>Other mods can register custom keys via {@link #register(String, int, int, int)}
 * during mod construction. The registry is case-insensitive (keys are folded
 * to {@link Locale#ROOT} lower-case on insert + read).
 *
 * <p>API status: stable. Added in 2.0.
 */
public final class VfxEffectTypes {

    private VfxEffectTypes() {}

    /** Fallback RGB used when an effect_type key isn't registered. Soft blue. */
    public static final int[] DEFAULT = {150, 200, 255};

    private static final Map<String, int[]> COLORS = new ConcurrentHashMap<>();

    static {
        // Damage / fire
        register("damage",      255,  80,  80);  // red
        register("fire",        255, 120,  50);  // orange-red
        register("ignite",      255, 160,  50);  // amber
        register("lava",        255,  80,  30);  // dark orange
        register("explosion",   255, 120,  50);  // orange

        // Ice / cold
        register("freeze",      120, 200, 255);  // ice blue
        register("frost",       200, 230, 255);  // frost white

        // Nature
        register("poison",      150, 255,  50);  // toxic green
        register("spore",       180, 220,  80);  // mossy green
        register("nature",      100, 220,  80);  // leaf green
        register("heal",        100, 255, 120);  // lime
        register("saturation",  100, 255, 120);  // lime

        // Magic / arcane
        register("magic",       180, 120, 220);  // violet
        register("arcane",      200, 150, 255);  // lavender
        register("corruption",  140,  60, 180);  // dark purple
        register("void",         40,  40,  80);  // near-black blue
        register("shadow",       30,  30,  50);  // dark
        register("wither",      180,  50, 220);  // dark purple

        // Wind / sonic / air
        register("wind",        200, 220, 255);  // pale cyan
        register("sonic",        80, 200, 220);  // cyan
        register("air",         180, 230, 255);  // light blue

        // Water / aquatic
        register("water",        50, 120, 255);  // ocean blue
        register("drown",        30,  80, 200);  // deep blue

        // Light / holy
        register("light",       255, 240, 180);  // pale gold
        register("holy",        255, 220, 120);  // gold
        register("sun",         255, 180,  80);  // sunlight
        register("lightning",   255, 255, 100);  // yellow

        // Earth / stone
        register("earth",       140, 100,  60);  // brown
        register("stone",       160, 160, 140);  // stone gray
        register("petrify",     130, 110, 160);  // gray-violet

        // Movement
        register("teleport",     50, 255, 255);  // cyan
        register("push",        100, 150, 255);  // azure
        register("pull",        100, 150, 255);  // azure
    }

    /**
     * Register (or overwrite) a color for an {@code effect_type} key.
     * Keys are case-insensitive; whitespace is not trimmed.
     *
     * @param key    effect type identifier (pack authors reference this string)
     * @param r  red   0-255
     * @param g  green 0-255
     * @param b  blue  0-255
     */
    public static void register(String key, int r, int g, int b) {
        if (key == null) return;
        COLORS.put(key.toLowerCase(Locale.ROOT), new int[]{clamp(r), clamp(g), clamp(b)});
    }

    /**
     * Look up the RGB color for {@code key}. Returns {@link #DEFAULT} if unknown.
     * Never returns {@code null}. The returned array is a shared reference —
     * don't mutate it.
     */
    public static int[] get(String key) {
        if (key == null || key.isEmpty()) return DEFAULT;
        int[] c = COLORS.get(key.toLowerCase(Locale.ROOT));
        return c != null ? c : DEFAULT;
    }

    /** True if a key is registered. Mostly for diagnostics. */
    public static boolean isRegistered(String key) {
        return key != null && COLORS.containsKey(key.toLowerCase(Locale.ROOT));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
