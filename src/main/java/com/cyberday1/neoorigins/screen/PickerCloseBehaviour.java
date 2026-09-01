package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The picker's close contract — ESC lock, nitwit auto-assign, orb cancel and
 * the abandon report. Shared by every picker layout so the server sees the
 * same packets no matter which screen the player used.
 */
final class PickerCloseBehaviour {

    private PickerCloseBehaviour() {}

    static final Identifier CLASS_LAYER_ID =
        Identifier.fromNamespaceAndPath("neoorigins", "class");
    private static final Identifier NITWIT_ORIGIN_ID =
        Identifier.fromNamespaceAndPath("neoorigins", "class_nitwit");

    /**
     * Lock players into the mandatory initial origin selection: ESC must not
     * dismiss the screen until they have actually chosen. This is the fix for
     * the divergence where the picker could be escaped on a dedicated server.
     *
     * <p>Escape stays allowed for the cases where backing out is intended:
     * <ul>
     *   <li>{@code isOrb} — an orb re-roll is voluntary and
     *       {@link #onPickerClosed} refunds/cancels it.</li>
     *   <li>the player already has all origins (voluntary re-selection via the
     *       editor / {@code /origin} command / VIEW_INFO recovery once done).</li>
     *   <li>the local selection is already complete ({@code presenter.isDone()})
     *       — by then {@code confirm()} normally closes the screen anyway; this
     *       is just a safety net.</li>
     * </ul>
     */
    static boolean shouldCloseOnEsc(boolean isOrb, OriginSelectionPresenter presenter) {
        if (isOrb) return true;
        if (ClientOriginState.isHadAllOrigins()) return true;
        return presenter.isDone();
    }

    static void onPickerClosed(boolean isOrb, OriginSelectionPresenter presenter) {
        // If the player escaped out with their primary origin picked but no
        // class, auto-assign the nitwit class (no-effect default) so the
        // server sees `hadAllOrigins` flip true and runs grantAllPending.
        // Otherwise the player would sit in a half-selected state with no
        // starting_equipment items granted. See tester feedback 2026-04-22.
        var origins = ClientOriginState.getOrigins();
        boolean hasClass = origins.keySet().stream().anyMatch(CLASS_LAYER_ID::equals);
        boolean hasAnyOrigin = !origins.isEmpty();
        // Only auto-assign nitwit when it's actually a selectable class. A pack
        // can disable every class origin (including class_nitwit) — in that case
        // the presenter skips the empty class layer entirely, and sending the
        // nitwit choice anyway makes the server reject it with a "non-existent
        // origin" warning. Mirror the presenter's availability filter so we stay
        // silent when there's nothing to assign.
        // Never auto-assign on an orb-driven close: the server refunds/rolls back
        // a cancelled orb use (CancelOrbPayload). For the Orb of Class this keeps
        // ESC a true free cancel that restores the previous class instead of
        // silently locking in nitwit; the Orb of Origin clears every layer so
        // hasAnyOrigin is already false here anyway.
        boolean nitwitAssigned = false;
        if (!isOrb && hasAnyOrigin && !hasClass && isNitwitAssignable()) {
            ClientPacketDistributor.sendToServer(new ChooseOriginPayload(CLASS_LAYER_ID, NITWIT_ORIGIN_ID));
            nitwitAssigned = true;
        }
        // Tell the server to cancel any pending orb-of-origin commit. If the
        // player already picked at least once during this picker session, the
        // commit already fired and the server's flag is already cleared, so
        // this is a no-op. If they never picked, the orb stays in the
        // inventory and no XP is charged.
        if (isOrb) {
            ClientPacketDistributor.sendToServer(new com.cyberday1.neoorigins.network.payload.CancelOrbPayload());
        }
        // Non-orb picker closed with an INCOMPLETE pick (zero origins OR some
        // but not all required layers): tell the server to drop first-pick
        // invulnerability. Now that the invuln gate covers the whole multi-layer
        // pick (not just the first layer), a player who picks an origin then
        // escapes before choosing a class would otherwise stay immortal until a
        // relog. The nitwit auto-assign above can complete the class layer; pass
        // that through so a legitimate completion isn't falsely reported as
        // abandoned (ClientOriginState won't reflect the just-sent packet yet).
        if (!isOrb && !isPickComplete(nitwitAssigned)) {
            ClientPacketDistributor.sendToServer(new com.cyberday1.neoorigins.network.payload.PickerAbandonedPayload());
        }
        Minecraft.getInstance().setScreen(null);
        // Every exit from a picker funnels through here, so this is the one place
        // a Dragon Survival species screen we held back gets its turn.
        com.cyberday1.neoorigins.client.DragonAltarSuppressor.onPickerClosed();
    }

    /**
     * Client-side mirror of the server's {@code allFilled} loop in
     * {@code NeoOriginsNetwork.handleChooseOrigin}: true when every layer the
     * picker would actually show has a selection. Iterates the same sorted
     * layers, skips hidden layers and layers with no available+existing origin,
     * and checks {@link ClientOriginState#getOrigins()} for an entry per layer.
     *
     * @param nitwitAssigned true if onPickerClosed just auto-assigned the nitwit
     *     class this frame — its {@link ChooseOriginPayload} is in flight to the
     *     server but not yet reflected in {@code ClientOriginState}, so treat
     *     the class layer as filled to avoid a spurious abandon after a legit
     *     completion.
     */
    private static boolean isPickComplete(boolean nitwitAssigned) {
        var choices = ClientOriginState.getOrigins();
        for (var l : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (l.hidden()) continue;
            boolean hasAnyOrigin = l.origins().stream()
                .anyMatch(co -> co.isAvailable(choices)
                             && OriginDataManager.INSTANCE.hasOrigin(co.origin()));
            if (!hasAnyOrigin) continue;
            boolean filled = choices.containsKey(l.id());
            if (!filled && nitwitAssigned && l.id().equals(CLASS_LAYER_ID)) filled = true;
            if (!filled) return false;
        }
        return true;
    }

    /**
     * True only when {@code neoorigins:class_nitwit} is a real, selectable
     * option right now — mirrors {@link OriginSelectionPresenter}'s availability
     * filter so the close path doesn't auto-assign a class the server will reject.
     */
    private static boolean isNitwitAssignable() {
        var layer = LayerDataManager.INSTANCE.getLayer(CLASS_LAYER_ID);
        if (layer == null || layer.hidden()) return false;
        var choices = ClientOriginState.getOrigins();
        for (var co : layer.origins()) {
            if (!co.origin().equals(NITWIT_ORIGIN_ID)) continue;
            if (!co.isAvailable(choices)) return false;
            if (ContentTogglesConfig.isOriginDisabled(co.origin())) return false;
            if (!OriginDataManager.INSTANCE.hasOrigin(co.origin())) return false;
            var origin = OriginDataManager.INSTANCE.getOrigin(co.origin());
            if (origin != null && origin.unchoosable()) return false;
            return true;
        }
        return false;
    }
}
