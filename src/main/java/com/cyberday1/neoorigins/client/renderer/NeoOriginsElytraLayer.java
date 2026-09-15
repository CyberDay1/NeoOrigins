package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.client.ClientElytraFlightState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a vanilla elytra on the back of players whose flight power asks for wings via
 * {@code render_elytra} ({@code elytra_flight}, {@code natural_glide} or
 * {@code flight}), when they are NOT wearing a real equipped elytra (so we never
 * double up on vanilla's own {@link ElytraLayer}).
 *
 * <p>{@code "flying"} draws only during a glide; {@code "always"} draws whenever the
 * power is active. Vanilla's {@code ElytraModel.setupAnim} already folds the wings
 * against the back when the entity is not fall-flying, so the standing pose needs no
 * model work here.
 *
 * <p>Subclasses vanilla {@link ElytraLayer} to reuse its pose / {@code setupAnim} /
 * render entirely — we only override:
 * <ul>
 *   <li>{@link #shouldRender} — delegates to {@link #shouldDrawWings}, the pure
 *       static that owns the whole draw decision so it can be unit-tested.</li>
 *   <li>{@link #getElytraTexture} — swap in the power's custom texture when set,
 *       else fall through to the vanilla elytra texture.</li>
 * </ul>
 *
 * <p>Registered via {@code EntityRenderersEvent.AddLayers} on both the default and
 * slim player renderers. Client-side only.
 */
// hub: neoorigins/elytra-draw-gate.md
public class NeoOriginsElytraLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends ElytraLayer<T, M> {

    public NeoOriginsElytraLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent, models);
    }

    @Override
    public boolean shouldRender(ItemStack stack, T entity) {
        int id = entity.getId();
        return shouldDrawWings(entity.isFallFlying(),
                               ClientElytraFlightState.shouldRenderElytra(id),
                               ClientElytraFlightState.alwaysRendersElytra(id),
                               stack,
                               ClientElytraFlightState.textureFor(id));
    }

    /**
     * The whole draw decision, as a pure function of the four render inputs plus the
     * resolved texture. Kept static and side-effect-free so the nine-row contract can
     * be pinned by a unit test; {@link #shouldRender} supplies the values and does
     * nothing else.
     *
     * <p>{@code chest} holding a real elytra means vanilla's own {@link ElytraLayer}
     * is already drawing — bail rather than double up.
     */
    static boolean shouldDrawWings(boolean isFallFlying, boolean renderFlag, boolean always,
                                   ItemStack chest, @Nullable ResourceLocation texture) {
        if (chest.is(Items.ELYTRA)) return false;
        if (!renderFlag) return false;
        if (!isFallFlying && !always) return false;
        return texture != null;
    }

    @Override
    public ResourceLocation getElytraTexture(ItemStack stack, T entity) {
        // textureFor returns the vanilla texture when no custom one was set.
        return ClientElytraFlightState.textureFor(entity.getId());
    }
}
