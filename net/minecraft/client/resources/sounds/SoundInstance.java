package net.minecraft.client.resources.sounds;

import javax.annotation.Nullable;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface SoundInstance {
    ResourceLocation getLocation();

    @Nullable
    WeighedSoundEvents resolve(SoundManager p_119841_);

    Sound getSound();

    SoundSource getSource();

    boolean isLooping();

    boolean isRelative();

    int getDelay();

    float getVolume();

    float getPitch();

    double getX();

    double getY();

    double getZ();

    SoundInstance.Attenuation getAttenuation();

    default boolean canStartSilent() {
        return false;
    }

    default boolean canPlaySound() {
        return true;
    }

    static RandomSource createUnseededRandom() {
        return RandomSource.create();
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Attenuation {
        NONE,
        LINEAR;
    }

    // Neo: Allow SoundInstances to change source of audio stream if needed
    default java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getStream(net.minecraft.client.sounds.SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return soundBuffers.getStream(sound.getPath(), looping);
    }
}
