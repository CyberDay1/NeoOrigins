package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public record ItemContainerPredicate(Optional<CollectionPredicate<ItemStack, ItemPredicate>> items)
    implements SingleComponentItemPredicate<ItemContainerContents> {
    public static final Codec<ItemContainerPredicate> CODEC = RecordCodecBuilder.create(
        p_341032_ -> p_341032_.group(
                    CollectionPredicate.<ItemStack, ItemPredicate>codec(ItemPredicate.CODEC).optionalFieldOf("items").forGetter(ItemContainerPredicate::items)
                )
                .apply(p_341032_, ItemContainerPredicate::new)
    );

    @Override
    public DataComponentType<ItemContainerContents> componentType() {
        return DataComponents.CONTAINER;
    }

    public boolean matches(ItemStack p_340936_, ItemContainerContents p_340971_) {
        return !this.items.isPresent() || this.items.get().test(p_340971_.nonEmptyItems());
    }
}
