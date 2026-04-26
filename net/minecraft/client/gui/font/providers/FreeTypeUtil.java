package net.minecraft.client.gui.font.providers;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Vector;
import org.lwjgl.util.freetype.FreeType;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class FreeTypeUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Object LIBRARY_LOCK = new Object();
    private static long library = 0L;

    public static long getLibrary() {
        synchronized (LIBRARY_LOCK) {
            if (library == 0L) {
                try (MemoryStack memorystack = MemoryStack.stackPush()) {
                    PointerBuffer pointerbuffer = memorystack.mallocPointer(1);
                    assertError(FreeType.FT_Init_FreeType(pointerbuffer), "Initializing FreeType library");
                    library = pointerbuffer.get();
                }
            }

            return library;
        }
    }

    public static void assertError(int p_319997_, String p_320575_) {
        if (p_319997_ != 0) {
            throw new IllegalStateException("FreeType error: " + describeError(p_319997_) + " (" + p_320575_ + ")");
        }
    }

    public static boolean checkError(int p_341678_, String p_341603_) {
        if (p_341678_ != 0) {
            LOGGER.error("FreeType error: {} ({})", describeError(p_341678_), p_341603_);
            return true;
        } else {
            return false;
        }
    }

    private static String describeError(int p_320372_) {
        String s = FreeType.FT_Error_String(p_320372_);
        return s != null ? s : "Unrecognized error: 0x" + Integer.toHexString(p_320372_);
    }

    public static FT_Vector setVector(FT_Vector p_320591_, float p_319765_, float p_320303_) {
        long i = (long)Math.round(p_319765_ * 64.0F);
        long j = (long)Math.round(p_320303_ * 64.0F);
        return p_320591_.set(i, j);
    }

    public static float x(FT_Vector p_320117_) {
        return (float)p_320117_.x() / 64.0F;
    }

    public static void destroy() {
        synchronized (LIBRARY_LOCK) {
            if (library != 0L) {
                FreeType.FT_Done_Library(library);
                library = 0L;
            }
        }
    }
}
