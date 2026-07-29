package com.cyberday1.neoorigins.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why the {@code execute_command} action gates on "does this already parse"
 * rather than on {@link LegacyCommandRewriter#needsRewrite} alone.
 *
 * <p>{@code BuiltinActions}' {@code execute_command} used to call
 * {@link LegacyCommandRewriter#rewrite} unconditionally on every pack-authored
 * command string. That is the semantic tier: heuristics written on the
 * assumption that the command has already failed to parse. Running them on a
 * command that was fine is what took out vanilla attribute commands pack-wide
 * on the chat/mcfunction path (GitHub #92).
 *
 * <p>The obvious cheap defence — only rewrite when {@code needsRewrite} says the
 * line smells legacy — is not enough, and this test is the proof.
 */
class ExecuteCommandRewriteGateTest {

    @Test
    void needsRewriteAloneDoesNotProtectAValidCommand() {
        // `modifier add <id> <value> <operation>` is the modern spelling, but
        // rule 4 only looks for the literal "modifier add " and then rewrites
        // whatever id follows into a synthesised `neoorigins:compat_…` one —
        // silently retargeting a modifier the pack manages itself. (The
        // attribute id spelling is irrelevant to the point being made here.)
        String modern = "attribute @p minecraft:armor modifier add abcdef 1 add_value";

        assertTrue(LegacyCommandRewriter.needsRewrite(modern),
            "the cheap prefilter lets this through, so it cannot be the only gate");
        assertNotEquals(modern, LegacyCommandRewriter.rewrite(modern),
            "the semantic tier really does corrupt it — only a parse check stops that");
    }
}
