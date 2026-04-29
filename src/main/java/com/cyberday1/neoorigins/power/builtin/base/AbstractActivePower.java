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
    public interface Config extends PowerConfiguration {
        int cooldownTicks();
        /** Food points debited on activation (not hunger bars). Default 0 = no cost. */
        default int hungerCost() { return 0; }
    }

    @Override
    public final boolean isActivePower() { return true; }

    @Override
    public final void onActivated(ServerPlayer player, C config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        String key = getCooldownKey(config);
        if (data.isOnCooldown(key, player.tickCount)) return;

        int hungerCost = config.hungerCost();
        if (hungerCost > 0 && player.getFoodData().getFoodLevel() < hungerCost) {
            return;  // not enough hunger — silent abort, no cooldown consumed
        }

        if (execute(player, config)) {
            if (hungerCost > 0) {
                player.getFoodData().setFoodLevel(
                    player.getFoodData().getFoodLevel() - hungerCost);
            }
            data.setCooldown(key, player.tickCount, config.cooldownTicks());
        }
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
