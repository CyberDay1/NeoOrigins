package com.cyberday1.neoorigins.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

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

    /** A player's box: its grid cell and the size it is currently built at (0 = never built). */
    public record Box(int cell, int builtSize) {
        static final Codec<Box> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("cell").forGetter(Box::cell),
            Codec.INT.optionalFieldOf("size", 0).forGetter(Box::builtSize)
        ).apply(i, Box::new));
    }

    public static final Codec<PersonalSpaceData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("next_cell", 0).forGetter(d -> d.nextCell),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Box.CODEC).optionalFieldOf("boxes", Map.of()).forGetter(d -> d.boxes)
    ).apply(i, PersonalSpaceData::new));

    public static final SavedDataType<PersonalSpaceData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("neoorigins", "personal_space"), PersonalSpaceData::new, CODEC);

    private final Map<UUID, Box> boxes = new HashMap<>();
    private int nextCell = 0;

    public PersonalSpaceData() {}

    private PersonalSpaceData(int nextCell, Map<UUID, Box> loaded) {
        this.boxes.putAll(loaded);
        int next = nextCell;
        for (Box box : loaded.values()) next = Math.max(next, box.cell() + 1);
        this.nextCell = next;
    }

    public static PersonalSpaceData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
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
}
