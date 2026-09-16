package com.cyberday1.neoorigins.util;

import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code PlayerSneakLedgeMixin}'s injection point resolves, against the
 * Minecraft actually on this branch's classpath.
 *
 * <p>Nothing else in the build checks a mixin target. {@code defaultRequire: 1}
 * turns a miss into a hard load error rather than a silent no-op, but that only
 * fires when the game starts — which is exactly the check that cannot run here.
 * So this test does what Mixin's own selector does: find
 * {@code maybeBackOffFromEdge}, count the {@code maxUpStep()F} calls in it, and
 * insist there is exactly one.
 *
 * <p>The count is the whole point. One call means
 * {@code @ModifyExpressionValue} rewrites the single local that
 * {@code isAboveGround} and every {@code canFallAtLeast} probe share, so they
 * cannot disagree — the invariant the Forge comment above {@code isAboveGround}
 * exists to protect. Two calls would mean the value is read more than once and
 * the substitution is no longer uniform.
 */
// hub: neoorigins/sneak-ledge-guard.md
class PlayerLedgeInjectionPointTest {

    private static final String TARGET_METHOD = "maybeBackOffFromEdge";
    private static final String STEP_HEIGHT_GETTER = "maxUpStep";
    private static final String STEP_HEIGHT_DESC = "()F";

    @Test
    void theStepHeightReadIsSingleAndInjectable() {
        MethodNode method = findMethod(readPlayer(), TARGET_METHOD);
        assertNotNull(method,
            "Player." + TARGET_METHOD + " is gone or renamed — PlayerSneakLedgeMixin "
                + "will fail to apply and the game will refuse to start");

        List<MethodInsnNode> reads = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call
                && STEP_HEIGHT_GETTER.equals(call.name)
                && STEP_HEIGHT_DESC.equals(call.desc)) {
                reads.add(call);
            }
        }

        assertEquals(1, reads.size(),
            "PlayerSneakLedgeMixin targets maxUpStep()F inside " + TARGET_METHOD
                + " and relies on there being exactly one call to rewrite; found "
                + reads.size());
    }

    /**
     * The substitution would be pointless if the probe read the step height
     * straight off the attribute instead of through the local, so pin that
     * {@code canFallAtLeast} takes its depth as a parameter.
     */
    @Test
    void theProbeTakesItsDepthAsAParameterRatherThanReadingItBack() {
        ClassNode player = readPlayer();
        MethodNode probe = findMethod(player, "canFallAtLeast");
        assertNotNull(probe, "Player.canFallAtLeast is gone or renamed");

        for (AbstractInsnNode insn : probe.instructions) {
            assertTrue(!(insn instanceof MethodInsnNode call
                         && STEP_HEIGHT_GETTER.equals(call.name)),
                "canFallAtLeast now reads maxUpStep() itself, so rewriting the caller's "
                    + "local no longer controls the probe depth");
        }
    }

    private static MethodNode findMethod(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name)) return method;
        }
        return null;
    }

    private static ClassNode readPlayer() {
        String resource = Player.class.getName().replace('.', '/') + ".class";
        try (InputStream in = Player.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "could not read " + resource + " off the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }
}
