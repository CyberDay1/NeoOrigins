package com.cyberday1.neoorigins.client.theme;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared themed-font wrappers for the NeoOrigins screens.
 *
 * <p>Canonical home for the {@code themed} / {@code themedBold} helpers that
 * several screens previously duplicated verbatim. Wrapping a {@link Component}
 * with the active theme's font {@link net.minecraft.network.chat.Style#withFont}
 * lets a custom font provider (e.g. a user-supplied TTF) take effect while the
 * renderer stays the vanilla {@link net.minecraft.client.gui.Font}.
 */
public final class UIThemeUtils {

    private UIThemeUtils() {}

    /**
     * Wraps a Component with the active theme's font Style so a custom font
     * provider can take effect. Returns the component unchanged when the theme
     * declares no font.
     */
    public static Component themed(Component c) {
        ResourceLocation fid = UITheme.current().font();
        return fid != null ? c.copy().withStyle(s -> s.withFont(fid)) : c;
    }

    /**
     * Like {@link #themed} but also marks the Style bold — used for section
     * headers and per-power name lines so the TTF renderer picks up its
     * synthesized bold weight.
     */
    public static Component themedBold(Component c) {
        ResourceLocation fid = UITheme.current().font();
        return c.copy().withStyle(s -> {
            var styled = s.withBold(true);
            return fid != null ? styled.withFont(fid) : styled;
        });
    }
}
