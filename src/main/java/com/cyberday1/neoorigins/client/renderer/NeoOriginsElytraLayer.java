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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a vanilla elytra on the back of players whose flight power asks for wings via
 * {@code render_elytra} ({@code elytra_flight}, {@code natural_glide} or
 * {@code flight}), when they are NOT wearing a real equipped elytra (so we never
 * double up on vanilla's own {@code WingsLayer}).
 *
 * <p>{@code "flying"} draws only during a glide; {@code "always"} draws whenever the
 * power is active. {@code ElytraModel.setupAnim} already folds the wings against the
 * back when the render state is not fall-flying, so the standing pose needs no model
 * work here.
 *
 * <p><b>26.1 rewrite.</b> On 1.21.1 this subclassed vanilla {@code ElytraLayer} and
 * overrode {@code shouldRender}/{@code getElytraTexture}. On 26.1 vanilla's
 * {@code ElytraLayer} no longer exists — elytra render moved into
 * {@link net.minecraft.client.renderer.entity.layers.WingsLayer}, a render-state
 * layer whose sole {@code submit(...)} draws only when {@code state.chestEquipment}
 * carries an {@code Equippable} with a non-empty asset id. It has no
 * {@code shouldRender}/{@code getElytraTexture} to override. So this is a standalone
 * {@link RenderLayer} that owns its own {@link ElytraModel} (adult + baby) and draws
 * it directly through {@link #renderColoredCutoutModel} — the vanilla wing pose
 * ({@code elytraRotX/Y/Z}) is already computed on the render state, so
 * {@code ElytraModel.setupAnim(state)} reproduces vanilla's wing animation exactly.
 *
 * <p>The draw decision itself lives in {@link #shouldDrawWings}; {@code submit} only
 * reads the render-state inputs and hands them over. {@code RENDER_ELYTRA_ALWAYS_KEY}
 * is what carries the tri-state's third value into this entity-less layer, and exists
 * on this line only.
 *
 * <p>Registered via {@code EntityRenderersEvent.AddLayers} on the avatar renderer(s).
 * Client-side only.
 */
// hub: neoorigins/elytra-draw-gate.md
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
        Identifier texture = state.getRenderData(ClientElytraFlightState.ELYTRA_TEXTURE_KEY);
        if (!shouldDrawWings(state.isFallFlying,
                             Boolean.TRUE.equals(state.getRenderData(ClientElytraFlightState.RENDER_ELYTRA_KEY)),
                             Boolean.TRUE.equals(state.getRenderData(ClientElytraFlightState.RENDER_ELYTRA_ALWAYS_KEY)),
                             state.chestEquipment,
                             texture)) {
            return;
        }

        ElytraModel model = state.isBaby ? this.elytraBabyModel : this.elytraModel;
        model.setupAnim(state);

        poseStack.pushPose();
        // Match WingsLayer's slight back-offset so the power wings sit where vanilla's do.
        poseStack.translate(0.0F, 0.0F, 0.125F);
        renderColoredCutoutModel(model, texture, poseStack, submitNodeCollector, lightCoords, state, -1, 0);
        poseStack.popPose();
    }

    /**
     * The whole draw decision, as a pure function of the render-state inputs plus the
     * resolved texture. Kept static and side-effect-free so the nine-row contract can
     * be pinned by a unit test; {@link #submit} supplies the values and does nothing
     * else with them.
     *
     * <p>{@code chest} holding a real elytra means vanilla's {@code WingsLayer} is
     * already drawing — bail rather than double up. The test is the item, not the
     * EQUIPPABLE asset id {@code WingsLayer} enters on: every chestplate carries one,
     * and vanilla enters and then draws nothing, where bailing here draws nothing at
     * all. That was issue #130's second sentence.
     */
    static boolean shouldDrawWings(boolean isFallFlying, boolean renderFlag, boolean always,
                                   ItemStack chest, @Nullable Identifier texture) {
        if (chest.is(Items.ELYTRA)) return false;
        if (!renderFlag) return false;
        if (!isFallFlying && !always) return false;
        return texture != null;
    }
}
