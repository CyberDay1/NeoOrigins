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
     *  Authored as plain ids; serialized into {@code EntityTargetSpec}.
     *  Both single-value fields start empty so the creator forces the author
     *  to make an explicit choice between an exact type and a tag. */
    public String targetEntityType = "";
    public String targetEntityTag = "";
    public final List<String> targetEntityTypes = new ArrayList<>();

    public final List<OriginDraft.PowerDraft> powers = new ArrayList<>();

    // ── Spawn rules (Phase 4) ──────────────────────────────────────────────
    // Whole block is opt-in: when {@code spawnRulesEnabled} is false the
    // serializer omits {@code spawn_rules} so the codec falls back to
    // {@link com.cyberday1.neoorigins.api.mob_origin.SpawnRules#NEVER}.
    public boolean spawnRulesEnabled = false;
    public double weight = 0.25;
    public String timeOfDay = "any"; // any | day | night
    public final java.util.Set<String> spawnReasons = new java.util.LinkedHashSet<>(); // lowercase MobSpawnType names
    public String mutexGroup = "";
    public boolean replace = false;

    public boolean yRangeEnabled = false;
    public int yRangeMin = -64;
    public int yRangeMax = 320;
    public boolean lightRangeEnabled = false;
    public int lightRangeMin = 0;
    public int lightRangeMax = 15;

    // Location sub-condition (Phase 4c wires the UI; 4a only round-trips).
    // Empty strings → absent in JSON. tri-state can_see_sky encoded as any/true/false.
    public String locationDimension = "";
    public String locationBiome = "";
    public String locationBiomeTag = "";
    public final java.util.List<String> locationBiomes = new java.util.ArrayList<>();
    public String locationStructure = "";
    public String locationStructureTag = "";
    public boolean locationAllowWaterSurface = false;
    public boolean locationAllowOceanFloor = false;
    public boolean locationMinYEnabled = false;
    public int locationMinY = -64;
    public boolean locationMaxYEnabled = false;
    public int locationMaxY = 320;
    public String locationCanSeeSky = "any"; // any | true | false

    // ── Drops (Phase 5) ────────────────────────────────────────────────────
    // Block is opt-in: when {@code dropsEnabled} is false the serializer omits
    // {@code drops} so the codec falls back to {@link DropRules#NONE}.
    public boolean dropsEnabled = false;
    public String dropMode = "additive";              // additive | replace
    public String dropStrategy = "independent_chance"; // independent_chance | weighted_pool
    public int dropPoolRolls = 1;
    public final java.util.List<DropRow> dropEntries = new java.util.ArrayList<>();

    /** One drop row on the wire / in-creator. Same shape regardless of strategy;
     *  irrelevant fields are simply ignored by whichever strategy is active. */
    public static final class DropRow {
        public String item = "minecraft:rotten_flesh";
        public int countMin = 1;
        public int countMax = 1;
        public double chance = 1.0;
        public int rolls = 1;
        public int weight = 1;

        public DropRow() {}
        public DropRow(String item) { this.item = item; }
    }

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
