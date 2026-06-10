package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.content.ModEntities;
import com.cyberday1.neoorigins.network.payload.ActivateClassPowerPayload;
import com.cyberday1.neoorigins.network.payload.ActivatePowerPayload;
import com.cyberday1.neoorigins.network.payload.AirJumpPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public class NeoOriginsClientEvents {

    private static boolean wasJumping = false;

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;

        ClientCooldownState.tick();

        if (NeoOriginsKeybindings.SKILL_1.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(0));
        if (NeoOriginsKeybindings.SKILL_2.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(1));
        if (NeoOriginsKeybindings.SKILL_3.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(2));
        if (NeoOriginsKeybindings.SKILL_4.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(3));
        if (NeoOriginsKeybindings.SKILL_5.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(4));
        if (NeoOriginsKeybindings.SKILL_6.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(5));
        if (NeoOriginsKeybindings.CLASS_SKILL.consumeClick()) ClientPacketDistributor.sendToServer(new ActivateClassPowerPayload());
        if (NeoOriginsKeybindings.VIEW_INFO.consumeClick()) {
            // If the player never finished the origin picker (Escape'd out, died
            // before committing, etc.), the info screen has nothing to show.
            // Re-open the selector instead so they can complete selection.
            if (!ClientOriginState.isHadAllOrigins()) {
                ClientOriginState.openSelectionScreen(false, false);
            } else {
                ClientOriginState.openInfoScreen();
            }
        }

        if (NeoOriginsKeybindings.EDIT_HUD.consumeClick()) {
            Minecraft.getInstance().setScreen(new ResourceHudEditorScreen());
        }

        if (NeoOriginsKeybindings.OPEN_CREATOR.consumeClick()) {
            // Server gates access and replies with OpenEditorScreenPayload;
            // we never open the creator client-side directly.
            ClientPacketDistributor.sendToServer(
                new com.cyberday1.neoorigins.network.payload.RequestOpenCreatorPayload());
        }

        if (NeoOriginsKeybindings.OPEN_MOB_CREATOR.consumeClick()) {
            ClientPacketDistributor.sendToServer(
                new com.cyberday1.neoorigins.network.payload.RequestOpenMobCreatorPayload());
        }

        // Detect jump press while airborne for flight power activation
        boolean jumpHeld = Minecraft.getInstance().options.keyJump.isDown();
        boolean jumpPressed = jumpHeld && !wasJumping;
        wasJumping = jumpHeld;

        // Rising-edge detection (jumpPressed = jumpHeld && !wasJumping) is the
        // only debounce we need: a player physically cannot produce a fresh
        // rising edge faster than they can release + re-press, and we sample
        // once per tick. The previous ms-based self-cooldown (500 ms, later
        // 100 ms) dropped the elytra-start press if it landed inside that
        // window — felt as a "delay" before glide kicked in. Removed entirely.
        if (jumpPressed && !player.onGround() && !player.isInWater()
                && !player.isFallFlying() && !player.isPassenger()) {
            ClientPacketDistributor.sendToServer(new AirJumpPayload());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        // Drop any datapack-declared theme so leaving a server doesn't leak its
        // selection into the next world / main-menu screens.
        com.cyberday1.neoorigins.client.theme.ActiveThemeRegistry.clearServerDeclared();
        // Drop the cached template bundle — it's keyed to the server's loaded
        // origins, not to anything persistent on the client.
        ClientTemplateCache.clear();
        // Drop morph state + cached dummy entities so they don't leak across
        // a disconnect/reconnect within the same client JVM.
        ClientMorphState.clear();
        MorphRenderHandler.clearCache();
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // Re-add the "Edit HUD" button to the pause (Esc) menu. Only show it when
        // the player actually has resource bars to lay out — otherwise the editor
        // has nothing to edit.
        if (!(event.getScreen() instanceof PauseScreen pause)) return;
        if (ClientResourceState.getResources().isEmpty()) return;

        // Tuck it in the top-left corner so it doesn't fight the centered vanilla
        // button column.
        Button btn = Button.builder(
                Component.translatable("button.neoorigins.edit_hud"),
                b -> Minecraft.getInstance().setScreen(new ResourceHudEditorScreen()))
            .bounds(8, 8, 80, 20)
            .build();
        event.addListener(btn);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.COBWEB_PROJECTILE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.HOMING_PROJECTILE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.MAGIC_ORB.get(),
            com.cyberday1.neoorigins.client.renderer.MagicOrbRenderer::new);
        event.registerEntityRenderer(ModEntities.LINGERING_AREA.get(),
            com.cyberday1.neoorigins.client.renderer.LingeringAreaRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACK_HOLE.get(),
            com.cyberday1.neoorigins.client.renderer.BlackHoleRenderer::new);
        event.registerEntityRenderer(ModEntities.TORNADO.get(),
            com.cyberday1.neoorigins.client.renderer.TornadoRenderer::new);
    }
}
