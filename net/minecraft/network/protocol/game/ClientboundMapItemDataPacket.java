package net.minecraft.network.protocol.game;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public record ClientboundMapItemDataPacket(
    MapId mapId, byte scale, boolean locked, Optional<List<MapDecoration>> decorations, Optional<MapItemSavedData.MapPatch> colorPatch
) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMapItemDataPacket> STREAM_CODEC = StreamCodec.composite(
        MapId.STREAM_CODEC,
        ClientboundMapItemDataPacket::mapId,
        ByteBufCodecs.BYTE,
        ClientboundMapItemDataPacket::scale,
        ByteBufCodecs.BOOL,
        ClientboundMapItemDataPacket::locked,
        MapDecoration.STREAM_CODEC.apply(ByteBufCodecs.list()).apply(ByteBufCodecs::optional),
        ClientboundMapItemDataPacket::decorations,
        MapItemSavedData.MapPatch.STREAM_CODEC,
        ClientboundMapItemDataPacket::colorPatch,
        ClientboundMapItemDataPacket::new
    );

    public ClientboundMapItemDataPacket(
        MapId p_324389_, byte p_323701_, boolean p_323593_, @Nullable Collection<MapDecoration> p_324520_, @Nullable MapItemSavedData.MapPatch p_324306_
    ) {
        this(p_324389_, p_323701_, p_323593_, p_324520_ != null ? Optional.of(List.copyOf(p_324520_)) : Optional.empty(), Optional.ofNullable(p_324306_));
    }

    @Override
    public PacketType<ClientboundMapItemDataPacket> type() {
        return GamePacketTypes.CLIENTBOUND_MAP_ITEM_DATA;
    }

    public void handle(ClientGamePacketListener p_132444_) {
        p_132444_.handleMapItemData(this);
    }

    public void applyToMap(MapItemSavedData p_132438_) {
        this.decorations.ifPresent(p_132438_::addClientSideDecorations);
        this.colorPatch.ifPresent(p_323145_ -> p_323145_.applyToMap(p_132438_));
    }
}
