package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class EnchantingTableBlockEntity extends BlockEntity implements Nameable {
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable
    private Component name;

    public EnchantingTableBlockEntity(BlockPos p_340972_, BlockState p_340871_) {
        super(BlockEntityType.ENCHANTING_TABLE, p_340972_, p_340871_);
    }

    @Override
    protected void saveAdditional(CompoundTag p_341088_, HolderLookup.Provider p_341376_) {
        super.saveAdditional(p_341088_, p_341376_);
        if (this.hasCustomName()) {
            p_341088_.putString("CustomName", Component.Serializer.toJson(this.name, p_341376_));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag p_341199_, HolderLookup.Provider p_341063_) {
        super.loadAdditional(p_341199_, p_341063_);
        if (p_341199_.contains("CustomName", 8)) {
            this.name = parseCustomNameSafe(p_341199_.getString("CustomName"), p_341063_);
        }
    }

    public static void bookAnimationTick(Level p_341315_, BlockPos p_341271_, BlockState p_341158_, EnchantingTableBlockEntity p_341066_) {
        p_341066_.oOpen = p_341066_.open;
        p_341066_.oRot = p_341066_.rot;
        Player player = p_341315_.getNearestPlayer((double)p_341271_.getX() + 0.5, (double)p_341271_.getY() + 0.5, (double)p_341271_.getZ() + 0.5, 3.0, false);
        if (player != null) {
            double d0 = player.getX() - ((double)p_341271_.getX() + 0.5);
            double d1 = player.getZ() - ((double)p_341271_.getZ() + 0.5);
            p_341066_.tRot = (float)Mth.atan2(d1, d0);
            p_341066_.open += 0.1F;
            if (p_341066_.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float f1 = p_341066_.flipT;

                do {
                    p_341066_.flipT = p_341066_.flipT + (float)(RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while (f1 == p_341066_.flipT);
            }
        } else {
            p_341066_.tRot += 0.02F;
            p_341066_.open -= 0.1F;
        }

        while (p_341066_.rot >= (float) Math.PI) {
            p_341066_.rot -= (float) (Math.PI * 2);
        }

        while (p_341066_.rot < (float) -Math.PI) {
            p_341066_.rot += (float) (Math.PI * 2);
        }

        while (p_341066_.tRot >= (float) Math.PI) {
            p_341066_.tRot -= (float) (Math.PI * 2);
        }

        while (p_341066_.tRot < (float) -Math.PI) {
            p_341066_.tRot += (float) (Math.PI * 2);
        }

        float f2 = p_341066_.tRot - p_341066_.rot;

        while (f2 >= (float) Math.PI) {
            f2 -= (float) (Math.PI * 2);
        }

        while (f2 < (float) -Math.PI) {
            f2 += (float) (Math.PI * 2);
        }

        p_341066_.rot += f2 * 0.4F;
        p_341066_.open = Mth.clamp(p_341066_.open, 0.0F, 1.0F);
        p_341066_.time++;
        p_341066_.oFlip = p_341066_.flip;
        float f = (p_341066_.flipT - p_341066_.flip) * 0.4F;
        float f3 = 0.2F;
        f = Mth.clamp(f, -0.2F, 0.2F);
        p_341066_.flipA = p_341066_.flipA + (f - p_341066_.flipA) * 0.9F;
        p_341066_.flip = p_341066_.flip + p_341066_.flipA;
    }

    @Override
    public Component getName() {
        return (Component)(this.name != null ? this.name : Component.translatable("container.enchant"));
    }

    public void setCustomName(@Nullable Component p_341274_) {
        this.name = p_341274_;
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput p_341179_) {
        super.applyImplicitComponents(p_341179_);
        this.name = p_341179_.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder p_340897_) {
        super.collectImplicitComponents(p_340897_);
        p_340897_.set(DataComponents.CUSTOM_NAME, this.name);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag p_341218_) {
        p_341218_.remove("CustomName");
    }
}
