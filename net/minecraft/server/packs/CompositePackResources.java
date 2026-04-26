package net.minecraft.server.packs;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

public class CompositePackResources implements PackResources {
    private final PackResources primaryPackResources;
    private final List<PackResources> packResourcesStack;

    public CompositePackResources(PackResources p_296246_, List<PackResources> p_294643_) {
        this.primaryPackResources = p_296246_;
        List<PackResources> list = new ArrayList<>(p_294643_.size() + 1);
        list.addAll(Lists.reverse(p_294643_));
        list.add(p_296246_);
        this.packResourcesStack = List.copyOf(list);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... p_295316_) {
        return this.primaryPackResources.getRootResource(p_295316_);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType p_295406_, ResourceLocation p_296382_) {
        for (PackResources packresources : this.packResourcesStack) {
            IoSupplier<InputStream> iosupplier = packresources.getResource(p_295406_, p_296382_);
            if (iosupplier != null) {
                return iosupplier;
            }
        }

        return null;
    }

    @Override
    public void listResources(PackType p_295490_, String p_296164_, String p_294691_, PackResources.ResourceOutput p_295313_) {
        Map<ResourceLocation, IoSupplier<InputStream>> map = new HashMap<>();

        for (PackResources packresources : this.packResourcesStack) {
            packresources.listResources(p_295490_, p_296164_, p_294691_, map::putIfAbsent);
        }

        map.forEach(p_295313_);
    }

    @Override
    public Set<String> getNamespaces(PackType p_294708_) {
        Set<String> set = new HashSet<>();

        for (PackResources packresources : this.packResourcesStack) {
            set.addAll(packresources.getNamespaces(p_294708_));
        }

        return set;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> p_295061_) throws IOException {
        return this.primaryPackResources.getMetadataSection(p_295061_);
    }

    @Override
    public PackLocationInfo location() {
        return this.primaryPackResources.location();
    }

    @Override
    public void close() {
        this.packResourcesStack.forEach(PackResources::close);
    }
}
