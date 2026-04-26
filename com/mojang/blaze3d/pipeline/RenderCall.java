package com.mojang.blaze3d.pipeline;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface RenderCall {
    void execute();
}
