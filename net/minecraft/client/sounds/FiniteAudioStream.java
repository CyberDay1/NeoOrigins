package net.minecraft.client.sounds;

import java.io.IOException;
import java.nio.ByteBuffer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface FiniteAudioStream extends AudioStream {
    ByteBuffer readAll() throws IOException;
}
