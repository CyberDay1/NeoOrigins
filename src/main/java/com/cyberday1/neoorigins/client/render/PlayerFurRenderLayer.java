package com.cyberday1.neoorigins.client.render;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.client.ClientOriginFurCache;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/**
 * Vanilla player render layer that draws the GeckoLib fur cosmetic for the
 * player's currently-selected origin on top of their regular body model.
 *
 * <p>Phase 1 renders a static pose anchored at the player's feet. The pivot
 * structure inside the geo JSON is responsible for placing bones at the correct
 * visual offsets (ears on the head, tail from the back), not this class.</p>
 *
 * <p>Lookup priority for the origin id: the {@code neoorigins:origin} layer is
 * checked first, then {@code origins:origin} (the vanilla Origins compat layer),
 * and finally any other layer the player has a selection for. The first origin
 * with a fur entry in {@link ClientOriginFurCache} wins.</p>
 *
 * <p>Guarded by {@code @OnlyIn(Dist.CLIENT)}. Failures inside GeckoLib (missing
 * model, bad JSON, etc.) are caught and logged once per origin so a broken
 * asset can't crash the player renderer.</p>
 */
@OnlyIn(Dist.CLIENT)
public class PlayerFurRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation NEO_ORIGIN_LAYER =
        ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "origin");
    private static final ResourceLocation ORIGINS_ORIGIN_LAYER =
        ResourceLocation.fromNamespaceAndPath("origins", "origin");

    private final FurGeoModel model = new FurGeoModel();
    private final GeoObjectRenderer<FurAnimatable> renderer = new GeoObjectRenderer<>(this.model);

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
        // Phase 1: only fur the local player so we don't try to look up remote players' origins
        // through ClientOriginState (which only holds the local player's data).
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(player.getUUID())) return;

        ResourceLocation originId = resolveOriginId();
        if (originId == null) return;

        ClientOriginFurCache.Entry fur = ClientOriginFurCache.get(originId);
        if (fur == null) return;

        if (this.warnedOrigins.contains(originId)) return;

        poseStack.pushPose();
        try {
            // Anchor at the player's feet. The geo model's pivots were authored in
            // vanilla-player coordinates (y=0 at feet, y=24 at top of head), so no
            // additional translation is required here for Phase 1.
            this.model.setContext(fur.model(), fur.texture(), fur.animation());
            this.renderer.render(poseStack, FurAnimatable.INSTANCE, bufferSource, null, null,
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
     * Walk the local player's origin selections in priority order and return the
     * first origin id that has a fur entry. Returns {@code null} if no selection
     * on any layer yields a renderable fur.
     */
    private static ResourceLocation resolveOriginId() {
        var origins = ClientOriginState.getOrigins();
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
}
