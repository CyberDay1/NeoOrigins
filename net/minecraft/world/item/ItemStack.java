package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.NullOps;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

public final class ItemStack implements DataComponentHolder, net.neoforged.neoforge.common.MutableDataComponentHolder, net.neoforged.neoforge.common.extensions.IItemStackExtension {
    public static final Codec<Holder<Item>> ITEM_NON_AIR_CODEC = BuiltInRegistries.ITEM
        .holderByNameCodec()
        .validate(
            p_330100_ -> p_330100_.is(Items.AIR.builtInRegistryHolder())
                    ? DataResult.error(() -> "Item must not be minecraft:air")
                    : DataResult.success(p_330100_)
        );
    public static final Codec<ItemStack> CODEC = Codec.lazyInitialized(
        () -> RecordCodecBuilder.create(
                p_347288_ -> p_347288_.group(
                            ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                            ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
                            DataComponentPatch.CODEC
                                .optionalFieldOf("components", DataComponentPatch.EMPTY)
                                .forGetter(p_330103_ -> p_330103_.components.asPatch())
                        )
                        .apply(p_347288_, ItemStack::new)
            )
    );
    public static final Codec<ItemStack> SINGLE_ITEM_CODEC = Codec.lazyInitialized(
        () -> RecordCodecBuilder.create(
                p_337931_ -> p_337931_.group(
                            ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                            DataComponentPatch.CODEC
                                .optionalFieldOf("components", DataComponentPatch.EMPTY)
                                .forGetter(p_332616_ -> p_332616_.components.asPatch())
                        )
                        .apply(p_337931_, (p_332614_, p_332615_) -> new ItemStack(p_332614_, 1, p_332615_))
            )
    );
    public static final Codec<ItemStack> STRICT_CODEC = CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> STRICT_SINGLE_ITEM_CODEC = SINGLE_ITEM_CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
        .xmap(p_330099_ -> p_330099_.orElse(ItemStack.EMPTY), p_330101_ -> p_330101_.isEmpty() ? Optional.empty() : Optional.of(p_330101_));
    public static final Codec<ItemStack> SIMPLE_ITEM_CODEC = ITEM_NON_AIR_CODEC.xmap(ItemStack::new, ItemStack::getItemHolder);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
        private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> ITEM_STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ITEM);

        public ItemStack decode(RegistryFriendlyByteBuf p_320491_) {
            int i = p_320491_.readVarInt();
            if (i <= 0) {
                return ItemStack.EMPTY;
            } else {
                Holder<Item> holder = ITEM_STREAM_CODEC.decode(p_320491_);
                DataComponentPatch datacomponentpatch = DataComponentPatch.STREAM_CODEC.decode(p_320491_);
                return new ItemStack(holder, i, datacomponentpatch);
            }
        }

        public void encode(RegistryFriendlyByteBuf p_320527_, ItemStack p_320873_) {
            if (p_320873_.isEmpty()) {
                p_320527_.writeVarInt(0);
            } else {
                p_320527_.writeVarInt(p_320873_.getCount());
                ITEM_STREAM_CODEC.encode(p_320527_, p_320873_.getItemHolder());
                DataComponentPatch.STREAM_CODEC.encode(p_320527_, p_320873_.components.asPatch());
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
        public ItemStack decode(RegistryFriendlyByteBuf p_330597_) {
            ItemStack itemstack = ItemStack.OPTIONAL_STREAM_CODEC.decode(p_330597_);
            if (itemstack.isEmpty()) {
                throw new DecoderException("Empty ItemStack not allowed");
            } else {
                return itemstack;
            }
        }

        public void encode(RegistryFriendlyByteBuf p_331762_, ItemStack p_331138_) {
            if (p_331138_.isEmpty()) {
                throw new EncoderException("Empty ItemStack not allowed");
            } else {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(p_331762_, p_331138_);
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC = OPTIONAL_STREAM_CODEC.apply(
        ByteBufCodecs.collection(NonNullList::createWithCapacity)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> LIST_STREAM_CODEC = STREAM_CODEC.apply(
        ByteBufCodecs.collection(NonNullList::createWithCapacity)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ItemStack EMPTY = new ItemStack((Void)null);
    private static final Component DISABLED_ITEM_TOOLTIP = Component.translatable("item.disabled").withStyle(ChatFormatting.RED);
    private int count;
    private int popTime;
    @Deprecated
    @Nullable
    private final Item item;
    final PatchedDataComponentMap components;
    @Nullable
    private Entity entityRepresentation;

    private static DataResult<ItemStack> validateStrict(ItemStack p_340966_) {
        DataResult<Unit> dataresult = validateComponents(p_340966_.getComponents());
        if (dataresult.isError()) {
            return dataresult.map(p_340777_ -> p_340966_);
        } else {
            return p_340966_.getCount() > p_340966_.getMaxStackSize()
                ? DataResult.error(() -> "Item stack with stack size of " + p_340966_.getCount() + " was larger than maximum: " + p_340966_.getMaxStackSize())
                : DataResult.success(p_340966_);
        }
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> validatedStreamCodec(final StreamCodec<RegistryFriendlyByteBuf, ItemStack> p_340962_) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf p_341238_) {
                ItemStack itemstack = p_340962_.decode(p_341238_);
                if (!itemstack.isEmpty()) {
                    RegistryOps<Unit> registryops = p_341238_.registryAccess().createSerializationContext(NullOps.INSTANCE);
                    ItemStack.CODEC.encodeStart(registryops, itemstack).getOrThrow(DecoderException::new);
                }

                return itemstack;
            }

            public void encode(RegistryFriendlyByteBuf p_341112_, ItemStack p_341358_) {
                p_340962_.encode(p_341112_, p_341358_);
            }
        };
    }

    public Optional<TooltipComponent> getTooltipImage() {
        return this.getItem().getTooltipImage(this);
    }

    @Override
    public DataComponentMap getComponents() {
        return (DataComponentMap)(!this.isEmpty() ? this.components : DataComponentMap.EMPTY);
    }

    public DataComponentMap getPrototype() {
        return !this.isEmpty() ? this.getItem().components() : DataComponentMap.EMPTY;
    }

    public DataComponentPatch getComponentsPatch() {
        return !this.isEmpty() ? this.components.asPatch() : DataComponentPatch.EMPTY;
    }

    public boolean isComponentsPatchEmpty() {
        return !this.isEmpty() ? this.components.isPatchEmpty() : true;
    }

    public ItemStack(ItemLike p_41599_) {
        this(p_41599_, 1);
    }

    public ItemStack(Holder<Item> p_204116_) {
        this(p_204116_.value(), 1);
    }

    public ItemStack(Holder<Item> p_312081_, int p_41605_, DataComponentPatch p_330362_) {
        this(p_312081_.value(), p_41605_, PatchedDataComponentMap.fromPatch(p_312081_.value().components(), p_330362_));
    }

    public ItemStack(Holder<Item> p_220155_, int p_220156_) {
        this(p_220155_.value(), p_220156_);
    }

    public ItemStack(ItemLike p_41601_, int p_41602_) {
        this(p_41601_, p_41602_, new PatchedDataComponentMap(p_41601_.asItem().components()));
    }

    private ItemStack(ItemLike p_330978_, int p_330639_, PatchedDataComponentMap p_330546_) {
        this.item = p_330978_.asItem();
        this.count = p_330639_;
        this.components = p_330546_;
        this.getItem().verifyComponentsAfterLoad(this);
    }

    private ItemStack(@Nullable Void p_282703_) {
        this.item = null;
        this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }

    public static DataResult<Unit> validateComponents(DataComponentMap p_341201_) {
        if (p_341201_.has(DataComponents.MAX_DAMAGE) && p_341201_.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
            return DataResult.error(() -> "Item cannot be both damageable and stackable");
        } else {
            ItemContainerContents itemcontainercontents = p_341201_.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

            for (ItemStack itemstack : itemcontainercontents.nonEmptyItems()) {
                int i = itemstack.getCount();
                int j = itemstack.getMaxStackSize();
                if (i > j) {
                    return DataResult.error(() -> "Item stack with count of " + i + " was larger than maximum: " + j);
                }
            }

            return DataResult.success(Unit.INSTANCE);
        }
    }

    public static Optional<ItemStack> parse(HolderLookup.Provider p_331096_, Tag p_330238_) {
        return CODEC.parse(p_331096_.createSerializationContext(NbtOps.INSTANCE), p_330238_)
            .resultOrPartial(p_330102_ -> LOGGER.error("Tried to load invalid item: '{}'", p_330102_));
    }

    public static ItemStack parseOptional(HolderLookup.Provider p_330543_, CompoundTag p_330539_) {
        return p_330539_.isEmpty() ? EMPTY : parse(p_330543_, p_330539_).orElse(EMPTY);
    }

    public boolean isEmpty() {
        return this == EMPTY || this.item == Items.AIR || this.count <= 0;
    }

    public boolean isItemEnabled(FeatureFlagSet p_250869_) {
        return this.isEmpty() || this.getItem().isEnabled(p_250869_);
    }

    public ItemStack split(int p_41621_) {
        int i = Math.min(p_41621_, this.getCount());
        ItemStack itemstack = this.copyWithCount(i);
        this.shrink(i);
        return itemstack;
    }

    public ItemStack copyAndClear() {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            ItemStack itemstack = this.copy();
            this.setCount(0);
            return itemstack;
        }
    }

    public Item getItem() {
        return this.isEmpty() ? Items.AIR : this.item;
    }

    public Holder<Item> getItemHolder() {
        return this.getItem().builtInRegistryHolder();
    }

    public boolean is(TagKey<Item> p_204118_) {
        return this.getItem().builtInRegistryHolder().is(p_204118_);
    }

    public boolean is(Item p_150931_) {
        return this.getItem() == p_150931_;
    }

    public boolean is(Predicate<Holder<Item>> p_220168_) {
        return p_220168_.test(this.getItem().builtInRegistryHolder());
    }

    public boolean is(Holder<Item> p_220166_) {
        return is(p_220166_.value()); // Neo: Fix comparing for custom holders such as DeferredHolders
    }

    public boolean is(HolderSet<Item> p_298683_) {
        return p_298683_.contains(this.getItemHolder());
    }

    public Stream<TagKey<Item>> getTags() {
        return this.getItem().builtInRegistryHolder().tags();
    }

    public InteractionResult useOn(UseOnContext p_41662_) {
        var e = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent(p_41662_, net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK));
        if (e.isCanceled()) return e.getCancellationResult().result();
        if (!p_41662_.getLevel().isClientSide) return net.neoforged.neoforge.common.CommonHooks.onPlaceItemIntoWorld(p_41662_);
        return onItemUse(p_41662_, (c) -> getItem().useOn(p_41662_));
    }

    public InteractionResult onItemUseFirst(UseOnContext p_41662_) {
        var e = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent(p_41662_, net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent.UsePhase.ITEM_BEFORE_BLOCK));
        if (e.isCanceled()) return e.getCancellationResult().result();
        return onItemUse(p_41662_, (c) -> getItem().onItemUseFirst(this, p_41662_));
    }

    private InteractionResult onItemUse(UseOnContext p_41662_, java.util.function.Function<UseOnContext, InteractionResult> callback) {
        Player player = p_41662_.getPlayer();
        BlockPos blockpos = p_41662_.getClickedPos();
        if (player != null && !player.getAbilities().mayBuild && !this.canPlaceOnBlockInAdventureMode(new BlockInWorld(p_41662_.getLevel(), blockpos, false))) {
            return InteractionResult.PASS;
        } else {
            Item item = this.getItem();
            InteractionResult interactionresult = callback.apply(p_41662_);
            if (player != null && interactionresult.indicateItemUse()) {
                player.awardStat(Stats.ITEM_USED.get(item));
            }

            return interactionresult;
        }
    }

    public float getDestroySpeed(BlockState p_41692_) {
        return this.getItem().getDestroySpeed(this, p_41692_);
    }

    public InteractionResultHolder<ItemStack> use(Level p_41683_, Player p_41684_, InteractionHand p_41685_) {
        return this.getItem().use(p_41683_, p_41684_, p_41685_);
    }

    public ItemStack finishUsingItem(Level p_41672_, LivingEntity p_41673_) {
        return this.getItem().finishUsingItem(this, p_41672_, p_41673_);
    }

    public Tag save(HolderLookup.Provider p_331900_, Tag p_330830_) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            // Neo: Logs extra information about this ItemStack on error
            return net.neoforged.neoforge.common.util.DataComponentUtil.wrapEncodingExceptions(this, CODEC, p_331900_, p_330830_);
        }
    }

    public Tag save(HolderLookup.Provider p_332160_) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            // Neo: Logs extra information about this ItemStack on error
            return net.neoforged.neoforge.common.util.DataComponentUtil.wrapEncodingExceptions(this, CODEC, p_332160_);
        }
    }

    public Tag saveOptional(HolderLookup.Provider p_330895_) {
        return (Tag)(this.isEmpty() ? new CompoundTag() : this.save(p_330895_, new CompoundTag()));
    }

    public int getMaxStackSize() {
        return this.getItem().getMaxStackSize(this);
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        return this.has(DataComponents.MAX_DAMAGE) && !this.has(DataComponents.UNBREAKABLE) && this.has(DataComponents.DAMAGE);
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && getItem().isDamaged(this);
    }

    public int getDamageValue() {
        return this.getItem().getDamage(this);
    }

    public void setDamageValue(int p_41722_) {
        this.getItem().setDamage(this, p_41722_);
    }

    public int getMaxDamage() {
        return this.getItem().getMaxDamage(this);
    }

    public void hurtAndBreak(int p_220158_, ServerLevel p_346256_, @Nullable ServerPlayer p_220160_, Consumer<Item> p_348596_) {
        this.hurtAndBreak(p_220158_, p_346256_, (LivingEntity) p_220160_, p_348596_);
    }

    public void hurtAndBreak(int p_220158_, ServerLevel p_346256_, @Nullable LivingEntity p_220160_, Consumer<Item> p_348596_) {
        if (this.isDamageableItem()) {
            p_220158_ = getItem().damageItem(this, p_220158_, p_220160_, p_348596_);
            if (p_220160_ == null || !p_220160_.hasInfiniteMaterials()) {
                if (p_220158_ > 0) {
                    p_220158_ = EnchantmentHelper.processDurabilityChange(p_346256_, this, p_220158_);
                    if (p_220158_ <= 0) {
                        return;
                    }
                }

                if (p_220160_ instanceof ServerPlayer sp && p_220158_ != 0) {
                    CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(sp, this, this.getDamageValue() + p_220158_);
                }

                int i = this.getDamageValue() + p_220158_;
                this.setDamageValue(i);
                if (i >= this.getMaxDamage()) {
                    Item item = this.getItem();
                    this.shrink(1);
                    p_348596_.accept(item);
                }
            }
        }
    }

    public void hurtAndBreak(int p_41623_, LivingEntity p_41624_, EquipmentSlot p_319898_) {
        if (p_41624_.level() instanceof ServerLevel serverlevel) {
            this.hurtAndBreak(
                p_41623_,
                serverlevel,
                p_41624_,
                p_348383_ -> p_41624_.onEquippedItemBroken(p_348383_, p_319898_)
            );
        }
    }

    public ItemStack hurtAndConvertOnBreak(int p_352405_, ItemLike p_352337_, LivingEntity p_352175_, EquipmentSlot p_352058_) {
        this.hurtAndBreak(p_352405_, p_352175_, p_352058_);
        if (this.isEmpty()) {
            ItemStack itemstack = this.transmuteCopyIgnoreEmpty(p_352337_, 1);
            if (itemstack.isDamageableItem()) {
                itemstack.setDamageValue(0);
            }

            return itemstack;
        } else {
            return this;
        }
    }

    public boolean isBarVisible() {
        return this.getItem().isBarVisible(this);
    }

    public int getBarWidth() {
        return this.getItem().getBarWidth(this);
    }

    public int getBarColor() {
        return this.getItem().getBarColor(this);
    }

    public boolean overrideStackedOnOther(Slot p_150927_, ClickAction p_150928_, Player p_150929_) {
        return this.getItem().overrideStackedOnOther(this, p_150927_, p_150928_, p_150929_);
    }

    public boolean overrideOtherStackedOnMe(ItemStack p_150933_, Slot p_150934_, ClickAction p_150935_, Player p_150936_, SlotAccess p_150937_) {
        return this.getItem().overrideOtherStackedOnMe(this, p_150933_, p_150934_, p_150935_, p_150936_, p_150937_);
    }

    public boolean hurtEnemy(LivingEntity p_41641_, Player p_41642_) {
        Item item = this.getItem();
        if (item.hurtEnemy(this, p_41641_, p_41642_)) {
            p_41642_.awardStat(Stats.ITEM_USED.get(item));
            return true;
        } else {
            return false;
        }
    }

    public void postHurtEnemy(LivingEntity p_346409_, Player p_345517_) {
        this.getItem().postHurtEnemy(this, p_346409_, p_345517_);
    }

    public void mineBlock(Level p_41687_, BlockState p_41688_, BlockPos p_41689_, Player p_41690_) {
        Item item = this.getItem();
        if (item.mineBlock(this, p_41687_, p_41688_, p_41689_, p_41690_)) {
            p_41690_.awardStat(Stats.ITEM_USED.get(item));
        }
    }

    public boolean isCorrectToolForDrops(BlockState p_41736_) {
        return this.getItem().isCorrectToolForDrops(this, p_41736_);
    }

    public InteractionResult interactLivingEntity(Player p_41648_, LivingEntity p_41649_, InteractionHand p_41650_) {
        return this.getItem().interactLivingEntity(this, p_41648_, p_41649_, p_41650_);
    }

    public ItemStack copy() {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            ItemStack itemstack = new ItemStack(this.getItem(), this.count, this.components.copy());
            itemstack.setPopTime(this.getPopTime());
            return itemstack;
        }
    }

    public ItemStack copyWithCount(int p_256354_) {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            ItemStack itemstack = this.copy();
            itemstack.setCount(p_256354_);
            return itemstack;
        }
    }

    public ItemStack transmuteCopy(ItemLike p_347550_) {
        return this.transmuteCopy(p_347550_, this.getCount());
    }

    public ItemStack transmuteCopy(ItemLike p_323864_, int p_323647_) {
        return this.isEmpty() ? EMPTY : this.transmuteCopyIgnoreEmpty(p_323864_, p_323647_);
    }

    private ItemStack transmuteCopyIgnoreEmpty(ItemLike p_323811_, int p_323856_) {
        return new ItemStack(p_323811_.asItem().builtInRegistryHolder(), p_323856_, this.components.asPatch());
    }

    public static boolean matches(ItemStack p_41729_, ItemStack p_41730_) {
        if (p_41729_ == p_41730_) {
            return true;
        } else {
            return p_41729_.getCount() != p_41730_.getCount() ? false : isSameItemSameComponents(p_41729_, p_41730_);
        }
    }

    @Deprecated
    public static boolean listMatches(List<ItemStack> p_331725_, List<ItemStack> p_331113_) {
        if (p_331725_.size() != p_331113_.size()) {
            return false;
        } else {
            for (int i = 0; i < p_331725_.size(); i++) {
                if (!matches(p_331725_.get(i), p_331113_.get(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    public static boolean isSameItem(ItemStack p_287761_, ItemStack p_287676_) {
        return p_287761_.is(p_287676_.getItem());
    }

    public static boolean isSameItemSameComponents(ItemStack p_150943_, ItemStack p_150944_) {
        if (!p_150943_.is(p_150944_.getItem())) {
            return false;
        } else {
            return p_150943_.isEmpty() && p_150944_.isEmpty() ? true : Objects.equals(p_150943_.components, p_150944_.components);
        }
    }

    public static MapCodec<ItemStack> lenientOptionalFieldOf(String p_338501_) {
        return CODEC.lenientOptionalFieldOf(p_338501_)
            .xmap(p_323389_ -> p_323389_.orElse(EMPTY), p_323388_ -> p_323388_.isEmpty() ? Optional.empty() : Optional.of(p_323388_));
    }

    public static int hashItemAndComponents(@Nullable ItemStack p_331961_) {
        if (p_331961_ != null) {
            int i = 31 + p_331961_.getItem().hashCode();
            return 31 * i + p_331961_.getComponents().hashCode();
        } else {
            return 0;
        }
    }

    @Deprecated
    public static int hashStackList(List<ItemStack> p_332135_) {
        int i = 0;

        for (ItemStack itemstack : p_332135_) {
            i = i * 31 + hashItemAndComponents(itemstack);
        }

        return i;
    }

    public String getDescriptionId() {
        return this.getItem().getDescriptionId(this);
    }

    @Override
    public String toString() {
        return this.getCount() + " " + this.getItem();
    }

    public void inventoryTick(Level p_41667_, Entity p_41668_, int p_41669_, boolean p_41670_) {
        if (this.popTime > 0) {
            this.popTime--;
        }

        if (this.getItem() != null) {
            this.getItem().inventoryTick(this, p_41667_, p_41668_, p_41669_, p_41670_);
        }
    }

    public void onCraftedBy(Level p_41679_, Player p_41680_, int p_41681_) {
        p_41680_.awardStat(Stats.ITEM_CRAFTED.get(this.getItem()), p_41681_);
        this.getItem().onCraftedBy(this, p_41679_, p_41680_);
    }

    public void onCraftedBySystem(Level p_307669_) {
        this.getItem().onCraftedPostProcess(this, p_307669_);
    }

    public int getUseDuration(LivingEntity p_345724_) {
        return this.getItem().getUseDuration(this, p_345724_);
    }

    public UseAnim getUseAnimation() {
        return this.getItem().getUseAnimation(this);
    }

    public void releaseUsing(Level p_41675_, LivingEntity p_41676_, int p_41677_) {
        this.getItem().releaseUsing(this, p_41675_, p_41676_, p_41677_);
    }

    public boolean useOnRelease() {
        return this.getItem().useOnRelease(this);
    }

    @Nullable
    public <T> T set(DataComponentType<? super T> p_331064_, @Nullable T p_330775_) {
        return this.components.set(p_331064_, p_330775_);
    }

    @Nullable
    public <T, U> T update(DataComponentType<T> p_331083_, T p_331443_, U p_331049_, BiFunction<T, U, T> p_331846_) {
        return this.set(p_331083_, p_331846_.apply(this.getOrDefault(p_331083_, p_331443_), p_331049_));
    }

    @Nullable
    public <T> T update(DataComponentType<T> p_330921_, T p_331257_, UnaryOperator<T> p_331701_) {
        T t = this.getOrDefault(p_330921_, p_331257_);
        return this.set(p_330921_, p_331701_.apply(t));
    }

    @Nullable
    public <T> T remove(DataComponentType<? extends T> p_332139_) {
        return this.components.remove(p_332139_);
    }

    public void applyComponentsAndValidate(DataComponentPatch p_341407_) {
        DataComponentPatch datacomponentpatch = this.components.asPatch();
        this.components.applyPatch(p_341407_);
        Optional<Error<ItemStack>> optional = validateStrict(this).error();
        if (optional.isPresent()) {
            LOGGER.error("Failed to apply component patch '{}' to item: '{}'", p_341407_, optional.get().message());
            this.components.restorePatch(datacomponentpatch);
        } else {
            this.getItem().verifyComponentsAfterLoad(this);
        }
    }

    public void applyComponents(DataComponentPatch p_332097_) {
        this.components.applyPatch(p_332097_);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public void applyComponents(DataComponentMap p_330402_) {
        this.components.setAll(p_330402_);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public Component getHoverName() {
        Component component = this.get(DataComponents.CUSTOM_NAME);
        if (component != null) {
            return component;
        } else {
            Component component1 = this.get(DataComponents.ITEM_NAME);
            return component1 != null ? component1 : this.getItem().getName(this);
        }
    }

    public <T extends TooltipProvider> void addToTooltip(
        DataComponentType<T> p_331344_, Item.TooltipContext p_341231_, Consumer<Component> p_331885_, TooltipFlag p_331177_
    ) {
        T t = (T)this.get(p_331344_);
        if (t != null) {
            t.addToTooltip(p_341231_, p_331885_, p_331177_);
        }
    }

    public List<Component> getTooltipLines(Item.TooltipContext p_339637_, @Nullable Player p_41652_, TooltipFlag p_41653_) {
        if (!p_41653_.isCreative() && this.has(DataComponents.HIDE_TOOLTIP)) {
            return List.of();
        } else {
            List<Component> list = Lists.newArrayList();
            MutableComponent mutablecomponent = Component.empty().append(this.getHoverName()).withStyle(this.getRarity().getStyleModifier());
            if (this.has(DataComponents.CUSTOM_NAME)) {
                mutablecomponent.withStyle(ChatFormatting.ITALIC);
            }

            list.add(mutablecomponent);
            if (!p_41653_.isAdvanced() && !this.has(DataComponents.CUSTOM_NAME) && this.is(Items.FILLED_MAP)) {
                MapId mapid = this.get(DataComponents.MAP_ID);
                if (mapid != null) {
                    list.add(MapItem.getTooltipForId(mapid));
                }
            }

            Consumer<Component> consumer = list::add;
            if (!this.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) {
                this.getItem().appendHoverText(this, p_339637_, list, p_41653_);
            }

            this.addToTooltip(DataComponents.JUKEBOX_PLAYABLE, p_339637_, consumer, p_41653_);
            this.addToTooltip(DataComponents.TRIM, p_339637_, consumer, p_41653_);
            this.addToTooltip(DataComponents.STORED_ENCHANTMENTS, p_339637_, consumer, p_41653_);
            this.addToTooltip(DataComponents.ENCHANTMENTS, p_339637_, consumer, p_41653_);
            this.addToTooltip(DataComponents.DYED_COLOR, p_339637_, consumer, p_41653_);
            this.addToTooltip(DataComponents.LORE, p_339637_, consumer, p_41653_);
            // Neo: Replace attribute tooltips with custom handling
            net.neoforged.neoforge.common.util.AttributeUtil.addAttributeTooltips(this, consumer,
                    net.neoforged.neoforge.common.util.AttributeTooltipContext.of(p_41652_, p_339637_, p_41653_));
            this.addToTooltip(DataComponents.UNBREAKABLE, p_339637_, consumer, p_41653_);
            AdventureModePredicate adventuremodepredicate = this.get(DataComponents.CAN_BREAK);
            if (adventuremodepredicate != null && adventuremodepredicate.showInTooltip()) {
                consumer.accept(CommonComponents.EMPTY);
                consumer.accept(AdventureModePredicate.CAN_BREAK_HEADER);
                adventuremodepredicate.addToTooltip(consumer);
            }

            AdventureModePredicate adventuremodepredicate1 = this.get(DataComponents.CAN_PLACE_ON);
            if (adventuremodepredicate1 != null && adventuremodepredicate1.showInTooltip()) {
                consumer.accept(CommonComponents.EMPTY);
                consumer.accept(AdventureModePredicate.CAN_PLACE_HEADER);
                adventuremodepredicate1.addToTooltip(consumer);
            }

            if (p_41653_.isAdvanced()) {
                if (this.isDamaged()) {
                    list.add(Component.translatable("item.durability", this.getMaxDamage() - this.getDamageValue(), this.getMaxDamage()));
                }

                list.add(Component.literal(BuiltInRegistries.ITEM.getKey(this.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
                int i = this.components.size();
                if (i > 0) {
                    list.add(Component.translatable("item.components", i).withStyle(ChatFormatting.DARK_GRAY));
                }
            }

            if (p_41652_ != null && !this.getItem().isEnabled(p_41652_.level().enabledFeatures())) {
                list.add(DISABLED_ITEM_TOOLTIP);
            }

            net.neoforged.neoforge.event.EventHooks.onItemTooltip(this, p_41652_, list, p_41653_, p_339637_);
            return list;
        }
    }

    /**
     * @deprecated Neo: Use {@link net.neoforged.neoforge.client.util.TooltipUtil#addAttributeTooltips}
     */
    @Deprecated
    private void addAttributeTooltips(Consumer<Component> p_330796_, @Nullable Player p_330530_) {
        ItemAttributeModifiers itemattributemodifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (itemattributemodifiers.showInTooltip()) {
            for (EquipmentSlotGroup equipmentslotgroup : EquipmentSlotGroup.values()) {
                MutableBoolean mutableboolean = new MutableBoolean(true);
                this.forEachModifier(equipmentslotgroup, (p_348379_, p_348380_) -> {
                    if (mutableboolean.isTrue()) {
                        p_330796_.accept(CommonComponents.EMPTY);
                        p_330796_.accept(Component.translatable("item.modifiers." + equipmentslotgroup.getSerializedName()).withStyle(ChatFormatting.GRAY));
                        mutableboolean.setFalse();
                    }

                    this.addModifierTooltip(p_330796_, p_330530_, p_348379_, p_348380_);
                });
            }
        }
    }

    private void addModifierTooltip(Consumer<Component> p_331062_, @Nullable Player p_330317_, Holder<Attribute> p_330626_, AttributeModifier p_331252_) {
        double d0 = p_331252_.amount();
        boolean flag = false;
        if (p_330317_ != null) {
            if (p_331252_.is(Item.BASE_ATTACK_DAMAGE_ID)) {
                d0 += p_330317_.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                flag = true;
            } else if (p_331252_.is(Item.BASE_ATTACK_SPEED_ID)) {
                d0 += p_330317_.getAttributeBaseValue(Attributes.ATTACK_SPEED);
                flag = true;
            }
        }

        double d1;
        if (p_331252_.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            || p_331252_.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            d1 = d0 * 100.0;
        } else if (p_330626_.is(Attributes.KNOCKBACK_RESISTANCE)) {
            d1 = d0 * 10.0;
        } else {
            d1 = d0;
        }

        if (flag) {
            p_331062_.accept(
                CommonComponents.space()
                    .append(
                        Component.translatable(
                            "attribute.modifier.equals." + p_331252_.operation().id(),
                            ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1),
                            Component.translatable(p_330626_.value().getDescriptionId())
                        )
                    )
                    .withStyle(ChatFormatting.DARK_GREEN)
            );
        } else if (d0 > 0.0) {
            p_331062_.accept(
                Component.translatable(
                        "attribute.modifier.plus." + p_331252_.operation().id(),
                        ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1),
                        Component.translatable(p_330626_.value().getDescriptionId())
                    )
                    .withStyle(p_330626_.value().getStyle(true))
            );
        } else if (d0 < 0.0) {
            p_331062_.accept(
                Component.translatable(
                        "attribute.modifier.take." + p_331252_.operation().id(),
                        ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(-d1),
                        Component.translatable(p_330626_.value().getDescriptionId())
                    )
                    .withStyle(p_330626_.value().getStyle(false))
            );
        }
    }

    public boolean hasFoil() {
        Boolean obool = this.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return obool != null ? obool : this.getItem().isFoil(this);
    }

    public Rarity getRarity() {
        Rarity rarity = this.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        if (!this.isEnchanted()) {
            return rarity;
        } else {
            return switch (rarity) {
                case COMMON, UNCOMMON -> Rarity.RARE;
                case RARE -> Rarity.EPIC;
                default -> rarity;
            };
        }
    }

    public boolean isEnchantable() {
        if (!this.getItem().isEnchantable(this)) {
            return false;
        } else {
            ItemEnchantments itemenchantments = this.get(DataComponents.ENCHANTMENTS);
            return itemenchantments != null && itemenchantments.isEmpty();
        }
    }

    public void enchant(Holder<Enchantment> p_344751_, int p_41665_) {
        EnchantmentHelper.updateEnchantments(this, p_344404_ -> p_344404_.upgrade(p_344751_, p_41665_));
    }

    public boolean isEnchanted() {
        return !this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    /**
     * Gets all enchantments from NBT. Use {@link ItemStack#getAllEnchantments} for gameplay logic.
     */
    public ItemEnchantments getTagEnchantments() {
        return getEnchantments();
    }

    /**
     * @deprecated Neo: Use {@link #getTagEnchantments()} for NBT enchantments, or {@link #getAllEnchantments} for gameplay.
     */
    @Deprecated
    public ItemEnchantments getEnchantments() {
        return this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    public boolean isFramed() {
        return this.entityRepresentation instanceof ItemFrame;
    }

    public void setEntityRepresentation(@Nullable Entity p_41637_) {
        if (!this.isEmpty()) {
            this.entityRepresentation = p_41637_;
        }
    }

    @Nullable
    public ItemFrame getFrame() {
        return this.entityRepresentation instanceof ItemFrame ? (ItemFrame)this.getEntityRepresentation() : null;
    }

    @Nullable
    public Entity getEntityRepresentation() {
        return !this.isEmpty() ? this.entityRepresentation : null;
    }

    public void forEachModifier(EquipmentSlotGroup p_348610_, BiConsumer<Holder<Attribute>, AttributeModifier> p_348516_) {
        // Neo: Reflect real attribute modifiers when doing iteration
        this.getAttributeModifiers().forEach(p_348610_, p_348516_);

        if (false) {
        // Start disabled vanilla code

        ItemAttributeModifiers itemattributemodifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (!itemattributemodifiers.modifiers().isEmpty()) {
            itemattributemodifiers.forEach(p_348610_, p_348516_);
        } else {
            this.getItem().getDefaultAttributeModifiers().forEach(p_348610_, p_348516_);
        }

        // end disabled vanilla code
        }

        EnchantmentHelper.forEachModifier(this, p_348610_, p_348516_);
    }

    public void forEachModifier(EquipmentSlot p_332001_, BiConsumer<Holder<Attribute>, AttributeModifier> p_330882_) {
        // Neo: Reflect real attribute modifiers when doing iteration
        this.getAttributeModifiers().forEach(p_332001_, p_330882_);

        if (false) {
        // Start disabled vanilla code

        ItemAttributeModifiers itemattributemodifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (!itemattributemodifiers.modifiers().isEmpty()) {
            itemattributemodifiers.forEach(p_332001_, p_330882_);
        } else {
            this.getItem().getDefaultAttributeModifiers().forEach(p_332001_, p_330882_);
        }

        // end disabled vanilla code
        }

        EnchantmentHelper.forEachModifier(this, p_332001_, p_330882_);
    }

    public Component getDisplayName() {
        MutableComponent mutablecomponent = Component.empty().append(this.getHoverName());
        if (this.has(DataComponents.CUSTOM_NAME)) {
            mutablecomponent.withStyle(ChatFormatting.ITALIC);
        }

        MutableComponent mutablecomponent1 = ComponentUtils.wrapInSquareBrackets(mutablecomponent);
        if (!this.isEmpty()) {
            mutablecomponent1.withStyle(this.getRarity().getStyleModifier())
                .withStyle(p_220170_ -> p_220170_.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(this))));
        }

        return mutablecomponent1;
    }

    public boolean canPlaceOnBlockInAdventureMode(BlockInWorld p_331419_) {
        AdventureModePredicate adventuremodepredicate = this.get(DataComponents.CAN_PLACE_ON);
        return adventuremodepredicate != null && adventuremodepredicate.test(p_331419_);
    }

    public boolean canBreakBlockInAdventureMode(BlockInWorld p_331592_) {
        AdventureModePredicate adventuremodepredicate = this.get(DataComponents.CAN_BREAK);
        return adventuremodepredicate != null && adventuremodepredicate.test(p_331592_);
    }

    public int getPopTime() {
        return this.popTime;
    }

    public void setPopTime(int p_41755_) {
        this.popTime = p_41755_;
    }

    public int getCount() {
        return this.isEmpty() ? 0 : this.count;
    }

    public void setCount(int p_41765_) {
        this.count = p_41765_;
    }

    public void limitSize(int p_335437_) {
        if (!this.isEmpty() && this.getCount() > p_335437_) {
            this.setCount(p_335437_);
        }
    }

    public void grow(int p_41770_) {
        this.setCount(this.getCount() + p_41770_);
    }

    public void shrink(int p_41775_) {
        this.grow(-p_41775_);
    }

    public void consume(int p_326311_, @Nullable LivingEntity p_326200_) {
        if (p_326200_ == null || !p_326200_.hasInfiniteMaterials()) {
            this.shrink(p_326311_);
        }
    }

    public ItemStack consumeAndReturn(int p_347624_, @Nullable LivingEntity p_347710_) {
        ItemStack itemstack = this.copyWithCount(p_347624_);
        this.consume(p_347624_, p_347710_);
        return itemstack;
    }

    public void onUseTick(Level p_41732_, LivingEntity p_41733_, int p_41734_) {
        this.getItem().onUseTick(p_41732_, p_41733_, this, p_41734_);
    }

    /** @deprecated Forge: Use {@linkplain net.neoforged.neoforge.common.extensions.IItemStackExtension#onDestroyed(ItemEntity, net.minecraft.world.damagesource.DamageSource) damage source sensitive version} */
    @Deprecated
    public void onDestroyed(ItemEntity p_150925_) {
        this.getItem().onDestroyed(p_150925_);
    }

    public SoundEvent getDrinkingSound() {
        return this.getItem().getDrinkingSound();
    }

    public SoundEvent getEatingSound() {
        return this.getItem().getEatingSound();
    }

    public SoundEvent getBreakingSound() {
        return this.getItem().getBreakingSound();
    }

    public boolean canBeHurtBy(DamageSource p_335431_) {
        if (!getItem().canBeHurtBy(this, p_335431_)) return false;
        return !this.has(DataComponents.FIRE_RESISTANT) || !p_335431_.is(DamageTypeTags.IS_FIRE);
    }
}
