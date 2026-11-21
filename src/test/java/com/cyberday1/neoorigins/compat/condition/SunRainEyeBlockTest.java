package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * exposed_to_sun, in_rain and thundering follow vanilla's own blocks: the eye block
 * for sun (Mob.isSunBurnTick) and feet or top of the bounding box for rain
 * (Entity.isInRain). Block 64 dims sky light (soul sand, or the water a player wades
 * in); 65 and up are open unless {@code roofed}. Rain reaches what the sky does.
 */
class SunRainEyeBlockTest {

    private MockedStatic<GameplayConfig> config;

    @BeforeEach
    void helmetsProtectWithoutWear() {
        config = mockStatic(GameplayConfig.class);
        config.when(GameplayConfig::sunHelmetProtection).thenReturn(true);
        config.when(GameplayConfig::sunHelmetDuraDamageChance).thenReturn(0f);
    }

    @AfterEach
    void close() {
        config.close();
    }

    private static ServerPlayer at(double feetY, boolean roofed, boolean raining, boolean thunder) {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.setPos(0.5, feetY, 0.5);
        ServerLevel level = (ServerLevel) sp.level();
        when(level.getDayTime()).thenReturn(1000L);
        when(level.isRaining()).thenReturn(raining);
        when(level.isThundering()).thenReturn(thunder);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.canSeeSky(any(BlockPos.class))).thenAnswer(i ->
            !roofed && ((BlockPos) i.getArgument(0)).getY() >= 65);
        when(level.isRainingAt(any(BlockPos.class))).thenAnswer(i ->
            raining && !roofed && ((BlockPos) i.getArgument(0)).getY() >= 65);
        return sp;
    }

    /** On soul sand: the 14/16 top puts the feet inside the opaque block. */
    private static ServerPlayer onSoulSand(boolean roofed, boolean raining) {
        ServerPlayer sp = at(64.875, roofed, raining, false);
        assertEquals(64, sp.blockPosition().getY());
        return sp;
    }

    /** Feet in a water block, head in the air; vanilla's fluid pass sets this flag each tick. */
    private static ServerPlayer wading(boolean roofed, boolean raining) {
        ServerPlayer sp = at(64.0, roofed, raining, false);
        setTouchingWater(sp);
        return sp;
    }

    private static void setTouchingWater(ServerPlayer sp) {
        try {
            Field f = Entity.class.getDeclaredField("wasTouchingWater");
            f.setAccessible(true);
            f.setBoolean(sp, true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean test(ServerPlayer sp, String type) {
        return ConditionParser.parse(JsonParser.parseString("{\"type\": \"" + type + "\"}").getAsJsonObject(), "test")
            .test(sp);
    }

    @Test
    void sunBurnsOnSoulSandUnderOpenSky() {
        assertTrue(test(onSoulSand(false, false), "neoorigins:exposed_to_sun"));
        assertTrue(test(onSoulSand(false, false), "origins:exposed_to_sun"), "the compat route is the same check");
        assertTrue(ConditionParser.isExposedToSun(onSoulSand(false, false)), "entity_group burns_in_sunlight shares it");
    }

    @Test
    void aRoofStillShades() {
        assertFalse(test(onSoulSand(true, false), "neoorigins:exposed_to_sun"));
        assertFalse(test(at(65.0, true, false, false), "neoorigins:exposed_to_sun"));
        assertTrue(test(at(65.0, false, false, false), "neoorigins:exposed_to_sun"), "plain ground, open sky");
    }

    @Test
    void aHelmetStillProtectsOnSoulSand() {
        ServerPlayer sp = onSoulSand(false, false);
        sp.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        assertFalse(test(sp, "neoorigins:exposed_to_sun"));
    }

    @Test
    void waterAndPowderSnowStopTheBurnAsInVanilla() {
        assertFalse(test(wading(false, false), "neoorigins:exposed_to_sun"));
        ServerPlayer snow = at(64.0, false, false, false);
        snow.isInPowderSnow = true;
        assertFalse(test(snow, "neoorigins:exposed_to_sun"));
        // Same dim feet block but dry (a cobweb, say): the eyes are in the sun.
        assertTrue(test(at(64.0, false, false, false), "neoorigins:exposed_to_sun"));
    }

    @Test
    void rainFallsOnAPlayerOnSoulSandOrWading() {
        assertTrue(test(onSoulSand(false, true), "neoorigins:in_rain"));
        assertTrue(test(wading(false, true), "neoorigins:in_rain"));
        assertTrue(test(wading(false, true), "origins:in_rain"), "the compat route is the same check");
        assertFalse(test(onSoulSand(true, true), "neoorigins:in_rain"), "not under a roof");
        assertFalse(test(onSoulSand(false, false), "neoorigins:in_rain"), "not in clear weather");
    }

    @Test
    void thunderingUsesTheSameRainRule() {
        assertTrue(test(at(64.875, false, true, true), "neoorigins:thundering"));
        assertFalse(test(at(64.875, true, true, true), "neoorigins:thundering"));
        assertFalse(test(at(64.875, false, true, false), "neoorigins:thundering"));
    }
}
