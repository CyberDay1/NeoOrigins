package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DustPlumeParticle extends BaseAshSmokeParticle {
    private static final int COLOR_RGB24 = 12235202;

    protected DustPlumeParticle(
        ClientLevel p_306144_,
        double p_305922_,
        double p_305880_,
        double p_305916_,
        double p_305992_,
        double p_305773_,
        double p_306172_,
        float p_306224_,
        SpriteSet p_305888_
    ) {
        super(p_306144_, p_305922_, p_305880_, p_305916_, 0.7F, 0.6F, 0.7F, p_305992_, p_305773_ + 0.15F, p_306172_, p_306224_, p_305888_, 0.5F, 7, 0.5F, false);
        float f = (float)Math.random() * 0.2F;
        this.rCol = (float)FastColor.ARGB32.red(12235202) / 255.0F - f;
        this.gCol = (float)FastColor.ARGB32.green(12235202) / 255.0F - f;
        this.bCol = (float)FastColor.ARGB32.blue(12235202) / 255.0F - f;
    }

    @Override
    public void tick() {
        this.gravity = 0.88F * this.gravity;
        this.friction = 0.92F * this.friction;
        super.tick();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet p_305831_) {
            this.sprites = p_305831_;
        }

        public Particle createParticle(
            SimpleParticleType p_306321_,
            ClientLevel p_306062_,
            double p_306327_,
            double p_305987_,
            double p_306266_,
            double p_306120_,
            double p_306315_,
            double p_306033_
        ) {
            return new DustPlumeParticle(p_306062_, p_306327_, p_305987_, p_306266_, p_306120_, p_306315_, p_306033_, 1.0F, this.sprites);
        }
    }
}
