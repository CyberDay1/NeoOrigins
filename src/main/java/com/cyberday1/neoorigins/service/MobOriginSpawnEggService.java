package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.mob_origin.MobOrigin;
import com.cyberday1.neoorigins.data.MobOriginDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Builds a vanilla spawn-egg {@link ItemStack} pre-tagged with a mob-origin
 * marker so the spawned entity gets the origin applied at
 * {@link com.cyberday1.neoorigins.event.MobOriginEventHandler} time.
 *
 * <p><b>Mechanism:</b> the stack carries {@link DataComponents#ENTITY_DATA}
 * with a single scoreboard tag {@code MARKER_PREFIX + originId}. Vanilla
 * spawn-egg + spawner code applies this NBT to the freshly-spawned entity, so
 * the tag is on the mob when {@code FinalizeSpawnEvent} fires. The event
 * handler then bypasses the usual {@code SpawnRules} roll, applies the
 * tagged origin, and strips the marker tag. This works through vanilla mob
 * spawners "for free" because the spawner reads {@code ENTITY_DATA} off the
 * egg slot into its own spawn data.
 *
 * <p>Naming: stack gets {@code ITEM_NAME} = "&lt;Origin Name&gt; Spawn Egg" and
 * a lore line. A {@code CUSTOM_MODEL_DATA} marker is set so a future resource
 * pack can replace the texture; no texture is shipped today.
 *
 * <p>Cross-version note: 1.21.1 uses {@code new CustomModelData(int)}; 26.1
 * reworked it to a 4-list record. This file (2.1) uses the int form.
 */
public final class MobOriginSpawnEggService {

    /** Prefix for the entity scoreboard tag that carries the origin id. */
    public static final String MARKER_PREFIX = "neoorigins_mob_origin/";
    /** Stable custom-model-data marker so a resource pack can re-skin these eggs. */
    public static final int EGG_MODEL_MARKER = 1;

    private MobOriginSpawnEggService() {}

    /** Result of an egg build — either an item stack or a human error. */
    public record Result(ItemStack stack, String error) {
        public boolean ok() { return error == null; }
        public static Result ok(ItemStack s) { return new Result(s, null); }
        public static Result fail(String msg) { return new Result(ItemStack.EMPTY, msg); }
    }

    /**
     * Build a spawn egg for {@code originId} targeting {@code entityTypeId}.
     * If {@code entityTypeId} is {@code null} the origin's single
     * {@code target.entity_type} is used; otherwise the override must be matched
     * by the origin's {@link com.cyberday1.neoorigins.api.mob_origin.EntityTargetSpec}.
     */
    public static Result buildEgg(ResourceLocation originId, ResourceLocation entityTypeId, int count) {
        if (count < 1 || count > 64) return Result.fail("count must be 1–64");
        MobOrigin origin = MobOriginDataManager.INSTANCE.getMobOrigin(originId);
        if (origin == null) return Result.fail("Unknown mob origin: " + originId);

        ResourceLocation typeId = entityTypeId;
        if (typeId == null) {
            // Fall back to origin's single entity_type when present.
            if (origin.target().entityType().isPresent()) {
                typeId = origin.target().entityType().get();
            } else {
                return Result.fail("Mob origin " + originId + " targets a tag or list; "
                    + "specify an entity_type override.");
            }
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (entityType == null) return Result.fail("Unknown entity type: " + typeId);
        if (!origin.target().matches(entityType)) {
            return Result.fail("Entity type " + typeId
                + " is not matched by mob origin " + originId + "'s target spec.");
        }
        SpawnEggItem egg = SpawnEggItem.byId(entityType);
        if (egg == null) return Result.fail("No spawn egg item registered for " + typeId);

        ItemStack stack = new ItemStack(egg, count);

        CompoundTag entityNbt = new CompoundTag();
        // Vanilla's ENTITY_DATA component codec on 1.21.1 is CustomData.CODEC_WITH_ID
        // — encoding (e.g. when saving a stack into an inventory at world-save time)
        // throws "Missing id for entity in ..." if the NBT has no `id` field.
        // The id must match the egg's entity type. Without this the server crashes
        // the next time the player's inventory persists. (On 26.1 TypedEntityData.of
        // strips a redundant id; this line is also harmless there.)
        entityNbt.putString("id", typeId.toString());
        ListTag tagsList = new ListTag();
        tagsList.add(StringTag.valueOf(MARKER_PREFIX + originId.toString()));
        entityNbt.put("Tags", tagsList);
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(entityNbt));

        Component originName = origin.name().getString().isBlank()
            ? Component.literal(originId.toString())
            : origin.name();
        stack.set(DataComponents.ITEM_NAME, Component.empty()
            .append(originName).append(" Spawn Egg"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Spawns with origin:").withStyle(ChatFormatting.GRAY),
            Component.literal("  " + originId).withStyle(ChatFormatting.DARK_GRAY))));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(EGG_MODEL_MARKER));

        return Result.ok(stack);
    }

    /** Read the first {@link #MARKER_PREFIX}-prefixed tag off the entity, or
     *  {@code null} if absent. Used by the FinalizeSpawn handler. */
    public static ResourceLocation findMarkerTag(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(MARKER_PREFIX)) {
                return ResourceLocation.tryParse(tag.substring(MARKER_PREFIX.length()));
            }
        }
        return null;
    }

    /** Read the marker from a spawn-egg stack's ENTITY_DATA NBT. Used by the
     *  in-hand right-click handler — vanilla gates the NBT-to-entity copy
     *  behind {@code canUseGameMasterBlocks()} (creative+op), so the survival
     *  path has to read the marker off the stack directly and spawn the
     *  entity itself with the tag pre-applied. */
    public static ResourceLocation markerFromNbt(net.minecraft.nbt.CompoundTag nbt) {
        if (nbt == null || !nbt.contains("Tags", net.minecraft.nbt.Tag.TAG_LIST)) return null;
        net.minecraft.nbt.ListTag tags = nbt.getList("Tags", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.getString(i);
            if (tag.startsWith(MARKER_PREFIX)) {
                return ResourceLocation.tryParse(tag.substring(MARKER_PREFIX.length()));
            }
        }
        return null;
    }

    /** Remove every {@link #MARKER_PREFIX}-prefixed tag from the entity (one
     *  egg = one origin, but be defensive in case multiple were ever set). */
    public static void stripMarkerTag(Entity entity) {
        // entity.getTags() returns a live view in vanilla; copy to avoid CME.
        for (String tag : List.copyOf(entity.getTags())) {
            if (tag.startsWith(MARKER_PREFIX)) entity.removeTag(tag);
        }
    }
}
