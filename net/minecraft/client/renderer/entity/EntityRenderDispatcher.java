package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class EntityRenderDispatcher implements ResourceManagerReloadListener {
    private static final RenderType SHADOW_RENDER_TYPE = RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));
    private static final float MAX_SHADOW_RADIUS = 32.0F;
    private static final float SHADOW_POWER_FALLOFF_Y = 0.5F;
    private Map<EntityType<?>, EntityRenderer<?>> renderers = ImmutableMap.of();
    private Map<PlayerSkin.Model, EntityRenderer<? extends Player>> playerRenderers = Map.of();
    public final TextureManager textureManager;
    private Level level;
    public Camera camera;
    private Quaternionf cameraOrientation;
    public Entity crosshairPickEntity;
    private final ItemRenderer itemRenderer;
    private final BlockRenderDispatcher blockRenderDispatcher;
    private final ItemInHandRenderer itemInHandRenderer;
    private final Font font;
    public final Options options;
    private final EntityModelSet entityModels;
    private boolean shouldRenderShadow = true;
    private boolean renderHitBoxes;

    public <E extends Entity> int getPackedLightCoords(E p_114395_, float p_114396_) {
        return this.getRenderer(p_114395_).getPackedLightCoords(p_114395_, p_114396_);
    }

    public EntityRenderDispatcher(
        Minecraft p_234579_,
        TextureManager p_234580_,
        ItemRenderer p_234581_,
        BlockRenderDispatcher p_234582_,
        Font p_234583_,
        Options p_234584_,
        EntityModelSet p_234585_
    ) {
        this.textureManager = p_234580_;
        this.itemRenderer = p_234581_;
        this.itemInHandRenderer = new ItemInHandRenderer(p_234579_, this, p_234581_);
        this.blockRenderDispatcher = p_234582_;
        this.font = p_234583_;
        this.options = p_234584_;
        this.entityModels = p_234585_;
    }

    public <T extends Entity> EntityRenderer<? super T> getRenderer(T p_114383_) {
        if (p_114383_ instanceof AbstractClientPlayer abstractclientplayer) {
            PlayerSkin.Model playerskin$model = abstractclientplayer.getSkin().model();
            EntityRenderer<? extends Player> entityrenderer = this.playerRenderers.get(playerskin$model);
            return (EntityRenderer<? super T>)(entityrenderer != null ? entityrenderer : this.playerRenderers.get(PlayerSkin.Model.WIDE));
        } else {
            return (EntityRenderer<? super T>)this.renderers.get(p_114383_.getType());
        }
    }

    public void prepare(Level p_114409_, Camera p_114410_, Entity p_114411_) {
        this.level = p_114409_;
        this.camera = p_114410_;
        this.cameraOrientation = p_114410_.rotation();
        this.crosshairPickEntity = p_114411_;
    }

    public void overrideCameraOrientation(Quaternionf p_254264_) {
        this.cameraOrientation = p_254264_;
    }

    public void setRenderShadow(boolean p_114469_) {
        this.shouldRenderShadow = p_114469_;
    }

    public void setRenderHitBoxes(boolean p_114474_) {
        this.renderHitBoxes = p_114474_;
    }

    public boolean shouldRenderHitBoxes() {
        return this.renderHitBoxes;
    }

    public <E extends Entity> boolean shouldRender(E p_114398_, Frustum p_114399_, double p_114400_, double p_114401_, double p_114402_) {
        EntityRenderer<? super E> entityrenderer = this.getRenderer(p_114398_);
        return entityrenderer.shouldRender(p_114398_, p_114399_, p_114400_, p_114401_, p_114402_);
    }

    public <E extends Entity> void render(
        E p_114385_,
        double p_114386_,
        double p_114387_,
        double p_114388_,
        float p_114389_,
        float p_114390_,
        PoseStack p_114391_,
        MultiBufferSource p_114392_,
        int p_114393_
    ) {
        EntityRenderer<? super E> entityrenderer = this.getRenderer(p_114385_);

        try {
            Vec3 vec3 = entityrenderer.getRenderOffset(p_114385_, p_114390_);
            double d2 = p_114386_ + vec3.x();
            double d3 = p_114387_ + vec3.y();
            double d0 = p_114388_ + vec3.z();
            p_114391_.pushPose();
            p_114391_.translate(d2, d3, d0);
            entityrenderer.render(p_114385_, p_114389_, p_114390_, p_114391_, p_114392_, p_114393_);
            if (p_114385_.displayFireAnimation()) {
                this.renderFlame(p_114391_, p_114392_, p_114385_, Mth.rotationAroundAxis(Mth.Y_AXIS, this.cameraOrientation, new Quaternionf()));
            }

            p_114391_.translate(-vec3.x(), -vec3.y(), -vec3.z());
            if (this.options.entityShadows().get() && this.shouldRenderShadow && !p_114385_.isInvisible()) {
                float f = entityrenderer.getShadowRadius(p_114385_);
                if (f > 0.0F) {
                    double d1 = this.distanceToSqr(p_114385_.getX(), p_114385_.getY(), p_114385_.getZ());
                    float f1 = (float)((1.0 - d1 / 256.0) * (double)entityrenderer.shadowStrength);
                    if (f1 > 0.0F) {
                        renderShadow(p_114391_, p_114392_, p_114385_, f1, p_114390_, this.level, Math.min(f, 32.0F));
                    }
                }
            }

            if (this.renderHitBoxes && !p_114385_.isInvisible() && !Minecraft.getInstance().showOnlyReducedInfo()) {
                renderHitbox(p_114391_, p_114392_.getBuffer(RenderType.lines()), p_114385_, p_114390_, 1.0F, 1.0F, 1.0F);
            }

            p_114391_.popPose();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering entity in world");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being rendered");
            p_114385_.fillCrashReportCategory(crashreportcategory);
            CrashReportCategory crashreportcategory1 = crashreport.addCategory("Renderer details");
            crashreportcategory1.setDetail("Assigned renderer", entityrenderer);
            crashreportcategory1.setDetail("Location", CrashReportCategory.formatLocation(this.level, p_114386_, p_114387_, p_114388_));
            crashreportcategory1.setDetail("Rotation", p_114389_);
            crashreportcategory1.setDetail("Delta", p_114390_);
            throw new ReportedException(crashreport);
        }
    }

    private static void renderServerSideHitbox(PoseStack p_353069_, Entity p_353061_, MultiBufferSource p_353074_) {
        Entity entity = getServerSideEntity(p_353061_);
        if (entity == null) {
            DebugRenderer.renderFloatingText(p_353069_, p_353074_, "Missing", p_353061_.getX(), p_353061_.getBoundingBox().maxY + 1.5, p_353061_.getZ(), -65536);
        } else {
            p_353069_.pushPose();
            p_353069_.translate(entity.getX() - p_353061_.getX(), entity.getY() - p_353061_.getY(), entity.getZ() - p_353061_.getZ());
            renderHitbox(p_353069_, p_353074_.getBuffer(RenderType.lines()), entity, 1.0F, 0.0F, 1.0F, 0.0F);
            renderVector(p_353069_, p_353074_.getBuffer(RenderType.lines()), new Vector3f(), entity.getDeltaMovement(), -256);
            p_353069_.popPose();
        }
    }

    @Nullable
    private static Entity getServerSideEntity(Entity p_353043_) {
        IntegratedServer integratedserver = Minecraft.getInstance().getSingleplayerServer();
        if (integratedserver != null) {
            ServerLevel serverlevel = integratedserver.getLevel(p_353043_.level().dimension());
            if (serverlevel != null) {
                return serverlevel.getEntity(p_353043_.getId());
            }
        }

        return null;
    }

    private static void renderHitbox(
        PoseStack p_114442_, VertexConsumer p_114443_, Entity p_114444_, float p_114445_, float p_353064_, float p_353059_, float p_353042_
    ) {
        AABB aabb = p_114444_.getBoundingBox().move(-p_114444_.getX(), -p_114444_.getY(), -p_114444_.getZ());
        LevelRenderer.renderLineBox(p_114442_, p_114443_, aabb, p_353064_, p_353059_, p_353042_, 1.0F);
        if (p_114444_.isMultipartEntity()) {
            double d0 = -Mth.lerp((double)p_114445_, p_114444_.xOld, p_114444_.getX());
            double d1 = -Mth.lerp((double)p_114445_, p_114444_.yOld, p_114444_.getY());
            double d2 = -Mth.lerp((double)p_114445_, p_114444_.zOld, p_114444_.getZ());

            for (net.neoforged.neoforge.entity.PartEntity<?> enderdragonpart : p_114444_.getParts()) {
                p_114442_.pushPose();
                double d3 = d0 + Mth.lerp((double)p_114445_, enderdragonpart.xOld, enderdragonpart.getX());
                double d4 = d1 + Mth.lerp((double)p_114445_, enderdragonpart.yOld, enderdragonpart.getY());
                double d5 = d2 + Mth.lerp((double)p_114445_, enderdragonpart.zOld, enderdragonpart.getZ());
                p_114442_.translate(d3, d4, d5);
                LevelRenderer.renderLineBox(
                    p_114442_,
                    p_114443_,
                    enderdragonpart.getBoundingBox().move(-enderdragonpart.getX(), -enderdragonpart.getY(), -enderdragonpart.getZ()),
                    0.25F,
                    1.0F,
                    0.0F,
                    1.0F
                );
                p_114442_.popPose();
            }
        }

        if (p_114444_ instanceof LivingEntity) {
            float f1 = 0.01F;
            LevelRenderer.renderLineBox(
                p_114442_,
                p_114443_,
                aabb.minX,
                (double)(p_114444_.getEyeHeight() - 0.01F),
                aabb.minZ,
                aabb.maxX,
                (double)(p_114444_.getEyeHeight() + 0.01F),
                aabb.maxZ,
                1.0F,
                0.0F,
                0.0F,
                1.0F
            );
        }

        Entity entity = p_114444_.getVehicle();
        if (entity != null) {
            float f = Math.min(entity.getBbWidth(), p_114444_.getBbWidth()) / 2.0F;
            float f2 = 0.0625F;
            Vec3 vec3 = entity.getPassengerRidingPosition(p_114444_).subtract(p_114444_.position());
            LevelRenderer.renderLineBox(
                p_114442_,
                p_114443_,
                vec3.x - (double)f,
                vec3.y,
                vec3.z - (double)f,
                vec3.x + (double)f,
                vec3.y + 0.0625,
                vec3.z + (double)f,
                1.0F,
                1.0F,
                0.0F,
                1.0F
            );
        }

        renderVector(p_114442_, p_114443_, new Vector3f(0.0F, p_114444_.getEyeHeight(), 0.0F), p_114444_.getViewVector(p_114445_).scale(2.0), -16776961);
    }

    private static void renderVector(PoseStack p_353057_, VertexConsumer p_353027_, Vector3f p_353068_, Vec3 p_353070_, int p_353032_) {
        PoseStack.Pose posestack$pose = p_353057_.last();
        p_353027_.addVertex(posestack$pose, p_353068_)
            .setColor(p_353032_)
            .setNormal(posestack$pose, (float)p_353070_.x, (float)p_353070_.y, (float)p_353070_.z);
        p_353027_.addVertex(
                posestack$pose,
                (float)((double)p_353068_.x() + p_353070_.x),
                (float)((double)p_353068_.y() + p_353070_.y),
                (float)((double)p_353068_.z() + p_353070_.z)
            )
            .setColor(p_353032_)
            .setNormal(posestack$pose, (float)p_353070_.x, (float)p_353070_.y, (float)p_353070_.z);
    }

    private void renderFlame(PoseStack p_114454_, MultiBufferSource p_114455_, Entity p_114456_, Quaternionf p_304964_) {
        TextureAtlasSprite textureatlassprite = ModelBakery.FIRE_0.sprite();
        TextureAtlasSprite textureatlassprite1 = ModelBakery.FIRE_1.sprite();
        p_114454_.pushPose();
        float f = p_114456_.getBbWidth() * 1.4F;
        p_114454_.scale(f, f, f);
        float f1 = 0.5F;
        float f2 = 0.0F;
        float f3 = p_114456_.getBbHeight() / f;
        float f4 = 0.0F;
        p_114454_.mulPose(p_304964_);
        p_114454_.translate(0.0F, 0.0F, 0.3F - (float)((int)f3) * 0.02F);
        float f5 = 0.0F;
        int i = 0;
        VertexConsumer vertexconsumer = p_114455_.getBuffer(Sheets.cutoutBlockSheet());

        for (PoseStack.Pose posestack$pose = p_114454_.last(); f3 > 0.0F; i++) {
            TextureAtlasSprite textureatlassprite2 = i % 2 == 0 ? textureatlassprite : textureatlassprite1;
            float f6 = textureatlassprite2.getU0();
            float f7 = textureatlassprite2.getV0();
            float f8 = textureatlassprite2.getU1();
            float f9 = textureatlassprite2.getV1();
            if (i / 2 % 2 == 0) {
                float f10 = f8;
                f8 = f6;
                f6 = f10;
            }

            fireVertex(posestack$pose, vertexconsumer, -f1 - 0.0F, 0.0F - f4, f5, f8, f9);
            fireVertex(posestack$pose, vertexconsumer, f1 - 0.0F, 0.0F - f4, f5, f6, f9);
            fireVertex(posestack$pose, vertexconsumer, f1 - 0.0F, 1.4F - f4, f5, f6, f7);
            fireVertex(posestack$pose, vertexconsumer, -f1 - 0.0F, 1.4F - f4, f5, f8, f7);
            f3 -= 0.45F;
            f4 -= 0.45F;
            f1 *= 0.9F;
            f5 -= 0.03F;
        }

        p_114454_.popPose();
    }

    private static void fireVertex(
        PoseStack.Pose p_114415_, VertexConsumer p_114416_, float p_114417_, float p_114418_, float p_114419_, float p_114420_, float p_114421_
    ) {
        p_114416_.addVertex(p_114415_, p_114417_, p_114418_, p_114419_)
            .setColor(-1)
            .setUv(p_114420_, p_114421_)
            .setUv1(0, 10)
            .setLight(240)
            .setNormal(p_114415_, 0.0F, 1.0F, 0.0F);
    }

    private static void renderShadow(
        PoseStack p_114458_, MultiBufferSource p_114459_, Entity p_114460_, float p_114461_, float p_114462_, LevelReader p_114463_, float p_114464_
    ) {
        double d0 = Mth.lerp((double)p_114462_, p_114460_.xOld, p_114460_.getX());
        double d1 = Mth.lerp((double)p_114462_, p_114460_.yOld, p_114460_.getY());
        double d2 = Mth.lerp((double)p_114462_, p_114460_.zOld, p_114460_.getZ());
        float f = Math.min(p_114461_ / 0.5F, p_114464_);
        int i = Mth.floor(d0 - (double)p_114464_);
        int j = Mth.floor(d0 + (double)p_114464_);
        int k = Mth.floor(d1 - (double)f);
        int l = Mth.floor(d1);
        int i1 = Mth.floor(d2 - (double)p_114464_);
        int j1 = Mth.floor(d2 + (double)p_114464_);
        PoseStack.Pose posestack$pose = p_114458_.last();
        VertexConsumer vertexconsumer = p_114459_.getBuffer(SHADOW_RENDER_TYPE);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int k1 = i1; k1 <= j1; k1++) {
            for (int l1 = i; l1 <= j; l1++) {
                blockpos$mutableblockpos.set(l1, 0, k1);
                ChunkAccess chunkaccess = p_114463_.getChunk(blockpos$mutableblockpos);

                for (int i2 = k; i2 <= l; i2++) {
                    blockpos$mutableblockpos.setY(i2);
                    float f1 = p_114461_ - (float)(d1 - (double)blockpos$mutableblockpos.getY()) * 0.5F;
                    renderBlockShadow(posestack$pose, vertexconsumer, chunkaccess, p_114463_, blockpos$mutableblockpos, d0, d1, d2, p_114464_, f1);
                }
            }
        }
    }

    private static void renderBlockShadow(
        PoseStack.Pose p_277956_,
        VertexConsumer p_277533_,
        ChunkAccess p_277501_,
        LevelReader p_277622_,
        BlockPos p_277911_,
        double p_277682_,
        double p_278099_,
        double p_277806_,
        float p_277844_,
        float p_277496_
    ) {
        BlockPos blockpos = p_277911_.below();
        BlockState blockstate = p_277501_.getBlockState(blockpos);
        if (blockstate.getRenderShape() != RenderShape.INVISIBLE && p_277622_.getMaxLocalRawBrightness(p_277911_) > 3) {
            if (blockstate.isCollisionShapeFullBlock(p_277501_, blockpos)) {
                VoxelShape voxelshape = blockstate.getShape(p_277501_, blockpos);
                if (!voxelshape.isEmpty()) {
                    float f = LightTexture.getBrightness(p_277622_.dimensionType(), p_277622_.getMaxLocalRawBrightness(p_277911_));
                    float f1 = p_277496_ * 0.5F * f;
                    if (f1 >= 0.0F) {
                        if (f1 > 1.0F) {
                            f1 = 1.0F;
                        }

                        int i = FastColor.ARGB32.color(Mth.floor(f1 * 255.0F), 255, 255, 255);
                        AABB aabb = voxelshape.bounds();
                        double d0 = (double)p_277911_.getX() + aabb.minX;
                        double d1 = (double)p_277911_.getX() + aabb.maxX;
                        double d2 = (double)p_277911_.getY() + aabb.minY;
                        double d3 = (double)p_277911_.getZ() + aabb.minZ;
                        double d4 = (double)p_277911_.getZ() + aabb.maxZ;
                        float f2 = (float)(d0 - p_277682_);
                        float f3 = (float)(d1 - p_277682_);
                        float f4 = (float)(d2 - p_278099_);
                        float f5 = (float)(d3 - p_277806_);
                        float f6 = (float)(d4 - p_277806_);
                        float f7 = -f2 / 2.0F / p_277844_ + 0.5F;
                        float f8 = -f3 / 2.0F / p_277844_ + 0.5F;
                        float f9 = -f5 / 2.0F / p_277844_ + 0.5F;
                        float f10 = -f6 / 2.0F / p_277844_ + 0.5F;
                        shadowVertex(p_277956_, p_277533_, i, f2, f4, f5, f7, f9);
                        shadowVertex(p_277956_, p_277533_, i, f2, f4, f6, f7, f10);
                        shadowVertex(p_277956_, p_277533_, i, f3, f4, f6, f8, f10);
                        shadowVertex(p_277956_, p_277533_, i, f3, f4, f5, f8, f9);
                    }
                }
            }
        }
    }

    private static void shadowVertex(
        PoseStack.Pose p_114423_, VertexConsumer p_114424_, int p_350992_, float p_114425_, float p_114426_, float p_114427_, float p_114428_, float p_114429_
    ) {
        Vector3f vector3f = p_114423_.pose().transformPosition(p_114425_, p_114426_, p_114427_, new Vector3f());
        p_114424_.addVertex(vector3f.x(), vector3f.y(), vector3f.z(), p_350992_, p_114428_, p_114429_, OverlayTexture.NO_OVERLAY, 15728880, 0.0F, 1.0F, 0.0F);
    }

    public void setLevel(@Nullable Level p_114407_) {
        this.level = p_114407_;
        if (p_114407_ == null) {
            this.camera = null;
        }
    }

    public double distanceToSqr(Entity p_114472_) {
        return this.camera.getPosition().distanceToSqr(p_114472_.position());
    }

    public double distanceToSqr(double p_114379_, double p_114380_, double p_114381_) {
        return this.camera.getPosition().distanceToSqr(p_114379_, p_114380_, p_114381_);
    }

    public Quaternionf cameraOrientation() {
        return this.cameraOrientation;
    }

    public ItemInHandRenderer getItemInHandRenderer() {
        return this.itemInHandRenderer;
    }

    public Map<PlayerSkin.Model, EntityRenderer<? extends Player>> getSkinMap() {
        return java.util.Collections.unmodifiableMap(playerRenderers);
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_174004_) {
        EntityRendererProvider.Context entityrendererprovider$context = new EntityRendererProvider.Context(
            this, this.itemRenderer, this.blockRenderDispatcher, this.itemInHandRenderer, p_174004_, this.entityModels, this.font
        );
        this.renderers = EntityRenderers.createEntityRenderers(entityrendererprovider$context);
        this.playerRenderers = EntityRenderers.createPlayerRenderers(entityrendererprovider$context);
        net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers(renderers, playerRenderers, entityrendererprovider$context));
    }
}
