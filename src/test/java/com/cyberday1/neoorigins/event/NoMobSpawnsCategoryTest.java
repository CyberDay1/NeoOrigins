package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.power.builtin.NoMobSpawnsNearbyPower;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ward suppresses exactly the categories it lists. The spawn hook used to return
 * early for every non-monster, so `"categories": ["creature"]` suppressed nothing.
 */
class NoMobSpawnsCategoryTest {

    private static NoMobSpawnsNearbyPower.Config ward(String... categories) {
        return new NoMobSpawnsNearbyPower.Config(24, List.of(categories), "", "", false);
    }

    @Test
    void listedCategoriesAreSuppressed() {
        var cfg = ward("creature", "water_ambient");
        assertTrue(WorldPowerEvents.matchesSpawnCategory(cfg, MobCategory.CREATURE));
        assertTrue(WorldPowerEvents.matchesSpawnCategory(cfg, MobCategory.WATER_AMBIENT));
        assertFalse(WorldPowerEvents.matchesSpawnCategory(cfg, MobCategory.MONSTER));
    }

    @Test
    void theDefaultIsMonstersAndAllIsEverything() {
        assertTrue(WorldPowerEvents.matchesSpawnCategory(ward("monster"), MobCategory.MONSTER));
        assertFalse(WorldPowerEvents.matchesSpawnCategory(ward("monster"), MobCategory.CREATURE));
        assertTrue(WorldPowerEvents.matchesSpawnCategory(ward("all"), MobCategory.AMBIENT));
    }
}
