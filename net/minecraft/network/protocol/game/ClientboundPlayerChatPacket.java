package net.minecraft.network.protocol.game;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundPlayerChatPacket(
    UUID sender,
    int index,
    @Nullable MessageSignature signature,
    SignedMessageBody.Packed body,
    @Nullable Component unsignedContent,
    FilterMask filterMask,
    ChatType.Bound chatType
) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerChatPacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerChatPacket::write, ClientboundPlayerChatPacket::new
    );

    private ClientboundPlayerChatPacket(RegistryFriendlyByteBuf p_321853_) {
        this(
            p_321853_.readUUID(),
            p_321853_.readVarInt(),
            p_321853_.readNullable(MessageSignature::read),
            new SignedMessageBody.Packed(p_321853_),
            FriendlyByteBuf.readNullable(p_321853_, ComponentSerialization.TRUSTED_STREAM_CODEC),
            FilterMask.read(p_321853_),
            ChatType.Bound.STREAM_CODEC.decode(p_321853_)
        );
    }

    private void write(RegistryFriendlyByteBuf p_321846_) {
        p_321846_.writeUUID(this.sender);
        p_321846_.writeVarInt(this.index);
        p_321846_.writeNullable(this.signature, MessageSignature::write);
        this.body.write(p_321846_);
        FriendlyByteBuf.writeNullable(p_321846_, this.unsignedContent, ComponentSerialization.TRUSTED_STREAM_CODEC);
        FilterMask.write(p_321846_, this.filterMask);
        ChatType.Bound.STREAM_CODEC.encode(p_321846_, this.chatType);
    }

    @Override
    public PacketType<ClientboundPlayerChatPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_CHAT;
    }

    public void handle(ClientGamePacketListener p_237759_) {
        p_237759_.handlePlayerChat(this);
    }

    @Override
    public boolean isSkippable() {
        return true;
    }
}
