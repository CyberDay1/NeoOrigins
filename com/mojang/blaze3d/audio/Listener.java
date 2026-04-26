package com.mojang.blaze3d.audio;

import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL10;

@OnlyIn(Dist.CLIENT)
public class Listener {
    private float gain = 1.0F;
    private ListenerTransform transform = ListenerTransform.INITIAL;

    public void setTransform(ListenerTransform p_314718_) {
        this.transform = p_314718_;
        Vec3 vec3 = p_314718_.position();
        Vec3 vec31 = p_314718_.forward();
        Vec3 vec32 = p_314718_.up();
        AL10.alListener3f(4100, (float)vec3.x, (float)vec3.y, (float)vec3.z);
        AL10.alListenerfv(4111, new float[]{(float)vec31.x, (float)vec31.y, (float)vec31.z, (float)vec32.x(), (float)vec32.y(), (float)vec32.z()});
    }

    public void setGain(float p_83738_) {
        AL10.alListenerf(4106, p_83738_);
        this.gain = p_83738_;
    }

    public float getGain() {
        return this.gain;
    }

    public void reset() {
        this.setTransform(ListenerTransform.INITIAL);
    }

    public ListenerTransform getTransform() {
        return this.transform;
    }
}
