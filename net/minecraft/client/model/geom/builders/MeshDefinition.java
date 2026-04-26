package net.minecraft.client.model.geom.builders;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.geom.PartPose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MeshDefinition {
    private final PartDefinition root = new PartDefinition(ImmutableList.of(), PartPose.ZERO);

    public PartDefinition getRoot() {
        return this.root;
    }
}
