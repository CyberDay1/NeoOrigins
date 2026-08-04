package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public abstract class AbstractTogglePower<C extends PowerConfiguration> extends PowerType<C> {

    @Override
    public final boolean isActivePower() { return true; }

    @Override
    public final void onActivated(ServerPlayer player, C config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        String key = getToggleKey(config);
        boolean wasOff = data.isPowerToggledOff(key);

        if (wasOff) {
            data.setPowerToggledOff(key, false);
            onToggledOn(player, config);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.on")
                .withStyle(ChatFormatting.GREEN));
        } else {
            data.setPowerToggledOff(key, true);
            removeEffect(player, config);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.off")
                .withStyle(ChatFormatting.RED));
        }
        // Toggles count as activations in BOTH directions — the keypress always
        // does something. Listeners that care about direction can pair with a
        // power_active condition on the toggled power.
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatchPowerActivated(
            player, com.cyberday1.neoorigins.api.power.PowerHolder.currentDispatchId());
    }

    @Override
    public final void onTick(ServerPlayer player, C config) {
        if (isToggledOff(player, config)) return;
        tickEffect(player, config);
    }

    @Override
    public void onRevoked(ServerPlayer player, C config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.setPowerToggledOff(getToggleKey(config), false);
        removeEffect(player, config);
    }

    protected abstract void tickEffect(ServerPlayer player, C config);
    protected abstract void removeEffect(ServerPlayer player, C config);

    /**
     * Called once when the keybind flips the power from off → on (not on the
     * passive per-tick path and not when the power is first granted). Lets a
     * toggle deliver an immediate effect on activation — e.g. creative-flight
     * lifts the player off the ground so the keypress actually starts flight
     * instead of only arming the mayfly ability. Default: no-op.
     */
    protected void onToggledOn(ServerPlayer player, C config) {}

    public boolean isToggledOff(ServerPlayer player, C config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        return data.isPowerToggledOff(getToggleKey(config));
    }

    /**
     * Writes the toggle flag. Split out from {@link #isToggledOff} only so both
     * halves of the state can be redirected together in a test double.
     */
    protected void setToggledOff(ServerPlayer player, C config, boolean off) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.setPowerToggledOff(getToggleKey(config), off);
    }

    /**
     * Forces this toggle off and tears its effect down, with none of the
     * keypress path's chat feedback or activation dispatch. Used by
     * {@code neoorigins:suppression}, which must not leave a player stranded
     * mid-transformation. Returns true if the toggle was on and is now off.
     */
    public boolean forceOff(ServerPlayer player, C config) {
        if (isToggledOff(player, config)) return false;
        setToggledOff(player, config, true);
        removeEffect(player, config);
        return true;
    }

    /**
     * Per-instance toggle key. Subclasses with multiple configurations on a
     * single player (e.g. StatusEffectPower where several status_effect powers
     * coexist) must override to include a config-derived discriminator —
     * otherwise all instances share one toggle state.
     */
    protected String getToggleKey(C config) {
        return getClass().getName();
    }
}
