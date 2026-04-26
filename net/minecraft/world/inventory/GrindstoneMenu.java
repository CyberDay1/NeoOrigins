package net.minecraft.world.inventory;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class GrindstoneMenu extends AbstractContainerMenu {
    public static final int MAX_NAME_LENGTH = 35;
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private final Container resultSlots = new ResultContainer();
    final Container repairSlots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            GrindstoneMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    private int xp = -1;

    public GrindstoneMenu(int p_39563_, Inventory p_39564_) {
        this(p_39563_, p_39564_, ContainerLevelAccess.NULL);
    }

    public GrindstoneMenu(int p_39566_, Inventory p_39567_, final ContainerLevelAccess p_39568_) {
        super(MenuType.GRINDSTONE, p_39566_);
        this.access = p_39568_;
        this.addSlot(new Slot(this.repairSlots, 0, 49, 19) {
            @Override
            public boolean mayPlace(ItemStack p_39607_) {
                return p_39607_.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(p_39607_) || p_39607_.canGrindstoneRepair();
            }
        });
        this.addSlot(new Slot(this.repairSlots, 1, 49, 40) {
            @Override
            public boolean mayPlace(ItemStack p_39616_) {
                return p_39616_.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(p_39616_) || p_39616_.canGrindstoneRepair();
            }
        });
        this.addSlot(new Slot(this.resultSlots, 2, 129, 34) {
            @Override
            public boolean mayPlace(ItemStack p_39630_) {
                return false;
            }

            @Override
            public void onTake(Player p_150574_, ItemStack p_150575_) {
                if (net.neoforged.neoforge.common.CommonHooks.onGrindstoneTake(GrindstoneMenu.this.repairSlots, p_39568_, p_150574_, this::getExperienceAmount)) return;
                p_39568_.execute((p_39634_, p_39635_) -> {
                    if (p_39634_ instanceof ServerLevel) {
                        ExperienceOrb.award((ServerLevel)p_39634_, Vec3.atCenterOf(p_39635_), this.getExperienceAmount(p_39634_));
                    }

                    p_39634_.levelEvent(1042, p_39635_, 0);
                });
                GrindstoneMenu.this.repairSlots.setItem(0, ItemStack.EMPTY);
                GrindstoneMenu.this.repairSlots.setItem(1, ItemStack.EMPTY);
            }

            private int getExperienceAmount(Level p_39632_) {
                if (xp > -1) return xp;
                int l = 0;
                l += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(0));
                l += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(1));
                if (l > 0) {
                    int i1 = (int)Math.ceil((double)l / 2.0);
                    return i1 + p_39632_.random.nextInt(i1);
                } else {
                    return 0;
                }
            }

            private int getExperienceFromItem(ItemStack p_39637_) {
                int l = 0;
                ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(p_39637_);

                for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    int i1 = entry.getIntValue();
                    if (!holder.is(EnchantmentTags.CURSE)) {
                        l += holder.value().getMinCost(i1);
                    }
                }

                return l;
            }
        });

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(p_39567_, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (int k = 0; k < 9; k++) {
            this.addSlot(new Slot(p_39567_, k, 8 + k * 18, 142));
        }
    }

    @Override
    public void slotsChanged(Container p_39570_) {
        super.slotsChanged(p_39570_);
        if (p_39570_ == this.repairSlots) {
            this.createResult();
        }
    }

    private void createResult() {
        this.xp = net.neoforged.neoforge.common.CommonHooks.onGrindstoneChange(this.repairSlots.getItem(0), this.repairSlots.getItem(1), this.resultSlots, -1);
        if (this.xp == Integer.MIN_VALUE)
        this.resultSlots.setItem(0, this.computeResult(this.repairSlots.getItem(0), this.repairSlots.getItem(1)));
        this.broadcastChanges();
    }

    private ItemStack computeResult(ItemStack p_332654_, ItemStack p_332736_) {
        boolean flag = !p_332654_.isEmpty() || !p_332736_.isEmpty();
        if (!flag) {
            return ItemStack.EMPTY;
        } else if (p_332654_.getCount() <= 1 && p_332736_.getCount() <= 1) {
            boolean flag1 = !p_332654_.isEmpty() && !p_332736_.isEmpty();
            if (!flag1) {
                ItemStack itemstack = !p_332654_.isEmpty() ? p_332654_ : p_332736_;
                return !EnchantmentHelper.hasAnyEnchantments(itemstack) ? ItemStack.EMPTY : this.removeNonCursesFrom(itemstack.copy());
            } else {
                return this.mergeItems(p_332654_, p_332736_);
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    private ItemStack mergeItems(ItemStack p_332723_, ItemStack p_332686_) {
        if (!p_332723_.is(p_332686_.getItem())) {
            return ItemStack.EMPTY;
        } else {
            int i = Math.max(p_332723_.getMaxDamage(), p_332686_.getMaxDamage());
            int j = p_332723_.getMaxDamage() - p_332723_.getDamageValue();
            int k = p_332686_.getMaxDamage() - p_332686_.getDamageValue();
            int l = j + k + i * 5 / 100;
            int i1 = 1;
            if (!p_332723_.isDamageableItem() || !p_332723_.isRepairable()) {
                if (p_332723_.getMaxStackSize() < 2 || !ItemStack.matches(p_332723_, p_332686_)) {
                    return ItemStack.EMPTY;
                }

                i1 = 2;
            }

            ItemStack itemstack = p_332723_.copyWithCount(i1);
            if (itemstack.isDamageableItem()) {
                itemstack.set(DataComponents.MAX_DAMAGE, i);
                itemstack.setDamageValue(Math.max(i - l, 0));
                if (!p_332686_.isRepairable()) itemstack.setDamageValue(p_332723_.getDamageValue());
            }

            this.mergeEnchantsFrom(itemstack, p_332686_);
            return this.removeNonCursesFrom(itemstack);
        }
    }

    private void mergeEnchantsFrom(ItemStack p_332680_, ItemStack p_332800_) {
        EnchantmentHelper.updateEnchantments(p_332680_, p_344370_ -> {
            ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(p_332800_);

            for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                if (!holder.is(EnchantmentTags.CURSE) || p_344370_.getLevel(holder) == 0) {
                    p_344370_.upgrade(holder, entry.getIntValue());
                }
            }
        });
    }

    private ItemStack removeNonCursesFrom(ItemStack p_332709_) {
        ItemEnchantments itemenchantments = EnchantmentHelper.updateEnchantments(
            p_332709_, p_330066_ -> p_330066_.removeIf(p_344368_ -> !p_344368_.is(EnchantmentTags.CURSE))
        );
        if (p_332709_.is(Items.ENCHANTED_BOOK) && itemenchantments.isEmpty()) {
            p_332709_ = p_332709_.transmuteCopy(Items.BOOK);
        }

        int i = 0;

        for (int j = 0; j < itemenchantments.size(); j++) {
            i = AnvilMenu.calculateIncreasedRepairCost(i);
        }

        p_332709_.set(DataComponents.REPAIR_COST, i);
        return p_332709_;
    }

    @Override
    public void removed(Player p_39586_) {
        super.removed(p_39586_);
        this.access.execute((p_39575_, p_39576_) -> this.clearContainer(p_39586_, this.repairSlots));
    }

    @Override
    public boolean stillValid(Player p_39572_) {
        return stillValid(this.access, p_39572_, Blocks.GRINDSTONE);
    }

    @Override
    public ItemStack quickMoveStack(Player p_39588_, int p_39589_) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(p_39589_);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            ItemStack itemstack2 = this.repairSlots.getItem(0);
            ItemStack itemstack3 = this.repairSlots.getItem(1);
            if (p_39589_ == 2) {
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (p_39589_ != 0 && p_39589_ != 1) {
                if (!itemstack2.isEmpty() && !itemstack3.isEmpty()) {
                    if (p_39589_ >= 3 && p_39589_ < 30) {
                        if (!this.moveItemStackTo(itemstack1, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (p_39589_ >= 30 && p_39589_ < 39 && !this.moveItemStackTo(itemstack1, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(p_39588_, itemstack1);
        }

        return itemstack;
    }
}
