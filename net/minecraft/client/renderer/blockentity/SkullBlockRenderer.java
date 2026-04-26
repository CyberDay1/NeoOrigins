package net.minecraft.client.renderer.blockentity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PiglinHeadModel;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.dragon.DragonHeadModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SkullBlockRenderer implements BlockEntityRenderer<SkullBlockEntity> {
    private final Map<SkullBlock.Type, SkullModelBase> modelByType;
    public static final Map<SkullBlock.Type, ResourceLocation> SKIN_BY_TYPE = Util.make(Maps.newHashMap(), p_349877_ -> {
        p_349877_.put(SkullBlock.Types.SKELETON, ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png"));
        p_349877_.put(SkullBlock.Types.WITHER_SKELETON, ResourceLocation.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png"));
        p_349877_.put(SkullBlock.Types.ZOMBIE, ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png"));
        p_349877_.put(SkullBlock.Types.CREEPER, ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper.png"));
        p_349877_.put(SkullBlock.Types.DRAGON, ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png"));
        p_349877_.put(SkullBlock.Types.PIGLIN, ResourceLocation.withDefaultNamespace("textures/entity/piglin/piglin.png"));
        p_349877_.put(SkullBlock.Types.PLAYER, DefaultPlayerSkin.getDefaultTexture());
    });

    public static Map<SkullBlock.Type, SkullModelBase> createSkullRenderers(EntityModelSet p_173662_) {
        Builder<SkullBlock.Type, SkullModelBase> builder = ImmutableMap.builder();
        builder.put(SkullBlock.Types.SKELETON, new SkullModel(p_173662_.bakeLayer(ModelLayers.SKELETON_SKULL)));
        builder.put(SkullBlock.Types.WITHER_SKELETON, new SkullModel(p_173662_.bakeLayer(ModelLayers.WITHER_SKELETON_SKULL)));
        builder.put(SkullBlock.Types.PLAYER, new SkullModel(p_173662_.bakeLayer(ModelLayers.PLAYER_HEAD)));
        builder.put(SkullBlock.Types.ZOMBIE, new SkullModel(p_173662_.bakeLayer(ModelLayers.ZOMBIE_HEAD)));
        builder.put(SkullBlock.Types.CREEPER, new SkullModel(p_173662_.bakeLayer(ModelLayers.CREEPER_HEAD)));
        builder.put(SkullBlock.Types.DRAGON, new DragonHeadModel(p_173662_.bakeLayer(ModelLayers.DRAGON_SKULL)));
        builder.put(SkullBlock.Types.PIGLIN, new PiglinHeadModel(p_173662_.bakeLayer(ModelLayers.PIGLIN_HEAD)));
        net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.client.event.EntityRenderersEvent.CreateSkullModels(builder, p_173662_));
        return builder.build();
    }

    public SkullBlockRenderer(BlockEntityRendererProvider.Context p_173660_) {
        this.modelByType = createSkullRenderers(p_173660_.getModelSet());
    }

    public void render(SkullBlockEntity p_112534_, float p_112535_, PoseStack p_112536_, MultiBufferSource p_112537_, int p_112538_, int p_112539_) {
        float f = p_112534_.getAnimation(p_112535_);
        BlockState blockstate = p_112534_.getBlockState();
        boolean flag = blockstate.getBlock() instanceof WallSkullBlock;
        Direction direction = flag ? blockstate.getValue(WallSkullBlock.FACING) : null;
        int i = flag ? RotationSegment.convertToSegment(direction.getOpposite()) : blockstate.getValue(SkullBlock.ROTATION);
        float f1 = RotationSegment.convertToDegrees(i);
        SkullBlock.Type skullblock$type = ((AbstractSkullBlock)blockstate.getBlock()).getType();
        SkullModelBase skullmodelbase = this.modelByType.get(skullblock$type);
        RenderType rendertype = getRenderType(skullblock$type, p_112534_.getOwnerProfile());
        renderSkull(direction, f1, f, p_112536_, p_112537_, p_112538_, skullmodelbase, rendertype);
    }

    public static void renderSkull(
        @Nullable Direction p_173664_,
        float p_173665_,
        float p_173666_,
        PoseStack p_173667_,
        MultiBufferSource p_173668_,
        int p_173669_,
        SkullModelBase p_173670_,
        RenderType p_173671_
    ) {
        p_173667_.pushPose();
        if (p_173664_ == null) {
            p_173667_.translate(0.5F, 0.0F, 0.5F);
        } else {
            float f = 0.25F;
            p_173667_.translate(0.5F - (float)p_173664_.getStepX() * 0.25F, 0.25F, 0.5F - (float)p_173664_.getStepZ() * 0.25F);
        }

        p_173667_.scale(-1.0F, -1.0F, 1.0F);
        VertexConsumer vertexconsumer = p_173668_.getBuffer(p_173671_);
        p_173670_.setupAnim(p_173666_, p_173665_, 0.0F);
        p_173670_.renderToBuffer(p_173667_, vertexconsumer, p_173669_, OverlayTexture.NO_OVERLAY);
        p_173667_.popPose();
    }

    public static RenderType getRenderType(SkullBlock.Type p_112524_, @Nullable ResolvableProfile p_332722_) {
        ResourceLocation resourcelocation = SKIN_BY_TYPE.get(p_112524_);
        if (p_112524_ == SkullBlock.Types.PLAYER && p_332722_ != null) {
            SkinManager skinmanager = Minecraft.getInstance().getSkinManager();
            return RenderType.entityTranslucent(skinmanager.getInsecureSkin(p_332722_.gameProfile()).texture());
        } else {
            return RenderType.entityCutoutNoCullZOffset(resourcelocation);
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SkullBlockEntity blockEntity) {
        SkullBlock.Type type = ((AbstractSkullBlock) blockEntity.getBlockState().getBlock()).getType();
        if (type == SkullBlock.Types.DRAGON) {
            net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
            return new net.minecraft.world.phys.AABB(pos.getX() - .75, pos.getY() - .35, pos.getZ() - .75, pos.getX() + 1.75, pos.getY() + 1.0, pos.getZ() + 1.75);
        }
        return BlockEntityRenderer.super.getRenderBoundingBox(blockEntity);
    }
}
