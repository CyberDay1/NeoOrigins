package net.minecraft.util.datafix;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import net.minecraft.util.GsonHelper;

public class ComponentDataFixUtils {
    private static final String EMPTY_CONTENTS = createTextComponentJson("");

    public static <T> Dynamic<T> createPlainTextComponent(DynamicOps<T> p_304546_, String p_304390_) {
        String s = createTextComponentJson(p_304390_);
        return new Dynamic<>(p_304546_, p_304546_.createString(s));
    }

    public static <T> Dynamic<T> createEmptyComponent(DynamicOps<T> p_304990_) {
        return new Dynamic<>(p_304990_, p_304990_.createString(EMPTY_CONTENTS));
    }

    private static String createTextComponentJson(String p_304837_) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("text", p_304837_);
        return GsonHelper.toStableString(jsonobject);
    }

    public static <T> Dynamic<T> createTranslatableComponent(DynamicOps<T> p_304499_, String p_304830_) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("translate", p_304830_);
        return new Dynamic<>(p_304499_, p_304499_.createString(GsonHelper.toStableString(jsonobject)));
    }

    public static <T> Dynamic<T> wrapLiteralStringAsComponent(Dynamic<T> p_304540_) {
        return DataFixUtils.orElse(p_304540_.asString().map(p_304989_ -> createPlainTextComponent(p_304540_.getOps(), p_304989_)).result(), p_304540_);
    }

    public static Dynamic<?> rewriteFromLenient(Dynamic<?> p_323863_) {
        Optional<String> optional = p_323863_.asString().result();
        if (optional.isEmpty()) {
            return p_323863_;
        } else {
            String s = optional.get();
            if (!s.isEmpty() && !s.equals("null")) {
                char c0 = s.charAt(0);
                char c1 = s.charAt(s.length() - 1);
                if (c0 == '"' && c1 == '"' || c0 == '{' && c1 == '}' || c0 == '[' && c1 == ']') {
                    try {
                        JsonElement jsonelement = JsonParser.parseString(s);
                        if (jsonelement.isJsonPrimitive()) {
                            return createPlainTextComponent(p_323863_.getOps(), jsonelement.getAsString());
                        }

                        return p_323863_.createString(GsonHelper.toStableString(jsonelement));
                    } catch (JsonParseException jsonparseexception) {
                    }
                }

                return createPlainTextComponent(p_323863_.getOps(), s);
            } else {
                return createEmptyComponent(p_323863_.getOps());
            }
        }
    }

    public static Optional<String> extractTranslationString(String p_338737_) {
        try {
            JsonElement jsonelement = JsonParser.parseString(p_338737_);
            if (jsonelement.isJsonObject()) {
                JsonObject jsonobject = jsonelement.getAsJsonObject();
                JsonElement jsonelement1 = jsonobject.get("translate");
                if (jsonelement1 != null && jsonelement1.isJsonPrimitive()) {
                    return Optional.of(jsonelement1.getAsString());
                }
            }
        } catch (JsonParseException jsonparseexception) {
        }

        return Optional.empty();
    }
}
