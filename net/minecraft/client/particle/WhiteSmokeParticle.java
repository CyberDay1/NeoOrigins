package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WhiteSmokeParticle extends BaseAshSmokeParticle {
    private static final int COLOR_RGB24 = 12235202;

    protected WhiteSmokeParticle(
        ClientLevel p_307477_,
        double p_307301_,
        double p_307458_,
        double p_307404_,
        double p_307585_,
        double p_307374_,
        double p_307496_,
        float p_307375_,
        SpriteSet p_307591_
    ) {
        super(p_307477_, p_307301_, p_307458_, p_307404_, 0.1F, 0.1F, 0.1F, p_307585_, p_307374_, p_307496_, p_307375_, p_307591_, 0.3F, 8, -0.1F, true);
        this.rCol = 0.7294118F;
        this.gCol = 0.69411767F;
        this.bCol = 0.7607843F;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet p_307398_) {
            this.sprites = p_307398_;
        }

        public Particle createParticle(
            SimpleParticleType p_307553_,
            ClientLevel p_307230_,
            double p_307667_,
            double p_307538_,
            double p_307238_,
            double p_307216_,
            double p_307331_,
            double p_307243_
        ) {
            return new WhiteSmokeParticle(p_307230_, p_307667_, p_307538_, p_307238_, p_307216_, p_307331_, p_307243_, 1.0F, this.sprites);
        }
    }
}
