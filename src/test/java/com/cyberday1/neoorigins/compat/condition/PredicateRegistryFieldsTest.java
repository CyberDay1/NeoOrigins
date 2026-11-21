package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code neoorigins:predicate} fields that name registry entries (block, fluid, item
 * and entity type lists, biome sets) parse through RegistryOps over the server's
 * registries on first evaluation. Plain JsonOps rejected them with "Can't access
 * registry", so these all failed closed. The player stands with feet in (0, 64, 0) on
 * soul sand, with water in its feet block and a plains biome only one block up.
 */
class PredicateRegistryFieldsTest {

    private static final BlockPos FEET = new BlockPos(0, 64, 0);
    private static final BlockPos ABOVE = FEET.above();
    private static HolderLookup.Provider vanilla;

    @BeforeAll
    static void registries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // A stack cannot be built while its item's components are unbound; these
        // tests read nothing but the item, so an empty map is enough.
        for (Item item : new Item[] { Items.STICK, Items.LEATHER_HELMET }) {
            if (!item.builtInRegistryHolder().areComponentsBound()) {
                item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            }
        }
        vanilla = VanillaRegistries.createWorldLookup();
    }

    private static ServerPlayer player() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.setPos(0.5, 64.0, 0.5);
        ServerLevel level = (ServerLevel) sp.level();
        RegistryAccess access = mock(RegistryAccess.class);
        when(access.lookup(any())).thenAnswer(i -> vanilla.lookup(i.getArgument(0)));
        when(level.registryAccess()).thenReturn(access);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(i ->
            FEET.equals(i.getArgument(0)) ? Blocks.SOUL_SAND.defaultBlockState() : Blocks.STONE.defaultBlockState());
        when(level.getFluidState(any(BlockPos.class))).thenAnswer(i ->
            FEET.equals(i.getArgument(0)) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState());
        var biomes = vanilla.lookupOrThrow(Registries.BIOME);
        Holder<Biome> plains = biomes.getOrThrow(Biomes.PLAINS);
        Holder<Biome> desert = biomes.getOrThrow(Biomes.DESERT);
        doAnswer(i -> ABOVE.equals(i.getArgument(0)) ? plains : desert).when(level).getBiome(any(BlockPos.class));
        return sp;
    }

    private static boolean test(ServerPlayer sp, String type, String predicate, String extra) {
        String json = "{\"type\": \"neoorigins:predicate\", \"predicate_type\": \"" + type
            + "\", \"predicate\": " + predicate + extra + "}";
        return ConditionParser.parse(JsonParser.parseString(json).getAsJsonObject(), "test").test(sp);
    }

    private static boolean test(String type, String predicate) {
        return test(player(), type, predicate, "");
    }

    @Test
    void blockStateBlocksList() {
        assertTrue(test("block_state", "{\"blocks\": [\"minecraft:soul_sand\", \"minecraft:mud\"]}"));
        assertFalse(test("block_state", "{\"blocks\": [\"minecraft:mud\"]}"));
    }

    @Test
    void fluidStateFluidsList() {
        assertTrue(test("fluid_state", "{\"fluids\": [\"minecraft:water\"]}"));
        assertFalse(test("fluid_state", "{\"fluids\": [\"minecraft:lava\"]}"));
    }

    @Test
    void locationBiomesFollowTheOffset() {
        String plains = "{\"biomes\": [\"minecraft:plains\"]}";
        assertFalse(test(player(), "location", plains, ""));
        assertTrue(test(player(), "location", plains, ", \"offsetY\": 1"));
    }

    @Test
    void blocksListFollowsTheOffset() {
        String stone = "{\"blocks\": [\"minecraft:stone\"]}";
        assertFalse(test(player(), "block_state", stone, ""));
        assertTrue(test(player(), "block_state", stone, ", \"offsetY\": 1"));
    }

    /** 26.2 keys entity predicates by sub-predicate id: the type is "entity_type", not "type". */
    @Test
    void entityPropertiesTypeAndEquipment() {
        assertTrue(test("entity_properties", "{\"entity_type\": \"minecraft:player\"}"));
        assertFalse(test("entity_properties", "{\"entity_type\": \"minecraft:zombie\"}"));
        ServerPlayer sp = player();
        String helmet = "{\"equipment\": {\"head\": {\"items\": [\"minecraft:leather_helmet\"]}}}";
        assertFalse(test(sp, "entity_properties", helmet, ""));
        sp.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        assertTrue(test(sp, "entity_properties", helmet, ""));
    }

    @Test
    void itemItemsList() {
        ServerPlayer sp = player();
        String stick = "{\"items\": [\"minecraft:stick\"]}";
        assertFalse(test(sp, "item", stick, ""));
        sp.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
        assertTrue(test(sp, "item", stick, ""));
    }

    @Test
    void anUnknownEntryFailsClosedEveryTime() {
        var cond = ConditionParser.parse(JsonParser.parseString("{\"type\": \"neoorigins:predicate\", "
            + "\"predicate_type\": \"block_state\", \"predicate\": {\"blocks\": [\"minecraft:not_a_block\"]}}")
            .getAsJsonObject(), "test");
        ServerPlayer sp = player();
        assertFalse(cond.test(sp));
        assertFalse(cond.test(sp), "the failure is cached, not retried into a match");
    }
}
