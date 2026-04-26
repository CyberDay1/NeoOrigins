package net.minecraft.resources;

import java.util.List;
import java.util.Map;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class FileToIdConverter {
    private final String prefix;
    private final String extension;

    public FileToIdConverter(String p_248876_, String p_251478_) {
        this.prefix = p_248876_;
        this.extension = p_251478_;
    }

    public static FileToIdConverter json(String p_248754_) {
        return new FileToIdConverter(p_248754_, ".json");
    }

    public ResourceLocation idToFile(ResourceLocation p_251878_) {
        return p_251878_.withPath(this.prefix + "/" + p_251878_.getPath() + this.extension);
    }

    public ResourceLocation fileToId(ResourceLocation p_249595_) {
        String s = p_249595_.getPath();
        return p_249595_.withPath(s.substring(this.prefix.length() + 1, s.length() - this.extension.length()));
    }

    public Map<ResourceLocation, Resource> listMatchingResources(ResourceManager p_252045_) {
        return p_252045_.listResources(this.prefix, p_251986_ -> p_251986_.getPath().endsWith(this.extension));
    }

    public Map<ResourceLocation, List<Resource>> listMatchingResourceStacks(ResourceManager p_249881_) {
        return p_249881_.listResourceStacks(this.prefix, p_248700_ -> p_248700_.getPath().endsWith(this.extension));
    }

    /**
     * List all resources under the given namespace which match this converter
     *
     * @param manager   The resource manager to collect the resources from
     * @param namespace The namespace to search under
     * @return All resources from the given namespace which match this converter
     */
    public Map<ResourceLocation, Resource> listMatchingResourcesFromNamespace(ResourceManager manager, String namespace) {
        return manager.listResources(this.prefix, path -> path.getNamespace().equals(namespace) && path.getPath().endsWith(this.extension));
    }

    /**
     * List all resource stacks under the given namespace which match this converter
     *
     * @param manager   The resource manager to collect the resources from
     * @param namespace The namespace to search under
     * @return All resource stacks from the given namespace which match this converter
     */
    public Map<ResourceLocation, List<Resource>> listMatchingResourceStacksFromNamespace(ResourceManager manager, String namespace) {
        return manager.listResourceStacks(this.prefix, path -> path.getNamespace().equals(namespace) && path.getPath().endsWith(this.extension));
    }
}
