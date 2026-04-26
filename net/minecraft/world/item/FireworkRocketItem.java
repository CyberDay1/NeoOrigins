package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class FireworkRocketItem extends Item implements ProjectileItem {
    public static final byte[] CRAFTABLE_DURATIONS = new byte[]{1, 2, 3};
    public static final double ROCKET_PLACEMENT_OFFSET = 0.15;

    public FireworkRocketItem(Item.Properties p_41209_) {
        super(p_41209_);
    }

    @Override
    public InteractionResult useOn(UseOnContext p_41216_) {
        Level level = p_41216_.getLevel();
        if (!level.isClientSide) {
            ItemStack itemstack = p_41216_.getItemInHand();
            Vec3 vec3 = p_41216_.getClickLocation();
            Direction direction = p_41216_.getClickedFace();
            FireworkRocketEntity fireworkrocketentity = new FireworkRocketEntity(
                level,
                p_41216_.getPlayer(),
                vec3.x + (double)direction.getStepX() * 0.15,
                vec3.y + (double)direction.getStepY() * 0.15,
                vec3.z + (double)direction.getStepZ() * 0.15,
                itemstack
            );
            level.addFreshEntity(fireworkrocketentity);
            itemstack.shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_41218_, Player p_41219_, InteractionHand p_41220_) {
        if (p_41219_.isFallFlying()) {
            ItemStack itemstack = p_41219_.getItemInHand(p_41220_);
            if (!p_41218_.isClientSide) {
                FireworkRocketEntity fireworkrocketentity = new FireworkRocketEntity(p_41218_, itemstack, p_41219_);
                p_41218_.addFreshEntity(fireworkrocketentity);
                itemstack.consume(1, p_41219_);
                p_41219_.awardStat(Stats.ITEM_USED.get(this));
            }

            return InteractionResultHolder.sidedSuccess(p_41219_.getItemInHand(p_41220_), p_41218_.isClientSide());
        } else {
            return InteractionResultHolder.pass(p_41219_.getItemInHand(p_41220_));
        }
    }

    @Override
    public void appendHoverText(ItemStack p_41211_, Item.TooltipContext p_339661_, List<Component> p_41213_, TooltipFlag p_41214_) {
        Fireworks fireworks = p_41211_.get(DataComponents.FIREWORKS);
        if (fireworks != null) {
            fireworks.addToTooltip(p_339661_, p_41213_::add, p_41214_);
        }
    }

    @Override
    public Projectile asProjectile(Level p_338390_, Position p_338574_, ItemStack p_338487_, Direction p_338368_) {
        return new FireworkRocketEntity(p_338390_, p_338487_.copyWithCount(1), p_338574_.x(), p_338574_.y(), p_338574_.z(), true);
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .positionFunction(FireworkRocketItem::getEntityPokingOutOfBlockPos)
            .uncertainty(1.0F)
            .power(0.5F)
            .overrideDispenseEvent(1004)
            .build();
    }

    private static Vec3 getEntityPokingOutOfBlockPos(BlockSource p_338806_, Direction p_338706_) {
        return p_338806_.center()
            .add(
                (double)p_338706_.getStepX() * (0.5000099999997474 - (double)EntityType.FIREWORK_ROCKET.getWidth() / 2.0),
                (double)p_338706_.getStepY() * (0.5000099999997474 - (double)EntityType.FIREWORK_ROCKET.getHeight() / 2.0)
                    - (double)EntityType.FIREWORK_ROCKET.getHeight() / 2.0,
                (double)p_338706_.getStepZ() * (0.5000099999997474 - (double)EntityType.FIREWORK_ROCKET.getWidth() / 2.0)
            );
    }
}
