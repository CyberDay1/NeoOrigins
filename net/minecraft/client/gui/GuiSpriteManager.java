package net.minecraft.client.gui;

import java.util.Set;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.TextureAtlasHolder;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GuiSpriteManager extends TextureAtlasHolder {
    private static final Set<MetadataSectionSerializer<?>> METADATA_SECTIONS = Set.of(AnimationMetadataSection.SERIALIZER, GuiMetadataSection.TYPE);

    public GuiSpriteManager(TextureManager p_294434_) {
        super(p_294434_, ResourceLocation.withDefaultNamespace("textures/atlas/gui.png"), ResourceLocation.withDefaultNamespace("gui"), METADATA_SECTIONS);
    }

    @Override
    public TextureAtlasSprite getSprite(ResourceLocation p_296464_) {
        return super.getSprite(p_296464_);
    }

    public GuiSpriteScaling getSpriteScaling(TextureAtlasSprite p_294850_) {
        return this.getMetadata(p_294850_).scaling();
    }

    private GuiMetadataSection getMetadata(TextureAtlasSprite p_294333_) {
        return p_294333_.contents().metadata().getSection(GuiMetadataSection.TYPE).orElse(GuiMetadataSection.DEFAULT);
    }
}
