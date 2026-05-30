package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.ConditionType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in {@link ConditionType} descriptors — the registry refactor's
 * static, class-load-time source of truth for condition verbs that have been
 * migrated off the {@code ConditionParser} switch. Condition analogue of
 * {@link com.cyberday1.neoorigins.compat.action.BuiltinActions}; see that type
 * for the full rationale.
 *
 * <p><b>Why static, not just the NeoForge registry?</b> {@link com.cyberday1.neoorigins.compat.registry.CompatRegistries}
 * exposes the verb set via {@code conditionKeys()}, but that reads the live
 * registry which only populates after {@code NewRegistryEvent} fires — i.e. never
 * in the headless harnesses ({@code compatTest}, {@code goldenMaster},
 * {@code schemaFormCheck}). This table is available the moment the class loads,
 * with or without a running NeoForge, so it can back both the parser's dispatch
 * and {@code KNOWN_TYPES} auditing headlessly. At mod init
 * {@code CompatRegistries.register} copies every entry into the DeferredRegister,
 * so runtime lookups and addon contributions see the same descriptors through the
 * registry.
 *
 * <p>Migration is verb-by-verb (locked decision D1): each entry added here lets
 * its {@code case} arm be deleted from {@code ConditionParser}, gated on the
 * golden-master staying byte-identical and {@code SchemaFormCheck} green.
 */
public final class BuiltinConditions {

    private BuiltinConditions() {}

    /** Insertion-ordered so registration/audit output is deterministic. */
    private static final Map<Identifier, ConditionType> DESCRIPTORS = new LinkedHashMap<>();
    /** Canonical {@code "neoorigins:<verb>"} string → descriptor, for hot-path dispatch. */
    private static final Map<String, ConditionType> BY_KEY = new java.util.HashMap<>();

    private static void define(String path, ConditionType.Factory factory, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        ConditionType type = new ConditionType(id, factory, fields);
        DESCRIPTORS.put(id, type);
        BY_KEY.put(id.toString(), type);
    }

    /**
     * Define an aliased descriptor: {@code path} is the canonical id; every entry
     * in {@code aliasPaths} dispatches to the same factory. Only the canonical id
     * is registered ({@link #DESCRIPTORS} / the live registry) and counted toward
     * the type total — the aliases are known-verb synonyms (lift-and-shift of a
     * multi-label {@code case "a", "b" ->} switch arm), routed through
     * {@link #BY_KEY} so {@code ConditionParser} dispatch accepts them verbatim,
     * and surfaced to {@code SchemaFormCheck} via {@link #aliasIds()} so the
     * {@code KNOWN_TYPES} parity check treats them as handled.
     */
    private static void define(String path, List<String> aliasPaths,
                               ConditionType.Factory factory, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        List<Identifier> aliases = aliasPaths.stream()
            .map(p -> Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, p))
            .toList();
        ConditionType type = new ConditionType(id, factory, fields, aliases);
        DESCRIPTORS.put(id, type);
        BY_KEY.put(id.toString(), type);
        for (Identifier alias : aliases) BY_KEY.put(alias.toString(), type);
    }

    static {
        // Descriptors are migrated here verb-by-verb off the ConditionParser
        // switch (locked decision D1). Each is the lift-and-shift of the old case
        // body; the parser dispatches through BuiltinConditions for migrated verbs.

        // ---- Zero-config marker conditions (no JSON fields read) ----
        // sneaking — player is crouching.
        define("sneaking", (json, ctx) -> p -> p.isShiftKeyDown(), List.of());
        // sprinting — player is sprinting.
        define("sprinting", (json, ctx) -> p -> p.isSprinting(), List.of());
        // on_ground — player is standing on ground.
        define("on_ground", (json, ctx) -> p -> p.onGround(), List.of());
        // in_water — player is in a water column.
        define("in_water", (json, ctx) -> p -> p.isInWater(), List.of());
        // swimming — player is in the swimming pose.
        define("swimming", (json, ctx) -> p -> p.isSwimming(), List.of());
        // submerged_in_water — player's eyes are underwater.
        define("submerged_in_water", (json, ctx) -> p -> p.isUnderWater(), List.of());
        // fall_flying — player is gliding with an elytra.
        define("fall_flying", (json, ctx) -> p -> p.isFallFlying(), List.of());
        // invisible — player has the invisibility flag set.
        define("invisible", (json, ctx) -> p -> p.isInvisible(), List.of());
        // using_item — player is actively using/charging an item.
        define("using_item", (json, ctx) -> p -> p.isUsingItem(), List.of());
        // ticking — player entity has not been removed.
        define("ticking", (json, ctx) -> p -> !p.isRemoved(), List.of());
        // exists — player is present and not removed.
        define("exists", (json, ctx) -> p -> p != null && !p.isRemoved(), List.of());
        // living — player is alive.
        define("living", (json, ctx) -> p -> p.isAlive(), List.of());
        // creative_flying — player's flying ability is active.
        define("creative_flying", (json, ctx) -> p -> p.getAbilities().flying, List.of());
        // climbing — player is on a climbable block.
        define("climbing", (json, ctx) -> p -> p.onClimbable(), List.of());
        // moving — player has nonzero horizontal delta movement.
        define("moving", (json, ctx) -> p -> {
            var dm = p.getDeltaMovement();
            return dm.x != 0 || dm.z != 0;
        }, List.of());
        // passenger / riding — player is riding a vehicle. `riding` is a synonym.
        define("passenger", List.of("riding"), (json, ctx) -> p -> p.isPassenger(), List.of());
        // on_fire / fire — player is on fire. `fire` is a synonym.
        define("on_fire", List.of("fire"), (json, ctx) -> p -> p.isOnFire(), List.of());
    }

    /** Descriptor for the given canonical {@code "neoorigins:<verb>"} id, or {@code null}. */
    public static ConditionType get(String canonicalType) {
        return BY_KEY.get(canonicalType);
    }

    /** All built-in condition descriptors, in registration order. */
    public static Map<Identifier, ConditionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }

    /**
     * Canonical {@code neoorigins:<verb>} id strings for every descriptor — the
     * type total the audit counts (aliases excluded, since an alias is not a
     * separate type).
     */
    public static java.util.Set<String> canonicalIds() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (Identifier rl : DESCRIPTORS.keySet()) ids.add(rl.toString());
        return ids;
    }

    /**
     * Alias id strings across all descriptors (synonyms that dispatch to a
     * canonical verb). Surfaced so {@code SchemaFormCheck} can treat them as known
     * verbs in the {@code KNOWN_TYPES} parity check without counting them as
     * separate types.
     */
    public static java.util.Set<String> aliasIds() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (ConditionType t : DESCRIPTORS.values()) {
            for (Identifier alias : t.aliases()) ids.add(alias.toString());
        }
        return ids;
    }
}
