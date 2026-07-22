package org.figuramc.figura.avatar;

import java.util.UUID;

/**
 * Compile-only API stub for Figura's {@code Avatar}.
 *
 * <p>This is NOT Figura's real class — it is a minimal signature stand-in carrying
 * ONLY the member {@code compat/figura/NeoOriginsFiguraGlobal} references
 * ({@link #owner}), with the EXACT descriptor of the real field (verified via
 * {@code javap} against figura-0.1.6-neoforge). At runtime Figura's own classes
 * load instead; the compat classes are classloaded solely by Figura's annotation
 * scanner and never touched when Figura is absent.
 *
 * <p>Why a stub instead of the real jar: Figura publishes no MC 26.2 build (the
 * only jar on disk targets 1.21.1), and the API surface we use — a {@code UUID}
 * field and a handful of MC-version-agnostic {@code FiguraAPI} methods — carries
 * no MC-version-specific types, so a hand-written stub is the clean soft-dep path
 * matching this branch's {@code src/apistubs} convention (BaS / Iron's Spells).
 * Wired onto main's COMPILE classpath only; never bundled, never on runtime.
 */
public final class Avatar {

    /** The owning player's UUID — how the compat global resolves whose state to read. */
    public final UUID owner;

    public Avatar(UUID owner) {
        this.owner = owner;
    }
}
