package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public record FireworkExplosion(FireworkExplosion.Shape shape, IntList colors, IntList fadeColors, boolean hasTrail, boolean hasTwinkle)
    implements TooltipProvider {
    public static final FireworkExplosion DEFAULT = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(), IntList.of(), false, false);
    public static final Codec<IntList> COLOR_LIST_CODEC = Codec.INT.listOf().xmap(IntArrayList::new, ArrayList::new);
    public static final Codec<FireworkExplosion> CODEC = RecordCodecBuilder.create(
        p_337945_ -> p_337945_.group(
                    FireworkExplosion.Shape.CODEC.fieldOf("shape").forGetter(FireworkExplosion::shape),
                    COLOR_LIST_CODEC.optionalFieldOf("colors", IntList.of()).forGetter(FireworkExplosion::colors),
                    COLOR_LIST_CODEC.optionalFieldOf("fade_colors", IntList.of()).forGetter(FireworkExplosion::fadeColors),
                    Codec.BOOL.optionalFieldOf("has_trail", Boolean.valueOf(false)).forGetter(FireworkExplosion::hasTrail),
                    Codec.BOOL.optionalFieldOf("has_twinkle", Boolean.valueOf(false)).forGetter(FireworkExplosion::hasTwinkle)
                )
                .apply(p_337945_, FireworkExplosion::new)
    );
    private static final StreamCodec<ByteBuf, IntList> COLOR_LIST_STREAM_CODEC = ByteBufCodecs.INT
        .apply(ByteBufCodecs.list())
        .map(IntArrayList::new, ArrayList::new);
    public static final StreamCodec<ByteBuf, FireworkExplosion> STREAM_CODEC = StreamCodec.composite(
        FireworkExplosion.Shape.STREAM_CODEC,
        FireworkExplosion::shape,
        COLOR_LIST_STREAM_CODEC,
        FireworkExplosion::colors,
        COLOR_LIST_STREAM_CODEC,
        FireworkExplosion::fadeColors,
        ByteBufCodecs.BOOL,
        FireworkExplosion::hasTrail,
        ByteBufCodecs.BOOL,
        FireworkExplosion::hasTwinkle,
        FireworkExplosion::new
    );
    private static final Component CUSTOM_COLOR_NAME = Component.translatable("item.minecraft.firework_star.custom_color");

    @Override
    public void addToTooltip(Item.TooltipContext p_341341_, Consumer<Component> p_331404_, TooltipFlag p_330365_) {
        this.addShapeNameTooltip(p_331404_);
        this.addAdditionalTooltip(p_331404_);
    }

    public void addShapeNameTooltip(Consumer<Component> p_331748_) {
        p_331748_.accept(this.shape.getName().withStyle(ChatFormatting.GRAY));
    }

    public void addAdditionalTooltip(Consumer<Component> p_331075_) {
        if (!this.colors.isEmpty()) {
            p_331075_.accept(appendColors(Component.empty().withStyle(ChatFormatting.GRAY), this.colors));
        }

        if (!this.fadeColors.isEmpty()) {
            p_331075_.accept(
                appendColors(
                    Component.translatable("item.minecraft.firework_star.fade_to").append(CommonComponents.SPACE).withStyle(ChatFormatting.GRAY),
                    this.fadeColors
                )
            );
        }

        if (this.hasTrail) {
            p_331075_.accept(Component.translatable("item.minecraft.firework_star.trail").withStyle(ChatFormatting.GRAY));
        }

        if (this.hasTwinkle) {
            p_331075_.accept(Component.translatable("item.minecraft.firework_star.flicker").withStyle(ChatFormatting.GRAY));
        }
    }

    private static Component appendColors(MutableComponent p_331806_, IntList p_330350_) {
        for (int i = 0; i < p_330350_.size(); i++) {
            if (i > 0) {
                p_331806_.append(", ");
            }

            p_331806_.append(getColorName(p_330350_.getInt(i)));
        }

        return p_331806_;
    }

    private static Component getColorName(int p_330226_) {
        DyeColor dyecolor = DyeColor.byFireworkColor(p_330226_);
        return (Component)(dyecolor == null ? CUSTOM_COLOR_NAME : Component.translatable("item.minecraft.firework_star." + dyecolor.getName()));
    }

    public FireworkExplosion withFadeColors(IntList p_330678_) {
        return new FireworkExplosion(this.shape, this.colors, new IntArrayList(p_330678_), this.hasTrail, this.hasTwinkle);
    }

    @net.neoforged.fml.common.asm.enumextension.IndexedEnum
    @net.neoforged.fml.common.asm.enumextension.NamedEnum(1)
    @net.neoforged.fml.common.asm.enumextension.NetworkedEnum(net.neoforged.fml.common.asm.enumextension.NetworkedEnum.NetworkCheck.BIDIRECTIONAL)
    public static enum Shape implements StringRepresentable, net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
        SMALL_BALL(0, "small_ball"),
        LARGE_BALL(1, "large_ball"),
        STAR(2, "star"),
        CREEPER(3, "creeper"),
        BURST(4, "burst");

        private static final IntFunction<FireworkExplosion.Shape> BY_ID = ByIdMap.continuous(
            FireworkExplosion.Shape::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO
        );
        public static final StreamCodec<ByteBuf, FireworkExplosion.Shape> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, FireworkExplosion.Shape::getId);
        public static final Codec<FireworkExplosion.Shape> CODEC = StringRepresentable.fromValues(FireworkExplosion.Shape::values);
        private final int id;
        private final String name;

        private Shape(int p_330605_, String p_331586_) {
            this.id = p_330605_;
            this.name = p_331586_;
        }

        public MutableComponent getName() {
            return Component.translatable("item.minecraft.firework_star.shape." + this.name);
        }

        public int getId() {
            return this.id;
        }

        public static FireworkExplosion.Shape byId(int p_330838_) {
            return BY_ID.apply(p_330838_);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
            return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(FireworkExplosion.Shape.class);
        }
    }
}
