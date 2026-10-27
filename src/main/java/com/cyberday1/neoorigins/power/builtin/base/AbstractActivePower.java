package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import net.minecraft.resources.ResourceLocation;
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
        /** If true, the remaining cooldown seconds are drawn on the icon (default true). */
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
        if (isFreeActivation(player, config)) {
            // No cooldown gate, no cost, no cooldown started (personal_dimension's exit).
            if (execute(player, config)) fireActivated(player);
            return;
        }
        String key = getCooldownKey(config);
        if (data.isOnCooldown(player, key)) return;

        // All-or-nothing; a resource cost falls back to hunger while resource
        // bars are globally disabled. Short = silent abort, no cooldown consumed.
        com.cyberday1.neoorigins.power.util.PowerUpkeep.Charge cost =
            com.cyberday1.neoorigins.power.util.PowerUpkeep.Charge.of(
                config.hungerCost(), config.resourceCost(), config.resourceCostAmount());
        if (!cost.affordable(player)) return;

        if (execute(player, config)) {
            cost.spend(player);
            data.setCooldown(key, player.tickCount, resolveCooldown(player, config));
            fireActivated(player);
        }
    }

    private static void fireActivated(ServerPlayer player) {
        ResourceLocation activatedId = PowerHolder.currentDispatchId();
        if (activatedId != null) {
            com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.firePowerActivated(player, activatedId);
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatchPowerActivated(player, activatedId);
        }
    }

    /**
     * True when this key press should bypass the cooldown entirely: it is
     * allowed while the cooldown runs, charges no cost and starts no cooldown.
     * Default false. {@code personal_dimension} uses it for leaving, so only
     * entering is cooldown-gated.
     */
    protected boolean isFreeActivation(ServerPlayer player, C config) {
        return false;
    }

    /**
     * Returns the cooldown key for this power instance. Uses the power's
     * ResourceLocation ID so each power has its own independent cooldown.
     */
    public String getCooldownKey(C config) {
        ResourceLocation id = PowerHolder.currentDispatchId();
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
