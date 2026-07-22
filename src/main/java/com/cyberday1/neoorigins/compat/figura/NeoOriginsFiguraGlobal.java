package com.cyberday1.neoorigins.compat.figura;

import com.cyberday1.neoorigins.api.origin.FiguraModelMap;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.ClientPlayerPowers;
import com.cyberday1.neoorigins.data.OriginDataManager;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.entries.FiguraAPI;
import org.figuramc.figura.lua.LuaWhitelist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Per-avatar {@code neoorigins} Lua API object. One instance is created per Figura
 * avatar (via {@link NeoOriginsFiguraPlugin#build}) and set as the avatar's
 * {@code neoorigins} global. Every method reads the NeoOrigins state of the player
 * this avatar belongs to — resolved from {@link Avatar#owner} (the owning player's
 * UUID) against the client-side {@link ClientPlayerPowers} store, which the server
 * keeps populated for every visible player. Because Figura runs one avatar per
 * visible player on each client, this answers per-player for all observers: an
 * avatar author's script sees the correct origin/powers for whoever the avatar is
 * attached to, on everyone's screen.
 *
 * <p>Returns are Lua-friendly: strings (namespaced ids), booleans, and string lists
 * (Lua tables). {@code nil} is returned as Java {@code null} where a value may be
 * absent. All exposed methods carry {@link LuaWhitelist @LuaWhitelist}; the class
 * itself is whitelisted via {@link NeoOriginsFiguraPlugin#getWhitelistedClasses()}.
 *
 * <p>This class implements {@link FiguraAPI} only because {@code build} is typed to
 * return one and Figura sets that object as the global — the interface methods are
 * never Lua-visible (not whitelisted) and are inert here.
 */
@LuaWhitelist
public class NeoOriginsFiguraGlobal implements FiguraAPI {

    private final UUID owner;

    NeoOriginsFiguraGlobal(Avatar avatar) {
        this.owner = avatar.owner;
    }

    /**
     * The player's origin id on the primary {@code neoorigins:origin} layer as a
     * namespaced string (e.g. {@code "neoorigins:windwalker"}), or {@code nil} if
     * they have no origin there / aren't currently synced. Falls back to the first
     * origin on any layer when no canonical origin layer is present.
     */
    @LuaWhitelist
    public String getOrigin() {
        ResourceLocation origin = ClientPlayerPowers.primaryOrigin(owner);
        return origin == null ? null : origin.toString();
    }

    /**
     * Every origin the player currently has, across all layers, as a list of
     * namespaced id strings (empty table if none). Use this for multi-layer packs
     * (origin + class, etc.).
     */
    @LuaWhitelist
    public List<String> getOrigins() {
        List<String> out = new ArrayList<>();
        for (ResourceLocation id : ClientPlayerPowers.origins(owner)) {
            out.add(id.toString());
        }
        return out;
    }

    /**
     * True if the player has the given power granted, regardless of toggle state.
     * {@code id} is a namespaced power id string (e.g. {@code "neoorigins:double_jump"}).
     * A malformed id returns false rather than erroring.
     */
    @LuaWhitelist
    public boolean hasPower(String id) {
        ResourceLocation powerId = ResourceLocation.tryParse(id);
        return powerId != null && ClientPlayerPowers.hasPower(owner, powerId);
    }

    /**
     * True if the player has the given power granted AND it's currently active
     * (non-toggleable, or toggleable and toggled on, with its top-level condition
     * satisfied). This is the "is it doing something right now?" query.
     */
    @LuaWhitelist
    public boolean isPowerActive(String id) {
        ResourceLocation powerId = ResourceLocation.tryParse(id);
        return powerId != null && ClientPlayerPowers.isPowerActive(owner, powerId);
    }

    /**
     * Every power id granted to the player (regardless of toggle state) as a list
     * of namespaced id strings (empty table if none).
     */
    @LuaWhitelist
    public List<String> getPowers() {
        List<String> out = new ArrayList<>();
        for (ResourceLocation id : ClientPlayerPowers.powers(owner)) {
            out.add(id.toString());
        }
        return out;
    }

    /**
     * True if any currently-active power on the player grants the given capability
     * tag (e.g. {@code "wall_climb"}, {@code "natural_glide"}). Capability tags are
     * the internal effect markers powers publish while active.
     */
    @LuaWhitelist
    public boolean hasCapability(String tag) {
        return tag != null && ClientPlayerPowers.hasCapability(owner, tag);
    }

    /** All active capability tags on the player as a list of strings (empty if none). */
    @LuaWhitelist
    public List<String> getCapabilities() {
        return new ArrayList<>(ClientPlayerPowers.capabilities(owner));
    }

    /**
     * The {@code figura_model} key declared by THIS avatar's player's currently-active
     * origin on the primary origin layer, or {@code nil} if the player has no origin
     * there / the origin declares no model. This is the opaque string an origin's
     * datapack JSON sets (e.g. {@code "archer"}) so a Figura script can pick the model
     * that represents the wearer's origin. The origin definition is read from the
     * client-side origin registry, which the server syncs in full via
     * {@code SyncOriginRegistryPayload} — no extra networking needed.
     */
    @LuaWhitelist
    public String getFiguraModel() {
        ResourceLocation originId = ClientPlayerPowers.primaryOrigin(owner);
        if (originId == null) return null;
        Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
        if (origin == null) return null;
        return origin.figuraModel().orElse(null);
    }

    /**
     * Every distinct {@code figura_model} key declared across ALL currently-loaded
     * origins (deduped, order undefined; empty table if none). Lets a generic avatar
     * script learn the full set of "managed" model keys without knowing every origin.
     */
    @LuaWhitelist
    public List<String> getFiguraModels() {
        java.util.LinkedHashSet<String> models = new java.util.LinkedHashSet<>();
        for (Origin origin : OriginDataManager.INSTANCE.getOrigins().values()) {
            origin.figuraModel().ifPresent(models::add);
        }
        return new ArrayList<>(models);
    }

    /**
     * The player's effective TIER model key: the origin's base {@code figura_model},
     * overridden by the highest {@code figura_models.tiers} entry whose integer index
     * is at most the player's current evolution tier. Returns {@code nil} when the
     * player has no origin / the origin declares no base key and no matching tier.
     * Non-integer tier keys are ignored. Unlike {@link #getFiguraModel()} (which
     * always returns the static base for back-compat), this one reacts to tier.
     */
    @LuaWhitelist
    public String getFiguraModelTier() {
        Origin origin = primaryOrigin();
        if (origin == null) return null;
        String base = origin.figuraModel().orElse(null);
        FiguraModelMap map = origin.figuraModels().orElse(null);
        if (map == null) return base;
        int playerTier = ClientPlayerPowers.tier(owner);
        String best = base;
        int bestIndex = Integer.MIN_VALUE;
        for (Map.Entry<String, String> e : map.tiers().entrySet()) {
            int index;
            try {
                index = Integer.parseInt(e.getKey().trim());
            } catch (NumberFormatException ignored) {
                continue; // opaque, non-integer tier key: skip gracefully
            }
            if (index <= playerTier && index > bestIndex) {
                bestIndex = index;
                best = e.getValue();
            }
        }
        return best;
    }

    /**
     * Every reactive model key that is currently "on" for this player, from the
     * origin's {@code figura_models} maps: every {@code capabilities} key whose tag
     * is present, then every {@code powers} key whose power is active. Deterministic
     * order: capabilities first (sorted by tag), then powers (sorted by power id).
     * This is declaration-independent ordering for stability, NOT a priority ranking:
     * an avatar script should treat the list as an unordered set of active keys, or
     * apply its own precedence. Empty table when nothing is on / no maps declared.
     */
    @LuaWhitelist
    public List<String> getActiveFiguraModelKeys() {
        Origin origin = primaryOrigin();
        List<String> out = new ArrayList<>();
        if (origin == null) return out;
        FiguraModelMap map = origin.figuraModels().orElse(null);
        if (map == null) return out;
        // Capabilities first, sorted by tag for a stable order.
        new TreeMap<>(map.capabilities()).forEach((tag, key) -> {
            if (ClientPlayerPowers.hasCapability(owner, tag)) out.add(key);
        });
        // Then powers, sorted by power id string for a stable order.
        TreeMap<String, String> sortedPowers = new TreeMap<>();
        map.powers().forEach((id, key) -> sortedPowers.put(id.toString(), key));
        for (Map.Entry<String, String> e : sortedPowers.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id != null && ClientPlayerPowers.isPowerActive(owner, id)) {
                out.add(e.getValue());
            }
        }
        return out;
    }

    /**
     * The author-declared {@code figura_models.vocab} map (model key to friendly
     * label) for this player's primary origin, as a Lua table. Purely for discovery:
     * lets a generic avatar UI list the managed keys with human names. Empty table
     * when the player has no origin / the origin declares no vocab.
     *
     * <p>Returned as a Java {@code Map<String,String>}: Figura's {@code LuaTypeManager}
     * auto-wraps a {@code Map} return into a Lua table (verified against
     * figura-0.1.6), so the Lua side reads it as {@code vocab[key] == label}.
     */
    @LuaWhitelist
    public Map<String, String> getFiguraModelVocab() {
        Origin origin = primaryOrigin();
        if (origin == null) return new LinkedHashMap<>();
        FiguraModelMap map = origin.figuraModels().orElse(null);
        if (map == null) return new LinkedHashMap<>();
        return new LinkedHashMap<>(map.vocab());
    }

    /**
     * This avatar's owner's primary-layer origin definition from the client-side
     * origin registry, or {@code null} if they have no primary origin / it isn't
     * loaded. Shared by the model-map reader methods above.
     */
    private Origin primaryOrigin() {
        ResourceLocation originId = ClientPlayerPowers.primaryOrigin(owner);
        if (originId == null) return null;
        return OriginDataManager.INSTANCE.getOrigin(originId);
    }

    // ── FiguraAPI interface (inert — this object is a global, not a plugin) ──────

    @Override
    public FiguraAPI build(Avatar avatar) {
        return new NeoOriginsFiguraGlobal(avatar);
    }

    @Override
    public String getName() {
        return "neoorigins";
    }

    @Override
    public java.util.Collection<Class<?>> getWhitelistedClasses() {
        return java.util.List.of();
    }

    @Override
    public java.util.Collection<Class<?>> getDocsClasses() {
        return java.util.List.of();
    }
}
