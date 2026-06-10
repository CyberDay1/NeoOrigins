package com.cyberday1.neoorigins.data;

import com.google.gson.JsonElement;
import net.neoforged.fml.ModList;

/**
 * Shared helper for the top-level {@code required_mods} datapack gate.
 *
 * <p>An origin or power JSON may carry an optional {@code "required_mods": [...]}
 * array of mod ids. The entry only loads when every listed mod is present on the
 * mod list. This lets a pack ship content that targets an optional mod (e.g. the
 * built-in Dragon Survival origins) without that content loading — or appearing
 * in the picker — when the target mod is absent.
 *
 * <p>The field name mirrors what the data managers read with
 * {@code json.get("required_mods")}, per the project's schema-mirrors-parser rule.
 */
public final class ModGate {

    private ModGate() {}

    /**
     * Returns true if the entry carrying this {@code required_mods} element should
     * load. A null/non-array element (i.e. no gate) is always satisfied. Otherwise
     * every listed mod id must be loaded.
     *
     * <p>In headless harnesses {@link ModList#get()} is null (no NeoForge runtime);
     * we treat a present-but-unverifiable gate as <b>not</b> satisfied so gated
     * content stays out of golden-master / schema snapshots rather than loading
     * with its target mod absent.
     */
    public static boolean satisfied(JsonElement requiredMods) {
        if (requiredMods == null || !requiredMods.isJsonArray()) return true;
        ModList modList = ModList.get();
        for (JsonElement el : requiredMods.getAsJsonArray()) {
            if (!el.isJsonPrimitive()) continue;
            String modId = el.getAsString();
            if (modId.isEmpty()) continue;
            if (modList == null || !modList.isLoaded(modId)) return false;
        }
        return true;
    }
}
