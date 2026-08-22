package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.config.GameplayConfig;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code neoorigins:umbrellas} item tag as the mod-independent route
 * to umbrella shielding, for both weather-damage conditions.
 *
 * <p><b>Why this proves the tag path specifically.</b> The FML-bootstrapped
 * harness loads no other mods, so Vampires Need Umbrellas is absent and the
 * {@code VNU_LOADED} shortcut is false. On top of that every stack used here is
 * a {@code minecraft:} item, which the VNU namespace check could never accept
 * even if the mod were present. The only way {@code isHoldingUmbrella} can
 * return true below is the tag — which is exactly the regression this guards:
 * before the change the helper began with an unconditional
 * {@code if (!VNU_LOADED) return false;}, so on any setup without VNU installed
 * no umbrella of any kind was ever seen.
 *
 * <p>Tags are unbound in a bare bootstrap (no server, no datapack load), so each
 * test binds the tag explicitly through the registry's own
 * {@code prepareTagReload}/{@code apply} path, the same one datapack loading uses, and {@link #unbindTags()} restores the
 * unbound state afterwards so no other test observes the mutation.
 */
class UmbrellaTagTest {

    private static final TagKey<Item> UMBRELLAS = TagKey.create(Registries.ITEM,
        Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "umbrellas"));

    /** Stand-in for a modded umbrella: a vanilla item put into the tag by "datapack". */
    private static final Item TAGGED_UMBRELLA = Items.STICK;
    /** Anything not in the tag must keep burning/getting rained on. */
    private static final Item NOT_AN_UMBRELLA = Items.STONE;

    private static void applyTags(Map<TagKey<Item>, List<Holder<Item>>> tags) {
        BuiltInRegistries.ITEM
            .prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags))
            .apply();
    }

    private static void bindUmbrellaTag() {
        Holder<Item> holder = TAGGED_UMBRELLA.builtInRegistryHolder();
        applyTags(Map.of(UMBRELLAS, List.of(holder)));
    }

    @AfterEach
    void unbindTags() {
        applyTags(Map.of());
    }

    /** A player on a clear noon day with open sky — burning unless something shades them. */
    private static ServerPlayer sunnyPlayer(ItemStack mainHand, ItemStack offHand) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDefaultClockTime()).thenReturn(1000L);
        when(level.canSeeSky(BlockPos.ZERO)).thenReturn(true);
        when(level.isRaining()).thenReturn(false);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        when(player.getMainHandItem()).thenReturn(mainHand);
        when(player.getOffhandItem()).thenReturn(offHand);
        return player;
    }

    /** A player standing in rain under open sky. */
    private static ServerPlayer rainedOnPlayer(ItemStack mainHand, ItemStack offHand) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.isRainingAt(BlockPos.ZERO)).thenReturn(true);
        when(level.canSeeSky(BlockPos.ZERO)).thenReturn(true);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        when(player.getMainHandItem()).thenReturn(mainHand);
        when(player.getOffhandItem()).thenReturn(offHand);
        return player;
    }

    private static EntityCondition inRain() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "neoorigins:in_rain");
        return ConditionParser.parse(json, "test:umbrella_in_rain");
    }

    // ---- the helper itself ----------------------------------------------

    @Test
    void aTaggedItemInTheMainHandIsAnUmbrella() {
        bindUmbrellaTag();
        ServerPlayer player = sunnyPlayer(
            new ItemStack(TAGGED_UMBRELLA), ItemStack.EMPTY);
        assertTrue(ConditionParser.neoorigins$isHoldingUmbrella(player),
            "an item in neoorigins:umbrellas must count as an umbrella in the main hand");
    }

    @Test
    void aTaggedItemInTheOffHandIsAnUmbrella() {
        bindUmbrellaTag();
        ServerPlayer player = sunnyPlayer(
            ItemStack.EMPTY, new ItemStack(TAGGED_UMBRELLA));
        assertTrue(ConditionParser.neoorigins$isHoldingUmbrella(player),
            "an item in neoorigins:umbrellas must count as an umbrella in the off hand");
    }

    /**
     * The negative that keeps this from being "everything is an umbrella": the
     * same item is NOT an umbrella while the tag is unbound, and an untagged item
     * is never one.
     */
    @Test
    void anUntaggedItemIsNotAnUmbrella() {
        bindUmbrellaTag();
        ServerPlayer player = sunnyPlayer(
            new ItemStack(NOT_AN_UMBRELLA), ItemStack.EMPTY);
        assertFalse(ConditionParser.neoorigins$isHoldingUmbrella(player),
            "an item outside neoorigins:umbrellas must not shield anything");
    }

    @Test
    void nothingIsAnUmbrellaWhileTheTagIsEmpty() {
        ServerPlayer player = sunnyPlayer(
            new ItemStack(TAGGED_UMBRELLA), ItemStack.EMPTY);
        assertFalse(ConditionParser.neoorigins$isHoldingUmbrella(player),
            "with no tag entries bound, the same stack must not be treated as an umbrella");
    }

    // ---- exposed_to_sun --------------------------------------------------

    @Test
    void aTaggedUmbrellaBlocksSunExposure() {
        bindUmbrellaTag();
        ServerPlayer player = sunnyPlayer(
            new ItemStack(TAGGED_UMBRELLA), ItemStack.EMPTY);
        assertFalse(ConditionParser.isExposedToSun(player),
            "holding a neoorigins:umbrellas item must stop sun damage without VNU installed");
    }

    /**
     * The control for the case above: identical player, untagged item, helmet
     * protection off — they burn. Without this, "blocked" could just mean the
     * mocked level never reported sun exposure in the first place.
     */
    @Test
    void theSamePlayerBurnsWithoutAnUmbrella() {
        bindUmbrellaTag();
        ServerPlayer player = sunnyPlayer(
            new ItemStack(NOT_AN_UMBRELLA), ItemStack.EMPTY);
        try (MockedStatic<GameplayConfig> config = mockStatic(GameplayConfig.class)) {
            config.when(GameplayConfig::sunHelmetProtection).thenReturn(false);
            assertTrue(ConditionParser.isExposedToSun(player),
                "the umbrella, not the mocked level, is what makes the case above false");
        }
    }

    // ---- in_rain ---------------------------------------------------------

    @Test
    void aTaggedUmbrellaBlocksInRain() {
        bindUmbrellaTag();
        ServerPlayer player = rainedOnPlayer(
            new ItemStack(TAGGED_UMBRELLA), ItemStack.EMPTY);
        assertFalse(inRain().test(player),
            "holding a neoorigins:umbrellas item must stop in_rain without VNU installed");
    }

    /** Control for the case above: same rain, untagged item, still soaked. */
    @Test
    void theSamePlayerIsStillRainedOnWithoutAnUmbrella() {
        bindUmbrellaTag();
        ServerPlayer player = rainedOnPlayer(
            new ItemStack(NOT_AN_UMBRELLA), ItemStack.EMPTY);
        assertTrue(inRain().test(player),
            "the umbrella, not the mocked weather, is what makes the case above false");
    }
}
