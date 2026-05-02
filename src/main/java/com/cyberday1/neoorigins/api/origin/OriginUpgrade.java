package com.cyberday1.neoorigins.api.origin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record OriginUpgrade(
    Identifier advancement,
    Identifier origin,
    String announcement
) {
    public static final Identifier NEVER_ADVANCEMENT =
        Identifier.fromNamespaceAndPath("neoorigins", "never");

    private static final Codec<OriginUpgrade> NATIVE_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.fieldOf("advancement").forGetter(OriginUpgrade::advancement),
        Identifier.CODEC.fieldOf("origin").forGetter(OriginUpgrade::origin),
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
        Identifier.CODEC.fieldOf("origin").forGetter(OriginUpgrade::origin),
        Codec.STRING.optionalFieldOf("announcement", "").forGetter(OriginUpgrade::announcement)
    ).apply(inst, (condition, origin, ann) -> {
        Identifier adv = extractAdvancement(condition);
        if (adv == null) {
            NeoOrigins.LOGGER.warn("[CompatB] Origin upgrade to {} uses a `condition` predicate we can't reduce to a plain advancement — the upgrade will not fire. Convert to `advancement: \"mod:id\"` for now.", origin);
            adv = NEVER_ADVANCEMENT;
        }
        return new OriginUpgrade(adv, origin, ann);
    }));

    public static final Codec<OriginUpgrade> CODEC = Codec.withAlternative(NATIVE_CODEC, COMPAT_CODEC);

    private static Identifier extractAdvancement(Dynamic<?> condition) {
        if (condition == null) return null;
        // Bare-string form: "condition": "minecraft:story/mine_diamond"
        // Treat the entire string as an advancement ID.
        var strOpt = condition.asString().result();
        if (strOpt.isPresent()) {
            return Identifier.tryParse(strOpt.get());
        }
        // Object form: the condition has an `advancement` field,
        // e.g. {type: "origins:advancement", advancement: "mod:foo"}.
        var advOpt = condition.get("advancement").asString().result();
        if (advOpt.isPresent()) {
            return Identifier.tryParse(advOpt.get());
        }
        return null;
    }
}
