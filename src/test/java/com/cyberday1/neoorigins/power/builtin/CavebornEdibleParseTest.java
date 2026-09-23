package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #117: two reporters on 26.x say the Caveborn cannot eat stone, ore or
 * ingots. The server path is identical across all three lines, so this pins the
 * half that a datapack change could silently break — that all seven shipped
 * edible powers still parse and still carry a non-empty match set.
 *
 * <p>It deliberately does NOT assert a tag MATCH: {@code stack.is(TagKey)} needs
 * a bound tag registry, which only a running server has. A green here means the
 * config survived the codec, not that the item matched in game.
 */
// hub: neoorigins/caveborn-eating-117.md
class CavebornEdibleParseTest {

    private static final List<String> EAT_POWERS = List.of(
        "caveborn_eat_stone", "caveborn_eat_copper", "caveborn_eat_iron",
        "caveborn_eat_gold", "caveborn_eat_diamond", "caveborn_eat_emerald",
        "caveborn_eat_netherite");

    @BeforeAll
    static void loadRealData() {
        RealDatapack.load();
    }

    private static EdibleItemPower.Config config(String path) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(
            Identifier.fromNamespaceAndPath("neoorigins", path));
        assertNotNull(holder, () -> path + " did not load at all");
        return assertInstanceOf(EdibleItemPower.Config.class, holder.config(),
            () -> path + " parsed as the wrong config type");
    }

    @Test
    void everyShippedCavebornEdibleParsesWithAMatchSet() {
        for (String path : EAT_POWERS) {
            EdibleItemPower.Config cfg = config(path);
            assertFalse(cfg.items().isEmpty() && cfg.tags().isEmpty(),
                () -> path + " parsed but matches nothing — it would silently never be edible");
            assertTrue(cfg.nutrition() > 0, () -> path + " would restore no hunger");
        }
    }

    @Test
    void stoneIsEdibleFromTheStoneTagAndIsNotAlwaysEdible() {
        EdibleItemPower.Config cfg = config("caveborn_eat_stone");
        assertEquals(List.of(Identifier.fromNamespaceAndPath("neoorigins", "caveborn_eat_stone")),
            cfg.tags());
        // always_edible false is the documented reason a full-hunger player sees
        // nothing happen — canEat(false) is invulnerable || false || needsFood().
        assertFalse(cfg.alwaysEdible(),
            "if this ever flips, the 'nothing happens when full' explanation for #117 is void");
    }

    @Test
    void ironKeepsItsNuggetAndBlockTiers() {
        EdibleItemPower.Config cfg = config("caveborn_eat_iron");
        assertEquals(2, cfg.tiers().size(), "the nugget and storage-block tiers must both survive the codec");
        // The nugget tier must stay at 1/0.0 — the 9x nugget-crafting exploit
        // returns the moment it inherits the 4-nutrition base.
        EdibleItemPower.Tier nugget = cfg.tiers().get(0);
        assertEquals(1, nugget.nutrition());
        assertEquals(List.of(Identifier.parse("c:nuggets/iron")), nugget.tags());
    }
}
