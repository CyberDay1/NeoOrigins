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

public class DustColorTransitionOptions extends ScalableParticleOptionsBase {
    public static final Vector3f SCULK_PARTICLE_COLOR = Vec3.fromRGB24(3790560).toVector3f();
    public static final DustColorTransitionOptions SCULK_TO_REDSTONE = new DustColorTransitionOptions(
        SCULK_PARTICLE_COLOR, DustParticleOptions.REDSTONE_PARTICLE_COLOR, 1.0F
    );
    public static final MapCodec<DustColorTransitionOptions> CODEC = RecordCodecBuilder.mapCodec(
        p_341564_ -> p_341564_.group(
                    ExtraCodecs.VECTOR3F.fieldOf("from_color").forGetter(p_341563_ -> p_341563_.fromColor),
                    ExtraCodecs.VECTOR3F.fieldOf("to_color").forGetter(p_253367_ -> p_253367_.toColor),
                    SCALE.fieldOf("scale").forGetter(ScalableParticleOptionsBase::getScale)
                )
                .apply(p_341564_, DustColorTransitionOptions::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DustColorTransitionOptions> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VECTOR3F,
        p_341565_ -> p_341565_.fromColor,
        ByteBufCodecs.VECTOR3F,
        p_319428_ -> p_319428_.toColor,
        ByteBufCodecs.FLOAT,
        ScalableParticleOptionsBase::getScale,
        DustColorTransitionOptions::new
    );
    private final Vector3f fromColor;
    private final Vector3f toColor;

    public DustColorTransitionOptions(Vector3f p_254199_, Vector3f p_254529_, float p_254178_) {
        super(p_254178_);
        this.fromColor = p_254199_;
        this.toColor = p_254529_;
    }

    public Vector3f getFromColor() {
        return this.fromColor;
    }

    public Vector3f getToColor() {
        return this.toColor;
    }

    @Override
    public ParticleType<DustColorTransitionOptions> getType() {
        return ParticleTypes.DUST_COLOR_TRANSITION;
    }
}
