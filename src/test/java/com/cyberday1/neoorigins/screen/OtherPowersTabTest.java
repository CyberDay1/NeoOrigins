package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the info screen lists held powers no origin gave: an API grant joins its layer's tab,
 * the rest go to the Other Powers tab, minus hidden ones.
 */
class OtherPowersTabTest {

    private static final Identifier ORIGIN_LAYER = id("origin");
    private static final Identifier CLASS_LAYER = id("class");
    private static final Identifier ABYSSAL = id("abyssal");
    private static final Identifier BASE = id("abyssal_water_breathing");
    private static final Identifier APEX = id("abyssal_apex_hp");
    private static final Identifier HIDDEN = id("caveborn_copper_tint");

    private static boolean loadedAdmin;
    private static boolean loadedToggles;

    @BeforeAll
    static void load() {
        RealDatapack.load();
        loadedAdmin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
    }

    @AfterAll
    static void unload() {
        if (loadedAdmin) InMemoryConfig.unload(AdminConfig.SPEC);
        if (loadedToggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
    }

    @Test
    void anOriginsOwnPowersAreLeftOut() {
        var origins = Map.of(ORIGIN_LAYER, ABYSSAL);
        assertEquals(List.of(APEX, HIDDEN),
            OriginDetailViewModel.heldOutsideOrigins(origins, List.of(BASE, APEX, HIDDEN), 0, CLASS_LAYER));
    }

    @Test
    void aTierPowerCountsAsTheOriginsOnceReached() {
        var origins = Map.of(ORIGIN_LAYER, ABYSSAL);
        // Tier 3 adds apex_hp and removes water_breathing.
        assertEquals(List.of(BASE, HIDDEN),
            OriginDetailViewModel.heldOutsideOrigins(origins, List.of(BASE, APEX, HIDDEN), 3, CLASS_LAYER),
            "at tier 3 apex_hp is abyssal's and water_breathing no longer is");
    }

    @Test
    void hiddenPowersGetNoRow() {
        var vm = OriginDetailViewModel.computeOtherPowers(List.of(APEX, HIDDEN));
        assertEquals(1, vm.powerNames().size(), "only the shown power gets a row: " + vm.powerNames());
        assertEquals(OriginDetailViewModel.OTHER_POWERS_ID, vm.origin().id());
    }

    @Test
    void nothingToListMeansNoRows() {
        assertTrue(OriginDetailViewModel.computeOtherPowers(List.of(HIDDEN)).powerNames().isEmpty(),
            "the screen adds the tab only when there is a row to show");
    }

    @Test
    void anUntiedGrantJoinsTheMainOriginTab() {
        var placed = OriginDetailViewModel.grantsByLayer(Map.of(APEX, Optional.empty()),
            List.of(APEX, HIDDEN), List.of(ORIGIN_LAYER, CLASS_LAYER), ORIGIN_LAYER);
        assertEquals(Map.of(ORIGIN_LAYER, List.of(APEX)), placed,
            "the API grant joins the main tab; the non-grant is left for Other Powers");
    }

    @Test
    void aTiedGrantJoinsItsLayersTab() {
        var placed = OriginDetailViewModel.grantsByLayer(Map.of(APEX, Optional.of(CLASS_LAYER)),
            List.of(APEX), List.of(ORIGIN_LAYER, CLASS_LAYER), ORIGIN_LAYER);
        assertEquals(Map.of(CLASS_LAYER, List.of(APEX)), placed);
    }

    @Test
    void aGrantOnALayerWithNoTabFallsBackToTheMainTab() {
        var placed = OriginDetailViewModel.grantsByLayer(Map.of(APEX, Optional.of(id("missing"))),
            List.of(APEX), List.of(ORIGIN_LAYER), ORIGIN_LAYER);
        assertEquals(Map.of(ORIGIN_LAYER, List.of(APEX)), placed);
    }

    @Test
    void withNoTabEveryGrantStaysForOtherPowers() {
        assertTrue(OriginDetailViewModel.grantsByLayer(Map.of(APEX, Optional.empty()),
            List.of(APEX), List.of(), null).isEmpty());
    }

    @Test
    void grantedPowersJoinTheOriginsOwnList() {
        int own = OriginDetailViewModel.computeForPlayer(ABYSSAL, false).powerNames().size();
        var vm = OriginDetailViewModel.computeForPlayer(ABYSSAL, false, List.of(APEX, BASE));
        assertEquals(own + 1, vm.powerNames().size(),
            "apex_hp is added once; water_breathing is already abyssal's and is not repeated");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neoorigins", path);
    }
}
