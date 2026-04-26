package net.minecraft.core.particles;

import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FastColor;

public class ColorParticleOption implements ParticleOptions {
    private final ParticleType<ColorParticleOption> type;
    private final int color;

    public static MapCodec<ColorParticleOption> codec(ParticleType<ColorParticleOption> p_333777_) {
        return ExtraCodecs.ARGB_COLOR_CODEC.xmap(p_333828_ -> new ColorParticleOption(p_333777_, p_333828_), p_333908_ -> p_333908_.color).fieldOf("color");
    }

    public static StreamCodec<? super ByteBuf, ColorParticleOption> streamCodec(ParticleType<ColorParticleOption> p_333948_) {
        return ByteBufCodecs.INT.map(p_333912_ -> new ColorParticleOption(p_333948_, p_333912_), p_334072_ -> p_334072_.color);
    }

    private ColorParticleOption(ParticleType<ColorParticleOption> p_333991_, int p_333769_) {
        this.type = p_333991_;
        this.color = p_333769_;
    }

    @Override
    public ParticleType<ColorParticleOption> getType() {
        return this.type;
    }

    public float getRed() {
        return (float)FastColor.ARGB32.red(this.color) / 255.0F;
    }

    public float getGreen() {
        return (float)FastColor.ARGB32.green(this.color) / 255.0F;
    }

    public float getBlue() {
        return (float)FastColor.ARGB32.blue(this.color) / 255.0F;
    }

    public float getAlpha() {
        return (float)FastColor.ARGB32.alpha(this.color) / 255.0F;
    }

    public static ColorParticleOption create(ParticleType<ColorParticleOption> p_334068_, int p_334062_) {
        return new ColorParticleOption(p_334068_, p_334062_);
    }

    public static ColorParticleOption create(ParticleType<ColorParticleOption> p_333772_, float p_333802_, float p_333962_, float p_333936_) {
        return create(p_333772_, FastColor.ARGB32.colorFromFloat(1.0F, p_333802_, p_333962_, p_333936_));
    }
}
