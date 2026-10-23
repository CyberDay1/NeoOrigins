package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #136: with Route B powers injected, getAllPowers() built a fresh merged map
 * per call, so the edible-animation cache never hit and Punchy lagged the
 * render thread. The view must be stable until the power set changes.
 */
class AllPowersViewCacheTest {

    private static PowerHolder<?> holder(String path) {
        Identifier id = Identifier.fromNamespaceAndPath("test", path);
        return new PowerHolder<>(id, null, null, null, null);
    }

    @Test
    void sameInstanceUntilThePowerSetChanges() {
        PowerDataManager manager = new PowerDataManager();
        manager.injectExternalPowers(Map.of(Identifier.fromNamespaceAndPath("test", "a"), holder("a")));

        Map<Identifier, PowerHolder<?>> first = manager.getAllPowers();
        assertSame(first, manager.getAllPowers(), "merged view rebuilt with nothing changed");

        manager.injectExternalPowers(Map.of(Identifier.fromNamespaceAndPath("test", "b"), holder("b")));
        Map<Identifier, PowerHolder<?>> second = manager.getAllPowers();
        assertNotSame(first, second, "merged view not rebuilt after a version bump");
        assertTrue(second.containsKey(Identifier.fromNamespaceAndPath("test", "b")));
        assertSame(second, manager.getAllPowers());
    }
}
