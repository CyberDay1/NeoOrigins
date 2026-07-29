package com.cyberday1.neoorigins.network.payload;

import com.cyberday1.neoorigins.power.morph.MorphSpec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Server → client. Tells receiving clients how player {@code entityId} should
 * currently be rendered — the resolved {@link MorphSpec} from their active
 * {@code neoorigins:entity_model} power — or, when {@code spec} is empty, that
 * the player is no longer morphed.
 *
 * <p>The server resolves the power's config (including any referenced morph
 * definition) before sending, so the client never has to know about morph ids
 * or inline-override precedence; it just renders what it is told.
 *
 * <p>Unlike {@link SyncActivePowersPayload} (which only reaches the owning
 * player), this is broadcast to every client tracking the morphed player AND
 * the player themselves, so the morph is visible to everyone. The receiving
 * client stores it in {@code ClientMorphState} keyed by entity id and the
 * morph renderer reads it during {@code RenderPlayerEvent.Pre}.
 */
public record SyncPlayerMorphPayload(
    int entityId,
    Optional<MorphSpec> spec
) implements CustomPacketPayload {

    public static final Type<SyncPlayerMorphPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_player_morph"));

    public static final StreamCodec<FriendlyByteBuf, SyncPlayerMorphPayload> STREAM_CODEC =
        StreamCodec.of(SyncPlayerMorphPayload::encode, SyncPlayerMorphPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncPlayerMorphPayload payload) {
        buf.writeVarInt(payload.entityId());
        buf.writeOptional(payload.spec(), (b, s) -> s.write(b));
    }

    private static SyncPlayerMorphPayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Optional<MorphSpec> spec = buf.readOptional(MorphSpec::read);
        return new SyncPlayerMorphPayload(id, spec);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
