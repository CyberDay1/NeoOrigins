package org.figuramc.figura.entries.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-only API stub for Figura's {@code @FiguraAPIPlugin} discovery annotation.
 *
 * <p>Retention/target mirror the real annotation EXACTLY (verified via {@code javap}
 * against figura-0.1.6-neoforge): {@code RUNTIME} retention, {@code TYPE} target.
 * Figura's own scanner walks jars for this annotation and reflectively instantiates
 * the hits; the stub only lets {@code NeoOriginsFiguraPlugin} carry it at compile
 * time. See {@link org.figuramc.figura.avatar.Avatar} for the stub rationale.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FiguraAPIPlugin {
}
