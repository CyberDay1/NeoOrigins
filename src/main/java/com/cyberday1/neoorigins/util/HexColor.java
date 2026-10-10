package com.cyberday1.neoorigins.util;

/**
 * Hex colour strings from pack JSON: {@code RRGGBB} or {@code AARRGGBB}, with an
 * optional leading {@code #}. Any other length is rejected rather than read as a
 * number, so {@code #FFF} cannot turn into a near-transparent {@code 0x00000FFF}.
 */
public final class HexColor {

    private HexColor() {}

    /** @return the ARGB colour (opaque when no alpha is given), or {@code null} if {@code s} is not 6 or 8 hex digits. */
    public static Integer parse(String s) {
        if (s == null) return null;
        String hex = s.startsWith("#") ? s.substring(1) : s;
        if (hex.length() != 6 && hex.length() != 8) return null;
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return null;
        }
        long v = Long.parseLong(hex, 16);
        return (int) (hex.length() == 6 ? 0xFF000000L | v : v);
    }
}
