package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.morph.MorphDimensions;
import com.cyberday1.neoorigins.power.morph.MorphSpec;
import com.cyberday1.neoorigins.power.morph.MorphState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives a morphed player the morph target's collision box.
 *
 * <p>Vanilla asks an entity for its size through {@code refreshDimensions()},
 * and NeoForge lets a mod answer differently by firing {@code EntityEvent.Size}
 * inside it. That is the whole mechanism — but it is a pull, not a push: the
 * answer only changes when something calls {@code refreshDimensions()}, and
 * nothing does when a power turns on. So this class is really two halves: the
 * {@link EntityEvent.Size} listener that gives the answer, and a per-tick
 * listener that notices when the answer has gone stale and asks the question
 * again.
 *
 * <p><b>Why the tick, and not the sync packet.</b> A morph changes on the
 * server when powers are recomputed and on the client when the sync payload
 * lands, and both would have to remember to refresh — in the client's case from
 * common code that has no business reaching for {@code Minecraft.getInstance()}.
 * Ticking is one mechanism that covers turning a morph on, changing it,
 * dropping it, <em>and</em> retrying one that couldn't be applied yet, on both
 * sides, with no plumbing. Non-morphed players cost a single map lookup.
 *
 * <p><b>Growing into a wall.</b> Vanilla deliberately never shoves a growing
 * player out of the blocks they've grown into ({@code refreshDimensions} skips
 * its unstick step for {@code Player}), and it is right not to: teleporting
 * someone is worse than the problem. So a morph whose box doesn't fit where the
 * player is standing is <em>deferred</em> — they stay their own size and the
 * next tick tries again. Ducking into a one-block gap as a slime therefore
 * works, and standing back up in it simply waits. The same test runs while a
 * morph is applied, so a player walled in mid-morph reverts instead of
 * suffocating.
 *
 * <p><b>Poses.</b> The size answer ignores the pose it is asked about, so
 * crouching and swimming don't change a morph's box. Vanilla's pose selection
 * keeps running and keeps using the player's own dimensions to decide what
 * fits, which is harmless precisely because our answer doesn't vary with it —
 * whatever pose it settles on, the box is the same.
 *
 * <p><b>Culling.</b> Because this class normally hands the player the morph's
 * real box, the renderer's off-screen test — which works off that same box —
 * comes out right for free, and a big morph stops being drawn at exactly the
 * point a mob of that size would. The exception is every case where this class
 * <em>declines</em>: a morph that opted out of hitboxes, a target whose size
 * couldn't be measured, and a morph held back because the player is walled in.
 * There the silhouette is the morph's but the box is still the player's, so a
 * large model would wink out while part of it should still be on screen. Those
 * players, and only those, are exempted from the test.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class MorphHitboxEvents {

    /**
     * What this side last decided about one player's size.
     *
     * <p>The entity id is part of it because a respawn hands back a brand-new
     * player object under the same UUID, built at vanilla size. Without it the
     * bookkeeping would say "already applied" about an entity that never was.
     */
    private record Sizing(int entityId, MorphSpec spec, boolean deferred) {

        boolean appliesTo(Player player) {
            return entityId == player.getId();
        }
    }

    // Single-player runs both logical sides in one JVM, and the two copies of a
    // player share a UUID — so the sides get a map each. They reach the same
    // conclusion from the same morph, but they get there on their own ticks and
    // must not consume each other's bookkeeping.
    private static final Map<UUID, Sizing> CLIENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Sizing> SERVER = new ConcurrentHashMap<>();

    /**
     * Entity ids of the players currently exempt from the renderer's off-screen
     * test, read back by {@code EntityRendererCullingMixin}.
     *
     * <p>26.x moved the decision off the entity: where 1.21.1 has a
     * {@code noCulling} flag to set, here it is a per-renderer
     * {@code EntityRenderer.affectedByCulling} to answer. So this side records
     * who is exempt and the mixin does the answering, rather than the flag
     * carrying the state itself.
     */
    private static final Set<Integer> UNCULLED = ConcurrentHashMap.newKeySet();

    private MorphHitboxEvents() {}

    /**
     * Answer with the morph's size, when this side has decided to use it.
     *
     * <p>Driven entirely by what the tick handler recorded, so the decision is
     * made in one place. That also keeps this listener safe: the event fires
     * from the {@code Entity} constructor, where a player has no UUID to look
     * up yet, and {@code isAddedToLevel} is false for exactly that window.
     */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player) || !player.isAddedToLevel()) return;
        Sizing sizing = stateFor(player).get(player.getUUID());
        if (sizing == null || sizing.deferred() || !sizing.appliesTo(player)) return;
        EntityDimensions morphed = sizeFor(player, sizing.spec());
        if (morphed != null) event.setNewSize(morphed);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Map<UUID, Sizing> state = stateFor(player);
        UUID id = player.getUUID();

        MorphSpec spec = MorphState.of(player);
        EntityDimensions wanted = spec == null ? null : sizeFor(player, spec);
        if (wanted == null) {
            // Not morphed, or a morph that leaves the hitbox alone. Only ask for
            // a resize if we were the ones who changed it. A morph still drawing
            // a model over a player-sized box is one whose silhouette the
            // off-screen test can't account for.
            updateCulling(player, spec != null && spec.hasModel());
            if (state.remove(id) != null) player.refreshDimensions();
            return;
        }

        Sizing current = state.get(id);
        boolean applied = current != null && !current.deferred() && current.appliesTo(player);

        if (!fits(player, wanted)) {
            // Bigger than the space they're standing in. Hand them their own
            // size back and try again next tick, rather than shoving them
            // through the wall.
            updateCulling(player, true);
            if (!matches(current, player, spec, true)) {
                state.put(id, new Sizing(player.getId(), spec, true));
                if (applied) player.refreshDimensions();
            }
            return;
        }

        updateCulling(player, false);
        if (!matches(current, player, spec, false)) {
            state.put(id, new Sizing(player.getId(), spec, false));
            player.refreshDimensions();
        }
    }

    /**
     * Exempt a player from the renderer's off-screen test, or hand them back to
     * it.
     *
     * <p>Client-side only: nothing on a server ever asks, so there is nothing
     * to record there. The exemption is not free — an exempt player is drawn
     * whenever they are in render distance, whether or not they are behind the
     * camera — which is why it is confined to the handful of morphs whose box
     * genuinely can't stand in for their silhouette.
     */
    private static void updateCulling(Player player, boolean exempt) {
        if (!player.level().isClientSide()) return;
        if (exempt) {
            UNCULLED.add(player.getId());
        } else {
            UNCULLED.remove(player.getId());
        }
    }

    /** Whether the renderer should skip its off-screen test for this player. */
    public static boolean isCullingExempt(Player player) {
        return !UNCULLED.isEmpty() && UNCULLED.contains(player.getId());
    }

    /** Whether the recorded decision is already the one we're about to make. */
    private static boolean matches(@Nullable Sizing current, Player player,
                                   MorphSpec spec, boolean deferred) {
        return current != null
            && current.deferred() == deferred
            && current.appliesTo(player)
            && current.spec().equals(spec);
    }

    /** Forget a player as they leave, so the maps don't outlive the session. */
    public static void forget(UUID id) {
        CLIENT.remove(id);
        SERVER.remove(id);
    }

    /** Forget everyone — on disconnect, where every player is about to go away. */
    public static void clearAll() {
        CLIENT.clear();
        SERVER.clear();
        // The entities these ids named are gone with the level, so there is
        // nothing left to hand back to the off-screen test.
        UNCULLED.clear();
    }

    /**
     * The morph's size for this player, or null when it doesn't ask for one.
     *
     * <p>The player's own {@code minecraft:scale} attribute is folded in last,
     * so {@code neoorigins:size_scaling} keeps working on a morphed player: a
     * player scaled to twice their size and morphed into a slime collides as a
     * slime twice its size, rather than one of them quietly winning.
     */
    @Nullable
    private static EntityDimensions sizeFor(Player player, MorphSpec spec) {
        EntityDimensions morphed = MorphDimensions.of(player, spec);
        return morphed == null ? null : morphed.scale(player.getScale());
    }

    /**
     * Whether {@code size} would clear the blocks around where the player is
     * standing. Shrinking always passes, so this only ever holds a morph back.
     */
    private static boolean fits(Player player, EntityDimensions size) {
        return player.level().noCollision(player, size.makeBoundingBox(player.position()));
    }

    private static Map<UUID, Sizing> stateFor(Player player) {
        return player.level().isClientSide() ? CLIENT : SERVER;
    }
}
