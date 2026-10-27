package com.cyberday1.neoorigins.power.util;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import com.cyberday1.neoorigins.util.FoodCost;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Hunger / resource costs for powers: the one-shot charge an active ability pays
 * on use, and the per-interval upkeep a toggle pays while it is on.
 *
 * <p>Every charge is all-or-nothing: nothing is deducted unless the whole cost
 * can be paid. A resource cost falls back to hunger while resource bars are
 * globally disabled ({@code disable_resource_bars}).
 *
 * <p>Toggle types read their upkeep fields off the raw power JSON at load
 * ({@link #parse}) into a table keyed by power id, so no toggle Config record
 * has to carry them. {@code effect_over_time} keeps its own cost fields.
 */
public final class PowerUpkeep {

    private PowerUpkeep() {}

    /** A resolved cost, after the bars-disabled fallback has been applied. */
    public record Charge(int hunger, String resource, int amount) {

        public static final Charge NONE = new Charge(0, "", 0);

        /** Folds a resource cost into hunger when resource bars are disabled. */
        public static Charge of(int hunger, String resource, int amount, boolean barsDisabled) {
            int h = Math.max(0, hunger);
            boolean hasResource = resource != null && !resource.isEmpty() && amount > 0;
            if (hasResource && barsDisabled) return new Charge(h + amount, "", 0);
            return hasResource ? new Charge(h, resource, amount) : new Charge(h, "", 0);
        }

        /** As above, reading the server's disable_resource_bars switch only when a resource is involved. */
        public static Charge of(int hunger, String resource, int amount) {
            boolean hasResource = resource != null && !resource.isEmpty() && amount > 0;
            return of(hunger, resource, amount, hasResource && ContentTogglesConfig.isResourceBarsDisabled());
        }

        public boolean isFree() { return hunger <= 0 && amount <= 0; }

        public boolean affordable(ServerPlayer player) {
            if (hunger > 0 && !FoodCost.canAfford(player, hunger)) return false;
            return amount <= 0 || ResourcePower.getValue(player, resource) >= amount;
        }

        /** Deducts the cost. Call only after {@link #affordable}. */
        public void spend(ServerPlayer player) {
            if (hunger > 0) FoodCost.spend(player, hunger);
            if (amount > 0) ResourcePower.deduct(player, resource, amount);
        }

        /** Pays the whole cost or none of it. */
        public boolean pay(ServerPlayer player) {
            if (isFree()) return true;
            if (!affordable(player)) return false;
            spend(player);
            return true;
        }
    }

    /** Per-interval upkeep authored on a toggle power. */
    public record Upkeep(int hungerCost, String resourceCost, int resourceCostAmount,
                         int interval, EntityCondition condition) {

        public static final int DEFAULT_INTERVAL = 20;
        public static final Upkeep FREE =
            new Upkeep(0, "", 0, DEFAULT_INTERVAL, EntityCondition.alwaysTrue());

        public Upkeep {
            resourceCost = resourceCost == null ? "" : resourceCost;
            interval = Math.max(1, interval);
            condition = condition == null ? EntityCondition.alwaysTrue() : condition;
        }

        public boolean isFree() {
            return hungerCost <= 0 && (resourceCost.isEmpty() || resourceCostAmount <= 0);
        }

        public Charge charge(boolean barsDisabled) {
            return Charge.of(hungerCost, resourceCost, resourceCostAmount, barsDisabled);
        }
    }

    /** What one tick of upkeep came to. */
    public enum Outcome {
        /** No cost, or not a charge tick. */
        NOT_DUE,
        /** A charge tick, but {@code upkeep_condition} failed, so nothing was owed. */
        WAIVED,
        PAID,
        /** A charge tick the player could not pay; nothing was deducted. */
        SHORT
    }

    /** What a power should do this tick after its upkeep ran. */
    public enum Verdict {
        RUN,
        /** A toggle missed a charge: switch it off. */
        SHUT_OFF,
        /** A power with no toggle missed a charge this tick: tear its effect down. */
        LAPSED,
        /** Still lapsed from an earlier missed charge: keep the effect down. */
        SUSPENDED,
        /** A lapsed power paid again this tick: bring the effect back. */
        RESUMED;

        public boolean runs() { return this == RUN || this == RESUMED; }
    }

    // ── Upkeep table ────────────────────────────────────────────────────────

    private static final Map<Identifier, Upkeep> TABLE = new ConcurrentHashMap<>();

    /** Lapsed non-toggle powers, as {@code uuid|power id}. */
    private static final Set<String> LAPSED = ConcurrentHashMap.newKeySet();

    /** Toggles authored {@code toggle_messages: false}: no on/off chat line. */
    private static final Set<Identifier> SILENT = ConcurrentHashMap.newKeySet();

    /** Called once per power reload, before any power is parsed. */
    public static void clearTable() {
        TABLE.clear();
        SILENT.clear();
    }

    /** Records a toggle's {@code toggle_messages} flag (true = the default chat lines). */
    public static void registerToggleMessages(Identifier id, boolean messages) {
        if (id == null) return;
        if (messages) SILENT.remove(id);
        else SILENT.add(id);
    }

    /** False when the toggle was authored {@code toggle_messages: false}. */
    public static boolean sendsToggleMessages(Identifier id) {
        return id == null || SILENT.isEmpty() || !SILENT.contains(id);
    }

    /** Reads {@code toggle_messages} (default true); a malformed value keeps the default. */
    public static boolean parseToggleMessages(JsonObject obj, String powerId) {
        if (!obj.has("toggle_messages")) return true;
        try {
            return obj.get("toggle_messages").getAsBoolean();
        } catch (RuntimeException e) {
            NeoOrigins.LOGGER.warn("Power {}: malformed toggle_messages, keeping the chat messages: {}",
                powerId, e.getMessage());
            return true;
        }
    }

    public static void register(Identifier id, Upkeep upkeep) {
        if (id == null) return;
        if (upkeep == null || upkeep.isFree()) TABLE.remove(id);
        else TABLE.put(id, upkeep);
    }

    public static Upkeep forPower(Identifier id) {
        if (id == null) return Upkeep.FREE;
        return TABLE.getOrDefault(id, Upkeep.FREE);
    }

    /**
     * Reads the shared upkeep fields off a toggle power's JSON. A malformed field
     * is logged and the power loads with no upkeep, rather than failing the power.
     */
    public static Upkeep parse(JsonObject obj, String powerId) {
        try {
            int hunger = obj.has("hunger_cost") ? obj.get("hunger_cost").getAsInt() : 0;
            String res = obj.has("resource_cost") ? obj.get("resource_cost").getAsString() : "";
            int amount = obj.has("resource_cost_amount") ? obj.get("resource_cost_amount").getAsInt() : 0;
            int interval = obj.has("upkeep_interval")
                ? obj.get("upkeep_interval").getAsInt() : Upkeep.DEFAULT_INTERVAL;
            EntityCondition cond = ConditionParser.parseField(obj, "upkeep_condition", powerId);
            return new Upkeep(hunger, res, amount, interval, cond);
        } catch (RuntimeException e) {
            NeoOrigins.LOGGER.warn("Power {}: malformed upkeep field, loading with no upkeep: {}",
                powerId, e.getMessage());
            return Upkeep.FREE;
        }
    }

    // ── Charging ────────────────────────────────────────────────────────────

    /** Runs one tick of {@code upkeep}: charges on every {@code interval}-th tick. */
    public static Outcome chargeIfDue(ServerPlayer player, Upkeep upkeep) {
        if (upkeep.isFree()) return Outcome.NOT_DUE;
        return chargeIfDue(player, upkeep, barsDisabled(upkeep));
    }

    public static Outcome chargeIfDue(ServerPlayer player, Upkeep upkeep, boolean barsDisabled) {
        if (upkeep.isFree()) return Outcome.NOT_DUE;
        // Keyed off the player's own tick count, so a toggle flipped on and off
        // between two charge ticks is never charged twice.
        if (player.tickCount % upkeep.interval() != 0) return Outcome.NOT_DUE;
        if (!upkeep.condition().test(player)) return Outcome.WAIVED;
        return upkeep.charge(barsDisabled).pay(player) ? Outcome.PAID : Outcome.SHORT;
    }

    /**
     * One tick of upkeep for power {@code id}. A toggle that misses a charge is
     * told to switch off. A power with no toggle lapses instead: its effect stays
     * down until a later charge tick it can pay (or one whose condition fails).
     */
    public static Verdict tick(ServerPlayer player, Identifier id, Upkeep upkeep, boolean toggle) {
        if (upkeep.isFree()) return Verdict.RUN;
        return tick(player, id, upkeep, toggle, barsDisabled(upkeep));
    }

    /** The config is only consulted when there is a resource cost it could change. */
    private static boolean barsDisabled(Upkeep upkeep) {
        return !upkeep.resourceCost().isEmpty() && upkeep.resourceCostAmount() > 0
            && ContentTogglesConfig.isResourceBarsDisabled();
    }

    public static Verdict tick(ServerPlayer player, Identifier id, Upkeep upkeep,
                               boolean toggle, boolean barsDisabled) {
        if (upkeep.isFree()) return Verdict.RUN;
        Outcome outcome = chargeIfDue(player, upkeep, barsDisabled);
        if (toggle) return outcome == Outcome.SHORT ? Verdict.SHUT_OFF : Verdict.RUN;

        String key = lapseKey(player, id);
        boolean wasLapsed = LAPSED.contains(key);
        switch (outcome) {
            case SHORT -> {
                LAPSED.add(key);
                return wasLapsed ? Verdict.SUSPENDED : Verdict.LAPSED;
            }
            case PAID, WAIVED -> {
                LAPSED.remove(key);
                return wasLapsed ? Verdict.RESUMED : Verdict.RUN;
            }
            default -> {
                return wasLapsed ? Verdict.SUSPENDED : Verdict.RUN;
            }
        }
    }

    public static boolean isLapsed(ServerPlayer player, Identifier id) {
        return !LAPSED.isEmpty() && LAPSED.contains(lapseKey(player, id));
    }

    public static void clearLapse(ServerPlayer player, Identifier id) {
        LAPSED.remove(lapseKey(player, id));
    }

    public static void clearPlayer(UUID uuid) {
        String prefix = uuid + "|";
        LAPSED.removeIf(k -> k.startsWith(prefix));
    }

    private static String lapseKey(ServerPlayer player, Identifier id) {
        return player.getUUID() + "|" + id;
    }

    // ── Feedback ────────────────────────────────────────────────────────────

    /** Pushes toggle/capability state to the client. Swappable so tests run without a network. */
    private static volatile Consumer<ServerPlayer> resync; // null = the real network sync

    /** Test seam: replaces the client resync. Returns the previous one. */
    public static Consumer<ServerPlayer> setResyncForTest(Consumer<ServerPlayer> replacement) {
        Consumer<ServerPlayer> prev = resync;
        resync = replacement;
        return prev;
    }

    /** Tells the player a toggle switched itself off, the same message a keypress gives. */
    public static void announceShutOff(ServerPlayer player) {
        announceShutOff(player, com.cyberday1.neoorigins.api.power.PowerHolder.currentDispatchId());
    }

    public static void announceShutOff(ServerPlayer player, Identifier id) {
        announceToggle(player, id, false);
        resyncClient(player);
    }

    /**
     * The green "on" / red "off" chat line for a toggle keypress, skipped when the
     * power was authored {@code toggle_messages: false}.
     */
    public static void announceToggle(ServerPlayer player, Identifier id, boolean on) {
        if (!sendsToggleMessages(id)) return;
        player.sendSystemMessage(Component.translatable(on ? "neoorigins.toggle.on" : "neoorigins.toggle.off")
            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    public static void resyncClient(ServerPlayer player) {
        Consumer<ServerPlayer> r = resync;
        if (r != null) r.accept(player);
        else com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncActivePowersToPlayer(player);
    }
}
