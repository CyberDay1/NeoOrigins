package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public record DyedItemColor(int rgb, boolean showInTooltip) implements TooltipProvider {
    private static final Codec<DyedItemColor> FULL_CODEC = RecordCodecBuilder.create(
        p_337944_ -> p_337944_.group(
                    Codec.INT.fieldOf("rgb").forGetter(DyedItemColor::rgb),
                    Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(DyedItemColor::showInTooltip)
                )
                .apply(p_337944_, DyedItemColor::new)
    );
    public static final Codec<DyedItemColor> CODEC = Codec.withAlternative(FULL_CODEC, Codec.INT, p_332619_ -> new DyedItemColor(p_332619_, true));
    public static final StreamCodec<ByteBuf, DyedItemColor> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, DyedItemColor::rgb, ByteBufCodecs.BOOL, DyedItemColor::showInTooltip, DyedItemColor::new
    );
    public static final int LEATHER_COLOR = -6265536;

    public static int getOrDefault(ItemStack p_330661_, int p_331869_) {
        DyedItemColor dyeditemcolor = p_330661_.get(DataComponents.DYED_COLOR);
        return dyeditemcolor != null ? FastColor.ARGB32.opaque(dyeditemcolor.rgb()) : p_331869_;
    }

    public static ItemStack applyDyes(ItemStack p_331581_, List<DyeItem> p_330568_) {
        if (!p_331581_.is(ItemTags.DYEABLE)) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = p_331581_.copyWithCount(1);
            int i = 0;
            int j = 0;
            int k = 0;
            int l = 0;
            int i1 = 0;
            DyedItemColor dyeditemcolor = itemstack.get(DataComponents.DYED_COLOR);
            if (dyeditemcolor != null) {
                int j1 = FastColor.ARGB32.red(dyeditemcolor.rgb());
                int k1 = FastColor.ARGB32.green(dyeditemcolor.rgb());
                int l1 = FastColor.ARGB32.blue(dyeditemcolor.rgb());
                l += Math.max(j1, Math.max(k1, l1));
                i += j1;
                j += k1;
                k += l1;
                i1++;
            }

            for (DyeItem dyeitem : p_330568_) {
                int j3 = dyeitem.getDyeColor().getTextureDiffuseColor();
                int i2 = FastColor.ARGB32.red(j3);
                int j2 = FastColor.ARGB32.green(j3);
                int k2 = FastColor.ARGB32.blue(j3);
                l += Math.max(i2, Math.max(j2, k2));
                i += i2;
                j += j2;
                k += k2;
                i1++;
            }

            int l2 = i / i1;
            int i3 = j / i1;
            int k3 = k / i1;
            float f = (float)l / (float)i1;
            float f1 = (float)Math.max(l2, Math.max(i3, k3));
            l2 = (int)((float)l2 * f / f1);
            i3 = (int)((float)i3 * f / f1);
            k3 = (int)((float)k3 * f / f1);
            int l3 = FastColor.ARGB32.color(0, l2, i3, k3);
            boolean flag = dyeditemcolor == null || dyeditemcolor.showInTooltip();
            itemstack.set(DataComponents.DYED_COLOR, new DyedItemColor(l3, flag));
            return itemstack;
        }
    }

    @Override
    public void addToTooltip(Item.TooltipContext p_340955_, Consumer<Component> p_331920_, TooltipFlag p_330757_) {
        if (this.showInTooltip) {
            if (p_330757_.isAdvanced()) {
                p_331920_.accept(Component.translatable("item.color", String.format(Locale.ROOT, "#%06X", this.rgb)).withStyle(ChatFormatting.GRAY));
            } else {
                p_331920_.accept(Component.translatable("item.dyed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    public DyedItemColor withTooltip(boolean p_335674_) {
        return new DyedItemColor(this.rgb, p_335674_);
    }
}
