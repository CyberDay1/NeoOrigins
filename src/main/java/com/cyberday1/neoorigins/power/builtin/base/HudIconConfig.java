package com.cyberday1.neoorigins.power.builtin.base;

/**
 * Mix-in interface for power configurations that can occupy an icon slot in
 * the cooldown/ability HUD cluster ({@code CooldownHudOverlay}).
 *
 * <p>Implemented by {@link AbstractActivePower.Config} (cooldown-gated keybind
 * actives) and the toggleable keybind powers' Config records (flight, stealth,
 * phantom_form, wraith_phase, item_magnetism, no_mob_spawns_nearby). Both
 * fields are optional in JSON; the defaults keep legacy behavior (plain bar /
 * hidden while idle).
 */
public interface HudIconConfig {

    /**
     * Optional HUD icon: an item id (rendered as the item) or a datapack
     * texture path ending in {@code .png} (resolved under
     * {@code assets/<ns>/textures/}). Empty string = no icon; cooldown powers
     * keep the plain cooldown bar and toggle powers stay off the cluster.
     */
    default String cooldownIcon() { return ""; }

    /**
     * If true, this power's icon renders on the HUD cluster even while idle
     * (off cooldown — full-bright, no sweep, no countdown). Default false:
     * cooldown icons appear only while recharging.
     */
    default boolean alwaysShowIcon() { return false; }
}
