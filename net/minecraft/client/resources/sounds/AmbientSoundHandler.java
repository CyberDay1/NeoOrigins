package net.minecraft.client.resources.sounds;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface AmbientSoundHandler {
    void tick();
}
