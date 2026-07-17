package com.cyberday1.neoorigins.screen.model;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.client.AbilitySlotKeys;
import com.cyberday1.neoorigins.client.ClientPowerCache;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.power.builtin.ConditionPassivePower;
import com.cyberday1.neoorigins.power.builtin.PersistentEffectPower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Computed detail-panel data for a selected origin. No rendering imports.
 *
 * <p>{@code powerKeyTags} is parallel to {@code powerNames}: a localized
 * hotkey tag like {@code "[R]"} (the key actually bound to the skill slot the
 * power gets, via {@link AbilitySlotKeys} — the same mapping the HUD cluster
 * labels use) or {@code ""} for passives. Screens draw it after the power
 * name in an accent color.
 */
public record OriginDetailViewModel(
    Origin origin,
    List<String> powerNames,
    List<String> powerDescs,
    List<String> powerKeyTags
) {
    public static final OriginDetailViewModel EMPTY =
        new OriginDetailViewModel(null, List.of(), List.of(), List.of());

    /**
     * @param classLayer true when this origin is being shown on the class
     *                   layer, whose first active power gets the class-skill
     *                   key instead of skill slots 1–6.
     */
    public static OriginDetailViewModel compute(Identifier selectedId, boolean classLayer) {
        if (selectedId == null) return EMPTY;
        Origin origin = OriginDataManager.INSTANCE.getOrigin(selectedId);
        if (origin == null) return EMPTY;

        Map<Identifier, Identifier> subToParent = new HashMap<>();
        for (var entry : OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.entrySet())
            for (Identifier subId : entry.getValue())
                subToParent.put(subId, entry.getKey());
        Set<Identifier> seenParents = new HashSet<>();

        Language lang = Language.getInstance();
        List<String> names = new ArrayList<>();
        List<String> descs = new ArrayList<>();
        List<String> keyTags = new ArrayList<>();

        // Skill-slot assignment, shared with the HUD cluster: actives in
        // powers() order get slots 0–5 (class layer: first active only).
        Map<Identifier, Integer> slotMap = AbilitySlotKeys.assignSlots(origin, classLayer);

        for (Identifier powerId : origin.powers()) {
            // Skip internal/capability-only power types from the info panel —
            // pack authors add these to drive client behaviour (HUD bars, etc.)
            // and don't want them cluttering the "Powers" section.
            if (isHiddenPowerType(powerId)) continue;

            Component powerName = resolvePowerName(powerId);
            Component powerDesc = resolvePowerDesc(powerId);
            String holderName = powerName != null ? powerName.getString() : "";
            String holderDesc = powerDesc != null ? powerDesc.getString() : "";

            String nameKey = "power." + powerId.getNamespace() + "." + powerId.getPath() + ".name";
            String descKey = "power." + powerId.getNamespace() + "." + powerId.getPath() + ".description";
            String resolvedName = !holderName.isEmpty() ? holderName
                : lang.has(nameKey) ? lang.getOrDefault(nameKey, "") : "";
            String resolvedDesc = !holderDesc.isEmpty() ? holderDesc
                : lang.has(descKey) ? lang.getOrDefault(descKey, "") : "";

            boolean isNamed  = !resolvedName.isEmpty();
            Identifier parentId = subToParent.get(powerId);

            if (parentId != null && !isNamed) {
                if (!seenParents.add(parentId)) continue;
                JsonObject display = OriginsMultipleExpander.MULTIPLE_DISPLAY_MAP.get(parentId);
                names.add(resolveParentDisplay(parentId, display, lang, "name", formatPowerId(parentId)));
                descs.add(resolveParentDisplay(parentId, display, lang, "description", ""));
                // A multiple collapses its un-named sub-powers into one parent row.
                // Surface the keys of ALL its keybind sub-powers (e.g. a grow/shrink
                // pair) instead of a blank tag, so multi-keybind multiples still show
                // their hotkeys in the info panel.
                keyTags.add(aggregateChildKeyTags(parentId, slotMap));
                continue;
            }

            names.add(isNamed ? resolvedName : formatPowerId(powerId));
            descs.add(resolvedDesc);
            keyTags.add(keyTagFor(powerId, slotMap));
        }

        return new OriginDetailViewModel(
            origin,
            Collections.unmodifiableList(names),
            Collections.unmodifiableList(descs),
            Collections.unmodifiableList(keyTags));
    }

    /**
     * Collected, de-duplicated hotkey tags for every sub-power of a collapsed
     * {@code multiple} parent row, space-joined (e.g. "[1] [2]"). Empty when no
     * sub-power is keybound. Order follows the expansion map (powers() order).
     */
    private static String aggregateChildKeyTags(Identifier parentId, Map<Identifier, Integer> slotMap) {
        List<Identifier> children = OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.get(parentId);
        if (children == null || children.isEmpty()) return "";
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (Identifier child : children) {
            String tag = keyTagFor(child, slotMap);
            if (!tag.isEmpty()) tags.add(tag);
        }
        return String.join(" ", tags);
    }

    /**
     * Localized hotkey tag for a slotted power — "[<bound key>]" (e.g. "[R]"),
     * with a Toggle suffix for toggle-like powers; "" for passives. Falls back
     * to the legacy S1–S6 / C labels when the slot's mapping is unbound, same
     * as the HUD cluster.
     */
    private static String keyTagFor(Identifier powerId, Map<Identifier, Integer> slotMap) {
        Integer slot = slotMap.get(powerId);
        String key;
        if (slot != null) {
            key = AbilitySlotKeys.keyNameOrLabel(slot);
        } else {
            // Not a skill-slot active, but it may be bound to a named hotkey or a
            // vanilla input key (key.use / key.attack / …). Those are real
            // activations and deserve a key tag instead of looking like passives.
            key = com.cyberday1.neoorigins.client.HotkeyAssignments.displayKeyForPower(powerId);
            if (key == null) return "";
        }
        return Component.translatable(
            isPowerToggle(powerId) ? "gui.neoorigins.power.key_tag.toggle"
                                   : "gui.neoorigins.power.key_tag",
            key).getString();
    }

    /** Returns true if the power occupies a keybind slot. Checks PowerDataManager first, then client cache. */
    private static boolean isPowerActive(Identifier powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) return holder.isActive();
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        return entry != null && entry.active();
    }

    private static boolean isHiddenPowerType(Identifier powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) {
            // Per-power `"hidden": true` opt-out from the info panel.
            if (holder.hidden()) return true;
            if (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.HideHudBarPower) {
                return true;
            }
        }
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        return entry != null && entry.hidden();
    }

    /** Returns true if the power is a toggle power. Checks PowerDataManager first, then client cache. */
    private static boolean isPowerToggle(Identifier powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) {
            if (holder.type() instanceof AbstractTogglePower<?>) return true;
            if (holder.type() instanceof PersistentEffectPower
                    && holder.config() instanceof PersistentEffectPower.Config pc) {
                return pc.toggleable();
            }
            if (holder.type() instanceof ConditionPassivePower
                    && holder.config() instanceof ConditionPassivePower.Config cc) {
                return cc.toggleable();
            }
            return false;
        }
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        return entry != null && entry.toggle();
    }

    /** Returns the power's name Component, or null if unknown. Checks PowerDataManager first, then client cache. */
    private static Component resolvePowerName(Identifier powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) return holder.name();
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        return entry != null ? entry.name() : null;
    }

    /** Returns the power's description Component, or null if unknown. Checks PowerDataManager first, then client cache. */
    private static Component resolvePowerDesc(Identifier powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) return holder.description();
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        return entry != null ? entry.description() : null;
    }

    /** Compute total scrollable content height given the number of wrapped description lines. */
    public int computeContentHeight(int descLineCount) {
        int powerSectionH = 0;
        if (!powerNames.isEmpty()) {
            powerSectionH = 9 + 4;
            for (int i = 0; i < powerNames.size(); i++) {
                powerSectionH += 11;
                if (i < powerDescs.size() && !powerDescs.get(i).isEmpty()) powerSectionH += 10;
            }
        }
        return 8 + descLineCount * 10 + 8 + powerSectionH + 6;
    }

    public static String formatPowerId(Identifier id) {
        String path = id.getPath();
        int firstSlash = path.indexOf('/');
        if (firstSlash >= 0) path = path.substring(firstSlash + 1);
        String[] segments = path.split("/");
        StringBuilder out = new StringBuilder();
        for (String seg : segments) {
            if (out.length() > 0) out.append(": ");
            boolean firstWord = true;
            for (String word : seg.split("_")) {
                if (word.isEmpty()) continue;
                if (!firstWord) out.append(' ');
                out.append(Character.toUpperCase(word.charAt(0)));
                out.append(word.substring(1));
                firstWord = false;
            }
        }
        return out.isEmpty() ? path : out.toString();
    }

    private static String resolveDisplayString(JsonElement el) {
        if (el == null) return "";
        if (el.isJsonPrimitive()) {
            String key = el.getAsString();
            return Language.getInstance().getOrDefault(key, key);
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text"))      return obj.get("text").getAsString();
            if (obj.has("translate")) {
                String key = obj.get("translate").getAsString();
                return Language.getInstance().getOrDefault(key, key);
            }
        }
        return "";
    }

    /**
     * Resolves a collapsed {@code multiple} parent's display name/description,
     * honoring (in priority order): an inline authored value on the display
     * object → an {@code auto_<field>} lang key stamped by the expander →
     * the conventional {@code power.<ns>.<path>.<field>} key → the fallback.
     * This lets legacy {@code origins:multiple} parents recover their
     * name/description from Apoli auto-generated lang keys.
     */
    private static String resolveParentDisplay(Identifier parentId, JsonObject display,
                                               Language lang, String field, String fallback) {
        if (display != null && display.has(field)) {
            String s = resolveDisplayString(display.get(field));
            if (!s.isEmpty()) return s;
        }
        String autoField = "auto_" + field;
        if (display != null && display.has(autoField) && display.get(autoField).isJsonPrimitive()) {
            String key = display.get(autoField).getAsString();
            if (lang.has(key)) return lang.getOrDefault(key, fallback);
        }
        String key = "power." + parentId.getNamespace() + "." + parentId.getPath() + "." + field;
        if (lang.has(key)) return lang.getOrDefault(key, fallback);
        return fallback;
    }

    /** A resolved display row for the evolution panel: localized name + (possibly empty) description. */
    public record TierPowerDisplay(String name, String description) {}

    /**
     * Resolves an evolution tier's power-id list (an overlay's {@code add} or
     * {@code remove} array) into deduped display rows for the info panel's
     * Evolution Path section. Synthetic sub-powers of an un-named {@code multiple}
     * collapse to a single parent row (name + description from
     * {@link OriginsMultipleExpander#MULTIPLE_DISPLAY_MAP}), exactly as the main
     * Powers section renders them — so a multiple-type evolution shows its own
     * authored name/description instead of one raw row per synthetic sub-power.
     */
    public static List<TierPowerDisplay> resolveTierPowerDisplays(List<Identifier> ids) {
        Map<Identifier, Identifier> subToParent = new HashMap<>();
        for (var entry : OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.entrySet())
            for (Identifier subId : entry.getValue())
                subToParent.put(subId, entry.getKey());
        Set<Identifier> seenParents = new HashSet<>();
        Language lang = Language.getInstance();
        List<TierPowerDisplay> out = new ArrayList<>();
        for (Identifier powerId : ids) {
            Component nameC = resolvePowerName(powerId);
            Component descC = resolvePowerDesc(powerId);
            String holderName = nameC != null ? nameC.getString() : "";
            String holderDesc = descC != null ? descC.getString() : "";
            String nameKey = "power." + powerId.getNamespace() + "." + powerId.getPath() + ".name";
            String descKey = "power." + powerId.getNamespace() + "." + powerId.getPath() + ".description";
            String resolvedName = !holderName.isEmpty() ? holderName
                : lang.has(nameKey) ? lang.getOrDefault(nameKey, "") : "";
            String resolvedDesc = !holderDesc.isEmpty() ? holderDesc
                : lang.has(descKey) ? lang.getOrDefault(descKey, "") : "";
            boolean isNamed = !resolvedName.isEmpty();
            Identifier parentId = subToParent.get(powerId);
            if (parentId != null && !isNamed) {
                if (!seenParents.add(parentId)) continue;
                JsonObject display = OriginsMultipleExpander.MULTIPLE_DISPLAY_MAP.get(parentId);
                String pn = resolveParentDisplay(parentId, display, lang, "name", formatPowerId(parentId));
                String pd = resolveParentDisplay(parentId, display, lang, "description", "");
                out.add(new TierPowerDisplay(pn, pd));
                continue;
            }
            out.add(new TierPowerDisplay(isNamed ? resolvedName : formatPowerId(powerId), resolvedDesc));
        }
        return out;
    }
}
