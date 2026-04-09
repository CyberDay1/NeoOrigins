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

/**
 * Base class for powers that can be toggled on/off via a skill keybind.
 *
 * <p>When toggled on (default), {@link #tickEffect} is called each server tick.
 * When toggled off, the power is dormant and {@link #removeEffect} is called once.
 * Pressing the skill key flips the state and sends a chat message.
 *
 * <p>Toggle state is session-only (resets to ON on login/respawn).
 */
public abstract class AbstractTogglePower<C extends PowerConfiguration> extends PowerType<C> {

    /** Players with this power toggled OFF. Absent = ON (default). */
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

    /** Called every tick when the power is toggled ON. */
    protected abstract void tickEffect(ServerPlayer player, C config);

    /** Called once when the power is toggled OFF or revoked. Clean up effects here. */
    protected abstract void removeEffect(ServerPlayer player, C config);

    private boolean isToggledOff(ServerPlayer player) {
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
