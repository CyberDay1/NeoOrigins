package com.cyberday1.neoorigins.client.renderer;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the power-granted elytra draw decision itself — the one thing a player can
 * see. {@code FlightWingRenderCapsTest} pins the codec and the wire and was green
 * on branches where the wings drew nothing.
 */
// hub: neoorigins/elytra-draw-gate.md
class ElytraDrawGateTest {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static boolean draw(boolean fallFlying, boolean flag, boolean always, ItemStack chest) {
        return NeoOriginsElytraLayer.shouldDrawWings(fallFlying, flag, always, chest, TEXTURE);
    }

    private static ItemStack empty()       { return ItemStack.EMPTY; }
    private static ItemStack chestplate()  { return new ItemStack(Items.DIAMOND_CHESTPLATE); }
    private static ItemStack realElytra()  { return new ItemStack(Items.ELYTRA); }

    // 1 — the ordinary glide.
    @Test
    void flyingWithFlyingStateDraws() {
        assertTrue(draw(true, true, false, empty()));
    }

    // 2 — "flying" means only while gliding.
    @Test
    void groundedWithFlyingStateDoesNotDraw() {
        assertFalse(draw(false, true, false, empty()));
    }

    // 3 — the tri-state's whole point; dead on master/26.2 before this lane.
    @Test
    void groundedWithAlwaysDraws() {
        assertTrue(draw(false, true, true, empty()));
    }

    // 4 — "always" does not stop applying mid-glide.
    @Test
    void flyingWithAlwaysDraws() {
        assertTrue(draw(true, true, true, empty()));
    }

    // 5 — no power, no wings, whatever the refinement says.
    @Test
    void alwaysWithoutTheRenderFlagDoesNotDraw() {
        assertFalse(draw(false, false, true, empty()));
    }

    // 6 — issue #130's second sentence: a chestplate must not suppress the wings.
    @Test
    void chestplateDoesNotSuppressTheWings() {
        assertTrue(draw(true, true, false, chestplate()));
    }

    // 7 — a real elytra is vanilla's to draw; drawing here would double up.
    @Test
    void realElytraWhileFlyingLeavesItToVanilla() {
        assertFalse(draw(true, true, true, realElytra()));
    }

    // 8 — and the same at rest, so "always" cannot sneak a second pair in.
    @Test
    void realElytraAtRestLeavesItToVanilla() {
        assertFalse(draw(false, true, true, realElytra()));
    }

    // 9 — no texture resolved, nothing to draw. Only reachable in production on
    // 26.1, where the texture arrives through a render-data key that can be absent.
    @Test
    void noResolvedTextureDoesNotDraw() {
        assertFalse(NeoOriginsElytraLayer.shouldDrawWings(true, true, false, empty(), null));
    }

    /**
     * The same 48 inputs, asserted against one literal that is byte-identical on all
     * three release branches. The three lines are different codebases, so a tree diff
     * between them proves nothing; this table is what "they carry the same feature"
     * means, and it fails on whichever line drifts.
     */
    @Test
    void theDrawTableIsIdenticalOnAllThreeReleaseBranches() {
        assertEquals(EXPECTED_TABLE, renderDrawTable());
    }

    private static String renderDrawTable() {
        String[] chestNames = {"empty", "chestplate", "elytra"};
        StringBuilder sb = new StringBuilder();
        for (int ff = 0; ff < 2; ff++)
            for (int fl = 0; fl < 2; fl++)
                for (int al = 0; al < 2; al++)
                    for (int c = 0; c < 3; c++)
                        for (int tx = 0; tx < 2; tx++) {
                            ItemStack chest = c == 0 ? empty() : c == 1 ? chestplate() : realElytra();
                            boolean drawn = NeoOriginsElytraLayer.shouldDrawWings(
                                ff == 1, fl == 1, al == 1, chest, tx == 1 ? TEXTURE : null);
                            // Explicit LF, never %n -- the literal below must not be platform-dependent.
                            sb.append(String.format("fly=%d flag=%d always=%d chest=%-10s tex=%d -> %s",
                                ff, fl, al, chestNames[c], tx, drawn ? "DRAW" : "----")).append("\n");
                        }
        return sb.toString();
    }

    private static final String EXPECTED_TABLE = """
            fly=0 flag=0 always=0 chest=empty      tex=0 -> ----
            fly=0 flag=0 always=0 chest=empty      tex=1 -> ----
            fly=0 flag=0 always=0 chest=chestplate tex=0 -> ----
            fly=0 flag=0 always=0 chest=chestplate tex=1 -> ----
            fly=0 flag=0 always=0 chest=elytra     tex=0 -> ----
            fly=0 flag=0 always=0 chest=elytra     tex=1 -> ----
            fly=0 flag=0 always=1 chest=empty      tex=0 -> ----
            fly=0 flag=0 always=1 chest=empty      tex=1 -> ----
            fly=0 flag=0 always=1 chest=chestplate tex=0 -> ----
            fly=0 flag=0 always=1 chest=chestplate tex=1 -> ----
            fly=0 flag=0 always=1 chest=elytra     tex=0 -> ----
            fly=0 flag=0 always=1 chest=elytra     tex=1 -> ----
            fly=0 flag=1 always=0 chest=empty      tex=0 -> ----
            fly=0 flag=1 always=0 chest=empty      tex=1 -> ----
            fly=0 flag=1 always=0 chest=chestplate tex=0 -> ----
            fly=0 flag=1 always=0 chest=chestplate tex=1 -> ----
            fly=0 flag=1 always=0 chest=elytra     tex=0 -> ----
            fly=0 flag=1 always=0 chest=elytra     tex=1 -> ----
            fly=0 flag=1 always=1 chest=empty      tex=0 -> ----
            fly=0 flag=1 always=1 chest=empty      tex=1 -> DRAW
            fly=0 flag=1 always=1 chest=chestplate tex=0 -> ----
            fly=0 flag=1 always=1 chest=chestplate tex=1 -> DRAW
            fly=0 flag=1 always=1 chest=elytra     tex=0 -> ----
            fly=0 flag=1 always=1 chest=elytra     tex=1 -> ----
            fly=1 flag=0 always=0 chest=empty      tex=0 -> ----
            fly=1 flag=0 always=0 chest=empty      tex=1 -> ----
            fly=1 flag=0 always=0 chest=chestplate tex=0 -> ----
            fly=1 flag=0 always=0 chest=chestplate tex=1 -> ----
            fly=1 flag=0 always=0 chest=elytra     tex=0 -> ----
            fly=1 flag=0 always=0 chest=elytra     tex=1 -> ----
            fly=1 flag=0 always=1 chest=empty      tex=0 -> ----
            fly=1 flag=0 always=1 chest=empty      tex=1 -> ----
            fly=1 flag=0 always=1 chest=chestplate tex=0 -> ----
            fly=1 flag=0 always=1 chest=chestplate tex=1 -> ----
            fly=1 flag=0 always=1 chest=elytra     tex=0 -> ----
            fly=1 flag=0 always=1 chest=elytra     tex=1 -> ----
            fly=1 flag=1 always=0 chest=empty      tex=0 -> ----
            fly=1 flag=1 always=0 chest=empty      tex=1 -> DRAW
            fly=1 flag=1 always=0 chest=chestplate tex=0 -> ----
            fly=1 flag=1 always=0 chest=chestplate tex=1 -> DRAW
            fly=1 flag=1 always=0 chest=elytra     tex=0 -> ----
            fly=1 flag=1 always=0 chest=elytra     tex=1 -> ----
            fly=1 flag=1 always=1 chest=empty      tex=0 -> ----
            fly=1 flag=1 always=1 chest=empty      tex=1 -> DRAW
            fly=1 flag=1 always=1 chest=chestplate tex=0 -> ----
            fly=1 flag=1 always=1 chest=chestplate tex=1 -> DRAW
            fly=1 flag=1 always=1 chest=elytra     tex=0 -> ----
            fly=1 flag=1 always=1 chest=elytra     tex=1 -> ----
            """;
}
