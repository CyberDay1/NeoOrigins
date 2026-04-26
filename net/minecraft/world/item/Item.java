package net.minecraft.world.item;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class Item implements FeatureElement, ItemLike, net.neoforged.neoforge.common.extensions.IItemExtension {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Map<Block, Item> BY_BLOCK = net.neoforged.neoforge.registries.GameData.getBlockItemMap();
    public static final ResourceLocation BASE_ATTACK_DAMAGE_ID = ResourceLocation.withDefaultNamespace("base_attack_damage");
    public static final ResourceLocation BASE_ATTACK_SPEED_ID = ResourceLocation.withDefaultNamespace("base_attack_speed");
    public static final int DEFAULT_MAX_STACK_SIZE = 64;
    public static final int ABSOLUTE_MAX_STACK_SIZE = 99;
    public static final int MAX_BAR_WIDTH = 13;
    private final Holder.Reference<Item> builtInRegistryHolder = BuiltInRegistries.ITEM.createIntrusiveHolder(this);
    private DataComponentMap components;
    @Nullable
    private final Item craftingRemainingItem;
    @Nullable
    private String descriptionId;
    private final FeatureFlagSet requiredFeatures;

    public static int getId(Item p_41394_) {
        return p_41394_ == null ? 0 : BuiltInRegistries.ITEM.getId(p_41394_);
    }

    public static Item byId(int p_41446_) {
        return BuiltInRegistries.ITEM.byId(p_41446_);
    }

    @Deprecated
    public static Item byBlock(Block p_41440_) {
        return BY_BLOCK.getOrDefault(p_41440_, Items.AIR);
    }

    public Item(Item.Properties p_41383_) {
        this.components = p_41383_.buildAndValidateComponents();
        this.craftingRemainingItem = p_41383_.craftingRemainingItem;
        this.requiredFeatures = p_41383_.requiredFeatures;
        if (SharedConstants.IS_RUNNING_IN_IDE && false) {
            String s = this.getClass().getSimpleName();
            if (!s.endsWith("Item")) {
                LOGGER.error("Item classes should end with Item and {} doesn't.", s);
            }
        }
        this.canRepair = p_41383_.canRepair;
    }

    @Deprecated
    public Holder.Reference<Item> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    public DataComponentMap components() {
        return this.components;
    }

    /** @deprecated Neo: do not use, use {@link net.neoforged.neoforge.event.ModifyDefaultComponentsEvent the event} instead */
    @org.jetbrains.annotations.ApiStatus.Internal @Deprecated
    public void modifyDefaultComponentsFrom(net.minecraft.core.component.DataComponentPatch patch) {
        if (!net.neoforged.neoforge.internal.RegistrationEvents.canModifyComponents()) throw new IllegalStateException("Default components cannot be modified now!");
        var builder = DataComponentMap.builder().addAll(components);
        patch.entrySet().forEach(entry -> builder.set((DataComponentType)entry.getKey(), entry.getValue().orElse(null)));
        components = Properties.COMPONENT_INTERNER.intern(Properties.validateComponents(builder.build()));
    }

    public int getDefaultMaxStackSize() {
        return this.components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    public void onUseTick(Level p_41428_, LivingEntity p_41429_, ItemStack p_41430_, int p_41431_) {
    }

    /** @deprecated Forge: {@link net.neoforged.neoforge.common.extensions.IItemExtension#onDestroyed(ItemEntity, DamageSource) Use damage source sensitive version} */
    @Deprecated
    public void onDestroyed(ItemEntity p_150887_) {
    }

    public void verifyComponentsAfterLoad(ItemStack p_331627_) {
    }

    public boolean canAttackBlock(BlockState p_41441_, Level p_41442_, BlockPos p_41443_, Player p_41444_) {
        return true;
    }

    @Override
    public Item asItem() {
        return this;
    }

    public InteractionResult useOn(UseOnContext p_41427_) {
        return InteractionResult.PASS;
    }

    public float getDestroySpeed(ItemStack p_41425_, BlockState p_41426_) {
        Tool tool = p_41425_.get(DataComponents.TOOL);
        return tool != null ? tool.getMiningSpeed(p_41426_) : 1.0F;
    }

    public InteractionResultHolder<ItemStack> use(Level p_41432_, Player p_41433_, InteractionHand p_41434_) {
        ItemStack itemstack = p_41433_.getItemInHand(p_41434_);
        FoodProperties foodproperties = itemstack.getFoodProperties(p_41433_);
        if (foodproperties != null) {
            if (p_41433_.canEat(foodproperties.canAlwaysEat())) {
                p_41433_.startUsingItem(p_41434_);
                return InteractionResultHolder.consume(itemstack);
            } else {
                return InteractionResultHolder.fail(itemstack);
            }
        } else {
            return InteractionResultHolder.pass(p_41433_.getItemInHand(p_41434_));
        }
    }

    public ItemStack finishUsingItem(ItemStack p_41409_, Level p_41410_, LivingEntity p_41411_) {
        FoodProperties foodproperties = p_41409_.getFoodProperties(p_41411_);
        return foodproperties != null ? p_41411_.eat(p_41410_, p_41409_, foodproperties) : p_41409_;
    }

    public boolean isBarVisible(ItemStack p_150899_) {
        return p_150899_.isDamaged();
    }

    public int getBarWidth(ItemStack p_150900_) {
        return Math.round(13.0F - (float)p_150900_.getDamageValue() * 13.0F / (float)this.getMaxDamage(p_150900_));
    }

    public int getBarColor(ItemStack p_150901_) {
        int i = p_150901_.getMaxDamage();
        float stackMaxDamage = this.getMaxDamage(p_150901_);
        float f = Math.max(0.0F, (stackMaxDamage - (float)p_150901_.getDamageValue()) / stackMaxDamage);
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    public boolean overrideStackedOnOther(ItemStack p_150888_, Slot p_150889_, ClickAction p_150890_, Player p_150891_) {
        return false;
    }

    public boolean overrideOtherStackedOnMe(
        ItemStack p_150892_, ItemStack p_150893_, Slot p_150894_, ClickAction p_150895_, Player p_150896_, SlotAccess p_150897_
    ) {
        return false;
    }

    public float getAttackDamageBonus(Entity p_345249_, float p_336179_, DamageSource p_345403_) {
        return 0.0F;
    }

    public boolean hurtEnemy(ItemStack p_41395_, LivingEntity p_41396_, LivingEntity p_41397_) {
        return false;
    }

    public void postHurtEnemy(ItemStack p_346136_, LivingEntity p_346250_, LivingEntity p_346014_) {
    }

    public boolean mineBlock(ItemStack p_41416_, Level p_41417_, BlockState p_41418_, BlockPos p_41419_, LivingEntity p_41420_) {
        Tool tool = p_41416_.get(DataComponents.TOOL);
        if (tool == null) {
            return false;
        } else {
            if (!p_41417_.isClientSide && p_41418_.getDestroySpeed(p_41417_, p_41419_) != 0.0F && tool.damagePerBlock() > 0) {
                p_41416_.hurtAndBreak(tool.damagePerBlock(), p_41420_, EquipmentSlot.MAINHAND);
            }

            return true;
        }
    }

    public boolean isCorrectToolForDrops(ItemStack p_336002_, BlockState p_41450_) {
        Tool tool = p_336002_.get(DataComponents.TOOL);
        return tool != null && tool.isCorrectForDrops(p_41450_);
    }

    public InteractionResult interactLivingEntity(ItemStack p_41398_, Player p_41399_, LivingEntity p_41400_, InteractionHand p_41401_) {
        return InteractionResult.PASS;
    }

    public Component getDescription() {
        return Component.translatable(this.getDescriptionId());
    }

    @Override
    public String toString() {
        return BuiltInRegistries.ITEM.wrapAsHolder(this).getRegisteredName();
    }

    protected String getOrCreateDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("item", BuiltInRegistries.ITEM.getKey(this));
        }

        return this.descriptionId;
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public String getDescriptionId(ItemStack p_41455_) {
        return this.getDescriptionId();
    }

    @Nullable
    @Deprecated // Use ItemStack sensitive version.
    public final Item getCraftingRemainingItem() {
        return this.craftingRemainingItem;
    }

    @Deprecated // Use ItemStack sensitive version.
    public boolean hasCraftingRemainingItem() {
        return this.craftingRemainingItem != null;
    }

    public void inventoryTick(ItemStack p_41404_, Level p_41405_, Entity p_41406_, int p_41407_, boolean p_41408_) {
    }

    public void onCraftedBy(ItemStack p_41447_, Level p_41448_, Player p_41449_) {
        this.onCraftedPostProcess(p_41447_, p_41448_);
    }

    public void onCraftedPostProcess(ItemStack p_307483_, Level p_307537_) {
    }

    public boolean isComplex() {
        return false;
    }

    public UseAnim getUseAnimation(ItemStack p_41452_) {
        return p_41452_.has(DataComponents.FOOD) ? UseAnim.EAT : UseAnim.NONE;
    }

    public int getUseDuration(ItemStack p_41454_, LivingEntity p_344979_) {
        FoodProperties foodproperties = p_41454_.getFoodProperties(null);
        return foodproperties != null ? foodproperties.eatDurationTicks() : 0;
    }

    public void releaseUsing(ItemStack p_41412_, Level p_41413_, LivingEntity p_41414_, int p_41415_) {
    }

    public void appendHoverText(ItemStack p_41421_, Item.TooltipContext p_339594_, List<Component> p_41423_, TooltipFlag p_41424_) {
    }

    public Optional<TooltipComponent> getTooltipImage(ItemStack p_150902_) {
        return Optional.empty();
    }

    public Component getName(ItemStack p_41458_) {
        return Component.translatable(this.getDescriptionId(p_41458_));
    }

    public boolean isFoil(ItemStack p_41453_) {
        return p_41453_.isEnchanted();
    }

    public boolean isEnchantable(ItemStack p_41456_) {
        return p_41456_.getMaxStackSize() == 1 && p_41456_.has(DataComponents.MAX_DAMAGE);
    }

    public static BlockHitResult getPlayerPOVHitResult(Level p_41436_, Player p_41437_, ClipContext.Fluid p_41438_) {
        Vec3 vec3 = p_41437_.getEyePosition();
        Vec3 vec31 = vec3.add(p_41437_.calculateViewVector(p_41437_.getXRot(), p_41437_.getYRot()).scale(p_41437_.blockInteractionRange()));
        return p_41436_.clip(new ClipContext(vec3, vec31, ClipContext.Block.OUTLINE, p_41438_, p_41437_));
    }

    /** @deprecated Neo: Use ItemStack sensitive version. */
    @Deprecated
    public int getEnchantmentValue() {
        return 0;
    }

    public boolean isValidRepairItem(ItemStack p_41402_, ItemStack p_41403_) {
        return false;
    }

    /**
     * @deprecated Neo: Use {@link Item#getDefaultAttributeModifiers(ItemStack)}
     */
    @Deprecated
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ItemAttributeModifiers.EMPTY;
    }

    protected final boolean canRepair;

    @Override
    public boolean isRepairable(ItemStack stack) {
        return canRepair && isDamageable(stack);
    }

    public boolean useOnRelease(ItemStack p_41464_) {
        return p_41464_.getItem() == Items.CROSSBOW;
    }

    public ItemStack getDefaultInstance() {
        return new ItemStack(this);
    }

    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_EAT;
    }

    public SoundEvent getBreakingSound() {
        return SoundEvents.ITEM_BREAK;
    }

    /**
     * @deprecated Neo: call {@link net.neoforged.neoforge.common.extensions.IItemStackExtension#canFitInsideContainerItems()} instead,
     * prefer overriding {@link net.neoforged.neoforge.common.extensions.IItemExtension#canFitInsideContainerItems(ItemStack)}
     * @implNote It is recommended to still override this to ensure mods calling this instead of the extension still behave safely.
     */
    @Deprecated
    public boolean canFitInsideContainerItems() {
        return true;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    /**
     * Neo: Allowing mods to define client behavior for their Items
     * @deprecated Use {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent} instead
     */
    @Deprecated(forRemoval = true, since = "1.21")
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
    }

    public static class Properties implements net.neoforged.neoforge.common.extensions.IItemPropertiesExtensions {
        private static final Interner<DataComponentMap> COMPONENT_INTERNER = Interners.newStrongInterner();
        @Nullable
        private DataComponentMap.Builder components;
        @Nullable
        Item craftingRemainingItem;
        FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;
        private boolean canRepair = true;

        public Item.Properties food(FoodProperties p_41490_) {
            return this.component(DataComponents.FOOD, p_41490_);
        }

        public Item.Properties stacksTo(int p_41488_) {
            return this.component(DataComponents.MAX_STACK_SIZE, p_41488_);
        }

        public Item.Properties durability(int p_41504_) {
            this.component(DataComponents.MAX_DAMAGE, p_41504_);
            this.component(DataComponents.MAX_STACK_SIZE, 1);
            this.component(DataComponents.DAMAGE, 0);
            return this;
        }

        public Item.Properties craftRemainder(Item p_41496_) {
            this.craftingRemainingItem = p_41496_;
            return this;
        }

        public Item.Properties rarity(Rarity p_41498_) {
            return this.component(DataComponents.RARITY, p_41498_);
        }

        public Item.Properties fireResistant() {
            return this.component(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
        }

        public Item.Properties jukeboxPlayable(ResourceKey<JukeboxSong> p_350862_) {
            return this.component(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(new EitherHolder<>(p_350862_), true));
        }

        public Item.Properties setNoRepair() {
            canRepair = false;
            return this;
        }

        public Item.Properties requiredFeatures(FeatureFlag... p_250948_) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(p_250948_);
            return this;
        }

        public <T> Item.Properties component(DataComponentType<T> p_330871_, T p_330323_) {
            net.neoforged.neoforge.common.CommonHooks.validateComponent(p_330323_);
            if (this.components == null) {
                this.components = DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS);
            }

            this.components.set(p_330871_, p_330323_);
            return this;
        }

        public Item.Properties attributes(ItemAttributeModifiers p_331533_) {
            return this.component(DataComponents.ATTRIBUTE_MODIFIERS, p_331533_);
        }

        DataComponentMap buildAndValidateComponents() {
            DataComponentMap datacomponentmap = this.buildComponents();
            return validateComponents(datacomponentmap);
        }

        public static DataComponentMap validateComponents(DataComponentMap datacomponentmap) {
            if (datacomponentmap.has(DataComponents.DAMAGE) && datacomponentmap.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
                throw new IllegalStateException("Item cannot have both durability and be stackable");
            } else {
                return datacomponentmap;
            }
        }

        private DataComponentMap buildComponents() {
            return this.components == null ? DataComponents.COMMON_ITEM_COMPONENTS : COMPONENT_INTERNER.intern(this.components.build());
        }
    }

    public interface TooltipContext {
        Item.TooltipContext EMPTY = new Item.TooltipContext() {
            @Nullable
            @Override
            public HolderLookup.Provider registries() {
                return null;
            }

            @Override
            public float tickRate() {
                return 20.0F;
            }

            @Nullable
            @Override
            public MapItemSavedData mapData(MapId p_339618_) {
                return null;
            }
        };

        @Nullable
        HolderLookup.Provider registries();

        float tickRate();

        @Nullable
        MapItemSavedData mapData(MapId p_339670_);

        /**
         * Neo: Returns the level if it's available.
         */
        @Nullable
        default Level level() {
            return null;
        }

        static Item.TooltipContext of(@Nullable final Level p_339599_) {
            return p_339599_ == null ? EMPTY : new Item.TooltipContext() {
                @Override
                public HolderLookup.Provider registries() {
                    return p_339599_.registryAccess();
                }

                @Override
                public float tickRate() {
                    return p_339599_.tickRateManager().tickrate();
                }

                @Override
                public MapItemSavedData mapData(MapId p_339628_) {
                    return p_339599_.getMapData(p_339628_);
                }

                @Override
                public Level level() {
                    return p_339599_;
                }
            };
        }

        static Item.TooltipContext of(final HolderLookup.Provider p_339633_) {
            return new Item.TooltipContext() {
                @Override
                public HolderLookup.Provider registries() {
                    return p_339633_;
                }

                @Override
                public float tickRate() {
                    return 20.0F;
                }

                @Nullable
                @Override
                public MapItemSavedData mapData(MapId p_339679_) {
                    return null;
                }
            };
        }
    }
}
