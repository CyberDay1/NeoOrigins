package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, RecipeCraftingHolder, StackedContentsCompatible {
    protected static final int SLOT_INPUT = 0;
    protected static final int SLOT_FUEL = 1;
    protected static final int SLOT_RESULT = 2;
    public static final int DATA_LIT_TIME = 0;
    private static final int[] SLOTS_FOR_UP = new int[]{0};
    private static final int[] SLOTS_FOR_DOWN = new int[]{2, 1};
    private static final int[] SLOTS_FOR_SIDES = new int[]{1};
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_COOKING_PROGRESS = 2;
    public static final int DATA_COOKING_TOTAL_TIME = 3;
    public static final int NUM_DATA_VALUES = 4;
    public static final int BURN_TIME_STANDARD = 200;
    public static final int BURN_COOL_SPEED = 2;
    private final RecipeType<? extends AbstractCookingRecipe> recipeType;
    protected NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    int litTime;
    int litDuration;
    int cookingProgress;
    int cookingTotalTime;
    @Nullable
    private static volatile Map<Item, Integer> fuelCache;
    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int p_58431_) {
            switch (p_58431_) {
                case 0:
                    if (litDuration > Short.MAX_VALUE) {
                        // Neo: preserve litTime / litDuration ratio on the client as data slots are synced as shorts.
                        return net.minecraft.util.Mth.floor(((double) litTime / litDuration) * Short.MAX_VALUE);
                    }

                    return AbstractFurnaceBlockEntity.this.litTime;
                case 1:
                    return Math.min(AbstractFurnaceBlockEntity.this.litDuration, Short.MAX_VALUE);
                case 2:
                    return AbstractFurnaceBlockEntity.this.cookingProgress;
                case 3:
                    return AbstractFurnaceBlockEntity.this.cookingTotalTime;
                default:
                    return 0;
            }
        }

        @Override
        public void set(int p_58433_, int p_58434_) {
            switch (p_58433_) {
                case 0:
                    AbstractFurnaceBlockEntity.this.litTime = p_58434_;
                    break;
                case 1:
                    AbstractFurnaceBlockEntity.this.litDuration = p_58434_;
                    break;
                case 2:
                    AbstractFurnaceBlockEntity.this.cookingProgress = p_58434_;
                    break;
                case 3:
                    AbstractFurnaceBlockEntity.this.cookingTotalTime = p_58434_;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };
    private final Object2IntOpenHashMap<ResourceLocation> recipesUsed = new Object2IntOpenHashMap<>();
    private final RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

    protected AbstractFurnaceBlockEntity(
        BlockEntityType<?> p_154991_, BlockPos p_154992_, BlockState p_154993_, RecipeType<? extends AbstractCookingRecipe> p_154994_
    ) {
        super(p_154991_, p_154992_, p_154993_);
        this.quickCheck = RecipeManager.createCheck((RecipeType<AbstractCookingRecipe>)p_154994_);
        this.recipeType = p_154994_;
    }

    public static void invalidateCache() {
        fuelCache = null;
    }

    /**
     * @deprecated Neo: get burn times by calling {@link net.neoforged.neoforge.common.extensions.IItemStackExtension#getBurnTime(RecipeType)}
     */
    @Deprecated
    public static Map<Item, Integer> getFuel() {
        Map<Item, Integer> map = fuelCache;
        if (map != null) {
            return map;
        } else {
            Map<Item, Integer> map1 = Maps.newLinkedHashMap();
            buildFuels((e, time) -> e.ifRight(tag -> add(map1, tag, time)).ifLeft(item -> add(map1, item, time)));
            fuelCache = map1;
            return map1;
        }
    }

    private static void add(java.util.function.ObjIntConsumer<com.mojang.datafixers.util.Either<Item, TagKey<Item>>> consumer, ItemLike item, int time) {
        consumer.accept(com.mojang.datafixers.util.Either.left(item.asItem()), time);
    }

    private static void add(java.util.function.ObjIntConsumer<com.mojang.datafixers.util.Either<Item, TagKey<Item>>> consumer, TagKey<Item> tag, int time) {
        consumer.accept(com.mojang.datafixers.util.Either.right(tag), time);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public static void buildFuels(java.util.function.ObjIntConsumer<com.mojang.datafixers.util.Either<Item, TagKey<Item>>> map1) {
        {
            add(map1, Items.LAVA_BUCKET, 20000);
            add(map1, Blocks.COAL_BLOCK, 16000);
            add(map1, Items.BLAZE_ROD, 2400);
            add(map1, Items.COAL, 1600);
            add(map1, Items.CHARCOAL, 1600);
            add(map1, ItemTags.LOGS, 300);
            add(map1, ItemTags.BAMBOO_BLOCKS, 300);
            add(map1, ItemTags.PLANKS, 300);
            add(map1, Blocks.BAMBOO_MOSAIC, 300);
            add(map1, ItemTags.WOODEN_STAIRS, 300);
            add(map1, Blocks.BAMBOO_MOSAIC_STAIRS, 300);
            add(map1, ItemTags.WOODEN_SLABS, 150);
            add(map1, Blocks.BAMBOO_MOSAIC_SLAB, 150);
            add(map1, ItemTags.WOODEN_TRAPDOORS, 300);
            add(map1, ItemTags.WOODEN_PRESSURE_PLATES, 300);
            add(map1, ItemTags.WOODEN_FENCES, 300);
            add(map1, ItemTags.FENCE_GATES, 300);
            add(map1, Blocks.NOTE_BLOCK, 300);
            add(map1, Blocks.BOOKSHELF, 300);
            add(map1, Blocks.CHISELED_BOOKSHELF, 300);
            add(map1, Blocks.LECTERN, 300);
            add(map1, Blocks.JUKEBOX, 300);
            add(map1, Blocks.CHEST, 300);
            add(map1, Blocks.TRAPPED_CHEST, 300);
            add(map1, Blocks.CRAFTING_TABLE, 300);
            add(map1, Blocks.DAYLIGHT_DETECTOR, 300);
            add(map1, ItemTags.BANNERS, 300);
            add(map1, Items.BOW, 300);
            add(map1, Items.FISHING_ROD, 300);
            add(map1, Blocks.LADDER, 300);
            add(map1, ItemTags.SIGNS, 200);
            add(map1, ItemTags.HANGING_SIGNS, 800);
            add(map1, Items.WOODEN_SHOVEL, 200);
            add(map1, Items.WOODEN_SWORD, 200);
            add(map1, Items.WOODEN_HOE, 200);
            add(map1, Items.WOODEN_AXE, 200);
            add(map1, Items.WOODEN_PICKAXE, 200);
            add(map1, ItemTags.WOODEN_DOORS, 200);
            add(map1, ItemTags.BOATS, 1200);
            add(map1, ItemTags.WOOL, 100);
            add(map1, ItemTags.WOODEN_BUTTONS, 100);
            add(map1, Items.STICK, 100);
            add(map1, ItemTags.SAPLINGS, 100);
            add(map1, Items.BOWL, 100);
            add(map1, ItemTags.WOOL_CARPETS, 67);
            add(map1, Blocks.DRIED_KELP_BLOCK, 4001);
            add(map1, Items.CROSSBOW, 300);
            add(map1, Blocks.BAMBOO, 50);
            add(map1, Blocks.DEAD_BUSH, 100);
            add(map1, Blocks.SCAFFOLDING, 50);
            add(map1, Blocks.LOOM, 300);
            add(map1, Blocks.BARREL, 300);
            add(map1, Blocks.CARTOGRAPHY_TABLE, 300);
            add(map1, Blocks.FLETCHING_TABLE, 300);
            add(map1, Blocks.SMITHING_TABLE, 300);
            add(map1, Blocks.COMPOSTER, 300);
            add(map1, Blocks.AZALEA, 100);
            add(map1, Blocks.FLOWERING_AZALEA, 100);
            add(map1, Blocks.MANGROVE_ROOTS, 300);
        }
    }

    private static boolean isNeverAFurnaceFuel(Item p_58398_) {
        return p_58398_.builtInRegistryHolder().is(ItemTags.NON_FLAMMABLE_WOOD);
    }

    private static void add(Map<Item, Integer> p_204303_, TagKey<Item> p_204304_, int p_204305_) {
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(p_204304_)) {
            if (!isNeverAFurnaceFuel(holder.value())) {
                p_204303_.put(holder.value(), p_204305_);
            }
        }
    }

    private static void add(Map<Item, Integer> p_58375_, ItemLike p_58376_, int p_58377_) {
        Item item = p_58376_.asItem();
        if (isNeverAFurnaceFuel(item)) {
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                throw (IllegalStateException)Util.pauseInIde(
                    new IllegalStateException(
                        "A developer tried to explicitly make fire resistant item " + item.getName(null).getString() + " a furnace fuel. That will not work!"
                    )
                );
            }
        } else {
            p_58375_.put(item, p_58377_);
        }
    }

    private boolean isLit() {
        return this.litTime > 0;
    }

    @Override
    protected void loadAdditional(CompoundTag p_155025_, HolderLookup.Provider p_323468_) {
        super.loadAdditional(p_155025_, p_323468_);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(p_155025_, this.items, p_323468_);
        this.litTime = p_155025_.getInt("BurnTime");
        this.cookingProgress = p_155025_.getInt("CookTime");
        this.cookingTotalTime = p_155025_.getInt("CookTimeTotal");
        this.litDuration = this.getBurnDuration(this.items.get(1));
        CompoundTag compoundtag = p_155025_.getCompound("RecipesUsed");

        for (String s : compoundtag.getAllKeys()) {
            this.recipesUsed.put(ResourceLocation.parse(s), compoundtag.getInt(s));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag p_187452_, HolderLookup.Provider p_323656_) {
        super.saveAdditional(p_187452_, p_323656_);
        p_187452_.putInt("BurnTime", this.litTime);
        p_187452_.putInt("CookTime", this.cookingProgress);
        p_187452_.putInt("CookTimeTotal", this.cookingTotalTime);
        ContainerHelper.saveAllItems(p_187452_, this.items, p_323656_);
        CompoundTag compoundtag = new CompoundTag();
        this.recipesUsed.forEach((p_187449_, p_187450_) -> compoundtag.putInt(p_187449_.toString(), p_187450_));
        p_187452_.put("RecipesUsed", compoundtag);
    }

    public static void serverTick(Level p_155014_, BlockPos p_155015_, BlockState p_155016_, AbstractFurnaceBlockEntity p_155017_) {
        boolean flag = p_155017_.isLit();
        boolean flag1 = false;
        if (p_155017_.isLit()) {
            p_155017_.litTime--;
        }

        ItemStack itemstack = p_155017_.items.get(1);
        ItemStack itemstack1 = p_155017_.items.get(0);
        boolean flag2 = !itemstack1.isEmpty();
        boolean flag3 = !itemstack.isEmpty();
        if (p_155017_.isLit() || flag3 && flag2) {
            RecipeHolder<?> recipeholder;
            if (flag2) {
                recipeholder = p_155017_.quickCheck.getRecipeFor(new SingleRecipeInput(itemstack1), p_155014_).orElse(null);
            } else {
                recipeholder = null;
            }

            int i = p_155017_.getMaxStackSize();
            if (!p_155017_.isLit() && canBurn(p_155014_.registryAccess(), recipeholder, p_155017_.items, i, p_155017_)) {
                p_155017_.litTime = p_155017_.getBurnDuration(itemstack);
                p_155017_.litDuration = p_155017_.litTime;
                if (p_155017_.isLit()) {
                    flag1 = true;
                    if (itemstack.hasCraftingRemainingItem())
                        p_155017_.items.set(1, itemstack.getCraftingRemainingItem());
                    else
                    if (flag3) {
                        Item item = itemstack.getItem();
                        itemstack.shrink(1);
                        if (itemstack.isEmpty()) {
                            p_155017_.items.set(1, itemstack.getCraftingRemainingItem());
                        }
                    }
                }
            }

            if (p_155017_.isLit() && canBurn(p_155014_.registryAccess(), recipeholder, p_155017_.items, i, p_155017_)) {
                p_155017_.cookingProgress++;
                if (p_155017_.cookingProgress == p_155017_.cookingTotalTime) {
                    p_155017_.cookingProgress = 0;
                    p_155017_.cookingTotalTime = getTotalCookTime(p_155014_, p_155017_);
                    if (burn(p_155014_.registryAccess(), recipeholder, p_155017_.items, i, p_155017_)) {
                        p_155017_.setRecipeUsed(recipeholder);
                    }

                    flag1 = true;
                }
            } else {
                p_155017_.cookingProgress = 0;
            }
        } else if (!p_155017_.isLit() && p_155017_.cookingProgress > 0) {
            p_155017_.cookingProgress = Mth.clamp(p_155017_.cookingProgress - 2, 0, p_155017_.cookingTotalTime);
        }

        if (flag != p_155017_.isLit()) {
            flag1 = true;
            p_155016_ = p_155016_.setValue(AbstractFurnaceBlock.LIT, Boolean.valueOf(p_155017_.isLit()));
            p_155014_.setBlock(p_155015_, p_155016_, 3);
        }

        if (flag1) {
            setChanged(p_155014_, p_155015_, p_155016_);
        }
    }

    private static boolean canBurn(RegistryAccess p_266924_, @Nullable RecipeHolder<?> p_301107_, NonNullList<ItemStack> p_155007_, int p_155008_, AbstractFurnaceBlockEntity furnace) {
        if (!p_155007_.get(0).isEmpty() && p_301107_ != null) {
            ItemStack itemstack = ((RecipeHolder<? extends AbstractCookingRecipe>) p_301107_).value().assemble(new SingleRecipeInput(furnace.getItem(0)), p_266924_);
            if (itemstack.isEmpty()) {
                return false;
            } else {
                ItemStack itemstack1 = p_155007_.get(2);
                if (itemstack1.isEmpty()) {
                    return true;
                } else if (!ItemStack.isSameItemSameComponents(itemstack1, itemstack)) {
                    return false;
                } else {
                    return itemstack1.getCount() + itemstack.getCount() <= p_155008_ && itemstack1.getCount() + itemstack.getCount() <= itemstack1.getMaxStackSize() // Neo fix: make furnace respect stack sizes in furnace recipes
                        ? true
                        : itemstack1.getCount() + itemstack.getCount() <= itemstack.getMaxStackSize(); // Neo fix: make furnace respect stack sizes in furnace recipes
                }
            }
        } else {
            return false;
        }
    }

    private static boolean burn(RegistryAccess p_266740_, @Nullable RecipeHolder<?> p_300910_, NonNullList<ItemStack> p_267073_, int p_267157_, AbstractFurnaceBlockEntity furnace) {
        if (p_300910_ != null && canBurn(p_266740_, p_300910_, p_267073_, p_267157_, furnace)) {
            ItemStack itemstack = p_267073_.get(0);
            ItemStack itemstack1 = ((RecipeHolder<? extends AbstractCookingRecipe>) p_300910_).value().assemble(new SingleRecipeInput(furnace.getItem(0)), p_266740_);
            ItemStack itemstack2 = p_267073_.get(2);
            if (itemstack2.isEmpty()) {
                p_267073_.set(2, itemstack1.copy());
            } else if (ItemStack.isSameItemSameComponents(itemstack2, itemstack1)) {
                itemstack2.grow(itemstack1.getCount());
            }

            if (itemstack.is(Blocks.WET_SPONGE.asItem()) && !p_267073_.get(1).isEmpty() && p_267073_.get(1).is(Items.BUCKET)) {
                p_267073_.set(1, new ItemStack(Items.WATER_BUCKET));
            }

            itemstack.shrink(1);
            return true;
        } else {
            return false;
        }
    }

    protected int getBurnDuration(ItemStack p_58343_) {
        if (p_58343_.isEmpty()) {
            return 0;
        } else {
            return p_58343_.getBurnTime(this.recipeType);
        }
    }

    private static int getTotalCookTime(Level p_222693_, AbstractFurnaceBlockEntity p_222694_) {
        SingleRecipeInput singlerecipeinput = new SingleRecipeInput(p_222694_.getItem(0));
        return p_222694_.quickCheck.getRecipeFor(singlerecipeinput, p_222693_).map(p_300840_ -> p_300840_.value().getCookingTime()).orElse(200);
    }

    public static boolean isFuel(ItemStack p_58400_) {
        return p_58400_.getBurnTime(null) > 0;
    }

    @Override
    public int[] getSlotsForFace(Direction p_58363_) {
        if (p_58363_ == Direction.DOWN) {
            return SLOTS_FOR_DOWN;
        } else {
            return p_58363_ == Direction.UP ? SLOTS_FOR_UP : SLOTS_FOR_SIDES;
        }
    }

    @Override
    public boolean canPlaceItemThroughFace(int p_58336_, ItemStack p_58337_, @Nullable Direction p_58338_) {
        return this.canPlaceItem(p_58336_, p_58337_);
    }

    @Override
    public boolean canTakeItemThroughFace(int p_58392_, ItemStack p_58393_, Direction p_58394_) {
        return p_58394_ == Direction.DOWN && p_58392_ == 1 ? p_58393_.is(Items.WATER_BUCKET) || p_58393_.is(Items.BUCKET) : true;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> p_332808_) {
        this.items = p_332808_;
    }

    @Override
    public void setItem(int p_58333_, ItemStack p_58334_) {
        ItemStack itemstack = this.items.get(p_58333_);
        boolean flag = !p_58334_.isEmpty() && ItemStack.isSameItemSameComponents(itemstack, p_58334_);
        this.items.set(p_58333_, p_58334_);
        p_58334_.limitSize(this.getMaxStackSize(p_58334_));
        if (p_58333_ == 0 && !flag) {
            this.cookingTotalTime = getTotalCookTime(this.level, this);
            this.cookingProgress = 0;
            this.setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int p_58389_, ItemStack p_58390_) {
        if (p_58389_ == 2) {
            return false;
        } else if (p_58389_ != 1) {
            return true;
        } else {
            ItemStack itemstack = this.items.get(1);
            return p_58390_.getBurnTime(this.recipeType) > 0 || p_58390_.is(Items.BUCKET) && !itemstack.is(Items.BUCKET);
        }
    }

    @Override
    public void setRecipeUsed(@Nullable RecipeHolder<?> p_301245_) {
        if (p_301245_ != null) {
            ResourceLocation resourcelocation = p_301245_.id();
            this.recipesUsed.addTo(resourcelocation, 1);
        }
    }

    @Nullable
    @Override
    public RecipeHolder<?> getRecipeUsed() {
        return null;
    }

    @Override
    public void awardUsedRecipes(Player p_58396_, List<ItemStack> p_282202_) {
    }

    public void awardUsedRecipesAndPopExperience(ServerPlayer p_155004_) {
        List<RecipeHolder<?>> list = this.getRecipesToAwardAndPopExperience(p_155004_.serverLevel(), p_155004_.position());
        p_155004_.awardRecipes(list);

        for (RecipeHolder<?> recipeholder : list) {
            if (recipeholder != null) {
                p_155004_.triggerRecipeCrafted(recipeholder, this.items);
            }
        }

        this.recipesUsed.clear();
    }

    public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(ServerLevel p_154996_, Vec3 p_154997_) {
        List<RecipeHolder<?>> list = Lists.newArrayList();

        for (Entry<ResourceLocation> entry : this.recipesUsed.object2IntEntrySet()) {
            p_154996_.getRecipeManager().byKey(entry.getKey()).ifPresent(p_300839_ -> {
                list.add((RecipeHolder<?>)p_300839_);
                createExperience(p_154996_, p_154997_, entry.getIntValue(), ((AbstractCookingRecipe)p_300839_.value()).getExperience());
            });
        }

        return list;
    }

    private static void createExperience(ServerLevel p_154999_, Vec3 p_155000_, int p_155001_, float p_155002_) {
        int i = Mth.floor((float)p_155001_ * p_155002_);
        float f = Mth.frac((float)p_155001_ * p_155002_);
        if (f != 0.0F && Math.random() < (double)f) {
            i++;
        }

        ExperienceOrb.award(p_154999_, p_155000_, i);
    }

    @Override
    public void fillStackedContents(StackedContents p_58342_) {
        for (ItemStack itemstack : this.items) {
            p_58342_.accountStack(itemstack);
        }
    }
}
