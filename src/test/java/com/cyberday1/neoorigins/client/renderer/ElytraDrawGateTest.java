package com.cyberday1.neoorigins.client.renderer;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
