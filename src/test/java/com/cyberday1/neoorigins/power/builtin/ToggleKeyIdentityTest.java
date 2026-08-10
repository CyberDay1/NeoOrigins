package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the reported {@code effect_over_time} collision: a pack author
 * gave one origin two active {@code effect_over_time} powers, and pressing the
 * keybind on either switched both on. They worked around it by giving the two
 * powers different {@code interval} values, 19 and 20.
 *
 * <p>That workaround is the diagnosis. Toggle state was keyed off the power's
 * CONFIG rather than its identity, and for this family the key was
 * {@code class + type + interval}. Class and type are constant across every
 * {@code effect_over_time} power, so {@code interval} was the only thing telling
 * two of them apart: equal intervals meant one shared flag. Nothing about the
 * two configs otherwise had to match, so powers with completely different
 * actions, conditions and costs collided just as readily.
 *
 * <p>The same shape covered {@code condition_passive} and, keyed off effect ids
 * instead, {@code persistent_effect}. Keys are now the power's resource id,
 * which is unique by construction.
 *
 * <p>The second half of the fix is pinned here too: the change orphans every
 * toggle flag already written into a save, so the old key is still READ as a
 * fallback and retired on the next write. Without that, updating would spring
 * every player's toggles back on.
 */
class ToggleKeyIdentityTest {

    private static final Identifier POWER_A = Identifier.parse("mypack:aura_a");
    private static final Identifier POWER_B = Identifier.parse("mypack:aura_b");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static EffectOverTimePower.Config effectOverTime(String json) {
        return EffectOverTimePower.Config.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(json))
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg)).getFirst();
    }

    private static ConditionPassivePower.Config conditionPassive(String json) {
        return ConditionPassivePower.Config.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(json))
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg)).getFirst();
    }

    private static PersistentEffectPower.Config persistentEffect(String json) {
        return PersistentEffectPower.Config.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(json))
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg)).getFirst();
    }

    // ── The bug as reported ─────────────────────────────────────────────

    /**
     * Two powers authored from byte-identical JSON, which is the worst case: if
     * anything in the config could tell them apart, the collision would be
     * narrower than it is.
     */
    @Test
    void effectOverTimePowersWithIdenticalConfigsGetDistinctKeys() {
        String json = """
            { "type": "neoorigins:effect_over_time", "activation": "active", "interval": 20,
              "entity_action": { "type": "neoorigins:heal", "amount": 1 } }
            """;
        EffectOverTimePower power = new EffectOverTimePower();
        EffectOverTimePower.Config a = effectOverTime(json);
        EffectOverTimePower.Config b = effectOverTime(json);

        assertEquals(power.legacyToggleKey(a), power.legacyToggleKey(b),
            "byte-identical JSON must land on one pre-2.2.24 key, or this is not reproducing the report");
        assertNotEquals(power.toggleKey(POWER_A, a), power.toggleKey(POWER_B, b),
            "two effect_over_time powers must never share a toggle flag");
        assertEquals(POWER_A.toString(), power.toggleKey(POWER_A, a),
            "the key is the power's own id");
    }

    /**
     * The workaround, pinned as the diagnosis: under the old formula, differing
     * intervals were the only thing that separated two of these powers. If this
     * ever stops holding, the explanation given to the reporter is wrong.
     */
    @Test
    void theOldKeyCollidedOnEverythingButInterval() {
        EffectOverTimePower power = new EffectOverTimePower();
        EffectOverTimePower.Config plain = effectOverTime("""
            { "type": "neoorigins:effect_over_time", "activation": "active", "interval": 20 }
            """);
        EffectOverTimePower.Config elaborate = effectOverTime("""
            { "type": "neoorigins:effect_over_time", "activation": "active", "interval": 20,
              "hunger_cost": 2, "condition": { "type": "neoorigins:in_water" },
              "entity_action": { "type": "neoorigins:heal", "amount": 3 } }
            """);
        EffectOverTimePower.Config shifted = effectOverTime("""
            { "type": "neoorigins:effect_over_time", "activation": "active", "interval": 19 }
            """);

        assertNotEquals(plain, elaborate, "these configs differ in everything but interval");
        assertEquals(power.legacyToggleKey(plain), power.legacyToggleKey(elaborate),
            "the pre-2.2.24 key ignored every field but interval, which is the reported bug");
        assertNotEquals(power.legacyToggleKey(plain), power.legacyToggleKey(shifted),
            "changing the interval was the author's workaround, so it has to have separated them");
    }

    @Test
    void conditionPassivePowersWithIdenticalConfigsGetDistinctKeys() {
        String json = """
            { "type": "neoorigins:condition_passive", "toggleable": true, "interval": 20,
              "condition": { "type": "neoorigins:in_water" } }
            """;
        ConditionPassivePower power = new ConditionPassivePower();
        ConditionPassivePower.Config a = conditionPassive(json);
        ConditionPassivePower.Config b = conditionPassive(json);

        assertNotEquals(power.toggleKey(POWER_A, a), power.toggleKey(POWER_B, b),
            "two condition_passive powers must never share a toggle flag");
        assertEquals(power.legacyToggleKey(a), power.legacyToggleKey(b),
            "they did share one before 2.2.24");
    }

    /**
     * {@code persistent_effect} keyed off its effect ids, so two powers granting
     * the same effect at different strengths collided: amplifier and duration
     * were not part of the key.
     */
    @Test
    void persistentEffectPowersWithTheSameEffectGetDistinctKeys() {
        PersistentEffectPower power = new PersistentEffectPower();
        PersistentEffectPower.Config weak = persistentEffect("""
            { "type": "neoorigins:persistent_effect", "toggleable": true,
              "effects": [ { "effect": "minecraft:speed", "amplifier": 0 } ] }
            """);
        PersistentEffectPower.Config strong = persistentEffect("""
            { "type": "neoorigins:persistent_effect", "toggleable": true,
              "effects": [ { "effect": "minecraft:speed", "amplifier": 3 } ] }
            """);

        assertNotEquals(weak, strong, "different amplifiers, so these are genuinely two powers");
        assertNotEquals(power.toggleKey(POWER_A, weak), power.toggleKey(POWER_B, strong),
            "two persistent_effect powers must never share a toggle flag");
        assertEquals(power.legacyToggleKey(weak), power.legacyToggleKey(strong),
            "the pre-2.2.24 key was the effect ids alone, so amplifier never separated them");
    }

    // ── Falling back outside a dispatch ─────────────────────────────────

    /**
     * The id is ambient only inside a {@link com.cyberday1.neoorigins.api.power.PowerHolder}
     * dispatch, so it can be null. Falling back to the legacy key keeps the
     * pre-2.2.24 behaviour rather than inventing a third key shape that would
     * match neither the saved flag nor the new one.
     */
    @Test
    void aNullDispatchIdFallsBackToTheLegacyKey() {
        EffectOverTimePower power = new EffectOverTimePower();
        EffectOverTimePower.Config cfg = effectOverTime("""
            { "type": "neoorigins:effect_over_time", "activation": "active" }
            """);
        assertEquals(power.legacyToggleKey(cfg), power.toggleKey(null, cfg));
    }

    // ── Saved flags survive the change ──────────────────────────────────

    /**
     * A player who had the power switched off before updating must still find it
     * switched off afterwards. The flag in their save is under the old key and
     * nothing rewrites it on load, so the read has to look at both.
     */
    @Test
    void aFlagSavedUnderTheOldKeyStillReadsAsOff() {
        PlayerOriginData data = new PlayerOriginData();
        EffectOverTimePower power = new EffectOverTimePower();
        EffectOverTimePower.Config cfg = effectOverTime("""
            { "type": "neoorigins:effect_over_time", "activation": "active" }
            """);
        String legacy = power.legacyToggleKey(cfg);
        String current = power.toggleKey(POWER_A, cfg);

        data.setPowerToggledOff(legacy, true);   // as written by a pre-2.2.24 build

        assertTrue(data.isPowerToggledOff(current, legacy),
            "updating must not spring every saved toggle back on");
        assertFalse(data.isPowerToggledOff(current, null),
            "and the fallback is what does it, not the new key");
    }

    /**
     * The save heals as it is played: the first write under the new key drops the
     * old one, so the fallback stops applying to that power. Until then both of
     * a colliding pair read "off", which is what the player last chose — a
     * migration pass would have had to pick one of them.
     */
    @Test
    void writingTheNewKeyRetiresTheLegacyOne() {
        PlayerOriginData data = new PlayerOriginData();
        EffectOverTimePower power = new EffectOverTimePower();
        EffectOverTimePower.Config cfg = effectOverTime("""
            { "type": "neoorigins:effect_over_time", "activation": "active" }
            """);
        String legacy = power.legacyToggleKey(cfg);
        String keyA = power.toggleKey(POWER_A, cfg);
        String keyB = power.toggleKey(POWER_B, cfg);

        data.setPowerToggledOff(legacy, true);
        assertTrue(data.isPowerToggledOff(keyB, legacy), "the twin reads off from the shared flag too");

        // The player switches power A back on. That retires the shared flag.
        data.setPowerToggledOff(keyA, legacy, false);

        assertFalse(data.isPowerToggledOff(keyA, legacy), "A is on");
        assertFalse(data.isPowerToggledOff(keyB, legacy),
            "and B is no longer dragged off by A's old shared flag");

        // From here the two are independent, which is the whole point.
        data.setPowerToggledOff(keyB, legacy, true);
        assertTrue(data.isPowerToggledOff(keyB, legacy), "B is off");
        assertFalse(data.isPowerToggledOff(keyA, legacy), "A is untouched by B");
    }
}
