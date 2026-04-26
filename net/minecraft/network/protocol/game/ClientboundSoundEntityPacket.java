package net.minecraft.network.protocol.game;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public class ClientboundSoundEntityPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSoundEntityPacket> STREAM_CODEC = Packet.codec(
        ClientboundSoundEntityPacket::write, ClientboundSoundEntityPacket::new
    );
    private final Holder<SoundEvent> sound;
    private final SoundSource source;
    private final int id;
    private final float volume;
    private final float pitch;
    private final long seed;

    public ClientboundSoundEntityPacket(Holder<SoundEvent> p_263513_, SoundSource p_263511_, Entity p_263496_, float p_263519_, float p_263523_, long p_263532_) {
        this.sound = p_263513_;
        this.source = p_263511_;
        this.id = p_263496_.getId();
        this.volume = p_263519_;
        this.pitch = p_263523_;
        this.seed = p_263532_;
    }

    private ClientboundSoundEntityPacket(RegistryFriendlyByteBuf p_319844_) {
        this.sound = SoundEvent.STREAM_CODEC.decode(p_319844_);
        this.source = p_319844_.readEnum(SoundSource.class);
        this.id = p_319844_.readVarInt();
        this.volume = p_319844_.readFloat();
        this.pitch = p_319844_.readFloat();
        this.seed = p_319844_.readLong();
    }

    private void write(RegistryFriendlyByteBuf p_320141_) {
        SoundEvent.STREAM_CODEC.encode(p_320141_, this.sound);
        p_320141_.writeEnum(this.source);
        p_320141_.writeVarInt(this.id);
        p_320141_.writeFloat(this.volume);
        p_320141_.writeFloat(this.pitch);
        p_320141_.writeLong(this.seed);
    }

    @Override
    public PacketType<ClientboundSoundEntityPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SOUND_ENTITY;
    }

    public void handle(ClientGamePacketListener p_133425_) {
        p_133425_.handleSoundEntityEvent(this);
    }

    public Holder<SoundEvent> getSound() {
        return this.sound;
    }

    public SoundSource getSource() {
        return this.source;
    }

    public int getId() {
        return this.id;
    }

    public float getVolume() {
        return this.volume;
    }

    public float getPitch() {
        return this.pitch;
    }

    public long getSeed() {
        return this.seed;
    }
}
