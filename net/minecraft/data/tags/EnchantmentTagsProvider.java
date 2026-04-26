package net.minecraft.data.tags;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;

public abstract class EnchantmentTagsProvider extends TagsProvider<Enchantment> {
    /** @deprecated Forge: Use the {@linkplain #EnchantmentTagsProvider(PackOutput, CompletableFuture, String, net.neoforged.neoforge.common.data.ExistingFileHelper) mod id variant} */
    public EnchantmentTagsProvider(PackOutput p_341044_, CompletableFuture<HolderLookup.Provider> p_341146_) {
        super(p_341044_, Registries.ENCHANTMENT, p_341146_);
    }
    public EnchantmentTagsProvider(PackOutput p_341044_, CompletableFuture<HolderLookup.Provider> p_341146_, String modId, @org.jetbrains.annotations.Nullable net.neoforged.neoforge.common.data.ExistingFileHelper existingFileHelper) {
        super(p_341044_, Registries.ENCHANTMENT, p_341146_, modId, existingFileHelper);
    }

    protected void tooltipOrder(HolderLookup.Provider p_341105_, ResourceKey<Enchantment>... p_345255_) {
        this.tag(EnchantmentTags.TOOLTIP_ORDER).add(p_345255_);
        Set<ResourceKey<Enchantment>> set = Set.of(p_345255_);
        List<String> list = p_341105_.lookupOrThrow(Registries.ENCHANTMENT)
            .listElements()
            .filter(p_344251_ -> !set.contains(p_344251_.unwrapKey().get()))
            .map(Holder::getRegisteredName)
            .collect(Collectors.toList());
        if (!list.isEmpty()) {
            throw new IllegalStateException("Not all enchantments were registered for tooltip ordering. Missing: " + String.join(", ", list));
        }
    }
}
