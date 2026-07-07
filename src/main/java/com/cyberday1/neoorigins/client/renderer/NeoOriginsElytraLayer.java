package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.client.ClientElytraFlightState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Draws a vanilla elytra on the back of players gliding via
 * {@code neoorigins:elytra_flight} with {@code render_elytra: true}, when they are
 * NOT wearing a real equipped elytra (so we never double up on vanilla's own
 * {@code WingsLayer}).
 *
 * <p><b>26.1 rewrite.</b> On 1.21.1 this subclassed vanilla {@code ElytraLayer} and
 * overrode {@code shouldRender}/{@code getElytraTexture}. On 26.1 vanilla's
 * {@code ElytraLayer} no longer exists — elytra render moved into
 * {@link net.minecraft.client.renderer.entity.layers.WingsLayer}, a render-state
 * layer whose sole {@code submit(...)} draws only when {@code state.chestEquipment}
 * carries an {@link Equippable} with a non-empty asset id. It has no
 * {@code shouldRender}/{@code getElytraTexture} to override. So this is a standalone
 * {@link RenderLayer} that owns its own {@link ElytraModel} (adult + baby) and draws
 * it directly through {@link #renderColoredCutoutModel} — the vanilla wing pose
 * ({@code elytraRotX/Y/Z}) is already computed on the render state, so
 * {@code ElytraModel.setupAnim(state)} reproduces vanilla's wing animation exactly.
 *
 * <p>Draw gate (mirrors the 1.21.1 semantics against render-state inputs):
 * <ul>
 *   <li>{@code state.isFallFlying} — only while actually gliding;</li>
 *   <li>the power's render flag on the state
 *       ({@link ClientElytraFlightState#RENDER_ELYTRA_KEY}, stamped from the
 *       server-synced {@link ClientElytraFlightState} by the avatar render-state
 *       modifier);</li>
 *   <li>NOT wearing a real equipped elytra — that case is {@code WingsLayer}'s job,
 *       detected the same way {@code WingsLayer} gates itself (EQUIPPABLE with a
 *       non-empty asset id in the chest slot).</li>
 * </ul>
 * The texture is taken from {@link ClientElytraFlightState#ELYTRA_TEXTURE_KEY}
 * (the vanilla elytra texture when no custom one was set).
 *
 * <p>Registered via {@code EntityRenderersEvent.AddLayers} on the avatar renderer(s).
 * Client-side only.
 */
public class NeoOriginsElytraLayer<S extends HumanoidRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    private final ElytraModel elytraModel;
    private final ElytraModel elytraBabyModel;

    public NeoOriginsElytraLayer(RenderLayerParent<S, M> parent, EntityModelSet models) {
        super(parent);
        this.elytraModel = new ElytraModel(models.bakeLayer(ModelLayers.ELYTRA));
        this.elytraBabyModel = new ElytraModel(models.bakeLayer(ModelLayers.ELYTRA_BABY));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                       S state, float yRot, float xRot) {
        if (!state.isFallFlying) return;
        if (!Boolean.TRUE.equals(state.getRenderData(ClientElytraFlightState.RENDER_ELYTRA_KEY))) return;
        // Real elytra equipped → vanilla's WingsLayer draws it; don't double up.
        if (hasRealElytra(state)) return;

        Identifier texture = state.getRenderData(ClientElytraFlightState.ELYTRA_TEXTURE_KEY);
        if (texture == null) return; // flag set but no texture stamped — nothing to draw

        ElytraModel model = state.isBaby ? this.elytraBabyModel : this.elytraModel;
        model.setupAnim(state);

        poseStack.pushPose();
        // Match WingsLayer's slight back-offset so the power wings sit where vanilla's do.
        poseStack.translate(0.0F, 0.0F, 0.125F);
        renderColoredCutoutModel(model, texture, poseStack, submitNodeCollector, lightCoords, state, -1, 0);
        poseStack.popPose();
    }

    /**
     * True when the render state's chest slot holds a real equippable elytra — the
     * same gate {@code WingsLayer} uses ({@link Equippable} present with a non-empty
     * asset id) — so we defer to vanilla instead of drawing a second pair of wings.
     */
    private static boolean hasRealElytra(HumanoidRenderState state) {
        ItemStack chest = state.chestEquipment;
        Equippable equippable = chest.get(DataComponents.EQUIPPABLE);
        return equippable != null && !equippable.assetId().isEmpty();
    }
}
