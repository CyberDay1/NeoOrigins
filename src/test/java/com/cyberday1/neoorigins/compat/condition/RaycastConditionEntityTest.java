package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code neoorigins:raycast} reads block hits only. An imported condition that
 * asks for entities with {@code "entity": true} would otherwise pass on any
 * block in view, so it never matches, like {@code "block": false}.
 */
class RaycastConditionEntityTest {

    /** A player whose look ray hits a block. */
    private static ServerPlayer lookingAtABlock() {
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(player.getEyePosition()).thenReturn(new Vec3(0.5, 65.6, 0.5));
        when(player.getLookAngle()).thenReturn(new Vec3(0, -1, 0));
        when(player.level()).thenReturn(level);
        when(level.clip(any())).thenReturn(new BlockHitResult(
            new Vec3(0.5, 64.0, 0.5), Direction.UP, new BlockPos(0, 63, 0), false));
        return player;
    }

    private static boolean test(String json) {
        return ConditionParser.parse(JsonParser.parseString(json).getAsJsonObject(), "test")
            .test(lookingAtABlock());
    }

    @Test
    void aBlockHitMatches() {
        assertTrue(test("{\"type\": \"neoorigins:raycast\"}"));
        assertTrue(test("{\"type\": \"neoorigins:raycast\", \"entity\": false}"));
    }

    @Test
    void entityTrueNeverMatches() {
        assertFalse(test("{\"type\": \"neoorigins:raycast\", \"entity\": true}"));
    }
}
