package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public abstract class BlockEntity extends net.neoforged.neoforge.attachment.AttachmentHolder implements net.neoforged.neoforge.common.extensions.IBlockEntityExtension {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Deprecated // Neo: always use getType()
    private final BlockEntityType<?> type;
    @Nullable
    protected Level level;
    protected final BlockPos worldPosition;
    protected boolean remove;
    private BlockState blockState;
    private DataComponentMap components = DataComponentMap.EMPTY;
    @Nullable
    private CompoundTag customPersistentData;

    public BlockEntity(BlockEntityType<?> p_155228_, BlockPos p_155229_, BlockState p_155230_) {
        this.type = p_155228_;
        this.worldPosition = p_155229_.immutable();
        this.validateBlockState(p_155230_);
        this.blockState = p_155230_;
    }

    private void validateBlockState(BlockState p_353132_) {
        if (!this.isValidBlockState(p_353132_)) {
            throw new IllegalStateException("Invalid block entity " + this.getNameForReporting() + " state at " + this.worldPosition + ", got " + p_353132_);
        }
    }

    public boolean isValidBlockState(BlockState p_353131_) {
        return this.getType().isValid(p_353131_); // Neo: use getter so correct type is checked for modded subclasses
    }

    public static BlockPos getPosFromTag(CompoundTag p_187473_) {
        return new BlockPos(p_187473_.getInt("x"), p_187473_.getInt("y"), p_187473_.getInt("z"));
    }

    @Nullable
    public Level getLevel() {
        return this.level;
    }

    public void setLevel(Level p_155231_) {
        this.level = p_155231_;
    }

    public boolean hasLevel() {
        return this.level != null;
    }

    protected void loadAdditional(CompoundTag p_338466_, HolderLookup.Provider p_338445_) {
        if (p_338466_.contains("NeoForgeData", net.minecraft.nbt.Tag.TAG_COMPOUND)) this.customPersistentData = p_338466_.getCompound("NeoForgeData");
        if (p_338466_.contains(ATTACHMENTS_NBT_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) deserializeAttachments(p_338445_, p_338466_.getCompound(ATTACHMENTS_NBT_KEY));
    }

    public final void loadWithComponents(CompoundTag p_338356_, HolderLookup.Provider p_338558_) {
        this.loadAdditional(p_338356_, p_338558_);
        BlockEntity.ComponentHelper.COMPONENTS_CODEC
            .parse(p_338558_.createSerializationContext(NbtOps.INSTANCE), p_338356_)
            .resultOrPartial(p_337987_ -> LOGGER.warn("Failed to load components: {}", p_337987_))
            .ifPresent(p_337995_ -> this.components = p_337995_);
    }

    public final void loadCustomOnly(CompoundTag p_338387_, HolderLookup.Provider p_338713_) {
        this.loadAdditional(p_338387_, p_338713_);
    }

    protected void saveAdditional(CompoundTag p_187471_, HolderLookup.Provider p_323635_) {
        if (this.customPersistentData != null) p_187471_.put("NeoForgeData", this.customPersistentData.copy());
        var attachmentsTag = serializeAttachments(p_323635_);
        if (attachmentsTag != null) p_187471_.put(ATTACHMENTS_NBT_KEY, attachmentsTag);
    }

    public final CompoundTag saveWithFullMetadata(HolderLookup.Provider p_323767_) {
        CompoundTag compoundtag = this.saveWithoutMetadata(p_323767_);
        this.saveMetadata(compoundtag);
        return compoundtag;
    }

    public final CompoundTag saveWithId(HolderLookup.Provider p_324357_) {
        CompoundTag compoundtag = this.saveWithoutMetadata(p_324357_);
        this.saveId(compoundtag);
        return compoundtag;
    }

    public final CompoundTag saveWithoutMetadata(HolderLookup.Provider p_324030_) {
        CompoundTag compoundtag = new CompoundTag();
        this.saveAdditional(compoundtag, p_324030_);
        BlockEntity.ComponentHelper.COMPONENTS_CODEC
            .encodeStart(p_324030_.createSerializationContext(NbtOps.INSTANCE), this.components)
            .resultOrPartial(p_337988_ -> LOGGER.warn("Failed to save components: {}", p_337988_))
            .ifPresent(p_337994_ -> compoundtag.merge((CompoundTag)p_337994_));
        return compoundtag;
    }

    public final CompoundTag saveCustomOnly(HolderLookup.Provider p_338656_) {
        CompoundTag compoundtag = new CompoundTag();
        this.saveAdditional(compoundtag, p_338656_);
        return compoundtag;
    }

    public final CompoundTag saveCustomAndMetadata(HolderLookup.Provider p_339688_) {
        CompoundTag compoundtag = this.saveCustomOnly(p_339688_);
        this.saveMetadata(compoundtag);
        return compoundtag;
    }

    private void saveId(CompoundTag p_187475_) {
        ResourceLocation resourcelocation = BlockEntityType.getKey(this.getType());
        if (resourcelocation == null) {
            throw new RuntimeException(this.getClass() + " is missing a mapping! This is a bug!");
        } else {
            p_187475_.putString("id", resourcelocation.toString());
        }
    }

    public static void addEntityType(CompoundTag p_187469_, BlockEntityType<?> p_187470_) {
        p_187469_.putString("id", BlockEntityType.getKey(p_187470_).toString());
    }

    public void saveToItem(ItemStack p_187477_, HolderLookup.Provider p_323484_) {
        CompoundTag compoundtag = this.saveCustomOnly(p_323484_);
        this.removeComponentsFromTag(compoundtag);
        BlockItem.setBlockEntityData(p_187477_, this.getType(), compoundtag);
        p_187477_.applyComponents(this.collectComponents());
    }

    private void saveMetadata(CompoundTag p_187479_) {
        this.saveId(p_187479_);
        p_187479_.putInt("x", this.worldPosition.getX());
        p_187479_.putInt("y", this.worldPosition.getY());
        p_187479_.putInt("z", this.worldPosition.getZ());
    }

    @Nullable
    public static BlockEntity loadStatic(BlockPos p_155242_, BlockState p_155243_, CompoundTag p_155244_, HolderLookup.Provider p_323542_) {
        String s = p_155244_.getString("id");
        ResourceLocation resourcelocation = ResourceLocation.tryParse(s);
        if (resourcelocation == null) {
            LOGGER.error("Block entity has invalid type: {}", s);
            return null;
        } else {
            return BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(resourcelocation).map(p_155240_ -> {
                try {
                    return p_155240_.create(p_155242_, p_155243_);
                } catch (Throwable throwable) {
                    LOGGER.error("Failed to create block entity {}", s, throwable);
                    return null;
                }
            }).map(p_337992_ -> {
                try {
                    p_337992_.loadWithComponents(p_155244_, p_323542_);
                    return (BlockEntity)p_337992_;
                } catch (Throwable throwable) {
                    LOGGER.error("Failed to load data for block entity {}", s, throwable);
                    return null;
                }
            }).orElseGet(() -> {
                LOGGER.warn("Skipping BlockEntity with id {}", s);
                return null;
            });
        }
    }

    public void setChanged() {
        if (this.level != null) {
            setChanged(this.level, this.worldPosition, this.blockState);
        }
    }

    protected static void setChanged(Level p_155233_, BlockPos p_155234_, BlockState p_155235_) {
        p_155233_.blockEntityChanged(p_155234_);
        if (!p_155235_.isAir()) {
            p_155233_.updateNeighbourForOutputSignal(p_155234_, p_155235_.getBlock());
        }
    }

    public BlockPos getBlockPos() {
        return this.worldPosition;
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return null;
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider p_323910_) {
        return new CompoundTag();
    }

    public boolean isRemoved() {
        return this.remove;
    }

    public void setRemoved() {
        this.remove = true;
        this.invalidateCapabilities();
        requestModelDataUpdate();
    }

    public void clearRemoved() {
        this.remove = false;
        // Neo: invalidate capabilities on block entity placement
        invalidateCapabilities();
    }

    public boolean triggerEvent(int p_58889_, int p_58890_) {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory p_58887_) {
        p_58887_.setDetail("Name", this::getNameForReporting);
        if (this.level != null) {
            CrashReportCategory.populateBlockDetails(p_58887_, this.level, this.worldPosition, this.getBlockState());
            CrashReportCategory.populateBlockDetails(p_58887_, this.level, this.worldPosition, this.level.getBlockState(this.worldPosition));
        }
    }

    private String getNameForReporting() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this.getType()) + " // " + this.getClass().getCanonicalName();
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    public BlockEntityType<?> getType() {
        return this.type;
    }

    @Override
    public CompoundTag getPersistentData() {
        if (this.customPersistentData == null)
            this.customPersistentData = new CompoundTag();
        return this.customPersistentData;
    }

    @Override
    @Nullable
    public final <T> T setData(net.neoforged.neoforge.attachment.AttachmentType<T> type, T data) {
        setChanged();
        return super.setData(type, data);
    }

    @Override
    @Nullable
    public final <T> T removeData(net.neoforged.neoforge.attachment.AttachmentType<T> type) {
        setChanged();
        return super.removeData(type);
    }

    @Override
    public final void syncData(net.neoforged.neoforge.attachment.AttachmentType<?> type) {
        net.neoforged.neoforge.attachment.AttachmentSync.syncBlockEntityUpdate(this, type);
    }

    @Deprecated
    public void setBlockState(BlockState p_155251_) {
        this.validateBlockState(p_155251_);
        this.blockState = p_155251_;
    }

    protected void applyImplicitComponents(BlockEntity.DataComponentInput p_338718_) {
    }

    public final void applyComponentsFromItemStack(ItemStack p_338428_) {
        this.applyComponents(p_338428_.getPrototype(), p_338428_.getComponentsPatch());
    }

    public final void applyComponents(DataComponentMap p_330364_, DataComponentPatch p_338381_) {
        final Set<DataComponentType<?>> set = new HashSet<>();
        set.add(DataComponents.BLOCK_ENTITY_DATA);
        final DataComponentMap datacomponentmap = PatchedDataComponentMap.fromPatch(p_330364_, p_338381_);
        this.applyImplicitComponents(new BlockEntity.DataComponentInput() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<T> p_338266_) {
                set.add(p_338266_);
                return datacomponentmap.get(p_338266_);
            }

            @Override
            public <T> T getOrDefault(DataComponentType<? extends T> p_338358_, T p_338352_) {
                set.add(p_338358_);
                return datacomponentmap.getOrDefault(p_338358_, p_338352_);
            }
        });
        DataComponentPatch datacomponentpatch = p_338381_.forget(set::contains);
        this.components = datacomponentpatch.split().added();
    }

    protected void collectImplicitComponents(DataComponentMap.Builder p_338210_) {
    }

    @Deprecated
    public void removeComponentsFromTag(CompoundTag p_332032_) {
    }

    public final DataComponentMap collectComponents() {
        DataComponentMap.Builder datacomponentmap$builder = DataComponentMap.builder();
        datacomponentmap$builder.addAll(this.components);
        this.collectImplicitComponents(datacomponentmap$builder);
        return datacomponentmap$builder.build();
    }

    public DataComponentMap components() {
        return this.components;
    }

    public void setComponents(DataComponentMap p_338529_) {
        this.components = p_338529_;
    }

    @Nullable
    public static Component parseCustomNameSafe(String p_342033_, HolderLookup.Provider p_342030_) {
        try {
            return Component.Serializer.fromJson(p_342033_, p_342030_);
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse custom name from string '{}', discarding", p_342033_, exception);
            return null;
        }
    }

    static class ComponentHelper {
        public static final Codec<DataComponentMap> COMPONENTS_CODEC = DataComponentMap.CODEC.optionalFieldOf("components", DataComponentMap.EMPTY).codec();

        private ComponentHelper() {
        }
    }

    protected interface DataComponentInput {
        @Nullable
        <T> T get(DataComponentType<T> p_338658_);

        <T> T getOrDefault(DataComponentType<? extends T> p_338573_, T p_338734_);

        // Neo: Utility for modded component types, to remove the need to invoke '.value()'
        @Nullable
        default <T> T get(java.util.function.Supplier<? extends DataComponentType<T>> componentType) {
            return get(componentType.get());
        }

        default <T> T getOrDefault(java.util.function.Supplier<? extends DataComponentType<T>> componentType, T value) {
            return getOrDefault(componentType.get(), value);
        }
    }
}
