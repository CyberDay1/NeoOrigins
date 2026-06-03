package com.cyberday1.neoorigins.recipe;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * A single per-player gate evaluated against the player attempting to craft an
 * {@link OriginGatedRecipe}. Three shapes are supported, mirroring the Apoli
 * {@code apoli:power} recipe-condition convention:
 *
 * <ul>
 *   <li>{@link HasOrigin} — passes when the player has the named origin in any layer.</li>
 *   <li>{@link HasPower} — passes when the player currently has the named power
 *       (whether origin-granted, layer-granted, or dynamically granted).</li>
 *   <li>{@link InLayer}  — passes when the player's selection for the given layer
 *       matches {@code origin}.</li>
 * </ul>
 *
 * <p>Each shape carries a {@code type} string discriminator under the
 * {@code neoorigins:} namespace; the {@link #CODEC} dispatches by that field.
 *
 * <p>All gates are evaluated server-side at craft time (see
 * {@link OriginGatedRecipe#matches}); the recipe still loads on clients so the
 * recipe book can display it.
 */
public sealed interface OriginGate permits OriginGate.HasOrigin, OriginGate.HasPower, OriginGate.InLayer {

    String type();

    boolean test(Player player);

    /** {@code {"type": "neoorigins:has_origin", "origin": "neoorigins:human"}} */
    record HasOrigin(Identifier origin) implements OriginGate {
        public static final MapCodec<HasOrigin> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf("origin").forGetter(HasOrigin::origin)
        ).apply(inst, HasOrigin::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, HasOrigin> STREAM_CODEC =
            StreamCodec.composite(
                Identifier.STREAM_CODEC, HasOrigin::origin,
                HasOrigin::new
            );

        @Override public String type() { return "has_origin"; }

        @Override
        public boolean test(Player player) {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            for (Map.Entry<Identifier, Identifier> e : data.getOrigins().entrySet()) {
                if (origin.equals(e.getValue())) return true;
            }
            return false;
        }
    }

    /** {@code {"type": "neoorigins:has_power", "power": "<power_id>"}} */
    record HasPower(Identifier power) implements OriginGate {
        public static final MapCodec<HasPower> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf("power").forGetter(HasPower::power)
        ).apply(inst, HasPower::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, HasPower> STREAM_CODEC =
            StreamCodec.composite(
                Identifier.STREAM_CODEC, HasPower::power,
                HasPower::new
            );

        @Override public String type() { return "has_power"; }

        @Override
        public boolean test(Player player) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
                // Client-side eval: best-effort using dynamic grants stored on the
                // attachment; full power-set resolution lives server-side via
                // ActiveOriginService. matches() defers to the server path anyway.
                return player.getData(OriginAttachments.originData())
                    .hasDynamicGrant(power);
            }
            for (var holder : com.cyberday1.neoorigins.service.ActiveOriginService.allPowers(sp)) {
                if (power.equals(holder.id())) return true;
            }
            return false;
        }
    }

    /** {@code {"type": "neoorigins:in_layer", "layer": "neoorigins:origin", "origin": "<id>"}} */
    record InLayer(Identifier layer, Identifier origin) implements OriginGate {
        public static final MapCodec<InLayer> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf("layer").forGetter(InLayer::layer),
            Identifier.CODEC.fieldOf("origin").forGetter(InLayer::origin)
        ).apply(inst, InLayer::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, InLayer> STREAM_CODEC =
            StreamCodec.composite(
                Identifier.STREAM_CODEC, InLayer::layer,
                Identifier.STREAM_CODEC, InLayer::origin,
                InLayer::new
            );

        @Override public String type() { return "in_layer"; }

        @Override
        public boolean test(Player player) {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            Identifier current = data.getOrigin(layer);
            return origin.equals(current);
        }
    }

    // ── Dispatch codec ───────────────────────────────────────────────────

    Codec<OriginGate> CODEC = Identifier.CODEC.dispatch(
        "type",
        gate -> Identifier.fromNamespaceAndPath("neoorigins", gate.type()),
        OriginGate::codecForType
    );

    /**
     * Stream codec for sync over the recipe-book network channel. Encodes the
     * type discriminator as a varint over a fixed 1..3 mapping; decodes back to
     * the corresponding record.
     */
    StreamCodec<RegistryFriendlyByteBuf, OriginGate> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OriginGate decode(RegistryFriendlyByteBuf buf) {
            int tag = ByteBufCodecs.VAR_INT.decode(buf);
            return switch (tag) {
                case 1 -> HasOrigin.STREAM_CODEC.decode(buf);
                case 2 -> HasPower.STREAM_CODEC.decode(buf);
                case 3 -> InLayer.STREAM_CODEC.decode(buf);
                default -> throw new IllegalStateException("Unknown OriginGate tag: " + tag);
            };
        }
        @Override
        public void encode(RegistryFriendlyByteBuf buf, OriginGate gate) {
            switch (gate) {
                case HasOrigin h -> { ByteBufCodecs.VAR_INT.encode(buf, 1); HasOrigin.STREAM_CODEC.encode(buf, h); }
                case HasPower  h -> { ByteBufCodecs.VAR_INT.encode(buf, 2); HasPower.STREAM_CODEC.encode(buf, h);  }
                case InLayer   i -> { ByteBufCodecs.VAR_INT.encode(buf, 3); InLayer.STREAM_CODEC.encode(buf, i);   }
            }
        }
    };

    private static MapCodec<? extends OriginGate> codecForType(Identifier id) {
        if (!"neoorigins".equals(id.getNamespace())) {
            throw new IllegalArgumentException("Unknown OriginGate type namespace: " + id);
        }
        return switch (id.getPath()) {
            case "has_origin" -> HasOrigin.CODEC;
            case "has_power"  -> HasPower.CODEC;
            case "in_layer"   -> InLayer.CODEC;
            default -> throw new IllegalArgumentException("Unknown OriginGate type: " + id);
        };
    }

}
