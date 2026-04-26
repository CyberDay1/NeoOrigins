package net.minecraft.server.network.config;

import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.TagNetworkSerialization;

public class SynchronizeRegistriesTask implements ConfigurationTask {
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("synchronize_registries");
    private final List<KnownPack> requestedPacks;
    private final LayeredRegistryAccess<RegistryLayer> registries;

    public SynchronizeRegistriesTask(List<KnownPack> p_326209_, LayeredRegistryAccess<RegistryLayer> p_326049_) {
        this.requestedPacks = p_326209_;
        this.registries = p_326049_;
    }

    @Override
    public void start(Consumer<Packet<?>> p_326485_) {
        p_326485_.accept(new ClientboundSelectKnownPacks(this.requestedPacks));
    }

    private void sendRegistries(Consumer<Packet<?>> p_325923_, Set<KnownPack> p_326322_) {
        DynamicOps<Tag> dynamicops = this.registries.compositeAccess().createSerializationContext(NbtOps.INSTANCE);
        RegistrySynchronization.packRegistries(
            dynamicops,
            this.registries.getAccessFrom(RegistryLayer.WORLDGEN),
            p_326322_,
            (p_326010_, p_326361_) -> p_325923_.accept(new ClientboundRegistryDataPacket(p_326010_, p_326361_))
        );
        p_325923_.accept(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
    }

    public void handleResponse(List<KnownPack> p_326076_, Consumer<Packet<?>> p_326167_) {
        // Neo: instead of using either all available KnownPacks or none, allow partial fallback to normal syncing
        Set<KnownPack> requested = new java.util.HashSet<>(this.requestedPacks);
        requested.retainAll(p_326076_);
        this.sendRegistries(p_326167_, requested);
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
