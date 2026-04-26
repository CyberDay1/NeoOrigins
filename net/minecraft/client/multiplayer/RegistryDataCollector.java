package net.minecraft.client.multiplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagNetworkSerialization;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RegistryDataCollector {
    @Nullable
    private RegistryDataCollector.ContentsCollector contentsCollector;
    @Nullable
    private TagCollector tagCollector;

    public void appendContents(ResourceKey<? extends Registry<?>> p_321794_, List<RegistrySynchronization.PackedRegistryEntry> p_321772_) {
        if (this.contentsCollector == null) {
            this.contentsCollector = new RegistryDataCollector.ContentsCollector();
        }

        this.contentsCollector.append(p_321794_, p_321772_);
    }

    public void appendTags(Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> p_321771_) {
        if (this.tagCollector == null) {
            this.tagCollector = new TagCollector();
        }

        p_321771_.forEach(this.tagCollector::append);
    }

    public RegistryAccess.Frozen collectGameRegistries(ResourceProvider p_326319_, RegistryAccess p_321556_, boolean p_321683_) {
        LayeredRegistryAccess<ClientRegistryLayer> layeredregistryaccess = ClientRegistryLayer.createRegistryAccess();
        RegistryAccess registryaccess;
        if (this.contentsCollector != null) {
            RegistryAccess.Frozen registryaccess$frozen = layeredregistryaccess.getAccessForLoading(ClientRegistryLayer.REMOTE);
            RegistryAccess.Frozen registryaccess$frozen1 = this.contentsCollector.loadRegistries(p_326319_, registryaccess$frozen).freeze();
            registryaccess = layeredregistryaccess.replaceFrom(ClientRegistryLayer.REMOTE, registryaccess$frozen1).compositeAccess();
        } else {
            registryaccess = p_321556_;
        }

        if (this.tagCollector != null) {
            this.tagCollector.updateTags(registryaccess, p_321683_);
        }

        return registryaccess.freeze();
    }

    @OnlyIn(Dist.CLIENT)
    static class ContentsCollector {
        private final Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> elements = new HashMap<>();

        public void append(ResourceKey<? extends Registry<?>> p_321577_, List<RegistrySynchronization.PackedRegistryEntry> p_321551_) {
            this.elements.computeIfAbsent(p_321577_, p_321745_ -> new ArrayList<>()).addAll(p_321551_);
        }

        public RegistryAccess loadRegistries(ResourceProvider p_326039_, RegistryAccess p_321627_) {
            return RegistryDataLoader.load(this.elements, p_326039_, p_321627_, RegistryDataLoader.SYNCHRONIZED_REGISTRIES);
        }
    }
}
