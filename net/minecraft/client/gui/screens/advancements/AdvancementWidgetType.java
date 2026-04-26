package net.minecraft.client.gui.screens.advancements;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum AdvancementWidgetType {
    OBTAINED(
        ResourceLocation.withDefaultNamespace("advancements/box_obtained"),
        ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained"),
        ResourceLocation.withDefaultNamespace("advancements/challenge_frame_obtained"),
        ResourceLocation.withDefaultNamespace("advancements/goal_frame_obtained")
    ),
    UNOBTAINED(
        ResourceLocation.withDefaultNamespace("advancements/box_unobtained"),
        ResourceLocation.withDefaultNamespace("advancements/task_frame_unobtained"),
        ResourceLocation.withDefaultNamespace("advancements/challenge_frame_unobtained"),
        ResourceLocation.withDefaultNamespace("advancements/goal_frame_unobtained")
    );

    private final ResourceLocation boxSprite;
    private final ResourceLocation taskFrameSprite;
    private final ResourceLocation challengeFrameSprite;
    private final ResourceLocation goalFrameSprite;

    private AdvancementWidgetType(ResourceLocation p_295820_, ResourceLocation p_294158_, ResourceLocation p_294676_, ResourceLocation p_296261_) {
        this.boxSprite = p_295820_;
        this.taskFrameSprite = p_294158_;
        this.challengeFrameSprite = p_294676_;
        this.goalFrameSprite = p_296261_;
    }

    public ResourceLocation boxSprite() {
        return this.boxSprite;
    }

    public ResourceLocation frameSprite(AdvancementType p_312712_) {
        return switch (p_312712_) {
            case TASK -> this.taskFrameSprite;
            case CHALLENGE -> this.challengeFrameSprite;
            case GOAL -> this.goalFrameSprite;
        };
    }
}
