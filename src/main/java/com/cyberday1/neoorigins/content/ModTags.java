package com.cyberday1.neoorigins.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags the mod's own behaviour reads. */
public final class ModTags {

    /**
     * What counts as cobweb for {@code cobweb_affinity}. Climbing, the slowdown
     * suppression and the faster break all read this one tag, so the mod's own
     * {@code temporary_cobweb} and a pack's own web behave like vanilla's.
     */
    public static final TagKey<Block> COBWEBS = TagKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("neoorigins", "cobwebs"));

    private ModTags() {}
}
