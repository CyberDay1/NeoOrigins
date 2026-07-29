package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the three block-condition leaves added to close the residual compat
 * gap: {@code block_state} (Origins++ Giant's sleep facing check and Kelperet's
 * {@code waterlogged: true} swim penalty), {@code height} (Fairytale's sea-level
 * affinity), and {@code adjacent} (Origins++ Glacier's cold_nap).
 *
 * <p>Two independent compilers consume these nodes and deliberately fail in
 * opposite directions, so both entry points are exercised: the positional
 * {@code in_block} / {@code near_block} chain in {@link ConditionParser}, and
 * the shared {@code block_state} property matcher the action_on_event compiler
 * reuses. Matching itself is asserted against mocked states carrying real
 * {@link Property} instances, which need no Minecraft bootstrap.
 */
class BlockStateConditionTest {

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** A BlockState whose state definition exposes exactly one property. */
    private static <T extends Comparable<T>> BlockState stateWith(Property<T> prop, T value) {
        BlockState state = mock(BlockState.class);
        Block block = mock(Block.class);
        @SuppressWarnings("unchecked")
        StateDefinition<Block, BlockState> def = mock(StateDefinition.class);
        when(state.getBlock()).thenReturn(block);
        when(block.getStateDefinition()).thenReturn(def);
        // doReturn: getProperty is declared Property<?>, whose capture cannot
        // absorb a Property<T> through the type-checked when(...) form.
        doReturn(prop).when(def).getProperty(prop.getName());
        when(state.getValue(prop)).thenReturn(value);
        return state;
    }

    /** A BlockState carrying no properties at all. */
    private static BlockState statelessBlock() {
        BlockState state = mock(BlockState.class);
        Block block = mock(Block.class);
        @SuppressWarnings("unchecked")
        StateDefinition<Block, BlockState> def = mock(StateDefinition.class);
        when(state.getBlock()).thenReturn(block);
        when(block.getStateDefinition()).thenReturn(def);
        doReturn(null).when(def).getProperty(any());
        return state;
    }

    // ── editor descriptors ───────────────────────────────────────────────

    @Test
    void allThreeLeavesHaveBlockConditionDescriptors() {
        var ids = BuiltinBlockConditions.descriptors().keySet().stream()
            .map(id -> id.getPath()).toList();
        assertTrue(ids.contains("block_state"), "block_state must appear in block_condition.schema");
        assertTrue(ids.contains("height"), "height must appear in block_condition.schema");
        assertTrue(ids.contains("adjacent"), "adjacent must appear in block_condition.schema");
    }

    // ── block_state: shape reading ───────────────────────────────────────

    @Test
    void aNodeWithoutAPropertyIsUnreadable() {
        assertNull(ConditionParser.compileBlockStateProperty(obj("{ \"value\": true }")),
            "block_state names no property — the caller decides how to fail");
        assertNull(ConditionParser.compileBlockStateProperty(null));
    }

    /** Origins++ Kelperet: "property": "waterlogged", "value": true. */
    @Test
    void booleanValueMatchesUnquoted() {
        Predicate<BlockState> pred = ConditionParser.compileBlockStateProperty(
            obj("{ \"property\": \"waterlogged\", \"value\": true }"));
        assertNotNull(pred);
        BooleanProperty waterlogged = BooleanProperty.create("waterlogged");
        assertTrue(pred.test(stateWith(waterlogged, Boolean.TRUE)));
        assertFalse(pred.test(stateWith(waterlogged, Boolean.FALSE)));
    }

    /** Origins++ Giant sleep: "property": "facing", "enum": ["south", …]. */
    @Test
    void enumListMatchesAnyListedName() {
        Predicate<BlockState> pred = ConditionParser.compileBlockStateProperty(
            obj("{ \"property\": \"facing\", \"enum\": [\"south\", \"west\"] }"));
        assertNotNull(pred);
        EnumProperty<Direction> facing = EnumProperty.create("facing", Direction.class);
        assertTrue(pred.test(stateWith(facing, Direction.SOUTH)));
        assertTrue(pred.test(stateWith(facing, Direction.WEST)));
        assertFalse(pred.test(stateWith(facing, Direction.NORTH)));
    }

    /** Numeric properties may be compared instead of matched. */
    @Test
    void comparisonOverridesValueMatchingForNumericProperties() {
        Predicate<BlockState> pred = ConditionParser.compileBlockStateProperty(
            obj("{ \"property\": \"level\", \"comparison\": \">=\", \"compare_to\": 4 }"));
        assertNotNull(pred);
        IntegerProperty level = IntegerProperty.create("level", 0, 15);
        assertTrue(pred.test(stateWith(level, 4)));
        assertTrue(pred.test(stateWith(level, 15)));
        assertFalse(pred.test(stateWith(level, 3)));
    }

    /** A non-numeric value under a comparison must not throw. */
    @Test
    void comparisonAgainstANonNumericValueIsFalseNotAnException() {
        Predicate<BlockState> pred = ConditionParser.compileBlockStateProperty(
            obj("{ \"property\": \"facing\", \"comparison\": \">\", \"compare_to\": 1 }"));
        assertNotNull(pred);
        assertFalse(pred.test(stateWith(EnumProperty.create("facing", Direction.class), Direction.UP)));
    }

    /** Apoli's own behaviour: a block lacking the property never matches. */
    @Test
    void aBlockWithoutThePropertyNeverMatches() {
        Predicate<BlockState> pred = ConditionParser.compileBlockStateProperty(
            obj("{ \"property\": \"waterlogged\", \"value\": true }"));
        assertNotNull(pred);
        assertFalse(pred.test(statelessBlock()));
    }

    /** A property named but no accepted value and no comparison matches nothing. */
    @Test
    void aPropertyWithNoValueAndNoComparisonMatchesNothing() {
        Predicate<BlockState> pred = ConditionParser.compileBlockStateProperty(
            obj("{ \"property\": \"waterlogged\" }"));
        assertNotNull(pred);
        assertFalse(pred.test(stateWith(BooleanProperty.create("waterlogged"), Boolean.TRUE)));
    }

    // ── the positional in_block / near_block chain ───────────────────────

    /**
     * Fairytale height_weakness: block_in_radius over a bare height node. Before
     * the leaf existed the compiler returned null, near_block saw no selectors
     * at all and failed the whole condition closed.
     */
    @Test
    void nearBlockOverAHeightNodeCompiles() {
        assertNotSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(obj("""
            {
              "type": "origins:block_in_radius",
              "block_condition": { "type": "origins:height", "comparison": "<=", "compare_to": 63 },
              "radius": 4
            }
            """), "test:height_weakness"),
            "a height block_condition must supply a selector, not fail closed");
    }

    @Test
    void nearBlockOverABlockStateNodeCompiles() {
        assertNotSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(obj("""
            {
              "type": "origins:block_in_radius",
              "block_condition": { "type": "origins:block_state", "property": "waterlogged", "value": true }
            }
            """), "test:sink_in_water"));
    }

    /** Origins++ Glacier cold_nap: at most two snow/ice neighbours. */
    @Test
    void nearBlockOverAnAdjacentNodeCompiles() {
        assertNotSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(obj("""
            {
              "type": "origins:block_in_radius",
              "block_condition": {
                "type": "origins:adjacent",
                "adjacent_condition": { "type": "origins:in_tag", "tag": "minecraft:ice" },
                "comparison": "<=",
                "compare_to": 2
              }
            }
            """), "test:cold_nap"));
    }

    /** The offset wrapper composes with the new leaves. */
    @Test
    void nearBlockOverAnOffsetWrappedBlockStateCompiles() {
        assertNotSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(obj("""
            {
              "type": "origins:block_in_radius",
              "block_condition": {
                "type": "apoli:offset",
                "y": -1,
                "condition": { "type": "apoli:block_state", "property": "level", "comparison": ">=", "compare_to": 1 }
              }
            }
            """), "test:offset_block_state"));
    }

    // ── fail-closed paths of the new leaves ──────────────────────────────

    /**
     * adjacent without an adjacent_condition has nothing to count, and offset
     * without a nested condition has nothing to evaluate: both compile to null,
     * which leaves near_block with no selector at all and fails it closed rather
     * than turning a "few icy neighbours" gate into "any block anywhere".
     */
    @Test
    void structurallyEmptyLeavesFailNearBlockClosed() {
        assertSameFailClosed("""
            { "type": "origins:block_in_radius",
              "block_condition": { "type": "origins:adjacent", "comparison": "<=", "compare_to": 2 } }
            """, "test:adjacent_no_inner");
        assertSameFailClosed("""
            { "type": "origins:block_in_radius",
              "block_condition": { "type": "apoli:offset", "y": -1 } }
            """, "test:offset_no_inner");
        assertSameFailClosed("""
            { "type": "origins:block_in_radius",
              "block_condition": { "type": "origins:block_state", "value": true } }
            """, "test:block_state_no_property");
        assertSameFailClosed("""
            { "type": "origins:block_in_radius",
              "block_condition": { "type": "origins:no_such_block_leaf" } }
            """, "test:unknown_leaf");
    }

    /** An adjacent node whose inner condition itself cannot compile fails too. */
    @Test
    void adjacentWithAnUncompilableInnerFailsClosed() {
        assertSameFailClosed("""
            { "type": "origins:block_in_radius",
              "block_condition": {
                "type": "origins:adjacent",
                "adjacent_condition": { "type": "origins:no_such_block_leaf" }
              } }
            """, "test:adjacent_bad_inner");
    }

    private static void assertSameFailClosed(String json, String contextId) {
        assertSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(obj(json), contextId),
            contextId + " must fail closed");
    }

    // ── height comparison semantics ──────────────────────────────────────

    /**
     * height compares the tested BLOCK's Y, defaults to ">=", and 63 is the sea
     * level every pack writes it against.
     */
    @Test
    void heightComparisonsBehaveAgainstSeaLevel() {
        assertTrue(ComparisonType.fromString("<=").test(63, 63));
        assertTrue(ComparisonType.fromString("<=").test(11, 63));
        assertFalse(ComparisonType.fromString("<=").test(64, 63));
        assertTrue(ComparisonType.fromString(">=").test(0, 0),
            "the default comparison/compare_to pair (>= 0) accepts the overworld surface");
    }

    /** adjacent defaults to ">= 1" — "any matching neighbour" — over 0..6. */
    @Test
    void adjacentDefaultCountIsAnyNeighbourOverSixFaces() {
        ComparisonType ge = ComparisonType.fromString(">=");
        assertFalse(ge.test(0, 1));
        assertTrue(ge.test(1, 1));
        assertTrue(ge.test(6, 1));
        assertEquals(6, Direction.values().length,
            "adjacent counts exactly the six face neighbours, so 6 is the count ceiling");
    }
}
