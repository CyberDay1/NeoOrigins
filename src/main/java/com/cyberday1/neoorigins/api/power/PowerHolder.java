package com.cyberday1.neoorigins.api.power;

import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A resolved power instance: the type paired with its decoded configuration.
 * Used internally to avoid repeated deserialization.
 */
public final class PowerHolder<C extends PowerConfiguration> {
    private final PowerType<C> type;
    private final C config;
    private final Component name;
    private final Component description;
    private final boolean hidden;
    private final EntityCondition condition;
    private final ConditionMode conditionMode;

    /** Condition mode for the top-level power condition gate. */
    public enum ConditionMode {
        /** Power is blocked when the condition is true (default). */
        DENY,
        /** Power only operates when the condition is true. */
        ALLOW
    }

    public PowerHolder(PowerType<C> type, C config, Component name, Component description) {
        this(type, config, name, description, false, null, ConditionMode.DENY);
    }

    public PowerHolder(PowerType<C> type, C config, Component name, Component description, boolean hidden) {
        this(type, config, name, description, hidden, null, ConditionMode.DENY);
    }

    public PowerHolder(PowerType<C> type, C config, Component name, Component description,
                        boolean hidden, EntityCondition condition, ConditionMode conditionMode) {
        this.type = type;
        this.config = config;
        this.name = name;
        this.description = description;
        this.hidden = hidden;
        this.condition = condition;
        this.conditionMode = conditionMode;
    }

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

    // Lifecycle methods that are NOT condition-gated (power is still owned):
    public void onGranted(ServerPlayer player)          { type.onGranted(player, config); }
    public void onRevoked(ServerPlayer player)          { type.onRevoked(player, config); }
    public void onLogin(ServerPlayer player)            { type.onLogin(player, config); }
    public void onRespawn(ServerPlayer player)          { type.onRespawn(player, config); }

    // Condition-gated methods — skipped when the top-level condition is not satisfied:
    public void onTick(ServerPlayer player)             { if (!isConditionSatisfied(player)) return; type.onTick(player, config); }
    public void onActivated(ServerPlayer player)        { if (!isConditionSatisfied(player)) return; type.onActivated(player, config); }
    public void onHit(ServerPlayer player, float amount){ if (!isConditionSatisfied(player)) return; type.onHit(player, config, amount); }
    public void onKill(ServerPlayer player, LivingEntity killed) { if (!isConditionSatisfied(player)) return; type.onKill(player, config, killed); }
}
