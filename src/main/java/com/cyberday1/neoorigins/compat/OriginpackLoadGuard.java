package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.resources.ResourceKey;

/**
 * 26.3 loads recipes, advancements and loot data as strict registries, so one
 * unreadable file stops the server. An originpack file the legacy rewriter could
 * not repair is logged and skipped instead; every other pack keeps vanilla's behaviour.
 */
public final class OriginpackLoadGuard {

    private OriginpackLoadGuard() {}

    /** True when the failed element came from originpacks/ and should be skipped; logs why. */
    public static boolean skip(String sourcePackId, ResourceKey<?> key, Exception error) {
        if (sourcePackId == null || !sourcePackId.startsWith("originpacks/")) return false;
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        NeoOrigins.LOGGER.warn("OriginsCompat: pack '{}': skipping {} '{}', which this version cannot read ({})",
            sourcePackId, key.registry().getPath(), key.identifier(), cause.getMessage());
        return true;
    }
}
