package net.minecraft.world.level;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public interface Spawner {
    void setEntityId(EntityType<?> p_312782_, RandomSource p_312579_);

    static void appendHoverText(ItemStack p_311863_, List<Component> p_312185_, String p_312723_) {
        Component component = getSpawnEntityDisplayName(p_311863_, p_312723_);
        if (component != null) {
            p_312185_.add(component);
        } else {
            p_312185_.add(CommonComponents.EMPTY);
            p_312185_.add(Component.translatable("block.minecraft.spawner.desc1").withStyle(ChatFormatting.GRAY));
            p_312185_.add(CommonComponents.space().append(Component.translatable("block.minecraft.spawner.desc2").withStyle(ChatFormatting.BLUE)));
        }
    }

    @Nullable
    static Component getSpawnEntityDisplayName(ItemStack p_312585_, String p_312442_) {
        CompoundTag compoundtag = p_312585_.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).getUnsafe();
        ResourceLocation resourcelocation = getEntityKey(compoundtag, p_312442_);
        return resourcelocation != null
            ? BuiltInRegistries.ENTITY_TYPE
                .getOptional(resourcelocation)
                .map(p_312609_ -> Component.translatable(p_312609_.getDescriptionId()).withStyle(ChatFormatting.GRAY))
                .orElse(null)
            : null;
    }

    @Nullable
    private static ResourceLocation getEntityKey(CompoundTag p_312232_, String p_312196_) {
        if (p_312232_.contains(p_312196_, 10)) {
            String s = p_312232_.getCompound(p_312196_).getCompound("entity").getString("id");
            return ResourceLocation.tryParse(s);
        } else {
            return null;
        }
    }
}
