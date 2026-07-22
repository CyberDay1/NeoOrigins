package org.figuramc.figura.lua;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-only API stub for Figura's {@code @LuaWhitelist} marker annotation.
 *
 * <p>Retention/target mirror the real annotation EXACTLY (verified via {@code javap}
 * against figura-0.1.6-neoforge): {@code RUNTIME} retention, applicable to
 * {@code TYPE}, {@code METHOD}, {@code FIELD}. At runtime Figura's own annotation
 * loads; when Figura is absent the compat classes carrying it are never classloaded.
 * See {@link org.figuramc.figura.avatar.Avatar} for the stub rationale and wiring.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface LuaWhitelist {
}
