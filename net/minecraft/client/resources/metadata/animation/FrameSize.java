package net.minecraft.client.resources.metadata.animation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record FrameSize(int width, int height) {
}
