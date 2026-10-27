package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.power.builtin.PersonalDimensionPower;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Box geometry, spawn rules and config defaults of the personal_dimension power. */
class PersonalSpaceTest {

    private static PersonalDimensionPower.Config decode(String json) {
        return PersonalDimensionPower.Config.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    @Test
    void defaults() {
        var cfg = decode("{}");
        assertEquals(12, cfg.size());
        assertEquals(1200, cfg.cooldownTicks());
        assertEquals(0, cfg.xpLevelsCost());
    }

    @Test
    void sizeOutsideRangeIsRejected() {
        String[] bad = {"{\"size\": 0}", "{\"size\": 65}", "{\"xp_levels_cost\": -1}"};
        for (String json : bad) {
            assertTrue(PersonalDimensionPower.Config.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .error().isPresent(), json);
        }
        assertEquals(64, decode("{\"size\": 64}").size());
        assertEquals(1, decode("{\"size\": 1}").size());
    }

    private static Set<String> shell(int size) {
        Set<String> out = new HashSet<>();
        PersonalSpace.forEachShell(size, (dx, dy, dz) -> out.add(dx + "," + dy + "," + dz));
        return out;
    }

    @Test
    void shellEnclosesExactlyTheInterior() {
        for (int size : new int[] {1, 2, 12, 13, 64}) {
            Set<String> shell = shell(size);
            int outer = size + 2;
            // Full outer cube minus the size^3 interior.
            assertEquals(outer * outer * outer - size * size * size, shell.size(), "size " + size);
            for (int dx = -1; dx <= size; dx++)
                for (int dy = 0; dy <= size + 1; dy++)
                    for (int dz = -1; dz <= size; dz++)
                        assertEquals(!PersonalSpace.inInterior(dx, dy, dz, size), shell.contains(dx + "," + dy + "," + dz));
        }
    }

    @Test
    void growingKeepsTheOldInteriorInsideTheNewOne() {
        for (int from = 1; from < 20; from++) {
            for (int to = from + 1; to <= 21; to++) {
                int oldStart = PersonalSpace.interiorStart(0, from);
                int newStart = PersonalSpace.interiorStart(0, to);
                assertTrue(newStart <= oldStart, from + "->" + to);
                assertTrue(newStart + to >= oldStart + from, from + "->" + to);
            }
        }
    }

    @Test
    void arrivalIsTheFloorCentre() {
        Vec3 even = PersonalSpace.arrivalPoint(0, 12);
        assertEquals(0.0, even.x);
        assertEquals(PersonalSpace.FLOOR_Y + 1, even.y);
        Vec3 odd = PersonalSpace.arrivalPoint(0, 13);
        assertEquals(0.5, odd.x);
        assertEquals(0.5, odd.z);
    }

    @Test
    void cellsAreFarApart() {
        assertEquals(0, PersonalSpace.cellCentreX(0));
        assertEquals(PersonalSpace.SPACING, PersonalSpace.cellCentreX(1));
        assertEquals(0, PersonalSpace.cellCentreX(PersonalSpace.COLUMNS));
        assertEquals(PersonalSpace.SPACING, PersonalSpace.cellCentreZ(PersonalSpace.COLUMNS));
        // The largest box (66 blocks outside) never reaches the next cell.
        assertTrue(PersonalSpace.SPACING > PersonalSpace.MAX_SIZE + 2);
    }

    @Test
    void onlyWorldSpawnsAreDenied() {
        Set<MobSpawnType> denied = Set.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION,
            MobSpawnType.STRUCTURE, MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT, MobSpawnType.EVENT);
        for (MobSpawnType type : MobSpawnType.values()) {
            assertEquals(denied.contains(type), PersonalSpace.deniesSpawn(type), type.name());
        }
        assertFalse(PersonalSpace.deniesSpawn(MobSpawnType.SPAWNER));
        assertFalse(PersonalSpace.deniesSpawn(MobSpawnType.SPAWN_EGG));
    }
}
