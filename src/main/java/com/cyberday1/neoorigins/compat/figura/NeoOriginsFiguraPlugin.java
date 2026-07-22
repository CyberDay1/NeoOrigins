package com.cyberday1.neoorigins.compat.figura;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.entries.FiguraAPI;
import org.figuramc.figura.entries.annotations.FiguraAPIPlugin;

import java.util.Collection;
import java.util.List;

/**
 * Figura soft-dependency entry point. Registers a {@code neoorigins} global into
 * every Figura avatar's Lua sandbox so avatar authors can react to the NeoOrigins
 * state of the player an avatar belongs to — for ALL players, as seen by every
 * observer (Figura scripts run on each client, once per visible avatar).
 *
 * <p>Example avatar Lua:
 * <pre>
 *   function events.render()
 *     if neoorigins:getOrigin() == "neoorigins:windwalker" then
 *       models.wings:setVisible(true)
 *     end
 *   end
 * </pre>
 *
 * <h2>Why this compiles as a soft dep and never crashes without Figura</h2>
 * This class (and {@link NeoOriginsFiguraGlobal}) are the ONLY classes in the mod
 * that reference Figura types, and nothing in NeoOrigins' own code path ever
 * imports, news, or otherwise names them. They are discovered and instantiated
 * <em>solely</em> by Figura's own scanner
 * ({@code org.figuramc.figura.entries.neoforge.EntryPointManagerImpl}), which
 * walks mod jars' {@link FiguraAPIPlugin @FiguraAPIPlugin}-annotated ASM scan
 * data and reflectively {@code Class.forName}s the hits. If Figura is absent, its
 * scanner never runs, so this class is never classloaded and its Figura-typed
 * references never resolve — no {@code NoClassDefFoundError}. Figura is declared
 * {@code compileOnly} in build.gradle purely so this module compiles; it is NOT
 * listed in neoforge.mods.toml dependencies.
 *
 * <p>A public no-arg constructor is required — Figura instantiates the plugin via
 * reflection.
 */
@FiguraAPIPlugin
public class NeoOriginsFiguraPlugin implements FiguraAPI {

    /** Required public no-arg constructor — Figura reflectively instantiates this. */
    public NeoOriginsFiguraPlugin() {}

    /**
     * Build the per-avatar API object. Figura sets its return value as the Lua
     * global named {@link #getName()} on the avatar's sandbox, exposing that
     * object's {@code @LuaWhitelist} methods. The avatar carries the owning
     * player's UUID ({@code Avatar.owner}), which is how the returned object
     * resolves whose NeoOrigins state to read.
     */
    @Override
    public FiguraAPI build(Avatar avatar) {
        return new NeoOriginsFiguraGlobal(avatar);
    }

    /** The Lua global name — becomes {@code neoorigins} in avatar scripts. */
    @Override
    public String getName() {
        return "neoorigins";
    }

    /**
     * Classes exposed to the Lua sandbox. The per-avatar API object is the global
     * itself, so its class must be whitelisted for Figura to expose its
     * {@code @LuaWhitelist} methods.
     */
    @Override
    public Collection<Class<?>> getWhitelistedClasses() {
        return List.of(NeoOriginsFiguraGlobal.class);
    }

    /** No custom LuaTypeDoc classes to surface in Figura's in-game docs. */
    @Override
    public Collection<Class<?>> getDocsClasses() {
        return List.of();
    }
}
