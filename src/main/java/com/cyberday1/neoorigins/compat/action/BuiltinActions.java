package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.ActionType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in {@link ActionType} descriptors — the registry refactor's static,
 * class-load-time source of truth for action verbs that have been migrated off
 * the {@code ActionParser} switch.
 *
 * <p><b>Why static, not just the NeoForge registry?</b> {@link com.cyberday1.neoorigins.compat.registry.CompatRegistries}
 * exposes the verb set via {@code actionKeys()}, but that reads the live registry
 * which only populates after {@code NewRegistryEvent} fires — i.e. never in the
 * headless harnesses ({@code compatTest}, {@code goldenMaster}, {@code schemaFormCheck}).
 * This table is available the moment the class loads, with or without a running
 * NeoForge, so it can back both the parser's dispatch and {@code KNOWN_TYPES}
 * auditing headlessly. At mod init {@code CompatRegistries.register} copies every
 * entry into the DeferredRegister, so runtime lookups and addon contributions
 * see the same descriptors through the registry.
 *
 * <p>Migration is verb-by-verb (locked decision D1): each entry added here lets
 * its {@code case} arm be deleted from {@code ActionParser}, gated on the
 * golden-master staying byte-identical and {@code SchemaFormCheck} green.
 */
public final class BuiltinActions {

    private BuiltinActions() {}

    /** Insertion-ordered so registration/audit output is deterministic. */
    private static final Map<Identifier, ActionType> DESCRIPTORS = new LinkedHashMap<>();
    /** Canonical {@code "neoorigins:<verb>"} string → descriptor, for hot-path dispatch. */
    private static final Map<String, ActionType> BY_KEY = new java.util.HashMap<>();

    private static void define(String path, ActionType.Factory factory, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        ActionType type = new ActionType(id, factory, fields);
        DESCRIPTORS.put(id, type);
        BY_KEY.put(id.toString(), type);
    }

    static {
        // nothing — explicit no-op. Lift-and-shift of `case "neoorigins:nothing"
        // -> EntityAction.noop()`. No config fields.
        define("nothing", (json, ctx) -> EntityAction.noop(), List.of());

        // extinguish — clear the player's fire. No config fields.
        define("extinguish", (json, ctx) -> player -> player.clearFire(), List.of());

        // dismount — stop riding the current vehicle. No config fields.
        define("dismount", (json, ctx) -> player -> player.stopRiding(), List.of());

        // heal — restore health. Lift-and-shift of parseHeal. `amount` is
        // optional at parse time (parser falls back to 1.0), so it's modelled
        // optional-with-default rather than required — the FieldSpec reflects the
        // parser's actual contract, which collapses the two redundant schema
        // branches (shared "amount-only" vs. the per-verb branch) into one shape.
        define("heal",
            (json, ctx) -> {
                float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
                return player -> player.heal(amount);
            },
            List.of(new FieldSpec("amount", FormFieldSpec.Kind.NUMBER, false)
                .def(1.0)
                .doc("Health points to restore (1.0 = half a heart; default 1.0).")));
    }

    /** Descriptor for the given canonical {@code "neoorigins:<verb>"} id, or {@code null}. */
    public static ActionType get(String canonicalType) {
        return BY_KEY.get(canonicalType);
    }

    /** All built-in action descriptors, in registration order. */
    public static Map<Identifier, ActionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }
}
