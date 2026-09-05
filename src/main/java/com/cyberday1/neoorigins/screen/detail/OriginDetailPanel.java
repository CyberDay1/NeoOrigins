package com.cyberday1.neoorigins.screen.detail;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginTierOverlay;
import com.cyberday1.neoorigins.client.theme.PanelRenderer;
import com.cyberday1.neoorigins.client.theme.UITheme;
import com.cyberday1.neoorigins.client.theme.UIThemeUtils;
import com.cyberday1.neoorigins.evolution.EssenceEvolutionManager;
import com.cyberday1.neoorigins.screen.OriginButton;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The origin detail view — icon, name, impact row, description, power list,
 * evolution path and its scroll rail — shared by every picker screen.
 *
 * <p>Deliberately not an {@code AbstractWidget}: it is never focused or
 * narrated, and hosts draw their own content inside its rect (the info
 * screen's browse-position line sits in the header gutter), which the widget
 * contract does not accommodate.
 *
 * <p>Construct from the host's {@code init()}, never at field-init:
 * {@code Screen.font} is only assigned once {@code Screen#init(Minecraft,int,int)}
 * runs, so a field initialiser would capture null.
 */
public final class OriginDetailPanel {

    private static final int DETAIL_PAD = 10;
    /** Icon block + name + impact row: everything above the scroll area. */
    public static final int HEADER_H = DETAIL_PAD + 32 + 6 + 9 + 4 + 5 + 10; // 76
    private static final int DOT_SIZE = 5;
    private static final int DOT_SPACING = 8;
    private static final int DOT_COUNT = 4;
    private static final int LINE_H = 10;
    /** Vertical gap between consecutive power entries. */
    private static final int POWER_GAP = 5;

    /** Live evolution progress, or {@link #NONE} for the static path only. */
    public interface EvolutionProgress {
        boolean enabled();
        int currentTier();
        int currentKills();
        int killsForTier(int tier);

        EvolutionProgress NONE = new EvolutionProgress() {
            @Override public boolean enabled()            { return false; }
            @Override public int currentTier()            { return -1; }
            @Override public int currentKills()           { return 0; }
            @Override public int killsForTier(int tier)   { return 0; }
        };
    }

    /** One added power in an evolution tier: display name + wrapped description. */
    private record EvoLine(String name, List<FormattedCharSequence> desc) {}
    /** One evolution tier: the powers it adds and the names it removes. */
    private record EvoTier(int tier, List<EvoLine> added, List<String> removed) {}

    private final Font font;

    private int x, y, w, h;
    private int textW;
    private int panelInset = 12;
    private boolean drawPanelBackground = true;

    private OriginDetailViewModel vm = OriginDetailViewModel.EMPTY;
    private List<FormattedCharSequence> descLines = List.of();
    private List<List<FormattedCharSequence>> wrappedPowerDescs = List.of();
    private List<EvoTier> evoTiers = List.of();
    private int contentH = 0;

    private EvolutionProgress progress = EvolutionProgress.NONE;

    private int scrollOffset = 0;
    private boolean draggingThumb = false;
    private double dragGrabY = 0;

    public OriginDetailPanel(Font font) {
        this.font = font;
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    /** Outer panel rect, 9-slice border included. Call from the host's init(). */
    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.textW = w - DETAIL_PAD * 2 - 6;
        rewrap();
    }

    /** Clearance from the panel edge for the scroll rail and scroll-area bottom. */
    public void setPanelInset(int inset) { this.panelInset = inset; }

    public void setDrawPanelBackground(boolean b) { this.drawPanelBackground = b; }

    // ── Content ───────────────────────────────────────────────────────────────

    public void setEvolutionProgress(EvolutionProgress p) {
        this.progress = p == null ? EvolutionProgress.NONE : p;
    }

    public void setOrigin(OriginDetailViewModel model) {
        this.vm = model == null ? OriginDetailViewModel.EMPTY : model;
        this.scrollOffset = 0;
        rewrap();
    }

    public void clear() { setOrigin(OriginDetailViewModel.EMPTY); }

    private void rewrap() {
        if (vm.origin() == null || textW <= 0) {
            descLines = List.of();
            wrappedPowerDescs = List.of();
            evoTiers = List.of();
            contentH = 0;
            return;
        }
        // Wrap with themed() BEFORE splitting — Font.split bakes the style
        // (including the font selector) into each FormattedCharSequence.
        descLines = font.split(UIThemeUtils.themed(vm.origin().description()), textW);

        int powerDescW = textW - 8; // indent for the bullet
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
        for (String desc : vm.powerDescs()) {
            wrapped.add(desc.isEmpty()
                ? List.of()
                : font.split(UIThemeUtils.themed(Component.literal(desc)), powerDescW));
        }
        wrappedPowerDescs = wrapped;

        evoTiers = computeEvoTiers(powerDescW);
        contentH = computeContentHeight();
    }

    /** Per-tier evolution display, synthetic multiple-power sub-ids collapsed
     *  back to their parent. Tiers ascend (Evolved → Ascended → Apex). */
    private List<EvoTier> computeEvoTiers(int descW) {
        Origin origin = vm.origin();
        if (origin == null || origin.tierPowers().isEmpty()) return List.of();
        var sorted = new ArrayList<>(origin.tierPowers());
        sorted.sort(java.util.Comparator.comparingInt(OriginTierOverlay::tier));
        List<EvoTier> out = new ArrayList<>();
        for (OriginTierOverlay overlay : sorted) {
            List<EvoLine> added = new ArrayList<>();
            for (var d : OriginDetailViewModel.resolveTierPowerDisplays(overlay.add())) {
                List<FormattedCharSequence> desc = d.description().isEmpty()
                    ? List.of()
                    : font.split(UIThemeUtils.themed(Component.literal(d.description())), descW);
                added.add(new EvoLine(d.name(), desc));
            }
            List<String> removed = new ArrayList<>();
            for (var d : OriginDetailViewModel.resolveTierPowerDisplays(overlay.remove())) {
                removed.add(d.name());
            }
            out.add(new EvoTier(overlay.tier(), added, removed));
        }
        return out;
    }

    private static String tierName(int tier) {
        if (tier >= 0 && tier < EssenceEvolutionManager.TIER_NAMES.length) {
            String n = EssenceEvolutionManager.TIER_NAMES[tier];
            if (!n.isEmpty()) return n;
        }
        return "Tier " + tier;
    }

    private int computeContentHeight() {
        int total = 8 + descLines.size() * LINE_H + 8; // separator + desc + gap
        Origin origin = vm.origin();
        if (origin != null && origin.spawnLocation().isPresent()
            && !origin.spawnLocation().get().formatSummary().isEmpty()) {
            total += LINE_H;
        }
        total += evolutionSectionHeight();
        List<String> pNames = vm.powerNames();
        if (!pNames.isEmpty()) {
            total += 9 + 4; // "Powers" header
            for (int i = 0; i < pNames.size(); i++) {
                total += 11; // power name line
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    total += wrappedPowerDescs.get(i).size() * LINE_H;
                }
                total += POWER_GAP;
            }
        }
        return total + 6; // bottom padding
    }

    /** Pixel height of the evolution section; 0 when the origin has no tiers. */
    private int evolutionSectionHeight() {
        if (evoTiers.isEmpty()) return 0;
        boolean evoOn = progress.enabled();
        int total = 8;                      // gap before section
        if (evoOn) total += LINE_H;         // "Next Evolution: X / Y" summary
        total += 9 + 4;                     // "Evolution Path" header
        for (EvoTier tier : evoTiers) {
            total += 11;                    // tier subheader
            if (evoOn) total += LINE_H;     // per-tier progress annotation
            for (EvoLine line : tier.added()) {
                total += LINE_H;                        // "+ Name"
                total += line.desc().size() * LINE_H;   // wrapped description
            }
            total += tier.removed().size() * LINE_H;    // "- Name"
        }
        return total;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public int headerHeight()  { return HEADER_H; }
    public int contentHeight() { return contentH; }
    public int scrollOffset()  { return scrollOffset; }
    public void resetScroll()  { scrollOffset = 0; }

    private int scrollTop()    { return y + HEADER_H; }
    /** Pulled in so the rail and content clear the parchment burnt-edge curl. */
    private int scrollBottom() { return y + h - panelInset; }
    private int scrollAreaH()  { return scrollBottom() - scrollTop(); }
    private int maxScroll()    { return Math.max(0, contentH - scrollAreaH()); }

    // ── Rendering ─────────────────────────────────────────────────────────────

    public void render(GuiGraphicsExtractor g) {
        renderHeader(g);
        renderBody(g);
    }

    /** Panel background, icon, name and impact row. */
    public void renderHeader(GuiGraphicsExtractor g) {
        UITheme theme = UITheme.current();
        if (drawPanelBackground) {
            PanelRenderer.drawPanel(g, theme, x - 1, y - 1, w + 2, h + 2);
        }
        Origin origin = vm.origin();
        if (origin == null) {
            var hint = UIThemeUtils.themed(Component.translatable("gui.neoorigins.hint.select"));
            g.text(font, hint, x + w / 2 - font.width(hint) / 2,
                y + h / 2 - 4, theme.mutedColor(), false);
            return;
        }

        int cx = x + w / 2;
        int hy = y + DETAIL_PAD;
        g.outline(cx - 16, hy, 32, 32, theme.borderColor());
        OriginButton.renderIcon(g, origin.icon(), cx - 8, hy + 8);
        hy += 32 + 6;
        var nameC = UIThemeUtils.themedBold(origin.name());
        g.text(font, nameC, cx - font.width(nameC) / 2, hy, theme.nameColor(), false);
        hy += 9 + 4;
        drawImpactRow(g, cx, hy, origin.impact());
    }

    /** Scissored scroll region plus the scroll rail. */
    public void renderBody(GuiGraphicsExtractor g) {
        Origin origin = vm.origin();
        if (origin == null) return;
        UITheme theme = UITheme.current();

        int scrollTop = scrollTop();
        int scrollBottom = scrollBottom();
        int scrollAreaH = scrollAreaH();
        int maxScroll = maxScroll();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        g.enableScissor(x + 1, scrollTop, x + w - 5, scrollBottom);
        int sy = scrollTop - scrollOffset;
        g.fill(x + DETAIL_PAD, sy + 3, x + w - DETAIL_PAD - 6, sy + 4, theme.borderColor());
        sy += 8;
        for (FormattedCharSequence line : descLines) {
            g.text(font, line, x + DETAIL_PAD, sy, theme.descriptionColor(), false);
            sy += LINE_H;
        }
        if (origin.spawnLocation().isPresent()) {
            String spawnSummary = origin.spawnLocation().get().formatSummary();
            if (!spawnSummary.isEmpty()) {
                g.text(font, UIThemeUtils.themed(Component.literal(spawnSummary)),
                    x + DETAIL_PAD, sy, theme.accentColor(), false);
                sy += LINE_H;
            }
        }
        sy += 8;
        sy = renderPowers(g, theme, sy);
        renderEvolution(g, theme, sy);
        g.disableScissor();

        if (maxScroll > 0) {
            int barX = x + w - panelInset;
            int thumbH = thumbHeight(scrollAreaH, maxScroll);
            int thumbY = scrollTop + (int) ((long) scrollOffset * (scrollAreaH - thumbH) / maxScroll);
            g.fill(barX, scrollTop, barX + 1, scrollBottom, theme.borderColor());
            g.fill(barX, thumbY, barX + 1, thumbY + thumbH, theme.accentColor());
        }
    }

    private int renderPowers(GuiGraphicsExtractor g, UITheme theme, int sy) {
        List<String> pNames = vm.powerNames();
        if (pNames.isEmpty()) return sy;
        g.text(font, UIThemeUtils.themedBold(Component.translatable("gui.neoorigins.detail.powers_header")),
            x + DETAIL_PAD, sy, theme.headerColor(), false);
        sy += 9 + 4;
        List<String> pTags = vm.powerKeyTags();
        for (int i = 0; i < pNames.size(); i++) {
            g.fill(x + DETAIL_PAD, sy + 3, x + DETAIL_PAD + 3, sy + 6, theme.accentColor());
            var pNameC = UIThemeUtils.themedBold(Component.literal(pNames.get(i)));
            g.text(font, pNameC, x + DETAIL_PAD + 8, sy, theme.powerNameColor(), false);
            // Hotkey tag (e.g. "[R]") — same slot logic as the HUD cluster.
            if (i < pTags.size() && !pTags.get(i).isEmpty()) {
                g.text(font, UIThemeUtils.themed(Component.literal(pTags.get(i))),
                    x + DETAIL_PAD + 8 + font.width(pNameC) + 5, sy, theme.accentColor(), false);
            }
            sy += 11;
            if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                for (FormattedCharSequence dLine : wrappedPowerDescs.get(i)) {
                    g.text(font, dLine, x + DETAIL_PAD + 8, sy, theme.powerDescriptionColor(), false);
                    sy += LINE_H;
                }
            }
            sy += POWER_GAP;
        }
        return sy;
    }

    /**
     * Evolution path, below the powers list so the base kit reads first. Live
     * kill progress is only drawn when the host supplied a progress source that
     * reports enabled — otherwise the static path renders alone.
     */
    private void renderEvolution(GuiGraphicsExtractor g, UITheme theme, int sy) {
        if (evoTiers.isEmpty()) return;
        sy += 8;
        boolean evoOn = progress.enabled();
        int curTier = evoOn ? progress.currentTier() : -1;
        int curKills = evoOn ? progress.currentKills() : 0;
        if (evoOn) {
            Component summary;
            if (curTier >= 3) {
                summary = Component.translatable("gui.neoorigins.info.evolution_apex");
            } else {
                int need = progress.killsForTier(curTier + 1);
                summary = Component.translatable("gui.neoorigins.info.evolution_progress",
                    String.valueOf(curKills), String.valueOf(need));
            }
            g.text(font, UIThemeUtils.themed(summary), x + DETAIL_PAD, sy, theme.accentColor(), false);
            sy += LINE_H;
        }
        g.text(font, UIThemeUtils.themedBold(Component.translatable("gui.neoorigins.info.evolution_path")),
            x + DETAIL_PAD, sy, theme.headerColor(), false);
        sy += 9 + 4;
        for (EvoTier tier : evoTiers) {
            g.text(font, UIThemeUtils.themed(Component.literal(tierName(tier.tier()))),
                x + DETAIL_PAD, sy, theme.powerNameColor(), false);
            sy += 11;
            if (evoOn) {
                // "Achieved" for tiers already reached, otherwise progress toward it.
                Component annotation = tier.tier() <= curTier
                    ? Component.translatable("gui.neoorigins.info.evolution_tier_achieved")
                    : Component.translatable("gui.neoorigins.info.evolution_tier_progress",
                        String.valueOf(curKills), String.valueOf(progress.killsForTier(tier.tier())));
                g.text(font, UIThemeUtils.themed(annotation),
                    x + DETAIL_PAD + 8, sy, theme.mutedColor(), false);
                sy += LINE_H;
            }
            for (EvoLine line : tier.added()) {
                g.text(font, UIThemeUtils.themed(Component.literal("+ " + line.name())),
                    x + DETAIL_PAD + 8, sy, theme.powerNameColor(), false);
                sy += LINE_H;
                for (FormattedCharSequence dl : line.desc()) {
                    g.text(font, dl, x + DETAIL_PAD + 16, sy, theme.powerDescriptionColor(), false);
                    sy += LINE_H;
                }
            }
            for (String rname : tier.removed()) {
                g.text(font, UIThemeUtils.themed(Component.literal("- " + rname)),
                    x + DETAIL_PAD + 8, sy, theme.mutedColor(), false);
                sy += LINE_H;
            }
        }
    }

    private void drawImpactRow(GuiGraphicsExtractor g, int cx, int rowY, Impact impact) {
        UITheme theme = UITheme.current();
        int totalW = drawImpactDots(g, cx, rowY, impact);
        Component label = Component.translatable("origins.gui.impact.impact").append(": ")
            .append(switch (impact) {
                case NONE   -> Component.translatable("origins.gui.impact.none");
                case LOW    -> Component.translatable("origins.gui.impact.low");
                case MEDIUM -> Component.translatable("origins.gui.impact.medium");
                case HIGH   -> Component.translatable("origins.gui.impact.high");
            });
        g.text(font, UIThemeUtils.themed(label), cx + totalW / 2 + 6, rowY - 1, theme.mutedColor(), false);
    }

    /**
     * The impact dots alone, centred on {@code cx}, with no "Impact: High"
     * label — the form a compact card wants. Returns the row's pixel width.
     */
    public static int drawImpactDots(GuiGraphicsExtractor g, int cx, int rowY, Impact impact) {
        UITheme theme = UITheme.current();
        int totalW = (DOT_COUNT - 1) * DOT_SPACING + DOT_SIZE;
        int x0 = cx - totalW / 2;
        for (int i = 0; i < DOT_COUNT; i++) {
            g.fill(x0 + i * DOT_SPACING, rowY, x0 + i * DOT_SPACING + DOT_SIZE, rowY + DOT_SIZE,
                i < impact.getDotCount() ? theme.accentColor() : theme.borderColor());
        }
        return totalW;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private static int thumbHeight(int scrollAreaH, int maxScroll) {
        return Math.max(10, scrollAreaH * scrollAreaH / (scrollAreaH + maxScroll));
    }

    private boolean inPanel(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public boolean mouseScrolled(double mx, double my, double sy) {
        if (!inPanel(mx, my)) return false;
        scrollOffset = Mth.clamp(scrollOffset + (sy > 0 ? -14 : 14), 0, maxScroll());
        return true;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return false;
        int scrollTop = scrollTop();
        int scrollBottom = scrollBottom();
        int scrollAreaH = scrollAreaH();
        int barX = x + w - panelInset;
        int thumbH = thumbHeight(scrollAreaH, maxScroll);
        int thumbY = scrollTop + (int) ((long) scrollOffset * (scrollAreaH - thumbH) / maxScroll);
        // Widen the clickable rail by a few px so the 1px bar is grabbable.
        if (mx < barX - 3 || mx > barX + 4 || my < scrollTop || my > scrollBottom) return false;
        draggingThumb = true;
        if (my >= thumbY && my <= thumbY + thumbH) {
            dragGrabY = my - thumbY;
        } else {
            // Track click: centre the thumb on the cursor, then drag from there.
            dragGrabY = thumbH / 2.0;
            setScrollFromThumbTop(my - dragGrabY, scrollTop, scrollAreaH, thumbH, maxScroll);
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        if (!draggingThumb || button != 0) return false;
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int scrollAreaH = scrollAreaH();
            setScrollFromThumbTop(my - dragGrabY, scrollTop(), scrollAreaH,
                thumbHeight(scrollAreaH, maxScroll), maxScroll);
        }
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0 || !draggingThumb) return false;
        draggingThumb = false;
        return true;
    }

    /** Inverse of the render-side thumbY formula, clamped to the valid range. */
    private void setScrollFromThumbTop(double thumbTop, int scrollTop, int scrollAreaH,
                                       int thumbH, int maxScroll) {
        int travel = scrollAreaH - thumbH;
        if (travel <= 0) { scrollOffset = 0; return; }
        double frac = Mth.clamp((thumbTop - scrollTop) / travel, 0.0, 1.0);
        scrollOffset = (int) Math.round(frac * maxScroll);
    }
}
