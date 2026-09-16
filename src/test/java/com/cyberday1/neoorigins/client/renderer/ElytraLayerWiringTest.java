package com.cyberday1.neoorigins.client.renderer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regression guard for the bug that shipped: a {@code render_elytra} tri-state
 * that resolves, syncs, and draws nothing.
 *
 * <p>{@code ElytraDrawGateTest} pins {@link NeoOriginsElytraLayer#shouldDrawWings}
 * across nine rows and passes perfectly well on a branch where the wings never
 * appear — because it tests the decision in isolation and the defect was one link
 * upstream: the entry point was not asking the always state at all. Both halves of
 * the lane were green on {@code master} and {@code 26.2} while
 * {@code alwaysRendersElytra} had exactly one reference in the whole tree, its own
 * declaration.
 *
 * <p>So this test walks the compiled bytecode of the entry point and insists it
 * reaches both the gate and the always lookup. It cannot be satisfied by a class
 * that merely declares the machinery.
 *
 * <p>Deliberately per-branch. The three lines feed the always state in three
 * different ways - a direct static call here and on {@code 1.21.1}, a render-state
 * {@code ContextKey} on {@code 26.1} - so the expectation below describes this
 * branch only, exactly as the sneak-ledge tests each carry their own epsilon. That
 * is also why this had to be written three times rather than cherry-picked.
 */
// hub: neoorigins/elytra-draw-gate.md
class ElytraLayerWiringTest {

    /** The layer's draw entry point on the 26.2 line. */
    private static final String ENTRY_POINT = "submit";
    private static final String GATE = "shouldDrawWings";
    /** 26.2 looks the always state up statically off the render-state id. */
    private static final String ALWAYS_LOOKUP = "alwaysRendersElytra";
    private static final String RENDER_LOOKUP = "shouldRenderElytra";

    @Test
    void theDrawEntryPointDelegatesToTheGate() {
        List<String> calls = namesIn(ENTRY_POINT);
        assertTrue(calls.contains(GATE),
            ENTRY_POINT + " no longer calls " + GATE + ", so the nine rows "
                + "ElytraDrawGateTest pins are not the decision the game makes; calls were " + calls);
    }

    @Test
    void theDrawEntryPointConsultsTheAlwaysState() {
        List<String> calls = namesIn(ENTRY_POINT);
        assertTrue(calls.contains(ALWAYS_LOOKUP),
            "the wings would only ever draw during a glide: " + ENTRY_POINT + " never asks "
                + ALWAYS_LOOKUP + ", which is exactly how render_elytra \"always\" shipped "
                + "dead on the 26.x branches; calls were " + calls);
    }

    @Test
    void theDrawEntryPointStillAsksWhetherTheWingsAreWantedAtAll() {
        // The plain render flag is the condition-gated one. Losing it would draw
        // wings for a power whose condition is unmet.
        assertTrue(namesIn(ENTRY_POINT).contains(RENDER_LOOKUP),
            ENTRY_POINT + " must still read the plain render_elytra flag");
    }

    /** Every method and field name the given method references, in order. */
    private static List<String> namesIn(String methodName) {
        MethodNode method = null;
        for (MethodNode candidate : readLayer().methods) {
            if (candidate.name.equals(methodName)) {
                // Skip the synthetic bridge the generic supertype forces.
                if (method == null || candidate.instructions.size() > method.instructions.size()) {
                    method = candidate;
                }
            }
        }
        assertNotNull(method, NeoOriginsElytraLayer.class.getSimpleName()
            + "." + methodName + " is gone or renamed");

        List<String> calls = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call) calls.add(call.name);
            if (insn instanceof FieldInsnNode field) calls.add(field.name);
        }
        return calls;
    }

    private static ClassNode readLayer() {
        String resource = NeoOriginsElytraLayer.class.getName().replace('.', '/') + ".class";
        try (InputStream in = NeoOriginsElytraLayer.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "could not read " + resource + " off the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }
}
