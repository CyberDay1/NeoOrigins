package com.cyberday1.neoorigins.client.render;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.client.ClientOriginFurCache;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.client.RemoteOriginCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

import java.util.Map;

/**
 * Vanilla player render layer that draws the GeckoLib fur cosmetic for each
 * rendered player's currently-selected origin on top of their regular body
 * model.
 *
 * <p>Origin resolution is per-player: for the local player we read from
 * {@link ClientOriginState}; for remote players we read from
 * {@link RemoteOriginCache}, populated by the server's
 * {@code SyncRemoteOriginsPayload} broadcasts.</p>
 *
 * <p>Before each render the layer walks the baked bone tree and toggles bone
 * visibility based on two rules:</p>
 * <ul>
 *   <li><b>Armor slot convention</b>: bones named {@code head_fur_*},
 *       {@code chest_fur_*}, {@code legs_fur_*} or {@code feet_fur_*} are
 *       hidden when the corresponding armor slot on the rendered player has a
 *       non-empty item (so helmets don't clip through ears, etc.). Bones
 *       outside these prefixes — tails, wings — render unconditionally.</li>
 *   <li><b>First-person culling</b>: when the rendered player is the local
 *       player and the camera is in first-person view, {@code head_fur_*}
 *       bones are force-hidden so head geometry doesn't obstruct the
 *       screen.</li>
 * </ul>
 *
 * <p>Guarded by {@code @OnlyIn(Dist.CLIENT)}. Failures inside GeckoLib
 * (missing model, bad JSON, etc.) are caught and logged once per origin so a
 * broken asset can't crash the player renderer.</p>
 */
@OnlyIn(Dist.CLIENT)
public class PlayerFurRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation NEO_ORIGIN_LAYER =
        ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "origin");
    private static final ResourceLocation ORIGINS_ORIGIN_LAYER =
        ResourceLocation.fromNamespaceAndPath("origins", "origin");

    private static final String HEAD_FUR_PREFIX = "head_fur_";
    private static final String CHEST_FUR_PREFIX = "chest_fur_";
    private static final String LEGS_FUR_PREFIX = "legs_fur_";
    private static final String FEET_FUR_PREFIX = "feet_fur_";

    private final FurGeoModel model = new FurGeoModel();
    private final GeoObjectRenderer<FurAnimatable> renderer = new GeoObjectRenderer<>(this.model);

    /**
     * One {@link FurAnimatable} per player UUID so every player's animation
     * controller has its own phase + blend state. A shared singleton would
     * smear state across every rendered cat.
     *
     * <p>Static because the render layer is instantiated per player model
     * variant ({@code DEFAULT} and {@code SLIM}); both variants must share
     * animatables so a skin change doesn't reset the animation phase.
     * Cleared on disconnect by {@link NeoOriginsClientEvents}.</p>
     */
    private static final java.util.Map<java.util.UUID, FurAnimatable> ANIMATABLES =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Cleanup hook for disconnect. */
    public static void clearAnimatables() {
        ANIMATABLES.clear();
    }

    /**
     * Tracks origins whose assets have already failed to load, so we log the
     * failure exactly once rather than spamming on every frame.
     */
    private final java.util.Set<ResourceLocation> warnedOrigins = new java.util.HashSet<>();

    public PlayerFurRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;

        ResourceLocation originId = resolveOriginId(player);
        if (originId == null) return;

        ClientOriginFurCache.Entry fur = ClientOriginFurCache.get(originId);
        if (fur == null) return;

        if (this.warnedOrigins.contains(originId)) return;

        FurAnimatable animatable = ANIMATABLES.computeIfAbsent(
            player.getUUID(), uuid -> new FurAnimatable());
        animatable.setCurrentPlayer(player);

        poseStack.pushPose();
        try {
            // Anchor at the player's feet. The geo model's pivots were authored in
            // vanilla-player coordinates (y=0 at feet, y=24 at top of head), so no
            // additional translation is required here for Phase 1.
            this.model.setContext(fur.model(), fur.texture(), fur.animation());
            applyBoneVisibility(player, fur.model());
            this.renderer.render(poseStack, animatable, bufferSource, null, null,
                packedLight, partialTick);
        } catch (Throwable t) {
            NeoOrigins.LOGGER.warn("Failed to render fur for origin {} on player {}: {}",
                originId, player.getName().getString(), t.toString());
            this.warnedOrigins.add(originId);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Resolve the origin id for the player being rendered. The local player's
     * selections live in {@link ClientOriginState}; everyone else comes from
     * {@link RemoteOriginCache} which the server populates over the network.
     */
    private static ResourceLocation resolveOriginId(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        Map<ResourceLocation, ResourceLocation> origins;
        if (mc.player != null && mc.player.getUUID().equals(player.getUUID())) {
            origins = ClientOriginState.getOrigins();
        } else {
            origins = RemoteOriginCache.get(player.getUUID());
        }
        return pickOriginWithFur(origins);
    }

    /**
     * Walk a player's origin selections in priority order and return the first
     * origin id that has a fur entry. Returns {@code null} if no selection on
     * any layer yields a renderable fur.
     */
    private static ResourceLocation pickOriginWithFur(Map<ResourceLocation, ResourceLocation> origins) {
        if (origins.isEmpty()) return null;

        ResourceLocation preferred = origins.get(NEO_ORIGIN_LAYER);
        if (preferred != null && ClientOriginFurCache.get(preferred) != null) return preferred;

        ResourceLocation compat = origins.get(ORIGINS_ORIGIN_LAYER);
        if (compat != null && ClientOriginFurCache.get(compat) != null) return compat;

        for (var entry : origins.entrySet()) {
            if (entry.getKey().equals(NEO_ORIGIN_LAYER) || entry.getKey().equals(ORIGINS_ORIGIN_LAYER)) continue;
            if (ClientOriginFurCache.get(entry.getValue()) != null) return entry.getValue();
        }
        return null;
    }

    /**
     * Apply the armor-slot and first-person visibility rules to every bone in
     * the baked model. Called each frame just before rendering because the
     * model instance is shared across all players and any prior-frame state
     * would leak.
     */
    private void applyBoneVisibility(AbstractClientPlayer player, ResourceLocation modelId) {
        BakedGeoModel baked = this.model.getBakedModel(modelId);
        if (baked == null) return;

        Minecraft mc = Minecraft.getInstance();
        boolean firstPersonLocal = mc.player != null
            && mc.player.getUUID().equals(player.getUUID())
            && mc.options.getCameraType().isFirstPerson();

        boolean hideHead = firstPersonLocal
            || !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean hideChest = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean hideLegs = !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        boolean hideFeet = !player.getItemBySlot(EquipmentSlot.FEET).isEmpty();

        for (GeoBone bone : baked.topLevelBones()) {
            applyBoneVisibilityRecursive(bone, hideHead, hideChest, hideLegs, hideFeet);
        }
    }

    private static void applyBoneVisibilityRecursive(GeoBone bone,
                                                      boolean hideHead, boolean hideChest,
                                                      boolean hideLegs, boolean hideFeet) {
        String name = bone.getName();
        if (name.startsWith(HEAD_FUR_PREFIX)) {
            bone.setHidden(hideHead);
        } else if (name.startsWith(CHEST_FUR_PREFIX)) {
            bone.setHidden(hideChest);
        } else if (name.startsWith(LEGS_FUR_PREFIX)) {
            bone.setHidden(hideLegs);
        } else if (name.startsWith(FEET_FUR_PREFIX)) {
            bone.setHidden(hideFeet);
        } else {
            // Non-armor bones (tails, wings, root anchors) always render; reset
            // in case a prior frame or another model hid them.
            bone.setHidden(false);
        }
        for (GeoBone child : bone.getChildBones()) {
            applyBoneVisibilityRecursive(child, hideHead, hideChest, hideLegs, hideFeet);
        }
    }
}
