package net.minecraft.tags;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;

public record TagFile(List<TagEntry> entries, boolean replace, List<TagEntry> remove) {
    public static final Codec<TagFile> CODEC = RecordCodecBuilder.create(
        p_215967_ -> p_215967_.group(
                    TagEntry.CODEC.listOf().fieldOf("values").forGetter(TagFile::entries),
                    Codec.BOOL.optionalFieldOf("replace", Boolean.valueOf(false)).forGetter(TagFile::replace),
                    TagEntry.CODEC.listOf().optionalFieldOf("remove", List.of()).forGetter(TagFile::remove)
                )
                .apply(p_215967_, TagFile::new)
    );

    /**
     * @deprecated Forge: Use {@link TagFile#TagFile(List, boolean, List)} which has support for remove entries.
     */
    @Deprecated
    public TagFile(List<TagEntry> entries, boolean replace) {
        this(entries, replace, List.of());
    }
}
