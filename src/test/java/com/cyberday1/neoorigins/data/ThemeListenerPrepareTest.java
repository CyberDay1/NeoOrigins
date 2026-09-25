package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.client.theme.UIThemeManager;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Both theme listeners build their INSTANCE during static init. A pack that ships
 * one file in their folder must load, not abort the whole reload.
 */
class ThemeListenerPrepareTest {

    @Test
    void activeThemeFileLoads() throws Exception {
        assertLoads(ActiveThemeManager.INSTANCE, "neoorigins", "mypack:neoorigins/active_theme.json",
            "{\"theme\": \"mypack:my_theme\"}");
    }

    @Test
    void uiThemeFileLoads() throws Exception {
        assertLoads(UIThemeManager.INSTANCE, "ui_themes", "mypack:ui_themes/my_theme.json", "{}");
    }

    @SuppressWarnings("unchecked")
    private static void assertLoads(SimpleJsonResourceReloadListener listener, String dir, String file, String json)
            throws Exception {
        ResourceManager rm = Mockito.mock(ResourceManager.class);
        var resource = new Resource(Mockito.mock(PackResources.class),
            () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        Mockito.when(rm.listResources(Mockito.eq(dir), Mockito.any()))
            .thenReturn(Map.of(ResourceLocation.parse(file), resource));
        Method prepare = SimpleJsonResourceReloadListener.class.getDeclaredMethod("prepare",
            ResourceManager.class, net.minecraft.util.profiling.ProfilerFiller.class);
        prepare.setAccessible(true);
        Map<ResourceLocation, JsonElement> loaded = assertDoesNotThrow(
            () -> (Map<ResourceLocation, JsonElement>) prepare.invoke(listener, rm, InactiveProfiler.INSTANCE),
            "one " + dir + " file aborted the reload");
        assertEquals(1, loaded.size(), "the " + dir + " file was not read");
    }
}
