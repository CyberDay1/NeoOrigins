package net.minecraft.world.item;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class InstrumentItem extends Item {
    private final TagKey<Instrument> instruments;

    public InstrumentItem(Item.Properties p_220099_, TagKey<Instrument> p_220100_) {
        super(p_220099_);
        this.instruments = p_220100_;
    }

    @Override
    public void appendHoverText(ItemStack p_220115_, Item.TooltipContext p_339630_, List<Component> p_220117_, TooltipFlag p_220118_) {
        super.appendHoverText(p_220115_, p_339630_, p_220117_, p_220118_);
        Optional<ResourceKey<Instrument>> optional = this.getInstrument(p_220115_).flatMap(Holder::unwrapKey);
        if (optional.isPresent()) {
            MutableComponent mutablecomponent = Component.translatable(Util.makeDescriptionId("instrument", optional.get().location()));
            p_220117_.add(mutablecomponent.withStyle(ChatFormatting.GRAY));
        }
    }

    public static ItemStack create(Item p_220108_, Holder<Instrument> p_220109_) {
        ItemStack itemstack = new ItemStack(p_220108_);
        itemstack.set(DataComponents.INSTRUMENT, p_220109_);
        return itemstack;
    }

    public static void setRandom(ItemStack p_220111_, TagKey<Instrument> p_220112_, RandomSource p_220113_) {
        Optional<Holder<Instrument>> optional = BuiltInRegistries.INSTRUMENT.getRandomElementOf(p_220112_, p_220113_);
        optional.ifPresent(p_330088_ -> p_220111_.set(DataComponents.INSTRUMENT, (Holder<Instrument>)p_330088_));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_220123_, Player p_220124_, InteractionHand p_220125_) {
        ItemStack itemstack = p_220124_.getItemInHand(p_220125_);
        Optional<? extends Holder<Instrument>> optional = this.getInstrument(itemstack);
        if (optional.isPresent()) {
            Instrument instrument = optional.get().value();
            p_220124_.startUsingItem(p_220125_);
            play(p_220123_, p_220124_, instrument);
            p_220124_.getCooldowns().addCooldown(this, instrument.useDuration());
            p_220124_.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.consume(itemstack);
        } else {
            return InteractionResultHolder.fail(itemstack);
        }
    }

    @Override
    public int getUseDuration(ItemStack p_220131_, LivingEntity p_345916_) {
        Optional<Holder<Instrument>> optional = this.getInstrument(p_220131_);
        return optional.<Integer>map(p_248418_ -> p_248418_.value().useDuration()).orElse(0);
    }

    private Optional<Holder<Instrument>> getInstrument(ItemStack p_220135_) {
        Holder<Instrument> holder = p_220135_.get(DataComponents.INSTRUMENT);
        if (holder != null) {
            return Optional.of(holder);
        } else {
            Iterator<Holder<Instrument>> iterator = BuiltInRegistries.INSTRUMENT.getTagOrEmpty(this.instruments).iterator();
            return iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty();
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack p_220133_) {
        return UseAnim.TOOT_HORN;
    }

    private static void play(Level p_220127_, Player p_220128_, Instrument p_220129_) {
        SoundEvent soundevent = p_220129_.soundEvent().value();
        float f = p_220129_.range() / 16.0F;
        p_220127_.playSound(p_220128_, p_220128_, soundevent, SoundSource.RECORDS, f, 1.0F);
        p_220127_.gameEvent(GameEvent.INSTRUMENT_PLAY, p_220128_.position(), GameEvent.Context.of(p_220128_));
    }
}
