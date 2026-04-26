package net.minecraft.world.inventory;

import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SmithingMenu extends ItemCombinerMenu {
    public static final int TEMPLATE_SLOT = 0;
    public static final int BASE_SLOT = 1;
    public static final int ADDITIONAL_SLOT = 2;
    public static final int RESULT_SLOT = 3;
    public static final int TEMPLATE_SLOT_X_PLACEMENT = 8;
    public static final int BASE_SLOT_X_PLACEMENT = 26;
    public static final int ADDITIONAL_SLOT_X_PLACEMENT = 44;
    private static final int RESULT_SLOT_X_PLACEMENT = 98;
    public static final int SLOT_Y_PLACEMENT = 48;
    private final Level level;
    @Nullable
    private RecipeHolder<SmithingRecipe> selectedRecipe;
    private final List<RecipeHolder<SmithingRecipe>> recipes;

    public SmithingMenu(int p_40245_, Inventory p_40246_) {
        this(p_40245_, p_40246_, ContainerLevelAccess.NULL);
    }

    public SmithingMenu(int p_40248_, Inventory p_40249_, ContainerLevelAccess p_40250_) {
        super(MenuType.SMITHING, p_40248_, p_40249_, p_40250_);
        this.level = p_40249_.player.level();
        this.recipes = this.level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING);
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create()
            .withSlot(0, 8, 48, p_266643_ -> this.recipes.stream().anyMatch(p_300804_ -> p_300804_.value().isTemplateIngredient(p_266643_)))
            .withSlot(1, 26, 48, p_286208_ -> this.recipes.stream().anyMatch(p_300802_ -> p_300802_.value().isBaseIngredient(p_286208_)))
            .withSlot(2, 44, 48, p_286207_ -> this.recipes.stream().anyMatch(p_300798_ -> p_300798_.value().isAdditionIngredient(p_286207_)))
            .withResultSlot(3, 98, 48)
            .build();
    }

    @Override
    protected boolean isValidBlock(BlockState p_40266_) {
        return p_40266_.is(Blocks.SMITHING_TABLE);
    }

    @Override
    protected boolean mayPickup(Player p_40268_, boolean p_40269_) {
        return this.selectedRecipe != null && this.selectedRecipe.value().matches(this.createRecipeInput(), this.level);
    }

    @Override
    protected void onTake(Player p_150663_, ItemStack p_150664_) {
        p_150664_.onCraftedBy(p_150663_.level(), p_150663_, p_150664_.getCount());
        this.resultSlots.awardUsedRecipes(p_150663_, this.getRelevantItems());
        this.shrinkStackInSlot(0);
        this.shrinkStackInSlot(1);
        this.shrinkStackInSlot(2);
        this.access.execute((p_40263_, p_40264_) -> p_40263_.levelEvent(1044, p_40264_, 0));
    }

    private List<ItemStack> getRelevantItems() {
        return List.of(this.inputSlots.getItem(0), this.inputSlots.getItem(1), this.inputSlots.getItem(2));
    }

    private SmithingRecipeInput createRecipeInput() {
        return new SmithingRecipeInput(this.inputSlots.getItem(0), this.inputSlots.getItem(1), this.inputSlots.getItem(2));
    }

    private void shrinkStackInSlot(int p_40271_) {
        ItemStack itemstack = this.inputSlots.getItem(p_40271_);
        if (!itemstack.isEmpty()) {
            itemstack.shrink(1);
            this.inputSlots.setItem(p_40271_, itemstack);
        }
    }

    @Override
    public void createResult() {
        SmithingRecipeInput smithingrecipeinput = this.createRecipeInput();
        List<RecipeHolder<SmithingRecipe>> list = this.level.getRecipeManager().getRecipesFor(RecipeType.SMITHING, smithingrecipeinput, this.level);
        if (list.isEmpty()) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
        } else {
            RecipeHolder<SmithingRecipe> recipeholder = list.get(0);
            ItemStack itemstack = recipeholder.value().assemble(smithingrecipeinput, this.level.registryAccess());
            if (itemstack.isItemEnabled(this.level.enabledFeatures())) {
                this.selectedRecipe = recipeholder;
                this.resultSlots.setRecipeUsed(recipeholder);
                this.resultSlots.setItem(0, itemstack);
            }
        }
    }

    @Override
    public int getSlotToQuickMoveTo(ItemStack p_266739_) {
        return this.findSlotToQuickMoveTo(p_266739_).orElse(0);
    }

    private static OptionalInt findSlotMatchingIngredient(SmithingRecipe p_266790_, ItemStack p_266818_) {
        if (p_266790_.isTemplateIngredient(p_266818_)) {
            return OptionalInt.of(0);
        } else if (p_266790_.isBaseIngredient(p_266818_)) {
            return OptionalInt.of(1);
        } else {
            return p_266790_.isAdditionIngredient(p_266818_) ? OptionalInt.of(2) : OptionalInt.empty();
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack p_40257_, Slot p_40258_) {
        return p_40258_.container != this.resultSlots && super.canTakeItemForPickAll(p_40257_, p_40258_);
    }

    @Override
    public boolean canMoveIntoInputSlots(ItemStack p_266846_) {
        return this.findSlotToQuickMoveTo(p_266846_).isPresent();
    }

    private OptionalInt findSlotToQuickMoveTo(ItemStack p_295440_) {
        return this.recipes
            .stream()
            .flatMapToInt(p_300800_ -> findSlotMatchingIngredient(p_300800_.value(), p_295440_).stream())
            .filter(p_294045_ -> !this.getSlot(p_294045_).hasItem())
            .findFirst();
    }
}
