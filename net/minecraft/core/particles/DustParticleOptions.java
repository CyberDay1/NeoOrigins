package net.minecraft.core.particles;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class DustParticleOptions extends ScalableParticleOptionsBase {
    public static final Vector3f REDSTONE_PARTICLE_COLOR = Vec3.fromRGB24(16711680).toVector3f();
    public static final DustParticleOptions REDSTONE = new DustParticleOptions(REDSTONE_PARTICLE_COLOR, 1.0F);
    public static final MapCodec<DustParticleOptions> CODEC = RecordCodecBuilder.mapCodec(
        p_341566_ -> p_341566_.group(
                    ExtraCodecs.VECTOR3F.fieldOf("color").forGetter(p_253371_ -> p_253371_.color),
                    SCALE.fieldOf("scale").forGetter(ScalableParticleOptionsBase::getScale)
                )
                .apply(p_341566_, DustParticleOptions::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DustParticleOptions> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VECTOR3F, p_319429_ -> p_319429_.color, ByteBufCodecs.FLOAT, ScalableParticleOptionsBase::getScale, DustParticleOptions::new
    );
    private final Vector3f color;

    public DustParticleOptions(Vector3f p_253868_, float p_254154_) {
        super(p_254154_);
        this.color = p_253868_;
    }

    @Override
    public ParticleType<DustParticleOptions> getType() {
        return ParticleTypes.DUST;
    }

    public Vector3f getColor() {
        return this.color;
    }
}
