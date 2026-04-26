package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WitherRoseBlock extends FlowerBlock {
    public static final MapCodec<WitherRoseBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_308854_ -> p_308854_.group(EFFECTS_FIELD.forGetter(FlowerBlock::getSuspiciousEffects), propertiesCodec()).apply(p_308854_, WitherRoseBlock::new)
    );

    @Override
    public MapCodec<WitherRoseBlock> codec() {
        return CODEC;
    }

    public WitherRoseBlock(Holder<MobEffect> p_316323_, float p_332767_, BlockBehaviour.Properties p_304869_) {
        this(makeEffectList(p_316323_, p_332767_), p_304869_);
    }

    public WitherRoseBlock(SuspiciousStewEffects p_330573_, BlockBehaviour.Properties p_58236_) {
        super(p_330573_, p_58236_);
    }

    @Override
    protected boolean mayPlaceOn(BlockState p_58248_, BlockGetter p_58249_, BlockPos p_58250_) {
        return super.mayPlaceOn(p_58248_, p_58249_, p_58250_)
            || p_58248_.is(Blocks.NETHERRACK)
            || p_58248_.is(Blocks.SOUL_SAND)
            || p_58248_.is(Blocks.SOUL_SOIL);
    }

    @Override
    public void animateTick(BlockState p_222687_, Level p_222688_, BlockPos p_222689_, RandomSource p_222690_) {
        VoxelShape voxelshape = this.getShape(p_222687_, p_222688_, p_222689_, CollisionContext.empty());
        Vec3 vec3 = voxelshape.bounds().getCenter();
        double d0 = (double)p_222689_.getX() + vec3.x;
        double d1 = (double)p_222689_.getZ() + vec3.z;

        for (int i = 0; i < 3; i++) {
            if (p_222690_.nextBoolean()) {
                p_222688_.addParticle(
                    ParticleTypes.SMOKE,
                    d0 + p_222690_.nextDouble() / 5.0,
                    (double)p_222689_.getY() + (0.5 - p_222690_.nextDouble()),
                    d1 + p_222690_.nextDouble() / 5.0,
                    0.0,
                    0.0,
                    0.0
                );
            }
        }
    }

    @Override
    protected void entityInside(BlockState p_58238_, Level p_58239_, BlockPos p_58240_, Entity p_58241_) {
        if (!p_58239_.isClientSide && p_58239_.getDifficulty() != Difficulty.PEACEFUL) {
            if (p_58241_ instanceof LivingEntity livingentity && !livingentity.isInvulnerableTo(p_58239_.damageSources().wither())) {
                livingentity.addEffect(new MobEffectInstance(MobEffects.WITHER, 40));
            }
        }
    }
}
