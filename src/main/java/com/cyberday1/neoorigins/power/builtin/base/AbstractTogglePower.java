package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public abstract class AbstractTogglePower<C extends PowerConfiguration> extends PowerType<C> {

    @Override
    public final boolean isActivePower() { return true; }

    @Override
    public final void onActivated(ServerPlayer player, C config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        String key = getToggleKey(config);
        String legacy = getLegacyToggleKey(config);
        boolean wasOff = data.isPowerToggledOff(key, legacy);

        if (wasOff) {
            data.setPowerToggledOff(key, legacy, false);
            onToggledOn(player, config);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.on")
                .withStyle(ChatFormatting.GREEN));
        } else {
            data.setPowerToggledOff(key, legacy, true);
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
        data.setPowerToggledOff(getToggleKey(config), getLegacyToggleKey(config), false);
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
        return isToggledOff(player, config, PowerHolder.currentDispatchId());
    }

    /**
     * Toggle state for a named power, for callers that are not inside a
     * {@link PowerHolder} dispatch and so cannot rely on the ambient id. The HUD
     * sync reads holders directly, and without this it would resolve the legacy
     * key and report a stale answer once the player had actually toggled
     * anything.
     */
    public boolean isToggledOff(ServerPlayer player, C config, ResourceLocation id) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        return data.isPowerToggledOff(toggleKeyFor(id, config), getLegacyToggleKey(config));
    }

    /**
     * Writes the toggle flag. Split out from {@link #isToggledOff} only so both
     * halves of the state can be redirected together in a test double.
     */
    protected void setToggledOff(ServerPlayer player, C config, boolean off) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.setPowerToggledOff(getToggleKey(config), getLegacyToggleKey(config), off);
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
     * Per-instance toggle key: the power's own resource id, so two powers of the
     * same type on one player keep independent toggle state.
     *
     * <p>This used to return {@code getClass().getName()} and nothing else, with
     * a note telling subclasses to override it with a config-derived
     * discriminator. No subclass ever did, so every pair of same-type toggles on
     * a player shared one flag: pressing the key on one silently flipped the
     * other. Deriving the key from the id instead fixes it for all of them at
     * once and cannot be forgotten by a new subclass, which is the same reason
     * {@link com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower#getCooldownKey}
     * already works this way.
     */
    protected final String getToggleKey(C config) {
        return toggleKeyFor(PowerHolder.currentDispatchId(), config);
    }

    /** As {@link #getToggleKey}, for callers outside a {@link PowerHolder} dispatch. */
    protected final String toggleKeyFor(ResourceLocation id, C config) {
        return id != null ? id.toString() : getLegacyToggleKey(config);
    }

    /**
     * The pre-2.2.24 toggle key, read as a fallback so flags already in a save
     * survive the change. Subclasses that overrode the old key shape must
     * override this with the SAME formula they used before, or their players'
     * saved toggles will read as on after updating.
     */
    protected String getLegacyToggleKey(C config) {
        return getClass().getName();
    }
}
