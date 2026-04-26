package net.minecraft.world.item.enchantment;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableObject;

public class EnchantmentHelper {
    /**
     * @deprecated Neo: Use {@link #getTagEnchantmentLevel(Holder, ItemStack)} for NBT enchantments, or {@link ItemStack#getEnchantmentLevel(Holder)} for gameplay.
     */
    @Deprecated
    public static int getItemEnchantmentLevel(Holder<Enchantment> p_346179_, ItemStack p_44845_) {
        // Neo: To reduce patch size, update this method to always check gameplay enchantments, and add getTagEnchantmentLevel as a helper for mods.
        return p_44845_.getEnchantmentLevel(p_346179_);
    }

    /**
     * Gets the level of an enchantment from NBT. Use {@link ItemStack#getEnchantmentLevel(Holder)} for gameplay logic.
     */
    public static int getTagEnchantmentLevel(Holder<Enchantment> p_346179_, ItemStack p_44845_) {
        ItemEnchantments itemenchantments = p_44845_.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return itemenchantments.getLevel(p_346179_);
    }

    public static ItemEnchantments updateEnchantments(ItemStack p_331034_, Consumer<ItemEnchantments.Mutable> p_332031_) {
        DataComponentType<ItemEnchantments> datacomponenttype = getComponentType(p_331034_);
        ItemEnchantments itemenchantments = p_331034_.get(datacomponenttype);
        if (itemenchantments == null) {
            return ItemEnchantments.EMPTY;
        } else {
            ItemEnchantments.Mutable itemenchantments$mutable = new ItemEnchantments.Mutable(itemenchantments);
            p_332031_.accept(itemenchantments$mutable);
            ItemEnchantments itemenchantments1 = itemenchantments$mutable.toImmutable();
            p_331034_.set(datacomponenttype, itemenchantments1);
            return itemenchantments1;
        }
    }

    public static boolean canStoreEnchantments(ItemStack p_330666_) {
        return p_330666_.has(getComponentType(p_330666_));
    }

    public static void setEnchantments(ItemStack p_44867_, ItemEnchantments p_332148_) {
        p_44867_.set(getComponentType(p_44867_), p_332148_);
    }

    public static ItemEnchantments getEnchantmentsForCrafting(ItemStack p_330538_) {
        return p_330538_.getOrDefault(getComponentType(p_330538_), ItemEnchantments.EMPTY);
    }

    public static DataComponentType<ItemEnchantments> getComponentType(ItemStack p_331909_) {
        return p_331909_.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    public static boolean hasAnyEnchantments(ItemStack p_332657_) {
        return !p_332657_.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
            || !p_332657_.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public static int processDurabilityChange(ServerLevel p_345153_, ItemStack p_344889_, int p_345787_) {
        MutableFloat mutablefloat = new MutableFloat((float)p_345787_);
        runIterationOnItem(p_344889_, (p_344593_, p_344594_) -> p_344593_.value().modifyDurabilityChange(p_345153_, p_344594_, p_344889_, mutablefloat));
        return mutablefloat.intValue();
    }

    public static int processAmmoUse(ServerLevel p_344848_, ItemStack p_345072_, ItemStack p_345407_, int p_346289_) {
        MutableFloat mutablefloat = new MutableFloat((float)p_346289_);
        runIterationOnItem(p_345072_, (p_344545_, p_344546_) -> p_344545_.value().modifyAmmoCount(p_344848_, p_344546_, p_345407_, mutablefloat));
        return mutablefloat.intValue();
    }

    public static int processBlockExperience(ServerLevel p_344948_, ItemStack p_345630_, int p_345026_) {
        MutableFloat mutablefloat = new MutableFloat((float)p_345026_);
        runIterationOnItem(p_345630_, (p_344491_, p_344492_) -> p_344491_.value().modifyBlockExperience(p_344948_, p_344492_, p_345630_, mutablefloat));
        return mutablefloat.intValue();
    }

    public static int processMobExperience(ServerLevel p_344940_, @Nullable Entity p_345838_, Entity p_345369_, int p_344901_) {
        if (p_345838_ instanceof LivingEntity livingentity) {
            MutableFloat mutablefloat = new MutableFloat((float)p_344901_);
            runIterationOnEquipment(
                livingentity,
                (p_344574_, p_344575_, p_344576_) -> p_344574_.value()
                        .modifyMobExperience(p_344940_, p_344575_, p_344576_.itemStack(), p_345369_, mutablefloat)
            );
            return mutablefloat.intValue();
        } else {
            return p_344901_;
        }
    }

    public static void runIterationOnItem(ItemStack p_345425_, EnchantmentHelper.EnchantmentVisitor p_345023_) {
        ItemEnchantments itemenchantments = p_345425_.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        // Neo: Respect gameplay-only enchantments when doing iterations
        var lookup = net.neoforged.neoforge.common.CommonHooks.resolveLookup(net.minecraft.core.registries.Registries.ENCHANTMENT);
        if (lookup != null) {
            itemenchantments = p_345425_.getAllEnchantments(lookup);
        }

        for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
            p_345023_.accept(entry.getKey(), entry.getIntValue());
        }
    }

    public static void runIterationOnItem(
        ItemStack p_44852_, EquipmentSlot p_345566_, LivingEntity p_345792_, EnchantmentHelper.EnchantmentInSlotVisitor p_345683_
    ) {
        if (!p_44852_.isEmpty()) {
            ItemEnchantments itemenchantments = p_44852_.get(DataComponents.ENCHANTMENTS);

            // Neo: Respect gameplay-only enchantments when doing iterations
            itemenchantments = p_44852_.getAllEnchantments(p_345792_.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT));

            if (itemenchantments != null && !itemenchantments.isEmpty()) {
                EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(p_44852_, p_345566_, p_345792_);

                for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().matchingSlot(p_345566_)) {
                        p_345683_.accept(holder, entry.getIntValue(), enchantediteminuse);
                    }
                }
            }
        }
    }

    public static void runIterationOnEquipment(LivingEntity p_344744_, EnchantmentHelper.EnchantmentInSlotVisitor p_345709_) {
        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            runIterationOnItem(p_344744_.getItemBySlot(equipmentslot), equipmentslot, p_344744_, p_345709_);
        }
    }

    public static boolean isImmuneToDamage(ServerLevel p_346228_, LivingEntity p_345220_, DamageSource p_345884_) {
        MutableBoolean mutableboolean = new MutableBoolean();
        runIterationOnEquipment(
            p_345220_,
            (p_344534_, p_344535_, p_344536_) -> mutableboolean.setValue(
                    mutableboolean.isTrue() || p_344534_.value().isImmuneToDamage(p_346228_, p_344535_, p_345220_, p_345884_)
                )
        );
        return mutableboolean.isTrue();
    }

    public static float getDamageProtection(ServerLevel p_346015_, LivingEntity p_346118_, DamageSource p_44858_) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnEquipment(
            p_346118_,
            (p_344604_, p_344605_, p_344606_) -> p_344604_.value()
                    .modifyDamageProtection(p_346015_, p_344605_, p_344606_.itemStack(), p_346118_, p_44858_, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static float modifyDamage(ServerLevel p_345523_, ItemStack p_345856_, Entity p_344995_, DamageSource p_345216_, float p_346025_) {
        MutableFloat mutablefloat = new MutableFloat(p_346025_);
        runIterationOnItem(
            p_345856_, (p_344525_, p_344526_) -> p_344525_.value().modifyDamage(p_345523_, p_344526_, p_345856_, p_344995_, p_345216_, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static float modifyFallBasedDamage(ServerLevel p_345433_, ItemStack p_345368_, Entity p_345047_, DamageSource p_345711_, float p_346245_) {
        MutableFloat mutablefloat = new MutableFloat(p_346245_);
        runIterationOnItem(
            p_345368_, (p_344552_, p_344553_) -> p_344552_.value().modifyFallBasedDamage(p_345433_, p_344553_, p_345368_, p_345047_, p_345711_, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static float modifyArmorEffectiveness(ServerLevel p_345426_, ItemStack p_345454_, Entity p_345834_, DamageSource p_345082_, float p_344790_) {
        MutableFloat mutablefloat = new MutableFloat(p_344790_);
        runIterationOnItem(
            p_345454_, (p_344468_, p_344469_) -> p_344468_.value().modifyArmorEffectivness(p_345426_, p_344469_, p_345454_, p_345834_, p_345082_, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static float modifyKnockback(ServerLevel p_346221_, ItemStack p_344862_, Entity p_345720_, DamageSource p_345322_, float p_345116_) {
        MutableFloat mutablefloat = new MutableFloat(p_345116_);
        runIterationOnItem(
            p_344862_, (p_344446_, p_344447_) -> p_344446_.value().modifyKnockback(p_346221_, p_344447_, p_344862_, p_345720_, p_345322_, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static void doPostAttackEffects(ServerLevel p_345941_, Entity p_345661_, DamageSource p_345580_) {
        if (p_345580_.getEntity() instanceof LivingEntity livingentity) {
            doPostAttackEffectsWithItemSource(p_345941_, p_345661_, p_345580_, livingentity.getWeaponItem());
        } else {
            doPostAttackEffectsWithItemSource(p_345941_, p_345661_, p_345580_, null);
        }
    }

    public static void doPostAttackEffectsWithItemSource(ServerLevel p_348463_, Entity p_348545_, DamageSource p_348552_, @Nullable ItemStack p_348507_) {
        if (p_348545_ instanceof LivingEntity livingentity) {
            runIterationOnEquipment(
                livingentity,
                (p_344427_, p_344428_, p_344429_) -> p_344427_.value()
                        .doPostAttack(p_348463_, p_344428_, p_344429_, EnchantmentTarget.VICTIM, p_348545_, p_348552_)
            );
        }

        if (p_348507_ != null && p_348552_.getEntity() instanceof LivingEntity livingentity1) {
            runIterationOnItem(
                p_348507_,
                EquipmentSlot.MAINHAND,
                livingentity1,
                (p_344557_, p_344558_, p_344559_) -> p_344557_.value()
                        .doPostAttack(p_348463_, p_344558_, p_344559_, EnchantmentTarget.ATTACKER, p_348545_, p_348552_)
            );
        }
    }

    public static void runLocationChangedEffects(ServerLevel p_345674_, LivingEntity p_346396_) {
        runIterationOnEquipment(
            p_346396_, (p_344496_, p_344497_, p_344498_) -> p_344496_.value().runLocationChangedEffects(p_345674_, p_344497_, p_344498_, p_346396_)
        );
    }

    public static void runLocationChangedEffects(ServerLevel p_345755_, ItemStack p_345291_, LivingEntity p_346348_, EquipmentSlot p_345919_) {
        runIterationOnItem(
            p_345291_,
            p_345919_,
            p_346348_,
            (p_344615_, p_344616_, p_344617_) -> p_344615_.value().runLocationChangedEffects(p_345755_, p_344616_, p_344617_, p_346348_)
        );
    }

    public static void stopLocationBasedEffects(LivingEntity p_346034_) {
        runIterationOnEquipment(p_346034_, (p_344643_, p_344644_, p_344645_) -> p_344643_.value().stopLocationBasedEffects(p_344644_, p_344645_, p_346034_));
    }

    public static void stopLocationBasedEffects(ItemStack p_344726_, LivingEntity p_346085_, EquipmentSlot p_345691_) {
        runIterationOnItem(
            p_344726_, p_345691_, p_346085_, (p_344480_, p_344481_, p_344482_) -> p_344480_.value().stopLocationBasedEffects(p_344481_, p_344482_, p_346085_)
        );
    }

    public static void tickEffects(ServerLevel p_345788_, LivingEntity p_344873_) {
        runIterationOnEquipment(p_344873_, (p_344432_, p_344433_, p_344434_) -> p_344432_.value().tick(p_345788_, p_344433_, p_344434_, p_344873_));
    }

    public static int getEnchantmentLevel(Holder<Enchantment> p_345086_, LivingEntity p_44838_) {
        Iterable<ItemStack> iterable = p_345086_.value().getSlotItems(p_44838_).values();
        int i = 0;

        for (ItemStack itemstack : iterable) {
            int j = getItemEnchantmentLevel(p_345086_, itemstack);
            if (j > i) {
                i = j;
            }
        }

        return i;
    }

    public static int processProjectileCount(ServerLevel p_345598_, ItemStack p_346421_, Entity p_346006_, int p_346388_) {
        MutableFloat mutablefloat = new MutableFloat((float)p_346388_);
        runIterationOnItem(
            p_346421_, (p_344634_, p_344635_) -> p_344634_.value().modifyProjectileCount(p_345598_, p_344635_, p_346421_, p_346006_, mutablefloat)
        );
        return Math.max(0, mutablefloat.intValue());
    }

    public static float processProjectileSpread(ServerLevel p_346048_, ItemStack p_345702_, Entity p_346314_, float p_346070_) {
        MutableFloat mutablefloat = new MutableFloat(p_346070_);
        runIterationOnItem(
            p_345702_, (p_344474_, p_344475_) -> p_344474_.value().modifyProjectileSpread(p_346048_, p_344475_, p_345702_, p_346314_, mutablefloat)
        );
        return Math.max(0.0F, mutablefloat.floatValue());
    }

    public static int getPiercingCount(ServerLevel p_345735_, ItemStack p_344942_, ItemStack p_345766_) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(p_344942_, (p_344598_, p_344599_) -> p_344598_.value().modifyPiercingCount(p_345735_, p_344599_, p_345766_, mutablefloat));
        return Math.max(0, mutablefloat.intValue());
    }

    public static void onProjectileSpawned(ServerLevel p_345062_, ItemStack p_345805_, AbstractArrow p_346298_, Consumer<Item> p_348544_) {
        LivingEntity livingentity = p_346298_.getOwner() instanceof LivingEntity livingentity1 ? livingentity1 : null;
        EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(p_345805_, null, livingentity, p_348544_);
        runIterationOnItem(p_345805_, (p_344580_, p_344581_) -> p_344580_.value().onProjectileSpawned(p_345062_, p_344581_, enchantediteminuse, p_346298_));
    }

    public static void onHitBlock(
        ServerLevel p_346213_,
        ItemStack p_344826_,
        @Nullable LivingEntity p_345015_,
        Entity p_345210_,
        @Nullable EquipmentSlot p_345889_,
        Vec3 p_345922_,
        BlockState p_350787_,
        Consumer<Item> p_348575_
    ) {
        EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(p_344826_, p_345889_, p_345015_, p_348575_);
        runIterationOnItem(
            p_344826_, (p_350196_, p_350197_) -> p_350196_.value().onHitBlock(p_346213_, p_350197_, enchantediteminuse, p_345210_, p_345922_, p_350787_)
        );
    }

    public static int modifyDurabilityToRepairFromXp(ServerLevel p_345119_, ItemStack p_345686_, int p_344847_) {
        MutableFloat mutablefloat = new MutableFloat((float)p_344847_);
        runIterationOnItem(p_345686_, (p_344540_, p_344541_) -> p_344540_.value().modifyDurabilityToRepairFromXp(p_345119_, p_344541_, p_345686_, mutablefloat));
        return Math.max(0, mutablefloat.intValue());
    }

    public static float processEquipmentDropChance(ServerLevel p_346339_, LivingEntity p_345864_, DamageSource p_345060_, float p_346089_) {
        MutableFloat mutablefloat = new MutableFloat(p_346089_);
        RandomSource randomsource = p_345864_.getRandom();
        runIterationOnEquipment(p_345864_, (p_347320_, p_347321_, p_347322_) -> {
            LootContext lootcontext = Enchantment.damageContext(p_346339_, p_347321_, p_345864_, p_345060_);
            p_347320_.value().getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS).forEach(p_347345_ -> {
                if (p_347345_.enchanted() == EnchantmentTarget.VICTIM && p_347345_.affected() == EnchantmentTarget.VICTIM && p_347345_.matches(lootcontext)) {
                    mutablefloat.setValue(p_347345_.effect().process(p_347321_, randomsource, mutablefloat.floatValue()));
                }
            });
        });
        if (p_345060_.getEntity() instanceof LivingEntity livingentity) {
            runIterationOnEquipment(
                livingentity,
                (p_347338_, p_347339_, p_347340_) -> {
                    LootContext lootcontext = Enchantment.damageContext(p_346339_, p_347339_, p_345864_, p_345060_);
                    p_347338_.value()
                        .getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS)
                        .forEach(
                            p_347327_ -> {
                                if (p_347327_.enchanted() == EnchantmentTarget.ATTACKER
                                    && p_347327_.affected() == EnchantmentTarget.VICTIM
                                    && p_347327_.matches(lootcontext)) {
                                    mutablefloat.setValue(p_347327_.effect().process(p_347339_, randomsource, mutablefloat.floatValue()));
                                }
                            }
                        );
                }
            );
        }

        return mutablefloat.floatValue();
    }

    public static void forEachModifier(ItemStack p_348634_, EquipmentSlotGroup p_348528_, BiConsumer<Holder<Attribute>, AttributeModifier> p_348554_) {
        runIterationOnItem(p_348634_, (p_344461_, p_344462_) -> p_344461_.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(p_350185_ -> {
                if (((Enchantment)p_344461_.value()).definition().slots().contains(p_348528_)) {
                    p_348554_.accept(p_350185_.attribute(), p_350185_.getModifier(p_344462_, p_348528_));
                }
            }));
    }

    public static void forEachModifier(ItemStack p_345685_, EquipmentSlot p_345123_, BiConsumer<Holder<Attribute>, AttributeModifier> p_345061_) {
        runIterationOnItem(p_345685_, (p_348409_, p_348410_) -> p_348409_.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(p_350180_ -> {
                if (((Enchantment)p_348409_.value()).matchingSlot(p_345123_)) {
                    p_345061_.accept(p_350180_.attribute(), p_350180_.getModifier(p_348410_, p_345123_));
                }
            }));
    }

    public static int getFishingLuckBonus(ServerLevel p_346163_, ItemStack p_44905_, Entity p_345772_) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(
            p_44905_, (p_344564_, p_344565_) -> p_344564_.value().modifyFishingLuckBonus(p_346163_, p_344565_, p_44905_, p_345772_, mutablefloat)
        );
        return Math.max(0, mutablefloat.intValue());
    }

    public static float getFishingTimeReduction(ServerLevel p_345589_, ItemStack p_344902_, Entity p_346054_) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(
            p_344902_, (p_344611_, p_344612_) -> p_344611_.value().modifyFishingTimeReduction(p_345589_, p_344612_, p_344902_, p_346054_, mutablefloat)
        );
        return Math.max(0.0F, mutablefloat.floatValue());
    }

    public static int getTridentReturnToOwnerAcceleration(ServerLevel p_344814_, ItemStack p_346255_, Entity p_346332_) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(
            p_346255_,
            (p_344516_, p_344517_) -> p_344516_.value().modifyTridentReturnToOwnerAcceleration(p_344814_, p_344517_, p_346255_, p_346332_, mutablefloat)
        );
        return Math.max(0, mutablefloat.intValue());
    }

    public static float modifyCrossbowChargingTime(ItemStack p_352460_, LivingEntity p_347534_, float p_345633_) {
        MutableFloat mutablefloat = new MutableFloat(p_345633_);
        runIterationOnItem(p_352460_, (p_352869_, p_352870_) -> p_352869_.value().modifyCrossbowChargeTime(p_347534_.getRandom(), p_352870_, mutablefloat));
        return Math.max(0.0F, mutablefloat.floatValue());
    }

    public static float getTridentSpinAttackStrength(ItemStack p_352129_, LivingEntity p_345705_) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(
            p_352129_, (p_352865_, p_352866_) -> p_352865_.value().modifyTridentSpinAttackStrength(p_345705_.getRandom(), p_352866_, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static boolean hasTag(ItemStack p_345665_, TagKey<Enchantment> p_345928_) {
        ItemEnchantments itemenchantments = p_345665_.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        // Neo: Respect gameplay-only enchantments when enchantment effect tag checks
        var lookup = net.neoforged.neoforge.common.CommonHooks.resolveLookup(net.minecraft.core.registries.Registries.ENCHANTMENT);
        if (lookup != null) {
            itemenchantments = p_345665_.getAllEnchantments(lookup);
        }

        for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(p_345928_)) {
                return true;
            }
        }

        return false;
    }

    public static boolean has(ItemStack p_345474_, DataComponentType<?> p_344897_) {
        MutableBoolean mutableboolean = new MutableBoolean(false);
        runIterationOnItem(p_345474_, (p_344620_, p_344621_) -> {
            if (p_344620_.value().effects().has(p_344897_)) {
                mutableboolean.setTrue();
            }
        });
        return mutableboolean.booleanValue();
    }

    public static <T> Optional<T> pickHighestLevel(ItemStack p_345398_, DataComponentType<List<T>> p_346022_) {
        Pair<List<T>, Integer> pair = getHighestLevel(p_345398_, p_346022_);
        if (pair != null) {
            List<T> list = pair.getFirst();
            int i = pair.getSecond();
            return Optional.of(list.get(Math.min(i, list.size()) - 1));
        } else {
            return Optional.empty();
        }
    }

    @Nullable
    public static <T> Pair<T, Integer> getHighestLevel(ItemStack p_346269_, DataComponentType<T> p_345899_) {
        MutableObject<Pair<T, Integer>> mutableobject = new MutableObject<>();
        runIterationOnItem(p_346269_, (p_344457_, p_344458_) -> {
            if (mutableobject.getValue() == null || mutableobject.getValue().getSecond() < p_344458_) {
                T t = p_344457_.value().effects().get(p_345899_);
                if (t != null) {
                    mutableobject.setValue(Pair.of(t, p_344458_));
                }
            }
        });
        return mutableobject.getValue();
    }

    public static Optional<EnchantedItemInUse> getRandomItemWith(DataComponentType<?> p_345509_, LivingEntity p_44841_, Predicate<ItemStack> p_44842_) {
        List<EnchantedItemInUse> list = new ArrayList<>();

        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            ItemStack itemstack = p_44841_.getItemBySlot(equipmentslot);
            if (p_44842_.test(itemstack)) {
                ItemEnchantments itemenchantments = itemstack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

                for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().effects().has(p_345509_) && holder.value().matchingSlot(equipmentslot)) {
                        list.add(new EnchantedItemInUse(itemstack, equipmentslot, p_44841_));
                    }
                }
            }
        }

        return Util.getRandomSafe(list, p_44841_.getRandom());
    }

    public static int getEnchantmentCost(RandomSource p_220288_, int p_220289_, int p_220290_, ItemStack p_220291_) {
        Item item = p_220291_.getItem();
        int i = p_220291_.getEnchantmentValue();
        if (i <= 0) {
            return 0;
        } else {
            if (p_220290_ > 15) {
                p_220290_ = 15;
            }

            int j = p_220288_.nextInt(8) + 1 + (p_220290_ >> 1) + p_220288_.nextInt(p_220290_ + 1);
            if (p_220289_ == 0) {
                return Math.max(j / 3, 1);
            } else {
                return p_220289_ == 1 ? j * 2 / 3 + 1 : Math.max(j, p_220290_ * 2);
            }
        }
    }

    public static ItemStack enchantItem(
        RandomSource p_346328_, ItemStack p_346267_, int p_345272_, RegistryAccess p_345660_, Optional<? extends HolderSet<Enchantment>> p_345161_
    ) {
        return enchantItem(
            p_346328_,
            p_346267_,
            p_345272_,
            p_345161_.map(HolderSet::stream)
                .orElseGet(() -> p_345660_.registryOrThrow(Registries.ENCHANTMENT).holders().map(p_344499_ -> (Holder<Enchantment>)p_344499_))
        );
    }

    public static ItemStack enchantItem(RandomSource p_220293_, ItemStack p_220294_, int p_220295_, Stream<Holder<Enchantment>> p_345380_) {
        List<EnchantmentInstance> list = selectEnchantment(p_220293_, p_220294_, p_220295_, p_345380_);
        if (p_220294_.is(Items.BOOK)) {
            p_220294_ = new ItemStack(Items.ENCHANTED_BOOK);
        }

        for (EnchantmentInstance enchantmentinstance : list) {
            p_220294_.enchant(enchantmentinstance.enchantment, enchantmentinstance.level);
        }

        return p_220294_;
    }

    public static List<EnchantmentInstance> selectEnchantment(RandomSource p_220298_, ItemStack p_220299_, int p_220300_, Stream<Holder<Enchantment>> p_346061_) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        Item item = p_220299_.getItem();
        int i = p_220299_.getEnchantmentValue();
        if (i <= 0) {
            return list;
        } else {
            p_220300_ += 1 + p_220298_.nextInt(i / 4 + 1) + p_220298_.nextInt(i / 4 + 1);
            float f = (p_220298_.nextFloat() + p_220298_.nextFloat() - 1.0F) * 0.15F;
            p_220300_ = Mth.clamp(Math.round((float)p_220300_ + (float)p_220300_ * f), 1, Integer.MAX_VALUE);
            List<EnchantmentInstance> list1 = getAvailableEnchantmentResults(p_220300_, p_220299_, p_346061_);
            if (!list1.isEmpty()) {
                WeightedRandom.getRandomItem(p_220298_, list1).ifPresent(list::add);

                while (p_220298_.nextInt(50) <= p_220300_) {
                    if (!list.isEmpty()) {
                        filterCompatibleEnchantments(list1, Util.lastOf(list));
                    }

                    if (list1.isEmpty()) {
                        break;
                    }

                    WeightedRandom.getRandomItem(p_220298_, list1).ifPresent(list::add);
                    p_220300_ /= 2;
                }
            }

            return list;
        }
    }

    public static void filterCompatibleEnchantments(List<EnchantmentInstance> p_44863_, EnchantmentInstance p_44864_) {
        p_44863_.removeIf(p_344519_ -> !Enchantment.areCompatible(p_44864_.enchantment, p_344519_.enchantment));
    }

    public static boolean isEnchantmentCompatible(Collection<Holder<Enchantment>> p_44860_, Holder<Enchantment> p_345356_) {
        for (Holder<Enchantment> holder : p_44860_) {
            if (!Enchantment.areCompatible(holder, p_345356_)) {
                return false;
            }
        }

        return true;
    }

    public static List<EnchantmentInstance> getAvailableEnchantmentResults(int p_44818_, ItemStack p_44819_, Stream<Holder<Enchantment>> p_345348_) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        boolean flag = p_44819_.is(Items.BOOK);
        // Neo: Rewrite filter logic to call isPrimaryItemFor instead of hardcoded vanilla logic.
        // The original logic is recorded in the default implementation of IItemExtension#isPrimaryItemFor.
        p_345348_.filter(p_44819_::isPrimaryItemFor).forEach(p_344478_ -> {
            Enchantment enchantment = p_344478_.value();

            for (int i = enchantment.getMaxLevel(); i >= enchantment.getMinLevel(); i--) {
                if (p_44818_ >= enchantment.getMinCost(i) && p_44818_ <= enchantment.getMaxCost(i)) {
                    list.add(new EnchantmentInstance((Holder<Enchantment>)p_344478_, i));
                    break;
                }
            }
        });
        return list;
    }

    public static void enchantItemFromProvider(
        ItemStack p_345172_, RegistryAccess p_348593_, ResourceKey<EnchantmentProvider> p_345876_, DifficultyInstance p_348599_, RandomSource p_345717_
    ) {
        EnchantmentProvider enchantmentprovider = p_348593_.registryOrThrow(Registries.ENCHANTMENT_PROVIDER).get(p_345876_);
        if (enchantmentprovider != null) {
            updateEnchantments(p_345172_, p_348401_ -> enchantmentprovider.enchant(p_345172_, p_348401_, p_345717_, p_348599_));
        }
    }

    @FunctionalInterface
    public interface EnchantmentInSlotVisitor {
        void accept(Holder<Enchantment> p_346326_, int p_346009_, EnchantedItemInUse p_345960_);
    }

    @FunctionalInterface
    public interface EnchantmentVisitor {
        void accept(Holder<Enchantment> p_346050_, int p_44946_);
    }
}
