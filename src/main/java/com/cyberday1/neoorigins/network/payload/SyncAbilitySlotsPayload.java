package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client. Pushes the player's keybind-slot ability roster for the
 * cooldown/ability HUD cluster: one entry per occupied skill slot (0–5) plus
 * the class active (slot -1), in slot order.
 *
 * <p>Sent alongside {@code SyncActivePowersPayload} (login, respawn, origin
 * change, toggle change, dimension change) — sync-on-change, never per-tick.
 * Live cooldown progress still travels via {@code SyncCooldownPayload};
 * per-power toggle on/off state via {@code SyncActivePowersPayload}.
 *
 * @param slots ordered slot entries (skill slots first, class active last).
 */
public record SyncAbilitySlotsPayload(List<Entry> slots) implements CustomPacketPayload {

    /**
     * @param slot       skill-slot index 0–5, or -1 for the class active.
     * @param powerId    the power occupying the slot.
     * @param icon       {@code cooldown_icon} (item id / .png path); empty = no icon.
     * @param toggleable true if the power's keybind flips an on/off state (AbstractTogglePower,
     *                   or a toggleable persistent_effect / condition_passive).
     * @param alwaysShow {@code always_show_icon} — render the icon even while idle.
     * @param countdown  {@code cooldown_countdown} — draw remaining seconds on the icon.
     */
    public record Entry(int slot, Identifier powerId, String icon,
                        boolean toggleable, boolean alwaysShow, boolean countdown) {}

    public static final Type<SyncAbilitySlotsPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_ability_slots"));

    public static final StreamCodec<FriendlyByteBuf, SyncAbilitySlotsPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.slots().size());
                for (Entry e : payload.slots()) {
                    buf.writeByte(e.slot());
                    buf.writeIdentifier(e.powerId());
                    buf.writeUtf(e.icon());
                    buf.writeBoolean(e.toggleable());
                    buf.writeBoolean(e.alwaysShow());
                    buf.writeBoolean(e.countdown());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<Entry> slots = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    slots.add(new Entry(buf.readByte(), buf.readIdentifier(),
                        buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
                }
                return new SyncAbilitySlotsPayload(slots);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
