package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
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

    /** Minimum config interface required by all active powers. */
    public interface Config extends PowerConfiguration {
        int cooldownTicks();
    }

    /** Called once on class load — used as stable session cooldown key. */
    private final String cooldownKey = getClass().getName();

    @Override
    public final boolean isActivePower() { return true; }

    @Override
    public final void onActivated(ServerPlayer player, C config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        if (data.isOnCooldown(cooldownKey, player.tickCount)) return;
        if (execute(player, config)) {
            data.setCooldown(cooldownKey, player.tickCount, config.cooldownTicks());
        }
    }

    /**
     * Execute this power's effect.
     *
     * @return {@code true} if the power fired and the cooldown should be started;
     *         {@code false} if nothing happened (no cooldown consumed).
     */
    protected abstract boolean execute(ServerPlayer player, C config);
}
