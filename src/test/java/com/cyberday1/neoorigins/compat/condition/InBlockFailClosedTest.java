package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The {@code in_block} family fails CLOSED on a block_condition it cannot
 * compile.
 *
 * <p>It used to fail open. {@code compileInBlockLeaf} returned {@code null} for
 * an unrecognised type and logged "treating as match-none", but the callers
 * turned that {@code null} into {@code alwaysTrue()} — so the power fired
 * unconditionally, and because a node's {@code inverted} flag is only applied to
 * a non-null base, the negated twin of the same node fired too. Both halves of a
 * wet/dry pair were permanently on. The live instance was the downloaded pack
 * Mycelium Construct's {@code hal:wet}, which gates on {@code origins:fluid} —
 * a type this parser did not implement.
 *
 * <p>Every assertion here evaluates the compiled condition against a player
 * rather than only checking that a parse succeeded: a parse-only test passes on
 * a parser that recognises the shape and then answers true for everything, which
 * is the exact bug being fixed.
 */
class InBlockFailClosedTest {

    @BeforeAll
    static void bootstrap() {
        // Real BLOCK/FLUID registries so id matching resolves against vanilla
        // rather than against a stub that could agree with a broken lookup.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockPos POS = BlockPos.ZERO;

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static BlockState stateOf(Block block) {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        return state;
    }

    private static FluidState fluidStateOf(Fluid fluid) {
        FluidState fluidState = mock(FluidState.class);
        when(fluidState.getType()).thenReturn(fluid);
        when(fluidState.isEmpty()).thenReturn(fluid == Fluids.EMPTY);
        when(fluidState.isSource()).thenReturn(fluid == Fluids.WATER || fluid == Fluids.LAVA);
        when(fluidState.is(FluidTags.WATER))
            .thenReturn(fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER);
        return fluidState;
    }

    /**
     * A player standing at {@link #POS} where the given block and fluid are,
     * on ground, with dry stone underfoot. The block below is stubbed
     * separately so an on_block test cannot accidentally pass by reading the
     * block at head height.
     */
    private static ServerPlayer standingIn(Block block, Fluid fluid) {
        return standingIn(block, fluid, Blocks.STONE, Fluids.EMPTY);
    }

    private static ServerPlayer standingIn(Block block, Fluid fluid, Block below, Fluid belowFluid) {
        // Built before the when(...) calls: stubbing a fresh mock inside an
        // unfinished when(...) leaves Mockito mid-stub and fails the whole class.
        BlockState here = stateOf(block);
        FluidState hereFluid = fluidStateOf(fluid);
        BlockState under = stateOf(below);
        FluidState underFluid = fluidStateOf(belowFluid);

        // ServerPlayer.level() narrows to ServerLevel on 26.x, so the mock has to
        // be the narrower type for the stub to typecheck; the parser only needs
        // it to BE a Level, which ServerLevel is.
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(POS)).thenReturn(here);
        when(level.getFluidState(POS)).thenReturn(hereFluid);
        when(level.getBlockState(POS.below())).thenReturn(under);
        when(level.getFluidState(POS.below())).thenReturn(underFluid);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(POS);
        when(player.onGround()).thenReturn(true);
        return player;
    }

    /** A player at {@link #POS} where the effective local brightness is {@code light}. */
    private static ServerPlayer standingInLight(int light) {
        ServerPlayer player = standingIn(Blocks.AIR, Fluids.EMPTY);
        when(player.level().getMaxLocalRawBrightness(POS)).thenReturn(light);
        return player;
    }

    private static ServerPlayer standingUnderSky(boolean canSeeSky) {
        ServerPlayer player = standingIn(Blocks.AIR, Fluids.EMPTY);
        when(player.level().canSeeSky(POS)).thenReturn(canSeeSky);
        return player;
    }

    private static ServerPlayer standingInBlocking(boolean blocksMotion) {
        ServerPlayer player = standingIn(Blocks.AIR, Fluids.EMPTY);
        when(player.level().getBlockState(POS).blocksMotion()).thenReturn(blocksMotion);
        return player;
    }

    /** Waterlogged slab: the block is not water, the fluid is. */
    private static ServerPlayer standingInAWaterloggedSlab() {
        return standingIn(Blocks.OAK_SLAB, Fluids.WATER);
    }

    private static ServerPlayer standingInDryStone() {
        return standingIn(Blocks.STONE, Fluids.EMPTY);
    }

    // ── the fail-open regression ─────────────────────────────────────────

    /**
     * The defect itself. An unrecognised block_condition type must match
     * nothing; it used to match everything.
     */
    @Test
    void anUnknownBlockConditionTypeMatchesNothing() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:definitely_not_a_verb" } }
            """), "unknown-leaf");
        assertFalse(cond.test(standingInDryStone()),
            "an unimplemented block_condition type must fail closed, not fire unconditionally");
    }

    /**
     * The half that made the bug loud in practice: {@code inverted} is applied
     * only to a compiled base, so an unknown type slipped past the negation too
     * and the condition and its own inverse were simultaneously true. Mycelium
     * Construct's wet/dry pair is exactly this shape.
     */
    @Test
    void aConditionAndItsInverseCannotBothMatch() {
        String plain = """
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:definitely_not_a_verb" } }
            """;
        String negated = """
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:definitely_not_a_verb", "inverted": true } }
            """;
        ServerPlayer player = standingInDryStone();
        boolean a = ConditionParser.parse(obj(plain), "wet").test(player);
        boolean b = ConditionParser.parse(obj(negated), "dry").test(player);
        assertFalse(a && b,
            "a node and its inverted twin were both true — the wet/dry pair was permanently on");
        assertFalse(a, "the plain node must fail closed");
        assertFalse(b, "the inverted node must fail closed too, not become always-true");
    }

    /**
     * in_block_anywhere counts matching positions, so "fail closed" cannot be
     * expressed as a zero count: {@code <= 2} against zero reads true, which is
     * the same fail-open trap wearing a comparison.
     */
    @Test
    void inBlockAnywhereFailsClosedRatherThanCountingZero() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block_anywhere",
              "comparison": "<=", "compare_to": 2,
              "block_condition": { "type": "origins:definitely_not_a_verb" } }
            """), "anywhere-unknown");
        assertFalse(cond.test(standingInDryStone()),
            "an at-most comparison must not be satisfied by an uncompilable condition");
    }

    /**
     * Dropping an uncompilable branch silently rewrites the author's condition,
     * and for {@code and} it rewrites it BROADER — the fail-open direction.
     */
    @Test
    void anAndNodeWithOneUncompilableBranchMatchesNothing() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:and", "conditions": [
                  { "type": "origins:block", "block": "minecraft:oak_slab" },
                  { "type": "origins:definitely_not_a_verb" } ] } }
            """), "and-with-unknown");
        assertFalse(cond.test(standingInAWaterloggedSlab()),
            "the surviving branch matches this block, so a dropped branch would read true");
    }

    /**
     * Non-vacuity for all of the above: propagating null must not have turned
     * every combinator into a fail-closed stub.
     */
    @Test
    void aFullyCompilableAndNodeStillDiscriminates() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:and", "conditions": [
                  { "type": "origins:block", "block": "minecraft:oak_slab" } ] } }
            """), "and-ok");
        assertTrue(cond.test(standingInAWaterloggedSlab()), "oak slab must match");
        assertFalse(cond.test(standingInDryStone()), "stone must not match");
    }

    /** An ABSENT block_condition is authored intent for "any block". */
    @Test
    void anAbsentBlockConditionStillMeansAnyBlock() {
        EntityCondition cond = ConditionParser.parse(
            obj("{ \"type\": \"origins:in_block\" }"), "bare-in-block");
        assertTrue(cond.test(standingInDryStone()),
            "no block_condition means no restriction — that is not the same as an unreadable one");
    }

    // ── the fluid verb ───────────────────────────────────────────────────

    /**
     * The reason {@code fluid} is not a synonym for {@code block}: a waterlogged
     * slab is an oak slab as a block and water as a fluid, and "am I wet" means
     * the fluid.
     */
    @Test
    void fluidTestsTheFluidNotTheBlock() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:fluid",
                "fluid_condition": { "type": "origins:in_tag", "tag": "minecraft:water" } } }
            """), "hal:wet");
        assertTrue(cond.test(standingInAWaterloggedSlab()),
            "a waterlogged slab holds water even though the block is not water");
        assertFalse(cond.test(standingInDryStone()));
    }

    /** Mycelium Construct's dry half — now the genuine complement of the wet half. */
    @Test
    void invertedFluidIsTheComplementOfTheWetHalf() {
        String wet = """
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:fluid",
                "fluid_condition": { "type": "origins:in_tag", "tag": "minecraft:water" } } }
            """;
        String dry = """
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:fluid", "inverted": true,
                "fluid_condition": { "type": "origins:in_tag", "tag": "minecraft:water" } } }
            """;
        ServerPlayer wetPlayer = standingInAWaterloggedSlab();
        ServerPlayer dryPlayer = standingInDryStone();
        assertTrue(ConditionParser.parse(obj(wet), "wet").test(wetPlayer));
        assertFalse(ConditionParser.parse(obj(dry), "dry").test(wetPlayer));
        assertFalse(ConditionParser.parse(obj(wet), "wet").test(dryPlayer));
        assertTrue(ConditionParser.parse(obj(dry), "dry").test(dryPlayer));
    }

    /** Fluid id matching resolves against the real FLUID registry. */
    @Test
    void fluidMatchesByIdAsWellAsByTag() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:fluid",
                "fluid_condition": { "type": "origins:fluid", "fluid": "minecraft:water" } } }
            """), "fluid-by-id");
        assertTrue(cond.test(standingInAWaterloggedSlab()));
        assertFalse(cond.test(standingIn(Blocks.STONE, Fluids.LAVA)));
    }

    /** An unreadable fluid_condition fails the whole block condition closed. */
    @Test
    void anUnknownFluidConditionTypeMatchesNothing() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:fluid",
                "fluid_condition": { "type": "origins:not_a_fluid_verb" } } }
            """), "fluid-unknown");
        assertFalse(cond.test(standingInAWaterloggedSlab()),
            "an unreadable fluid_condition must not leak back out as always-true");
    }

    // ── the three verbs the loud failure exposed ─────────────────────────

    /**
     * Origins++ zero-aizawa/heliophobia and four siblings gate on
     * {@code origins:light_level} as a BLOCK condition. It was unimplemented, so
     * heliophobia's slowness+blindness fired in the dark as readily as in the
     * sun. Making the fallback fail closed would have swung it to "never fires",
     * which is the opposite regression — so the verb is implemented.
     */
    @Test
    void lightLevelComparesTheLightAtThePosition() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:light_level",
                                   "comparison": "<=", "compare_to": 7 } }
            """), "shadow:invisibility");
        assertTrue(cond.test(standingInLight(4)), "light 4 satisfies <= 7");
        assertFalse(cond.test(standingInLight(12)), "light 12 does not satisfy <= 7");
    }

    /** Origins++ rat/nocturnal_eyes. */
    @Test
    void exposedToSkyReadsSkyVisibility() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:exposed_to_sky" } }
            """), "rat:nocturnal_eyes");
        assertTrue(cond.test(standingUnderSky(true)));
        assertFalse(cond.test(standingUnderSky(false)));
    }

    /**
     * Origins++ giant/slam. Deliberately not "is not air": a torch and tall
     * grass are both non-air and both non-blocking.
     */
    @Test
    void movementBlockingIsNotMerelyNonAir() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:in_block",
              "block_condition": { "type": "origins:movement_blocking" } }
            """), "giant:slam");
        assertTrue(cond.test(standingInBlocking(true)));
        assertFalse(cond.test(standingInBlocking(false)));
    }

    /** The editor must be able to author everything the runtime now accepts. */
    @Test
    void theNewVerbsAllHaveBlockConditionDescriptors() {
        var ids = BuiltinBlockConditions.descriptors().keySet().stream()
            .map(id -> id.getPath()).toList();
        for (String verb : new String[] {"fluid", "light_level", "exposed_to_sky", "movement_blocking"}) {
            assertTrue(ids.contains(verb), verb + " must appear in block_condition.schema");
        }
    }

    // ── neoorigins:block, routed through the shared compiler ─────────────

    /**
     * The narrow helper behind {@code neoorigins:block} handled only
     * block/id/tag and returned always-true for everything else, silently, while
     * advertising the whole block_condition schema to the editors.
     */
    @Test
    void blockConditionHonoursANestedCombinator() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "neoorigins:block",
              "block_condition": { "type": "origins:or", "conditions": [
                  { "type": "origins:block", "block": "minecraft:oak_slab" },
                  { "type": "origins:block", "block": "minecraft:glass" } ] } }
            """), "block-or");
        assertTrue(cond.test(standingInAWaterloggedSlab()), "oak slab is one of the alternatives");
        assertFalse(cond.test(standingInDryStone()), "stone is neither — this used to read true");
    }

    @Test
    void blockConditionFailsClosedOnAnUnknownNestedType() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "neoorigins:block",
              "block_condition": { "type": "origins:definitely_not_a_verb" } }
            """), "block-unknown");
        assertFalse(cond.test(standingInDryStone()));
    }

    /**
     * Deliberately preserved: a wrapper with no discriminating field still means
     * "any block", which is what the field docs promise and what packs rely on.
     */
    @Test
    void aBareBlockWrapperStillMeansAnyBlock() {
        assertTrue(ConditionParser.parse(
            obj("{ \"type\": \"neoorigins:block\" }"), "block-bare").test(standingInDryStone()));
    }

    // ── on_block, the third instance of the same fallback ────────────────

    /**
     * The old fallback dropped the block filter and passed through as bare
     * {@code onGround()}, so "standing on X" became "standing on anything".
     * No pack in the corpus reaches it today, but the field docs advertise the
     * whole grammar, so an authored {@code block_state} node landed here.
     */
    @Test
    void onBlockDiscardingItsFilterIsNotAcceptableFallback() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:on_block",
              "block_condition": { "type": "origins:definitely_not_a_verb" } }
            """), "on-block-unknown");
        assertFalse(cond.test(standingIn(Blocks.AIR, Fluids.EMPTY, Blocks.STONE, Fluids.EMPTY)),
            "an unreadable block_condition must not degrade to plain onGround()");
    }

    /** The extended grammar now reaches on_block, evaluated one block down. */
    @Test
    void onBlockReachesTheExtendedGrammarAtTheBlockBelow() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:on_block",
              "block_condition": { "type": "origins:fluid",
                "fluid_condition": { "type": "origins:in_tag", "tag": "minecraft:water" } } }
            """), "on-block-fluid");
        assertTrue(cond.test(standingIn(Blocks.AIR, Fluids.EMPTY, Blocks.OAK_SLAB, Fluids.WATER)),
            "the waterlogged slab underfoot must match");
        assertFalse(cond.test(standingIn(Blocks.AIR, Fluids.WATER, Blocks.STONE, Fluids.EMPTY)),
            "water at head height is not water underfoot");
    }

    /** The arms that every corpus authoring actually uses are untouched. */
    @Test
    void onBlockStillMatchesPlainBlockAndTagArms() {
        EntityCondition byId = ConditionParser.parse(obj("""
            { "type": "origins:on_block",
              "block_condition": { "type": "origins:block", "block": "minecraft:stone" } }
            """), "on-block-id");
        assertTrue(byId.test(standingIn(Blocks.AIR, Fluids.EMPTY, Blocks.STONE, Fluids.EMPTY)));
        assertFalse(byId.test(standingIn(Blocks.AIR, Fluids.EMPTY, Blocks.OAK_SLAB, Fluids.EMPTY)));
    }

    @Test
    void blockConditionStillMatchesAPlainIdInline() {
        EntityCondition cond = ConditionParser.parse(
            obj("{ \"type\": \"neoorigins:block\", \"block\": \"minecraft:stone\" }"), "block-inline");
        assertTrue(cond.test(standingInDryStone()));
        assertFalse(cond.test(standingInAWaterloggedSlab()));
    }
}
