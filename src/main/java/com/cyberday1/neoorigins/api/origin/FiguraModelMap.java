package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Advanced, reactive Figura-model mapping for an origin. Declared in origin JSON
 * under the optional {@code figura_models} object, alongside the shorthand base
 * {@code figura_model} string. Every key and value here is an OPAQUE string:
 * NeoOrigins never interprets or validates them, it only hands them to the Figura
 * Lua sandbox (soft-dep) so an avatar author can pick a model that reflects the
 * wearer's live state.
 *
 * <p>The four maps are:
 * <ul>
 *   <li>{@code tiers}: string integer index (e.g. {@code "2"}) to model key. The
 *       effective tier model is the base key overridden by the highest index whose
 *       integer value is at most the player's current evolution tier. Non-integer
 *       keys are ignored at resolve time rather than erroring.</li>
 *   <li>{@code powers}: power id to model key. A key is "on" while its power is
 *       currently active on the player.</li>
 *   <li>{@code capabilities}: capability tag to model key. A key is "on" while its
 *       capability tag is currently present on the player.</li>
 *   <li>{@code vocab}: model key to human label. Purely for discovery: it lets an
 *       avatar script list the author-declared keys and their friendly names.</li>
 * </ul>
 *
 * <p>All four fields are optional and default to an empty map, so an origin may
 * declare any subset. An origin with no {@code figura_models} object at all loads
 * exactly as before.
 */
public record FiguraModelMap(
    Map<String, String> tiers,
    Map<ResourceLocation, String> powers,
    Map<String, String> capabilities,
    Map<String, String> vocab
) {
    public static final Codec<FiguraModelMap> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.unboundedMap(Codec.STRING, Codec.STRING)
            .optionalFieldOf("tiers", Map.of()).forGetter(FiguraModelMap::tiers),
        Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING)
            .optionalFieldOf("powers", Map.of()).forGetter(FiguraModelMap::powers),
        Codec.unboundedMap(Codec.STRING, Codec.STRING)
            .optionalFieldOf("capabilities", Map.of()).forGetter(FiguraModelMap::capabilities),
        Codec.unboundedMap(Codec.STRING, Codec.STRING)
            .optionalFieldOf("vocab", Map.of()).forGetter(FiguraModelMap::vocab)
    ).apply(inst, FiguraModelMap::new));
}
