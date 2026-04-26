package net.minecraft.server.packs.metadata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

public interface MetadataSectionType<T> extends MetadataSectionSerializer<T> {
    JsonObject toJson(T p_249140_);

    static <T> MetadataSectionType<T> fromCodec(final String p_249716_, final Codec<T> p_249525_) {
        return new MetadataSectionType<T>() {
            @Override
            public String getMetadataSectionName() {
                return p_249716_;
            }

            @Override
            public T fromJson(JsonObject p_249450_) {
                return p_249525_.parse(JsonOps.INSTANCE, p_249450_).getOrThrow(JsonParseException::new);
            }

            @Override
            public JsonObject toJson(T p_250691_) {
                return p_249525_.encodeStart(JsonOps.INSTANCE, p_250691_).getOrThrow(IllegalArgumentException::new).getAsJsonObject();
            }
        };
    }
}
