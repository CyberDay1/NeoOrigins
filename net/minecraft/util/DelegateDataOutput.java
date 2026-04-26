package net.minecraft.util;

import java.io.DataOutput;
import java.io.IOException;

public class DelegateDataOutput implements DataOutput {
    private final DataOutput parent;

    public DelegateDataOutput(DataOutput p_312521_) {
        this.parent = p_312521_;
    }

    @Override
    public void write(int p_311810_) throws IOException {
        this.parent.write(p_311810_);
    }

    @Override
    public void write(byte[] p_312321_) throws IOException {
        this.parent.write(p_312321_);
    }

    @Override
    public void write(byte[] p_311876_, int p_312050_, int p_312363_) throws IOException {
        this.parent.write(p_311876_, p_312050_, p_312363_);
    }

    @Override
    public void writeBoolean(boolean p_312564_) throws IOException {
        this.parent.writeBoolean(p_312564_);
    }

    @Override
    public void writeByte(int p_312307_) throws IOException {
        this.parent.writeByte(p_312307_);
    }

    @Override
    public void writeShort(int p_312426_) throws IOException {
        this.parent.writeShort(p_312426_);
    }

    @Override
    public void writeChar(int p_312139_) throws IOException {
        this.parent.writeChar(p_312139_);
    }

    @Override
    public void writeInt(int p_312539_) throws IOException {
        this.parent.writeInt(p_312539_);
    }

    @Override
    public void writeLong(long p_312522_) throws IOException {
        this.parent.writeLong(p_312522_);
    }

    @Override
    public void writeFloat(float p_312854_) throws IOException {
        this.parent.writeFloat(p_312854_);
    }

    @Override
    public void writeDouble(double p_312512_) throws IOException {
        this.parent.writeDouble(p_312512_);
    }

    @Override
    public void writeBytes(String p_312376_) throws IOException {
        this.parent.writeBytes(p_312376_);
    }

    @Override
    public void writeChars(String p_311925_) throws IOException {
        this.parent.writeChars(p_311925_);
    }

    @Override
    public void writeUTF(String p_312577_) throws IOException {
        this.parent.writeUTF(p_312577_);
    }
}
