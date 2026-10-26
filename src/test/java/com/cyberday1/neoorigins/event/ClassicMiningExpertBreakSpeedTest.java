package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.BreakSpeedModifierPower;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Classic Mining Expert (x1.45 on pickaxe blocks, with an effective tool) used a
 * {@code using_effective_tool} power condition. That condition is only true mid-swing,
 * so the capability tag the client predicts mining from was almost never published
 * and the boost never showed. The tool check now lives in the break-speed event and
 * travels in the capability tag, so the client applies it too.
 */
class ClassicMiningExpertBreakSpeedTest {

    private static final ResourceLocation POWER =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "classic_miner_mining_expert");

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.load();
    }

    @AfterEach
    void clear() {
        ClientActivePowers.clear();
    }

    @Test
    void shipsWithoutAConditionThatHidesItFromTheClient() {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(POWER);
        assertNotNull(holder, POWER + " did not load");
        assertNull(holder.condition(), "a power condition gates the capability tag the client needs");
        assertEquals(true, ((BreakSpeedModifierPower.Config) holder.config()).effectiveToolOnly());
    }

    @Test
    void clientAppliesTheBoostOnlyWithACorrectTool() {
        assertEquals(1.45f, clientSpeed(true), 1e-5);
        assertEquals(1.0f, clientSpeed(false), 1e-5);
    }

    /** Break speed the client predicts for a pickaxe block, from the power's own capability tag. */
    private static float clientSpeed(boolean correctTool) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(POWER);
        @SuppressWarnings("unchecked")
        var type = (BreakSpeedModifierPower) (Object) holder.type();
        var config = (BreakSpeedModifierPower.Config) holder.config();
        ClientActivePowers.set(Map.of(POWER, true), type.capabilities(config));

        BlockState state = Mockito.mock(BlockState.class);
        Mockito.when(state.is(Mockito.<TagKey<net.minecraft.world.level.block.Block>>any())).thenReturn(true);
        ItemStack held = Mockito.mock(ItemStack.class);
        Mockito.when(held.isCorrectToolForDrops(state)).thenReturn(correctTool);
        Player player = Mockito.mock(Player.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(player.level().isClientSide()).thenReturn(true);
        Mockito.when(player.getMainHandItem()).thenReturn(held);

        var event = new PlayerEvent.BreakSpeed(player, state, 1.0f, BlockPos.ZERO);
        BreakSpeedModifierEvents.onBreakSpeed(event);
        return event.getNewSpeed();
    }
}
