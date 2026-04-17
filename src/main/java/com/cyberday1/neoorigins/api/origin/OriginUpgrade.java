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
        var advOpt = condition.get("advancement").asString().result();
        if (advOpt.isPresent()) {
            return Identifier.tryParse(advOpt.get());
        }
        return null;
    }
}
