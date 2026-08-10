package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.client.ClientElytraFlightState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a vanilla elytra on the back of players gliding via any flight power set to
 * {@code render_elytra: true} ({@code elytra_flight}, {@code natural_glide} or
 * {@code flight}), when they are NOT wearing a real equipped elytra (so we never
 * double up on vanilla's own {@link ElytraLayer}).
 *
 * <p>Subclasses vanilla {@link ElytraLayer} to reuse its pose / {@code setupAnim} /
 * render entirely — we only override:
 * <ul>
 *   <li>{@link #shouldRender} — vanilla returns true only when a real elytra is in
 *       the chest slot; we instead render when the player is fall-flying, carries
 *       this power's render flag ({@link ClientElytraFlightState}), and is NOT
 *       wearing a real elytra (that case is vanilla's job).</li>
 *   <li>{@link #getElytraTexture} — swap in the power's custom texture when set,
 *       else fall through to the vanilla elytra texture.</li>
 * </ul>
 *
 * <p>Registered via {@code EntityRenderersEvent.AddLayers} on both the default and
 * slim player renderers. Client-side only.
 */
public class NeoOriginsElytraLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends ElytraLayer<T, M> {

    public NeoOriginsElytraLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent, models);
    }

    @Override
    public boolean shouldRender(ItemStack stack, T entity) {
        // Real elytra equipped → let vanilla's own ElytraLayer draw it; don't double up.
        if (super.shouldRender(stack, entity)) return false;
        return entity.isFallFlying()
            && ClientElytraFlightState.shouldRenderElytra(entity.getId());
    }

    @Override
    public ResourceLocation getElytraTexture(ItemStack stack, T entity) {
        // textureFor returns the vanilla texture when no custom one was set.
        return ClientElytraFlightState.textureFor(entity.getId());
    }
}
