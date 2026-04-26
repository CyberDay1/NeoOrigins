package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public record Unbreakable(boolean showInTooltip) implements TooltipProvider {
    public static final Codec<Unbreakable> CODEC = RecordCodecBuilder.create(
        p_337955_ -> p_337955_.group(Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(Unbreakable::showInTooltip))
                .apply(p_337955_, Unbreakable::new)
    );
    public static final StreamCodec<ByteBuf, Unbreakable> STREAM_CODEC = ByteBufCodecs.BOOL.map(Unbreakable::new, Unbreakable::showInTooltip);
    private static final Component TOOLTIP = Component.translatable("item.unbreakable").withStyle(ChatFormatting.BLUE);

    @Override
    public void addToTooltip(Item.TooltipContext p_341037_, Consumer<Component> p_330706_, TooltipFlag p_331385_) {
        if (this.showInTooltip) {
            p_330706_.accept(TOOLTIP);
        }
    }

    public Unbreakable withTooltip(boolean p_335889_) {
        return new Unbreakable(p_335889_);
    }
}
