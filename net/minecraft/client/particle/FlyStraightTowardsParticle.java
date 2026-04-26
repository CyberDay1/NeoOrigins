package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FlyStraightTowardsParticle extends TextureSheetParticle {
    private final double xStart;
    private final double yStart;
    private final double zStart;
    private final int startColor;
    private final int endColor;

    protected FlyStraightTowardsParticle(
        ClientLevel p_338359_,
        double p_338512_,
        double p_338787_,
        double p_338665_,
        double p_338833_,
        double p_338537_,
        double p_338840_,
        int p_338764_,
        int p_338316_
    ) {
        super(p_338359_, p_338512_, p_338787_, p_338665_);
        this.xd = p_338833_;
        this.yd = p_338537_;
        this.zd = p_338840_;
        this.xStart = p_338512_;
        this.yStart = p_338787_;
        this.zStart = p_338665_;
        this.xo = p_338512_ + p_338833_;
        this.yo = p_338787_ + p_338537_;
        this.zo = p_338665_ + p_338840_;
        this.x = this.xo;
        this.y = this.yo;
        this.z = this.zo;
        this.quadSize = 0.1F * (this.random.nextFloat() * 0.5F + 0.2F);
        this.hasPhysics = false;
        this.lifetime = (int)(Math.random() * 5.0) + 25;
        this.startColor = p_338764_;
        this.endColor = p_338316_;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void move(double p_338805_, double p_338843_, double p_338720_) {
    }

    @Override
    public int getLightColor(float p_338732_) {
        return 240;
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
            float f1 = 1.0F - f;
            this.x = this.xStart + this.xd * (double)f1;
            this.y = this.yStart + this.yd * (double)f1;
            this.z = this.zStart + this.zd * (double)f1;
            int i = FastColor.ARGB32.lerp(f, this.startColor, this.endColor);
            this.setColor((float)FastColor.ARGB32.red(i) / 255.0F, (float)FastColor.ARGB32.green(i) / 255.0F, (float)FastColor.ARGB32.blue(i) / 255.0F);
            this.setAlpha((float)FastColor.ARGB32.alpha(i) / 255.0F);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class OminousSpawnProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public OminousSpawnProvider(SpriteSet p_338292_) {
            this.sprite = p_338292_;
        }

        public Particle createParticle(
            SimpleParticleType p_338365_,
            ClientLevel p_338448_,
            double p_338829_,
            double p_338561_,
            double p_338765_,
            double p_338694_,
            double p_338802_,
            double p_338768_
        ) {
            FlyStraightTowardsParticle flystraighttowardsparticle = new FlyStraightTowardsParticle(
                p_338448_, p_338829_, p_338561_, p_338765_, p_338694_, p_338802_, p_338768_, -12210434, -1
            );
            flystraighttowardsparticle.scale(Mth.randomBetween(p_338448_.getRandom(), 3.0F, 5.0F));
            flystraighttowardsparticle.pickSprite(this.sprite);
            return flystraighttowardsparticle;
        }
    }
}
