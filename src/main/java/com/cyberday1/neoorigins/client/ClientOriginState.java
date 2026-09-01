package com.cyberday1.neoorigins.client;

import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClientOriginState {

    private static Map<ResourceLocation, ResourceLocation> origins = new HashMap<>();
    private static boolean hadAllOrigins = false;

    public static void setOrigins(Map<ResourceLocation, ResourceLocation> newOrigins, boolean hadAll) {
        origins = new HashMap<>(newOrigins);
        hadAllOrigins = hadAll;
    }

    public static Map<ResourceLocation, ResourceLocation> getOrigins() {
        return Collections.unmodifiableMap(origins);
    }

    public static ResourceLocation getOrigin(ResourceLocation layerId) {
        return origins.get(layerId);
    }

    public static boolean isHadAllOrigins() { return hadAllOrigins; }

    public static void openSelectionScreen(boolean isOrb, boolean forceReselect) {
        openSelectionScreen(isOrb, forceReselect, java.util.List.of());
    }

    public static void openSelectionScreen(boolean isOrb, boolean forceReselect,
                                           java.util.List<ResourceLocation> scopedLayers) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // GRID is still a placeholder; swapping it in is a one-line change. No default
        // arm, so a new enum value fails the build. This switch must stay in this
        // client-package class: RuntimeDistCleaner walks NEW opcodes in common-side
        // classes, so a Screen reference from NeoOriginsNetwork crashes a dedicated
        // server on boot.
        mc.setScreen(switch (NeoOriginsClientConfig.pickerLayout()) {
            case TWO_PANEL -> new com.cyberday1.neoorigins.screen.OriginSelectionScreen(isOrb, forceReselect, scopedLayers);
            case GRID -> new com.cyberday1.neoorigins.screen.OriginSelectionScreen(isOrb, forceReselect, scopedLayers);
            case CAROUSEL -> new com.cyberday1.neoorigins.screen.OriginCarouselSelectionScreen(isOrb, forceReselect, scopedLayers);
        });
    }

    public static void openInfoScreen() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new com.cyberday1.neoorigins.screen.OriginInfoScreen());
    }

    /**
     * Trampoline used by {@code NeoOriginsNetwork.handleOpenEditorScreen} so the
     * `new OriginCreatorScreen(...)` opcode lives in this client-package class
     * instead of in NeoOriginsNetwork. RuntimeDistCleaner walks NEW opcodes in
     * common-side classes during dist verification — referencing a Screen
     * subclass directly from NeoOriginsNetwork forces a Screen class load on
     * dedicated server and the boot crashes. Routing through this method keeps
     * the constant-pool reference in client-side code only.
     *
     * <p>2.1: opens the tabbed {@code OriginCreatorScreen} with a fresh draft
     * (editing an existing origin lands in a later phase). The old
     * {@code OriginEditorScreen} is retained in-tree but no longer opened.
     */
    public static void openEditorScreen() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new com.cyberday1.neoorigins.screen.creator.OriginCreatorScreen(
            null, new com.cyberday1.neoorigins.screen.creator.model.OriginDraft()));
    }
}
