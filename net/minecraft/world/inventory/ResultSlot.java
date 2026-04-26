package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

public class ResultSlot extends Slot {
    private final CraftingContainer craftSlots;
    private final Player player;
    private int removeCount;

    public ResultSlot(Player p_40166_, CraftingContainer p_40167_, Container p_40168_, int p_40169_, int p_40170_, int p_40171_) {
        super(p_40168_, p_40169_, p_40170_, p_40171_);
        this.player = p_40166_;
        this.craftSlots = p_40167_;
    }

    @Override
    public boolean mayPlace(ItemStack p_40178_) {
        return false;
    }

    @Override
    public ItemStack remove(int p_40173_) {
        if (this.hasItem()) {
            this.removeCount = this.removeCount + Math.min(p_40173_, this.getItem().getCount());
        }

        return super.remove(p_40173_);
    }

    @Override
    protected void onQuickCraft(ItemStack p_40180_, int p_40181_) {
        this.removeCount += p_40181_;
        this.checkTakeAchievements(p_40180_);
    }

    @Override
    protected void onSwapCraft(int p_40183_) {
        this.removeCount += p_40183_;
    }

    @Override
    protected void checkTakeAchievements(ItemStack p_40185_) {
        if (this.removeCount > 0) {
            p_40185_.onCraftedBy(this.player.level(), this.player, this.removeCount);
            net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(this.player, p_40185_, this.craftSlots);
        }

        if (this.container instanceof RecipeCraftingHolder recipecraftingholder) {
            recipecraftingholder.awardUsedRecipes(this.player, this.craftSlots.getItems());
        }

        this.removeCount = 0;
    }

    @Override
    public void onTake(Player p_150638_, ItemStack p_150639_) {
        this.checkTakeAchievements(p_150639_);
        CraftingInput.Positioned craftinginput$positioned = this.craftSlots.asPositionedCraftInput();
        CraftingInput craftinginput = craftinginput$positioned.input();
        int i = craftinginput$positioned.left();
        int j = craftinginput$positioned.top();
        net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(p_150638_);
        NonNullList<ItemStack> nonnulllist = p_150638_.level().getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, craftinginput, p_150638_.level());
        net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(null);

        for (int k = 0; k < craftinginput.height(); k++) {
            for (int l = 0; l < craftinginput.width(); l++) {
                int i1 = l + i + (k + j) * this.craftSlots.getWidth();
                ItemStack itemstack = this.craftSlots.getItem(i1);
                ItemStack itemstack1 = nonnulllist.get(l + k * craftinginput.width());
                if (!itemstack.isEmpty()) {
                    this.craftSlots.removeItem(i1, 1);
                    itemstack = this.craftSlots.getItem(i1);
                }

                if (!itemstack1.isEmpty()) {
                    if (itemstack.isEmpty()) {
                        this.craftSlots.setItem(i1, itemstack1);
                    } else if (ItemStack.isSameItemSameComponents(itemstack, itemstack1)) {
                        itemstack1.grow(itemstack.getCount());
                        this.craftSlots.setItem(i1, itemstack1);
                    } else if (!this.player.getInventory().add(itemstack1)) {
                        this.player.drop(itemstack1, false);
                    }
                }
            }
        }
    }

    @Override
    public boolean isFake() {
        return true;
    }
}
