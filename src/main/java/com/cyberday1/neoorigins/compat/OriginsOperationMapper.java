package com.cyberday1.neoorigins.compat;

/** Maps Origins attribute modifier operation names to NeoOrigins/NeoForge equivalents. */
public final class OriginsOperationMapper {

    private OriginsOperationMapper() {}

    /**
     * Maps an Origins modifier operation string to the NeoOrigins equivalent.
     * Origins uses: "addition", "multiply_base", "multiply_total"
     * NeoOrigins uses: "add_value", "add_multiplied_base", "add_multiplied_total"
     */
    public static String mapOperation(String originsOp) {
        return switch (originsOp) {
            case "addition"             -> "add_value";
            case "multiply_base"        -> "add_multiplied_base";
            case "multiply_total"       -> "add_multiplied_total";
            // Pass-through if already in NeoForge format
            case "add_value"            -> "add_value";
            case "add_multiplied_base"  -> "add_multiplied_base";
            case "add_multiplied_total" -> "add_multiplied_total";
            default -> {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "OriginsCompat: unknown attribute operation '{}', defaulting to add_value", originsOp);
                yield "add_value";
            }
        };
    }
}
