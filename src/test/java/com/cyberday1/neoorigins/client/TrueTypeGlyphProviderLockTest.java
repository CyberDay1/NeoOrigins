package com.cyberday1.neoorigins.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The face lock is a client mixin, and the unit-test harness runs as a dedicated server, so the lock
 * itself cannot run here. This pins what it needs: registration on the client side, and the two
 * methods and the field it wraps. The boot proof is in the hub doc.
 */
// hub: neoorigins/freetype-face-lock.md
class TrueTypeGlyphProviderLockTest {

    private static final String MIXIN = "client.TrueTypeGlyphProviderLockMixin";

    @Test
    void theLockIsRegisteredAsAClientMixin() throws Exception {
        JsonObject config;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("neoorigins.mixins.json")) {
            assertNotNull(in, "neoorigins.mixins.json not on the test classpath");
            config = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonArray client = config.getAsJsonArray("client");
        assertTrue(client.contains(new com.google.gson.JsonPrimitive(MIXIN)),
            MIXIN + " is not in the client mixin list, so a font reference can race on 1.21.1 again");
    }

    @Test
    void theWrappedMembersExist() throws Exception {
        ClassNode provider = new ClassNode();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("com/mojang/blaze3d/font/TrueTypeGlyphProvider.class")) {
            assertNotNull(in, "TrueTypeGlyphProvider.class not on the test classpath");
            new ClassReader(in).accept(provider, ClassReader.SKIP_CODE);
        }
        assertTrue(provider.methods.stream().anyMatch(m -> m.name.equals("getGlyph")
            && m.desc.equals("(I)Lcom/mojang/blaze3d/font/GlyphInfo;")), "getGlyph(int) is gone or changed");
        assertTrue(provider.methods.stream().anyMatch(m -> m.name.equals("getSupportedGlyphs")
            && m.desc.equals("()Lit/unimi/dsi/fastutil/ints/IntSet;")), "getSupportedGlyphs() is gone or changed");
        assertTrue(provider.fields.stream().anyMatch(f -> f.name.equals("face")
            && f.desc.equals("Lorg/lwjgl/util/freetype/FT_Face;")), "the face field is gone or changed");
    }
}
