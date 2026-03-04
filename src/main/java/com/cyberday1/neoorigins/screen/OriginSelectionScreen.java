package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class OriginSelectionScreen extends Screen {

    private final boolean isOrb;
    private List<OriginLayer> pendingLayers = new ArrayList<>();
    private int currentLayerIndex = 0;
    private ResourceLocation selectedOrigin = null;
    private final List<OriginButton> originButtons = new ArrayList<>();

    private Button confirmButton;
    private Button backButton;
    private Button randomButton;

    private int scrollOffset = 0;
    private static final int BUTTON_HEIGHT = 40;
    private static final int BUTTON_SPACING = 4;
    private static final int VISIBLE_BUTTONS = 4;

    public OriginSelectionScreen(boolean isOrb) {
        super(Component.translatable("screen.neoorigins.choose_origin"));
        this.isOrb = isOrb;
    }

    @Override
    protected void init() {
        // Find layers without an origin chosen yet
        pendingLayers = LayerDataManager.INSTANCE.getSortedLayers().stream()
            .filter(layer -> !layer.hidden())
            .filter(layer -> !ClientOriginState.getOrigins().containsKey(layer.id()))
            .toList();

        if (pendingLayers.isEmpty()) {
            onClose();
            return;
        }

        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        originButtons.clear();
        selectedOrigin = null;

        if (currentLayerIndex >= pendingLayers.size()) {
            onClose();
            return;
        }

        OriginLayer layer = pendingLayers.get(currentLayerIndex);
        int panelWidth = Math.min(width - 40, 340);
        int panelX = (width - panelWidth) / 2;
        int panelY = 50;

        int btnY = panelY;
        for (var condOrigin : layer.origins()) {
            var origin = OriginDataManager.INSTANCE.getOrigin(condOrigin.origin());
            if (origin == null) continue;
            var btn = new OriginButton(panelX, btnY, panelWidth, BUTTON_HEIGHT, origin, b -> {
                selectOrigin(condOrigin.origin());
            });
            originButtons.add(btn);
            addRenderableWidget(btn);
            btnY += BUTTON_HEIGHT + BUTTON_SPACING;
        }

        // Random button
        randomButton = Button.builder(Component.translatable("button.neoorigins.random"), b -> {
            if (!layer.allowRandom()) return;
            List<ResourceLocation> ids = layer.getAvailableOriginIds();
            if (!ids.isEmpty()) {
                int idx = (int) (Math.random() * ids.size());
                selectOrigin(ids.get(idx));
            }
        }).bounds(20, height - 30, 80, 20).build();
        if (layer.allowRandom()) addRenderableWidget(randomButton);

        // Back button
        backButton = Button.builder(Component.literal("< Back"), b -> {
            if (currentLayerIndex > 0) { currentLayerIndex--; rebuildButtons(); }
        }).bounds(width / 2 - 100, height - 30, 90, 20).build();
        addRenderableWidget(backButton);
        backButton.active = currentLayerIndex > 0;

        // Confirm button
        confirmButton = Button.builder(Component.literal("Confirm >"), b -> confirmSelection()).
            bounds(width / 2 + 10, height - 30, 90, 20).build();
        addRenderableWidget(confirmButton);
        confirmButton.active = false;
    }

    private void selectOrigin(ResourceLocation id) {
        selectedOrigin = id;
        originButtons.forEach(btn -> btn.setSelected(btn.getOrigin().id().equals(id)));
        if (confirmButton != null) confirmButton.active = true;
    }

    private void confirmSelection() {
        if (selectedOrigin == null || currentLayerIndex >= pendingLayers.size()) return;
        OriginLayer layer = pendingLayers.get(currentLayerIndex);

        // Send choice to server
        PacketDistributor.sendToServer(new ChooseOriginPayload(layer.id(), selectedOrigin));

        // Update local state optimistically
        var currentOrigins = new java.util.HashMap<>(ClientOriginState.getOrigins());
        currentOrigins.put(layer.id(), selectedOrigin);
        ClientOriginState.setOrigins(currentOrigins, false);

        currentLayerIndex++;
        if (currentLayerIndex >= pendingLayers.size()) {
            onClose();
        } else {
            rebuildButtons();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        if (currentLayerIndex >= pendingLayers.size()) return;
        OriginLayer layer = pendingLayers.get(currentLayerIndex);

        // Title
        graphics.drawCenteredString(font, getTitle(), width / 2, 15, 0xFFFFFF);
        // Layer name
        graphics.drawCenteredString(font, layer.name(), width / 2, 28, 0xCCCCCC);
        // Progress
        String progress = (currentLayerIndex + 1) + " / " + pendingLayers.size();
        graphics.drawString(font, progress, width - 20 - font.width(progress), 28, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
