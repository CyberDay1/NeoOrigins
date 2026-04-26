package com.mojang.blaze3d.shaders;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum FogShape {
    SPHERE(0),
    CYLINDER(1);

    private final int index;

    private FogShape(int p_202323_) {
        this.index = p_202323_;
    }

    public int getIndex() {
        return this.index;
    }
}
