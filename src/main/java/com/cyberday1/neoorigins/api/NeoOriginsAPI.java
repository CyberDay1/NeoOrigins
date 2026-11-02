package com.cyberday1.neoorigins.api;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.api.origin.SetOriginResult;
import com.cyberday1.neoorigins.api.resource.ResourceBacking;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.compat.ResourceBackingRouter;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.OriginChangeService;
import com.cyberday1.neoorigins.service.MinionTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Stable entry point for Java-side integration with NeoOrigins.
 *
 * <p>Other mods should call through this facade rather than reaching
 * directly into service/ or event/ classes. Methods here carry API
 * stability guarantees (see package-info); the delegated services do not.
 *
 * <p>All methods are safe to call from the server thread. Client-side
 * callers should use {@link com.cyberday1.neoorigins.api.power.PowerHolder}
 * reads only — mutating power state client-side is undefined.
 */
public final class NeoOriginsAPI {

    private NeoOriginsAPI() {}

    // ---------------------------------------------------------------------
    // Power queries
    // ---------------------------------------------------------------------

    /**
     * All power holders currently active on the player, regardless of origin.
     * Order is registration order within the player's origin.
     */
    public static List<PowerHolder<?>> powers(ServerPlayer player) {
        return ActiveOriginService.allPowers(player);
    }

    /**
     * True if the player has at least one power of the given type whose
     * configuration matches the predicate.
     *
     * @param type   the power type class (e.g. {@code SummonMinionPower.class})
     * @param filter a predicate on the power's configuration
     */
    public static <C extends PowerConfiguration, T extends PowerType<C>> boolean has(
            ServerPlayer player, Class<T> type, Predicate<C> filter) {
        return ActiveOriginService.has(player, type, filter);
    }

    /**
     * Iterate every active power's configuration of the given type.
     * Multiple instances (multiple origins stack) are all visited.
     */
    public static <C extends PowerConfiguration, T extends PowerType<C>> void forEachOfType(
            ServerPlayer player, Class<T> type, Consumer<C> visitor) {
        ActiveOriginService.forEachOfType(player, type, visitor);
    }

    /**
     * True if a capability tag is currently emitted by any of the player's
     * active powers. Capability tags are the shared vocabulary between
     * powers and client-side effect layers (e.g. {@code "enhanced_vision"},
     * {@code "hide_hunger_bar"}).
     */
    public static boolean hasCapability(ServerPlayer player, String tag) {
        return ActiveOriginService.hasCapability(player, tag);
    }

    // ---------------------------------------------------------------------
    // Minion integration
    // ---------------------------------------------------------------------

    /**
     * Reverse-lookup an entity's summoner, if it is a currently-tracked
     * minion of some player. Useful for mods that want to exempt minions
     * from their own targeting / damage logic.
     *
     * @return the summoner's server-side handle, or empty if the entity is
     *         not a tracked minion (or the summoner is offline).
     */
    public static Optional<ServerPlayer> summonerOf(LivingEntity entity) {
        return MinionTracker.summonerOf(entity);
    }

    /**
     * True if {@code entity} is tracked as a minion summoned by
     * {@code summoner}. Shorthand for {@code summonerOf(entity)} + equality.
     */
    public static boolean isMinionOf(Entity entity, ServerPlayer summoner) {
        return MinionTracker.isTrackedMinionOf(entity, summoner.getUUID());
    }

    /**
     * True if {@code entity} is tracked as a minion of any player.
     * Cheap check (capability-backed) for mods that want to skip custom
     * logic on any summoned mob.
     */
    public static boolean isAnyMinion(Entity entity) {
        return MinionTracker.isAnyTrackedMinion(entity);
    }

    // ---------------------------------------------------------------------
    // Origins
    // ---------------------------------------------------------------------

    /** The player's origin on {@code layer}, or empty if the layer holds none. */
    public static Optional<ResourceLocation> getOrigin(ServerPlayer player, ResourceLocation layer) {
        return Optional.ofNullable(player.getData(OriginAttachments.originData()).getOrigin(layer));
    }

    /** Every layer the player holds an origin on, mapped to that origin. Unmodifiable copy. */
    public static Map<ResourceLocation, ResourceLocation> getOrigins(ServerPlayer player) {
        return Map.copyOf(player.getData(OriginAttachments.originData()).getOrigins());
    }

    /**
     * Sets the player's origin on {@code layer}, the same way an origin change in game does:
     * the old origin's powers on that layer are revoked, the new one's granted, and the client
     * synced. Fires {@link com.cyberday1.neoorigins.api.event.OriginChangedEvent}, whose
     * listeners may cancel or redirect it. Doesn't check the layer's own origin list or
     * conditions, and doesn't open a confirmation prompt or change the evolution tier.
     */
    public static SetOriginResult setOrigin(ServerPlayer player, ResourceLocation layer, ResourceLocation origin) {
        return OriginChangeService.set(player, layer, origin, OriginChangeService.Mode.NORMAL);
    }

    /**
     * Empties {@code layer}, revoking only that layer's powers. Other layers and powers granted
     * by commands or actions are kept. Returns false if the layer held nothing.
     */
    public static boolean clearOrigin(ServerPlayer player, ResourceLocation layer) {
        return OriginChangeService.clear(player, layer);
    }

    // ---------------------------------------------------------------------
    // Resource backings
    // ---------------------------------------------------------------------

    /**
     * Registers an external pool that {@code neoorigins:resource} bars can name with
     * {@code "backing": "<id>"}. Call once, from your mod's constructor or
     * {@code FMLCommonSetupEvent}, before datapacks load.
     *
     * @throws IllegalStateException if {@code id} is already registered,
     *         including the built-in {@code irons_spellbooks:mana}
     */
    public static void registerResourceBacking(ResourceLocation id, ResourceBacking backing) {
        ResourceBackingRouter.register(id.toString(), backing);
    }
}
