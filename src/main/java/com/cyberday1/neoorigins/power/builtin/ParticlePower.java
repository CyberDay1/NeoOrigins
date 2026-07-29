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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
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
 *
 * <p>1.20-era packs wrote the parameterized forms as one space-separated string,
 * the way a {@code /particle} command took them ({@code "minecraft:dust 0.1 0.5 0.1 1"},
 * {@code "minecraft:block minecraft:stone"}). That is accepted too; if the
 * arguments can't be read, the head token alone is used rather than failing the
 * whole power over a cosmetic field.
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

                EntityCondition cond = ConditionParser.parseField(obj, "condition", t);

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
            // String form: a registry id ("minecraft:end_rod"), or — in 1.20-era
            // packs — the whole particle with its arguments inline, exactly as it
            // would have been written in a /particle command:
            //   "minecraft:dust 0.1 0.5 0.1 1"      (r g b scale)
            //   "minecraft:block minecraft:stone"   (block id)
            //   "minecraft:item minecraft:apple"    (item id)
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String raw = el.getAsString().trim();
                int sp = raw.indexOf(' ');
                if (sp < 0) return resolveSimple(raw);
                String head = raw.substring(0, sp);
                String[] args = raw.substring(sp + 1).trim().split("\\s+");
                ParticleOptions parameterized = parseLegacyArgs(head, args);
                // A cosmetic field must never sink the whole power: if the args
                // are unreadable, keep the head token and lose the decoration.
                return parameterized != null ? parameterized : resolveSimple(head);
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

        /**
         * Build a parameterized particle from the 1.20 positional argument list.
         * Returns null when the head is not a parameterized type we know, or the
         * arguments don't fit it — the caller then falls back to the head token.
         *
         * <p>26.x-specific: {@code DustParticleOptions} and
         * {@code DustColorTransitionOptions} carry packed 0xRRGGBB ints rather
         * than the 1.21.1 {@code Vector3f}, and {@code ItemParticleOption} takes
         * an {@code Item} directly instead of an {@code ItemStack}.
         */
        @SuppressWarnings("unchecked")
        private static ParticleOptions parseLegacyArgs(String head, String[] args) {
            ParticleType<?> type;
            try {
                type = BuiltInRegistries.PARTICLE_TYPE.get(Identifier.parse(head))
                    .map(net.minecraft.core.Holder.Reference::value)
                    .orElse(null);
            } catch (Exception e) {
                return null;
            }
            if (type == null) return null;
            try {
                if (type == ParticleTypes.DUST && args.length >= 4) {
                    return new DustParticleOptions(
                        packRgb(Float.parseFloat(args[0]), Float.parseFloat(args[1]), Float.parseFloat(args[2])),
                        Float.parseFloat(args[3]));
                }
                if (type == ParticleTypes.DUST_COLOR_TRANSITION && args.length >= 7) {
                    return new DustColorTransitionOptions(
                        packRgb(Float.parseFloat(args[0]), Float.parseFloat(args[1]), Float.parseFloat(args[2])),
                        packRgb(Float.parseFloat(args[4]), Float.parseFloat(args[5]), Float.parseFloat(args[6])),
                        Float.parseFloat(args[3]));
                }
                if (type instanceof SimpleParticleType simple) {
                    // e.g. "minecraft:flame 0 1 0" — trailing junk on a type that
                    // takes no options; the id alone is the honest reading.
                    return simple;
                }
                if (args.length >= 1) {
                    if (type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_MARKER
                        || type == ParticleTypes.FALLING_DUST || type == ParticleTypes.DUST_PILLAR) {
                        var block = BuiltInRegistries.BLOCK.get(Identifier.parse(args[0]))
                            .map(net.minecraft.core.Holder.Reference::value)
                            .orElse(null);
                        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) return null;
                        return new BlockParticleOption(
                            (ParticleType<BlockParticleOption>) type, block.defaultBlockState());
                    }
                    if (type == ParticleTypes.ITEM) {
                        var item = BuiltInRegistries.ITEM.get(Identifier.parse(args[0]))
                            .map(net.minecraft.core.Holder.Reference::value)
                            .orElse(null);
                        if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
                        return new ItemParticleOption((ParticleType<ItemParticleOption>) type, item);
                    }
                }
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn(
                    "neoorigins:particle — could not read legacy arguments for '{}': {}", head, e.getMessage());
            }
            return null;
        }

        /** Pack three 0..1 floats into the 0xRRGGBB int 26.x's dust options take. */
        private static int packRgb(float r, float g, float b) {
            return (Math.round(clamp01(r) * 255) << 16)
                 | (Math.round(clamp01(g) * 255) << 8)
                 |  Math.round(clamp01(b) * 255);
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
