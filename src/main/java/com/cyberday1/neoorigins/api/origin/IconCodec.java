package com.cyberday1.neoorigins.api.origin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.LegacyTagToComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.Optional;

/**
 * Codec for origin icons that accepts multiple input forms and preserves
 * data components (like CustomModelData) through the server→client JSON
 * round-trip used by SyncOriginRegistryPayload.
 *
 * <p>Input forms:
 * <ul>
 *   <li>A string item id: {@code "minecraft:ink_sac"}</li>
 *   <li>An object with item + legacy SNBT tag: {@code {"item": "minecraft:ink_sac", "tag": "{CustomModelData:1}"}}</li>
 * </ul>
 *
 * <p>Encoding writes item id + SNBT tag (if components are present), which
 * doesn't require RegistryOps and survives plain JsonOps round-trips.
 */
final class IconCodec {
    private IconCodec() {}

    /**
     * Object form: "item" + optional "tag" SNBT string.
     * On encode, extracts known components back into SNBT so the tag
     * survives the JSON round-trip without needing RegistryOps.
     */
    private static final Codec<ItemStack> OBJECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.fieldOf("item").forGetter(stack ->
            BuiltInRegistries.ITEM.getKey(stack.getItem())),
        Codec.STRING.optionalFieldOf("tag").forGetter(IconCodec::extractSnbt)
    ).apply(inst, (itemId, tag) -> {
        var holder = BuiltInRegistries.ITEM.get(itemId);
        if (holder.isEmpty()) {
            NeoOrigins.LOGGER.warn("[Origin] Icon item not found: {}", itemId);
            return ItemStack.EMPTY;
        }
        Item item = holder.get().value();
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        tag.ifPresent(snbt -> LegacyTagToComponents.applySnbt(stack, snbt, null));
        return stack;
    }));

    /** Simple string form: just an item id. */
    private static final Codec<ItemStack> STRING_CODEC = Identifier.CODEC.xmap(
        id -> {
            var holder = BuiltInRegistries.ITEM.get(id);
            if (holder.isEmpty()) {
                NeoOrigins.LOGGER.warn("[Origin] Icon item not found: {} — using stone", id);
                return new ItemStack(Items.STONE);
            }
            Item item = holder.get().value();
            if (item == Items.AIR) return new ItemStack(Items.STONE);
            return new ItemStack(item);
        },
        stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())
    );

    static final Codec<ItemStack> CODEC = Codec.withAlternative(OBJECT_CODEC, STRING_CODEC);

    /**
     * Extracts known data components from a stack back into SNBT for
     * round-trip encoding. Returns empty if the stack has no extra components.
     */
    private static Optional<String> extractSnbt(ItemStack stack) {
        CompoundTag tag = new CompoundTag();

        // 26.1: CustomModelData is a 4-list record (floats, flags, strings, colors).
        // Extract the first float value as the legacy int for SNBT round-trip.
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null && !cmd.floats().isEmpty()) {
            tag.putInt("CustomModelData", (int) cmd.floats().getFirst().floatValue());
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag cd = customData.copyTag();
            for (String key : cd.keySet()) {
                var v = cd.get(key);
                if (v != null) tag.put(key, v);
            }
        }

        return tag.isEmpty() ? Optional.empty() : Optional.of(tag.toString());
    }
}
