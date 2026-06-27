package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.compat.condition.ItemCondition;
import com.cyberday1.neoorigins.compat.condition.ItemConditionParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * One modular, multipurpose gate over <em>equipping</em> and <em>using</em>
 * items: {@code neoorigins:restrict_items}. A single flexible power that covers
 * both blacklist ({@code deny: true}) and allow-list ({@code deny: false})
 * semantics for any item matched by a reusable {@link ItemCondition} predicate,
 * across any equipment slot and/or hand.
 *
 * <p>Config fields:
 * <ul>
 *   <li>{@code item_condition} — an Apoli-style item condition (id / tag / NBT /
 *       enchantment / empty, with AND/OR/NOT composition), parsed by
 *       {@link ItemConditionParser}. Absent → matches every stack.</li>
 *   <li>{@code slots} — optional list of {@link EquipmentSlot} names the equip
 *       gate applies to (empty = all slots, including {@code offhand}).</li>
 *   <li>{@code hands} — optional list of {@link InteractionHand} names the use
 *       gate applies to (empty = both hands).</li>
 *   <li>{@code prevent_equip} — reject equipping a gated item.</li>
 *   <li>{@code prevent_use} — reject <em>using</em> a gated item (right-click /
 *       raise: shields, totems, bows, food, ...).</li>
 *   <li>{@code deny} — {@code true} = blacklist (matching items are forbidden);
 *       {@code false} = allow-list (ONLY matching items are permitted for the
 *       gated action, everything else is forbidden).</li>
 *   <li>{@code condition} — optional {@link EntityCondition} gating the whole
 *       power: the gate is inert unless it passes.</li>
 * </ul>
 *
 * <p><b>Modularity.</b> The single decision function {@link #isForbidden} applies
 * the {@code deny} / allow-list semantics and is called from every enforcement
 * site — the equip handler, the use handlers, and the totem mixin — so the
 * predicate logic is never duplicated. Enforcement is wired in
 * {@link com.cyberday1.neoorigins.event.InteractionPowerEvents} (equip + use)
 * and {@code LivingEntityTotemGateMixin} (totem-of-undying).
 */
public class RestrictItemsPower extends PowerType<RestrictItemsPower.Config> {

    public record Config(
        ItemCondition itemCondition,
        Set<EquipmentSlot> slots,
        Set<InteractionHand> hands,
        boolean preventEquip,
        boolean preventUse,
        boolean deny,
        Optional<EntityCondition> condition,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "restrict_items: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "restrict_items: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();

                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:restrict_items";

                // item_condition: reuse the shared Apoli item-condition parser.
                // Absent → match every stack (alwaysTrue), so an allow-list with no
                // condition permits everything and a blacklist forbids everything.
                ItemCondition itemCond;
                if (obj.has("item_condition") && obj.get("item_condition").isJsonObject()) {
                    itemCond = ItemConditionParser.parse(obj.getAsJsonObject("item_condition"));
                } else {
                    itemCond = ItemCondition.alwaysTrue();
                }

                Set<EquipmentSlot> slots = parseSlots(obj);
                Set<InteractionHand> hands = parseHands(obj);

                boolean preventEquip = obj.has("prevent_equip") && obj.get("prevent_equip").getAsBoolean();
                boolean preventUse = obj.has("prevent_use") && obj.get("prevent_use").getAsBoolean();
                boolean deny = !obj.has("deny") || obj.get("deny").getAsBoolean();

                Optional<EntityCondition> cond = obj.has("condition")
                    ? Optional.of(ConditionParser.parseField(obj, "condition", t))
                    : Optional.empty();

                return DataResult.success(Pair.of(
                    new Config(itemCond, slots, hands, preventEquip, preventUse, deny, cond, t),
                    ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };

        private static Set<EquipmentSlot> parseSlots(JsonObject obj) {
            Set<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
            if (obj.has("slots") && obj.get("slots").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("slots");
                for (JsonElement el : arr) {
                    EquipmentSlot slot = slotByName(el.getAsString());
                    if (slot != null) slots.add(slot);
                }
            }
            return slots;
        }

        private static Set<InteractionHand> parseHands(JsonObject obj) {
            Set<InteractionHand> hands = EnumSet.noneOf(InteractionHand.class);
            if (obj.has("hands") && obj.get("hands").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("hands");
                for (JsonElement el : arr) {
                    InteractionHand hand = handByName(el.getAsString());
                    if (hand != null) hands.add(hand);
                }
            }
            return hands;
        }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /** Tolerant slot lookup by vanilla name (e.g. {@code mainhand}, {@code offhand}, {@code head}). */
    private static EquipmentSlot slotByName(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (EquipmentSlot s : EquipmentSlot.values()) {
            if (s.getName().equalsIgnoreCase(n) || s.name().equalsIgnoreCase(n)) return s;
        }
        return null;
    }

    /** Tolerant hand lookup: {@code mainhand}/{@code main_hand}/{@code main} and {@code offhand}/{@code off_hand}/{@code off}. */
    private static InteractionHand handByName(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "mainhand", "main_hand", "main", "main_hand_slot" -> InteractionHand.MAIN_HAND;
            case "offhand", "off_hand", "off" -> InteractionHand.OFF_HAND;
            default -> {
                try { yield InteractionHand.valueOf(n.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { yield null; }
            }
        };
    }

    // ── The single decision function (called from every enforcement site) ──────

    /**
     * Core gate decision applied to a single stack. Returns true when the gated
     * action on {@code stack} is FORBIDDEN by this config, applying the
     * {@code deny} / allow-list semantics:
     * <ul>
     *   <li>{@code deny == true} (blacklist): forbidden iff the stack MATCHES
     *       the item condition.</li>
     *   <li>{@code deny == false} (allow-list): forbidden iff the stack does NOT
     *       match — only matching items are permitted, everything else is barred.</li>
     * </ul>
     *
     * <p>Slot / hand scoping is the caller's responsibility (it knows which one
     * applies) and is provided by the {@link #appliesToSlot} / {@link #appliesToHand}
     * helpers. This method only decides match-vs-action and is shared verbatim by
     * the equip, use, and totem paths.
     */
    public static boolean isForbidden(ItemStack stack, Config config) {
        if (stack.isEmpty()) return false;
        boolean matches = config.itemCondition().test(stack);
        return config.deny() ? matches : !matches;
    }

    /** True if the equip gate applies to {@code slot} (empty slot list = all slots). */
    public static boolean appliesToSlot(EquipmentSlot slot, Config config) {
        return config.slots().isEmpty() || config.slots().contains(slot);
    }

    /** True if the use gate applies to {@code hand} (empty hand list = both hands). */
    public static boolean appliesToHand(InteractionHand hand, Config config) {
        return config.hands().isEmpty() || config.hands().contains(hand);
    }

    /**
     * True when this config forbids EQUIPPING {@code stack} into {@code slot}.
     * Combines the whole-power {@link EntityCondition} gate, the equip-enabled
     * flag, slot scoping, and {@link #isForbidden}.
     */
    public static boolean blocksEquip(net.minecraft.world.entity.LivingEntity entity,
                                      ItemStack stack, EquipmentSlot slot, Config config) {
        if (!config.preventEquip()) return false;
        if (!appliesToSlot(slot, config)) return false;
        if (!conditionPasses(entity, config)) return false;
        return isForbidden(stack, config);
    }

    /**
     * True when this config forbids USING {@code stack} in {@code hand}.
     * Combines the whole-power {@link EntityCondition} gate, the use-enabled flag,
     * hand scoping, and {@link #isForbidden}. {@code hand} may be {@code null}
     * (totem path, where the dying entity's hands are scanned by the caller) — a
     * null hand skips the hand-scope test.
     */
    public static boolean blocksUse(net.minecraft.world.entity.LivingEntity entity,
                                    ItemStack stack, InteractionHand hand, Config config) {
        if (!config.preventUse()) return false;
        if (hand != null && !appliesToHand(hand, config)) return false;
        if (!conditionPasses(entity, config)) return false;
        return isForbidden(stack, config);
    }

    /**
     * Evaluates the optional whole-power {@link EntityCondition}. Only player
     * entities can be tested (the condition DSL targets players); for non-player
     * entities a present condition is treated as failing closed (gate inert), so a
     * mob can never be unexpectedly locked out by a player-shaped condition.
     */
    private static boolean conditionPasses(net.minecraft.world.entity.LivingEntity entity, Config config) {
        if (config.condition().isEmpty()) return true;
        if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
            return config.condition().get().test(sp);
        }
        return false;
    }

    /**
     * Server-authoritative correction for a use the gate just cancelled.
     *
     * <p>Item <em>use</em> is client-predicted: when the player right-clicks a
     * shield (or any {@link net.minecraft.world.item.UseAnim BLOCK}/bow/food item),
     * the client's {@code MultiPlayerGameMode.useItem} fires its own
     * {@code RightClickItem} / {@code startUsingItem} and raises the item locally
     * <em>before</em> the server round-trips. Our use handlers are server-only
     * ({@code instanceof ServerPlayer}), so cancelling them stops the use on the
     * server — {@code isUsingItem()} stays false, so a shield grants no protection —
     * but the server never tells the predicting client to drop the raise. The result
     * the tester sees is "I can still raise / use a shield" even though it's purely
     * cosmetic prediction with no server effect.
     *
     * <p>This pushes the player's authoritative state back down so the client's
     * prediction is overwritten: the living-entity flags (the using-item bit the
     * shield-raise visual reads) and the held items. Per the project rule "server is
     * source of truth, sync to clients" — the gate decision is unchanged; we only
     * make the rejected client prediction snap back.
     */
    public static void resyncUseState(net.minecraft.server.level.ServerPlayer sp) {
        // Re-broadcast the authoritative entity data (DATA_LIVING_ENTITY_FLAGS holds
        // the "using item" / "offhand" bits the client reads to draw a raised shield).
        // Since the server cancelled the use, those bits are clear, and resending them
        // makes the client's onSyncedDataUpdated drop its predicted useItem.
        var values = sp.getEntityData().getNonDefaultValues();
        if (values != null && !values.isEmpty()) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                sp.getId(), values));
        }
        // Resend both hands so a predicted consume/raise that touched the stack is
        // corrected too (mirrors vanilla's held-item resync on a rejected use).
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(
            sp.getId(), java.util.List.of(
                com.mojang.datafixers.util.Pair.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    sp.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)),
                com.mojang.datafixers.util.Pair.of(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    sp.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND)))));
    }
}
