package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.network.payload.SyncResourcePayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code hud_render.should_render: true} shows the bar of a {@code hidden}
 * resource, and {@code text_color} / {@code text_outline} reach the client.
 */
class ResourceHudTextAndVisibilityTest {

    private static final int DEFAULT_TEXT = 0xFFCCCCCC;

    private static ResourcePower.Config decode(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        json.addProperty("_power_id", "test:bar");
        return new ResourcePower().codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void hiddenAloneStillHidesTheBar() {
        assertTrue(decode("{\"hidden\": true}").hidden());
        assertTrue(decode("{\"hidden\": true, \"hud_render\": {\"label\": \"X\"}}").hidden());
    }

    @Test
    void anExplicitShouldRenderTrueShowsTheBarOfAHiddenResource() {
        assertFalse(decode("{\"hidden\": true, \"hud_render\": {\"should_render\": true}}").hidden());
    }

    @Test
    void shouldRenderFalseStillHidesTheBar() {
        assertTrue(decode("{\"hud_render\": {\"should_render\": false}}").hidden());
        assertTrue(decode("{\"hidden\": false, \"hud_render\": {\"should_render\": false}}").hidden());
        assertFalse(decode("{}").hidden());
    }

    @Test
    void textColourParsesLikeColourAndFallsBackToTheStockGrey() {
        assertEquals(DEFAULT_TEXT, decode("{}").textColor());
        assertEquals(0xFFFFFFFF, decode("{\"hud_render\": {\"text_color\": \"#FFFFFF\"}}").textColor());
        assertEquals(0x80112233, decode("{\"hud_render\": {\"text_color\": \"#80112233\"}}").textColor());
        assertEquals(DEFAULT_TEXT, decode("{\"hud_render\": {\"text_color\": \"#FFF\"}}").textColor());
    }

    @Test
    void textOutlineDefaultsOff() {
        assertFalse(decode("{}").textOutline());
        assertTrue(decode("{\"hud_render\": {\"text_outline\": true}}").textOutline());
    }

    @Test
    void textStylingSurvivesTheSyncPayload() {
        var entry = new SyncResourcePayload.Entry(5, 0, 10, "Heat", 0xFFFF0000, -1, -1, "", "", 0, 0,
            false, false, 0xFF00FF00, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncResourcePayload.STREAM_CODEC.encode(buf, new SyncResourcePayload(Map.of("test:bar", entry)));
        SyncResourcePayload back = SyncResourcePayload.STREAM_CODEC.decode(buf);
        assertEquals(entry, back.resources().get("test:bar"));
    }

    @Test
    void theShortEntryConstructorKeepsTheStockText() {
        var entry = new SyncResourcePayload.Entry(5, 0, 10, "Heat", 0xFFFF0000, -1, -1, "", "", 0, 0,
            false, false);
        assertEquals(DEFAULT_TEXT, entry.textColor());
        assertFalse(entry.textOutline());
    }
}
