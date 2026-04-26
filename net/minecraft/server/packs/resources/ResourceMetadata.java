package net.minecraft.server.packs.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.GsonHelper;

public interface ResourceMetadata {
    ResourceMetadata EMPTY = new ResourceMetadata() {
        @Override
        public <T> Optional<T> getSection(MetadataSectionSerializer<T> p_215584_) {
            return Optional.empty();
        }
    };
    IoSupplier<ResourceMetadata> EMPTY_SUPPLIER = () -> EMPTY;

    static ResourceMetadata fromJsonStream(InputStream p_215581_) throws IOException {
        ResourceMetadata resourcemetadata;
        try (BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(p_215581_, StandardCharsets.UTF_8))) {
            final JsonObject jsonobject = GsonHelper.parse(bufferedreader);
            resourcemetadata = new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionSerializer<T> p_215589_) {
                    String s = p_215589_.getMetadataSectionName();
                    return jsonobject.has(s) ? Optional.of(p_215589_.fromJson(GsonHelper.getAsJsonObject(jsonobject, s))) : Optional.empty();
                }
            };
        }

        return resourcemetadata;
    }

    <T> Optional<T> getSection(MetadataSectionSerializer<T> p_215579_);

    default ResourceMetadata copySections(Collection<MetadataSectionSerializer<?>> p_295308_) {
        ResourceMetadata.Builder resourcemetadata$builder = new ResourceMetadata.Builder();

        for (MetadataSectionSerializer<?> metadatasectionserializer : p_295308_) {
            this.copySection(resourcemetadata$builder, metadatasectionserializer);
        }

        return resourcemetadata$builder.build();
    }

    private <T> void copySection(ResourceMetadata.Builder p_295962_, MetadataSectionSerializer<T> p_294347_) {
        this.getSection(p_294347_).ifPresent(p_293816_ -> p_295962_.put(p_294347_, (T)p_293816_));
    }

    public static class Builder {
        private final ImmutableMap.Builder<MetadataSectionSerializer<?>, Object> map = ImmutableMap.builder();

        public <T> ResourceMetadata.Builder put(MetadataSectionSerializer<T> p_294292_, T p_294133_) {
            this.map.put(p_294292_, p_294133_);
            return this;
        }

        public ResourceMetadata build() {
            final ImmutableMap<MetadataSectionSerializer<?>, Object> immutablemap = this.map.build();
            return immutablemap.isEmpty() ? ResourceMetadata.EMPTY : new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionSerializer<T> p_295383_) {
                    return Optional.ofNullable((T)immutablemap.get(p_295383_));
                }
            };
        }
    }
}
