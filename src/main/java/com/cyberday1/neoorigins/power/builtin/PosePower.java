package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.power.builtin.base.HudIconConfig;
import com.cyberday1.neoorigins.power.util.EnabledGate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;

import java.util.Locale;
import java.util.Set;

/**
 * Holds the player's body in a chosen pose: prone (crawling), crouched, or
 * upright.
 *
 * <p><b>Why this is a power and not an action.</b> A forced pose is state that
 * lasts, not an event: it has to be re-asserted for as long as the origin says
 * so and released the moment it stops. An action fires once and has nothing to
 * hold on to.
 *
 * <p><b>The mechanism.</b> NeoForge adds {@code Player#setForcedPose}, and
 * {@code Player.updatePlayerPose()} short-circuits on it, so vanilla's per-tick
 * pose recompute does not fight us and no mixin is needed. The field is a plain
 * field rather than synced data, and {@code LocalPlayer} runs the same recompute
 * the server does, so it must be set on BOTH logical sides or the owning client
 * would overwrite the pose every tick and rubber-band. That is what the
 * capability tags below are for: {@code ForcedPoseEvents} reads them through
 * {@link com.cyberday1.neoorigins.power.capability.PowerCapabilities}, which
 * answers the same question on either side, and applies the pose there. Other
 * players' clients need nothing — {@code RemotePlayer} does not recompute poses,
 * it just takes the synced one.
 *
 * <p><b>Clearance is deliberately not checked.</b> Forced swimming fits through
 * a one-block gap anywhere, including gaps the player could not otherwise pass.
 * That is the point of the power; balancing it is the pack's job. Releasing the
 * pose hands the player back to vanilla, which does check clearance and falls
 * back to crouching (or swimming) if they no longer fit where they are.
 *
 * <p><b>What rides along.</b> The pose carries the hitbox and the animation, and
 * it costs speed: {@code LocalPlayer.isMovingSlowly()} is
 * {@code isCrouching() || isVisuallyCrawling()}, both pose reads, so a forced
 * crouch or land crawl scales movement input by {@code SNEAKING_SPEED}. It does
 * not carry the sneak <em>flag</em> — {@code maybeBackOffFromEdge},
 * {@code isDiscrete} and friends all read shared flag 1 — so there is no
 * ledge-stop and no quiet stepping. Nor does it grant swimming:
 * {@code Player.travel} gates its swim propulsion on {@code isSwimming()},
 * shared flag 4, which {@code Entity.updateSwimming} sets from sprinting in
 * water and never from a pose.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:pose",
 *   "pose": "swimming",
 *   "toggleable": true,
 *   "default_off": false,
 *   "condition": { "type": "neoorigins:sneaking" }
 * }
 * }</pre>
 */
public class PosePower extends PowerType<PosePower.Config> {

    /**
     * The three poses a pack may ask for, in the order they win when more than
     * one is active at once.
     *
     * <p>Smallest first, so the winner is always a pose that fits wherever the
     * losers would have: two pose powers overlapping can never wedge the player
     * into a box bigger than the one they are standing in.
     *
     * <p>Sleeping, fall-flying and spin-attack are vanilla poses too, but they
     * carry behaviour the pose alone does not deliver (a bed, an elytra, a
     * trident) and are left out rather than offered half-working.
     */
    public enum ForcedPose {
        SWIMMING(Pose.SWIMMING, "forced_pose_swimming"),
        CROUCHING(Pose.CROUCHING, "forced_pose_crouching"),
        STANDING(Pose.STANDING, "forced_pose_standing");

        private final Pose vanilla;
        private final String tag;
        private final Set<String> tags;

        ForcedPose(Pose vanilla, String tag) {
            this.vanilla = vanilla;
            this.tag = tag;
            this.tags = Set.of(tag);
        }

        /** The vanilla pose handed to {@code Player#setForcedPose}. */
        public Pose vanilla() { return vanilla; }

        /** The capability tag this pose publishes. */
        public String tag() { return tag; }

        Set<String> tags() { return tags; }

        /** The author-facing token, i.e. what the codec reads and the schema lists. */
        public String token() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * A tag per pose rather than one tag carrying a value, because the capability
     * channel is a {@code Set<String>} end to end — from the type's own
     * declaration through the sync payload to the client's lookup. Three tags fit
     * that shape; a tag with a value would have to be encoded into the string and
     * decoded again at every read.
     */
    private static final Codec<ForcedPose> POSE_CODEC = Codec.STRING.comapFlatMap(
        PosePower::parsePose, ForcedPose::token);

    private static DataResult<ForcedPose> parsePose(String raw) {
        try {
            return DataResult.success(ForcedPose.valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            // Loud rather than lenient: a mistyped pose that quietly fell back to
            // standing would be a power that does nothing, with nothing to read.
            return DataResult.error(() -> "pose: unknown pose '" + raw
                + "' (expected standing, crouching or swimming)");
        }
    }

    public record Config(
        ForcedPose pose,
        boolean toggleable,
        boolean defaultOff,
        boolean enabled,
        String type,
        String cooldownIcon,
        boolean alwaysShowIcon
    ) implements PowerConfiguration, HudIconConfig {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            POSE_CODEC.fieldOf("pose").forGetter(Config::pose),
            Codec.BOOL.optionalFieldOf("toggleable", true).forGetter(Config::toggleable),
            Codec.BOOL.optionalFieldOf("default_off", false).forGetter(Config::defaultOff),
            EnabledGate.<Config>field(Config::enabled),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public boolean isActivePower(Config config) { return config.toggleable(); }

    /**
     * The static upper bound: the tag this power could publish, ignoring toggle
     * state. Sound as the negative filter {@code ActiveOriginService} builds its
     * capability union from — it never answers "yes" on its own.
     */
    @Override
    public Set<String> capabilities(Config config) {
        return config.enabled() ? config.pose().tags() : Set.of();
    }

    /**
     * The real answer, asked once per probe and once per sync. Narrows the static
     * set by the toggle, so a player who has switched the pose off stands up on
     * both sides at once — the server's probe and the client's synced set are
     * computed from this same method.
     */
    @Override
    public Set<String> capabilities(ServerPlayer player, Config config) {
        if (!config.enabled()) return Set.of();
        if (isToggledOff(player, config, PowerHolder.currentDispatchId())) return Set.of();
        return config.pose().tags();
    }

    /**
     * Whether the holder currently has this toggleable power switched off.
     * Exposed so the network layer can mirror toggle state to the HUD ability
     * cluster (bright = on, dimmed = off), as it does for
     * {@code persistent_effect}.
     */
    public boolean isToggledOff(ServerPlayer player, Config config) {
        return isToggledOff(player, config, PowerHolder.currentDispatchId());
    }

    /** As above, for callers outside a {@link PowerHolder} dispatch (the HUD sync). */
    public boolean isToggledOff(ServerPlayer player, Config config, Identifier id) {
        if (!config.toggleable()) return false;
        return player.getData(OriginAttachments.originData())
            .isPowerToggledOff(toggleKey(id, config), legacyToggleKey(config));
    }

    /** Per-instance toggle key: the power's own resource id, as everywhere else. */
    private String toggleKey(Config config) {
        return toggleKey(PowerHolder.currentDispatchId(), config);
    }

    private String toggleKey(Identifier id, Config config) {
        return id != null ? id.toString() : legacyToggleKey(config);
    }

    /**
     * Fallback key for a call with no dispatch id to name the power by. This type
     * is new, so there is no pre-2.2.24 saved flag to stay compatible with; the
     * pose keeps two powers forcing different poses from sharing one flag.
     */
    private String legacyToggleKey(Config config) {
        return getClass().getName() + ':' + config.pose().token();
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        // default_off:true means the player opts in, so seed the flag on the
        // first grant — otherwise the pose would be held from the next tick.
        if (config.toggleable() && config.defaultOff()) {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            data.setPowerToggledOff(toggleKey(config), legacyToggleKey(config), true);
        }
    }

    @Override
    public void onActivated(ServerPlayer player, Config config) {
        if (!config.toggleable()) return;
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        String key = toggleKey(config);
        String legacy = legacyToggleKey(config);
        boolean wasOff = data.isPowerToggledOff(key, legacy);
        data.setPowerToggledOff(key, legacy, !wasOff);
        player.sendSystemMessage(Component.translatable(
                wasOff ? "neoorigins.toggle.on" : "neoorigins.toggle.off")
            .withStyle(wasOff ? ChatFormatting.GREEN : ChatFormatting.RED));
        // Nothing to tear down here: the pose is released by ForcedPoseEvents on
        // the next tick, when the capability it reads stops being published.
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        // Leave no stuck flag behind for a power the player no longer has — a
        // re-grant should start from the authored default, not from whatever the
        // key was left on.
        player.getData(OriginAttachments.originData())
            .setPowerToggledOff(toggleKey(config), legacyToggleKey(config), false);
    }
}
