package net.minecraft.network.protocol.game;

import com.google.common.base.MoreObjects;
import com.mojang.authlib.GameProfile;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class ClientboundPlayerInfoUpdatePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerInfoUpdatePacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerInfoUpdatePacket::write, ClientboundPlayerInfoUpdatePacket::new
    );
    private final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions;
    private final List<ClientboundPlayerInfoUpdatePacket.Entry> entries;

    public ClientboundPlayerInfoUpdatePacket(EnumSet<ClientboundPlayerInfoUpdatePacket.Action> p_251739_, Collection<ServerPlayer> p_251579_) {
        this.actions = p_251739_;
        this.entries = p_251579_.stream().map(ClientboundPlayerInfoUpdatePacket.Entry::new).toList();
    }

    public ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action p_251648_, ServerPlayer p_252273_) {
        this.actions = EnumSet.of(p_251648_);
        this.entries = List.of(new ClientboundPlayerInfoUpdatePacket.Entry(p_252273_));
    }

    public static ClientboundPlayerInfoUpdatePacket createPlayerInitializing(Collection<ServerPlayer> p_252314_) {
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> enumset = EnumSet.of(
            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
            ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
        );
        return new ClientboundPlayerInfoUpdatePacket(enumset, p_252314_);
    }

    private ClientboundPlayerInfoUpdatePacket(RegistryFriendlyByteBuf p_323822_) {
        this.actions = p_323822_.readEnumSet(ClientboundPlayerInfoUpdatePacket.Action.class);
        this.entries = p_323822_.readList(
            p_323148_ -> {
                ClientboundPlayerInfoUpdatePacket.EntryBuilder clientboundplayerinfoupdatepacket$entrybuilder = new ClientboundPlayerInfoUpdatePacket.EntryBuilder(
                    p_323148_.readUUID()
                );

                for (ClientboundPlayerInfoUpdatePacket.Action clientboundplayerinfoupdatepacket$action : this.actions) {
                    clientboundplayerinfoupdatepacket$action.reader.read(clientboundplayerinfoupdatepacket$entrybuilder, (RegistryFriendlyByteBuf)p_323148_);
                }

                return clientboundplayerinfoupdatepacket$entrybuilder.build();
            }
        );
    }

    private void write(RegistryFriendlyByteBuf p_323752_) {
        p_323752_.writeEnumSet(this.actions, ClientboundPlayerInfoUpdatePacket.Action.class);
        p_323752_.writeCollection(this.entries, (p_323146_, p_323147_) -> {
            p_323146_.writeUUID(p_323147_.profileId());

            for (ClientboundPlayerInfoUpdatePacket.Action clientboundplayerinfoupdatepacket$action : this.actions) {
                clientboundplayerinfoupdatepacket$action.writer.write((RegistryFriendlyByteBuf)p_323146_, p_323147_);
            }
        });
    }

    @Override
    public PacketType<ClientboundPlayerInfoUpdatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE;
    }

    public void handle(ClientGamePacketListener p_249935_) {
        p_249935_.handlePlayerInfoUpdate(this);
    }

    public EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions() {
        return this.actions;
    }

    public List<ClientboundPlayerInfoUpdatePacket.Entry> entries() {
        return this.entries;
    }

    public List<ClientboundPlayerInfoUpdatePacket.Entry> newEntries() {
        return this.actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER) ? this.entries : List.of();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("actions", this.actions).add("entries", this.entries).toString();
    }

    public static enum Action {
        ADD_PLAYER((p_329872_, p_329873_) -> {
            GameProfile gameprofile = new GameProfile(p_329872_.profileId, p_329873_.readUtf(16));
            gameprofile.getProperties().putAll(ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(p_329873_));
            p_329872_.profile = gameprofile;
        }, (p_329874_, p_329875_) -> {
            GameProfile gameprofile = Objects.requireNonNull(p_329875_.profile());
            p_329874_.writeUtf(gameprofile.getName(), 16);
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(p_329874_, gameprofile.getProperties());
        }),
        INITIALIZE_CHAT(
            (p_323155_, p_323156_) -> p_323155_.chatSession = p_323156_.readNullable(RemoteChatSession.Data::read),
            (p_323151_, p_323152_) -> p_323151_.writeNullable(p_323152_.chatSession, RemoteChatSession.Data::write)
        ),
        UPDATE_GAME_MODE(
            (p_323161_, p_323162_) -> p_323161_.gameMode = GameType.byId(p_323162_.readVarInt()),
            (p_323157_, p_323158_) -> p_323157_.writeVarInt(p_323158_.gameMode().getId())
        ),
        UPDATE_LISTED(
            (p_323167_, p_323168_) -> p_323167_.listed = p_323168_.readBoolean(), (p_323171_, p_323172_) -> p_323171_.writeBoolean(p_323172_.listed())
        ),
        UPDATE_LATENCY(
            (p_323165_, p_323166_) -> p_323165_.latency = p_323166_.readVarInt(), (p_323153_, p_323154_) -> p_323153_.writeVarInt(p_323154_.latency())
        ),
        UPDATE_DISPLAY_NAME(
            (p_329878_, p_329879_) -> p_329878_.displayName = FriendlyByteBuf.readNullable(p_329879_, ComponentSerialization.TRUSTED_STREAM_CODEC),
            (p_329876_, p_329877_) -> FriendlyByteBuf.writeNullable(p_329876_, p_329877_.displayName(), ComponentSerialization.TRUSTED_STREAM_CODEC)
        );

        final ClientboundPlayerInfoUpdatePacket.Action.Reader reader;
        final ClientboundPlayerInfoUpdatePacket.Action.Writer writer;

        private Action(ClientboundPlayerInfoUpdatePacket.Action.Reader p_249392_, ClientboundPlayerInfoUpdatePacket.Action.Writer p_250487_) {
            this.reader = p_249392_;
            this.writer = p_250487_;
        }

        public interface Reader {
            void read(ClientboundPlayerInfoUpdatePacket.EntryBuilder p_251859_, RegistryFriendlyByteBuf p_324148_);
        }

        public interface Writer {
            void write(RegistryFriendlyByteBuf p_324483_, ClientboundPlayerInfoUpdatePacket.Entry p_249783_);
        }
    }

    public static record Entry(
        UUID profileId,
        @Nullable GameProfile profile,
        boolean listed,
        int latency,
        GameType gameMode,
        @Nullable Component displayName,
        @Nullable RemoteChatSession.Data chatSession
    ) {
        Entry(ServerPlayer p_252094_) {
            this(
                p_252094_.getUUID(),
                p_252094_.getGameProfile(),
                true,
                p_252094_.connection.latency(),
                p_252094_.gameMode.getGameModeForPlayer(),
                p_252094_.getTabListDisplayName(),
                Optionull.map(p_252094_.getChatSession(), RemoteChatSession::asData)
            );
        }
    }

    static class EntryBuilder {
        final UUID profileId;
        @Nullable
        GameProfile profile;
        boolean listed;
        int latency;
        GameType gameMode = GameType.DEFAULT_MODE;
        @Nullable
        Component displayName;
        @Nullable
        RemoteChatSession.Data chatSession;

        EntryBuilder(UUID p_251670_) {
            this.profileId = p_251670_;
        }

        ClientboundPlayerInfoUpdatePacket.Entry build() {
            return new ClientboundPlayerInfoUpdatePacket.Entry(
                this.profileId, this.profile, this.listed, this.latency, this.gameMode, this.displayName, this.chatSession
            );
        }
    }
}
