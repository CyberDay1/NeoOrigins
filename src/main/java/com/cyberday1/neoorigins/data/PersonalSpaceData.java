package com.cyberday1.neoorigins.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide ledger of personal-dimension boxes: which grid cell each player
 * owns and the largest size their box has been built at. Keyed by player UUID
 * only, so every {@code personal_dimension} power a player holds opens the same
 * box. Persisted on the overworld's data storage.
 */
public class PersonalSpaceData extends SavedData {

    private static final String DATA_NAME = "neoorigins_personal_space";

    /** A player's box: its grid cell and the size it is currently built at (0 = never built). */
    public record Box(int cell, int builtSize) {}

    private final Map<UUID, Box> boxes = new HashMap<>();
    private int nextCell = 0;

    public static PersonalSpaceData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PersonalSpaceData::new, PersonalSpaceData::load),
            DATA_NAME);
    }

    /** The player's box, allocating the next free grid cell on first use. */
    public Box getOrAllocate(UUID player) {
        Box box = boxes.get(player);
        if (box == null) {
            box = new Box(nextCell++, 0);
            boxes.put(player, box);
            setDirty();
        }
        return box;
    }

    /** Records that the player's box now stands at {@code size}. */
    public void setBuiltSize(UUID player, int size) {
        Box box = getOrAllocate(player);
        if (box.builtSize() != size) {
            boxes.put(player, new Box(box.cell(), size));
            setDirty();
        }
    }

    // ── Serialization ───────────────────────────────────────────────────────

    public static PersonalSpaceData load(CompoundTag tag, HolderLookup.Provider provider) {
        PersonalSpaceData data = new PersonalSpaceData();
        data.nextCell = tag.getInt("next_cell");
        CompoundTag players = tag.getCompound("boxes");
        for (String key : players.getAllKeys()) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            CompoundTag entry = players.getCompound(key);
            int cell = entry.getInt("cell");
            data.boxes.put(id, new Box(cell, entry.getInt("size")));
            data.nextCell = Math.max(data.nextCell, cell + 1);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("next_cell", nextCell);
        CompoundTag players = new CompoundTag();
        boxes.forEach((id, box) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("cell", box.cell());
            entry.putInt("size", box.builtSize());
            players.put(id.toString(), entry);
        });
        tag.put("boxes", players);
        return tag;
    }
}
