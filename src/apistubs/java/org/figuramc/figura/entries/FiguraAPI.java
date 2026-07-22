package org.figuramc.figura.entries;

import org.figuramc.figura.avatar.Avatar;

import java.util.Collection;

/**
 * Compile-only API stub for Figura's {@code FiguraAPI} interface.
 *
 * <p>Signature stand-in only — mirrors the EXACT four abstract methods of the real
 * interface (verified via {@code javap} against figura-0.1.6-neoforge) so the
 * compat classes ({@code NeoOriginsFiguraPlugin}, {@code NeoOriginsFiguraGlobal})
 * implement the real interface at runtime. None of these signatures reference
 * MC-version-specific types, which is why the stub is version-portable. See
 * {@link org.figuramc.figura.avatar.Avatar} for why this stub exists (no MC 26.2
 * Figura build) and how it is wired (apiStubs source set → compile classpath only).
 */
public interface FiguraAPI {

    FiguraAPI build(Avatar avatar);

    String getName();

    Collection<Class<?>> getWhitelistedClasses();

    Collection<Class<?>> getDocsClasses();
}
