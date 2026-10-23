package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.compat.condition.TargetConditionParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The six power types and type changes classic mode needed: what each computes
 * where that is testable without a world, and otherwise that its two halves are
 * wired together (the capability a mixin reads is the one the power publishes,
 * and the mixin is registered). See {@code BreathOutOfFluidConduitReachTest} for
 * why the wiring is asserted rather than assumed.
 */
class ClassicPowerTypesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <C> C decode(Codec<C> codec, String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return codec.parse(JsonOps.INSTANCE, obj).getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID = new java.util.concurrent.atomic.AtomicInteger();

    private static AttributeModifier mod(double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(Identifier.fromNamespaceAndPath("test", "m" + NEXT_ID.incrementAndGet()), amount, op);
    }

    // ── like_air ────────────────────────────────────────────────────────

    @Test
    void likeAirWithoutModifiersLeavesVanillaAirSpeed() {
        assertEquals(0.02, LikeAirPower.transfer(0.02, List.of()), 1e-9);
    }

    /** Tailwind (+20% of base) makes the Avian 20% faster in the air too. */
    @Test
    void likeAirAppliesMultipliedBaseModifiers() {
        assertEquals(0.024, LikeAirPower.transfer(0.02,
            List.of(mod(0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE))), 1e-9);
    }

    /** Vanilla order: additions, then summed base multipliers, then each total factor. */
    @Test
    void likeAirUsesVanillaModifierOrder() {
        double out = LikeAirPower.transfer(0.02, List.of(
            mod(0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            mod(0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            mod(0.01, AttributeModifier.Operation.ADD_VALUE)));
        assertEquals((0.02 + 0.01) * 1.2 * 1.3, out, 1e-9);
    }

    @Test
    void likeAirNeverGoesNegative() {
        assertEquals(0.0, LikeAirPower.transfer(0.02,
            List.of(mod(-5.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))), 1e-9);
    }

    @Test
    void likeAirIsWired() {
        assertTrue(new LikeAirPower().capabilities(decode(LikeAirPower.Config.CODEC, "{}"))
            .contains(LikeAirPower.CAPABILITY));
        assertTrue(declaredMixins().contains("PlayerFlyingSpeedMixin"));
    }

    // ── conduit_power_on_land ───────────────────────────────────────────

    @Test
    void conduitPowerOnLandIsWired() {
        assertTrue(new ConduitPowerOnLandPower().capabilities(decode(ConduitPowerOnLandPower.Config.CODEC, "{}"))
            .contains(ConduitPowerOnLandPower.CAPABILITY));
        assertTrue(declaredMixins().contains("ConduitBlockEntityMixin"));
    }

    // ── cobweb_affinity climb ───────────────────────────────────────────

    @Test
    void webClimbingIsOptIn() {
        var power = new CobwebAffinityPower();
        assertFalse(power.capabilities(decode(CobwebAffinityPower.Config.CODEC, "{}"))
            .contains(CobwebAffinityPower.WEB_CLIMB), "existing cobweb_affinity powers must not start climbing");
        var climbing = power.capabilities(decode(CobwebAffinityPower.Config.CODEC, "{\"climb\": true}"));
        assertTrue(climbing.contains(CobwebAffinityPower.WEB_CLIMB));
        assertTrue(climbing.contains("cobweb_affinity"));
        assertTrue(declaredMixins().contains("LivingEntityClimbMixin"));
    }

    // ── entity_glow ─────────────────────────────────────────────────────

    /** The Arachnid's web sense condition must compile, or the power outlines nothing. */
    @Test
    void webSenseConditionIsEntityGeneral() {
        JsonObject cond = JsonParser.parseString("""
            { "type": "neoorigins:and", "conditions": [
              { "type": "neoorigins:in_block_anywhere",
                "block_condition": { "type": "neoorigins:block", "tag": "neoorigins:cobwebs" } },
              { "type": "neoorigins:in_tag", "tag": "minecraft:arthropod", "inverted": true } ] }
            """).getAsJsonObject();
        assertNotNull(TargetConditionParser.parse(cond, "test"));
        var config = decode(new EntityGlowPower().codec(),
            "{ \"type\": \"neoorigins:entity_glow\", \"entity_condition\": " + cond + " }");
        assertTrue(config.entityCondition().isPresent());
        assertTrue(declaredMixins().contains("client.MinecraftEntityGlowMixin"));
    }

    // ── projectile_vulnerability ────────────────────────────────────────

    @Test
    void projectileVulnerabilityMatchesByIdAndTag() {
        var byId = decode(ProjectileVulnerabilityPower.Config.CODEC,
            "{ \"projectiles\": [\"minecraft:snowball\"] }");
        assertTrue(byId.matches(net.minecraft.world.entity.EntityTypes.SNOWBALL));
        assertFalse(byId.matches(net.minecraft.world.entity.EntityTypes.EGG));
        assertEquals(3.0F, byId.amount(), "Origins' snowball damage is 3");

        // #tag entries resolve against loaded tags, which a unit test has none of;
        // the id path is the one the shipped Blazeborn power uses.
        var byTag = decode(ProjectileVulnerabilityPower.Config.CODEC,
            "{ \"projectiles\": [\"#minecraft:arrows\"], \"amount\": 1.5 }");
        assertEquals(1.5F, byTag.amount());
    }

    // ── exhaustion_filter ───────────────────────────────────────────────

    @Test
    void exhaustionFilterSourcesAreAllWired() {
        assertEquals(java.util.Set.of("sprint", "mining", "jump", "swim"), ExhaustionFilterPower.SOURCES);
        var mixins = declaredMixins();
        for (String m : List.of("ExhaustionMiningMixin", "ExhaustionJumpMixin", "ExhaustionMovementMixin")) {
            assertTrue(mixins.contains(m), m + " is not registered, so its exhaustion source is never filtered");
        }
        assertEquals(List.of("sprint"), decode(ExhaustionFilterPower.Config.CODEC, "{}").sources());
    }

    private static List<String> declaredMixins() {
        try (var in = ClassicPowerTypesTest.class.getResourceAsStream("/neoorigins.mixins.json")) {
            assertNotNull(in, "neoorigins.mixins.json is not on the test classpath");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> out = new ArrayList<>();
            for (String key : List.of("mixins", "client", "server")) {
                if (root.has(key)) root.getAsJsonArray(key).forEach(e -> out.add(e.getAsString()));
            }
            return out;
        } catch (Exception e) {
            throw new AssertionError("could not read neoorigins.mixins.json", e);
        }
    }
}
