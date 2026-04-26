package net.minecraft.client.multiplayer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TagCollector {
    private final Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags = new HashMap<>();

    public void append(ResourceKey<? extends Registry<?>> p_326295_, TagNetworkSerialization.NetworkPayload p_326414_) {
        this.tags.put(p_326295_, p_326414_);
    }

    private static void refreshBuiltInTagDependentData() {
        AbstractFurnaceBlockEntity.invalidateCache();
        Blocks.rebuildCache();
    }

    private void applyTags(RegistryAccess p_326395_, Predicate<ResourceKey<? extends Registry<?>>> p_326512_) {
        this.tags.forEach((p_326303_, p_326438_) -> {
            if (p_326512_.test((ResourceKey<? extends Registry<?>>)p_326303_)) {
                p_326438_.applyToRegistry(p_326395_.registryOrThrow((ResourceKey<? extends Registry<?>>)p_326303_));
            }
        });
    }

    public void updateTags(RegistryAccess p_326147_, boolean p_326486_) {
        if (p_326486_) {
            this.applyTags(p_326147_, RegistrySynchronization.NETWORKABLE_REGISTRIES::contains);
        } else {
            p_326147_.registries()
                .filter(p_325935_ -> !RegistrySynchronization.NETWORKABLE_REGISTRIES.contains(p_325935_.key()))
                .forEach(p_325919_ -> p_325919_.value().resetTags());
            this.applyTags(p_326147_, p_326446_ -> true);
            refreshBuiltInTagDependentData();
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.TagsUpdatedEvent(p_326147_, true, p_326486_));
    }
}
