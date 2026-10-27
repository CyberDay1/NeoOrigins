package com.cyberday1.neoorigins.mixin;

import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the pyramid height {@link BeaconBlockEntity} keeps private, for the
 * {@code active_beacon} block condition.
 *
 * <p>Vanilla grants beacon effects on {@code levels > 0 && !beamSections.isEmpty()},
 * and only {@code beamSections} has a getter. The beam alone is not the same test:
 * it comes from an upward sky scan that never reads the base, and {@code BeaconBlock}
 * is itself a beam block, so a lone beacon under open sky has a beam section while
 * {@code levels} is 0. Reading the field is what makes the condition agree with the
 * game; rebuilding the base scan here would duplicate {@code updateBase} and drift.
 *
 * <p>Accessor rather than an access transformer: a read-only peek at one field.
 */
@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityLevelsAccessor {

    @Accessor("levels")
    int neoorigins$getLevels();
}
