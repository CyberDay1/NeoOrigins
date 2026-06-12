package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders the ability HUD cluster above the hotbar: one cell per occupied
 * skill slot (S1–S6) plus the class active (C).
 *
 * <p>A slot whose power declares no {@code cooldown_icon} keeps the original
 * 20×3 horizontal drain bar (and only appears while on cooldown). With a
 * {@code cooldown_icon} (item id, or a datapack {@code .png} texture under
 * {@code assets/<ns>/textures/}) the cell instead shows the 16×16 icon:
 * while recharging it sits under a clock-style radial sweep — a
 * semi-transparent dark fill over the not-yet-recharged arc, wiping clockwise
 * from 12 o'clock — and, when {@code cooldown_countdown} is set (and the
 * client's {@code show_cooldown_countdown} master switch allows it), the
 * remaining whole seconds centered on the icon, drawn translucently at the
 * {@code cooldown_countdown_opacity} client-config alpha.
 *
 * <p>Beyond live cooldowns the cluster also shows (per the
 * {@code hud_ability_display} client config):
 * <ul>
 *   <li>icon-bearing <b>toggleable</b> powers — full-bright while toggled on,
 *       dimmed (40% dark overlay) while off (default mode and up);</li>
 *   <li>in {@code ALL_ACTIVE_ABILITIES} mode, every icon-bearing keybind
 *       ability as a persistent slot, idle or not;</li>
 *   <li>idle cooldown abilities that declare {@code always_show_icon}, or all
 *       of them when the {@code always_show_ability_icons} client config is
 *       on (full-bright, no sweep, no countdown).</li>
 * </ul>
 *
 * <p>Icon slots are labeled with the actual bound key's short name in the
 * top-right corner of the icon (shadowed, ≥3-char names truncated to 2);
 * bar-only slots keep the legacy S1–S6/C labels above the bar. When a screen
 * is open over the HUD (e.g. chat) and the cursor rests on an icon slot, a
 * tooltip with the power's name and description appears.
 *
 * <p>The whole cluster is draggable in the HUD editor; its position persists
 * in {@code config/neoorigins/hud.json} under {@link #POSITION_KEY} as
 * screen-percentages, falling back to the historical centered /
 * {@code screenH - 52} default when unset.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public class CooldownHudOverlay {

    /** {@link ResourceHudPositions} key for the draggable cooldown cluster. */
    public static final String POSITION_KEY = "neoorigins:cooldown_cluster";

    public static final int BAR_W = 20;
    public static final int BAR_H = 3;
    public static final int BAR_GAP = 6;
    private static final int BAR_BG = 0xFF1A1A30;
    private static final int BAR_FILL = 0xFF4A90D9;
    private static final int LABEL_COLOR = 0xFF9999BB;

    public static final int ICON_SIZE = 16;
    /** Semi-transparent dark fill for the not-yet-recharged arc. */
    private static final int SWEEP_COLOR = 0xB0101018;
    private static final int COUNTDOWN_RGB = 0xFFFFFF;
    /** 40% dark overlay dimming a toggleable power's icon while toggled off. */
    private static final int TOGGLE_OFF_TINT = 0x66000000;
    private static final int KEY_LABEL_COLOR = 0xFFFFFFFF;
    private static final int TOOLTIP_BG = 0xF0100010;
    private static final int TOOLTIP_BORDER = 0xFF5000FF;

    /**
     * Resolved icon cache: raw {@code cooldown_icon} string → {@link ItemStack}
     * (item form), {@link Identifier} (texture form) or {@code null}
     * sentinel (unresolvable → keep the bar). Entries are few (one per distinct
     * icon string this session) and parsing/registry lookups stay off the
     * render loop.
     */
    private static final Map<String, Object> ICON_CACHE = new HashMap<>();

    /**
     * One renderable cluster cell, merged from the ability-slot roster
     * ({@link ClientAbilitySlots}), live cooldowns ({@link ClientCooldownState})
     * and toggle state ({@link ClientActivePowers}).
     *
     * @param icon resolved {@link ItemStack} / texture {@link Identifier},
     *             or {@code null} for the legacy bar path.
     * @param cd   live cooldown, or {@code null} when idle.
     */
    record RenderSlot(int slot, Identifier powerId, Object icon,
                      ClientCooldownState.CooldownEntry cd,
                      boolean toggleable, boolean toggledOn, boolean countdown) {}

    /**
     * Merges roster + cooldowns + toggle state into the ordered list of cells
     * the cluster should currently draw. Also used by the HUD editor for its
     * live preview.
     */
    static List<RenderSlot> buildRenderSlots() {
        var cooldowns = ClientCooldownState.getCooldowns();
        var mode = NeoOriginsClientConfig.hudAbilityDisplay();
        boolean alwaysAll = NeoOriginsClientConfig.isAlwaysShowAbilityIcons();

        List<RenderSlot> out = new ArrayList<>();
        Set<Integer> covered = new HashSet<>();
        for (var e : ClientAbilitySlots.get()) {
            covered.add(e.slot());
            ClientCooldownState.CooldownEntry cd = cooldowns.get(e.slot());
            Object icon = e.icon().isEmpty() ? null : resolveIcon(e.icon());
            if (icon == null && cd != null && !cd.icon().isEmpty()) {
                icon = resolveIcon(cd.icon());
            }
            boolean show;
            if (cd != null) {
                show = true;                                   // recharging — always visible
            } else if (icon == null) {
                show = false;                                  // idle + no icon — nothing to draw
            } else if (e.toggleable()) {
                show = true;                                   // toggles shown in BOTH modes
            } else {
                show = mode == NeoOriginsClientConfig.HudAbilityDisplay.ALL_ACTIVE_ABILITIES
                    || alwaysAll || e.alwaysShow();
            }
            if (!show) continue;
            boolean toggledOn = !e.toggleable() || ClientActivePowers.isActive(e.powerId());
            out.add(new RenderSlot(e.slot(), e.powerId(), icon, cd,
                e.toggleable(), toggledOn, e.countdown() || (cd != null && cd.countdown())));
        }
        // Cooldowns with no roster entry (e.g. Route B compat powers, or a sync
        // race right after an origin change) keep the legacy behavior.
        for (var en : cooldowns.entrySet()) {
            if (covered.contains(en.getKey())) continue;
            ClientCooldownState.CooldownEntry cd = en.getValue();
            Object icon = cd.icon().isEmpty() ? null : resolveIcon(cd.icon());
            out.add(new RenderSlot(en.getKey(), null, icon, cd, false, true, cd.countdown()));
        }
        // Skill slots in order, class active (-1) last.
        out.sort(Comparator.comparingInt(s -> s.slot() < 0 ? Integer.MAX_VALUE : s.slot()));
        return out;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // The HUD editor draws its own live cluster while open.
        if (mc.screen instanceof ResourceHudEditorScreen) return;

        List<RenderSlot> slots = buildRenderSlots();
        if (slots.isEmpty()) return;

        GuiGraphicsExtractor g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        for (CooldownClusterLayout.Cell cell : CooldownClusterLayout.fromSaved(screenW, screenH, slots)) {
            renderCell(g, mc, cell);
        }
    }

    /**
     * Draws one positioned cell from the shared layout: the 2D pose is
     * translated to the cell anchor and scaled, then the bar / icon paths draw
     * in local (unscaled) coordinates so icon, sweep, countdown and labels all
     * scale together.
     */
    static void renderCell(GuiGraphicsExtractor g, Minecraft mc, CooldownClusterLayout.Cell cell) {
        RenderSlot rs = cell.slot();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cell.x(), cell.y());
        pose.scale(cell.scale(), cell.scale());

        if (rs.icon() == null) {
            // ── Original bar path — unchanged for icon-less powers. ──
            String label = legacyLabel(rs.slot());
            float progress = (float) rs.cd().remainingTicks() / rs.cd().totalTicks();
            int labelW = mc.font.width(label);
            g.text(mc.font, label, (BAR_W - labelW) / 2, -10, LABEL_COLOR, false);

            g.fill(0, 0, BAR_W, BAR_H, BAR_BG);
            int fillW = Math.round(BAR_W * progress);
            if (fillW > 0) {
                g.fill(0, 0, fillW, BAR_H, BAR_FILL);
            }
        } else {
            // ── Icon path: full-bright when idle, sweep while recharging,
            //    dimmed while a toggle is off. ──
            renderIconSlot(g, mc, rs, (BAR_W - ICON_SIZE) / 2, BAR_H - ICON_SIZE);
        }
        pose.popMatrix();
    }

    /**
     * Hover tooltips for the HUD cluster while any screen overlays it (e.g.
     * chat). Rendered from {@code ScreenEvent.Render.Post} — not the HUD pass —
     * for two reasons: the event hands us properly gui-scaled mouse
     * coordinates, and anything drawn during {@code RenderGuiEvent} is painted
     * over by the screen that renders after it (the tooltip used to vanish
     * under the screen layer). The HUD editor draws its own cluster + tooltip.
     */
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (event.getScreen() instanceof ResourceHudEditorScreen) return;

        List<RenderSlot> slots = buildRenderSlots();
        if (slots.isEmpty()) {
            debugHover("no-slots");
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int mouseX = event.getMouseX(), mouseY = event.getMouseY();

        for (CooldownClusterLayout.Cell cell : CooldownClusterLayout.fromSaved(screenW, screenH, slots)) {
            if (!cell.iconHit(mouseX, mouseY)) continue;
            RenderSlot rs = cell.slot();
            List<Component> tooltip = tooltipFor(rs.powerId());
            if (tooltip != null) {
                drawTooltip(event.getGuiGraphics(), mc, tooltip, mouseX, mouseY, screenW, screenH);
                debugHover("tooltip slot=" + rs.slot() + " power=" + rs.powerId());
            } else {
                debugHover("hit-but-no-tooltip slot=" + rs.slot()
                    + " (fallback cooldown slot with no power id)");
            }
            return;
        }
        debugHover("no-hit");
    }

    /** Last hover state logged by {@link #debugHover}; logs only on transitions. */
    private static String lastHoverState = null;

    /**
     * Hover-state-change logger for the {@code debug_hud} admin-config flag:
     * one line per state transition (handler fired / slot hit / tooltip drawn
     * or why not), never per-frame spam.
     */
    private static void debugHover(String state) {
        if (!com.cyberday1.neoorigins.config.AdminConfig.isDebugHud()) {
            lastHoverState = null;
            return;
        }
        if (state.equals(lastHoverState)) return;
        lastHoverState = state;
        NeoOrigins.LOGGER.info("[debug_hud] cluster hover: {}", state);
    }

    /**
     * Draws one icon cell: the icon, the toggle-off dim tint, the cooldown
     * sweep + optional countdown, and the bound-key label in the top-right
     * corner. Shared with the HUD editor's live preview.
     */
    static void renderIconSlot(GuiGraphicsExtractor g, Minecraft mc, RenderSlot rs, int iconX, int iconY) {
        if (rs.icon() instanceof ItemStack stack) {
            g.item(stack, iconX, iconY);
        } else if (rs.icon() instanceof Identifier tex) {
            g.blit(RenderPipelines.GUI_TEXTURED, tex, iconX, iconY,
                0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }

        // Layering note: unlike 1.21.1 (where renderItem wrote depth at z≈150
        // and later z=0 draws vanished behind it), the 26.1 GuiRenderState
        // auto-stacks each submitted element above any earlier element with
        // intersecting bounds — so this submission order (icon → tint → sweep
        // → text) IS the visual order, with text always the top layer.
        if (rs.toggleable() && !rs.toggledOn() && rs.cd() == null) {
            g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, TOGGLE_OFF_TINT);
        }

        if (rs.cd() != null) {
            float progress = (float) rs.cd().remainingTicks() / rs.cd().totalTicks();
            renderRadialSweep(g, iconX, iconY, progress);
        }

        if (rs.cd() != null && rs.countdown() && NeoOriginsClientConfig.isShowCooldownCountdown()) {
            String secs = String.valueOf((rs.cd().remainingTicks() + 19) / 20);
            int sw = mc.font.width(secs);
            g.text(mc.font, secs,
                iconX + (ICON_SIZE - sw) / 2, iconY + (ICON_SIZE - 8) / 2,
                countdownColor(), true);
        }

        String key = keyLabelFor(rs.slot());
        if (key == null) key = legacyLabel(rs.slot());
        int kw = mc.font.width(key);
        // Inside the icon's top-right corner (right edges flush), with the
        // 1px drop shadow for contrast so it reads on any icon.
        g.text(mc.font, key, iconX + ICON_SIZE - kw, iconY, KEY_LABEL_COLOR, true);
    }

    /**
     * Translucent white for the countdown number, alpha driven by the
     * {@code cooldown_countdown_opacity} client config (0–100%). Clamped to a
     * 5% floor: the vanilla font renderer silently drops glyphs whose alpha is
     * below ~4/255, so anything lower would read as "countdown missing".
     */
    private static int countdownColor() {
        int pct = Math.max(5, Math.min(100, NeoOriginsClientConfig.cooldownCountdownOpacity()));
        int alpha = Math.round(pct * 255 / 100.0f);
        return (alpha << 24) | COUNTDOWN_RGB;
    }

    /** Legacy bar label for a slot: S1–S6 for skill slots, C for the class active. */
    static String legacyLabel(int slot) {
        return AbilitySlotKeys.legacyLabel(slot);
    }

    /**
     * Short display name of the key actually bound to a slot's ability —
     * "R", "V", mouse buttons included. ≥3-char names are truncated to their
     * first two characters. Returns {@code null} when the mapping is unbound
     * (callers fall back to the legacy slot label). Delegates the slot→key
     * resolution to {@link AbilitySlotKeys} so HUD labels and the origin
     * screens' hotkey tags can never disagree.
     */
    static String keyLabelFor(int slot) {
        String s = AbilitySlotKeys.keyName(slot);
        if (s == null) return null;
        if (s.length() > 2) s = s.substring(0, 2);
        return s;
    }

    /**
     * Hover tooltip for an icon slot: power name (bold) + description from the
     * login-synced {@link ClientPowerCache}. On a cache miss (e.g. a sync race
     * right after an origin change) falls back to the raw power id so the slot
     * is still identifiable; {@code null} only when there's no power id at all
     * (a fallback cooldown slot with no roster entry).
     */
    static List<Component> tooltipFor(Identifier powerId) {
        if (powerId == null) return null;
        ClientPowerCache.Entry entry = ClientPowerCache.get(powerId);
        if (entry == null) {
            return List.of(Component.literal(powerId.toString())
                .withStyle(ChatFormatting.GRAY));
        }
        List<Component> lines = new ArrayList<>(2);
        lines.add(entry.name().copy().withStyle(ChatFormatting.BOLD));
        if (!entry.description().getString().isBlank()) {
            lines.add(entry.description().copy());
        }
        return lines;
    }

    /**
     * Hand-rolled tooltip box: the 26.1 GUI extractor has no vanilla
     * component-tooltip helper usable from a HUD overlay, so the box is drawn
     * manually (same approach as the creator screens). Shared with the HUD
     * editor's live preview.
     */
    static void drawTooltip(GuiGraphicsExtractor g, Minecraft mc, List<Component> lines,
                            int mx, int my, int screenW, int screenH) {
        if (lines.isEmpty()) return;
        int wMax = 0;
        for (Component c : lines) wMax = Math.max(wMax, mc.font.width(c));
        int bw = wMax + 8, bh = lines.size() * 10 + 4;
        int bx = Math.min(mx + 12, screenW - bw - 6);
        int by = Math.min(Math.max(my - 6, 4), screenH - bh - 6);
        g.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, TOOLTIP_BG);
        g.outline(bx - 3, by - 3, bw + 6, bh + 6, TOOLTIP_BORDER);
        int ly = by + 2;
        for (Component c : lines) {
            g.text(mc.font, c, bx + 4, ly, 0xFFFFFFFF, false);
            ly += 10;
        }
    }

    /**
     * Clock-style radial sweep over the {@code ICON_SIZE}² icon area: the dark
     * fill covers the not-yet-recharged arc, shrinking clockwise from
     * 12 o'clock as the cooldown elapses ({@code remainingFraction} 1 → 0).
     *
     * <p>Vanilla has no radial GUI primitive and the 26.1 GUI pipeline has no
     * stable custom-geometry hook, so the pie is rasterized on the GUI pixel
     * grid instead of a triangle fan: each pixel's clockwise angle from
     * 12 o'clock decides membership, and horizontal runs are merged so a cell
     * costs at most two {@code fill} quads per row.
     */
    private static void renderRadialSweep(GuiGraphicsExtractor g, int iconX, int iconY, float remainingFraction) {
        float elapsed = 1.0f - Math.max(0.0f, Math.min(1.0f, remainingFraction));
        if (elapsed >= 1.0f) return;             // fully recharged — no overlay
        final double TWO_PI = Math.PI * 2.0;
        double threshold = elapsed * TWO_PI;      // dark iff angle >= threshold
        float half = ICON_SIZE / 2.0f;

        for (int py = 0; py < ICON_SIZE; py++) {
            int runStart = -1;
            for (int px = 0; px <= ICON_SIZE; px++) {
                boolean dark = false;
                if (px < ICON_SIZE) {
                    double dx = (px + 0.5) - half;
                    double dy = (py + 0.5) - half;
                    // Clockwise angle from 12 o'clock in [0, 2π).
                    double ang = Math.atan2(dx, -dy);
                    if (ang < 0) ang += TWO_PI;
                    dark = ang >= threshold;
                }
                if (dark && runStart < 0) {
                    runStart = px;
                } else if (!dark && runStart >= 0) {
                    g.fill(iconX + runStart, iconY + py, iconX + px, iconY + py + 1, SWEEP_COLOR);
                    runStart = -1;
                }
            }
        }
    }

    /**
     * Resolve a {@code cooldown_icon} string to an {@link ItemStack} (item id
     * form) or a texture {@link Identifier} ({@code .png} form, resolved
     * under {@code assets/<ns>/textures/}). Returns {@code null} when the
     * string is unparseable or names an unknown item — the slot then keeps the
     * plain bar.
     */
    static Object resolveIcon(String icon) {
        return ICON_CACHE.computeIfAbsent(icon, key -> {
            if (key.endsWith(".png")) {
                Identifier rl = Identifier.tryParse(key);
                if (rl == null) return null;
                return Identifier.fromNamespaceAndPath(rl.getNamespace(), "textures/" + rl.getPath());
            }
            Identifier rl = Identifier.tryParse(key);
            if (rl == null) return null;
            Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.AIR);
            if (item == Items.AIR) return null;
            return new ItemStack(item);
        });
    }
}
