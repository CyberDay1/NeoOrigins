package net.minecraft.client.gui.font.providers;

import com.mojang.blaze3d.font.SpaceProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@net.neoforged.fml.common.asm.enumextension.NamedEnum
public enum GlyphProviderType implements StringRepresentable, net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
    BITMAP("bitmap", BitmapProvider.Definition.CODEC),
    TTF("ttf", TrueTypeGlyphProviderDefinition.CODEC),
    SPACE("space", SpaceProvider.Definition.CODEC),
    UNIHEX("unihex", UnihexProvider.Definition.CODEC),
    REFERENCE("reference", ProviderReferenceDefinition.CODEC);

    public static final Codec<GlyphProviderType> CODEC = StringRepresentable.fromEnum(GlyphProviderType::values);
    private final String name;
    private final MapCodec<? extends GlyphProviderDefinition> codec;

    private GlyphProviderType(String p_286573_, MapCodec<? extends GlyphProviderDefinition> p_286248_) {
        this.name = p_286573_;
        this.codec = p_286248_;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public MapCodec<? extends GlyphProviderDefinition> mapCodec() {
        return this.codec;
    }

    public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
        return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(GlyphProviderType.class);
    }
}
