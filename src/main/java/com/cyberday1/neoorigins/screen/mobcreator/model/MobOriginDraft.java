package com.cyberday1.neoorigins.screen.mobcreator.model;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mutable model the Mob Origin Creator edits — the mob-side analogue of
 * {@link OriginDraft}. Deliberately reuses {@link OriginDraft.PowerDraft} for
 * its power entries so the existing Powers tab / PowerFormPanel work verbatim.
 *
 * <p>Phase 3 covers identity + target + powers; spawn-rules / drops are added
 * to this model in Phases 4–5 (the on-disk codec already carries them).
 */
public final class MobOriginDraft {

    public String idPath = "my_mob_origin";
    public String name = "";
    public String description = "";
    public Identifier icon = Identifier.withDefaultNamespace("zombie_spawn_egg");

    /** Exactly one of these three forms should be set (validator enforces).
     *  Authored as plain ids; serialized into {@code EntityTargetSpec}. */
    public String targetEntityType = "minecraft:zombie";
    public String targetEntityTag = "";
    public final List<String> targetEntityTypes = new ArrayList<>();

    public final List<OriginDraft.PowerDraft> powers = new ArrayList<>();

    public MobOriginDraft() {}

    public Identifier originId() {
        return Identifier.fromNamespaceAndPath(OriginDraft.CUSTOM_NAMESPACE, idPath);
    }

    /** Mint a stable, collision-free power id — same scheme as OriginDraft. */
    public Identifier mintPowerId(OriginDraft.PowerDraft self, String typeId) {
        String typeShort;
        try { typeShort = Identifier.parse(typeId).getPath(); }
        catch (RuntimeException e) { typeShort = "power"; }
        String base = sanitize(idPath) + "_" + typeShort;
        String candidate = base;
        int n = 1;
        boolean clash;
        do {
            clash = false;
            for (OriginDraft.PowerDraft o : powers) {
                if (o == self) continue;
                if (o.powerId != null && o.powerId.getPath().equals(candidate)) { clash = true; break; }
            }
            if (clash) candidate = base + "_" + (++n);
        } while (clash);
        return Identifier.fromNamespaceAndPath(OriginDraft.CUSTOM_NAMESPACE, candidate);
    }

    private static String sanitize(String s) {
        String v = s == null ? "" : s.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        return v.isEmpty() ? "mob_origin" : v;
    }
}
