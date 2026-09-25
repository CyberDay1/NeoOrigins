package com.cyberday1.neoorigins.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A theme naming a panel PNG that no pack ships must draw parchment's panel, not the checkerboard. */
class ThemePanelFallbackTest {

    private static final ResourceLocation ID = ResourceLocation.parse("mypack:my_theme");
    private static final ResourceLocation PANEL = ResourceLocation.parse("mypack:textures/gui/themes/my_theme/panel.png");
    private static final String JSON = "{\"panel_background\": \"" + PANEL + "\", \"name_color\": \"0xFF112233\","
        + " \"texture_width\": 128, \"texture_height\": 64, \"inset_left\": 4}";

    @AfterEach
    void reset() {
        UIThemeManager.INSTANCE.apply(Map.of(), Mockito.mock(ResourceManager.class), InactiveProfiler.INSTANCE);
    }

    @Test
    void missingPanelFallsBackToParchmentPanelAndGeometry() {
        UITheme theme = load(false);
        UITheme parchment = UITheme.PARCHMENT;
        assertEquals(parchment.panelBackground(), theme.panelBackground());
        assertEquals(parchment.textureWidth(), theme.textureWidth());
        assertEquals(parchment.textureHeight(), theme.textureHeight());
        assertEquals(parchment.insetLeft(), theme.insetLeft());
        assertEquals(0xFF112233, theme.nameColor(), "the author's colours must survive the fallback");
    }

    @Test
    void presentPanelIsKept() {
        UITheme theme = load(true);
        assertEquals(PANEL, theme.panelBackground());
        assertEquals(128, theme.textureWidth());
        assertEquals(4, theme.insetLeft());
    }

    @Test
    void flatThemeIsLeftAlone() {
        UITheme flat = new UITheme(PANEL, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 1, 1, 1, 1, 8, 8, true, 0);
        assertEquals(PANEL, UIThemeManager.withResolvablePanel(ID, flat, p -> false).panelBackground());
    }

    private static UITheme load(boolean panelExists) {
        ResourceManager rm = Mockito.mock(ResourceManager.class);
        Mockito.when(rm.getResource(Mockito.any())).thenReturn(Optional.empty());
        if (panelExists) {
            Resource png = new Resource(Mockito.mock(PackResources.class), () -> new ByteArrayInputStream(new byte[0]));
            Mockito.when(rm.getResource(PANEL)).thenReturn(Optional.of(png));
        }
        Map<ResourceLocation, JsonElement> map = Map.of(ID, JsonParser.parseString(JSON));
        UIThemeManager.INSTANCE.apply(map, rm, InactiveProfiler.INSTANCE);
        return UIThemeManager.get(ID);
    }
}
