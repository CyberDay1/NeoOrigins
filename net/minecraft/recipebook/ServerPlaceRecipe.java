package net.minecraft.recipebook;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

public class ServerPlaceRecipe<I extends RecipeInput, R extends Recipe<I>> implements PlaceRecipe<Integer> {
    private static final int ITEM_NOT_FOUND = -1;
    protected final StackedContents stackedContents = new StackedContents();
    protected Inventory inventory;
    protected RecipeBookMenu<I, R> menu;

    public ServerPlaceRecipe(RecipeBookMenu<I, R> p_135431_) {
        this.menu = p_135431_;
    }

    public void recipeClicked(ServerPlayer p_135435_, @Nullable RecipeHolder<R> p_301150_, boolean p_135437_) {
        if (p_301150_ != null && p_135435_.getRecipeBook().contains(p_301150_)) {
            this.inventory = p_135435_.getInventory();
            if (this.testClearGrid() || p_135435_.isCreative()) {
                this.stackedContents.clear();
                p_135435_.getInventory().fillStackedContents(this.stackedContents);
                this.menu.fillCraftSlotsStackedContents(this.stackedContents);
                if (this.stackedContents.canCraft(p_301150_.value(), null)) {
                    this.handleRecipeClicked(p_301150_, p_135437_);
                } else {
                    this.clearGrid();
                    p_135435_.connection.send(new ClientboundPlaceGhostRecipePacket(p_135435_.containerMenu.containerId, p_301150_));
                }

                p_135435_.getInventory().setChanged();
            }
        }
    }

    protected void clearGrid() {
        for (int i = 0; i < this.menu.getSize(); i++) {
            if (this.menu.shouldMoveToInventory(i)) {
                ItemStack itemstack = this.menu.getSlot(i).getItem().copy();
                this.inventory.placeItemBackInInventory(itemstack, false);
                this.menu.getSlot(i).set(itemstack);
            }
        }

        this.menu.clearCraftingContent();
    }

    protected void handleRecipeClicked(RecipeHolder<R> p_301187_, boolean p_135442_) {
        boolean flag = this.menu.recipeMatches(p_301187_);
        int i = this.stackedContents.getBiggestCraftableStack(p_301187_, null);
        if (flag) {
            for (int j = 0; j < this.menu.getGridHeight() * this.menu.getGridWidth() + 1; j++) {
                if (j != this.menu.getResultSlotIndex()) {
                    ItemStack itemstack = this.menu.getSlot(j).getItem();
                    if (!itemstack.isEmpty() && Math.min(i, itemstack.getMaxStackSize()) < itemstack.getCount() + 1) {
                        return;
                    }
                }
            }
        }

        int j1 = this.getStackSize(p_135442_, i, flag);
        IntList intlist = new IntArrayList();
        if (this.stackedContents.canCraft(p_301187_.value(), intlist, j1)) {
            int k = j1;

            for (int l : intlist) {
                ItemStack itemstack1 = StackedContents.fromStackingIndex(l);
                if (!itemstack1.isEmpty()) {
                    int i1 = itemstack1.getMaxStackSize();
                    if (i1 < k) {
                        k = i1;
                    }
                }
            }

            if (this.stackedContents.canCraft(p_301187_.value(), intlist, k)) {
                this.clearGrid();
                this.placeRecipe(this.menu.getGridWidth(), this.menu.getGridHeight(), this.menu.getResultSlotIndex(), p_301187_, intlist.iterator(), k);
            }
        }
    }

    public void addItemToSlot(Integer p_346390_, int p_346229_, int p_345733_, int p_345812_, int p_346351_) {
        Slot slot = this.menu.getSlot(p_346229_);
        ItemStack itemstack = StackedContents.fromStackingIndex(p_346390_);
        if (!itemstack.isEmpty()) {
            int i = p_345733_;

            while (i > 0) {
                i = this.moveItemToGrid(slot, itemstack, i);
                if (i == -1) {
                    return;
                }
            }
        }
    }

    protected int getStackSize(boolean p_135450_, int p_135451_, boolean p_135452_) {
        int i = 1;
        if (p_135450_) {
            i = p_135451_;
        } else if (p_135452_) {
            i = Integer.MAX_VALUE;

            for (int j = 0; j < this.menu.getGridWidth() * this.menu.getGridHeight() + 1; j++) {
                if (j != this.menu.getResultSlotIndex()) {
                    ItemStack itemstack = this.menu.getSlot(j).getItem();
                    if (!itemstack.isEmpty() && i > itemstack.getCount()) {
                        i = itemstack.getCount();
                    }
                }
            }

            if (i != Integer.MAX_VALUE) {
                i++;
            }
        }

        return i;
    }

    protected int moveItemToGrid(Slot p_135439_, ItemStack p_135440_, int p_346157_) {
        int i = this.inventory.findSlotMatchingUnusedItem(p_135440_);
        if (i == -1) {
            return -1;
        } else {
            ItemStack itemstack = this.inventory.getItem(i);
            int j;
            if (p_346157_ < itemstack.getCount()) {
                this.inventory.removeItem(i, p_346157_);
                j = p_346157_;
            } else {
                this.inventory.removeItemNoUpdate(i);
                j = itemstack.getCount();
            }

            if (p_135439_.getItem().isEmpty()) {
                p_135439_.set(itemstack.copyWithCount(j));
            } else {
                p_135439_.getItem().grow(j);
            }

            return p_346157_ - j;
        }
    }

    private boolean testClearGrid() {
        List<ItemStack> list = Lists.newArrayList();
        int i = this.getAmountOfFreeSlotsInInventory();

        for (int j = 0; j < this.menu.getGridWidth() * this.menu.getGridHeight() + 1; j++) {
            if (j != this.menu.getResultSlotIndex()) {
                ItemStack itemstack = this.menu.getSlot(j).getItem().copy();
                if (!itemstack.isEmpty()) {
                    int k = this.inventory.getSlotWithRemainingSpace(itemstack);
                    if (k == -1 && list.size() <= i) {
                        for (ItemStack itemstack1 : list) {
                            if (ItemStack.isSameItem(itemstack1, itemstack)
                                && itemstack1.getCount() != itemstack1.getMaxStackSize()
                                && itemstack1.getCount() + itemstack.getCount() <= itemstack1.getMaxStackSize()) {
                                itemstack1.grow(itemstack.getCount());
                                itemstack.setCount(0);
                                break;
                            }
                        }

                        if (!itemstack.isEmpty()) {
                            if (list.size() >= i) {
                                return false;
                            }

                            list.add(itemstack);
                        }
                    } else if (k == -1) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private int getAmountOfFreeSlotsInInventory() {
        int i = 0;

        for (ItemStack itemstack : this.inventory.items) {
            if (itemstack.isEmpty()) {
                i++;
            }
        }

        return i;
    }
}
