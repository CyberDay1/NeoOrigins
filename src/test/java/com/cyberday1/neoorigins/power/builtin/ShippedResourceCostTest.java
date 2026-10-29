package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A power's Codec drops keys it does not declare without a word, so a cost can be
 * authored and never charged. Fifteen shipped powers were in that state: four
 * {@code active_teleport} blinks (one of whose lang string promised "Costs 20
 * energy"), plus {@code summon_minion}, {@code shadow_orb}, {@code active_phase},
 * {@code ground_slam}, {@code active_place_block} and {@code tidal_wave}.
 *
 * <p>This walks the shipped JSON rather than a hand-written list, so the next
 * power that authors a cost its type cannot read fails here instead of shipping.
 */
// hub: neoorigins/teleport-resource-cost.md
class ShippedResourceCostTest {

    private static final String DATA_ROOT = "data/neoorigins/origins";

    /**
     * Types that charge the cost themselves, once per unit of work rather than
     * once per activation, and so deliberately keep the base-class accessors at
     * their defaults — see the comments on their Config records.
     */
    private static final Set<String> SELF_CHARGING =
        Set.of("neoorigins:summon_minion", "neoorigins:tame_mob");

    /** Shipped power id to the cost its JSON authors. */
    private static Map<Identifier, Cost> authored;

    private record Cost(String type, String resource, int amount) {}

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.load();
        authored = readAuthoredCosts();
    }

    @Test
    void theShippedDataAuthorsCostsAtAll() {
        assertFalse(authored.isEmpty(), "no shipped power authors a resource_cost — did the walk break?");
    }

    /**
     * The gate. Every authored cost has to come back out of the loaded config
     * through the accessor the engine consults, so a key the Codec silently drops
     * cannot pass.
     */
    @Test
    void everyAuthoredResourceCostIsActuallyRead() {
        List<String> unread = new ArrayList<>();
        for (var entry : authored.entrySet()) {
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(entry.getKey());
            assertNotNull(holder, entry.getKey() + " is not loaded");
            Cost cost = entry.getValue();

            if (SELF_CHARGING.contains(cost.type())) continue;
            if (!(holder.config() instanceof AbstractActivePower.Config config)) {
                // A non-active type authoring a per-use cost is its own problem;
                // the toggles read the keys through PowerUpkeep instead.
                continue;
            }
            if (!cost.resource().equals(config.resourceCost())
                    || cost.amount() != config.resourceCostAmount()) {
                unread.add("%s (%s) authors %s x%d, config reports %s x%d".formatted(
                    entry.getKey(), cost.type(), cost.resource(), cost.amount(),
                    config.resourceCost(), config.resourceCostAmount()));
            }
        }
        assertEquals(List.of(), unread, "shipped resource costs that nothing charges: " + unread);
    }

    /**
     * {@code summon_minion} charges its own cost inside {@code execute}, so the
     * base class must see nothing: its Config keeps the cost under
     * {@code hungerCostLegacy} / {@code summonResource*}, exactly as
     * {@code tame_mob} does. While its component was named {@code hungerCost} the
     * interface accessor returned it and {@code onActivated} charged it a second
     * time — a default summon cost 8 food points, not 4.
     */
    @Test
    void aSelfChargingTypeHidesItsCostFromTheBaseClass() {
        for (var entry : authored.entrySet()) {
            if (!SELF_CHARGING.contains(entry.getValue().type())) continue;
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(entry.getKey());
            assertNotNull(holder, entry.getKey() + " is not loaded");
            assertTrue(holder.config() instanceof AbstractActivePower.Config,
                entry.getKey() + " is not an active power");

            AbstractActivePower.Config config = (AbstractActivePower.Config) holder.config();
            assertEquals(0, config.hungerCost(),
                entry.getKey() + " would be charged hunger twice: once by itself, once by the base class");
            assertEquals("", config.resourceCost(),
                entry.getKey() + " would be charged its resource twice");
            assertEquals(0, config.resourceCostAmount(), entry.getKey() + " resource amount leaked");
        }
    }

    /** Each named resource has to exist, or the cost can never be paid and the key is dead. */
    @Test
    void everyAuthoredCostNamesABarThatExists() {
        List<String> missing = new ArrayList<>();
        for (var entry : authored.entrySet()) {
            Cost cost = entry.getValue();
            PowerHolder<?> bar =
                PowerDataManager.INSTANCE.getPower(Identifier.parse(cost.resource()));
            if (bar == null || !(bar.config() instanceof ResourcePower.Config)) {
                missing.add(entry.getKey() + " → " + cost.resource());
            }
        }
        assertEquals(List.of(), missing, "costs naming a resource no power defines: " + missing);
    }

    /**
     * The seven types the keys were wired into ship with no resource cost on
     * purpose: hunger stays the cost for the mod's own powers, and the wiring is
     * there for pack authors. Fifteen built-in powers used to author a cost that
     * nothing charged; rather than start charging it, the keys came off the data
     * and out of the descriptions. Pinned so a re-add is a deliberate edit, since
     * one would retune shipped origins on update.
     */
    @Test
    void theBuiltInPowersDoNotSpendResources() {
        Set<String> wiredLate = Set.of(
            "neoorigins:active_teleport", "neoorigins:active_phase",
            "neoorigins:active_place_block", "neoorigins:ground_slam",
            "neoorigins:tidal_wave", "neoorigins:shadow_orb",
            "neoorigins:summon_minion");

        List<String> spending = authored.entrySet().stream()
            .filter(e -> wiredLate.contains(e.getValue().type()))
            .map(e -> "%s (%s) spends %s x%d".formatted(
                e.getKey(), e.getValue().type(), e.getValue().resource(), e.getValue().amount()))
            .sorted()
            .toList();

        assertEquals(List.of(), spending,
            "a built-in power started spending a resource: " + spending);
    }

    /**
     * {@code summon_minion} charges its own cost inside {@code execute}, so the
     * base class must see nothing — and that has to hold whether or not any
     * shipped power authors a cost, which is why this reads the type rather than
     * the data. While its component was named {@code hungerCost} the interface
     * accessor returned it and {@code onActivated} charged it a second time: a
     * default summon cost 8 food points, not 4.
     */
    @Test
    void summonMinionHidesItsCostFromTheBaseClass() {
        List<String> leaking = new ArrayList<>();
        for (var entry : PowerDataManager.INSTANCE.getAllPowers().entrySet()) {
            if (!(entry.getValue().config() instanceof SummonMinionPower.Config config)) continue;
            if (config.hungerCost() != 0 || !config.resourceCost().isEmpty()
                    || config.resourceCostAmount() != 0) {
                leaking.add(entry.getKey().toString());
            }
        }
        assertTrue(PowerDataManager.INSTANCE.getAllPowers().values().stream()
                .anyMatch(h -> h.config() instanceof SummonMinionPower.Config),
            "no summon_minion power loaded, so this proves nothing");
        assertEquals(List.of(), leaking,
            "summon_minion powers the base class would charge a second time: " + leaking);
    }

    // ── reading the shipped JSON ─────────────────────────────────────────────

    private static Map<Identifier, Cost> readAuthoredCosts() {
        Map<Identifier, Cost> out = new HashMap<>();
        Path dir = locate("powers");
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                JsonObject obj;
                try {
                    obj = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (!obj.has("resource_cost") || !obj.has("type")) return;
                String resource = obj.get("resource_cost").getAsString();
                int amount = obj.has("resource_cost_amount")
                    ? obj.get("resource_cost_amount").getAsInt() : 0;
                if (resource.isEmpty() || amount <= 0) return;

                String name = p.getFileName().toString();
                out.put(Identifier.fromNamespaceAndPath("neoorigins",
                        name.substring(0, name.length() - ".json".length())),
                    new Cost(obj.get("type").getAsString(), resource, amount));
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static Path locate(String kind) {
        Path dir = Path.of("").toAbsolutePath();
        for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/resources").resolve(DATA_ROOT).resolve(kind);
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("could not locate " + DATA_ROOT + "/" + kind);
    }
}
