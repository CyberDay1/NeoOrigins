package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.client.BarFxManager;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.CreatorAssets;
import com.cyberday1.neoorigins.screen.creator.CreatorHost;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.service.CustomPackSerializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bar style fields ({@code hud_render.animated}, {@code tint}, {@code tint_end})
 * in the in-game creator: they are offered, validated like {@code color}, and a
 * resource power written through the form loads back with the same bar.
 */
class ResourceBarFieldsFormTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final class Host implements CreatorHost {
        @Override public <T extends GuiEventListener & Renderable & NarratableEntry> T register(T w) { return w; }
        @Override public <T extends GuiEventListener & Renderable & NarratableEntry> T registerInputOnly(T w) { return w; }
        @Override public Font font() { return null; }
        @Override public void requestRebuild() { }
        @Override public int hostWidth() { return 480; }
        @Override public int hostHeight() { return 320; }
        @Override public void queueTooltip(List<String> lines, int mx, int my) { }
    }

    private static FormFieldSpec hudChild(String name) {
        FormFieldSpec hud = FormModel.forPower(ResourceLocation.parse("neoorigins:resource")).stream()
            .filter(f -> f.name().equals("hud_render")).findFirst().orElse(null);
        assertNotNull(hud, "resource has no hud_render field");
        return hud.children().stream().filter(f -> f.name().equals(name)).findFirst()
            .orElseThrow(() -> new AssertionError("hud_render has no " + name));
    }

    private record Saved(JsonObject json, int errors) {}

    /** Open {@code body} in the creator's form, save it, and return what it wrote. */
    private static Saved throughForm(JsonObject body) {
        OriginDraft.PowerDraft pd = new OriginDraft.PowerDraft(
            ResourceLocation.parse("test:bar"), "neoorigins:resource");
        pd.rawJson = body.toString();
        PowerFormPanel panel = new PowerFormPanel();
        panel.init(new Host(), pd, false, 0, 0, 480, 320);
        int errors = panel.errorCount();
        panel.push();
        JsonObject written = CustomPackSerializer.powerJson(pd);
        written.remove("type");
        return new Saved(written, errors);
    }

    private static ResourcePower.Config load(JsonObject body) {
        JsonObject json = body.deepCopy();
        json.addProperty("_power_id", "test:bar");
        return new ResourcePower().codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static JsonObject bar(String hud) {
        return JsonParser.parseString("{\"min\": 0, \"max\": 50, \"hud_render\": " + hud + "}").getAsJsonObject();
    }

    @Test
    void theStyleFieldsAreStringsValidatedLikeColor() {
        FormFieldSpec color = hudChild("color");
        assertNotNull(color.pattern(), "color has no hex pattern");
        for (String name : List.of("tint", "tint_end")) {
            FormFieldSpec f = hudChild(name);
            assertEquals(FormFieldSpec.Kind.STRING, f.kind(), name);
            assertEquals(color.pattern(), f.pattern(), name + " must validate like color");
        }
        assertEquals(FormFieldSpec.Kind.STRING, hudChild("animated").kind());
    }

    @Test
    void animatedOffersEveryBuiltInStyle() {
        assertEquals("bar_style", CreatorAssets.registryKind("animated"));
        assertTrue(CreatorAssets.registryList("bar_style").containsAll(BarFxManager.BUILTIN_STYLES),
            "picker misses a built-in style: " + CreatorAssets.registryList("bar_style"));
    }

    @Test
    void everyStyleRoundTripsThroughTheForm() {
        for (String style : BarFxManager.BUILTIN_STYLES) {
            JsonObject in = bar("{\"label\": \"Heat\", \"color\": \"#FF5500\", \"should_render\": true,"
                + " \"animated\": \"" + style + "\", \"tint\": \"#80FFAA00\", \"tint_end\": \"#00FF00\","
                + " \"always_render\": true}");
            Saved out = throughForm(in);
            assertEquals(0, out.errors(), style + " flagged as invalid");
            assertEquals(in, out.json(), style + " changed on save");

            ResourcePower.Config c = load(out.json());
            assertEquals(style, c.animated());
            assertEquals(0xFFFF5500, c.color());
            assertEquals(0x80FFAA00, c.tint());
            assertEquals(0xFF00FF00, c.tintEnd());
            assertTrue(c.alwaysShow());
        }
    }

    @Test
    void aPackStyleIdIsAcceptedAsTyped() {
        JsonObject in = bar("{\"animated\": \"mypack:lava\", \"tint\": \"FF8800\"}");
        Saved out = throughForm(in);
        assertEquals(0, out.errors());
        assertEquals(in, out.json());
        assertEquals("mypack:lava", load(out.json()).animated());
    }

    @Test
    void aWrongLengthColorIsFlaggedInEveryColorField() {
        for (String field : List.of("color", "tint", "tint_end")) {
            Saved out = throughForm(bar("{\"" + field + "\": \"#FFF\"}"));
            assertEquals(1, out.errors(), field + " accepted a 3-digit hex");
        }
    }

    @Test
    void aBlankTintIsNotSet() {
        ResourcePower.Config c = load(bar("{\"tint\": \"\", \"tint_end\": \"\"}"));
        assertEquals(0, c.tint());
        assertEquals(0, c.tintEnd());
    }
}
