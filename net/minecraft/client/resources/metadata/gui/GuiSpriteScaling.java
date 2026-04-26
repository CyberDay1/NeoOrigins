package net.minecraft.client.resources.metadata.gui;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.OptionalInt;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface GuiSpriteScaling {
    Codec<GuiSpriteScaling> CODEC = GuiSpriteScaling.Type.CODEC.dispatch(GuiSpriteScaling::type, GuiSpriteScaling.Type::codec);
    GuiSpriteScaling DEFAULT = new GuiSpriteScaling.Stretch();

    GuiSpriteScaling.Type type();

    @OnlyIn(Dist.CLIENT)
    public static record NineSlice(int width, int height, GuiSpriteScaling.NineSlice.Border border) implements GuiSpriteScaling {
        public static final MapCodec<GuiSpriteScaling.NineSlice> CODEC = RecordCodecBuilder.<GuiSpriteScaling.NineSlice>mapCodec(
                p_295296_ -> p_295296_.group(
                            ExtraCodecs.POSITIVE_INT.fieldOf("width").forGetter(GuiSpriteScaling.NineSlice::width),
                            ExtraCodecs.POSITIVE_INT.fieldOf("height").forGetter(GuiSpriteScaling.NineSlice::height),
                            GuiSpriteScaling.NineSlice.Border.CODEC.fieldOf("border").forGetter(GuiSpriteScaling.NineSlice::border)
                        )
                        .apply(p_295296_, GuiSpriteScaling.NineSlice::new)
            )
            .validate(GuiSpriteScaling.NineSlice::validate);

        private static DataResult<GuiSpriteScaling.NineSlice> validate(GuiSpriteScaling.NineSlice p_299275_) {
            GuiSpriteScaling.NineSlice.Border guispritescaling$nineslice$border = p_299275_.border();
            if (guispritescaling$nineslice$border.left() + guispritescaling$nineslice$border.right() >= p_299275_.width()) {
                return DataResult.error(
                    () -> "Nine-sliced texture has no horizontal center slice: "
                            + guispritescaling$nineslice$border.left()
                            + " + "
                            + guispritescaling$nineslice$border.right()
                            + " >= "
                            + p_299275_.width()
                );
            } else {
                return guispritescaling$nineslice$border.top() + guispritescaling$nineslice$border.bottom() >= p_299275_.height()
                    ? DataResult.error(
                        () -> "Nine-sliced texture has no vertical center slice: "
                                + guispritescaling$nineslice$border.top()
                                + " + "
                                + guispritescaling$nineslice$border.bottom()
                                + " >= "
                                + p_299275_.height()
                    )
                    : DataResult.success(p_299275_);
            }
        }

        @Override
        public GuiSpriteScaling.Type type() {
            return GuiSpriteScaling.Type.NINE_SLICE;
        }

        @OnlyIn(Dist.CLIENT)
        public static record Border(int left, int top, int right, int bottom) {
            private static final Codec<GuiSpriteScaling.NineSlice.Border> VALUE_CODEC = ExtraCodecs.POSITIVE_INT
                .flatComapMap(p_295538_ -> new GuiSpriteScaling.NineSlice.Border(p_295538_, p_295538_, p_295538_, p_295538_), p_295407_ -> {
                    OptionalInt optionalint = p_295407_.unpackValue();
                    return optionalint.isPresent() ? DataResult.success(optionalint.getAsInt()) : DataResult.error(() -> "Border has different side sizes");
                });
            private static final Codec<GuiSpriteScaling.NineSlice.Border> RECORD_CODEC = RecordCodecBuilder.create(
                p_297930_ -> p_297930_.group(
                            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("left").forGetter(GuiSpriteScaling.NineSlice.Border::left),
                            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("top").forGetter(GuiSpriteScaling.NineSlice.Border::top),
                            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("right").forGetter(GuiSpriteScaling.NineSlice.Border::right),
                            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("bottom").forGetter(GuiSpriteScaling.NineSlice.Border::bottom)
                        )
                        .apply(p_297930_, GuiSpriteScaling.NineSlice.Border::new)
            );
            static final Codec<GuiSpriteScaling.NineSlice.Border> CODEC = Codec.either(VALUE_CODEC, RECORD_CODEC)
                .xmap(Either::unwrap, p_296295_ -> p_296295_.unpackValue().isPresent() ? Either.left(p_296295_) : Either.right(p_296295_));

            private OptionalInt unpackValue() {
                return this.left() == this.top() && this.top() == this.right() && this.right() == this.bottom()
                    ? OptionalInt.of(this.left())
                    : OptionalInt.empty();
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static record Stretch() implements GuiSpriteScaling {
        public static final MapCodec<GuiSpriteScaling.Stretch> CODEC = MapCodec.unit(GuiSpriteScaling.Stretch::new);

        @Override
        public GuiSpriteScaling.Type type() {
            return GuiSpriteScaling.Type.STRETCH;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static record Tile(int width, int height) implements GuiSpriteScaling {
        public static final MapCodec<GuiSpriteScaling.Tile> CODEC = RecordCodecBuilder.mapCodec(
            p_294311_ -> p_294311_.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("width").forGetter(GuiSpriteScaling.Tile::width),
                        ExtraCodecs.POSITIVE_INT.fieldOf("height").forGetter(GuiSpriteScaling.Tile::height)
                    )
                    .apply(p_294311_, GuiSpriteScaling.Tile::new)
        );

        @Override
        public GuiSpriteScaling.Type type() {
            return GuiSpriteScaling.Type.TILE;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Type implements StringRepresentable {
        STRETCH("stretch", GuiSpriteScaling.Stretch.CODEC),
        TILE("tile", GuiSpriteScaling.Tile.CODEC),
        NINE_SLICE("nine_slice", GuiSpriteScaling.NineSlice.CODEC);

        public static final Codec<GuiSpriteScaling.Type> CODEC = StringRepresentable.fromEnum(GuiSpriteScaling.Type::values);
        private final String key;
        private final MapCodec<? extends GuiSpriteScaling> codec;

        private Type(String p_295906_, MapCodec<? extends GuiSpriteScaling> p_338627_) {
            this.key = p_295906_;
            this.codec = p_338627_;
        }

        @Override
        public String getSerializedName() {
            return this.key;
        }

        public MapCodec<? extends GuiSpriteScaling> codec() {
            return this.codec;
        }
    }
}
