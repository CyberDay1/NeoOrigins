package com.cyberday1.neoorigins.compat;

/** Maps Origins attribute modifier operation names to NeoOrigins/NeoForge equivalents. */
public final class OriginsOperationMapper {

    private OriginsOperationMapper() {}

    /**
     * Maps an Origins modifier operation string to the NeoOrigins equivalent.
     * Origins/Calio uses "addition", "multiply_base", "multiply_total" plus the
     * extended "add_base_early/late" and "multiply_*_additive/multiplicative"
     * variants. NeoForge has exactly three: "add_value", "add_multiplied_base",
     * "add_multiplied_total".
     *
     * <p>The Apoli clamp/set operations (min/max/set, base or total) have NO vanilla
     * equivalent — see {@link #isRepresentable(String)}. They still resolve here (to
     * {@code add_value}) for callers that don't pre-filter, but callers that CAN drop
     * a modifier (the attribute translators) should consult {@code isRepresentable}
     * first rather than mis-applying a clamp as a flat addition.
     */
    public static String mapOperation(String originsOp) {
        // Normalize case: packs authored against NeoForge's modifier JSON use the
        // uppercase enum form (e.g. "ADD_MULTIPLIED_TOTAL"), while Apoli/Calio use
        // lowercase ("add_multiplied_total"). Without this, the uppercase form fell
        // through to the default branch and was silently demoted to add_value —
        // turning a multiply into a flat add and corrupting the attribute.
        String op = originsOp == null ? "" : originsOp.toLowerCase(java.util.Locale.ROOT);
        return switch (op) {
            // Additive → add to the base value.
            case "addition", "add_base_early", "add_base_late"            -> "add_value";
            // Multiply the base. (Calio's *_multiplicative has no exact vanilla
            // analogue; add_multiplied_base is the closest single-modifier match.)
            case "multiply_base", "multiply_base_additive",
                 "multiply_base_multiplicative"                           -> "add_multiplied_base";
            // Multiply the running total.
            case "multiply_total", "multiply_total_additive",
                 "multiply_total_multiplicative"                          -> "add_multiplied_total";
            // Pass-through if already in NeoForge format.
            case "add_value"            -> "add_value";
            case "add_multiplied_base"  -> "add_multiplied_base";
            case "add_multiplied_total" -> "add_multiplied_total";
            // Apoli/Calio clamp + set ops have NO single-modifier vanilla analogue
            // (see isRepresentable). Callers that can drop a modifier should consult
            // isRepresentable() first; for those that can't, add_value is the
            // least-wrong fallback. These are known-unrepresentable, not unknown, so
            // they don't deserve the scary warning.
            case "set_base", "set_total",
                 "min_base", "max_base",
                 "min_total", "max_total"                                 -> "add_value";
            default -> {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "OriginsCompat: unknown attribute operation '{}', defaulting to add_value", originsOp);
                yield "add_value";
            }
        };
    }

    /**
     * True if {@code originsOp} can be faithfully expressed as a single vanilla
     * {@link net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation}.
     * The Apoli/Calio clamp and set operations cannot: a static modifier only adds
     * or multiplies, it cannot floor, ceil, or set a value. Mapping those to
     * {@code add_value} actively corrupts the attribute — e.g. Deano's mage had a
     * {@code "max_total": 60} max-health <i>cap</i> that became a flat {@code +60}
     * health bonus, pinning the mage at ~37 hearts instead of the intended 7.
     * Callers that build a modifier should drop it when this returns false.
     */
    public static boolean isRepresentable(String originsOp) {
        String op = originsOp == null ? "" : originsOp.toLowerCase(java.util.Locale.ROOT);
        return switch (op) {
            case "set_base", "set_total",
                 "min_base", "max_base",
                 "min_total", "max_total" -> false;
            default -> true;
        };
    }
}
