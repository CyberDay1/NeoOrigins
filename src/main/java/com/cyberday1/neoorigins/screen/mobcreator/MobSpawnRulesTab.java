package com.cyberday1.neoorigins.screen.mobcreator;

import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Spawn-rules tab — edits {@code SpawnRules} on the draft. Phase 4a ships the
 * tab shell + scroll viewport only; Phase 4b adds the simple field widgets
 * (weight / time / spawn reasons / mutex / replace / Y / light ranges) and
 * Phase 4c the Location sub-editor.
 */
public final class MobSpawnRulesTab implements MobCreatorTab {

    @SuppressWarnings("unused")
    private MobOriginCreatorScreen parent;

    @Override public Component title() {
        return Component.translatable("gui.neoorigins.mob_creator.tab.spawn_rules");
    }
    @Override public Component help() {
        return Component.literal("When (and how often) this origin rolls onto a freshly-spawned mob.");
    }

    @Override
    public void init(MobOriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        // Phase 4b/4c populate widgets here.
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        Font font = parent.font();
        CreatorStyle.sectionHeader(g, font, "Spawn rules", x + 8, y, w - 16);
        g.text(font, "Editor lands in 4b/4c. Author the spawn_rules block in JSON for now,",
            x + 8, y + 18, CreatorStyle.TEXT_DIM, false);
        g.text(font, "or leave it empty — the codec defaults to SpawnRules.NEVER.",
            x + 8, y + 30, CreatorStyle.TEXT_DIM, false);
    }
}
