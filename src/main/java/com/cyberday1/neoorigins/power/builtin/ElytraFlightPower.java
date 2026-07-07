package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * Native {@code neoorigins:elytra_flight} power — the mirror of Apoli's
 * {@code apoli:elytra_flight}: grants elytra-style fall-flight without an
 * equipped elytra, and optionally draws a vanilla elytra on the player's back
 * during flight (with an optional custom texture).
 *
 * <p><b>Flight reuses natural glide.</b> This power emits the {@code natural_glide}
 * capability tag, so it drives the EXACT existing glide activation path
 * ({@code LocalPlayerNaturalGlideMixin} client-side, {@code PlayerStartFallFlyingMixin}
 * server-side) — no new flight code. Flight works whether or not the wings are
 * drawn; {@code render_elytra} is purely cosmetic.
 *
 * <p><b>Getting render info to the client.</b> {@code ClientActivePowers} only
 * mirrors the local player's capability STRINGS, not full power configs, and the
 * wings must draw on every viewer's screen (not just the flyer's own). So — exactly
 * like {@code neoorigins:invisibility} carries {@code render_armor:false} as the
 * {@code invisibility_hide_armor} tag — this power ENCODES its render state into the
 * capability set:
 * <ul>
 *   <li>{@link #CAP_RENDER_ELYTRA} — present when {@code render_elytra} is true, so
 *       the client render layer knows to draw wings.</li>
 *   <li>{@link #CAP_TEXTURE_PREFIX}{@code <id>} — present when a custom
 *       {@code texture_location} is set, carrying the texture id for the layer to
 *       recover.</li>
 * </ul>
 * The server broadcasts this flag to all tracking clients (see
 * {@code NeoOriginsNetwork.broadcastElytraFlight}), mirrored into
 * {@code ClientElytraFlightState} keyed by entity id, and read by
 * {@code NeoOriginsElytraLayer} while rendering the player. No new packet type was
 * needed beyond the broadcast the invisibility/morph render state already uses.
 */
public class ElytraFlightPower extends PowerType<ElytraFlightPower.Config> {

    /** Emitted while active with {@code render_elytra:true} — the client draws wings. */
    public static final String CAP_RENDER_ELYTRA = "render_elytra";
    /** Prefix carrying a custom elytra texture id, e.g. {@code elytra_texture:mymod:textures/...}. */
    public static final String CAP_TEXTURE_PREFIX = "elytra_texture:";

    public record Config(String type, boolean renderElytra, String textureLocation)
            implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.BOOL.optionalFieldOf("render_elytra", true).forGetter(Config::renderElytra),
            Codec.STRING.optionalFieldOf("texture_location", "").forGetter(Config::textureLocation)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        // Always grant natural_glide so the shared glide activation path fires.
        Set<String> caps = new HashSet<>();
        caps.add("natural_glide");
        if (config.renderElytra()) {
            caps.add(CAP_RENDER_ELYTRA);
            String tex = config.textureLocation();
            if (tex != null && !tex.isBlank()) {
                caps.add(CAP_TEXTURE_PREFIX + tex);
            }
        }
        return caps;
    }
}
