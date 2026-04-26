package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemInput {
    private static final Dynamic2CommandExceptionType ERROR_STACK_TOO_BIG = new Dynamic2CommandExceptionType(
        (p_304131_, p_304132_) -> Component.translatableEscape("arguments.item.overstacked", p_304131_, p_304132_)
    );
    private final Holder<Item> item;
    private final DataComponentPatch components;

    public ItemInput(Holder<Item> p_235282_, DataComponentPatch p_345942_) {
        this.item = p_235282_;
        this.components = p_345942_;
    }

    public Item getItem() {
        return this.item.value();
    }

    public ItemStack createItemStack(int p_120981_, boolean p_120982_) throws CommandSyntaxException {
        ItemStack itemstack = new ItemStack(this.item, p_120981_);
        itemstack.applyComponents(this.components);
        if (p_120982_ && p_120981_ > itemstack.getMaxStackSize()) {
            throw ERROR_STACK_TOO_BIG.create(this.getItemName(), itemstack.getMaxStackSize());
        } else {
            return itemstack;
        }
    }

    public String serialize(HolderLookup.Provider p_330692_) {
        StringBuilder stringbuilder = new StringBuilder(this.getItemName());
        String s = this.serializeComponents(p_330692_);
        if (!s.isEmpty()) {
            stringbuilder.append('[');
            stringbuilder.append(s);
            stringbuilder.append(']');
        }

        return stringbuilder.toString();
    }

    private String serializeComponents(HolderLookup.Provider p_331015_) {
        DynamicOps<Tag> dynamicops = p_331015_.createSerializationContext(NbtOps.INSTANCE);
        return this.components.entrySet().stream().flatMap(p_344174_ -> {
            DataComponentType<?> datacomponenttype = p_344174_.getKey();
            ResourceLocation resourcelocation = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(datacomponenttype);
            if (resourcelocation == null) {
                return Stream.empty();
            } else {
                Optional<?> optional = p_344174_.getValue();
                if (optional.isPresent()) {
                    TypedDataComponent<?> typeddatacomponent = TypedDataComponent.createUnchecked(datacomponenttype, optional.get());
                    return typeddatacomponent.encodeValue(dynamicops).result().stream().map(p_344172_ -> resourcelocation.toString() + "=" + p_344172_);
                } else {
                    return Stream.of("!" + resourcelocation.toString());
                }
            }
        }).collect(Collectors.joining(String.valueOf(',')));
    }

    private String getItemName() {
        return this.item.unwrapKey().<Object>map(ResourceKey::location).orElseGet(() -> "unknown[" + this.item + "]").toString();
    }
}
