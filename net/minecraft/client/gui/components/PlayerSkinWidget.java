package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PlayerSkinWidget extends AbstractWidget {
    private static final float MODEL_OFFSET = 0.0625F;
    private static final float MODEL_HEIGHT = 2.125F;
    private static final float Z_OFFSET = 100.0F;
    private static final float ROTATION_SENSITIVITY = 2.5F;
    private static final float DEFAULT_ROTATION_X = -5.0F;
    private static final float DEFAULT_ROTATION_Y = 30.0F;
    private static final float ROTATION_X_LIMIT = 50.0F;
    private final PlayerSkinWidget.Model model;
    private final Supplier<PlayerSkin> skin;
    private float rotationX = -5.0F;
    private float rotationY = 30.0F;

    public PlayerSkinWidget(int p_300014_, int p_299867_, EntityModelSet p_299948_, Supplier<PlayerSkin> p_299853_) {
        super(0, 0, p_300014_, p_299867_, CommonComponents.EMPTY);
        this.model = PlayerSkinWidget.Model.bake(p_299948_);
        this.skin = p_299853_;
    }

    @Override
    protected void renderWidget(GuiGraphics p_299897_, int p_299826_, int p_300009_, float p_299921_) {
        p_299897_.pose().pushPose();
        p_299897_.pose().translate((float)this.getX() + (float)this.getWidth() / 2.0F, (float)(this.getY() + this.getHeight()), 100.0F);
        float f = (float)this.getHeight() / 2.125F;
        p_299897_.pose().scale(f, f, f);
        p_299897_.pose().translate(0.0F, -0.0625F, 0.0F);
        p_299897_.pose().rotateAround(Axis.XP.rotationDegrees(this.rotationX), 0.0F, -1.0625F, 0.0F);
        p_299897_.pose().mulPose(Axis.YP.rotationDegrees(this.rotationY));
        p_299897_.flush();
        Lighting.setupForEntityInInventory(Axis.XP.rotationDegrees(this.rotationX));
        this.model.render(p_299897_, this.skin.get());
        p_299897_.flush();
        Lighting.setupFor3DItems();
        p_299897_.pose().popPose();
    }

    @Override
    protected void onDrag(double p_299829_, double p_299876_, double p_300028_, double p_299872_) {
        this.rotationX = Mth.clamp(this.rotationX - (float)p_299872_ * 2.5F, -50.0F, 50.0F);
        this.rotationY += (float)p_300028_ * 2.5F;
    }

    @Override
    public void playDownSound(SoundManager p_299880_) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput p_299955_) {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent p_299934_) {
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    static record Model(PlayerModel<?> wideModel, PlayerModel<?> slimModel) {
        public static PlayerSkinWidget.Model bake(EntityModelSet p_300003_) {
            PlayerModel<?> playermodel = new PlayerModel(p_300003_.bakeLayer(ModelLayers.PLAYER), false);
            PlayerModel<?> playermodel1 = new PlayerModel(p_300003_.bakeLayer(ModelLayers.PLAYER_SLIM), true);
            playermodel.young = false;
            playermodel1.young = false;
            return new PlayerSkinWidget.Model(playermodel, playermodel1);
        }

        public void render(GuiGraphics p_300018_, PlayerSkin p_299991_) {
            p_300018_.pose().pushPose();
            p_300018_.pose().scale(1.0F, 1.0F, -1.0F);
            p_300018_.pose().translate(0.0F, -1.5F, 0.0F);
            PlayerModel<?> playermodel = p_299991_.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.wideModel;
            RenderType rendertype = playermodel.renderType(p_299991_.texture());
            playermodel.renderToBuffer(p_300018_.pose(), p_300018_.bufferSource().getBuffer(rendertype), 15728880, OverlayTexture.NO_OVERLAY);
            p_300018_.pose().popPose();
        }
    }
}
