package com.cyberday1.neoorigins.api.power;

import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.effect.PowerSuppression;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A resolved power instance: the type paired with its decoded configuration.
 * Used internally to avoid repeated deserialization.
 */
public final class PowerHolder<C extends PowerConfiguration> {
    private final Identifier id;
    private final PowerType<C> type;
    private final C config;
    private final Component name;
    private final Component description;
    private final boolean hidden;
    private final EntityCondition condition;
    private final ConditionMode conditionMode;

    // Power dispatch is single-threaded on the server main thread, but ThreadLocal
    // is safer than a static field if anything ever dispatches off-thread (and the
    // overhead is negligible). PowerType subclasses that need to know which power
    // they're being invoked as can read PowerHolder.currentDispatchId().
    private static final ThreadLocal<Identifier> CURRENT_DISPATCH_ID = new ThreadLocal<>();

    public static Identifier currentDispatchId() { return CURRENT_DISPATCH_ID.get(); }

    /** Condition mode for the top-level power condition gate. */
    public enum ConditionMode {
        /** Power is blocked when the condition is true (default). */
        DENY,
        /** Power only operates when the condition is true. */
        ALLOW
    }

    public PowerHolder(Identifier id, PowerType<C> type, C config, Component name, Component description) {
        this(id, type, config, name, description, false, null, ConditionMode.DENY);
    }

    public PowerHolder(Identifier id, PowerType<C> type, C config, Component name, Component description, boolean hidden) {
        this(id, type, config, name, description, hidden, null, ConditionMode.DENY);
    }

    public PowerHolder(Identifier id, PowerType<C> type, C config, Component name, Component description,
                        boolean hidden, EntityCondition condition, ConditionMode conditionMode) {
        this.id = id;
        this.type = type;
        this.config = config;
        this.name = name;
        this.description = description;
        this.hidden = hidden;
        this.condition = condition;
        this.conditionMode = conditionMode;
    }

    public Identifier id()            { return id; }
    public PowerType<C> type()        { return type; }
    public C config()                 { return config; }
    public Component name()           { return name; }
    public Component description()    { return description; }
    /** When true, this power is excluded from the origin info panel (purely-internal flags, glue powers under multiple, etc.). */
    public boolean hidden()           { return hidden; }
    /** Returns the top-level entity condition, or null if none. */
    public EntityCondition condition()       { return condition; }
    /** Returns the condition mode (DENY or ALLOW). */
    public ConditionMode conditionMode()     { return conditionMode; }

    /**
     * Returns true if the power's top-level condition allows the power to operate.
     * <ul>
     *   <li>No condition → always satisfied.</li>
     *   <li>DENY mode  → satisfied when condition is <b>false</b> (condition blocks the power when true).</li>
     *   <li>ALLOW mode → satisfied when condition is <b>true</b> (condition enables the power when true).</li>
     * </ul>
     */
    public boolean isConditionSatisfied(ServerPlayer player) {
        if (condition == null) return true;
        boolean result = condition.test(player);
        return conditionMode == ConditionMode.ALLOW ? result : !result;
    }

    /** Returns true if this power occupies a keybind slot (has active behaviour). */
    public boolean isActive()                              { return type.isActivePower(config); }
    public boolean occupiesHotkeySlot()                    { return type.occupiesHotkeySlot(config); }

    // Lifecycle methods that are NOT condition-gated (power is still owned):
    public void onGranted(ServerPlayer player)          { Identifier prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onGranted(player, config); } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onRevoked(ServerPlayer player)          { Identifier prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onRevoked(player, config); } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onLogin(ServerPlayer player)            { Identifier prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onLogin(player, config);   } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onRespawn(ServerPlayer player)          { Identifier prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onRespawn(player, config); } finally { CURRENT_DISPATCH_ID.set(prev); } }

    // Mob-origin application — only meaningful when the type opts in via
    // appliesToMobs(); the caller (MobOriginService) checks that first.
    public boolean appliesToMobs()                      { return type.appliesToMobs(config); }
    public void applyToMob(LivingEntity mob)            { type.applyToMob(mob, config); }
    public void removeFromMob(LivingEntity mob)         { type.removeFromMob(mob, config); }

    // Condition-gated methods — skipped when the top-level condition is not satisfied:
    public void onTick(ServerPlayer player)             { if (!isConditionSatisfied(player)) return; Identifier prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onTick(player, config);    } finally { CURRENT_DISPATCH_ID.set(prev); } }
    // Every activation path funnels through here — the keybind packet, the
    // activate_power action, and the compat loader's programmatic fire — so the
    // neoorigins:suppression gate sits here rather than in AbstractActivePower.
    // AbstractTogglePower extends PowerType directly and is NOT an
    // AbstractActivePower, so a guard down there would leave every toggle firing
    // normally while the player was supposedly suppressed.
    //
    // The refusal is the same either way; only the feedback differs. This
    // signature is the silent one on purpose: the activate_power action and the
    // every-tick continuous keybind poll both land here, and a new call site
    // added without thought should fail quietly rather than repaint the action
    // bar every tick. Call onActivatedByKeypress for a real, one-shot press.
    public void onActivated(ServerPlayer player)        { activate(player, false); }

    /**
     * Activation from a deliberate, edge-triggered player input — a key press or
     * a power-GUI click. Identical to {@link #onActivated} except that a
     * suppression refusal tells the player why.
     */
    public void onActivatedByKeypress(ServerPlayer player) { activate(player, true); }

    private void activate(ServerPlayer player, boolean announceRefusal) {
        if (!isConditionSatisfied(player)) return;
        if (PowerSuppression.refuseActivation(player, announceRefusal)) return;
        Identifier prev = CURRENT_DISPATCH_ID.get();
        CURRENT_DISPATCH_ID.set(id);
        try { type.onActivated(player, config); } finally { CURRENT_DISPATCH_ID.set(prev); }
    }

    public void onHit(ServerPlayer player, float amount){ if (!isConditionSatisfied(player)) return; Identifier prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onHit(player, config, amount); } finally { CURRENT_DISPATCH_ID.set(prev); } }
    public void onKill(ServerPlayer player, LivingEntity killed) { if (!isConditionSatisfied(player)) return; Identifier prev = CURRENT_DISPATCH_ID.get(); CURRENT_DISPATCH_ID.set(id); try { type.onKill(player, config, killed); } finally { CURRENT_DISPATCH_ID.set(prev); } }
}
