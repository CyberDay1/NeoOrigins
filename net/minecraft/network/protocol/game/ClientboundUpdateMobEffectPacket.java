package net.minecraft.network.protocol.game;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public class ClientboundUpdateMobEffectPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateMobEffectPacket> STREAM_CODEC = Packet.codec(
        ClientboundUpdateMobEffectPacket::write, ClientboundUpdateMobEffectPacket::new
    );
    private static final int FLAG_AMBIENT = 1;
    private static final int FLAG_VISIBLE = 2;
    private static final int FLAG_SHOW_ICON = 4;
    private static final int FLAG_BLEND = 8;
    private final int entityId;
    private final Holder<MobEffect> effect;
    private final int effectAmplifier;
    private final int effectDurationTicks;
    private final byte flags;

    public ClientboundUpdateMobEffectPacket(int p_133611_, MobEffectInstance p_133612_, boolean p_316376_) {
        this.entityId = p_133611_;
        this.effect = p_133612_.getEffect();
        this.effectAmplifier = p_133612_.getAmplifier();
        this.effectDurationTicks = p_133612_.getDuration();
        byte b0 = 0;
        if (p_133612_.isAmbient()) {
            b0 = (byte)(b0 | 1);
        }

        if (p_133612_.isVisible()) {
            b0 = (byte)(b0 | 2);
        }

        if (p_133612_.showIcon()) {
            b0 = (byte)(b0 | 4);
        }

        if (p_316376_) {
            b0 = (byte)(b0 | 8);
        }

        this.flags = b0;
    }

    private ClientboundUpdateMobEffectPacket(RegistryFriendlyByteBuf p_320890_) {
        this.entityId = p_320890_.readVarInt();
        this.effect = MobEffect.STREAM_CODEC.decode(p_320890_);
        this.effectAmplifier = p_320890_.readVarInt();
        this.effectDurationTicks = p_320890_.readVarInt();
        this.flags = p_320890_.readByte();
    }

    private void write(RegistryFriendlyByteBuf p_320214_) {
        p_320214_.writeVarInt(this.entityId);
        MobEffect.STREAM_CODEC.encode(p_320214_, this.effect);
        p_320214_.writeVarInt(this.effectAmplifier);
        p_320214_.writeVarInt(this.effectDurationTicks);
        p_320214_.writeByte(this.flags);
    }

    @Override
    public PacketType<ClientboundUpdateMobEffectPacket> type() {
        return GamePacketTypes.CLIENTBOUND_UPDATE_MOB_EFFECT;
    }

    public void handle(ClientGamePacketListener p_133618_) {
        p_133618_.handleUpdateMobEffect(this);
    }

    public int getEntityId() {
        return this.entityId;
    }

    public Holder<MobEffect> getEffect() {
        return this.effect;
    }

    public int getEffectAmplifier() {
        return this.effectAmplifier;
    }

    public int getEffectDurationTicks() {
        return this.effectDurationTicks;
    }

    public boolean isEffectVisible() {
        return (this.flags & 2) != 0;
    }

    public boolean isEffectAmbient() {
        return (this.flags & 1) != 0;
    }

    public boolean effectShowsIcon() {
        return (this.flags & 4) != 0;
    }

    public boolean shouldBlend() {
        return (this.flags & 8) != 0;
    }
}
