package com.cyberday1.neoorigins.mixin;

import net.minecraft.resources.RegistryLoadTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the marker vanilla uses to drop an element without counting it as an error. */
@Mixin(RegistryLoadTask.class)
public interface RegistryLoadTaskAccessor {

    @Accessor("SKIPPED_ELEMENT_MARKER")
    static Exception neoorigins$skippedElementMarker() {
        throw new AssertionError();
    }
}
