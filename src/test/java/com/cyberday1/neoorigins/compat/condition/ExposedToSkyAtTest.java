package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@code exposed_to_sky} with {@code "at": "eyes"} tests the eye block (issue #151).
 * canSeeSky is full sky light, and full sky light only falls straight down, so a
 * standing player's feet block can see the sky only if the block above it does too.
 * The two answers differ when the feet block dims sky light and the eye block does
 * not: wading (feet in water, head in the air), or standing on soul sand or mud,
 * whose 14/16 top puts the feet inside the opaque block. Modelled here as block 64
 * dimmed and block 65 and up open.
 */
class ExposedToSkyAtTest {

    private static ServerPlayer wading() {
        return standingAt(64.0);
    }

    private static ServerPlayer standingAt(double feetY) {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.setPos(0.5, feetY, 0.5);
        ServerLevel level = (ServerLevel) sp.level();
        when(level.canSeeSky(any(BlockPos.class))).thenAnswer(i -> ((BlockPos) i.getArgument(0)).getY() >= 65);
        return sp;
    }

    private static boolean test(ServerPlayer sp, String fields) {
        String json = "{\"type\": \"neoorigins:exposed_to_sky\"" + fields + "}";
        return ConditionParser.parse(JsonParser.parseString(json).getAsJsonObject(), "test").test(sp);
    }

    @Test
    void feetIsTheDefault() {
        ServerPlayer sp = wading();
        assertFalse(test(sp, ""));
        assertFalse(test(sp, ", \"at\": \"feet\""));
    }

    @Test
    void eyesSeeTheSkyWhileWading() {
        assertTrue(test(wading(), ", \"at\": \"eyes\""));
        assertTrue(test(wading(), ", \"at\": \"EYES\""));
    }

    @Test
    void onSoulSandTheFeetAreInsideTheBlock() {
        ServerPlayer sp = standingAt(64.875);
        assertEquals(64, sp.blockPosition().getY());
        assertFalse(test(sp, ""));
        assertTrue(test(sp, ", \"at\": \"eyes\""));
    }

    @Test
    void anUnknownValueReadsFeet() {
        assertFalse(test(wading(), ", \"at\": \"head\""));
        assertFalse(test(wading(), ", \"at\": 1"));
    }

    @Test
    void swimmingDropsTheEyesIntoTheFeetBlock() {
        ServerPlayer sp = wading();
        sp.setPose(Pose.SWIMMING);
        sp.refreshDimensions();
        assertEquals(64, BlockPos.containing(sp.getEyePosition()).getY());
        assertFalse(test(sp, ", \"at\": \"eyes\""));
    }

    @Test
    void sneakingKeepsTheEyesAboveTheWater() {
        ServerPlayer sp = wading();
        sp.setPose(Pose.CROUCHING);
        sp.refreshDimensions();
        assertTrue(sp.getEyePosition().y < 64.0 + 1.62);
        assertTrue(test(sp, ", \"at\": \"eyes\""));
    }

    @Test
    void aHalfScalePlayerHasItsEyesInItsFeetBlock() {
        ServerPlayer sp = wading();
        sp.getAttribute(Attributes.SCALE).setBaseValue(0.5);
        sp.refreshDimensions();
        assertEquals(64, BlockPos.containing(sp.getEyePosition()).getY());
        assertFalse(test(sp, ", \"at\": \"eyes\""));
    }
}
