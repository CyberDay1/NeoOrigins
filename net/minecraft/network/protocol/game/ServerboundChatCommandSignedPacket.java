package net.minecraft.network.protocol.game;

import java.time.Instant;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundChatCommandSignedPacket(
    String command, Instant timeStamp, long salt, ArgumentSignatures argumentSignatures, LastSeenMessages.Update lastSeenMessages
) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChatCommandSignedPacket> STREAM_CODEC = Packet.codec(
        ServerboundChatCommandSignedPacket::write, ServerboundChatCommandSignedPacket::new
    );

    private ServerboundChatCommandSignedPacket(FriendlyByteBuf p_338652_) {
        this(p_338652_.readUtf(), p_338652_.readInstant(), p_338652_.readLong(), new ArgumentSignatures(p_338652_), new LastSeenMessages.Update(p_338652_));
    }

    private void write(FriendlyByteBuf p_338860_) {
        p_338860_.writeUtf(this.command);
        p_338860_.writeInstant(this.timeStamp);
        p_338860_.writeLong(this.salt);
        this.argumentSignatures.write(p_338860_);
        this.lastSeenMessages.write(p_338860_);
    }

    @Override
    public PacketType<ServerboundChatCommandSignedPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHAT_COMMAND_SIGNED;
    }

    public void handle(ServerGamePacketListener p_338886_) {
        p_338886_.handleSignedChatCommand(this);
    }
}
