package net.minecraft.server.packs.metadata.pack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.InclusiveRange;

public record PackMetadataSection(Component description, int packFormat, Optional<InclusiveRange<Integer>> supportedFormats) {
    public static final Codec<PackMetadataSection> CODEC = RecordCodecBuilder.create(
        p_337567_ -> p_337567_.group(
                    ComponentSerialization.CODEC.fieldOf("description").forGetter(PackMetadataSection::description),
                    Codec.INT.fieldOf("pack_format").forGetter(PackMetadataSection::packFormat),
                    InclusiveRange.codec(Codec.INT).lenientOptionalFieldOf("supported_formats").forGetter(PackMetadataSection::supportedFormats)
                )
                .apply(p_337567_, PackMetadataSection::new)
    );
    public static final MetadataSectionType<PackMetadataSection> TYPE = MetadataSectionType.fromCodec("pack", CODEC);

    public PackMetadataSection(Component description, int packVersion) {
        this(description, packVersion, Optional.empty());
    }
}
