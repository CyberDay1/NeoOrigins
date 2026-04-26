package net.minecraft.client.gui.components;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CommonButtons {
    public static SpriteIconButton language(int p_296187_, Button.OnPress p_296343_, boolean p_295488_) {
        return SpriteIconButton.builder(Component.translatable("options.language"), p_296343_, p_295488_)
            .width(p_296187_)
            .sprite(ResourceLocation.withDefaultNamespace("icon/language"), 15, 15)
            .build();
    }

    public static SpriteIconButton accessibility(int p_296469_, Button.OnPress p_294950_, boolean p_295903_) {
        Component component = p_295903_
            ? Component.translatable("options.accessibility")
            : Component.translatable("accessibility.onboarding.accessibility.button");
        return SpriteIconButton.builder(component, p_294950_, p_295903_)
            .width(p_296469_)
            .sprite(ResourceLocation.withDefaultNamespace("icon/accessibility"), 15, 15)
            .build();
    }
}
