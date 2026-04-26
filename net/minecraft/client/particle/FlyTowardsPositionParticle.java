package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FlyTowardsPositionParticle extends TextureSheetParticle {
    private final double xStart;
    private final double yStart;
    private final double zStart;
    private final boolean isGlowing;
    private final Particle.LifetimeAlpha lifetimeAlpha;

    protected FlyTowardsPositionParticle(
        ClientLevel p_323658_, double p_324523_, double p_324079_, double p_324377_, double p_324144_, double p_324286_, double p_323511_
    ) {
        this(p_323658_, p_324523_, p_324079_, p_324377_, p_324144_, p_324286_, p_323511_, false, Particle.LifetimeAlpha.ALWAYS_OPAQUE);
    }

    FlyTowardsPositionParticle(
        ClientLevel p_323938_,
        double p_323720_,
        double p_324407_,
        double p_324020_,
        double p_323737_,
        double p_323883_,
        double p_324615_,
        boolean p_323911_,
        Particle.LifetimeAlpha p_324427_
    ) {
        super(p_323938_, p_323720_, p_324407_, p_324020_);
        this.isGlowing = p_323911_;
        this.lifetimeAlpha = p_324427_;
        this.setAlpha(p_324427_.startAlpha());
        this.xd = p_323737_;
        this.yd = p_323883_;
        this.zd = p_324615_;
        this.xStart = p_323720_;
        this.yStart = p_324407_;
        this.zStart = p_324020_;
        this.xo = p_323720_ + p_323737_;
        this.yo = p_324407_ + p_323883_;
        this.zo = p_324020_ + p_324615_;
        this.x = this.xo;
        this.y = this.yo;
        this.z = this.zo;
        this.quadSize = 0.1F * (this.random.nextFloat() * 0.5F + 0.2F);
        float f = this.random.nextFloat() * 0.6F + 0.4F;
        this.rCol = 0.9F * f;
        this.gCol = 0.9F * f;
        this.bCol = f;
        this.hasPhysics = false;
        this.lifetime = (int)(Math.random() * 10.0) + 30;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.lifetimeAlpha.isOpaque() ? ParticleRenderType.PARTICLE_SHEET_OPAQUE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void move(double p_324487_, double p_323538_, double p_324364_) {
        this.setBoundingBox(this.getBoundingBox().move(p_324487_, p_323538_, p_324364_));
        this.setLocationFromBoundingbox();
    }

    @Override
    public int getLightColor(float p_323664_) {
        if (this.isGlowing) {
            return 240;
        } else {
            int i = super.getLightColor(p_323664_);
            float f = (float)this.age / (float)this.lifetime;
            f *= f;
            f *= f;
            int j = i & 0xFF;
            int k = i >> 16 & 0xFF;
            k += (int)(f * 15.0F * 16.0F);
            if (k > 240) {
                k = 240;
            }

            return j | k << 16;
        }
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            float f = (float)this.age / (float)this.lifetime;
            f = 1.0F - f;
            float f1 = 1.0F - f;
            f1 *= f1;
            f1 *= f1;
            this.x = this.xStart + this.xd * (double)f;
            this.y = this.yStart + this.yd * (double)f - (double)(f1 * 1.2F);
            this.z = this.zStart + this.zd * (double)f;
        }
    }

    @Override
    public void render(VertexConsumer p_324177_, Camera p_323683_, float p_323936_) {
        this.setAlpha(this.lifetimeAlpha.currentAlphaForAge(this.age, this.lifetime, p_323936_));
        super.render(p_324177_, p_323683_, p_323936_);
    }

    @OnlyIn(Dist.CLIENT)
    public static class EnchantProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public EnchantProvider(SpriteSet p_324184_) {
            this.sprite = p_324184_;
        }

        public Particle createParticle(
            SimpleParticleType p_323913_,
            ClientLevel p_323933_,
            double p_324281_,
            double p_323543_,
            double p_324051_,
            double p_323907_,
            double p_324082_,
            double p_323993_
        ) {
            FlyTowardsPositionParticle flytowardspositionparticle = new FlyTowardsPositionParticle(
                p_323933_, p_324281_, p_323543_, p_324051_, p_323907_, p_324082_, p_323993_
            );
            flytowardspositionparticle.pickSprite(this.sprite);
            return flytowardspositionparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class NautilusProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public NautilusProvider(SpriteSet p_323905_) {
            this.sprite = p_323905_;
        }

        public Particle createParticle(
            SimpleParticleType p_323965_,
            ClientLevel p_324479_,
            double p_323514_,
            double p_323507_,
            double p_324033_,
            double p_323555_,
            double p_323611_,
            double p_323980_
        ) {
            FlyTowardsPositionParticle flytowardspositionparticle = new FlyTowardsPositionParticle(
                p_324479_, p_323514_, p_323507_, p_324033_, p_323555_, p_323611_, p_323980_
            );
            flytowardspositionparticle.pickSprite(this.sprite);
            return flytowardspositionparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class VaultConnectionProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public VaultConnectionProvider(SpriteSet p_324409_) {
            this.sprite = p_324409_;
        }

        public Particle createParticle(
            SimpleParticleType p_324345_,
            ClientLevel p_324607_,
            double p_324458_,
            double p_324613_,
            double p_324276_,
            double p_323805_,
            double p_324556_,
            double p_323945_
        ) {
            FlyTowardsPositionParticle flytowardspositionparticle = new FlyTowardsPositionParticle(
                p_324607_, p_324458_, p_324613_, p_324276_, p_323805_, p_324556_, p_323945_, true, new Particle.LifetimeAlpha(0.0F, 0.6F, 0.25F, 1.0F)
            );
            flytowardspositionparticle.scale(1.5F);
            flytowardspositionparticle.pickSprite(this.sprite);
            return flytowardspositionparticle;
        }
    }
}
