package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two shipped biome gates named a biome tag that no registry defines, so each
 * power could never fire: Mushroom Symbiosis asked for {@code minecraft:is_mushroom}
 * (vanilla has none; NeoForge ships {@code c:is_mushroom}), and Soul Dread asked for
 * a tag called {@code minecraft:soul_sand_valley}, which is a biome. The biomes here
 * carry the tags the real game gives them, so the test fails on a gate that names
 * a tag the game never fills.
 */
class BiomeGatedPowerTest {

    private static final BlockPos POS = BlockPos.ZERO;

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.load();
    }

    private static ResourceLocation id(String s) {
        return ResourceLocation.parse(s);
    }

    private static EntityCondition shipped(String path) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(
            ResourceLocation.fromNamespaceAndPath("neoorigins", path));
        assertNotNull(holder, path + " did not load");
        return ((ConditionPassivePower.Config) holder.config()).condition();
    }

    /** A player standing in {@code biome}, which is a member of exactly {@code tags}. */
    @SuppressWarnings("unchecked")
    private static ServerPlayer in(String biome, String... tags) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id(biome));
        Set<TagKey<Biome>> members = new java.util.HashSet<>();
        for (String t : tags) members.add(TagKey.create(Registries.BIOME, id(t)));
        Holder<Biome> holder = mock(Holder.class, inv -> {
            if (inv.getMethod().getName().equals("is") && inv.getArguments().length == 1) {
                Object a = inv.getArgument(0);
                if (a instanceof TagKey<?> t) return members.contains(t);
                if (a instanceof ResourceKey<?> k) return k.equals(key);
            }
            return Mockito.RETURNS_DEFAULTS.answer(inv);
        });
        Level level = mock(Level.class);
        when(level.getBiome(POS)).thenReturn(holder);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(POS);
        return player;
    }

    @Test
    void mushroomSymbiosisFiresInMushroomFields() {
        assertTrue(shipped("sporeling_mushroom_buff").test(
            in("minecraft:mushroom_fields", "c:is_mushroom", "minecraft:is_overworld")));
    }

    @Test
    void mushroomSymbiosisStaysOffInAPlains() {
        assertFalse(shipped("sporeling_mushroom_buff").test(
            in("minecraft:plains", "minecraft:is_overworld")));
    }

    @Test
    void soulDreadFiresInASoulSandValley() {
        assertTrue(shipped("piglin_soul_fire_damage").test(
            in("minecraft:soul_sand_valley", "minecraft:is_nether")));
    }

    @Test
    void soulDreadStaysOffElsewhereInTheNether() {
        assertFalse(shipped("piglin_soul_fire_damage").test(
            in("minecraft:nether_wastes", "minecraft:is_nether")));
    }
}
