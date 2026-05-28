package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.content.ModEntities;
import com.cyberday1.neoorigins.network.payload.ActivateClassPowerPayload;
import com.cyberday1.neoorigins.network.payload.ActivatePowerByKeyPayload;
import com.cyberday1.neoorigins.network.payload.ActivatePowerPayload;
import com.cyberday1.neoorigins.network.payload.AirJumpPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public class NeoOriginsClientEvents {

    private static boolean wasJumping = false;
    /** Monotonic timestamp (ms) when the last air jump packet was sent — prevents infinite re-activation.
     *  Uses {@code Util.getMillis()} instead of {@code player.tickCount} because the client creates
     *  a new LocalPlayer (with tickCount=0) on dimension change, while a static field keeps the old
     *  value — making {@code (0 - oldTick) > 10} false and silently blocking all flight activation
     *  until tickCount catches up (minutes). Monotonic ms never resets. */
    private static long lastAirJumpMs = 0;

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        ClientCooldownState.tick();

        if (NeoOriginsKeybindings.SKILL_1.consumeClick()) PacketDistributor.sendToServer(new ActivatePowerPayload(0));
        if (NeoOriginsKeybindings.SKILL_2.consumeClick()) PacketDistributor.sendToServer(new ActivatePowerPayload(1));
        if (NeoOriginsKeybindings.SKILL_3.consumeClick()) PacketDistributor.sendToServer(new ActivatePowerPayload(2));
        if (NeoOriginsKeybindings.SKILL_4.consumeClick()) PacketDistributor.sendToServer(new ActivatePowerPayload(3));
        if (NeoOriginsKeybindings.SKILL_5.consumeClick()) PacketDistributor.sendToServer(new ActivatePowerPayload(4));
        if (NeoOriginsKeybindings.SKILL_6.consumeClick()) PacketDistributor.sendToServer(new ActivatePowerPayload(5));
        if (NeoOriginsKeybindings.CLASS_SKILL.consumeClick()) PacketDistributor.sendToServer(new ActivateClassPowerPayload());
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
            PacketDistributor.sendToServer(
                new com.cyberday1.neoorigins.network.payload.RequestOpenCreatorPayload());
        }

        if (NeoOriginsKeybindings.OPEN_MOB_CREATOR.consumeClick()) {
            PacketDistributor.sendToServer(
                new com.cyberday1.neoorigins.network.payload.RequestOpenMobCreatorPayload());
        }

        // Named-hotkey pool: each pool slot is bound to a pack-declared translation
        // key via HotkeyAssignments. For non-continuous bindings we send on edge
        // (consumeClick); for continuous bindings we send every tick while held.
        // Server enforces the actual edge/continuous semantics — sending both
        // shapes here keeps the client dumb.
        KeyMapping[] pool = NeoOriginsKeybindings.HOTKEY_POOL;
        for (int i = 0; i < pool.length; i++) {
            String key = com.cyberday1.neoorigins.client.HotkeyAssignments.poolKey(i);
            if (key == null) continue;
            KeyMapping km = pool[i];
            boolean continuous = com.cyberday1.neoorigins.client.HotkeyAssignments.isContinuous(key);
            if (continuous) {
                // Drain any click events so consumeClick doesn't queue them for
                // a separate single-fire path while we're in continuous mode.
                while (km.consumeClick()) { /* discard */ }
                if (km.isDown()) {
                    PacketDistributor.sendToServer(new ActivatePowerByKeyPayload(key, true));
                }
            } else {
                if (km.consumeClick()) {
                    PacketDistributor.sendToServer(new ActivatePowerByKeyPayload(key, false));
                }
            }
        }

        // External (keybindjs-owned) mappings — same dispatch but using the
        // foreign KeyMapping instance instead of our pool slot.
        for (var entry : com.cyberday1.neoorigins.client.HotkeyAssignments.externalMappings().entrySet()) {
            KeyMapping km = entry.getKey();
            String key = entry.getValue();
            boolean continuous = com.cyberday1.neoorigins.client.HotkeyAssignments.isContinuous(key);
            if (continuous) {
                while (km.consumeClick()) { /* discard */ }
                if (km.isDown()) {
                    PacketDistributor.sendToServer(new ActivatePowerByKeyPayload(key, true));
                }
            } else {
                if (km.consumeClick()) {
                    PacketDistributor.sendToServer(new ActivatePowerByKeyPayload(key, false));
                }
            }
        }

        // Detect jump press while airborne for flight power activation
        boolean jumpHeld = Minecraft.getInstance().options.keyJump.isDown();
        boolean jumpPressed = jumpHeld && !wasJumping;
        wasJumping = jumpHeld;

        if (jumpPressed && !player.onGround() && !player.isInWater()
                && !player.isFallFlying() && !player.isPassenger()
                && (net.minecraft.Util.getMillis() - lastAirJumpMs) > 500) {
            lastAirJumpMs = net.minecraft.Util.getMillis();
            PacketDistributor.sendToServer(new AirJumpPayload());
        }
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
