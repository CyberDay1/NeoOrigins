package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring contract for the conduit land bypass.
 *
 * <p>Background: {@code c281f612} added {@code hasEffect(CONDUIT_POWER)} to the
 * dry-out exemption and to {@code AttributeModifierPower}'s {@code on_land}
 * condition, and the whole gate went green — because nothing exercised it.
 * Neither clause could ever fire. Vanilla's {@code ConduitBlockEntity} gates
 * the effect behind {@code isInWaterOrRain()}, so a player drying out on land
 * was never granted it in the first place. The fix compiled, read correctly,
 * and did nothing for four months.
 *
 * <p>The lesson that shapes this file: the defect was not in any value the code
 * computed, so no assertion about a computed value would have caught it. It was
 * that the two halves of the mechanism were not connected. So these tests assert
 * the connections — the capability the mixin queries is the one the power
 * publishes, and the mixin that makes the exemption reachable is actually
 * registered. Both are silent, total failures if broken, and neither shows up
 * as a wrong number anywhere.
 */
class BreathOutOfFluidConduitReachTest {

    private static BreathOutOfFluidPower.Config decode(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return BreathOutOfFluidPower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    /**
     * {@code ConduitBlockEntityMixin} decides whether to lift vanilla's gate by
     * asking {@code ActiveOriginService.hasCapability(player, DRIES_OUT_CAPABILITY)}.
     * If the power ever stopped publishing that tag the mixin would quietly stop
     * matching anyone and the bug would return exactly as it was — no error, no
     * log line, just the drain running again beside an active conduit.
     */
    @Test
    void theCapabilityTheMixinQueriesIsTheOneThePowerPublishes() {
        var power = new BreathOutOfFluidPower();
        var caps = power.capabilities(decode("""
            { "type": "neoorigins:breath_out_of_fluid", "fluid": "water" }
            """));
        assertTrue(caps.contains(BreathOutOfFluidPower.DRIES_OUT_CAPABILITY),
            "breath_out_of_fluid must publish " + BreathOutOfFluidPower.DRIES_OUT_CAPABILITY
                + " or ConduitBlockEntityMixin matches nobody; published: " + caps);
    }

    /** The tag is also read by the client HUD, so it is load-bearing beyond the mixin. */
    @Test
    void theCapabilityTagIsStable() {
        assertTrue("dries_out_of_water".equals(BreathOutOfFluidPower.DRIES_OUT_CAPABILITY),
            "renaming this tag silently unwires the conduit bypass and the bubble-row "
                + "suppression; update both call sites deliberately if you must");
    }

    /**
     * An unregistered mixin is a no-op that compiles. That is the same shape of
     * failure as the original bug, so the registration is asserted rather than
     * assumed.
     */
    @Test
    void theConduitMixinIsRegistered() {
        assertTrue(declaredMixins().contains("ConduitBlockEntityMixin"),
            "ConduitBlockEntityMixin is absent from neoorigins.mixins.json, so vanilla's "
                + "isInWaterOrRain() gate is never wrapped and the conduit exemption in "
                + "BreathOutOfFluidPower is unreachable again");
    }

    /**
     * The refill suppression has to stand down under Conduit Power too. If it
     * does not, the drain stops but the bubble row never recovers, which reads
     * to a player as the exemption only half working.
     */
    @Test
    void theRefillSuppressionMixinIsRegistered() {
        assertTrue(declaredMixins().contains("LivingEntityAirRefillMixin"),
            "LivingEntityAirRefillMixin owns the out-of-water bubble row; unregistering it "
                + "leaves the drain fighting vanilla's +4/tick refill");
    }

    private static List<String> declaredMixins() {
        try (var in = BreathOutOfFluidConduitReachTest.class
                .getResourceAsStream("/neoorigins.mixins.json")) {
            assertNotNull(in, "neoorigins.mixins.json is not on the test classpath");
            JsonObject root = JsonParser
                .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
            List<String> out = new ArrayList<>();
            for (String key : List.of("mixins", "client", "server")) {
                if (!root.has(key)) continue;
                root.getAsJsonArray(key).forEach(e -> out.add(e.getAsString()));
            }
            return out;
        } catch (Exception e) {
            throw new AssertionError("could not read neoorigins.mixins.json", e);
        }
    }
}
