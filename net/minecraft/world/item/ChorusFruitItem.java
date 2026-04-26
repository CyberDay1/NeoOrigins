package net.minecraft.world.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class ChorusFruitItem extends Item {
    public ChorusFruitItem(Item.Properties p_40710_) {
        super(p_40710_);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack p_40712_, Level p_40713_, LivingEntity p_40714_) {
        ItemStack itemstack = super.finishUsingItem(p_40712_, p_40713_, p_40714_);
        if (!p_40713_.isClientSide) {
            for (int i = 0; i < 16; i++) {
                double d0 = p_40714_.getX() + (p_40714_.getRandom().nextDouble() - 0.5) * 16.0;
                double d1 = Mth.clamp(
                    p_40714_.getY() + (double)(p_40714_.getRandom().nextInt(16) - 8),
                    (double)p_40713_.getMinBuildHeight(),
                    (double)(p_40713_.getMinBuildHeight() + ((ServerLevel)p_40713_).getLogicalHeight() - 1)
                );
                double d2 = p_40714_.getZ() + (p_40714_.getRandom().nextDouble() - 0.5) * 16.0;
                if (p_40714_.isPassenger()) {
                    p_40714_.stopRiding();
                }

                Vec3 vec3 = p_40714_.position();
                net.neoforged.neoforge.event.entity.EntityTeleportEvent.ChorusFruit event = net.neoforged.neoforge.event.EventHooks.onChorusFruitTeleport(p_40714_, d0, d1, d2);
                if (event.isCanceled()) return itemstack;
                if (p_40714_.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true)) {
                    p_40713_.gameEvent(GameEvent.TELEPORT, vec3, GameEvent.Context.of(p_40714_));
                    SoundSource soundsource;
                    SoundEvent soundevent;
                    if (p_40714_ instanceof Fox) {
                        soundevent = SoundEvents.FOX_TELEPORT;
                        soundsource = SoundSource.NEUTRAL;
                    } else {
                        soundevent = SoundEvents.CHORUS_FRUIT_TELEPORT;
                        soundsource = SoundSource.PLAYERS;
                    }

                    p_40713_.playSound(null, p_40714_.getX(), p_40714_.getY(), p_40714_.getZ(), soundevent, soundsource);
                    p_40714_.resetFallDistance();
                    break;
                }
            }

            if (p_40714_ instanceof Player player) {
                player.resetCurrentImpulseContext();
                player.getCooldowns().addCooldown(this, 20);
            }
        }

        return itemstack;
    }
}
