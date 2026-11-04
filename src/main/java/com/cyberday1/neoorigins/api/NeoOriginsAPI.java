package com.cyberday1.neoorigins.api;

import com.cyberday1.neoorigins.api.power.CodePowerOptions;
import com.cyberday1.neoorigins.api.power.GrantPowerResult;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerDisplay;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.api.origin.SetOriginResult;
import com.cyberday1.neoorigins.api.resource.ResourceBacking;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.compat.ResourceBackingRouter;
import com.cyberday1.neoorigins.compat.action.ApiActions;
import com.cyberday1.neoorigins.compat.condition.ApiConditions;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.OriginChangeService;
import com.cyberday1.neoorigins.service.PowerGrantService;
import com.cyberday1.neoorigins.service.MinionTracker;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    // Powers registered in code
    // ---------------------------------------------------------------------

    /**
     * Registers a power built in code under {@code id} (your mod's namespace), from a power type and a
     * configuration instead of JSON. Call it on every launch, from your mod's constructor or
     * {@code FMLCommonSetupEvent}. The power then works like a datapack power: origins can list it and
     * {@link #grantPower} can grant it. A datapack power with the same id overrides it, with a warning.
     *
     * @throws IllegalStateException if {@code id} is already registered in code
     */
    public static <C extends PowerConfiguration> void registerPower(ResourceLocation id, PowerType<C> type, C config,
                                                                  PowerDisplay display) {
        registerPower(id, type, config, display, CodePowerOptions.NONE);
    }

    /**
     * As {@link #registerPower(ResourceLocation, PowerType, PowerConfiguration, PowerDisplay)}, with a
     * condition gate, toggle upkeep and {@code toggle_messages} set through {@code options}. A gate set
     * with {@code condition(predicate)} defaults to {@code ALLOW}: the power only works while it is true.
     *
     * @throws IllegalStateException if {@code id} is already registered in code
     */
    public static <C extends PowerConfiguration> void registerPower(ResourceLocation id, PowerType<C> type, C config,
                                                                  PowerDisplay display, CodePowerOptions options) {
        PowerDataManager.INSTANCE.registerCodePower(id, type, config, display, options);
    }

    /** As {@link #registerPowerFromJson(ResourceLocation, PowerType, JsonObject, PowerDisplay, CodePowerOptions)} with no options. */
    public static void registerPowerFromJson(ResourceLocation id, PowerType<?> type, JsonObject config,
                                             PowerDisplay display) {
        registerPowerFromJson(id, type, config, display, CodePowerOptions.NONE);
    }

    /**
     * Registers a power whose config is given as the JSON a datapack power file would hold, decoded
     * the same way: the power's id is injected, toggle upkeep fields and {@code toggle_messages} are
     * read, and {@code power_condition} / {@code power_condition_mode} gate it (default {@code DENY},
     * as in a datapack). Anything set in {@code options} replaces the matching JSON fields.
     *
     * @throws IllegalArgumentException if the JSON does not decode as {@code type}'s config
     * @throws IllegalStateException if {@code id} is already registered in code
     */
    public static void registerPowerFromJson(ResourceLocation id, PowerType<?> type, JsonObject config,
                                             PowerDisplay display, CodePowerOptions options) {
        PowerDataManager.INSTANCE.registerCodePowerFromJson(id, type, config, display, options);
    }

    // ---------------------------------------------------------------------
    // Conditions
    // ---------------------------------------------------------------------

    /**
     * Parses a NeoOrigins entity condition, the JSON a datapack writes in a {@code condition} field,
     * e.g. {@code {"type": "neoorigins:submerged_in", "fluid": "minecraft:water"}}. An unknown or
     * malformed condition is logged with {@code contextId} and evaluates false, as in a datapack.
     */
    public static Predicate<ServerPlayer> parseCondition(JsonObject json, String contextId) {
        return ApiConditions.parse(json, contextId);
    }

    /**
     * A codec for an entity condition field in your own power config: an object, or an array whose
     * conditions must all pass. Use {@code conditionCodec().optionalFieldOf("condition", p -> true)}
     * for an optional one. Only conditions decoded from JSON can be encoded back.
     */
    public static Codec<Predicate<ServerPlayer>> conditionCodec() {
        return ApiConditions.CODEC;
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    /**
     * Parses a NeoOrigins entity action, the JSON a datapack writes in an {@code entity_action} field,
     * e.g. {@code {"type": "neoorigins:heal", "amount": 4}}. An unknown or malformed action is logged
     * with {@code contextId} and does nothing, as in a datapack. Run it on the server thread.
     * Actions that read the triggering event (the attacker, the hit block) do nothing outside a
     * NeoOrigins event.
     */
    public static Consumer<ServerPlayer> parseAction(JsonObject json, String contextId) {
        return ApiActions.parse(json, contextId);
    }

    /**
     * A codec for an entity action field in your own power config: an object, or an array whose
     * actions run in order. Use {@code actionCodec().optionalFieldOf("action", p -> {})} for an
     * optional one. Only actions decoded from JSON can be encoded back.
     */
    public static Codec<Consumer<ServerPlayer>> actionCodec() {
        return ApiActions.CODEC;
    }

    // ---------------------------------------------------------------------
    // Runtime power grants
    // ---------------------------------------------------------------------

    /**
     * Grants a power by id on behalf of {@code source} (your mod's id, e.g. {@code mymod:quest_reward}),
     * until revoked. The grant persists across relog, death and restart; a full origin reset keeps it.
     * The same power granted by two sources stays until both revoke it. Datapack and
     * {@link #registerPower registered} powers both work; a {@code multiple} grants its sub-powers.
     */
    public static GrantPowerResult grantPower(ServerPlayer player, ResourceLocation power, ResourceLocation source) {
        return PowerGrantService.grant(player, power, source, null);
    }

    /**
     * As {@link #grantPower(ServerPlayer, ResourceLocation, ResourceLocation)}, but tied to {@code layer}:
     * the grant ends when that layer's origin changes, is cleared or is reset. The layer may be empty
     * now; the grant then ends at its next change. Granting again with another layer moves it.
     */
    public static GrantPowerResult grantPower(ServerPlayer player, ResourceLocation power, ResourceLocation source,
                                              ResourceLocation layer) {
        return PowerGrantService.grant(player, power, source, layer);
    }

    /** Ends {@code source}'s grant of {@code power}. @return false if it held no such grant. */
    public static boolean revokePower(ServerPlayer player, ResourceLocation power, ResourceLocation source) {
        return PowerGrantService.revoke(player, power, source);
    }

    /** Ends every grant {@code source} holds. @return how many were removed. */
    public static int revokePowers(ServerPlayer player, ResourceLocation source) {
        return PowerGrantService.revokeMatching(player, source, holder -> true);
    }

    /**
     * Ends {@code source}'s grants whose power matches {@code filter}. Only this source's API grants
     * are considered; origin, command and global powers are never touched. @return how many were removed.
     */
    public static int revokePowers(ServerPlayer player, ResourceLocation source, Predicate<PowerHolder<?>> filter) {
        return PowerGrantService.revokeMatching(player, source, filter);
    }

    /** The power ids {@code source} currently grants, as they were granted. */
    public static Set<ResourceLocation> grantedPowers(ServerPlayer player, ResourceLocation source) {
        return PowerGrantService.granted(player, source);
    }

    /** True if the player holds {@code power} from any origin, command, global set or API grant. */
    public static boolean hasPower(ServerPlayer player, ResourceLocation power) {
        return ActiveOriginService.activePowerIds(player).contains(power);
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
