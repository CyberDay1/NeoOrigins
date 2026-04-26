package net.minecraft.network.chat.contents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public interface PlainTextContents extends ComponentContents {
    MapCodec<PlainTextContents> CODEC = RecordCodecBuilder.mapCodec(
        p_304480_ -> p_304480_.group(Codec.STRING.fieldOf("text").forGetter(PlainTextContents::text)).apply(p_304480_, PlainTextContents::create)
    );
    ComponentContents.Type<PlainTextContents> TYPE = new ComponentContents.Type<>(CODEC, "text");
    PlainTextContents EMPTY = new PlainTextContents() {
        @Override
        public String toString() {
            return "empty";
        }

        @Override
        public String text() {
            return "";
        }
    };

    static PlainTextContents create(String p_304441_) {
        return (PlainTextContents)(p_304441_.isEmpty() ? EMPTY : new PlainTextContents.LiteralContents(p_304441_));
    }

    String text();

    @Override
    default ComponentContents.Type<?> type() {
        return TYPE;
    }

    public static record LiteralContents(String text) implements PlainTextContents {
        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> p_304816_) {
            return p_304816_.accept(this.text);
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> p_304890_, Style p_304907_) {
            return p_304890_.accept(p_304907_, this.text);
        }

        @Override
        public String toString() {
            return "literal{" + this.text + "}";
        }
    }
}
