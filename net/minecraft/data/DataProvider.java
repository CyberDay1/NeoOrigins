package net.minecraft.data;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public interface DataProvider {
    /**
     * Neo: Allows changing the indentation width used by {@link #saveStable}.
     */
    java.util.concurrent.atomic.AtomicInteger INDENT_WIDTH = new java.util.concurrent.atomic.AtomicInteger(2);

    ToIntFunction<String> FIXED_ORDER_FIELDS = Util.make(new Object2IntOpenHashMap<>(), p_236070_ -> {
        // Neo: conditions go first
        p_236070_.put("neoforge:conditions", -1);
        p_236070_.put("type", 0);
        p_236070_.put("parent", 1);
        p_236070_.defaultReturnValue(2);
    });
    Comparator<String> KEY_COMPARATOR = Comparator.comparingInt(FIXED_ORDER_FIELDS).thenComparing(p_236077_ -> (String)p_236077_);
    Logger LOGGER = LogUtils.getLogger();

    CompletableFuture<?> run(CachedOutput p_236071_);

    String getName();

    static <T> CompletableFuture<?> saveStable(CachedOutput p_298323_, HolderLookup.Provider p_323556_, Codec<T> p_299231_, T p_298793_, Path p_298236_) {
        RegistryOps<JsonElement> registryops = p_323556_.createSerializationContext(JsonOps.INSTANCE);
        JsonElement jsonelement = p_299231_.encodeStart(registryops, p_298793_).getOrThrow();
        return saveStable(p_298323_, jsonelement, p_298236_);
    }

    static CompletableFuture<?> saveStable(CachedOutput p_253653_, JsonElement p_254542_, Path p_254467_) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
                HashingOutputStream hashingoutputstream = new HashingOutputStream(Hashing.sha1(), bytearrayoutputstream);

                try (JsonWriter jsonwriter = new JsonWriter(new OutputStreamWriter(hashingoutputstream, StandardCharsets.UTF_8))) {
                    jsonwriter.setSerializeNulls(false);
                    jsonwriter.setIndent(" ".repeat(java.lang.Math.max(0, INDENT_WIDTH.get()))); // Neo: Allow changing the indent width without needing to mixin this lambda.
                    GsonHelper.writeValue(jsonwriter, p_254542_, KEY_COMPARATOR);
                }

                p_253653_.writeIfNeeded(p_254467_, bytearrayoutputstream.toByteArray(), hashingoutputstream.hash());
            } catch (IOException ioexception) {
                LOGGER.error("Failed to save file to {}", p_254467_, ioexception);
            }
        }, Util.backgroundExecutor());
    }

    @FunctionalInterface
    public interface Factory<T extends DataProvider> {
        T create(PackOutput p_253851_);
    }
}
