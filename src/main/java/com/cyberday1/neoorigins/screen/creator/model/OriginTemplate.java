package com.cyberday1.neoorigins.screen.creator.model;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One snapshot of a currently-loaded origin (or class) the in-game creator
 * can "Load template" from. Carries the raw post-translation JSON bodies for
 * both the origin file and every power it references so a templated draft
 * matches the live origin verbatim — no codec round-trip.
 *
 * <p>The template is read-only data; the picker turns it into a mutable
 * {@link OriginDraft} via {@link com.cyberday1.neoorigins.service.OriginTemplateLoader}.
 *
 * @param originId    full Identifier of the source origin (e.g. {@code neoorigins:abyssal}).
 *                    The picker honors this when filling the draft so saving overrides the source.
 * @param layerId     layer the source origin belongs to — used to split Origins vs Classes in the picker.
 * @param displayName a pre-resolved display string (translation-key resolved server-side, or
 *                    the raw value when no resolution was possible). Picker label only.
 * @param icon        icon item id for the picker thumbnail; falls back when the origin lacks one.
 * @param impact      Origins impact dots (0–3) — sortable in the picker.
 * @param originBody  raw origin JSON (post-translation), exactly as parsed by OriginDataManager,
 *                    sans the synthetic {@code id} field. Cloned into the draft on load.
 * @param powers      ordered map (powerId → raw power JSON body). Order matches the origin's
 *                    declaration so the loaded draft renders its power list in source order.
 */
public record OriginTemplate(
        Identifier originId,
        Identifier layerId,
        String displayName,
        Identifier icon,
        int impact,
        String originBody,
        LinkedHashMap<Identifier, String> powers) {

    public OriginTemplate {
        // Defensive copy so callers can't mutate the picker's view of the data.
        powers = new LinkedHashMap<>(powers);
    }

    /** True when this template lives in the {@code neoorigins:class} layer —
     *  i.e. the picker should show it under the "Classes" tab. */
    public boolean isClass() {
        return layerId != null && "class".equals(layerId.getPath())
            && "neoorigins".equals(layerId.getNamespace());
    }

    /** Read-only view of the power-id → raw-JSON map (the record accessor
     *  returns the mutable backing map so callers can't mutate it through
     *  this convenience getter). */
    public Map<Identifier, String> powersView() {
        return java.util.Collections.unmodifiableMap(powers);
    }
}
