package com.cyberday1.neoorigins.screen.creator.model;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The single mutable source of truth the in-game origin/class creator edits.
 *
 * <p>Deliberately Minecraft-light (ids + strings + a power list) so it can be
 * serialized to the on-disk datapack (Phase 2) and validated (Phase 4) without
 * dragging in {@code Screen} state. Every creator tab reads from and writes
 * back to one of these instances; the JSON-preview tab and the datapack writer
 * both consume it.
 *
 * <p>Phase 1 scope: the model exists and holds identity/layer/power fields;
 * the tabs that populate it and the serializer that drains it land in later
 * phases.
 */
public final class OriginDraft {

    /** A single power entry; {@code rawJson} is the escape-hatch representation
     *  the JSON tab edits and the Phase 2 writer emits verbatim. */
    public static final class PowerDraft {
        public ResourceLocation powerId;
        public String typeId = "";
        /** Raw power JSON body (without the synthetic {@code id}); "" until edited. */
        public String rawJson = "{}";

        public PowerDraft(ResourceLocation powerId, String typeId) {
            this.powerId = powerId;
            this.typeId = typeId;
        }
    }

    // ── Identity ────────────────────────────────────────────────────────────
    /** Datapack path segment, e.g. {@code "my_origin"} → origins/origins/my_origin.json. */
    public String idPath = "my_origin";
    public String name = "";
    public String description = "";
    /** Icon item id, e.g. {@code minecraft:feather}. */
    public ResourceLocation icon = ResourceLocation.withDefaultNamespace("player_head");
    /** Origins impact dots (0–3). */
    public int impact = 0;
    public int order = 0;

    // ── Layer ───────────────────────────────────────────────────────────────
    /** Target layer; a class = an origin in the {@code neoorigins:class} layer. */
    public ResourceLocation layerId = ResourceLocation.fromNamespaceAndPath("neoorigins", "origin");

    // ── Powers ──────────────────────────────────────────────────────────────
    public final List<PowerDraft> powers = new ArrayList<>();

    public OriginDraft() {}

    /** Namespace custom content lands in, kept distinct so it never shadows
     *  shipped origins (decision deferred to its phase; default stated here). */
    public static final String CUSTOM_NAMESPACE = "neoorigins_custom";

    public ResourceLocation originId() {
        return ResourceLocation.fromNamespaceAndPath(CUSTOM_NAMESPACE, idPath);
    }

    /**
     * Mint a unique power id under {@link #CUSTOM_NAMESPACE} as
     * {@code <idPath>_<typeShortName>}, suffixing {@code _2}, {@code _3}, … when
     * the same type repeats. Shared by the Powers and Appearance tabs so every
     * power in the draft keeps a stable, collision-free id.
     */
    public ResourceLocation mintPowerId(PowerDraft self, String typeId) {
        String typeShort;
        try { typeShort = ResourceLocation.parse(typeId).getPath(); }
        catch (RuntimeException e) { typeShort = "power"; }
        String base = sanitize(idPath) + "_" + typeShort;
        String candidate = base;
        int n = 1;
        boolean clash;
        do {
            clash = false;
            for (PowerDraft o : powers) {
                if (o == self) continue;
                if (o.powerId != null && o.powerId.getPath().equals(candidate)) {
                    clash = true;
                    break;
                }
            }
            if (clash) candidate = base + "_" + (++n);
        } while (clash);
        return ResourceLocation.fromNamespaceAndPath(CUSTOM_NAMESPACE, candidate);
    }

    private static String sanitize(String s) {
        String v = s == null ? "" : s.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        return v.isEmpty() ? "origin" : v;
    }
}
