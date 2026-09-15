package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
 *   <li>{@link #CAP_RENDER_ELYTRA} — present when {@code render_elytra} asks for wings
 *       at all ({@code flying} or {@code always}), so the client render layer knows to
 *       draw them.</li>
 *   <li>{@link #CAP_RENDER_ELYTRA_ALWAYS} — additionally present for {@code always},
 *       telling the layer to draw the (folded) wings even when not fall-flying.</li>
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

    /** Emitted whenever wings are drawn at all ({@code flying} or {@code always}). */
    public static final String CAP_RENDER_ELYTRA = "render_elytra";
    /** Emitted only for {@code always} — wings stay on whether or not the player is gliding. */
    public static final String CAP_RENDER_ELYTRA_ALWAYS = "render_elytra_always";
    /** Prefix carrying a custom elytra texture id, e.g. {@code elytra_texture:mymod:textures/...}. */
    public static final String CAP_TEXTURE_PREFIX = "elytra_texture:";

    /**
     * When the cosmetic wings are drawn. {@code render_elytra} was a boolean, so the
     * two historical spellings stay legal and keep their exact meaning: {@code false}
     * is {@link #NEVER}, {@code true} is {@link #FLYING}. {@link #ALWAYS} is the new
     * third state, reachable only by the string spelling.
     *
     * <p>{@code ALWAYS} means "whenever the power is active", not literally always:
     * capability sets are collected with the power's top-level condition gate already
     * applied (see {@code NeoOriginsNetwork.rendersElytraFrom}), so a conditioned
     * power still loses its wings when the condition fails.
     */
    public enum WingRender {
        NEVER("never"),
        FLYING("flying"),
        ALWAYS("always");

        private final String id;

        WingRender(String id) { this.id = id; }

        /** The JSON string spelling of this state. */
        public String id() { return id; }

        /** True when this state draws wings at all — i.e. everything but {@link #NEVER}. */
        public boolean draws() { return this != NEVER; }

        static DataResult<WingRender> fromString(String s) {
            for (WingRender v : values()) {
                if (v.id.equals(s)) return DataResult.success(v);
            }
            return DataResult.error(() -> "Unknown render_elytra value '" + s
                + "': legal values are true, false, \"never\", \"flying\", \"always\"");
        }

        /**
         * Accepts either the legacy boolean or one of the three string spellings.
         * Encodes back to the string form for all three, so a round-trip is total.
         */
        public static final Codec<WingRender> CODEC =
            Codec.either(Codec.BOOL, Codec.STRING).comapFlatMap(
                either -> either.map(
                    b -> DataResult.success(b ? FLYING : NEVER),
                    WingRender::fromString),
                v -> Either.right(v.id));
    }

    public record Config(String type, WingRender renderElytra, String textureLocation)
            implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            WingRender.CODEC.optionalFieldOf("render_elytra", WingRender.FLYING)
                .forGetter(Config::renderElytra),
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
        addRenderCaps(caps, config.renderElytra(), config.textureLocation());
        return caps;
    }

    /**
     * Encode cosmetic wing state into a capability set.
     *
     * <p>Shared with {@code NaturalGlidePower} and {@code FlightPower}, which accept the
     * same two cosmetic fields so an author can draw wings without switching power type.
     * The encoding is effectively a wire format: {@code NeoOriginsNetwork.rendersElytraFrom},
     * {@code alwaysRendersElytraFrom} and {@code elytraTextureFrom} read these tags back
     * out and none of them cares which power produced them. Keeping the write side in one
     * place is what makes that safe.
     *
     * <p>{@link WingRender#ALWAYS} emits {@link #CAP_RENDER_ELYTRA} too, not just
     * {@link #CAP_RENDER_ELYTRA_ALWAYS} — the tri-state is encoded as the old tag plus a
     * refinement, so every reader written against the boolean keeps working untouched.
     */
    public static void addRenderCaps(Set<String> caps, WingRender renderElytra, String textureLocation) {
        if (renderElytra == null || !renderElytra.draws()) {
            return;
        }
        caps.add(CAP_RENDER_ELYTRA);
        if (renderElytra == WingRender.ALWAYS) {
            caps.add(CAP_RENDER_ELYTRA_ALWAYS);
        }
        if (textureLocation != null && !textureLocation.isBlank()) {
            caps.add(CAP_TEXTURE_PREFIX + textureLocation);
        }
    }
}
