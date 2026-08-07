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
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

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
 *
 * <p>A parameterized particle named by bare id, with no arguments at all, falls
 * back to its plainest form — opaque white, unit scale, no delay — rather than
 * taking the power down with it. Particles that need a referent rather than a
 * decoration ({@code block}, {@code item}, {@code vibration}) still require the
 * object or inline-argument form.
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
                    Vector3f color = parseColor(pobj.get("color"));
                    float scale = pobj.has("scale") ? pobj.get("scale").getAsFloat() : 1.0f;
                    return new DustParticleOptions(color, scale);
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
         */
        @SuppressWarnings("unchecked")
        private static ParticleOptions parseLegacyArgs(String head, String[] args) {
            ParticleType<?> type;
            try {
                type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(head));
            } catch (Exception e) {
                return null;
            }
            if (type == null) return null;
            try {
                if (type == ParticleTypes.DUST && args.length >= 4) {
                    return new DustParticleOptions(
                        new Vector3f(Float.parseFloat(args[0]), Float.parseFloat(args[1]), Float.parseFloat(args[2])),
                        Float.parseFloat(args[3]));
                }
                if (type == ParticleTypes.DUST_COLOR_TRANSITION && args.length >= 7) {
                    return new DustColorTransitionOptions(
                        new Vector3f(Float.parseFloat(args[0]), Float.parseFloat(args[1]), Float.parseFloat(args[2])),
                        new Vector3f(Float.parseFloat(args[4]), Float.parseFloat(args[5]), Float.parseFloat(args[6])),
                        Float.parseFloat(args[3]));
                }
                if (type instanceof net.minecraft.core.particles.SimpleParticleType simple) {
                    // e.g. "minecraft:flame 0 1 0" — trailing junk on a type that
                    // takes no options; the id alone is the honest reading.
                    return simple;
                }
                if (args.length >= 1) {
                    if (type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_MARKER
                        || type == ParticleTypes.FALLING_DUST || type == ParticleTypes.DUST_PILLAR) {
                        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(args[0]));
                        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) return null;
                        return new net.minecraft.core.particles.BlockParticleOption(
                            (ParticleType<net.minecraft.core.particles.BlockParticleOption>) type,
                            block.defaultBlockState());
                    }
                    if (type == ParticleTypes.ITEM) {
                        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(args[0]));
                        if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
                        return new net.minecraft.core.particles.ItemParticleOption(
                            (ParticleType<net.minecraft.core.particles.ItemParticleOption>) type,
                            new net.minecraft.world.item.ItemStack(item));
                    }
                }
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn(
                    "neoorigins:particle — could not read legacy arguments for '{}': {}", head, e.getMessage());
            }
            return null;
        }

        private static ParticleOptions resolveSimple(String id) {
            try {
                ResourceLocation rl = ResourceLocation.parse(id);
                ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(rl);
                if (type instanceof SimpleParticleType simple) return simple;
                ParticleOptions defaulted = defaultOptionsFor(type);
                if (defaulted != null) return defaulted;
                NeoOrigins.LOGGER.warn(
                    "neoorigins:particle — '{}' is not a SimpleParticleType; use the object form for parameterized particles (e.g. dust).",
                    id);
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn("neoorigins:particle — could not resolve particle id '{}': {}", id, e.getMessage());
            }
            return null;
        }

        /**
         * A neutral default for a parameterized particle named by bare id.
         *
         * <p>Packs routinely write {@code "particle": "minecraft:entity_effect"} with
         * no arguments, the same way they write {@code "minecraft:flame"}. Rejecting
         * that dropped the entire power over one cosmetic field, so a parameterized
         * type whose options are purely decorative resolves to its plainest form
         * instead — opaque white, unit scale, no delay.
         *
         * <p>Types that need a <em>referent</em> rather than a decoration —
         * {@code block}, {@code item}, {@code vibration} — are deliberately absent.
         * There is no honest default for "which block", so those stay an error and
         * the author is told to supply one.
         *
         * <p>Returns null when the type has no sensible default.
         */
        private static ParticleOptions defaultOptionsFor(ParticleType<?> type) {
            if (type == ParticleTypes.ENTITY_EFFECT) {
                return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0f, 1.0f, 1.0f);
            }
            if (type == ParticleTypes.DUST) {
                return new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.0f);
            }
            if (type == ParticleTypes.SCULK_CHARGE) {
                return new SculkChargeParticleOptions(0.0f);
            }
            if (type == ParticleTypes.SHRIEK) {
                return new ShriekParticleOption(0);
            }
            return null;
        }

        private static Vector3f parseColor(JsonElement el) {
            // Accept [r,g,b] in 0..1 floats. Default to white if missing/malformed.
            if (el != null && el.isJsonArray()) {
                var arr = el.getAsJsonArray();
                if (arr.size() >= 3) {
                    return new Vector3f(
                        arr.get(0).getAsFloat(),
                        arr.get(1).getAsFloat(),
                        arr.get(2).getAsFloat());
                }
            }
            return new Vector3f(1.0f, 1.0f, 1.0f);
        }

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
