package net.minecraft.world.entity;

import net.minecraft.world.item.ItemStack;

public class Crackiness {
    public static final Crackiness GOLEM = new Crackiness(0.75F, 0.5F, 0.25F);
    public static final Crackiness WOLF_ARMOR = new Crackiness(0.95F, 0.69F, 0.32F);
    private final float fractionLow;
    private final float fractionMedium;
    private final float fractionHigh;

    private Crackiness(float p_330512_, float p_330332_, float p_330467_) {
        this.fractionLow = p_330512_;
        this.fractionMedium = p_330332_;
        this.fractionHigh = p_330467_;
    }

    public Crackiness.Level byFraction(float p_331959_) {
        if (p_331959_ < this.fractionHigh) {
            return Crackiness.Level.HIGH;
        } else if (p_331959_ < this.fractionMedium) {
            return Crackiness.Level.MEDIUM;
        } else {
            return p_331959_ < this.fractionLow ? Crackiness.Level.LOW : Crackiness.Level.NONE;
        }
    }

    public Crackiness.Level byDamage(ItemStack p_331331_) {
        return !p_331331_.isDamageableItem() ? Crackiness.Level.NONE : this.byDamage(p_331331_.getDamageValue(), p_331331_.getMaxDamage());
    }

    public Crackiness.Level byDamage(int p_330780_, int p_330215_) {
        return this.byFraction((float)(p_330215_ - p_330780_) / (float)p_330215_);
    }

    public static enum Level {
        NONE,
        LOW,
        MEDIUM,
        HIGH;
    }
}
