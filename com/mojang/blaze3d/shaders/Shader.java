package com.mojang.blaze3d.shaders;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface Shader {
    int getId();

    void markDirty();

    Program getVertexProgram();

    Program getFragmentProgram();

    void attachToProgram();
}
