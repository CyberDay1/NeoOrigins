package com.cyberday1.neoorigins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Serialises FreeType calls on a shared face. A font that {@code reference}s another shares its
 * provider, and {@code FontManager#finalizeProviderLoading} runs per font on separate workers, so
 * two threads can walk one face at once and crash the client. 26.1 and later lock on the face in
 * vanilla; this is the 1.21.1 equivalent.
 */
// hub: neoorigins/freetype-face-lock.md
@Mixin(TrueTypeGlyphProvider.class)
public abstract class TrueTypeGlyphProviderLockMixin {

    @Shadow
    private FT_Face face;

    @WrapMethod(method = "getGlyph")
    private GlyphInfo neoorigins$lockGetGlyph(int codepoint, Operation<GlyphInfo> original) {
        FT_Face lock = this.face;
        if (lock == null) return original.call(codepoint);
        synchronized (lock) {
            return original.call(codepoint);
        }
    }

    @WrapMethod(method = "getSupportedGlyphs")
    private IntSet neoorigins$lockSupportedGlyphs(Operation<IntSet> original) {
        FT_Face lock = this.face;
        if (lock == null) return original.call();
        synchronized (lock) {
            return original.call();
        }
    }
}
