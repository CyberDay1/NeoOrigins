package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public abstract class ServerboundMovePlayerPacket implements Packet<ServerGamePacketListener> {
    protected final double x;
    protected final double y;
    protected final double z;
    protected final float yRot;
    protected final float xRot;
    protected final boolean onGround;
    protected final boolean hasPos;
    protected final boolean hasRot;

    protected ServerboundMovePlayerPacket(
        double p_179675_, double p_179676_, double p_179677_, float p_179678_, float p_179679_, boolean p_179680_, boolean p_179681_, boolean p_179682_
    ) {
        this.x = p_179675_;
        this.y = p_179676_;
        this.z = p_179677_;
        this.yRot = p_179678_;
        this.xRot = p_179679_;
        this.onGround = p_179680_;
        this.hasPos = p_179681_;
        this.hasRot = p_179682_;
    }

    @Override
    public abstract PacketType<? extends ServerboundMovePlayerPacket> type();

    public void handle(ServerGamePacketListener p_134138_) {
        p_134138_.handleMovePlayer(this);
    }

    public double getX(double p_134130_) {
        return this.hasPos ? this.x : p_134130_;
    }

    public double getY(double p_134141_) {
        return this.hasPos ? this.y : p_134141_;
    }

    public double getZ(double p_134147_) {
        return this.hasPos ? this.z : p_134147_;
    }

    public float getYRot(float p_134132_) {
        return this.hasRot ? this.yRot : p_134132_;
    }

    public float getXRot(float p_134143_) {
        return this.hasRot ? this.xRot : p_134143_;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public boolean hasPosition() {
        return this.hasPos;
    }

    public boolean hasRotation() {
        return this.hasRot;
    }

    public static class Pos extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.Pos> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.Pos::write, ServerboundMovePlayerPacket.Pos::read
        );

        public Pos(double p_134150_, double p_134151_, double p_134152_, boolean p_134153_) {
            super(p_134150_, p_134151_, p_134152_, 0.0F, 0.0F, p_134153_, true, false);
        }

        private static ServerboundMovePlayerPacket.Pos read(FriendlyByteBuf p_179686_) {
            double d0 = p_179686_.readDouble();
            double d1 = p_179686_.readDouble();
            double d2 = p_179686_.readDouble();
            boolean flag = p_179686_.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.Pos(d0, d1, d2, flag);
        }

        private void write(FriendlyByteBuf p_134159_) {
            p_134159_.writeDouble(this.x);
            p_134159_.writeDouble(this.y);
            p_134159_.writeDouble(this.z);
            p_134159_.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.Pos> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS;
        }
    }

    public static class PosRot extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.PosRot> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.PosRot::write, ServerboundMovePlayerPacket.PosRot::read
        );

        public PosRot(double p_134162_, double p_134163_, double p_134164_, float p_134165_, float p_134166_, boolean p_134167_) {
            super(p_134162_, p_134163_, p_134164_, p_134165_, p_134166_, p_134167_, true, true);
        }

        private static ServerboundMovePlayerPacket.PosRot read(FriendlyByteBuf p_179688_) {
            double d0 = p_179688_.readDouble();
            double d1 = p_179688_.readDouble();
            double d2 = p_179688_.readDouble();
            float f = p_179688_.readFloat();
            float f1 = p_179688_.readFloat();
            boolean flag = p_179688_.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.PosRot(d0, d1, d2, f, f1, flag);
        }

        private void write(FriendlyByteBuf p_134173_) {
            p_134173_.writeDouble(this.x);
            p_134173_.writeDouble(this.y);
            p_134173_.writeDouble(this.z);
            p_134173_.writeFloat(this.yRot);
            p_134173_.writeFloat(this.xRot);
            p_134173_.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.PosRot> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS_ROT;
        }
    }

    public static class Rot extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.Rot> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.Rot::write, ServerboundMovePlayerPacket.Rot::read
        );

        public Rot(float p_134176_, float p_134177_, boolean p_134178_) {
            super(0.0, 0.0, 0.0, p_134176_, p_134177_, p_134178_, false, true);
        }

        private static ServerboundMovePlayerPacket.Rot read(FriendlyByteBuf p_179690_) {
            float f = p_179690_.readFloat();
            float f1 = p_179690_.readFloat();
            boolean flag = p_179690_.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.Rot(f, f1, flag);
        }

        private void write(FriendlyByteBuf p_134184_) {
            p_134184_.writeFloat(this.yRot);
            p_134184_.writeFloat(this.xRot);
            p_134184_.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.Rot> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_ROT;
        }
    }

    public static class StatusOnly extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.StatusOnly> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.StatusOnly::write, ServerboundMovePlayerPacket.StatusOnly::read
        );

        public StatusOnly(boolean p_179692_) {
            super(0.0, 0.0, 0.0, 0.0F, 0.0F, p_179692_, false, false);
        }

        private static ServerboundMovePlayerPacket.StatusOnly read(FriendlyByteBuf p_179698_) {
            boolean flag = p_179698_.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.StatusOnly(flag);
        }

        private void write(FriendlyByteBuf p_179694_) {
            p_179694_.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.StatusOnly> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_STATUS_ONLY;
        }
    }
}
