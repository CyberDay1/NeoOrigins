package net.minecraft.world.item.component;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public record ItemLore(List<Component> lines, List<Component> styledLines) implements TooltipProvider {
    public static final ItemLore EMPTY = new ItemLore(List.of());
    public static final int MAX_LINES = 256;
    private static final Style LORE_STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true);
    public static final Codec<ItemLore> CODEC = ComponentSerialization.FLAT_CODEC.sizeLimitedListOf(256).xmap(ItemLore::new, ItemLore::lines);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemLore> STREAM_CODEC = ComponentSerialization.STREAM_CODEC
        .apply(ByteBufCodecs.list(256))
        .map(ItemLore::new, ItemLore::lines);

    public ItemLore(List<Component> p_330411_) {
        this(p_330411_, Lists.transform(p_330411_, p_330607_ -> ComponentUtils.mergeStyles(p_330607_.copy(), LORE_STYLE)));
    }

    public ItemLore(List<Component> lines, List<Component> styledLines) {
        if (lines.size() > 256) {
            throw new IllegalArgumentException("Got " + lines.size() + " lines, but maximum is 256");
        } else {
            this.lines = lines;
            this.styledLines = styledLines;
        }
    }

    public ItemLore withLineAdded(Component p_330547_) {
        return new ItemLore(Util.copyAndAdd(this.lines, p_330547_));
    }

    @Override
    public void addToTooltip(Item.TooltipContext p_341353_, Consumer<Component> p_332116_, TooltipFlag p_330800_) {
        this.styledLines.forEach(p_332116_);
    }
}
