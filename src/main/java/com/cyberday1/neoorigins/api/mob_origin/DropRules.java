package com.cyberday1.neoorigins.api.mob_origin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.List;

/**
 * Per-mob-origin drop table consumed by the Phase-5 global loot modifier.
 *
 * <p>Carries one of two {@link Strategy strategies}:
 * <ul>
 *   <li>{@link Strategy#INDEPENDENT_CHANCE} — each {@link DropEntry} rolls
 *       independently; {@code chance} / {@code rolls} per entry drive output,
 *       {@code weight} is ignored.</li>
 *   <li>{@link Strategy#WEIGHTED_POOL} — {@link #poolRolls} weighted picks
 *       across all entries (relative {@code weight}); {@code chance} /
 *       {@code rolls} on entries are ignored.</li>
 * </ul>
 *
 * <p>{@link DropMode} controls how the rolled stacks combine with the mob's
 * vanilla drops (additive vs. replace).
 */
public record DropRules(DropMode mode, Strategy strategy, int poolRolls, List<DropEntry> entries) {

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

    public enum Strategy implements StringRepresentable {
        /** Each entry's {@code chance} / {@code rolls} drive independent rolls. */
        INDEPENDENT_CHANCE("independent_chance"),
        /** Pool of weighted entries, picked {@link DropRules#poolRolls()} times. */
        WEIGHTED_POOL("weighted_pool");

        public static final Codec<Strategy> CODEC = StringRepresentable.fromEnum(Strategy::values);
        private final String id;
        Strategy(String id) { this.id = id; }
        @Override public String getSerializedName() { return id; }
    }

    public static final DropRules NONE =
        new DropRules(DropMode.ADDITIVE, Strategy.INDEPENDENT_CHANCE, 0, List.of());

    public static final Codec<DropRules> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        DropMode.CODEC.optionalFieldOf("mode", DropMode.ADDITIVE).forGetter(DropRules::mode),
        Strategy.CODEC.optionalFieldOf("strategy", Strategy.INDEPENDENT_CHANCE).forGetter(DropRules::strategy),
        Codec.INT.optionalFieldOf("pool_rolls", 0).forGetter(DropRules::poolRolls),
        DropEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(DropRules::entries)
    ).apply(inst, DropRules::new));

    public boolean isEmpty() { return entries.isEmpty(); }
}
