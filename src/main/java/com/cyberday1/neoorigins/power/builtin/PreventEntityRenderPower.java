package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.condition.TargetCondition;
import com.cyberday1.neoorigins.compat.condition.TargetConditionParser;
import com.cyberday1.neoorigins.network.payload.SyncHiddenEntitiesPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native {@code neoorigins:prevent_entity_render} — the holder simply does not see
 * entities matching {@code entity_condition}. Apoli's power of the same name; the
 * Beholder Origin builds its whole identity on it (see nothing but what is
 * Glowing, then apply Glowing to reveal things).
 *
 * <h2>Why this is server-evaluated rather than client-evaluated</h2>
 * Apoli can run the condition on the client because Cardinal Components ships the
 * full power object — condition tree and all — to every client. NeoOrigins does
 * not: the client mirror of a power ({@code ClientPowerCache.Entry}) carries only
 * display data plus the type id, never the config JSON, so there is nothing on the
 * client to compile an arbitrary Apoli condition tree from.
 *
 * <p>Syncing the config would not be enough either. Several of the condition verbs
 * this power legitimately accepts read state the server never replicates for a
 * <em>remote</em> entity: {@code status_effect} resolves through
 * {@code LivingEntity#hasEffect}, whose backing {@code activeEffects} map is only
 * populated client-side by {@code ClientboundUpdateMobEffectPacket}, which the
 * server sends exclusively to the affected player. {@code nbt} and {@code health}
 * are in the same position. A client-side evaluator would therefore read "no
 * effects" on every mob and silently mis-evaluate exactly the leaf the Beholder
 * cares about.
 *
 * <p>So the server owns the verdict (matching the project's server-is-source-of-
 * truth rule): each holder is re-evaluated on a fixed interval against the entities
 * within their view distance, and the resulting set of hidden entity ids is synced
 * to that one player via {@link SyncHiddenEntitiesPayload}. The client stores it in
 * {@code ClientHiddenEntities}, and {@code EntityRenderDispatcherHideMixin} makes
 * {@code shouldRender} return false for those ids — the same seam Apoli's own mixin
 * uses, so the entity still exists client-side (sounds, collision, targeting are
 * untouched) and only its rendering is suppressed. This works identically in
 * singleplayer and on a dedicated server.
 *
 * <p><b>Cost of the trade:</b> the hidden set refreshes every
 * {@link #EVAL_INTERVAL_TICKS} ticks instead of every frame, so an entity that
 * gains/loses the condition pops in or out up to that long afterwards.
 */
public class PreventEntityRenderPower extends PowerType<PreventEntityRenderPower.Config> {

    /** Ticks between re-evaluations of a holder's hidden set. */
    private static final int EVAL_INTERVAL_TICKS = 5;

    /** Hard cap on hidden ids per player, so a mob farm can't inflate the packet. */
    private static final int MAX_HIDDEN = 1024;

    /**
     * Last set sent to each player, so a tick that changes nothing sends nothing.
     * Keyed by player UUID; dropped on revoke.
     */
    private static final Map<UUID, Set<Integer>> lastSent = new ConcurrentHashMap<>();

    /**
     * Sentinel for "an {@code entity_condition} was authored but could not be
     * compiled". Matching nothing makes the power hide nothing, which is the
     * fail-open direction: a prevention that guessed would blind the player to the
     * whole world. It is a condition rather than a separate flag so the record stays
     * one authorable field wide and the schema needs no phantom property.
     */
    private static final TargetCondition NEVER = (entity, actor) -> false;

    /**
     * @param entityCondition compiled predicate over a candidate entity, or empty
     *                        when the pack authored no condition — which in Apoli
     *                        means "applies to every entity".
     */
    public record Config(
        Optional<TargetCondition> entityCondition,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "prevent_entity_render: not a JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString()
                    : "neoorigins:prevent_entity_render";

                if (!obj.has("entity_condition") || obj.get("entity_condition").isJsonNull()) {
                    // Apoli: no condition = applies to everything.
                    return DataResult.success(Pair.of(
                        new Config(Optional.empty(), t), ops.empty()));
                }
                JsonElement cond = obj.get("entity_condition");
                if (cond.isJsonArray()) {
                    // Every other condition field in the mod takes an object OR an
                    // array (AND-combined), and the generated schema and the editor
                    // both advertise that here too. Read as absent, an array would
                    // mean "hide EVERY living entity" — schema-valid JSON that blinds
                    // the player. Fold it into an `and` instead.
                    JsonObject and = new JsonObject();
                    and.addProperty("type", "neoorigins:and");
                    and.add("conditions", cond.getAsJsonArray());
                    cond = and;
                } else if (!cond.isJsonObject()) {
                    NeoOrigins.LOGGER.warn(
                        "[NeoOrigins] prevent_entity_render ({}): entity_condition is neither an "
                        + "object nor an array — the power will hide nothing rather than hide "
                        + "everything", t);
                    return DataResult.success(Pair.of(
                        new Config(Optional.of(NEVER), t), ops.empty()));
                }
                TargetCondition tc = TargetConditionParser.parse(cond.getAsJsonObject(), t);
                if (tc == null) {
                    NeoOrigins.LOGGER.warn(
                        "[NeoOrigins] prevent_entity_render ({}): entity_condition uses a verb that "
                        + "cannot be evaluated against an arbitrary entity — the power will hide "
                        + "nothing rather than hide everything", t);
                    tc = NEVER;
                }
                return DataResult.success(Pair.of(
                    new Config(Optional.of(tc), t), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % EVAL_INTERVAL_TICKS != 0) return;

        Set<Integer> hidden = new HashSet<>();
        double range = viewRangeBlocks(player);
        AABB box = player.getBoundingBox().inflate(range);
        List<Entity> candidates = ((ServerLevel) player.level()).getEntities(player, box);
        for (Entity e : candidates) {
            if (!(e instanceof LivingEntity le)) continue; // see TargetConditionParser
            if (config.entityCondition().isPresent()
                    && !config.entityCondition().get().test(le, player)) {
                continue;
            }
            hidden.add(e.getId());
            if (hidden.size() >= MAX_HIDDEN) break;
        }

        Set<Integer> previous = lastSent.get(player.getUUID());
        if (hidden.equals(previous)) return;
        lastSent.put(player.getUUID(), hidden);
        PacketDistributor.sendToPlayer(player,
            new SyncHiddenEntitiesPayload(new ArrayList<>(hidden)));
    }

    /**
     * Blocks out to which entities are considered. The server only tracks entities
     * within its own entity view distance, so anything beyond it is not on the
     * client to hide in the first place.
     */
    private static double viewRangeBlocks(ServerPlayer player) {
        int chunks = ((ServerLevel) player.level()).getServer().getPlayerList().getViewDistance();
        return Math.max(16, Math.min(chunks, 16) * 16);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        // Drop the memo AND tell the client to forget, or a revoked Beholder keeps
        // a stale hidden set until they relog.
        lastSent.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new SyncHiddenEntitiesPayload(List.of()));
    }

    @Override
    public void onRespawn(ServerPlayer player, Config config) {
        // Entity ids are per-level, and death moves the player; force a resend by
        // clearing the memo rather than re-running onGranted (which has no work).
        lastSent.remove(player.getUUID());
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        lastSent.remove(player.getUUID());
    }
}
