package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class BannerBlockEntity extends BlockEntity implements Nameable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_PATTERNS = 6;
    private static final String TAG_PATTERNS = "patterns";
    @Nullable
    private Component name;
    private DyeColor baseColor;
    private BannerPatternLayers patterns = BannerPatternLayers.EMPTY;

    public BannerBlockEntity(BlockPos p_155035_, BlockState p_155036_) {
        super(BlockEntityType.BANNER, p_155035_, p_155036_);
        this.baseColor = ((AbstractBannerBlock)p_155036_.getBlock()).getColor();
    }

    public BannerBlockEntity(BlockPos p_155038_, BlockState p_155039_, DyeColor p_155040_) {
        this(p_155038_, p_155039_);
        this.baseColor = p_155040_;
    }

    public void fromItem(ItemStack p_58490_, DyeColor p_58491_) {
        this.baseColor = p_58491_;
        this.applyComponentsFromItemStack(p_58490_);
    }

    @Override
    public Component getName() {
        return (Component)(this.name != null ? this.name : Component.translatable("block.minecraft.banner"));
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    @Override
    protected void saveAdditional(CompoundTag p_187456_, HolderLookup.Provider p_324143_) {
        super.saveAdditional(p_187456_, p_324143_);
        if (!this.patterns.equals(BannerPatternLayers.EMPTY)) {
            p_187456_.put("patterns", BannerPatternLayers.CODEC.encodeStart(p_324143_.createSerializationContext(NbtOps.INSTANCE), this.patterns).getOrThrow());
        }

        if (this.name != null) {
            p_187456_.putString("CustomName", Component.Serializer.toJson(this.name, p_324143_));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag p_338645_, HolderLookup.Provider p_338247_) {
        super.loadAdditional(p_338645_, p_338247_);
        if (p_338645_.contains("CustomName", 8)) {
            this.name = parseCustomNameSafe(p_338645_.getString("CustomName"), p_338247_);
        }

        if (p_338645_.contains("patterns")) {
            BannerPatternLayers.CODEC
                .parse(p_338247_.createSerializationContext(NbtOps.INSTANCE), p_338645_.get("patterns"))
                .resultOrPartial(p_331289_ -> LOGGER.error("Failed to parse banner patterns: '{}'", p_331289_))
                .ifPresent(p_332632_ -> this.patterns = p_332632_);
        }
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p_324478_) {
        return this.saveWithoutMetadata(p_324478_);
    }

    public BannerPatternLayers getPatterns() {
        return this.patterns;
    }

    public ItemStack getItem() {
        ItemStack itemstack = new ItemStack(BannerBlock.byColor(this.baseColor));
        itemstack.applyComponents(this.collectComponents());
        return itemstack;
    }

    public DyeColor getBaseColor() {
        return this.baseColor;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput p_338244_) {
        super.applyImplicitComponents(p_338244_);
        this.patterns = p_338244_.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        this.name = p_338244_.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder p_338762_) {
        super.collectImplicitComponents(p_338762_);
        p_338762_.set(DataComponents.BANNER_PATTERNS, this.patterns);
        p_338762_.set(DataComponents.CUSTOM_NAME, this.name);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag p_331690_) {
        p_331690_.remove("patterns");
        p_331690_.remove("CustomName");
    }
}
