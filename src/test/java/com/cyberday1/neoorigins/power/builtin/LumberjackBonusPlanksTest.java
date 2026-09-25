package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.PowerLayers;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The Lumberjack's "Efficient Cuts" promises 2 extra planks for any plank craft. It
 * matched one item id, so every wood but oak got nothing. The planks tag is bound
 * from vanilla's own tag file, as a datapack load would.
 */
class LumberjackBonusPlanksTest {

    private static final ResourceLocation LUMBERJACK =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "class_lumberjack");
    /** Stand-in for a modded plank: any item a mod's datapack puts in the tag. */
    private static final Item MODDED_PLANK = Items.STICK;

    @BeforeAll
    static void load() {
        RealDatapack.load();
    }

    @BeforeEach
    void bindPlanks() {
        List<Holder<Item>> members = new ArrayList<>();
        for (Item item : vanillaPlanks()) members.add(item.builtInRegistryHolder());
        members.add(MODDED_PLANK.builtInRegistryHolder());
        BuiltInRegistries.ITEM.bindTags(Map.of(ItemTags.PLANKS, members));
    }

    @AfterEach
    void unbind() {
        BuiltInRegistries.ITEM.resetTags();
    }

    @Test
    void everyVanillaPlankGetsTheBonus() {
        List<Item> planks = vanillaPlanks();
        assertFalse(planks.isEmpty());
        List<String> missed = new ArrayList<>();
        for (Item plank : planks) {
            if (bonusFor(plank) != 2) missed.add(BuiltInRegistries.ITEM.getKey(plank).toString());
        }
        assertEquals(List.of(), missed, "planks that got no bonus");
    }

    @Test
    void aModdedPlankInTheTagGetsTheBonus() {
        assertEquals(2, bonusFor(MODDED_PLANK));
    }

    @Test
    void aNonPlankCraftGetsNothing() {
        assertEquals(0, bonusFor(Items.CRAFTING_TABLE));
    }

    @Test
    void aClasslessPlayerGetsNothing() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        craft(sp, Items.OAK_PLANKS);
        assertEquals(0, sp.getInventory().countItem(Items.OAK_PLANKS));
    }

    /** Extra copies of {@code plank} a fresh Lumberjack receives for one 4-plank craft. */
    private static int bonusFor(Item plank) {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.getData(OriginAttachments.originData()).setOrigin(PowerLayers.CLASS_LAYER, LUMBERJACK);
        ActiveOriginService.applyOriginPowers(sp, PowerLayers.CLASS_LAYER, null, LUMBERJACK);
        craft(sp, plank);
        return sp.getInventory().countItem(plank);
    }

    /** ResultSlot#checkTakeAchievements posts this with the taken stack, not the inventory. */
    private static void craft(ServerPlayer sp, Item output) {
        CraftAmountBonusPower.Handler.onItemCrafted(
            new PlayerEvent.ItemCraftedEvent(sp, new ItemStack(output, 4), new SimpleContainer(4)));
    }

    private static List<Item> vanillaPlanks() {
        InputStream in = LumberjackBonusPlanksTest.class.getClassLoader()
            .getResourceAsStream("data/minecraft/tags/item/planks.json");
        assertNotNull(in, "vanilla planks tag not on the test classpath");
        List<Item> out = new ArrayList<>();
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            for (JsonElement e : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")) {
                out.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.getAsString())));
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return out;
    }
}
