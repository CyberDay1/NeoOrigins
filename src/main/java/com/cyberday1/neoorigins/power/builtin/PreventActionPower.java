package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Locale;
import java.util.Set;

public class PreventActionPower extends PowerType<PreventActionPower.Config> {

    public enum Action {
        FALL_DAMAGE, FIRE, DROWN, FREEZE, SPRINT_FOOD,
        // ARMOR_EQUIP — per-slot prevention via head/chest/legs/feet booleans on Config.
        ARMOR_EQUIP,
        // CHESTPLATE_EQUIP — legacy alias kept for back-compat with packs that
        // pre-date ARMOR_EQUIP. Treated as ARMOR_EQUIP with chest=true at the
        // event-handler site; the legacy enum value otherwise has no extra config.
        CHESTPLATE_EQUIP,
        EYE_DAMAGE, WATER_DAMAGE, SWIM, SLEEP, ELYTRA, NONE;

        public static final Codec<Action> CODEC = Codec.STRING.xmap(
            s -> {
                try { return Action.valueOf(s.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { return NONE; }
            },
            a -> a.name().toLowerCase(Locale.ROOT)
        );
    }

    /**
     * Optional gate that disables the prevention under specific player states.
     * Primary use: let pack authors opt-out of fall-damage prevention while
     * crouching so players can still land critical hits on Avian-style origins.
     * Default {@link #ALWAYS} preserves legacy behaviour.
     */
    public enum ActiveWhen {
        ALWAYS, NOT_SNEAKING, SNEAKING, NOT_ON_GROUND, ON_GROUND;

        public static final Codec<ActiveWhen> CODEC = Codec.STRING.xmap(
            s -> {
                try { return ActiveWhen.valueOf(s.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { return ALWAYS; }
            },
            a -> a.name().toLowerCase(Locale.ROOT)
        );
    }

    public record Config(Action action, ActiveWhen activeWhen,
                         boolean head, boolean chest, boolean legs, boolean feet,
                         String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Action.CODEC.fieldOf("action").forGetter(Config::action),
            ActiveWhen.CODEC.optionalFieldOf("active_when", ActiveWhen.ALWAYS).forGetter(Config::activeWhen),
            // Per-slot toggles for ARMOR_EQUIP. Omitted = false. Ignored when
            // action is anything other than ARMOR_EQUIP.
            Codec.BOOL.optionalFieldOf("head",  false).forGetter(Config::head),
            Codec.BOOL.optionalFieldOf("chest", false).forGetter(Config::chest),
            Codec.BOOL.optionalFieldOf("legs",  false).forGetter(Config::legs),
            Codec.BOOL.optionalFieldOf("feet",  false).forGetter(Config::feet),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    /**
     * True when this power blocks equipping armor in the given slot.
     * Returns true if {@code action == ARMOR_EQUIP} and the matching per-slot
     * boolean is set, OR if {@code action == CHESTPLATE_EQUIP} (legacy alias)
     * and the slot is {@link EquipmentSlot#CHEST}.
     */
    public static boolean blocksArmorSlot(EquipmentSlot slot, Config config) {
        if (config.action() == Action.CHESTPLATE_EQUIP) return slot == EquipmentSlot.CHEST;
        if (config.action() != Action.ARMOR_EQUIP) return false;
        return switch (slot) {
            case HEAD  -> config.head();
            case CHEST -> config.chest();
            case LEGS  -> config.legs();
            case FEET  -> config.feet();
            default    -> false;
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /** True when the configured gate permits the prevention to apply right now. */
    public static boolean isGateOpen(ServerPlayer player, Config config) {
        return switch (config.activeWhen()) {
            case ALWAYS -> true;
            case NOT_SNEAKING -> !player.isShiftKeyDown();
            case SNEAKING -> player.isShiftKeyDown();
            case NOT_ON_GROUND -> !player.onGround();
            case ON_GROUND -> player.onGround();
        };
    }

    // Actual prevention is handled by OriginEventHandler routing to this type
    // These methods are intentionally no-op; the event handler checks the action field
    @Override public void onGranted(ServerPlayer player, Config config) {}

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        if (config.action() == Action.SWIM) setSwimWalk(player, false);
    }

    @Override
    public void onTickBlocked(ServerPlayer player, Config config) {
        if (config.action() == Action.SWIM) setSwimWalk(player, false);
    }

    /**
     * Synced capability behind {@code action: swim}. The swim pose and the
     * swim-up are decided by client-predicted movement, so they are blocked by
     * {@code EntityPreventSwimMixin} and {@code LivingEntityPreventSwimMixin} on
     * both sides from this tag; a server-only reset loses to the client every tick.
     */
    public static final String PREVENT_SWIM = "prevent_swim";

    @Override
    public Set<String> capabilities(Config config) {
        return config.action() == Action.SWIM ? Set.of(PREVENT_SWIM) : Set.of();
    }

    /** {@code active_when} decides whether the swim block is on right now. */
    @Override
    public Set<String> capabilities(ServerPlayer player, Config config) {
        return config.action() == Action.SWIM && isGateOpen(player, config)
            ? Set.of(PREVENT_SWIM) : Set.of();
    }

    /** Per-power id, so two swim-blocking powers don't remove each other's modifier. */
    private static ResourceLocation swimWalkModId() {
        ResourceLocation powerId = PowerHolder.currentDispatchId();
        String key = powerId != null
            ? (powerId.getNamespace() + "_" + powerId.getPath()).replace('/', '_')
            : "anon";
        return ResourceLocation.fromNamespaceAndPath("neoorigins", "prevent_swim_" + key + "_walk");
    }

    /**
     * A player who can't swim walks the bottom instead, at their land speed.
     * Full {@code water_movement_efficiency} is the vanilla way to get that (it is
     * what Depth Strider III sets): on the ground, water speed and friction become
     * land speed and friction, so {@code movement_speed} changes carry over.
     */
    private static void setSwimWalk(ServerPlayer player, boolean on) {
        AttributeInstance inst = player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (inst == null) return;
        ResourceLocation id = swimWalkModId();
        if (on) {
            if (inst.getModifier(id) == null) {
                inst.addPermanentModifier(new AttributeModifier(id, 1.0, AttributeModifier.Operation.ADD_VALUE));
            }
        } else {
            inst.removeModifier(id);
        }
    }

    // Clear residual fire ticks for FIRE-immune players so the burning visual
    // doesn't flicker on contact with lava / fire sources — damage is already
    // cancelled in CombatPowerEvents, but the fire-tick counter ticks on
    // independently and the player model still renders flames. See issue #27.
    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.action() == Action.FIRE && isGateOpen(player, config)
                && player.getRemainingFireTicks() > 0) {
            player.setRemainingFireTicks(0);
        }
        if (config.action() == Action.SWIM) setSwimWalk(player, isGateOpen(player, config));
        // ELYTRA enforcement lives in {@link
        // com.cyberday1.neoorigins.event.PreventActionPostTickHandler}
        // (subscribed to PlayerTickEvent.Post). The .Pre path here runs
        // *before* vanilla's travel(), so a reset applied here gets
        // overwritten the same tick. .Post fires after the physics, so the
        // reset is authoritative.
    }

    /**
     * Apply ELYTRA enforcement. Called from
     * {@link com.cyberday1.neoorigins.event.PreventActionPostTickHandler}
     * after vanilla physics has run. Public so the post-tick handler can
     * call this without duplicating the gate / state logic.
     *
     * <p>SWIM used to be enforced here too, by clamping the player's velocity
     * and marking it for resend every tick. That resend overwrote the client's
     * own horizontal motion with the server's, so the player crawled underwater
     * whatever their speed, and the client still predicted the swim pose
     * (issue #149). It is now {@link #PREVENT_SWIM} plus two mixins.
     */
    public static void enforceMovementBlock(ServerPlayer player, Config config) {
        if (config.action() == Action.ELYTRA && isGateOpen(player, config)
                && player.isFallFlying()) {
            player.stopFallFlying();
        }
    }
}
