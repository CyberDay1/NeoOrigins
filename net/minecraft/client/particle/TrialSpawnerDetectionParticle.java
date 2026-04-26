package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TrialSpawnerDetectionParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private static final int BASE_LIFETIME = 8;

    protected TrialSpawnerDetectionParticle(
        ClientLevel p_312476_,
        double p_312565_,
        double p_312753_,
        double p_311963_,
        double p_312472_,
        double p_311956_,
        double p_312272_,
        float p_312057_,
        SpriteSet p_312002_
    ) {
        super(p_312476_, p_312565_, p_312753_, p_311963_, 0.0, 0.0, 0.0);
        this.sprites = p_312002_;
        this.friction = 0.96F;
        this.gravity = -0.1F;
        this.speedUpWhenYMotionIsBlocked = true;
        this.xd *= 0.0;
        this.yd *= 0.9;
        this.zd *= 0.0;
        this.xd += p_312472_;
        this.yd += p_311956_;
        this.zd += p_312272_;
        this.quadSize *= 0.75F * p_312057_;
        this.lifetime = (int)(8.0F / Mth.randomBetween(this.random, 0.5F, 1.0F) * p_312057_);
        this.lifetime = Math.max(this.lifetime, 1);
        this.setSpriteFromAge(p_312002_);
        this.hasPhysics = true;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public int getLightColor(float p_312871_) {
        return 240;
    }

    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        return SingleQuadParticle.FacingCameraMode.LOOKAT_Y;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public float getQuadSize(float p_312246_) {
        return this.quadSize * Mth.clamp(((float)this.age + p_312246_) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet p_312414_) {
            this.sprites = p_312414_;
        }

        public Particle createParticle(
            SimpleParticleType p_312488_,
            ClientLevel p_312627_,
            double p_312195_,
            double p_312322_,
            double p_312229_,
            double p_312548_,
            double p_312570_,
            double p_311993_
        ) {
            return new TrialSpawnerDetectionParticle(p_312627_, p_312195_, p_312322_, p_312229_, p_312548_, p_312570_, p_311993_, 1.5F, this.sprites);
        }
    }
}
