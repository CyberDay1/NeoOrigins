package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import com.cyberday1.neoorigins.screen.model.OriginListEntry;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.*;

/**
 * Stateful selection logic for the origin selection screen.
 * No Minecraft rendering imports — pure data logic.
 */
public class OriginSelectionPresenter {

    private List<OriginLayer> pendingLayers  = List.of();
    private int currentLayerIndex            = 0;
    private Identifier selectedOriginId      = null;
    private int listScrollOffset             = 0;
    private String searchText                = "";
    private boolean forceReselect            = false;

    private final List<OriginListEntry> allRows      = new ArrayList<>();
    private final List<OriginListEntry> filteredRows = new ArrayList<>();
    private final List<Identifier>      allOriginIds = new ArrayList<>();

    /** Set the forceReselect flag before calling init(). */
    public void setForceReselect(boolean forceReselect) {
        this.forceReselect = forceReselect;
    }

    /**
     * Query pending layers. Call on every screen init (e.g. after resize).
     * Does NOT reset currentLayerIndex so mid-selection state survives resize.
     * Returns false if there are no pending layers (screen should close).
     * When forceReselect is true, includes all layers (not just unfilled ones).
     */
    public boolean init() {
        if (forceReselect) {
            pendingLayers = LayerDataManager.INSTANCE.getSortedLayers().stream()
                .filter(l -> !l.hidden())
                .toList();
        } else {
            pendingLayers = LayerDataManager.INSTANCE.getSortedLayers().stream()
                .filter(l -> !l.hidden())
                .filter(l -> !ClientOriginState.getOrigins().containsKey(l.id()))
                .toList();
        }
        skipEmptyLayers();
        return !pendingLayers.isEmpty() && currentLayerIndex < pendingLayers.size();
    }

    /** Rebuild row data for the current layer, resetting scroll and search. */
    public void buildRows() {
        allRows.clear();
        allOriginIds.clear();
        listScrollOffset = 0;
        searchText       = "";

        OriginLayer layer = currentLayer();

        List<Identifier> rawIds = new ArrayList<>();
        for (var co : layer.origins()) {
            if (OriginDataManager.INSTANCE.hasOrigin(co.origin()))
                rawIds.add(co.origin());
        }

        Map<String, List<Identifier>> byNamespace = new LinkedHashMap<>();
        for (Identifier id : rawIds)
            byNamespace.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id);

        List<String> namespaces = new ArrayList<>(byNamespace.keySet());
        namespaces.sort((a, b) -> {
            if ("neoorigins".equals(a)) return -1;
            if ("neoorigins".equals(b)) return 1;
            return getModName(a).compareToIgnoreCase(getModName(b));
        });

        for (String ns : namespaces) {
            allRows.add(OriginListEntry.header(ns, getModName(ns)));
            List<Identifier> nsIds = byNamespace.get(ns);
            nsIds.sort(Comparator.comparing(id -> getOriginDisplayName(id).toLowerCase(Locale.ROOT)));
            for (Identifier id : nsIds) {
                allRows.add(OriginListEntry.origin(id, getOriginDisplayName(id), ns));
                allOriginIds.add(id);
            }
        }

        applySearch();
    }

    public void applySearch() {
        filteredRows.clear();
        if (searchText.isEmpty()) { filteredRows.addAll(allRows); return; }
        String lower = searchText.toLowerCase(Locale.ROOT);
        OriginListEntry pendingHeader = null;
        for (OriginListEntry row : allRows) {
            if (row.isSectionHeader()) {
                pendingHeader = row;
            } else if (row.displayName().toLowerCase(Locale.ROOT).contains(lower)) {
                if (pendingHeader != null) { filteredRows.add(pendingHeader); pendingHeader = null; }
                filteredRows.add(row);
            }
        }
    }

    /** Update search text and rebuild filtered list. Returns true if the filter changed. */
    public boolean setSearch(String text) {
        if (text.equals(searchText)) return false;
        searchText       = text;
        listScrollOffset = 0;
        applySearch();
        return true;
    }

    public void select(Identifier id) {
        selectedOriginId = id;
    }

    /**
     * Confirm current selection, send the network packet, and advance to the next layer.
     * Skips layers that have no available origins (e.g., all classes disabled).
     * Returns true if more layers remain, false if all layers are filled.
     */
    public boolean confirm() {
        if (selectedOriginId == null) return !isDone();
        OriginLayer layer = currentLayer();
        ClientPacketDistributor.sendToServer(new ChooseOriginPayload(layer.id(), selectedOriginId));
        var updated = new HashMap<>(ClientOriginState.getOrigins());
        updated.put(layer.id(), selectedOriginId);
        ClientOriginState.setOrigins(updated, false);
        currentLayerIndex++;
        selectedOriginId = null;
        skipEmptyLayers();
        return !isDone();
    }

    /** Go back one layer. Returns true if successful. */
    public boolean back() {
        if (currentLayerIndex <= 0) return false;
        currentLayerIndex--;
        selectedOriginId = null;
        return true;
    }

    /** Return a random origin ID from the current layer, or null if none. */
    public Identifier randomId() {
        if (allOriginIds.isEmpty()) return null;
        return allOriginIds.get((int) (Math.random() * allOriginIds.size()));
    }

    /**
     * Skip layers where all origins have been disabled/removed.
     * Advances currentLayerIndex past any empty layers.
     */
    private void skipEmptyLayers() {
        while (currentLayerIndex < pendingLayers.size()) {
            OriginLayer layer = pendingLayers.get(currentLayerIndex);
            boolean hasAny = layer.origins().stream()
                .anyMatch(co -> OriginDataManager.INSTANCE.hasOrigin(co.origin()));
            if (hasAny) break;
            currentLayerIndex++;
        }
    }

    public boolean isDone()                    { return currentLayerIndex >= pendingLayers.size(); }
    public OriginLayer currentLayer()          { return pendingLayers.get(currentLayerIndex); }
    public int currentLayerIndex()             { return currentLayerIndex; }
    public int totalLayers()                   { return pendingLayers.size(); }
    public Identifier selectedOriginId()       { return selectedOriginId; }
    public int listScrollOffset()              { return listScrollOffset; }
    public void setListScrollOffset(int v)     { listScrollOffset = v; }
    public List<OriginListEntry> filteredRows(){ return filteredRows; }
    public List<Identifier> allOriginIds()     { return allOriginIds; }
    public String searchText()                 { return searchText; }

    private static String getModName(String namespace) {
        return ModList.get()
            .getModContainerById(namespace)
            .map(c -> c.getModInfo().getDisplayName())
            .orElseGet(() -> namespace.isEmpty() ? namespace
                : Character.toUpperCase(namespace.charAt(0)) + namespace.substring(1));
    }

    private static String getOriginDisplayName(Identifier id) {
        var o = OriginDataManager.INSTANCE.getOrigin(id);
        return o != null ? o.name().getString() : id.getPath();
    }
}
