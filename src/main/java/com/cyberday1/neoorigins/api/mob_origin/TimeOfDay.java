package com.cyberday1.neoorigins.api.mob_origin;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;

/**
 * Coarse day/night spawn gate for {@link SpawnRules}. {@code ANY} never
 * restricts. Evaluated in Phase 2.
 */
public enum TimeOfDay implements StringRepresentable {
    ANY("any"),
    DAY("day"),
    NIGHT("night");

    public static final Codec<TimeOfDay> CODEC = StringRepresentable.fromEnum(TimeOfDay::values);

    private final String id;

    TimeOfDay(String id) { this.id = id; }

    @Override
    public String getSerializedName() { return id; }

    /** True if the level's current time satisfies this gate. Computed from
     *  day-time directly (vanilla: 0–12999 day, 13000–23999 night) so it is
     *  portable across MC versions that rename {@code Level.isDay()}. */
    public boolean matches(Level level) {
        if (this == ANY) return true;
        boolean day = (level.getDefaultClockTime() % 24000L) < 13000L;
        return this == DAY ? day : !day;
    }
}
