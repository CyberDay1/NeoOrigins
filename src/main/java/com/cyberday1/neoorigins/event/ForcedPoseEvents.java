package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.PosePower;
import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds a {@code neoorigins:pose} player in the pose their origin asks for.
 *
 * <p>NeoForge's {@code Player#setForcedPose} is the whole mechanism:
 * {@code updatePlayerPose()} short-circuits on it, so vanilla's per-tick
 * recompute stops fighting us and no mixin is needed. Its javadoc asks for the
 * pose to be set once rather than every tick, which is why this class keeps a
 * record of what it last set and only touches the player on a transition.
 *
 * <p><b>Why a tick, and why {@code Pre}.</b> A forced pose has to be applied on
 * BOTH logical sides: {@code forcedPose} is a plain field rather than synced
 * data, and {@code LocalPlayer} runs the same pose recompute the server does, so
 * an owning client left out of it would overwrite the pose every tick and
 * rubber-band. Ticking is the one mechanism that covers turning a pose on,
 * switching between two of them, and dropping it, on both sides, with no
 * plumbing — {@code PowerCapabilities} answers the same question either side.
 * {@code Pre} rather than {@code Post} because {@code Player.tick()} fires it
 * before {@code updatePlayerPose()}, so the pose lands on the tick it was asked
 * for instead of the one after. Other players' clients need nothing:
 * {@code RemotePlayer} does not recompute poses, it takes the synced one.
 *
 * <p><b>Clearance is deliberately not checked.</b> NeoForge's javadoc asks the
 * caller to make sure the pose is clear; we do not, on purpose. A forced
 * swimming pose fits through a one-block gap anywhere, including gaps the player
 * could not otherwise pass, and that is the point of the power — balancing it is
 * the pack's job. Nothing suffocates: {@code Pose.SWIMMING} puts the eye at 0.4
 * and {@code isInWall()} tests the eye. Releasing the pose hands the player back
 * to vanilla, which does check clearance and settles on whatever fits.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class ForcedPoseEvents {

    /**
     * The pose this side last forced on one player.
     *
     * <p>The entity id rides along because a respawn hands back a brand-new
     * player object under the same UUID, with a null {@code forcedPose}. Without
     * it the bookkeeping would say "already forced" about an entity that never
     * was, and the pose would never be applied.
     */
    private record Forced(int entityId, Pose pose) {

        boolean appliesTo(Player player) {
            return entityId == player.getId();
        }
    }

    // Single-player runs both logical sides in one JVM and the two copies of a
    // player share a UUID, so the sides get a map each. They reach the same
    // conclusion from the same power, but on their own ticks, and must not
    // consume each other's bookkeeping.
    private static final Map<UUID, Forced> CLIENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Forced> SERVER = new ConcurrentHashMap<>();

    private ForcedPoseEvents() {}

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        Map<UUID, Forced> state = stateFor(player);
        UUID id = player.getUUID();

        Pose wanted = wantedPose(player);
        Forced current = state.get(id);

        if (wanted == null) {
            if (current == null) return;
            state.remove(id);
            // Only release a pose we are still the ones holding. Something else
            // forcing a pose in the meantime gets to keep it.
            if (current.appliesTo(player) && player.getForcedPose() == current.pose()) {
                player.setForcedPose(null);
            }
            return;
        }

        if (current != null && current.appliesTo(player) && current.pose() == wanted
                && player.getForcedPose() == wanted) {
            return;
        }
        state.put(id, new Forced(player.getId(), wanted));
        player.setForcedPose(wanted);
    }

    /**
     * The pose the player's powers are asking for, or null when none are.
     *
     * <p>Iterates the enum in its declared order, which is smallest pose first,
     * so two pose powers active at once resolve to one that fits wherever the
     * loser would: overlapping powers can never wedge the player into a box
     * bigger than the one they are standing in.
     */
    @Nullable
    private static Pose wantedPose(Player player) {
        for (PosePower.ForcedPose choice : PosePower.ForcedPose.values()) {
            if (PowerCapabilities.hasActive(player, choice.tag())) return choice.vanilla();
        }
        return null;
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
    }

    private static Map<UUID, Forced> stateFor(Player player) {
        return player.level().isClientSide ? CLIENT : SERVER;
    }
}
