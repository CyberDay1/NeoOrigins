package com.cyberday1.neoorigins.data;

import net.minecraft.resources.Identifier;
import java.util.Map;

/**
 * Canonicalizes built-in origin ids that were renamed to match their display
 * names, so pre-rename saved worlds, layers and datapacks keep resolving:
 * <ul>
 *   <li>{@code neoorigins:jianxian} → {@code neoorigins:sword_immortal} ("Sword Immortal")</li>
 *   <li>{@code neoorigins:golden_bell} → {@code neoorigins:golden_body} ("Golden Body")</li>
 * </ul>
 * Only the ORIGIN ids change here; the powers those origins grant keep their
 * original ids.
 */
public final class LegacyOriginIds {
    private LegacyOriginIds() {}
    private static final Map<Identifier, Identifier> RENAMES = Map.of(
        Identifier.fromNamespaceAndPath("neoorigins", "jianxian"),
        Identifier.fromNamespaceAndPath("neoorigins", "sword_immortal"),
        Identifier.fromNamespaceAndPath("neoorigins", "golden_bell"),
        Identifier.fromNamespaceAndPath("neoorigins", "golden_body")
    );
    public static Identifier remap(Identifier id) {
        // A null id legitimately means "no origin" (e.g. the revoke path in
        // applyOriginPowers passes a null new-origin). Map.of() is an immutable
        // map that rejects null keys with an NPE in getOrDefault, so guard here
        // rather than crash the caller (this is what broke the Orb of Class).
        if (id == null) return null;
        return RENAMES.getOrDefault(id, id);
    }
    public static String remap(String id) {
        if (id == null) return null;
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return id;
        Identifier mapped = RENAMES.get(parsed);
        return mapped != null ? mapped.toString() : id;
    }
}
