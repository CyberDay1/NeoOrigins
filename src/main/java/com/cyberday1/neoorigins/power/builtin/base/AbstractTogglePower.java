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
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.on")
                .withStyle(ChatFormatting.GREEN));
        } else {
            data.setPowerToggledOff(key, true);
            removeEffect(player, config);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.off")
                .withStyle(ChatFormatting.RED));
        }
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

    public boolean isToggledOff(ServerPlayer player, C config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        return data.isPowerToggledOff(getToggleKey(config));
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
