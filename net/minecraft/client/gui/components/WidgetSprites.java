package net.minecraft.client.gui.components;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record WidgetSprites(ResourceLocation enabled, ResourceLocation disabled, ResourceLocation enabledFocused, ResourceLocation disabledFocused) {
    public WidgetSprites(ResourceLocation p_295225_, ResourceLocation p_294772_) {
        this(p_295225_, p_295225_, p_294772_, p_294772_);
    }

    public WidgetSprites(ResourceLocation p_296152_, ResourceLocation p_296020_, ResourceLocation p_296073_) {
        this(p_296152_, p_296020_, p_296073_, p_296020_);
    }

    public ResourceLocation get(boolean p_296269_, boolean p_295773_) {
        if (p_296269_) {
            return p_295773_ ? this.enabledFocused : this.enabled;
        } else {
            return p_295773_ ? this.disabledFocused : this.disabled;
        }
    }
}
