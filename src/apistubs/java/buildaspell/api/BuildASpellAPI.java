package buildaspell.api;

import buildaspell.spell.Spell;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Compile-only API stub for Build A Spell's public API.
 *
 * <p>This is NOT Build A Spell's real class — it is a minimal signature stand-in
 * carrying ONLY the members {@code BuildASpellBridge} references, with the EXACT
 * descriptors of the real API (verified via {@code javap} against the published
 * jar) so the bridge's compiled bytecode resolves against real BaS at runtime.
 * Adding or widening a parameter here would compile but then throw
 * {@code NoSuchMethodError} against the real mod, so keep these signatures in
 * lockstep with the real {@code buildaspell.api.BuildASpellAPI}.
 *
 * <p>See {@link buildaspell.spell.Spell} for why this stub exists and how it is
 * wired (apiStubs source set → main compile classpath only; never bundled).
 */
public final class BuildASpellAPI {

    private BuildASpellAPI() {}

    public static Spell createSpell(String delivery, List<String> components) {
        return null;
    }

    public static boolean cast(ServerPlayer player, Spell spell, boolean consumeMana) {
        return false;
    }
}
