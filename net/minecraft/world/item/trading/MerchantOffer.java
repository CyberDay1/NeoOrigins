package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class MerchantOffer {
    public static final Codec<MerchantOffer> CODEC = RecordCodecBuilder.create(
        p_324269_ -> p_324269_.group(
                    ItemCost.CODEC.fieldOf("buy").forGetter(p_330121_ -> p_330121_.baseCostA),
                    ItemCost.CODEC.lenientOptionalFieldOf("buyB").forGetter(p_330120_ -> p_330120_.costB),
                    ItemStack.CODEC.fieldOf("sell").forGetter(p_324095_ -> p_324095_.result),
                    Codec.INT.lenientOptionalFieldOf("uses", Integer.valueOf(0)).forGetter(p_324003_ -> p_324003_.uses),
                    Codec.INT.lenientOptionalFieldOf("maxUses", Integer.valueOf(4)).forGetter(p_323849_ -> p_323849_.maxUses),
                    Codec.BOOL.lenientOptionalFieldOf("rewardExp", Boolean.valueOf(true)).forGetter(p_323485_ -> p_323485_.rewardExp),
                    Codec.INT.lenientOptionalFieldOf("specialPrice", Integer.valueOf(0)).forGetter(p_324423_ -> p_324423_.specialPriceDiff),
                    Codec.INT.lenientOptionalFieldOf("demand", Integer.valueOf(0)).forGetter(p_324040_ -> p_324040_.demand),
                    Codec.FLOAT.lenientOptionalFieldOf("priceMultiplier", Float.valueOf(0.0F)).forGetter(p_323953_ -> p_323953_.priceMultiplier),
                    Codec.INT.lenientOptionalFieldOf("xp", Integer.valueOf(1)).forGetter(p_324202_ -> p_324202_.xp)
                )
                .apply(p_324269_, MerchantOffer::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffer> STREAM_CODEC = StreamCodec.of(
        MerchantOffer::writeToStream, MerchantOffer::createFromStream
    );
    private final ItemCost baseCostA;
    private final Optional<ItemCost> costB;
    private final ItemStack result;
    private int uses;
    private final int maxUses;
    private final boolean rewardExp;
    private int specialPriceDiff;
    private int demand;
    private final float priceMultiplier;
    private final int xp;

    private MerchantOffer(
        ItemCost p_330734_,
        Optional<ItemCost> p_331500_,
        ItemStack p_45327_,
        int p_45330_,
        int p_45331_,
        boolean p_330754_,
        int p_332006_,
        int p_330432_,
        float p_45332_,
        int p_330282_
    ) {
        this.baseCostA = p_330734_;
        this.costB = p_331500_;
        this.result = p_45327_;
        this.uses = p_45330_;
        this.maxUses = p_45331_;
        this.rewardExp = p_330754_;
        this.specialPriceDiff = p_332006_;
        this.demand = p_330432_;
        this.priceMultiplier = p_45332_;
        this.xp = p_330282_;
    }

    public MerchantOffer(ItemCost p_332077_, ItemStack p_320418_, int p_320071_, int p_320069_, float p_320947_) {
        this(p_332077_, Optional.empty(), p_320418_, p_320071_, p_320069_, p_320947_);
    }

    public MerchantOffer(ItemCost p_331596_, Optional<ItemCost> p_330410_, ItemStack p_45334_, int p_45337_, int p_45338_, float p_45340_) {
        this(p_331596_, p_330410_, p_45334_, 0, p_45337_, p_45338_, p_45340_);
    }

    public MerchantOffer(ItemCost p_331409_, Optional<ItemCost> p_331614_, ItemStack p_45321_, int p_45323_, int p_45324_, int p_330951_, float p_45325_) {
        this(p_331409_, p_331614_, p_45321_, p_45323_, p_45324_, p_330951_, p_45325_, 0);
    }

    public MerchantOffer(
        ItemCost p_331744_, Optional<ItemCost> p_330460_, ItemStack p_324239_, int p_324562_, int p_324493_, int p_323558_, float p_323528_, int p_324484_
    ) {
        this(p_331744_, p_330460_, p_324239_, p_324562_, p_324493_, true, 0, p_324484_, p_323528_, p_323558_);
    }

    private MerchantOffer(MerchantOffer p_302340_) {
        this(
            p_302340_.baseCostA,
            p_302340_.costB,
            p_302340_.result.copy(),
            p_302340_.uses,
            p_302340_.maxUses,
            p_302340_.rewardExp,
            p_302340_.specialPriceDiff,
            p_302340_.demand,
            p_302340_.priceMultiplier,
            p_302340_.xp
        );
    }

    public ItemStack getBaseCostA() {
        return this.baseCostA.itemStack();
    }

    public ItemStack getCostA() {
        return this.baseCostA.itemStack().copyWithCount(this.getModifiedCostCount(this.baseCostA));
    }

    private int getModifiedCostCount(ItemCost p_330556_) {
        int i = p_330556_.count();
        int j = Math.max(0, Mth.floor((float)(i * this.demand) * this.priceMultiplier));
        return Mth.clamp(i + j + this.specialPriceDiff, 1, p_330556_.itemStack().getMaxStackSize());
    }

    public ItemStack getCostB() {
        return this.costB.map(ItemCost::itemStack).orElse(ItemStack.EMPTY);
    }

    public ItemCost getItemCostA() {
        return this.baseCostA;
    }

    public Optional<ItemCost> getItemCostB() {
        return this.costB;
    }

    public ItemStack getResult() {
        return this.result;
    }

    public void updateDemand() {
        this.demand = this.demand + this.uses - (this.maxUses - this.uses);
    }

    public ItemStack assemble() {
        return this.result.copy();
    }

    public int getUses() {
        return this.uses;
    }

    public void resetUses() {
        this.uses = 0;
    }

    public int getMaxUses() {
        return this.maxUses;
    }

    public void increaseUses() {
        this.uses++;
    }

    public int getDemand() {
        return this.demand;
    }

    public void addToSpecialPriceDiff(int p_45354_) {
        this.specialPriceDiff += p_45354_;
    }

    public void resetSpecialPriceDiff() {
        this.specialPriceDiff = 0;
    }

    public int getSpecialPriceDiff() {
        return this.specialPriceDiff;
    }

    public void setSpecialPriceDiff(int p_45360_) {
        this.specialPriceDiff = p_45360_;
    }

    public float getPriceMultiplier() {
        return this.priceMultiplier;
    }

    public int getXp() {
        return this.xp;
    }

    public boolean isOutOfStock() {
        return this.uses >= this.maxUses;
    }

    public void setToOutOfStock() {
        this.uses = this.maxUses;
    }

    public boolean needsRestock() {
        return this.uses > 0;
    }

    public boolean shouldRewardExp() {
        return this.rewardExp;
    }

    public boolean satisfiedBy(ItemStack p_45356_, ItemStack p_45357_) {
        if (!this.baseCostA.test(p_45356_) || p_45356_.getCount() < this.getModifiedCostCount(this.baseCostA)) {
            return false;
        } else {
            return !this.costB.isPresent() ? p_45357_.isEmpty() : this.costB.get().test(p_45357_) && p_45357_.getCount() >= this.costB.get().count();
        }
    }

    public boolean take(ItemStack p_45362_, ItemStack p_45363_) {
        if (!this.satisfiedBy(p_45362_, p_45363_)) {
            return false;
        } else {
            p_45362_.shrink(this.getCostA().getCount());
            if (!this.getCostB().isEmpty()) {
                p_45363_.shrink(this.getCostB().getCount());
            }

            return true;
        }
    }

    public MerchantOffer copy() {
        return new MerchantOffer(this);
    }

    private static void writeToStream(RegistryFriendlyByteBuf p_320530_, MerchantOffer p_320384_) {
        ItemCost.STREAM_CODEC.encode(p_320530_, p_320384_.getItemCostA());
        ItemStack.STREAM_CODEC.encode(p_320530_, p_320384_.getResult());
        ItemCost.OPTIONAL_STREAM_CODEC.encode(p_320530_, p_320384_.getItemCostB());
        p_320530_.writeBoolean(p_320384_.isOutOfStock());
        p_320530_.writeInt(p_320384_.getUses());
        p_320530_.writeInt(p_320384_.getMaxUses());
        p_320530_.writeInt(p_320384_.getXp());
        p_320530_.writeInt(p_320384_.getSpecialPriceDiff());
        p_320530_.writeFloat(p_320384_.getPriceMultiplier());
        p_320530_.writeInt(p_320384_.getDemand());
    }

    public static MerchantOffer createFromStream(RegistryFriendlyByteBuf p_320207_) {
        ItemCost itemcost = ItemCost.STREAM_CODEC.decode(p_320207_);
        ItemStack itemstack = ItemStack.STREAM_CODEC.decode(p_320207_);
        Optional<ItemCost> optional = ItemCost.OPTIONAL_STREAM_CODEC.decode(p_320207_);
        boolean flag = p_320207_.readBoolean();
        int i = p_320207_.readInt();
        int j = p_320207_.readInt();
        int k = p_320207_.readInt();
        int l = p_320207_.readInt();
        float f = p_320207_.readFloat();
        int i1 = p_320207_.readInt();
        MerchantOffer merchantoffer = new MerchantOffer(itemcost, optional, itemstack, i, j, k, f, i1);
        if (flag) {
            merchantoffer.setToOutOfStock();
        }

        merchantoffer.setSpecialPriceDiff(l);
        return merchantoffer;
    }
}
