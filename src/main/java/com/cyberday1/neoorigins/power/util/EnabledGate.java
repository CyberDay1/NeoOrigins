package com.cyberday1.neoorigins.power.util;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.function.Function;

/**
 * Shared "config kill-switch" gate for power configs.
 *
 * <p>Several power types honour a top-level {@code "enabled": false} (injected
 * by the power_overrides system when a server admin disables a power) that
 * collapses the power to a no-op. This helper centralises both forms of that
 * flag — the hand-rolled JSON read and the {@code RecordCodecBuilder} field — so
 * the "absent → enabled, present → its boolean value" semantics stay identical
 * across power types.
 */
public final class EnabledGate {

    private EnabledGate() {}

    /**
     * Reads the top-level {@code enabled} flag from a power config JSON object.
     * Absent means enabled (the historical default); present returns its boolean
     * value. Semantics identical to the hand-rolled
     * {@code !obj.has("enabled") || obj.get("enabled").getAsBoolean()} idiom.
     */
    public static boolean isEnabled(JsonObject obj) {
        return !obj.has("enabled") || obj.get("enabled").getAsBoolean();
    }

    /**
     * Codec fragment for the {@code enabled} field in a {@link RecordCodecBuilder}
     * group: an optional boolean defaulting to {@code true}. Equivalent to
     * {@code Codec.BOOL.optionalFieldOf("enabled", true).forGetter(getter)}.
     *
     * @param getter extracts the {@code enabled} value from the config record
     */
    public static <C> RecordCodecBuilder<C, Boolean> field(Function<C, Boolean> getter) {
        return Codec.BOOL.optionalFieldOf("enabled", true).forGetter(getter::apply);
    }
}
