package net.minecraft.client.model;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ModelUtils {
    public static float rotlerpRad(float p_103126_, float p_103127_, float p_103128_) {
        float f = p_103127_ - p_103126_;

        while (f < (float) -Math.PI) {
            f += (float) (Math.PI * 2);
        }

        while (f >= (float) Math.PI) {
            f -= (float) (Math.PI * 2);
        }

        return p_103126_ + p_103128_ * f;
    }
}
