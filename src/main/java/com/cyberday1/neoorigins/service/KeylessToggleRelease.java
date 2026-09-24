package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.effect.PowerSuppression;
import com.cyberday1.neoorigins.power.builtin.base.OffFlagToggle;
import com.cyberday1.neoorigins.power.keybind.PowerKeybindRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Clears the off-flag of a toggle no key can reach. A class slot holds one power
 * and the origin layer six, so a toggle pushed out of its slot (the Rogue's
 * Stealth after 2.2.28) would otherwise stay off for good.
 */
// hub: neoorigins/class-skill-slot.md
public final class KeylessToggleRelease {

    private KeylessToggleRelease() {}

    /** Mirrors {@code NeoOriginsNetwork#handleActivatePower}'s slot bound. */
    private static final int SKILL_SLOTS = 6;

    /** Clears the off-flag of every keyless toggle, default_off included. Returns the ids it switched on. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<ResourceLocation> release(ServerPlayer player) {
        // Suppression forced these off on purpose; wait for a login without it.
        if (PowerSuppression.isSuppressed(player)) return List.of();

        List<PowerHolder<?>> keyed = new ArrayList<>();
        List<PowerHolder<?>> slotted = ActiveOriginService.activePowers(player);
        keyed.addAll(slotted.subList(0, Math.min(slotted.size(), SKILL_SLOTS)));
        List<PowerHolder<?>> classSlotted = ActiveOriginService.activeClassPowers(player);
        if (!classSlotted.isEmpty()) keyed.add(classSlotted.get(0));

        List<PowerHolder<?>> keyless = new ArrayList<>();
        for (PowerHolder<?> holder : ActiveOriginService.allPowers(player)) {
            if (!(holder.type() instanceof OffFlagToggle toggle)) continue;
            if (!holder.occupiesHotkeySlot() || PowerKeybindRegistry.isNativeHotkeyPower(holder.id())) continue;
            if (keyed.contains(holder) || !toggle.hasToggle(holder.config())) continue;
            if (toggle.isToggledOff(player, holder.config(), holder.id())) keyless.add(holder);
        }
        if (keyless.isEmpty()) return List.of();

        PlayerOriginData data = player.getData(OriginAttachments.originData());
        Set<String> retired = new HashSet<>();
        for (PowerHolder<?> holder : keyless) {
            retired.add(((OffFlagToggle) holder.type()).legacyToggleKey(holder.config()));
        }
        // A pre-2.2.24 key is shared by every power of one shape; keep it off for the rest.
        for (PowerHolder<?> other : ActiveOriginService.allPowers(player)) {
            if (keyless.contains(other) || !(other.type() instanceof OffFlagToggle toggle)) continue;
            String own = other.id().toString();
            if (retired.contains(toggle.legacyToggleKey(other.config()))
                    && toggle.isToggledOff(player, other.config(), other.id())) {
                data.setPowerToggledOff(own, true);
            }
        }
        List<ResourceLocation> released = new ArrayList<>();
        for (PowerHolder<?> holder : keyless) {
            String legacy = ((OffFlagToggle) holder.type()).legacyToggleKey(holder.config());
            data.setPowerToggledOff(holder.id().toString(), legacy, false);
            released.add(holder.id());
        }
        return released;
    }
}
