package net.minecraft.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import net.minecraft.CharPredicate;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;

public class ParserUtils {
    private static final Field JSON_READER_POS = Util.make(() -> {
        try {
            Field field = JsonReader.class.getDeclaredField("pos");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException nosuchfieldexception) {
            throw new IllegalStateException("Couldn't get field 'pos' for JsonReader", nosuchfieldexception);
        }
    });
    private static final Field JSON_READER_LINESTART = Util.make(() -> {
        try {
            Field field = JsonReader.class.getDeclaredField("lineStart");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException nosuchfieldexception) {
            throw new IllegalStateException("Couldn't get field 'lineStart' for JsonReader", nosuchfieldexception);
        }
    });

    private static int getPos(JsonReader p_313929_) {
        try {
            return JSON_READER_POS.getInt(p_313929_) - JSON_READER_LINESTART.getInt(p_313929_);
        } catch (IllegalAccessException illegalaccessexception) {
            throw new IllegalStateException("Couldn't read position of JsonReader", illegalaccessexception);
        }
    }

    public static <T> T parseJson(HolderLookup.Provider p_323581_, StringReader p_313786_, Codec<T> p_313876_) {
        JsonReader jsonreader = new JsonReader(new java.io.StringReader(p_313786_.getRemaining()));
        jsonreader.setLenient(false);

        Object object;
        try {
            JsonElement jsonelement = Streams.parse(jsonreader);
            object = p_313876_.parse(p_323581_.createSerializationContext(JsonOps.INSTANCE), jsonelement).getOrThrow(JsonParseException::new);
        } catch (StackOverflowError stackoverflowerror) {
            throw new JsonParseException(stackoverflowerror);
        } finally {
            p_313786_.setCursor(p_313786_.getCursor() + getPos(jsonreader));
        }

        return (T)object;
    }

    public static String readWhile(StringReader p_332772_, CharPredicate p_332664_) {
        int i = p_332772_.getCursor();

        while (p_332772_.canRead() && p_332664_.test(p_332772_.peek())) {
            p_332772_.skip();
        }

        return p_332772_.getString().substring(i, p_332772_.getCursor());
    }
}
