package net.minecraft.client.resources.sounds;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface TickableSoundInstance extends SoundInstance {
    boolean isStopped();

    void tick();
}
