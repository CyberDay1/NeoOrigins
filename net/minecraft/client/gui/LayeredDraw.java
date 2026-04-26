package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.DeltaTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LayeredDraw {
    public static final float Z_SEPARATION = 200.0F;
    private final List<LayeredDraw.Layer> layers = new ArrayList<>();

    public LayeredDraw add(LayeredDraw.Layer p_316797_) {
        this.layers.add(p_316797_);
        return this;
    }

    public LayeredDraw add(LayeredDraw p_316830_, BooleanSupplier p_316449_) {
        return this.add((p_348091_, p_348092_) -> {
            if (p_316449_.getAsBoolean()) {
                p_316830_.renderInner(p_348091_, p_348092_);
            }
        });
    }

    public void render(GuiGraphics p_316283_, DeltaTracker p_348508_) {
        p_316283_.pose().pushPose();
        this.renderInner(p_316283_, p_348508_);
        p_316283_.pose().popPose();
    }

    private void renderInner(GuiGraphics p_316483_, DeltaTracker p_348478_) {
        for (LayeredDraw.Layer layereddraw$layer : this.layers) {
            layereddraw$layer.render(p_316483_, p_348478_);
            p_316483_.pose().translate(0.0F, 0.0F, 200.0F);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public interface Layer {
        void render(GuiGraphics p_316811_, DeltaTracker p_348559_);
    }
}
