package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public record PlaySoundEffect(Holder<SoundEvent> soundEvent, FloatProvider volume, FloatProvider pitch) implements EnchantmentEntityEffect {
    public static final MapCodec<PlaySoundEffect> CODEC = RecordCodecBuilder.mapCodec(
        p_345234_ -> p_345234_.group(
                    SoundEvent.CODEC.fieldOf("sound").forGetter(PlaySoundEffect::soundEvent),
                    FloatProvider.codec(1.0E-5F, 10.0F).fieldOf("volume").forGetter(PlaySoundEffect::volume),
                    FloatProvider.codec(1.0E-5F, 2.0F).fieldOf("pitch").forGetter(PlaySoundEffect::pitch)
                )
                .apply(p_345234_, PlaySoundEffect::new)
    );

    @Override
    public void apply(ServerLevel p_344971_, int p_344872_, EnchantedItemInUse p_345016_, Entity p_346106_, Vec3 p_345017_) {
        RandomSource randomsource = p_346106_.getRandom();
        if (!p_346106_.isSilent()) {
            p_344971_.playSound(
                null,
                p_345017_.x(),
                p_345017_.y(),
                p_345017_.z(),
                this.soundEvent,
                p_346106_.getSoundSource(),
                this.volume.sample(randomsource),
                this.pitch.sample(randomsource)
            );
        }
    }

    @Override
    public MapCodec<PlaySoundEffect> codec() {
        return CODEC;
    }
}
