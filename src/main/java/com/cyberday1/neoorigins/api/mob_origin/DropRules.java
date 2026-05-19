package com.cyberday1.neoorigins.api.mob_origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.List;

/**
 * Per-mob-origin drop table. Defined in Phase 1 for codec stability; the
 * global loot modifier that consumes it is generated in Phase 5.
 */
public record DropRules(DropMode mode, List<DropEntry> entries) {

    public enum DropMode implements StringRepresentable {
        /** Append these drops to the mob's vanilla drops. */
        ADDITIVE("additive"),
        /** Clear vanilla drops and emit only these. */
        REPLACE("replace");

        public static final Codec<DropMode> CODEC = StringRepresentable.fromEnum(DropMode::values);
        private final String id;
        DropMode(String id) { this.id = id; }
        @Override public String getSerializedName() { return id; }
    }

    public static final DropRules NONE = new DropRules(DropMode.ADDITIVE, List.of());

    public static final Codec<DropRules> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        DropMode.CODEC.optionalFieldOf("mode", DropMode.ADDITIVE).forGetter(DropRules::mode),
        DropEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(DropRules::entries)
    ).apply(inst, DropRules::new));

    public boolean isEmpty() { return entries.isEmpty(); }
}
