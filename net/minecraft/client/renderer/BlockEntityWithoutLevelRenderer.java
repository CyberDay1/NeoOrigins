package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.TridentModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockEntityWithoutLevelRenderer implements ResourceManagerReloadListener {
    private static final ShulkerBoxBlockEntity[] SHULKER_BOXES = Arrays.stream(DyeColor.values())
        .sorted(Comparator.comparingInt(DyeColor::getId))
        .map(p_172557_ -> new ShulkerBoxBlockEntity(p_172557_, BlockPos.ZERO, Blocks.SHULKER_BOX.defaultBlockState()))
        .toArray(ShulkerBoxBlockEntity[]::new);
    private static final ShulkerBoxBlockEntity DEFAULT_SHULKER_BOX = new ShulkerBoxBlockEntity(BlockPos.ZERO, Blocks.SHULKER_BOX.defaultBlockState());
    private final ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
    private final ChestBlockEntity trappedChest = new TrappedChestBlockEntity(BlockPos.ZERO, Blocks.TRAPPED_CHEST.defaultBlockState());
    private final EnderChestBlockEntity enderChest = new EnderChestBlockEntity(BlockPos.ZERO, Blocks.ENDER_CHEST.defaultBlockState());
    private final BannerBlockEntity banner = new BannerBlockEntity(BlockPos.ZERO, Blocks.WHITE_BANNER.defaultBlockState());
    private final BedBlockEntity bed = new BedBlockEntity(BlockPos.ZERO, Blocks.RED_BED.defaultBlockState());
    private final ConduitBlockEntity conduit = new ConduitBlockEntity(BlockPos.ZERO, Blocks.CONDUIT.defaultBlockState());
    private final DecoratedPotBlockEntity decoratedPot = new DecoratedPotBlockEntity(BlockPos.ZERO, Blocks.DECORATED_POT.defaultBlockState());
    private ShieldModel shieldModel;
    private TridentModel tridentModel;
    private Map<SkullBlock.Type, SkullModelBase> skullModels;
    private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    private final EntityModelSet entityModelSet;

    public BlockEntityWithoutLevelRenderer(BlockEntityRenderDispatcher p_172550_, EntityModelSet p_172551_) {
        this.blockEntityRenderDispatcher = p_172550_;
        this.entityModelSet = p_172551_;
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_172555_) {
        this.shieldModel = new ShieldModel(this.entityModelSet.bakeLayer(ModelLayers.SHIELD));
        this.tridentModel = new TridentModel(this.entityModelSet.bakeLayer(ModelLayers.TRIDENT));
        this.skullModels = SkullBlockRenderer.createSkullRenderers(this.entityModelSet);
    }

    public void renderByItem(ItemStack p_108830_, ItemDisplayContext p_270899_, PoseStack p_108832_, MultiBufferSource p_108833_, int p_108834_, int p_108835_) {
        Item item = p_108830_.getItem();
        if (item instanceof BlockItem) {
            Block block = ((BlockItem)item).getBlock();
            if (block instanceof AbstractSkullBlock abstractskullblock) {
                ResolvableProfile resolvableprofile = p_108830_.get(DataComponents.PROFILE);
                if (resolvableprofile != null && !resolvableprofile.isResolved()) {
                    p_108830_.remove(DataComponents.PROFILE);
                    resolvableprofile.resolve().thenAcceptAsync(p_329787_ -> p_108830_.set(DataComponents.PROFILE, p_329787_), Minecraft.getInstance());
                    resolvableprofile = null;
                }

                SkullModelBase skullmodelbase = this.skullModels.get(abstractskullblock.getType());
                RenderType rendertype = SkullBlockRenderer.getRenderType(abstractskullblock.getType(), resolvableprofile);
                SkullBlockRenderer.renderSkull(null, 180.0F, 0.0F, p_108832_, p_108833_, p_108834_, skullmodelbase, rendertype);
            } else {
                BlockState blockstate = block.defaultBlockState();
                BlockEntity blockentity;
                if (block instanceof AbstractBannerBlock) {
                    this.banner.fromItem(p_108830_, ((AbstractBannerBlock)block).getColor());
                    blockentity = this.banner;
                } else if (block instanceof BedBlock) {
                    this.bed.setColor(((BedBlock)block).getColor());
                    blockentity = this.bed;
                } else if (blockstate.is(Blocks.CONDUIT)) {
                    blockentity = this.conduit;
                } else if (blockstate.is(Blocks.CHEST)) {
                    blockentity = this.chest;
                } else if (blockstate.is(Blocks.ENDER_CHEST)) {
                    blockentity = this.enderChest;
                } else if (blockstate.is(Blocks.TRAPPED_CHEST)) {
                    blockentity = this.trappedChest;
                } else if (blockstate.is(Blocks.DECORATED_POT)) {
                    this.decoratedPot.setFromItem(p_108830_);
                    blockentity = this.decoratedPot;
                } else {
                    if (!(block instanceof ShulkerBoxBlock)) {
                        return;
                    }

                    DyeColor dyecolor1 = ShulkerBoxBlock.getColorFromItem(item);
                    if (dyecolor1 == null) {
                        blockentity = DEFAULT_SHULKER_BOX;
                    } else {
                        blockentity = SHULKER_BOXES[dyecolor1.getId()];
                    }
                }

                this.blockEntityRenderDispatcher.renderItem(blockentity, p_108832_, p_108833_, p_108834_, p_108835_);
            }
        } else {
            if (p_108830_.is(Items.SHIELD)) {
                BannerPatternLayers bannerpatternlayers = p_108830_.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
                DyeColor dyecolor = p_108830_.get(DataComponents.BASE_COLOR);
                boolean flag = !bannerpatternlayers.layers().isEmpty() || dyecolor != null;
                p_108832_.pushPose();
                p_108832_.scale(1.0F, -1.0F, -1.0F);
                Material material = flag ? ModelBakery.SHIELD_BASE : ModelBakery.NO_PATTERN_SHIELD;
                VertexConsumer vertexconsumer = material.sprite()
                    .wrap(ItemRenderer.getFoilBufferDirect(p_108833_, this.shieldModel.renderType(material.atlasLocation()), true, p_108830_.hasFoil()));
                this.shieldModel.handle().render(p_108832_, vertexconsumer, p_108834_, p_108835_);
                if (flag) {
                    BannerRenderer.renderPatterns(
                        p_108832_,
                        p_108833_,
                        p_108834_,
                        p_108835_,
                        this.shieldModel.plate(),
                        material,
                        false,
                        Objects.requireNonNullElse(dyecolor, DyeColor.WHITE),
                        bannerpatternlayers,
                        p_108830_.hasFoil()
                    );
                } else {
                    this.shieldModel.plate().render(p_108832_, vertexconsumer, p_108834_, p_108835_);
                }

                p_108832_.popPose();
            } else if (p_108830_.is(Items.TRIDENT)) {
                p_108832_.pushPose();
                p_108832_.scale(1.0F, -1.0F, -1.0F);
                VertexConsumer vertexconsumer1 = ItemRenderer.getFoilBufferDirect(
                    p_108833_, this.tridentModel.renderType(TridentModel.TEXTURE), false, p_108830_.hasFoil()
                );
                this.tridentModel.renderToBuffer(p_108832_, vertexconsumer1, p_108834_, p_108835_);
                p_108832_.popPose();
            }
        }
    }
}
