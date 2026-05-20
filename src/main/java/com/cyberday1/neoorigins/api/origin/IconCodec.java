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
public final class IconCodec {
    private IconCodec() {}

    /**
     * Object form: "item" + optional "tag" SNBT string.
     * On encode, extracts known components back into SNBT so the tag
     * survives the JSON round-trip without needing RegistryOps.
     * Decode accepts both "item" and "id" keys for compat with various pack formats.
     */
    private static final Codec<ItemStack> OBJECT_CODEC = Codec.of(
        // Encode: write as {"item": "...", "tag": "..."} for round-trip
        RecordCodecBuilder.<ItemStack>create(inst -> inst.group(
            Identifier.CODEC.fieldOf("item").forGetter(stack ->
                BuiltInRegistries.ITEM.getKey(stack.getItem())),
            Codec.STRING.optionalFieldOf("tag").forGetter(IconCodec::extractSnbt)
        ).apply(inst, (id, tag) -> ItemStack.EMPTY)).comap(stack -> stack),
        // Decode: accept "item" or "id" key
        new com.mojang.serialization.Codec<ItemStack>() {
            @Override
            public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<ItemStack, T>> decode(com.mojang.serialization.DynamicOps<T> ops, T input) {
                var map = ops.getMap(input);
                if (map.error().isPresent()) return com.mojang.serialization.DataResult.error(() -> "Not a map");
                var mapLike = map.result().get();
                // Try "item" first, then "id"
                T itemVal = mapLike.get("item");
                if (itemVal == null) itemVal = mapLike.get("id");
                if (itemVal == null) return com.mojang.serialization.DataResult.error(() -> "No key 'item' or 'id'");
                var itemIdResult = Identifier.CODEC.parse(ops, itemVal);
                if (itemIdResult.error().isPresent()) return com.mojang.serialization.DataResult.error(() -> "Invalid item id");
                Identifier itemId = itemIdResult.result().get();
                var holder = BuiltInRegistries.ITEM.get(itemId);
                if (holder.isEmpty()) {
                    NeoOrigins.LOGGER.warn("[Origin] Icon item not found: {} — using stone", itemId);
                    return com.mojang.serialization.DataResult.success(com.mojang.datafixers.util.Pair.of(safeStack(Items.STONE), ops.empty()));
                }
                Item item = holder.get().value();
                if (item == Items.AIR) {
                    return com.mojang.serialization.DataResult.success(com.mojang.datafixers.util.Pair.of(safeStack(Items.STONE), ops.empty()));
                }
                ItemStack stack = safeStack(item);
                T tagVal = mapLike.get("tag");
                if (tagVal != null && !stack.isEmpty()) {
                    var tagResult = Codec.STRING.parse(ops, tagVal);
                    tagResult.result().ifPresent(snbt -> LegacyTagToComponents.applySnbt(stack, snbt, null));
                }
                return com.mojang.serialization.DataResult.success(com.mojang.datafixers.util.Pair.of(stack, ops.empty()));
            }
            @Override
            public <T> com.mojang.serialization.DataResult<T> encode(ItemStack input, com.mojang.serialization.DynamicOps<T> ops, T prefix) {
                return com.mojang.serialization.DataResult.success(prefix);
            }
        }
    );

    /** Simple string form: just an item id. */
    private static final Codec<ItemStack> STRING_CODEC = Identifier.CODEC.xmap(
        id -> {
            var holder = BuiltInRegistries.ITEM.get(id);
            if (holder.isEmpty()) {
                NeoOrigins.LOGGER.warn("[Origin] Icon item not found: {} — using stone", id);
                return safeStack(Items.STONE);
            }
            Item item = holder.get().value();
            if (item == Items.AIR) return safeStack(Items.STONE);
            return safeStack(item);
        },
        stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())
    );

    /**
     * 26.1 quirk: vanilla's {@code new ItemStack(Item)} constructor eagerly
     * reads {@code holder.value().components()} to seed the stack's component
     * map. During the integrated server's datapack reload at world-load time,
     * items haven't had their components bound yet — {@code Holder.Reference
     * .components()} throws "Components not bound yet" and every origin (and
     * mob origin) fails to parse. The icon is only used by the creator UI;
     * runtime power application + the mob-origin lookup that
     * {@code /neoorigins mob egg} queries never touch it. Returning EMPTY
     * here lets origins load with a placeholder icon — a subsequent
     * {@code /reload} after world load (when items are bound) repopulates
     * the icons properly.
     */
    private static ItemStack safeStack(Item item) {
        try {
            return new ItemStack(item);
        } catch (NullPointerException e) {
            return ItemStack.EMPTY;
        }
    }

    public static final Codec<ItemStack> CODEC = Codec.withAlternative(OBJECT_CODEC, STRING_CODEC);

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
