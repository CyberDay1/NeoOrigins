package com.cyberday1.neoorigins.api.origin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record OriginUpgrade(
    ResourceLocation advancement,
    ResourceLocation origin,
    String announcement
) {
    /**
     * Sentinel advancement ID used when an Origin++-style {@code condition} predicate can't be
     * reduced to a plain advancement reference. Upgrades with this value will never fire — the
     * rest of the pack still loads cleanly.
     */
    public static final ResourceLocation NEVER_ADVANCEMENT =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "never");

    /** Native form: {@code {"advancement": "...", "origin": "...", "announcement": "..."}}. */
    private static final Codec<OriginUpgrade> NATIVE_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.fieldOf("advancement").forGetter(OriginUpgrade::advancement),
        ResourceLocation.CODEC.fieldOf("origin").forGetter(OriginUpgrade::origin),
        Codec.STRING.optionalFieldOf("announcement", "").forGetter(OriginUpgrade::announcement)
    ).apply(inst, OriginUpgrade::new));

    /**
     * Origin++ form: {@code {"condition": {...}, "origin": "...", "announcement": "..."}}.
     * We try to pull a plain advancement ID out of the condition payload (the common case is
     * {@code {"type": "origins:advancement", "advancement": "mod:foo"}}). If the condition is
     * more complex, the upgrade is kept with the NEVER sentinel so the pack still loads.
     * Also handles bare-string conditions (e.g. {@code "condition": "minecraft:story/mine_diamond"})
     * which some packs use as a shorthand for the advancement ID.
     * This codec is decode-only in practice — encoding always goes through NATIVE_CODEC via
     * {@link Codec#withAlternative}.
     */
    private static final Codec<OriginUpgrade> COMPAT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.PASSTHROUGH.fieldOf("condition").forGetter(u -> null),
        ResourceLocation.CODEC.fieldOf("origin").forGetter(OriginUpgrade::origin),
        Codec.STRING.optionalFieldOf("announcement", "").forGetter(OriginUpgrade::announcement)
    ).apply(inst, (condition, origin, ann) -> {
        ResourceLocation adv = extractAdvancement(condition);
        if (adv == null) {
            NeoOrigins.LOGGER.warn(
                "[CompatB] Origin upgrade to {} uses a `condition` predicate we can't reduce to a plain advancement — the upgrade will not fire. Convert to `advancement: \"mod:id\"` for now.",
                origin);
            adv = NEVER_ADVANCEMENT;
        }
        return new OriginUpgrade(adv, origin, ann);
    }));

    public static final Codec<OriginUpgrade> CODEC = Codec.withAlternative(NATIVE_CODEC, COMPAT_CODEC);

    private static ResourceLocation extractAdvancement(Dynamic<?> condition) {
        if (condition == null) return null;
        // Bare-string form: "condition": "minecraft:story/mine_diamond"
        // Treat the entire string as an advancement ID.
        var strOpt = condition.asString().result();
        if (strOpt.isPresent()) {
            return ResourceLocation.tryParse(strOpt.get());
        }
        // Object form: the condition has an `advancement` field,
        // e.g. {type: "origins:advancement", advancement: "mod:foo"}.
        var advOpt = condition.get("advancement").asString().result();
        if (advOpt.isPresent()) {
            return ResourceLocation.tryParse(advOpt.get());
        }
        return null;
    }
}
