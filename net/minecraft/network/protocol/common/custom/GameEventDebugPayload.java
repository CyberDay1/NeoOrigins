package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public record GameEventDebugPayload(ResourceKey<GameEvent> gameEventType, Vec3 pos) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, GameEventDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        GameEventDebugPayload::write, GameEventDebugPayload::new
    );
    public static final CustomPacketPayload.Type<GameEventDebugPayload> TYPE = CustomPacketPayload.createType("debug/game_event");

    private GameEventDebugPayload(FriendlyByteBuf p_294345_) {
        this(p_294345_.readResourceKey(Registries.GAME_EVENT), p_294345_.readVec3());
    }

    private void write(FriendlyByteBuf p_295680_) {
        p_295680_.writeResourceKey(this.gameEventType);
        p_295680_.writeVec3(this.pos);
    }

    @Override
    public CustomPacketPayload.Type<GameEventDebugPayload> type() {
        return TYPE;
    }
}
