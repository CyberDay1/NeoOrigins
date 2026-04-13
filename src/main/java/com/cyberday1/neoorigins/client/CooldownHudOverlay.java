package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public class CooldownHudOverlay {

    private static final int BAR_W = 20;
    private static final int BAR_H = 3;
    private static final int BAR_GAP = 6;
    private static final int BAR_BG = 0xFF1A1A30;
    private static final int BAR_FILL = 0xFF4A90D9;
    private static final int LABEL_COLOR = 0xFF9999BB;
    private static final String[] SLOT_LABELS = {"S1", "S2", "S3", "S4", "C"};

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        var cooldowns = ClientCooldownState.getCooldowns();
        if (cooldowns.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphicsExtractor g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Position above hotbar center
        int totalSlots = cooldowns.size();
        int totalW = totalSlots * BAR_W + (totalSlots - 1) * BAR_GAP;
        int startX = (screenW - totalW) / 2;
        int y = screenH - 52;

        int idx = 0;
        for (var entry : cooldowns.entrySet()) {
            int slot = entry.getKey();
            var cd = entry.getValue();

            int x = startX + idx * (BAR_W + BAR_GAP);

            // Label
            String label = (slot >= 0 && slot < 4) ? SLOT_LABELS[slot] : SLOT_LABELS[4];
            int labelW = mc.font.width(label);
            g.text(mc.font, label, x + (BAR_W - labelW) / 2, y - 10, LABEL_COLOR, false);

            // Background bar
            g.fill(x, y, x + BAR_W, y + BAR_H, BAR_BG);

            // Fill bar
            float progress = (float) cd.remainingTicks() / cd.totalTicks();
            int fillW = Math.round(BAR_W * progress);
            if (fillW > 0) {
                g.fill(x, y, x + fillW, y + BAR_H, BAR_FILL);
            }

            idx++;
        }
    }
}
