package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped classic-mode data, loaded through the real managers: every
 * classic origin and class has a variant, every power a variant names loads,
 * and swapping the variants in replaces exactly those origins.
 */
class ClassicOriginVariantsTest {

    private static LayerDataManager layers;

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.load();
        layers = RealDatapack.layers();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("neoorigins", path);
    }

    @Test
    void everyClassicOriginAndSharedClassHasAVariant() {
        Set<String> expected = new TreeSet<>(ContentTogglesConfig.CLASSIC_ORIGINS);
        ContentTogglesConfig.CLASSIC_CLASSES.stream()
            .filter(p -> !ContentTogglesConfig.CLASSIC_ONLY.contains(p))
            .forEach(expected::add);
        Set<String> actual = OriginDataManager.INSTANCE.getClassicVariants().keySet().stream()
            .map(ResourceLocation::getPath).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(expected, actual);
    }

    /** The classic-only classes ship as ordinary origins, not variants. */
    @Test
    void classicOnlyClassesLoadAsOrdinaryOrigins() {
        for (String path : ContentTogglesConfig.CLASSIC_ONLY) {
            assertNotNull(OriginDataManager.INSTANCE.getOrigin(id(path)), path + " did not load");
        }
    }

    @Test
    void everyPowerAClassicDefinitionGrantsLoads() {
        List<String> dangling = new ArrayList<>();
        List<Origin> definitions = new ArrayList<>(OriginDataManager.INSTANCE.getClassicVariants().values());
        for (String path : ContentTogglesConfig.CLASSIC_ONLY) definitions.add(OriginDataManager.INSTANCE.getOrigin(id(path)));
        for (Origin origin : definitions) {
            for (ResourceLocation power : origin.powers()) {
                if (PowerDataManager.INSTANCE.getPower(power) == null) dangling.add(origin.id() + " -> " + power);
            }
        }
        assertEquals(List.of(), dangling, "classic powers that did not load");
    }

    /** Evolution tiers are NeoOrigins' own; the original origins never had them. */
    @Test
    void classicVariantsHaveNoEvolutionTiers() {
        OriginDataManager.INSTANCE.getClassicVariants().forEach((id, origin) ->
            assertEquals(List.of(), origin.tierPowers(), id + " carries tier_powers"));
    }

    @Test
    void applyingVariantsSwapsOnlyTheirOrigins() {
        Map<ResourceLocation, Origin> base = OriginDataManager.INSTANCE.getOrigins();
        Map<ResourceLocation, Origin> variants = OriginDataManager.INSTANCE.getClassicVariants();

        assertSame(base, OriginDataManager.applyClassicVariants(base, variants, false));

        Map<ResourceLocation, Origin> classic = OriginDataManager.applyClassicVariants(base, variants, true);
        assertEquals(base.keySet(), classic.keySet(), "classic mode must not add or drop origins");
        for (ResourceLocation key : base.keySet()) {
            if (variants.containsKey(key)) assertSame(variants.get(key), classic.get(key), key.toString());
            else assertSame(base.get(key), classic.get(key), key.toString());
        }
        assertNotSame(base.get(id("avian")), classic.get(id("avian")));
    }

    @Test
    void aVariantWithoutItsBaseOriginIsIgnored() {
        Map<ResourceLocation, Origin> variants = OriginDataManager.INSTANCE.getClassicVariants();
        Origin avian = variants.get(id("avian"));
        Map<ResourceLocation, Origin> out = OriginDataManager.applyClassicVariants(
            Map.of(), Map.of(id("avian"), avian), true);
        assertTrue(out.isEmpty());
    }

    /**
     * What a classic-mode picker offers from the shipped layers, with every toggle
     * on: the ten origins and the fourteen classes, nothing else. Fails when a new
     * built-in is added to a layer without being known to the toggle config.
     */
    @Test
    void classicModeOffersExactlyTheClassicContent() {
        Set<String> offeredOrigins = offered("origin", true);
        Set<String> offeredClasses = offered("class", true);
        assertEquals(new TreeSet<>(ContentTogglesConfig.CLASSIC_ORIGINS), offeredOrigins);
        assertEquals(new TreeSet<>(ContentTogglesConfig.CLASSIC_CLASSES), offeredClasses);
    }

    @Test
    void normalModeOffersEverythingButTheClassicOnlyClasses() {
        Set<String> offeredClasses = offered("class", false);
        assertTrue(offeredClasses.contains("class_paladin"));
        for (String path : ContentTogglesConfig.CLASSIC_ONLY) assertTrue(!offeredClasses.contains(path), path);
        assertEquals(58, offered("origin", false).size());
        assertEquals(20, offeredClasses.size());
    }

    private static Set<String> offered(String layer, boolean classic) {
        return layers.getLayer(id(layer)).getAvailableOriginIds().stream()
            .filter(o -> "neoorigins".equals(o.getNamespace()))
            .map(ResourceLocation::getPath)
            .filter(p -> !ContentTogglesConfig.isBuiltInDisabled(p, classic, x -> false))
            .collect(Collectors.toCollection(TreeSet::new));
    }
}
