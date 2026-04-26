package net.minecraft.client.sounds;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface SoundEventListener {
    void onPlaySound(SoundInstance p_120342_, WeighedSoundEvents p_120343_, float p_314712_);
}
