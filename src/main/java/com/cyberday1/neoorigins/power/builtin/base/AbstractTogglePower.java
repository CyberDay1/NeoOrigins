package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractTogglePower<C extends PowerConfiguration> extends PowerType<C> {

    private static final Map<String, Set<UUID>> TOGGLED_OFF = new ConcurrentHashMap<>();

    @Override
    public final boolean isActivePower() { return true; }

    @Override
    public final void onActivated(ServerPlayer player, C config) {
        String key = getToggleKey();
        Set<UUID> offSet = TOGGLED_OFF.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        UUID id = player.getUUID();

        if (offSet.contains(id)) {
            offSet.remove(id);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.on")
                .withStyle(ChatFormatting.GREEN));
        } else {
            offSet.add(id);
            removeEffect(player, config);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.off")
                .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public final void onTick(ServerPlayer player, C config) {
        if (isToggledOff(player)) return;
        tickEffect(player, config);
    }

    @Override
    public void onRevoked(ServerPlayer player, C config) {
        clearToggle(player);
        removeEffect(player, config);
    }

    protected abstract void tickEffect(ServerPlayer player, C config);
    protected abstract void removeEffect(ServerPlayer player, C config);

    public boolean isToggledOff(ServerPlayer player) {
        Set<UUID> offSet = TOGGLED_OFF.get(getToggleKey());
        return offSet != null && offSet.contains(player.getUUID());
    }

    private void clearToggle(ServerPlayer player) {
        Set<UUID> offSet = TOGGLED_OFF.get(getToggleKey());
        if (offSet != null) offSet.remove(player.getUUID());
    }

    private String getToggleKey() {
        return getClass().getName();
    }
}
