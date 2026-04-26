package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.model.AxolotlModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AxolotlRenderer extends MobRenderer<Axolotl, AxolotlModel<Axolotl>> {
    private static final Map<Axolotl.Variant, ResourceLocation> TEXTURE_BY_TYPE = Util.make(
        Maps.newHashMap(),
        p_349898_ -> {
            for (Axolotl.Variant axolotl$variant : Axolotl.Variant.values()) {
                p_349898_.put(
                    axolotl$variant,
                    ResourceLocation.withDefaultNamespace(String.format(Locale.ROOT, "textures/entity/axolotl/axolotl_%s.png", axolotl$variant.getName()))
                );
            }
        }
    );

    public AxolotlRenderer(EntityRendererProvider.Context p_173921_) {
        super(p_173921_, new AxolotlModel<>(p_173921_.bakeLayer(ModelLayers.AXOLOTL)), 0.5F);
    }

    public ResourceLocation getTextureLocation(Axolotl p_173925_) {
        return TEXTURE_BY_TYPE.get(p_173925_.getVariant());
    }
}
