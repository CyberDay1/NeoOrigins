package com.cyberday1.neoorigins.network.payload;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.client.ClientPowerCache;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Syncs the full origin/layer/power registry from server to client.
 * Sent on login and before opening the origin selection screen so that
 * dedicated-server clients have the data needed to render the GUI.
 */
public record SyncOriginRegistryPayload(
    Map<ResourceLocation, Origin> origins,
    List<OriginLayer> sortedLayers,
    Map<ResourceLocation, ClientPowerCache.Entry> powers,
    Map<ResourceLocation, List<ResourceLocation>> multipleExpansionMap,
    Map<ResourceLocation, JsonObject> multipleDisplayMap
) implements CustomPacketPayload {

    public static final Type<SyncOriginRegistryPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_origin_registry"));

    public static final StreamCodec<FriendlyByteBuf, SyncOriginRegistryPayload> STREAM_CODEC =
        StreamCodec.of(SyncOriginRegistryPayload::encode, SyncOriginRegistryPayload::decode);

    private static final Gson GSON = new Gson();

    // ── Encoding ─────────────────────────────────────────────────────────────

    private static void encode(FriendlyByteBuf buf, SyncOriginRegistryPayload payload) {
        // Origins
        buf.writeVarInt(payload.origins.size());
        for (var entry : payload.origins.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            JsonElement json = Origin.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue())
                .result().orElseThrow();
            buf.writeUtf(GSON.toJson(json), 65536);
        }

        // Sorted layers
        buf.writeVarInt(payload.sortedLayers.size());
        for (OriginLayer layer : payload.sortedLayers) {
            JsonElement json = OriginLayer.CODEC.encodeStart(JsonOps.INSTANCE, layer)
                .result().orElseThrow();
            buf.writeUtf(GSON.toJson(json), 65536);
        }

        // Power display info
        buf.writeVarInt(payload.powers.size());
        for (var entry : payload.powers.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            writeComponent(buf, entry.getValue().name());
            writeComponent(buf, entry.getValue().description());
            buf.writeBoolean(entry.getValue().active());
            buf.writeBoolean(entry.getValue().toggle());
        }

        // Multiple expansion map
        buf.writeVarInt(payload.multipleExpansionMap.size());
        for (var entry : payload.multipleExpansionMap.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (ResourceLocation subId : entry.getValue()) {
                buf.writeResourceLocation(subId);
            }
        }

        // Multiple display map
        buf.writeVarInt(payload.multipleDisplayMap.size());
        for (var entry : payload.multipleDisplayMap.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeUtf(GSON.toJson(entry.getValue()), 65536);
        }
    }

    // ── Decoding ─────────────────────────────────────────────────────────────

    private static SyncOriginRegistryPayload decode(FriendlyByteBuf buf) {
        // Origins
        int originCount = buf.readVarInt();
        Map<ResourceLocation, Origin> origins = new HashMap<>(originCount);
        for (int i = 0; i < originCount; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String json = buf.readUtf(65536);
            Origin.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .resultOrPartial(err -> {})
                .ifPresent(origin -> origins.put(id, origin));
        }

        // Sorted layers
        int layerCount = buf.readVarInt();
        List<OriginLayer> sortedLayers = new ArrayList<>(layerCount);
        for (int i = 0; i < layerCount; i++) {
            String json = buf.readUtf(65536);
            OriginLayer.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .resultOrPartial(err -> {})
                .ifPresent(sortedLayers::add);
        }

        // Power display info
        int powerCount = buf.readVarInt();
        Map<ResourceLocation, ClientPowerCache.Entry> powers = new HashMap<>(powerCount);
        for (int i = 0; i < powerCount; i++) {
            ResourceLocation id = buf.readResourceLocation();
            Component name = readComponent(buf);
            Component desc = readComponent(buf);
            boolean active = buf.readBoolean();
            boolean toggle = buf.readBoolean();
            powers.put(id, new ClientPowerCache.Entry(name, desc, active, toggle));
        }

        // Multiple expansion map
        int expansionCount = buf.readVarInt();
        Map<ResourceLocation, List<ResourceLocation>> multipleExpansionMap = new HashMap<>(expansionCount);
        for (int i = 0; i < expansionCount; i++) {
            ResourceLocation parentId = buf.readResourceLocation();
            int subCount = buf.readVarInt();
            List<ResourceLocation> subIds = new ArrayList<>(subCount);
            for (int j = 0; j < subCount; j++) {
                subIds.add(buf.readResourceLocation());
            }
            multipleExpansionMap.put(parentId, subIds);
        }

        // Multiple display map
        int displayCount = buf.readVarInt();
        Map<ResourceLocation, JsonObject> multipleDisplayMap = new HashMap<>(displayCount);
        for (int i = 0; i < displayCount; i++) {
            ResourceLocation parentId = buf.readResourceLocation();
            String json = buf.readUtf(65536);
            JsonElement el = JsonParser.parseString(json);
            if (el.isJsonObject()) multipleDisplayMap.put(parentId, el.getAsJsonObject());
        }

        return new SyncOriginRegistryPayload(origins, sortedLayers, powers, multipleExpansionMap, multipleDisplayMap);
    }

    // ── Component helpers ────────────────────────────────────────────────────

    private static void writeComponent(FriendlyByteBuf buf, Component comp) {
        if (comp.getContents() instanceof TranslatableContents tc) {
            buf.writeByte(1);
            buf.writeUtf(tc.getKey());
        } else if (comp.getContents() instanceof PlainTextContents ptc) {
            String text = ptc.text();
            if (text.isEmpty()) {
                buf.writeByte(0);
            } else {
                buf.writeByte(2);
                buf.writeUtf(text);
            }
        } else {
            // Fallback: resolve to literal
            String text = comp.getString();
            if (text.isEmpty()) {
                buf.writeByte(0);
            } else {
                buf.writeByte(2);
                buf.writeUtf(text);
            }
        }
    }

    private static Component readComponent(FriendlyByteBuf buf) {
        byte type = buf.readByte();
        return switch (type) {
            case 1 -> Component.translatable(buf.readUtf());
            case 2 -> Component.literal(buf.readUtf());
            default -> Component.empty();
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
