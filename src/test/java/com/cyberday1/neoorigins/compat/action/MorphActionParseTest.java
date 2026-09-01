package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.power.morph.MorphEntityEvents;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parse-time contract for the two morph action verbs.
 *
 * <p>Nothing here executes an action: both verbs' whole runtime is a packet
 * broadcast, which needs a live server. What is worth pinning down headlessly is
 * the parser's accept/reject surface — especially the two <em>safety</em> refusals
 * on {@code morph_entity_event}, which exist to stop a client-side phantom
 * {@code LivingDeathEvent} and a byte wrap, and which would otherwise be easy to
 * relax by accident.
 *
 * <p>A rejected parse degrades to the {@code EntityAction.NOOP} singleton (the
 * house style for "unsupported/invalid" — see {@code ActionParser.failNoop}), so
 * reference identity against it is the assertion.
 */
class MorphActionParseTest {

    private static JsonObject json(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        return o;
    }

    // ── morph_entity_event: the safety refusals ─────────────────────────────

    @Test
    void entityEventRejectsDeathByte() {
        JsonObject o = json("neoorigins:morph_entity_event");
        o.addProperty("id", 3);
        assertSame(EntityAction.NOOP, ActionParser.parse(o, "test:death_byte"),
            "byte 3 calls LivingEntity.die() client-side for an entity that is not in the "
          + "world; it must never reach a dummy");
    }

    @Test
    void entityEventRejectsOutOfByteRange() {
        for (int id : new int[] { 128, -129, 1000 }) {
            JsonObject o = json("neoorigins:morph_entity_event");
            o.addProperty("id", id);
            assertSame(EntityAction.NOOP, ActionParser.parse(o, "test:range"),
                "id " + id + " would wrap into a different event");
        }
    }

    @Test
    void entityEventAcceptsByteRangeEdges() {
        for (int id : new int[] { Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE }) {
            JsonObject o = json("neoorigins:morph_entity_event");
            o.addProperty("id", id);
            assertNotSame(EntityAction.NOOP, ActionParser.parse(o, "test:range_ok"),
                "id " + id + " is in range and is not the death byte");
        }
    }

    // ── morph_entity_event: the named vocabulary ────────────────────────────

    @Test
    void entityEventAcceptsNamedVanillaEvent() {
        JsonObject o = json("neoorigins:morph_entity_event");
        o.addProperty("event", "villager_angry");
        assertNotSame(EntityAction.NOOP, ActionParser.parse(o, "test:named"));
    }

    @Test
    void entityEventRejectsUnknownName() {
        JsonObject o = json("neoorigins:morph_entity_event");
        o.addProperty("event", "definitely_not_an_event");
        assertSame(EntityAction.NOOP, ActionParser.parse(o, "test:bad_name"));
    }

    @Test
    void entityEventNeedsEventOrId() {
        assertSame(EntityAction.NOOP,
            ActionParser.parse(json("neoorigins:morph_entity_event"), "test:empty"));
    }

    @Test
    void namedVocabularyExcludesDeathAndIsSorted() {
        var names = MorphEntityEvents.names();
        assertFalse(names.contains("death"), "death must not be authorable");
        assertNull(MorphEntityEvents.byName("death"));
        assertEquals(names.stream().sorted().toList(), names,
            "the enum order feeds a byte-compared schema, so it must be deterministic");
        // Spot-check that the table really came off Minecraft's constants.
        assertEquals((Byte) net.minecraft.world.entity.EntityEvent.JUMP,
            MorphEntityEvents.byName("jump"));
        assertEquals((Byte) net.minecraft.world.entity.EntityEvent.LOVE_HEARTS,
            MorphEntityEvents.byName("love_hearts"));
        assertTrue(names.size() > 40, "expected the full vanilla event table, got " + names.size());
    }

    @Test
    void nameLookupIsCaseInsensitive() {
        assertNotNull(MorphEntityEvents.byName("VILLAGER_ANGRY"));
    }

    // ── trigger_morph_animation ─────────────────────────────────────────────

    @Test
    void triggerAnimationNeedsAnimationName() {
        assertSame(EntityAction.NOOP,
            ActionParser.parse(json("neoorigins:trigger_morph_animation"), "test:no_anim"));
        JsonObject blank = json("neoorigins:trigger_morph_animation");
        blank.addProperty("animation", "");
        assertSame(EntityAction.NOOP, ActionParser.parse(blank, "test:blank_anim"));
    }

    @Test
    void triggerAnimationParsesWithAndWithoutController() {
        JsonObject bare = json("neoorigins:trigger_morph_animation");
        bare.addProperty("animation", "attack");
        assertNotSame(EntityAction.NOOP, ActionParser.parse(bare, "test:bare"));

        JsonObject full = json("neoorigins:trigger_morph_animation");
        full.addProperty("animation", "attack");
        full.addProperty("controller", "controller");
        full.addProperty("stop", true);
        assertNotSame(EntityAction.NOOP, ActionParser.parse(full, "test:full"));
    }

    /** The legacy-namespace canonicalisation applies to new verbs too. */
    @Test
    void legacyNamespacePrefixStillDispatches() {
        JsonObject o = json("apoli:trigger_morph_animation");
        o.addProperty("animation", "attack");
        assertNotSame(EntityAction.NOOP, ActionParser.parse(o, "test:legacy_ns"));
    }
}
