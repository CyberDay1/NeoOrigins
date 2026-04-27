package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Spawns a vanilla particle on the player at a fixed cadence.
 *
 * <p>Server-side {@code ServerLevel.sendParticles} packetizes to nearby
 * clients, so this works on dedicated servers without a client mixin.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:particle",
 *   "particle": "minecraft:end_rod",
 *   "frequency": 8,
 *   "count": 1,
 *   "spread": [0.3, 0.5, 0.3],
 *   "offset": [0.0, 1.0, 0.0],
 *   "speed": 0.0,
 *   "condition": { "type": "..." }
 * }
 * }</pre>
 *
 * <p>{@code particle} accepts either a registry-id string for any
 * {@link SimpleParticleType}, or an object form for parameterized particles:
 * <pre>{@code
 *   "particle": { "type": "minecraft:dust", "color": [1.0, 0.85, 0.2], "scale": 0.6 }
 * }</pre>
 */
public class ParticlePower extends PowerType<ParticlePower.Config> {

    public record Config(
        ParticleOptions particle,
        int frequency,
        int count,
        float spreadX, float spreadY, float spreadZ,
        float offsetX, float offsetY, float offsetZ,
        double speed,
        EntityCondition condition,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "particle: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "particle: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:particle";

                ParticleOptions particle = parseParticle(obj.get("particle"));
                if (particle == null) {
                    return DataResult.error(() -> "particle: missing or unknown 'particle' field");
                }

                int freq  = obj.has("frequency") ? Math.max(1, obj.get("frequency").getAsInt()) : 8;
                int count = obj.has("count") ? Math.max(0, obj.get("count").getAsInt()) : 1;

                float[] spread = parseFloat3(obj.get("spread"), 0.25f, 0.5f, 0.25f);
                float[] offset = parseFloat3(obj.get("offset"), 0.0f, 1.0f, 0.0f);
                double speed = obj.has("speed") ? obj.get("speed").getAsDouble() : 0.0;

                EntityCondition cond = obj.has("condition") && obj.get("condition").isJsonObject()
                    ? ConditionParser.parse(obj.getAsJsonObject("condition"), t)
                    : EntityCondition.alwaysTrue();

                return DataResult.success(Pair.of(
                    new Config(particle, freq, count,
                        spread[0], spread[1], spread[2],
                        offset[0], offset[1], offset[2],
                        speed, cond, t),
                    ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };

        private static ParticleOptions parseParticle(JsonElement el) {
            if (el == null || el.isJsonNull()) return null;
            // String form: just the registry id, e.g. "minecraft:end_rod".
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                return resolveSimple(el.getAsString());
            }
            // Object form: { "type": "minecraft:dust", "color": [...], "scale": 0.6 }
            // Required for parameterized particles (dust, dust_color_transition).
            if (el.isJsonObject()) {
                JsonObject pobj = el.getAsJsonObject();
                String typeId = pobj.has("type") ? pobj.get("type").getAsString()
                              : pobj.has("id")   ? pobj.get("id").getAsString()
                              : null;
                if (typeId == null) return null;
                if ("minecraft:dust".equals(typeId)) {
                    int packedColor = parseColorPacked(pobj.get("color"));
                    float scale = pobj.has("scale") ? pobj.get("scale").getAsFloat() : 1.0f;
                    return new DustParticleOptions(packedColor, scale);
                }
                // Other parameterized types fall through to the simple lookup;
                // packs that need them can be added incrementally.
                return resolveSimple(typeId);
            }
            return null;
        }

        private static ParticleOptions resolveSimple(String id) {
            try {
                Identifier rl = Identifier.parse(id);
                ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(rl)
                    .map(net.minecraft.core.Holder.Reference::value)
                    .orElse(null);
                if (type instanceof SimpleParticleType simple) return simple;
                NeoOrigins.LOGGER.warn(
                    "neoorigins:particle — '{}' is not a SimpleParticleType; use the object form for parameterized particles (e.g. dust).",
                    id);
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn("neoorigins:particle — could not resolve particle id '{}': {}", id, e.getMessage());
            }
            return null;
        }

        /** Pack [r,g,b] floats (0..1) into an 0xRRGGBB int. 26.1's DustParticleOptions takes a packed int. */
        private static int parseColorPacked(JsonElement el) {
            float r = 1.0f, g = 1.0f, b = 1.0f;
            if (el != null && el.isJsonArray()) {
                var arr = el.getAsJsonArray();
                if (arr.size() >= 3) {
                    r = clamp01(arr.get(0).getAsFloat());
                    g = clamp01(arr.get(1).getAsFloat());
                    b = clamp01(arr.get(2).getAsFloat());
                }
            }
            int ri = Math.round(r * 255);
            int gi = Math.round(g * 255);
            int bi = Math.round(b * 255);
            return (ri << 16) | (gi << 8) | bi;
        }

        private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

        private static float[] parseFloat3(JsonElement el, float dx, float dy, float dz) {
            if (el != null && el.isJsonArray()) {
                var arr = el.getAsJsonArray();
                if (arr.size() >= 3) {
                    return new float[] {
                        arr.get(0).getAsFloat(),
                        arr.get(1).getAsFloat(),
                        arr.get(2).getAsFloat()
                    };
                }
            }
            return new float[] { dx, dy, dz };
        }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % config.frequency() != 0) return;
        if (!config.condition().test(player)) return;
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(
            config.particle(),
            player.getX() + config.offsetX(),
            player.getY() + config.offsetY(),
            player.getZ() + config.offsetZ(),
            config.count(),
            config.spreadX(), config.spreadY(), config.spreadZ(),
            config.speed());
    }
}
