package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The entity-general {@code nbt} leaf. Origins++'s calamitous-rogue bad_trade
 * power puts an {@code apoli:nbt} check inside a bientity_condition; without an
 * entity-general form the whole condition refused to compile, and (because
 * {@link TargetConditionParser} fails closed by design) took the power with it.
 *
 * <p>The match is a genuine SNBT <em>partial</em> match through
 * {@code NbtUtils.compareNbt}, the same semantics vanilla's
 * {@code /execute if data} uses — a key-presence check would accept any value
 * and turn "trader with this offer" into "any trader".
 *
 * <p>26.x note: entity serialization goes through {@code ValueOutput} rather
 * than a raw {@code CompoundTag}, and there is no public way to seed a
 * {@link TagValueOutput} with a prepared tag. The factory is therefore stubbed
 * to hand back an output whose {@code buildResult()} is the fixture tag, which
 * is exactly the value the production code compares against.
 */
class TargetNbtConditionTest {

    private MockedStatic<TagValueOutput> tagOutputFactory;
    private TagValueOutput output;

    @BeforeEach
    void stubEntitySerialization() {
        output = mock(TagValueOutput.class);
        tagOutputFactory = mockStatic(TagValueOutput.class);
        tagOutputFactory.when(() -> TagValueOutput.createWithContext(any(), any()))
            .thenReturn(output);
    }

    @AfterEach
    void releaseStub() {
        tagOutputFactory.close();
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** A target whose serialized form is the given SNBT. */
    private LivingEntity targetWithNbt(String snbt) {
        LivingEntity target = mock(LivingEntity.class);
        when(target.level()).thenReturn(mock(Level.class));
        CompoundTag tag;
        try {
            tag = TagParser.parseCompoundFully(snbt);
        } catch (Exception e) {
            throw new AssertionError("test fixture SNBT is malformed: " + snbt, e);
        }
        when(output.buildResult()).thenReturn(tag);
        return target;
    }

    @Test
    void nbtIsGeneralizableToANonPlayerTarget() {
        assertNotNull(TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:nbt\", \"nbt\": \"{Invulnerable:1b}\" }"), "test:bad_trade"),
            "nbt must compile as a target condition, not drop out of the switch");
    }

    /** And therefore through the bientity wrapper the pack actually writes. */
    @Test
    void nbtCompilesThroughATargetConditionWrapper() {
        assertNotNull(TargetConditionParser.parseBiEntity(obj("""
            {
              "type": "apoli:target_condition",
              "condition": { "type": "apoli:nbt", "nbt": "{Invulnerable:1b}" }
            }
            """), "test:bad_trade"),
            "the bientity form must compile now that the leaf exists");
    }

    // ── partial-match semantics ──────────────────────────────────────────

    @Test
    void aSubsetOfTheTargetsTagMatches() {
        TargetCondition cond = TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:nbt\", \"nbt\": \"{Invulnerable:1b}\" }"), "test:partial");
        assertNotNull(cond);
        assertTrue(cond.test(targetWithNbt("{Invulnerable:1b,Health:20.0f,Age:-24000}"), null),
            "extra keys on the target must not defeat a partial match");
    }

    @Test
    void aDifferingValueDoesNotMatch() {
        TargetCondition cond = TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:nbt\", \"nbt\": \"{Invulnerable:1b}\" }"), "test:value_check");
        assertNotNull(cond);
        assertFalse(cond.test(targetWithNbt("{Invulnerable:0b,Health:20.0f}"), null),
            "the key is present but the value differs — a presence check would wrongly pass here");
    }

    @Test
    void anAbsentKeyDoesNotMatch() {
        TargetCondition cond = TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:nbt\", \"nbt\": \"{Invulnerable:1b}\" }"), "test:absent");
        assertNotNull(cond);
        assertFalse(cond.test(targetWithNbt("{Health:20.0f}"), null));
    }

    @Test
    void nestedCompoundsMatchPartiallyToo() {
        TargetCondition cond = TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:nbt\", \"nbt\": \"{Offers:{Recipes:[{rewardExp:0b}]}}\" }"),
            "test:nested");
        assertNotNull(cond);
        assertTrue(cond.test(targetWithNbt(
            "{Health:20.0f,Offers:{Recipes:[{rewardExp:0b,maxUses:12}]}}"), null));
        assertFalse(cond.test(targetWithNbt(
            "{Health:20.0f,Offers:{Recipes:[{rewardExp:1b,maxUses:12}]}}"), null));
    }

    // ── degenerate inputs ────────────────────────────────────────────────

    @Test
    void anEmptyOrAbsentNbtMatchesEverything() {
        assertTrue(TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:nbt\" }"), "test:no_nbt").test(null, null),
            "no nbt to compare means nothing to reject");
        assertTrue(TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:nbt\", \"nbt\": \"{}\" }"), "test:empty_nbt").test(null, null));
    }

    /** Malformed SNBT is a pack bug; matching everything would hide it. */
    @Test
    void malformedSnbtMatchesNothing() {
        assertFalse(TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:nbt\", \"nbt\": \"{not valid snbt\" }"),
            "test:malformed").test(null, null));
    }
}
