package com.cyberday1.neoorigins.screen.model;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.client.ClientPowerCache;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.LinkedHashMap;

/** Computed detail-panel data for a selected origin. No rendering imports. */
public record OriginDetailViewModel(
    Origin origin,
    List<String> powerNames,
    List<String> powerDescs
) {
    public static final OriginDetailViewModel EMPTY =
        new OriginDetailViewModel(null, List.of(), List.of());

    public static OriginDetailViewModel compute(ResourceLocation selectedId) {
        if (selectedId == null) return EMPTY;
        Origin origin = OriginDataManager.INSTANCE.getOrigin(selectedId);
        if (origin == null) return EMPTY;

        Map<ResourceLocation, ResourceLocation> subToParent = new HashMap<>();
        for (var entry : OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.entrySet())
            for (ResourceLocation subId : entry.getValue())
                subToParent.put(subId, entry.getKey());
        Set<ResourceLocation> seenParents = new HashSet<>();

        Language lang = Language.getInstance();
        List<String> names = new ArrayList<>();
        List<String> descs = new ArrayList<>();

        // Pre-count skill slot assignments across all powers in this origin
        int skillSlot = 1;
        Map<ResourceLocation, Integer> slotMap = new LinkedHashMap<>();
        for (ResourceLocation powerId : origin.powers()) {
            if (isPowerActive(powerId) && skillSlot <= 4) {
                slotMap.put(powerId, skillSlot++);
            }
        }

        for (ResourceLocation powerId : origin.powers()) {
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
            ResourceLocation parentId = subToParent.get(powerId);

            if (parentId != null && !isNamed) {
                if (!seenParents.add(parentId)) continue;
                JsonObject display = OriginsMultipleExpander.MULTIPLE_DISPLAY_MAP.get(parentId);
                names.add(display != null && display.has("name")
                    ? resolveDisplayString(display.get("name")) : formatPowerId(parentId));
                descs.add(display != null && display.has("description")
                    ? resolveDisplayString(display.get("description")) : "");
                continue;
            }

            String displayName = isNamed ? resolvedName : formatPowerId(powerId);
            String tag = "";
            if (slotMap.containsKey(powerId)) {
                int slot = slotMap.get(powerId);
                if (isPowerToggle(powerId)) {
                    tag = " [Skill " + slot + " - Toggle]";
                } else {
                    tag = " [Skill " + slot + "]";
                }
            }
            displayName += tag;
            names.add(displayName);
            descs.add(resolvedDesc);
        }

        return new OriginDetailViewModel(
            origin,
            Collections.unmodifiableList(names),
            Collections.unmodifiableList(descs));
    }

    /** Returns true if the power occupies a keybind slot. Checks PowerDataManager first, then client cache. */
    private static boolean isPowerActive(ResourceLocation powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) return holder.isActive();
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        return entry != null && entry.active();
    }

    /** Power types that should never appear in the info panel — capability-only / HUD-only. */
    private static final Set<String> HIDDEN_POWER_TYPES = Set.of(
        "neoorigins:hide_hud_bar"
    );

    private static boolean isHiddenPowerType(ResourceLocation powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) {
            // Per-power `"hidden": true` opt-out from the info panel.
            if (holder.hidden()) return true;
            if (holder.type() instanceof com.cyberday1.neoorigins.power.builtin.HideHudBarPower) {
                return true;
            }
        }
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        if (entry != null) {
            if (entry.hidden()) return true;
            if (entry.typeId() != null) {
                return HIDDEN_POWER_TYPES.contains(entry.typeId().toString());
            }
        }
        return false;
    }

    /** Returns true if the power is a toggle power. Checks PowerDataManager first, then client cache. */
    private static boolean isPowerToggle(ResourceLocation powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) return holder.type() instanceof AbstractTogglePower<?>;
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        return entry != null && entry.toggle();
    }

    /** Returns the power's name Component, or null if unknown. Checks PowerDataManager first, then client cache. */
    private static Component resolvePowerName(ResourceLocation powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null) return holder.name();
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        return entry != null ? entry.name() : null;
    }

    /** Returns the power's description Component, or null if unknown. Checks PowerDataManager first, then client cache. */
    private static Component resolvePowerDesc(ResourceLocation powerId) {
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

    public static String formatPowerId(ResourceLocation id) {
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
}
