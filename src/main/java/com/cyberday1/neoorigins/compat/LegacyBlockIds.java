package com.cyberday1.neoorigins.compat;

import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Remaps block ids that the original Origins mod registered in code and that
 * legacy Apoli packs therefore reference under the {@code origins:} namespace.
 * NeoOrigins registers equivalents under its own namespace; every compat-layer
 * point that resolves a block id (set_block, block conditions, projectile NBT)
 * should run the id through {@link #remap(String)} first.
 */
public final class LegacyBlockIds {

    private static final Map<String, String> REMAP = Map.of(
        "origins:temporary_cobweb", "neoorigins:temporary_cobweb"
    );

    private LegacyBlockIds() {}

    /** Remaps a legacy block id string; returns the input unchanged if unmapped. */
    public static String remap(String blockId) {
        if (blockId == null) return null;
        return REMAP.getOrDefault(blockId, blockId);
    }

    /**
     * Recursively remaps block ids inside entity NBT (e.g. falling_block
     * {@code BlockState.Name}). Mutates the tag in place and returns it.
     */
    public static CompoundTag remapNbt(CompoundTag tag) {
        if (tag == null) return null;
        for (String key : tag.keySet()) {
            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound) {
                remapNbt(compound);
            } else if (child instanceof ListTag list) {
                remapList(list);
            } else if (child instanceof StringTag str) {
                String remapped = REMAP.get(str.value());
                if (remapped != null) {
                    tag.putString(key, remapped);
                }
            }
        }
        return tag;
    }

    private static void remapList(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            Tag child = list.get(i);
            if (child instanceof CompoundTag compound) {
                remapNbt(compound);
            } else if (child instanceof ListTag nested) {
                remapList(nested);
            } else if (child instanceof StringTag str) {
                String remapped = REMAP.get(str.value());
                if (remapped != null) {
                    list.set(i, StringTag.valueOf(remapped));
                }
            }
        }
    }
}
