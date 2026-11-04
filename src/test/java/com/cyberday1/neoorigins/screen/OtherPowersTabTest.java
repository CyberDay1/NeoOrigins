package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the info screen's Other Powers tab lists: held powers no origin gave, minus hidden ones. */
class OtherPowersTabTest {

    private static final ResourceLocation ORIGIN_LAYER = id("origin");
    private static final ResourceLocation CLASS_LAYER = id("class");
    private static final ResourceLocation ABYSSAL = id("abyssal");
    private static final ResourceLocation BASE = id("abyssal_water_breathing");
    private static final ResourceLocation APEX = id("abyssal_apex_hp");
    private static final ResourceLocation HIDDEN = id("caveborn_copper_tint");

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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("neoorigins", path);
    }
}
