package net.minecraft.client.particle;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TerrainParticle extends TextureSheetParticle {
    private final BlockPos pos;
    private final float uo;
    private final float vo;

    public TerrainParticle(
        ClientLevel p_108282_, double p_108283_, double p_108284_, double p_108285_, double p_108286_, double p_108287_, double p_108288_, BlockState p_108289_
    ) {
        this(p_108282_, p_108283_, p_108284_, p_108285_, p_108286_, p_108287_, p_108288_, p_108289_, BlockPos.containing(p_108283_, p_108284_, p_108285_));
    }

    public TerrainParticle(
        ClientLevel p_172451_,
        double p_172452_,
        double p_172453_,
        double p_172454_,
        double p_172455_,
        double p_172456_,
        double p_172457_,
        BlockState p_172458_,
        BlockPos p_172459_
    ) {
        super(p_172451_, p_172452_, p_172453_, p_172454_, p_172455_, p_172456_, p_172457_);
        this.pos = p_172459_;
        this.setSprite(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(p_172458_));
        this.gravity = 1.0F;
        this.rCol = 0.6F;
        this.gCol = 0.6F;
        this.bCol = 0.6F;
        if (net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions.of(p_172458_).areBreakingParticlesTinted(p_172458_, p_172451_, p_172459_)) {
            int i = Minecraft.getInstance().getBlockColors().getColor(p_172458_, p_172451_, p_172459_, 0);
            this.rCol *= (float)(i >> 16 & 0xFF) / 255.0F;
            this.gCol *= (float)(i >> 8 & 0xFF) / 255.0F;
            this.bCol *= (float)(i & 0xFF) / 255.0F;
        }

        this.quadSize /= 2.0F;
        this.uo = this.random.nextFloat() * 3.0F;
        this.vo = this.random.nextFloat() * 3.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    @Override
    protected float getU0() {
        return this.sprite.getU((this.uo + 1.0F) / 4.0F);
    }

    @Override
    protected float getU1() {
        return this.sprite.getU(this.uo / 4.0F);
    }

    @Override
    protected float getV0() {
        return this.sprite.getV(this.vo / 4.0F);
    }

    @Override
    protected float getV1() {
        return this.sprite.getV((this.vo + 1.0F) / 4.0F);
    }

    @Override
    public int getLightColor(float p_108291_) {
        int i = super.getLightColor(p_108291_);
        return i == 0 && this.level.hasChunkAt(this.pos) ? LevelRenderer.getLightColor(this.level, this.pos) : i;
    }

    @Nullable
    static TerrainParticle createTerrainParticle(
        BlockParticleOption p_338588_,
        ClientLevel p_338858_,
        double p_338212_,
        double p_338420_,
        double p_338623_,
        double p_338262_,
        double p_338289_,
        double p_338400_
    ) {
        BlockState blockstate = p_338588_.getState();
        return !blockstate.isAir() && !blockstate.is(Blocks.MOVING_PISTON) && blockstate.shouldSpawnTerrainParticles()
            ? new TerrainParticle(p_338858_, p_338212_, p_338420_, p_338623_, p_338262_, p_338289_, p_338400_, blockstate).updateSprite(blockstate, p_338588_.getPos())
            : null;
    }

    public TerrainParticle updateSprite(BlockState state, BlockPos pos) { //FORGE: we cannot assume that the x y z of the particles match the block pos of the block.
        if (pos != null) // There are cases where we are not able to obtain the correct source pos, and need to fallback to the non-model data version
            this.setSprite(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getTexture(state, level, pos));
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    public static class DustPillarProvider implements ParticleProvider<BlockParticleOption> {
        @Nullable
        public Particle createParticle(
            BlockParticleOption p_338199_,
            ClientLevel p_338462_,
            double p_338552_,
            double p_338714_,
            double p_338211_,
            double p_338881_,
            double p_338238_,
            double p_338376_
        ) {
            Particle particle = TerrainParticle.createTerrainParticle(p_338199_, p_338462_, p_338552_, p_338714_, p_338211_, p_338881_, p_338238_, p_338376_);
            if (particle != null) {
                particle.setParticleSpeed(
                    p_338462_.random.nextGaussian() / 30.0, p_338238_ + p_338462_.random.nextGaussian() / 2.0, p_338462_.random.nextGaussian() / 30.0
                );
                particle.setLifetime(p_338462_.random.nextInt(20) + 20);
            }

            return particle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<BlockParticleOption> {
        @Nullable
        public Particle createParticle(
            BlockParticleOption p_108304_,
            ClientLevel p_108305_,
            double p_108306_,
            double p_108307_,
            double p_108308_,
            double p_108309_,
            double p_108310_,
            double p_108311_
        ) {
            return TerrainParticle.createTerrainParticle(p_108304_, p_108305_, p_108306_, p_108307_, p_108308_, p_108309_, p_108310_, p_108311_);
        }
    }
}
