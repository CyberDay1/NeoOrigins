package com.cyberday1.neoorigins.power.schemaform;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import net.minecraft.resources.Identifier;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Resolves a power type id to its {@code Config} record {@link Class}, the input
 * {@link CodecFieldSpecExtractor} reflects when a power has no structured
 * {@code power.schema.json} branch (the hybrid fallback half of {@link FormModel}).
 *
 * <p>The 2.0 architecture made every {@code PowerType<C>} carry its config as a
 * nested {@code record Config implements PowerConfiguration}. Powers bind {@code C}
 * concretely at their {@code extends} clause, but not always against
 * {@code PowerType} directly — some go through an intermediate base
 * (e.g. {@code AbstractActivePower<C>}, {@code AbstractTogglePower<C>}). So we
 * walk the generic superclass chain: the first parameterized ancestor whose
 * first type argument is a concrete {@code Class} is the bound {@code Config}.
 * A nested-{@code Config} scan backs this up if the chain is ever opaque.
 */
public final class PowerConfigClassResolver {

    private PowerConfigClassResolver() {}

    /** Resolve by registered power type id; {@code null} if unregistered. */
    public static Class<?> resolve(Identifier typeId) {
        PowerType<?> pt = PowerTypes.get(typeId);
        return pt == null ? null : resolve(pt.getClass());
    }

    /** Resolve from a concrete {@code PowerType} class. */
    public static Class<?> resolve(Class<?> powerClass) {
        for (Class<?> c = powerClass; c != null && c != Object.class; c = c.getSuperclass()) {
            Type gs = c.getGenericSuperclass();
            if (gs instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> arg
                        && PowerConfiguration.class.isAssignableFrom(arg)) {
                    return arg;
                }
            }
            if (c.getSuperclass() == PowerType.class || gs == PowerType.class) break;
        }
        return nestedConfigFallback(powerClass);
    }

    /** Last resort: a power that binds {@code C} opaquely still declares its
     *  config as a nested type — find a {@code PowerConfiguration} record. */
    private static Class<?> nestedConfigFallback(Class<?> powerClass) {
        for (Class<?> c = powerClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Class<?> nested : c.getDeclaredClasses()) {
                if (nested.isRecord() && PowerConfiguration.class.isAssignableFrom(nested)) {
                    return nested;
                }
            }
        }
        return null;
    }
}
