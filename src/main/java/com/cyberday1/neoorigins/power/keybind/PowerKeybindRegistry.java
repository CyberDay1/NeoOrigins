package com.cyberday1.neoorigins.power.keybind;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side index of pack-declared named keybinds.
 *
 * <p>Apoli-style active powers can declare {@code "key": "translation.key.id"}
 * to bind themselves to a named hotkey instead of one of the 6 hardcoded skill
 * slots. The compat loader writes those bindings into this registry at reload;
 * the client receives a sorted snapshot via
 * {@link com.cyberday1.neoorigins.network.payload.SyncKeybindRegistryPayload}
 * and assigns each declared key to an anonymous KeyMapping slot from the pool
 * sized by {@code NeoOriginsClientConfig.HOTKEY_POOL_SIZE}.
 *
 * <p>When the player presses an assigned slot, the client sends
 * {@link com.cyberday1.neoorigins.network.payload.ActivatePowerByKeyPayload}
 * with the original translation id; the server resolves it via
 * {@link #dispatch(ServerPlayer, String, boolean)} against the player's
 * currently-granted powers.
 *
 * <p>Edge vs. continuous semantics are stored per binding — the client always
 * polls the key as "currently held" and the server applies the right cadence
 * (edge detection for non-continuous, every-tick fire for continuous).
 */
public final class PowerKeybindRegistry {

    private PowerKeybindRegistry() {}

    /** Key: translation key (e.g. {@code "deanos_origins.key.origins.1"}).
     *  Value: list of bindings — multiple powers can share a hotkey. */
    private static final Map<String, List<Binding>> BY_KEY = new ConcurrentHashMap<>();

    /**
     * One pack-declared named keybind, captured at compat-load time.
     *
     * @param powerId      the power that owns this binding (used for cooldown keying)
     * @param action       the entity action to run when the key fires
     * @param condition    gate; if it fails the action is skipped (no cooldown burned)
     * @param cooldownTicks 0 = no cooldown
     * @param continuous   true = fire every tick while held; false = fire once per press
     * @param failAction   optional; runs when a press is blocked by {@code condition}
     *                     (NOT on cooldown blocks). Null = silent skip, the old behavior.
     */
    public record Binding(ResourceLocation powerId,
                          EntityAction action,
                          EntityCondition condition,
                          int cooldownTicks,
                          boolean continuous,
                          EntityAction failAction) {}

    /** Key: "uuid:powerId" → game-time of the last condition-failed dispatch. Used to
     *  collapse a continuous binding's per-tick held payloads into one fail_action
     *  firing per press (a gap in the stream = a fresh press). */
    private static final Map<String, Long> LAST_FAIL_TICK = new ConcurrentHashMap<>();

    /** A held key streams payloads every tick; a gap longer than this many ticks
     *  means the key was released and pressed again. */
    private static final long FAIL_REFIRE_GAP_TICKS = 3;

    /** Drop the entire registry — called by the compat loader at the start of every reload. */
    public static void clear() {
        BY_KEY.clear();
        LAST_FAIL_TICK.clear();
    }

    /** Register a single binding. Multiple powers may register the same translation key. */
    public static void register(String translationKey, Binding binding) {
        BY_KEY.computeIfAbsent(translationKey, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(binding);
    }

    /**
     * Sorted snapshot of every declared translation key. Sent to clients so they
     * can assign each one to a pool slot in stable order — a player who relogs
     * sees the same key in the same slot as long as the pack hasn't changed.
     */
    public static List<String> declaredKeys() {
        Set<String> sorted = new TreeSet<>(BY_KEY.keySet());
        return List.copyOf(sorted);
    }

    /**
     * Stable per-power index of (powerId → translationKey). Letting the client know
     * which translation key each power is bound to is what makes the Origin Info
     * screen show "Press [Hotkey 3]" instead of "Press [unbound]" for a hotkey power.
     */
    public static Map<ResourceLocation, String> powerToKey() {
        Map<ResourceLocation, String> out = new TreeMap<>();
        for (var entry : BY_KEY.entrySet()) {
            for (Binding b : entry.getValue()) {
                // First binding wins if a power somehow appears twice — keeps the
                // mapping deterministic without paying for a list-valued map.
                out.putIfAbsent(b.powerId(), entry.getKey());
            }
        }
        return out;
    }

    /**
     * Resolve a press into action execution. Called from the server payload handler.
     *
     * @param sp        the player who pressed the key
     * @param key       the translation key the client reported
     * @param held      true if the client says the key is currently held this tick
     *                  (used for continuous bindings); pressed-once payloads should
     *                  pass {@code true} too — edge detection happens here, not on the wire
     */
    public static void dispatch(ServerPlayer sp, String key, boolean held) {
        List<Binding> bindings = BY_KEY.get(key);
        if (bindings == null || bindings.isEmpty()) return;

        PlayerOriginData data = sp.getData(OriginAttachments.originData());

        // Snapshot the player's granted powers once so we don't pay the
        // ActiveOriginService cache lookup per binding.
        List<PowerHolder<?>> granted = ActiveOriginService.allPowers(sp);

        for (Binding b : bindings) {
            // Player must actually have the power right now (origin gate + dimension restrictions).
            boolean has = false;
            for (PowerHolder<?> h : granted) {
                if (h.id().equals(b.powerId())) { has = true; break; }
            }
            if (!has) continue;

            if (b.condition() != null && !b.condition().test(sp)) {
                fireFailAction(sp, b);
                continue;
            }

            if (b.continuous()) {
                // Continuous: client sends every tick the key is held; fire each tick.
                // No cooldown gate — continuous powers manage their own pacing in the action
                // (e.g. resource drain). Adding cooldown here would silence them entirely.
                b.action().execute(sp);
            } else {
                // Edge detection: fire once per press. The client only sends on press
                // (consumeClick), so every payload is a fresh edge.
                if (b.cooldownTicks() > 0) {
                    String cdKey = b.powerId().toString();
                    if (data.isOnCooldown(cdKey, sp.tickCount)) continue;
                    data.setCooldown(cdKey, sp.tickCount, b.cooldownTicks());
                }
                b.action().execute(sp);
            }
        }
    }

    /**
     * Run a binding's fail_action for a condition-blocked press. Non-continuous
     * bindings only dispatch on a fresh press, so every call is a real attempt;
     * continuous bindings stream a payload every held tick, so collapse the
     * stream via {@link #LAST_FAIL_TICK} to one firing per press.
     */
    private static void fireFailAction(ServerPlayer sp, Binding b) {
        if (b.failAction() == null) return;
        if (b.continuous()) {
            String fk = sp.getUUID() + ":" + b.powerId();
            long now = sp.level().getGameTime();
            Long last = LAST_FAIL_TICK.put(fk, now);
            if (last != null && (now - last) <= FAIL_REFIRE_GAP_TICKS) return;
        }
        b.failAction().execute(sp);
    }

    /**
     * Programmatic activation of a hotkey-bound power by id — the
     * {@code neoorigins:activate_power} entity-action path for powers whose
     * CompatPower config is a keybind marker (no {@code onActivated}).
     * Edge semantics: one attempt per call, condition + cooldown + fail_action
     * behave exactly like a single key press.
     *
     * @return true if a binding for {@code powerId} exists (whether or not the
     *         attempt passed its condition/cooldown), false if unknown here
     */
    public static boolean activateByPowerId(ServerPlayer sp, ResourceLocation powerId) {
        for (List<Binding> bindings : BY_KEY.values()) {
            synchronized (bindings) {
                for (Binding b : bindings) {
                    if (!b.powerId().equals(powerId)) continue;
                    if (b.condition() != null && !b.condition().test(sp)) {
                        if (b.failAction() != null) b.failAction().execute(sp);
                        return true;
                    }
                    if (b.cooldownTicks() > 0) {
                        PlayerOriginData data = sp.getData(OriginAttachments.originData());
                        String cdKey = b.powerId().toString();
                        if (data.isOnCooldown(cdKey, sp.tickCount)) return true;
                        data.setCooldown(cdKey, sp.tickCount, b.cooldownTicks());
                    }
                    b.action().execute(sp);
                    return true;
                }
            }
        }
        return false;
    }

    /** True if any binding exists for this translation key. */
    public static boolean isDeclared(String key) {
        return BY_KEY.containsKey(key);
    }

    /** True if at least one binding for the given key is continuous (every-tick while held). */
    public static boolean isContinuous(String key) {
        List<Binding> list = BY_KEY.get(key);
        if (list == null) return false;
        for (Binding b : list) {
            if (b.continuous()) return true;
        }
        return false;
    }

    /** Marker used in compat-loader diagnostics. */
    public static void logSummary() {
        if (BY_KEY.isEmpty()) return;
        NeoOrigins.LOGGER.info("[Hotkeys] {} named keybinds registered: {}",
            BY_KEY.size(), declaredKeys());
    }
}
