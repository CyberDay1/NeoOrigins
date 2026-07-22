package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Base class for all cooldown-gated active (keybind) powers.
 *
 * <p>Subclasses implement {@link #execute} with their effect logic. The base
 * handles cooldown checking and setting automatically. If {@code execute} returns
 * {@code false} (nothing happened), the cooldown is NOT started — allowing powers
 * like teleport to skip the cooldown when no valid target is found.
 *
 * <p>Subclasses must define a Config record that implements {@link Config} and
 * exposes {@code cooldownTicks()}.
 */
public abstract class AbstractActivePower<C extends AbstractActivePower.Config>
        extends PowerType<C> {

    /**
     * Minimum config interface required by all active powers.
     *
     * <p>Subclasses whose JSON exposes a {@code hunger_cost} field should
     * override {@link Config#hungerCost()} in their Config record and wire it
     * through the codec. Powers that return &gt; 0 from {@code hungerCost()}
     * will be gated and debited by {@link #onActivated} before {@link #execute}
     * runs — no per-class bookkeeping needed.
     *
     * <p>Powers that manage hunger internally (e.g. SummonMinion, TameMob)
     * must keep {@code hungerCost()} at the default of 0 so the base class
     * doesn't double-charge.
     */
    public interface Config extends PowerConfiguration, HudIconConfig {
        int cooldownTicks();
        /** Food points debited on activation (not hunger bars). Default 0 = no cost. */
        default int hungerCost() { return 0; }
        /** Power ID of the resource to debit on activation. Empty string = no resource cost. */
        default String resourceCost() { return ""; }
        /** Amount of resource to debit on activation. Only used when resourceCost is non-empty. */
        default int resourceCostAmount() { return 0; }
        /** If true, remaining whole seconds are drawn on the cooldown icon. */
        default boolean cooldownCountdown() { return true; }
        /**
         * Power id of a variable/resource counter whose live value (in ticks) is
         * used as the cooldown length, overriding {@link #cooldownTicks()} when set.
         * Empty string (default) = use the fixed {@code cooldownTicks()}.
         */
        default String cooldownResource() { return ""; }
    }

    @Override
    public final boolean isActivePower() { return true; }

    /**
     * Effective cooldown length in ticks for this activation. When the config
     * names a {@code cooldownResource}, the counter's current value (defaulting
     * to its declared {@code start}) is read live and used as the cooldown,
     * clamped to a non-negative value; otherwise the fixed {@link Config#cooldownTicks()}.
     */
    public int resolveCooldown(ServerPlayer player, C config) {
        String cdRes = config.cooldownResource();
        if (cdRes == null || cdRes.isEmpty()) return config.cooldownTicks();
        int live = player.getData(com.cyberday1.neoorigins.compat.CompatAttachments.resourceState())
                .get(cdRes, com.cyberday1.neoorigins.compat.CompatAttachments.variableStart(cdRes));
        return Math.max(0, live);
    }

    @Override
    public final void onActivated(ServerPlayer player, C config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        String key = getCooldownKey(config);
        if (data.isOnCooldown(player, key)) return;

        int hungerCost = config.hungerCost();

        // Resource cost — if resource bars are globally disabled, fall back to hunger
        String resCostKey = config.resourceCost();
        int resCostAmt = config.resourceCostAmount();
        boolean hasResourceCost = !resCostKey.isEmpty() && resCostAmt > 0;
        boolean resourceBarsDisabled = ContentTogglesConfig.isResourceBarsDisabled();
        if (hasResourceCost && resourceBarsDisabled) {
            // Convert resource cost to hunger cost
            hungerCost += resCostAmt;
            hasResourceCost = false;
        }

        if (hungerCost > 0 && !com.cyberday1.neoorigins.util.FoodCost.canAfford(player, hungerCost)) {
            return;  // not enough hunger — silent abort, no cooldown consumed
        }

        // Resource cost check (pre-flight)
        if (hasResourceCost) {
            int current = com.cyberday1.neoorigins.power.builtin.ResourcePower.getValue(player, resCostKey);
            if (current < resCostAmt) return; // not enough resource — silent abort
        }

        if (execute(player, config)) {
            if (hungerCost > 0) {
                com.cyberday1.neoorigins.util.FoodCost.spend(player, hungerCost);
            }
            if (hasResourceCost) {
                com.cyberday1.neoorigins.power.builtin.ResourcePower.deduct(player, resCostKey, resCostAmt);
            }
            data.setCooldown(key, player.tickCount, resolveCooldown(player, config));
            Identifier activatedId = PowerHolder.currentDispatchId();
            if (activatedId != null) {
                com.cyberday1.neoorigins.service.EventPowerIndex.dispatchPowerActivated(player, activatedId);
            }
        }
    }

    /**
     * Returns the cooldown key for this power instance. Uses the power's
     * Identifier so each power has its own independent cooldown that
     * survives JVM restarts and respawns.
     */
    public String getCooldownKey(C config) {
        Identifier id = PowerHolder.currentDispatchId();
        return id != null ? id.toString() : getClass().getName();
    }

    /**
     * Execute this power's effect.
     *
     * @return {@code true} if the power fired and the cooldown should be started;
     *         {@code false} if nothing happened (no cooldown consumed).
     */
    protected abstract boolean execute(ServerPlayer player, C config);
}
