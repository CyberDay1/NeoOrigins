package com.cyberday1.neoorigins.api.resource;

import net.minecraft.resources.Identifier;

/**
 * The declared shape of one backed {@code neoorigins:resource} bar, handed to a backing
 * factory registered through
 * {@link com.cyberday1.neoorigins.api.NeoOriginsAPI#registerResourceBacking(Identifier, java.util.function.Function)}.
 *
 * @param resource   the bar's resource id (the power id)
 * @param min        the bar's declared {@code min}
 * @param max        the bar's declared {@code max}
 * @param startValue the bar's declared {@code start_value}
 */
public record ResourceBar(Identifier resource, int min, int max, int startValue) {}
