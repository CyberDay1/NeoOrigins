package com.cyberday1.neoorigins.screen.model;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;

import java.util.*;

/** Computed detail-panel data for a selected origin. No rendering imports. */
public record OriginDetailViewModel(
    Origin origin,
    List<String> powerNames,
    List<String> powerDescs
) {
    public static final OriginDetailViewModel EMPTY =
        new OriginDetailViewModel(null, List.of(), List.of());

    public static OriginDetailViewModel compute(Identifier selectedId) {
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

        for (Identifier powerId : origin.powers()) {
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            String holderName = holder != null ? holder.name().getString() : "";
            String holderDesc = holder != null ? holder.description().getString() : "";

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
                names.add(display != null && display.has("name")
                    ? resolveDisplayString(display.get("name")) : formatPowerId(parentId));
                descs.add(display != null && display.has("description")
                    ? resolveDisplayString(display.get("description")) : "");
                continue;
            }

            String displayName = isNamed ? resolvedName : formatPowerId(powerId);
            if (holder != null) {
                if (holder.type() instanceof AbstractTogglePower<?>) {
                    displayName += " [Toggle]";
                } else if (holder.type() instanceof AbstractActivePower<?>) {
                    displayName += " [Active]";
                }
            }
            names.add(displayName);
            descs.add(resolvedDesc);
        }

        return new OriginDetailViewModel(
            origin,
            Collections.unmodifiableList(names),
            Collections.unmodifiableList(descs));
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
}
