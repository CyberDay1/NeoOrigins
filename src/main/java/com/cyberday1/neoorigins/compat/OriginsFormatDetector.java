package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonObject;

/** Detects whether a JSON object is in Origins/Apace mod format. */
public final class OriginsFormatDetector {

    private OriginsFormatDetector() {}

    /**
     * Returns true if the JSON's "type" field is in the origins: or apace: namespace.
     * Used for power JSONs.
     */
    public static boolean isOriginsFormat(JsonObject json) {
        if (!json.has("type")) return false;
        String type = json.get("type").getAsString();
        return type.startsWith("origins:") || type.startsWith("apace:");
    }

    /**
     * Returns true if the JSON looks like an Origins-format origin (not a power).
     * Detects by structural markers: integer impact, object icon, or object name/description.
     */
    public static boolean isOriginsOriginFormat(JsonObject json) {
        if (json.has("impact") && json.get("impact").isJsonPrimitive()
                && isNumericImpact(json.get("impact").getAsJsonPrimitive())) {
            return true;
        }
        if (json.has("icon") && json.get("icon").isJsonObject()) {
            return true;
        }
        if (json.has("name") && json.get("name").isJsonObject()) {
            return true;
        }
        if (json.has("description") && json.get("description").isJsonObject()) {
            return true;
        }
        if (json.has("hidden")) {
            return true;
        }
        return false;
    }

    /**
     * True if an impact primitive is the Origins numeric form — either a real
     * JSON number ({@code "impact": 2}) or a quoted number ({@code "impact": "2"},
     * common in real Apoli packs). The quoted-numeric case is the integer form
     * with quotes around it, so it should be treated as Origins format, not as a
     * native string impact ("low"/"medium"/...).
     */
    private static boolean isNumericImpact(com.google.gson.JsonPrimitive prim) {
        if (prim.isNumber()) return true;
        if (prim.isString()) {
            try {
                Integer.parseInt(prim.getAsString().trim());
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    /** Returns the type string, or empty string if not present. */
    public static String getType(JsonObject json) {
        return json.has("type") ? json.get("type").getAsString() : "";
    }

    /**
     * Apoli-family namespaces that are dispatch-aliases of {@code origins:}.
     * Apoli is the power library Origins re-exports, so a power declared as
     * {@code apoli:resource} is the same type as {@code origins:resource};
     * Apugli is the common addon that follows the same vocabulary.
     */
    private static final java.util.Set<String> APOLI_FAMILY_NS = java.util.Set.of("apoli", "apugli");

    /** True for a namespace {@link #canonicalizePowerType} rewrites to {@code origins:}. */
    public static boolean isApoliFamily(String namespace) {
        return APOLI_FAMILY_NS.contains(namespace);
    }

    /**
     * Rewrites an Apoli-family power {@code type} to the canonical {@code origins:}
     * namespace, in place, and returns the resulting type string. This lets the
     * compat dispatch — which keys on {@code origins:}/{@code apace:} — recognize
     * real-world packs (e.g. CrystalWeaver) that declare power types in the
     * {@code apoli:} namespace, instead of letting them fall through to the native
     * codec and log "Unknown power type". Mirrors the namespace stripping the
     * action/condition parsers already perform (see {@code ActionParser}).
     *
     * <p>No-op (returns the unchanged type) for namespaces outside the family,
     * including native {@code neoorigins:} and already-canonical {@code origins:}/
     * {@code apace:} powers. The reverse mistake — a legacy type spelled
     * {@code neoorigins:} — is handled by {@link #salvageLegacyPowerSpelling}, which
     * is deliberately NOT folded in here: this method must stay usable outside a
     * bootstrapped game (see {@code CompatTestHarness}) and that one reads the power
     * registry.
     */
    public static String canonicalizePowerType(JsonObject json) {
        if (!json.has("type")) return "";
        String type = json.get("type").getAsString();
        int colon = type.indexOf(':');
        if (colon > 0 && APOLI_FAMILY_NS.contains(type.substring(0, colon))) {
            String canonical = "origins:" + type.substring(colon + 1);
            json.addProperty("type", canonical);
            return canonical;
        }
        return type;
    }

    /** Legacy spellings already reported, so the advice is logged once per boot. */
    private static final java.util.Set<String> WARNED_LEGACY_SPELLING =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Rewrites a {@code neoorigins:<name>} power type to {@code origins:<name>} when
     * the name exists ONLY in the legacy compat vocabulary, and returns the resulting
     * type. Any other type is returned unchanged.
     *
     * <p>The compat types are reachable as {@code origins:}/{@code apace:} and nowhere
     * else, so a pack that reaches for {@code neoorigins:action_on_item_use} — a
     * natural mistake, since the mod is called NeoOrigins and every other type the
     * author writes really is {@code neoorigins:} — resolves to nothing and has the
     * power dropped outright. That silently costs the pack an origin: enough dropped
     * powers and the coverage gate hides it entirely.
     *
     * <p>Two guards keep this from being a blanket namespace alias, which would be
     * badly wrong. The name must be claimed by one of the two dispatch switches, and
     * it must have NO native power type — so the 34 names that exist in both
     * vocabularies keep resolving natively, exactly as before. Together they mean
     * this only ever fires where the alternative was dropping the power, and it
     * cannot take traffic from anything that loads today.
     *
     * <p>The native check is {@code PowerTypes.isBuiltinPath}, NOT {@code
     * PowerTypes.get}: the latter reads a registry that stays null until
     * {@code NewRegistryEvent} fires, so outside a running game it answers by luck
     * of initialisation order — which would make this guard's behaviour untestable
     * and unreproducible in the harnesses.
     *
     * <p>⚠ Still touches {@code PowerTypes}, whose class init registers into a
     * DeferredRegister and therefore needs a bootstrapped game. That is why this is
     * separate from {@link #canonicalizePowerType}, which {@code CompatTestHarness}
     * calls with no bootstrap at all.
     *
     * <p>The author is still told. A pack that only works by our goodwill should say
     * so in the log, or the mistake never gets fixed upstream.
     */
    public static String salvageLegacyPowerSpelling(JsonObject json) {
        if (!json.has("type")) return "";
        String type = json.get("type").getAsString();
        if (!type.startsWith("neoorigins:")) return type;
        String name = type.substring("neoorigins:".length());
        String legacy = "origins:" + name;
        boolean dispatchable = OriginsPowerTranslator.ROUTE_A_TYPES.contains(legacy)
            || OriginsCompatPowerLoader.ROUTE_B_TYPES.contains(legacy)
            || OriginsCompatPowerLoader.CONDITIONED_ROUTE_B_TYPES.contains(legacy);
        if (!dispatchable) return type;
        if (com.cyberday1.neoorigins.power.registry.PowerTypes.isBuiltinPath(name)) {
            return type;
        }
        json.addProperty("type", legacy);
        if (WARNED_LEGACY_SPELLING.add(name)) {
            com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                "Power type '{}' does not exist — reading it as '{}', the Origins compat type of"
                + " that name. Fix the namespace in the pack: these types are only spelled"
                + " 'origins:'.", type, legacy);
        }
        return legacy;
    }

    /**
     * The complete set of legacy power {@code type} ids this build accepts: the
     * verbatim case labels of both compat dispatch switches
     * ({@link OriginsPowerTranslator#ROUTE_A_TYPES},
     * {@link OriginsCompatPowerLoader#ROUTE_B_TYPES} and
     * {@link OriginsCompatPowerLoader#CONDITIONED_ROUTE_B_TYPES}) plus, for every
     * {@code origins:} entry, its {@code apoli:} and {@code apugli:} spellings —
     * which {@link #canonicalizePowerType} rewrites to {@code origins:} before
     * dispatch, unconditionally, for every power entry.
     *
     * <p>This is the authorable legacy surface, and the schema's {@code type} enum
     * is built from it. The bound matters both ways: an id missing from here is one
     * the editors and the web validator reject even though the pack loads fine (the
     * defect this exists to fix), and an id in here that does NOT dispatch is a
     * schema that lies — the pack validates, then logs "Unknown power type" at load.
     *
     * <p>{@code apace:} is NOT derived. For actions and conditions the parsers strip
     * the namespace wholesale, so blanket {@code apace:} aliasing is sound there; for
     * POWERS the two switches enumerate {@code apace:} case by case (three
     * {@code origins:} paths have no {@code apace:} sibling), so it is read from the
     * case labels instead of generated.
     *
     * <p>Deriving the Apoli-family spellings does advertise ids upstream Apugli never
     * defined (e.g. {@code apugli:active_self}). That is intentional: the contract is
     * what THIS parser accepts, and canonicalisation accepts them.
     */
    public static java.util.Set<String> legacyPowerTypeSurface() {
        java.util.Set<String> ids = new java.util.TreeSet<>(OriginsPowerTranslator.ROUTE_A_TYPES);
        ids.addAll(OriginsCompatPowerLoader.ROUTE_B_TYPES);
        ids.addAll(OriginsCompatPowerLoader.CONDITIONED_ROUTE_B_TYPES);
        for (String id : java.util.List.copyOf(ids)) {
            if (!id.startsWith("origins:")) continue;
            String path = id.substring("origins:".length());
            for (String ns : APOLI_FAMILY_NS) ids.add(ns + ":" + path);
        }
        return ids;
    }
}
