package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.power.morph.MorphSkin;
import com.cyberday1.neoorigins.power.morph.MorphSpec;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns the synced {@link MorphSkin} of a player-skin morph into the concrete
 * {@link PlayerSkin} the renderer consumes, layered over whatever the player's
 * real skin resolved to.
 *
 * <p>Called from {@code AbstractClientPlayerSkinMixin}, which patches
 * {@code AbstractClientPlayer.getSkin()} itself. That is the whole hook: the
 * render dispatcher picks the slim-vs-wide renderer from {@code getSkin().model()},
 * and the body, cape and elytra layers all read the same object — so overriding
 * one method restyles everything, arm width included. Doing this from
 * {@code RenderPlayerEvent.Pre} instead would be too late, because the arm width
 * has already been decided by then.
 *
 * <p>Results are cached per player because {@code getSkin()} is called several
 * times per frame per visible player. The cache key is the pair (requested
 * morph skin, underlying real skin): either changing — the pack toggles a power,
 * or the player's actual skin finishes downloading — invalidates the entry on
 * its own, with no explicit reset needed.
 */
public final class MorphSkinResolver {

    private record Cached(MorphSkin request, PlayerSkin base, PlayerSkin result) {}

    private static final Map<Integer, Cached> CACHE = new ConcurrentHashMap<>();

    private MorphSkinResolver() {}

    /**
     * The skin a morphed player should be drawn with, or null when they carry
     * no skin morph and vanilla's answer stands.
     *
     * @param base what {@code getSkin()} was about to return
     */
    @Nullable
    public static PlayerSkin apply(int entityId, PlayerSkin base) {
        MorphSpec spec = ClientMorphState.getSpec(entityId);
        MorphSkin skin = spec == null ? null : spec.activeSkin().orElse(null);
        if (skin == null) {
            CACHE.remove(entityId);
            return null;
        }

        Cached cached = CACHE.get(entityId);
        if (cached != null && cached.request.equals(skin) && cached.base.equals(base)) {
            return cached.result;
        }

        PlayerSkin result = build(skin, base);
        CACHE.put(entityId, new Cached(skin, base, result));
        return result;
    }

    /**
     * Layer the morph's textures over the player's own skin. Anything the morph
     * leaves unset is inherited, so a cape-only morph doesn't blank the body and
     * a texture-only morph doesn't strip a real cape.
     */
    private static PlayerSkin build(MorphSkin skin, PlayerSkin base) {
        ResourceLocation texture = skin.texture().map(MorphSkin::texturePath).orElse(base.texture());
        ResourceLocation cape = skin.cape().map(MorphSkin::texturePath).orElse(base.capeTexture());
        ResourceLocation elytra = skin.elytra().map(MorphSkin::texturePath).orElse(base.elytraTexture());
        PlayerSkin.Model model = skin.model()
            .map(m -> MorphSkin.MODEL_SLIM.equals(m) ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE)
            .orElse(base.model());

        // The URL describes where the BODY texture came from, so it has to go
        // when we replace that texture — leaving it would claim our pack asset
        // was downloaded from the player's real skin URL.
        String url = skin.texture().isPresent() ? null : base.textureUrl();

        return new PlayerSkin(texture, url, cape, elytra, model, base.secure());
    }

    public static void clear() {
        CACHE.clear();
    }
}
