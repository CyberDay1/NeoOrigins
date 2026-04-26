package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public abstract class ProjectileWeaponItem extends Item {
    public static final Predicate<ItemStack> ARROW_ONLY = p_43017_ -> p_43017_.is(ItemTags.ARROWS);
    public static final Predicate<ItemStack> ARROW_OR_FIREWORK = ARROW_ONLY.or(p_43015_ -> p_43015_.is(Items.FIREWORK_ROCKET));

    public ProjectileWeaponItem(Item.Properties p_43009_) {
        super(p_43009_);
    }

    /**
     * @deprecated Use ItemStack sensitive version {@link ProjectileWeaponItem#getSupportedHeldProjectiles(ItemStack)}
     */
    @Deprecated
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return this.getAllSupportedProjectiles();
    }

    /**
     * @deprecated Use ItemStack sensitive version {@link ProjectileWeaponItem#getAllSupportedProjectiles(ItemStack)}
     */
    @Deprecated
    public abstract Predicate<ItemStack> getAllSupportedProjectiles();

    /**
     * Override this method if the weapon stack allows special projectile that would only be used if it's in hand.
     * The default return value is a union-predicate of {@link ProjectileWeaponItem#getAllSupportedProjectiles(ItemStack)}
     * and {@link ProjectileWeaponItem#getSupportedHeldProjectiles()}
     *
     * @param stack The ProjectileWeapon stack
     * @return A predicate that returns true for supported projectile stack in hand
     */
    public Predicate<ItemStack> getSupportedHeldProjectiles(ItemStack stack) {
        return getAllSupportedProjectiles(stack).or(getSupportedHeldProjectiles());
    }

    /**
     * Override this method if the allowed projectile is weapon stack dependent.
     *
     * @param stack The ProjectileWeapon stack
     * @return A predicate that returns true for all supported projectile stack
     */
    public Predicate<ItemStack> getAllSupportedProjectiles(ItemStack stack) {
        return getAllSupportedProjectiles();
    }

    public static ItemStack getHeldProjectile(LivingEntity p_43011_, Predicate<ItemStack> p_43012_) {
        if (p_43012_.test(p_43011_.getItemInHand(InteractionHand.OFF_HAND))) {
            return p_43011_.getItemInHand(InteractionHand.OFF_HAND);
        } else {
            return p_43012_.test(p_43011_.getItemInHand(InteractionHand.MAIN_HAND)) ? p_43011_.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY;
        }
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    public abstract int getDefaultProjectileRange();

    protected void shoot(
        ServerLevel p_346125_,
        LivingEntity p_330728_,
        InteractionHand p_331152_,
        ItemStack p_330646_,
        List<ItemStack> p_331726_,
        float p_331007_,
        float p_331445_,
        boolean p_331107_,
        @Nullable LivingEntity p_331167_
    ) {
        float f = EnchantmentHelper.processProjectileSpread(p_346125_, p_330646_, p_330728_, 0.0F);
        float f1 = p_331726_.size() == 1 ? 0.0F : 2.0F * f / (float)(p_331726_.size() - 1);
        float f2 = (float)((p_331726_.size() - 1) % 2) * f1 / 2.0F;
        float f3 = 1.0F;

        for (int i = 0; i < p_331726_.size(); i++) {
            ItemStack itemstack = p_331726_.get(i);
            if (!itemstack.isEmpty()) {
                float f4 = f2 + f3 * (float)((i + 1) / 2) * f1;
                f3 = -f3;
                Projectile projectile = this.createProjectile(p_346125_, p_330728_, p_330646_, itemstack, p_331107_);
                this.shootProjectile(p_330728_, projectile, i, p_331007_, p_331445_, f4, p_331167_);
                p_346125_.addFreshEntity(projectile);
                p_330646_.hurtAndBreak(this.getDurabilityUse(itemstack), p_330728_, LivingEntity.getSlotForHand(p_331152_));
                if (p_330646_.isEmpty()) {
                    break;
                }
            }
        }
    }

    protected int getDurabilityUse(ItemStack p_331003_) {
        return 1;
    }

    protected abstract void shootProjectile(
        LivingEntity p_330966_, Projectile p_332201_, int p_331696_, float p_331444_, float p_331156_, float p_331718_, @Nullable LivingEntity p_331705_
    );

    protected Projectile createProjectile(Level p_331008_, LivingEntity p_330781_, ItemStack p_330846_, ItemStack p_331497_, boolean p_331305_) {
        ArrowItem arrowitem = p_331497_.getItem() instanceof ArrowItem arrowitem1 ? arrowitem1 : (ArrowItem)Items.ARROW;
        AbstractArrow abstractarrow = arrowitem.createArrow(p_331008_, p_331497_, p_330781_, p_330846_);
        if (p_331305_) {
            abstractarrow.setCritArrow(true);
        }

        return customArrow(abstractarrow, p_331497_, p_330846_);
    }

    protected static List<ItemStack> draw(ItemStack p_331565_, ItemStack p_330406_, LivingEntity p_330823_) {
        if (p_330406_.isEmpty()) {
            return List.of();
        } else {
            int i = p_330823_.level() instanceof ServerLevel serverlevel ? EnchantmentHelper.processProjectileCount(serverlevel, p_331565_, p_330823_, 1) : 1;
            List<ItemStack> list = new ArrayList<>(i);
            ItemStack itemstack1 = p_330406_.copy();

            for (int j = 0; j < i; j++) {
                ItemStack itemstack = useAmmo(p_331565_, j == 0 ? p_330406_ : itemstack1, p_330823_, j > 0);
                if (!itemstack.isEmpty()) {
                    list.add(itemstack);
                }
            }

            return list;
        }
    }

    protected static ItemStack useAmmo(ItemStack p_331207_, ItemStack p_331434_, LivingEntity p_330302_, boolean p_330934_) {
        // Neo: Adjust this check to respect ArrowItem#isInfinite, bypassing processAmmoUse if true.
        int i = !p_330934_ && p_330302_.level() instanceof ServerLevel serverlevel && !(p_330302_.hasInfiniteMaterials() || (p_331434_.getItem() instanceof ArrowItem ai && ai.isInfinite(p_331434_, p_331207_, p_330302_)))
            ? EnchantmentHelper.processAmmoUse(serverlevel, p_331207_, p_331434_, 1)
            : 0;
        if (i > p_331434_.getCount()) {
            return ItemStack.EMPTY;
        } else if (i == 0) {
            ItemStack itemstack1 = p_331434_.copyWithCount(1);
            itemstack1.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            return itemstack1;
        } else {
            ItemStack itemstack = p_331434_.split(i);
            if (p_331434_.isEmpty() && p_330302_ instanceof Player player) {
                player.getInventory().removeItem(p_331434_);
            }

            return itemstack;
        }
    }

    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        return arrow;
    }

    /**
     * Neo: Controls what ammo ItemStack that Creative Mode should return if the player has no valid ammo in inventory.
     * Modded weapons should override this to return their own ammo if they do not use vanilla arrows.
     * @param player The player (if in context) firing the weapon
     * @param projectileWeaponItem The weapon ItemStack the ammo is for
     * @return The default ammo ItemStack for this weapon
     */
    public ItemStack getDefaultCreativeAmmo(@Nullable Player player, ItemStack projectileWeaponItem) {
        return Items.ARROW.getDefaultInstance();
    }
}
