package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GustParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected GustParticle(ClientLevel p_312171_, double p_312015_, double p_312462_, double p_312867_, SpriteSet p_312279_) {
        super(p_312171_, p_312015_, p_312462_, p_312867_);
        this.sprites = p_312279_;
        this.setSpriteFromAge(p_312279_);
        this.lifetime = 12 + this.random.nextInt(4);
        this.quadSize = 1.0F;
        this.setSize(1.0F, 1.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    @Override
    public int getLightColor(float p_312697_) {
        return 15728880;
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet p_312644_) {
            this.sprites = p_312644_;
        }

        public Particle createParticle(
            SimpleParticleType p_311849_,
            ClientLevel p_312008_,
            double p_312498_,
            double p_312520_,
            double p_311822_,
            double p_312595_,
            double p_312181_,
            double p_312468_
        ) {
            return new GustParticle(p_312008_, p_312498_, p_312520_, p_311822_, this.sprites);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class SmallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SmallProvider(SpriteSet p_338457_) {
            this.sprites = p_338457_;
        }

        public Particle createParticle(
            SimpleParticleType p_338464_,
            ClientLevel p_338628_,
            double p_338495_,
            double p_338874_,
            double p_338214_,
            double p_338609_,
            double p_338492_,
            double p_338605_
        ) {
            Particle particle = new GustParticle(p_338628_, p_338495_, p_338874_, p_338214_, this.sprites);
            particle.scale(0.15F);
            return particle;
        }
    }
}
