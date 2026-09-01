package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.content.ModEntities;
import com.cyberday1.neoorigins.network.payload.ActivateClassPowerPayload;
import com.cyberday1.neoorigins.network.payload.ActivatePowerByKeyPayload;
import com.cyberday1.neoorigins.network.payload.ActivatePowerPayload;
import com.cyberday1.neoorigins.network.payload.AirJumpPayload;
import net.minecraft.client.KeyMapping;
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
    private static boolean wasUseDown = false;
    private static boolean wasAttackDown = false;

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

        if (NeoOriginsKeybindings.TOGGLE_NIGHT_VISION.consumeClick()) {
            // Pure flip request: the server owns the flag and echoes the result
            // back via SyncNightVisionPayload. We do NOT optimistically update
            // ClientNightVisionState here — a client that guessed wrong (e.g. the
            // server refused because an admin disabled night vision globally)
            // would show a brightness boost the server isn't granting.
            ClientPacketDistributor.sendToServer(
                new com.cyberday1.neoorigins.network.payload.ToggleNightVisionPayload());
        }

        // Named-hotkey pool: each pool slot is bound to a pack-declared translation
        // key via HotkeyAssignments. For non-continuous bindings we send on edge
        // (consumeClick); for continuous bindings we send every tick while held.
        // Server enforces the actual edge/continuous semantics — sending both
        // shapes here keeps the client dumb.
        KeyMapping[] pool = NeoOriginsKeybindings.HOTKEY_POOL;
        for (int i = 0; i < pool.length; i++) {
            String key = HotkeyAssignments.poolKey(i);
            if (key == null) continue;
            KeyMapping km = pool[i];
            boolean continuous = HotkeyAssignments.isContinuous(key);
            if (continuous) {
                // Drain any click events so consumeClick doesn't queue them for
                // a separate single-fire path while we're in continuous mode.
                while (km.consumeClick()) { /* discard */ }
                if (km.isDown()) {
                    ClientPacketDistributor.sendToServer(new ActivatePowerByKeyPayload(key, true));
                }
            } else {
                if (km.consumeClick()) {
                    ClientPacketDistributor.sendToServer(new ActivatePowerByKeyPayload(key, false));
                }
            }
        }

        // External (keybindjs-owned) mappings — same dispatch but using the
        // foreign KeyMapping instance instead of our pool slot.
        for (var entry : HotkeyAssignments.externalMappings().entrySet()) {
            KeyMapping km = entry.getKey();
            String key = entry.getValue();
            boolean continuous = HotkeyAssignments.isContinuous(key);
            if (continuous) {
                while (km.consumeClick()) { /* discard */ }
                if (km.isDown()) {
                    ClientPacketDistributor.sendToServer(new ActivatePowerByKeyPayload(key, true));
                }
            } else {
                if (km.consumeClick()) {
                    ClientPacketDistributor.sendToServer(new ActivatePowerByKeyPayload(key, false));
                }
            }
        }

        // Vanilla creative-toolbar keys (saveToolbarActivator / loadToolbarActivator).
        // Apoli packs bind active_self powers to these — typically several
        // condition-gated ritual powers on the same key (the Seer progression
        // rituals). The compat loader routes them through PowerKeybindRegistry so
        // every binding fires with its own condition; here we feed presses of the
        // REAL vanilla key into that same dispatch channel. We only send when the
        // pack actually declared a binding for the key, so we don't spam the
        // server on every toolbar press in creative.
        pollVanillaToolbarKey(Minecraft.getInstance().options.keySaveHotbarActivator,
            com.cyberday1.neoorigins.client.HotkeyAssignments.SAVE_TOOLBAR_KEY);
        pollVanillaToolbarKey(Minecraft.getInstance().options.keyLoadHotbarActivator,
            com.cyberday1.neoorigins.client.HotkeyAssignments.LOAD_TOOLBAR_KEY);

        // Stream the real held-state of the USE / ATTACK keys so compat
        // key.use / key.attack active_self powers fire on a genuine key hold —
        // not just when there's something under the crosshair to interact with
        // (the server's interaction-event / swing-flag proxies). Sent on edges
        // only; the server keeps the last reported state until the next change.
        boolean useDown = Minecraft.getInstance().options.keyUse.isDown();
        boolean attackDown = Minecraft.getInstance().options.keyAttack.isDown();
        if (useDown != wasUseDown || attackDown != wasAttackDown) {
            ClientPacketDistributor.sendToServer(
                new com.cyberday1.neoorigins.network.payload.VanillaKeyStatePayload(useDown, attackDown));
            wasUseDown = useDown;
            wasAttackDown = attackDown;
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

    /**
     * Feed presses of a vanilla creative-toolbar KeyMapping into the named-hotkey
     * dispatch channel, but only when the loaded pack actually declared a power on
     * that key (so non-Seer worlds never pay for the extra payloads, and a creative
     * player's normal hotbar save/load isn't shadowed). Continuous bindings send
     * every held tick; otherwise we edge-fire on consumeClick.
     */
    private static void pollVanillaToolbarKey(KeyMapping km, String translationKey) {
        if (!com.cyberday1.neoorigins.client.HotkeyAssignments.isToolbarKeyDeclared(translationKey)) {
            return;
        }
        boolean continuous = com.cyberday1.neoorigins.client.HotkeyAssignments.isContinuous(translationKey);
        if (continuous) {
            while (km.consumeClick()) { /* discard */ }
            if (km.isDown()) {
                ClientPacketDistributor.sendToServer(new ActivatePowerByKeyPayload(translationKey, true));
            }
        } else if (km.consumeClick()) {
            ClientPacketDistributor.sendToServer(new ActivatePowerByKeyPayload(translationKey, false));
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
        com.cyberday1.neoorigins.event.MorphHitboxEvents.clearAll();
        com.cyberday1.neoorigins.event.ForcedPoseEvents.clearAll();
        MorphRenderHandler.clearCache();
        MorphSkinResolver.clear();
        com.cyberday1.neoorigins.client.ClientInvisibilityArmorState.clear();
        com.cyberday1.neoorigins.client.ClientElytraFlightState.clear();
        // Entity ids are per-connection, so a surviving hidden set would blank
        // out unrelated entities in the next world.
        com.cyberday1.neoorigins.client.ClientHiddenEntities.clear();
        // Back to default-on so the next world starts bright until its own
        // sync lands; the persisted per-player value re-arrives at login.
        com.cyberday1.neoorigins.client.ClientNightVisionState.clear();
        // Drop the per-player Figura-facing state so a prior server's players
        // can't leak into the next session.
        com.cyberday1.neoorigins.client.ClientPlayerPowers.clear();
        // Drop named-hotkey assignments so a stale map can't fire the previous
        // server's powers on the next one.
        HotkeyAssignments.clear();
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

    /**
     * Adds the cosmetic elytra render layer to the avatar renderer(s). The layer
     * draws a vanilla elytra on the back of players gliding via any flight power
     * with render_elytra on, who aren't wearing a real equipped elytra. Fired on
     * the mod event bus (see NeoOrigins client setup).
     *
     * <p><b>26.1 port note.</b> The player renderer is now {@code AvatarRenderer}
     * and {@code AddLayers} exposes skins as {@code PlayerModelType} via
     * {@code getPlayerRenderer(...)} (was {@code PlayerSkin.Model} +
     * {@code getSkin(...)} on 1.21.1). We add the layer to the player renderer for
     * each model type, and to the matching mannequin renderer so posed mannequins
     * with the power also show the wings.
     */
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.world.entity.player.PlayerModelType skin : event.getSkins()) {
            var playerRenderer = event.getPlayerRenderer(skin);
            if (playerRenderer != null) {
                playerRenderer.addLayer(new com.cyberday1.neoorigins.client.renderer.NeoOriginsElytraLayer<>(
                    playerRenderer, event.getEntityModels()));
            }
            var mannequinRenderer = event.getMannequinRenderer(skin);
            if (mannequinRenderer != null) {
                mannequinRenderer.addLayer(new com.cyberday1.neoorigins.client.renderer.NeoOriginsElytraLayer<>(
                    mannequinRenderer, event.getEntityModels()));
            }
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
        event.registerEntityRenderer(ModEntities.PROJECTILE_RAIN.get(),
            com.cyberday1.neoorigins.client.renderer.ProjectileRainRenderer::new);
        event.registerEntityRenderer(ModEntities.TELEGRAPH.get(),
            com.cyberday1.neoorigins.client.renderer.TelegraphRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_SWORD.get(),
            com.cyberday1.neoorigins.client.renderer.ThrownSwordRenderer::new);
    }

    /**
     * Stamp the {@code neoorigins:invisibility} armor-hide flag onto each player's
     * render state so the entity-less {@code HumanoidArmorLayer} (26.1 renders from
     * a render state, not the live entity) can suppress worn armor for true
     * invisibility. The flag mirrors the server-synced
     * {@link ClientInvisibilityArmorState} set, keyed by entity id. We only set the
     * key when hiding is wanted — absence is the default the armor-layer mixin reads
     * as "render armor". Mod-bus, client only.
     */
    public static void onRegisterRenderStateModifiers(
            net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent event) {
        // 26.1: AvatarRenderer uses intersection-type generics that javac can't infer
        // from a raw Class<>; use the dedicated registerAvatarEntityModifier helper.
        event.registerAvatarEntityModifier(
            new net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier() {
                @Override
                public <T extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity>
                        void accept(T avatar, net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
                    if (ClientInvisibilityArmorState.shouldHideArmor(avatar.getId())) {
                        state.setRenderData(ClientInvisibilityArmorState.HIDE_ARMOR_KEY, Boolean.TRUE);
                    }
                }
            });

        // Stamp the neoorigins:elytra_flight cosmetic-wing flag + texture onto each
        // player's render state so the entity-less NeoOriginsElytraLayer (26.1 renders
        // from a render state) knows whether — and with what texture — to draw wings.
        // Mirrors the server-synced ClientElytraFlightState, keyed by entity id; we
        // only stamp when render is wanted (absence = no power wings).
        event.registerAvatarEntityModifier(
            new net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier() {
                @Override
                public <T extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity>
                        void accept(T avatar, net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
                    if (ClientElytraFlightState.shouldRenderElytra(avatar.getId())) {
                        state.setRenderData(ClientElytraFlightState.RENDER_ELYTRA_KEY, Boolean.TRUE);
                        state.setRenderData(ClientElytraFlightState.ELYTRA_TEXTURE_KEY,
                            ClientElytraFlightState.textureFor(avatar.getId()));
                    }
                }
            });
    }
}
