package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.mob_origin.MobOrigin;
import com.cyberday1.neoorigins.data.MobOriginDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;

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
 * reworked it to a 4-list record. This file (26.1) uses the 4-list form,
 * mapping the legacy int through the floats slot like
 * {@code LegacyTagToComponents.applyCustomModelData}.
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
    public static Result buildEgg(Identifier originId, Identifier entityTypeId, int count) {
        if (count < 1 || count > 64) return Result.fail("count must be 1–64");
        MobOrigin origin = MobOriginDataManager.INSTANCE.getMobOrigin(originId);
        if (origin == null) return Result.fail("Unknown mob origin: " + originId);

        Identifier typeId = entityTypeId;
        if (typeId == null) {
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
        // 26.1: SpawnEggItem.byId returns Optional<Holder<Item>> — vanilla rewired
        // the per-entity-type association into the Item registry / TypedEntityData
        // components rather than per-class subclasses.
        java.util.Optional<Holder<Item>> eggHolder = SpawnEggItem.byId(entityType);
        if (eggHolder.isEmpty()) return Result.fail("No spawn egg item registered for " + typeId);
        ItemStack stack = new ItemStack(eggHolder.get(), count);

        CompoundTag entityNbt = new CompoundTag();
        ListTag tagsList = new ListTag();
        tagsList.add(StringTag.valueOf(MARKER_PREFIX + originId.toString()));
        entityNbt.put("Tags", tagsList);
        // 26.1: ENTITY_DATA is now TypedEntityData<EntityType<?>> (carries the
        // type alongside the NBT). 1.21.1 just used CustomData.of(nbt) here.
        stack.set(DataComponents.ENTITY_DATA, TypedEntityData.of(entityType, entityNbt));

        Component originName = origin.name().getString().isBlank()
            ? Component.literal(originId.toString())
            : origin.name();
        stack.set(DataComponents.ITEM_NAME, Component.empty()
            .append(originName).append(" Spawn Egg"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Spawns with origin:").withStyle(ChatFormatting.GRAY),
            Component.literal("  " + originId).withStyle(ChatFormatting.DARK_GRAY))));
        // 26.1: CustomModelData is a 4-list record (floats, flags, strings, colors);
        // map the legacy int through the floats slot.
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
            List.of((float) EGG_MODEL_MARKER), List.of(), List.of(), List.of()));

        return Result.ok(stack);
    }

    /** Read the first {@link #MARKER_PREFIX}-prefixed tag off the entity, or
     *  {@code null} if absent. Used by the FinalizeSpawn handler. */
    public static Identifier findMarkerTag(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(MARKER_PREFIX)) {
                return Identifier.tryParse(tag.substring(MARKER_PREFIX.length()));
            }
        }
        return null;
    }

    /** Remove every {@link #MARKER_PREFIX}-prefixed tag from the entity (one
     *  egg = one origin, but be defensive in case multiple were ever set). */
    public static void stripMarkerTag(Entity entity) {
        for (String tag : List.copyOf(entity.entityTags())) {
            if (tag.startsWith(MARKER_PREFIX)) entity.removeTag(tag);
        }
    }
}
