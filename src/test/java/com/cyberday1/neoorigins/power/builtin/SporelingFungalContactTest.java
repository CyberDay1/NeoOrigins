package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fungal Contact heals "while standing on or inside mushroom blocks or mycelium".
 * It shipped with in_block's filter under {@code block}, a key parseInBlock never
 * reads, so the condition was always true and the Sporeling healed everywhere.
 * Evaluated against the shipped JSON through the real loader.
 */
class SporelingFungalContactTest {

    private static final BlockPos POS = BlockPos.ZERO;

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.load();
    }

    private static EntityCondition shipped() {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(
            Identifier.fromNamespaceAndPath("neoorigins", "sporeling_fungal_contact"));
        assertNotNull(holder, "sporeling_fungal_contact did not load");
        return ((ConditionPassivePower.Config) holder.config()).condition();
    }

    private static BlockState stateOf(Block block) {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        return state;
    }

    private static ServerPlayer at(Block feet, Block below) {
        BlockState here = stateOf(feet);
        BlockState under = stateOf(below);
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(POS)).thenReturn(here);
        when(level.getBlockState(POS.below())).thenReturn(under);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(POS);
        when(player.onGround()).thenReturn(true);
        return player;
    }

    @Test
    void doesNotHealOnOrdinaryGround() {
        assertFalse(shipped().test(at(Blocks.AIR, Blocks.STONE)),
            "Fungal Contact fired standing on stone: its block filter is not being read");
    }

    @Test
    void healsStandingOnEachFungalBlock() {
        for (Block b : new Block[] {Blocks.MYCELIUM, Blocks.RED_MUSHROOM_BLOCK,
                                    Blocks.BROWN_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM}) {
            assertTrue(shipped().test(at(Blocks.AIR, b)), "no heal standing on " + b);
        }
    }

    @Test
    void healsWithFeetInsideAFungalBlock() {
        assertTrue(shipped().test(at(Blocks.BROWN_MUSHROOM_BLOCK, Blocks.STONE)));
    }
}
