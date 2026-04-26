package net.minecraft.client.resources.model;

import com.mojang.math.Transformation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ModelState extends net.neoforged.neoforge.client.extensions.ModelStateExtension {
    default Transformation getRotation() {
        return Transformation.identity();
    }

    default boolean isUvLocked() {
        return false;
    }
}
