package net.minecraft.world.item;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.FastColor;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Contract;

public enum DyeColor implements StringRepresentable {
    WHITE(0, "white", 16383998, MapColor.SNOW, 15790320, 16777215),
    ORANGE(1, "orange", 16351261, MapColor.COLOR_ORANGE, 15435844, 16738335),
    MAGENTA(2, "magenta", 13061821, MapColor.COLOR_MAGENTA, 12801229, 16711935),
    LIGHT_BLUE(3, "light_blue", 3847130, MapColor.COLOR_LIGHT_BLUE, 6719955, 10141901),
    YELLOW(4, "yellow", 16701501, MapColor.COLOR_YELLOW, 14602026, 16776960),
    LIME(5, "lime", 8439583, MapColor.COLOR_LIGHT_GREEN, 4312372, 12582656),
    PINK(6, "pink", 15961002, MapColor.COLOR_PINK, 14188952, 16738740),
    GRAY(7, "gray", 4673362, MapColor.COLOR_GRAY, 4408131, 8421504),
    LIGHT_GRAY(8, "light_gray", 10329495, MapColor.COLOR_LIGHT_GRAY, 11250603, 13882323),
    CYAN(9, "cyan", 1481884, MapColor.COLOR_CYAN, 2651799, 65535),
    PURPLE(10, "purple", 8991416, MapColor.COLOR_PURPLE, 8073150, 10494192),
    BLUE(11, "blue", 3949738, MapColor.COLOR_BLUE, 2437522, 255),
    BROWN(12, "brown", 8606770, MapColor.COLOR_BROWN, 5320730, 9127187),
    GREEN(13, "green", 6192150, MapColor.COLOR_GREEN, 3887386, 65280),
    RED(14, "red", 11546150, MapColor.COLOR_RED, 11743532, 16711680),
    BLACK(15, "black", 1908001, MapColor.COLOR_BLACK, 1973019, 0);

    private static final IntFunction<DyeColor> BY_ID = ByIdMap.continuous(DyeColor::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    private static final Int2ObjectOpenHashMap<DyeColor> BY_FIREWORK_COLOR = new Int2ObjectOpenHashMap<>(
        Arrays.stream(values()).collect(Collectors.toMap(p_41064_ -> p_41064_.fireworkColor, p_41056_ -> (DyeColor)p_41056_))
    );
    public static final StringRepresentable.EnumCodec<DyeColor> CODEC = StringRepresentable.fromEnum(DyeColor::values);
    public static final StreamCodec<ByteBuf, DyeColor> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, DyeColor::getId);
    private final int id;
    private final String name;
    private final MapColor mapColor;
    private final int textureDiffuseColor;
    private final int fireworkColor;
    private final net.minecraft.tags.TagKey<Item> dyesTag;
    private final net.minecraft.tags.TagKey<Item> dyedTag;
    private final int textColor;

    private DyeColor(int p_41046_, String p_41047_, int p_41048_, MapColor p_285297_, int p_41050_, int p_41051_) {
        this.id = p_41046_;
        this.name = p_41047_;
        this.mapColor = p_285297_;
        this.textColor = p_41051_;
        this.dyesTag = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "dyes/" + p_41047_));
        this.dyedTag = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "dyed/" + p_41047_));
        this.textureDiffuseColor = FastColor.ARGB32.opaque(p_41048_);
        this.fireworkColor = p_41050_;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getTextureDiffuseColor() {
        return this.textureDiffuseColor;
    }

    public MapColor getMapColor() {
        return this.mapColor;
    }

    public int getFireworkColor() {
        return this.fireworkColor;
    }

    public int getTextColor() {
        return this.textColor;
    }

    public static DyeColor byId(int p_41054_) {
        return BY_ID.apply(p_41054_);
    }

    @Nullable
    @Contract("_,!null->!null;_,null->_")
    public static DyeColor byName(String p_41058_, @Nullable DyeColor p_41059_) {
        DyeColor dyecolor = CODEC.byName(p_41058_);
        return dyecolor != null ? dyecolor : p_41059_;
    }

    @Nullable
    public static DyeColor byFireworkColor(int p_41062_) {
        return BY_FIREWORK_COLOR.get(p_41062_);
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /**
     * Gets the tag key representing the set of items which provide this dye color.
     * @return A {@link net.minecraft.tags.TagKey<Item>} representing the set of items which provide this dye color.
     */
    public net.minecraft.tags.TagKey<Item> getTag() {
        return dyesTag;
    }

    /**
     * Gets the tag key representing the set of items which are dyed with this color.
     * @return A {@link net.minecraft.tags.TagKey<Item>} representing the set of items which are dyed with this color.
     */
    public net.minecraft.tags.TagKey<Item> getDyedTag() {
        return dyedTag;
    }

    @Nullable
    public static DyeColor getColor(ItemStack stack) {
        if (stack.getItem() instanceof DyeItem)
            return ((DyeItem)stack.getItem()).getDyeColor();

        for (int x = 0; x < BLACK.getId(); x++) {
            DyeColor color = byId(x);
            if (stack.is(color.getTag()))
                return color;
        }

        return null;
    }
}
