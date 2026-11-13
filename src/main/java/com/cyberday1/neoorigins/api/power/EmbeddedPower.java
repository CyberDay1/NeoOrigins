package com.cyberday1.neoorigins.api.power;

import com.cyberday1.neoorigins.data.EmbeddedPowerLoading;
import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;

/**
 * A named or inline child power decoded by {@code NeoOriginsAPI.powerCodec(slot)}.
 * Defining a child does not grant it or forward any lifecycle hooks. The parent
 * author chooses either explicit forwarding through {@link #holder()} or a normal
 * source-owned grant through {@code NeoOriginsAPI.grantPower(player, child.id(), source)}.
 */
public final class EmbeddedPower {
    private final Identifier id;
    private final JsonElement source;

    /** Internal codec constructor. Use the API codec to create definitions. */
    public EmbeddedPower(Identifier id, JsonElement source) {
        this.id = id;
        this.source = source.deepCopy();
    }

    /** Stable child ID; available after decoding as part of a loaded or registered power. */
    public Identifier id() {
        if (id == null) throw new IllegalStateException("Inline power was decoded without a parent power ID");
        return id;
    }

    /** Current definition, or the old generation while a reload revokes the parent. */
    public PowerHolder<?> holder() {
        PowerHolder<?> holder = EmbeddedPowerLoading.lookup(id());
        if (holder == null) throw new IllegalStateException("Missing embedded power " + id);
        return holder;
    }

    /** Original reference string or inline object, copied for safe codec/editor round-trips. */
    public JsonElement definition() { return source.deepCopy(); }
}
