package net.minecraft.data.worldgen;

import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class VillagePools {
    public static void bootstrap(BootstrapContext<StructureTemplatePool> p_321519_) {
        PlainVillagePools.bootstrap(p_321519_);
        SnowyVillagePools.bootstrap(p_321519_);
        SavannaVillagePools.bootstrap(p_321519_);
        DesertVillagePools.bootstrap(p_321519_);
        TaigaVillagePools.bootstrap(p_321519_);
    }
}
