package net.minecraft.world.item.enchantment;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;

public record Enchantment(Component description, Enchantment.EnchantmentDefinition definition, HolderSet<Enchantment> exclusiveSet, DataComponentMap effects) {
    public static final int MAX_LEVEL = 255;
    public static final Codec<Enchantment> DIRECT_CODEC = RecordCodecBuilder.create(
        p_344998_ -> p_344998_.group(
                    ComponentSerialization.CODEC.fieldOf("description").forGetter(Enchantment::description),
                    Enchantment.EnchantmentDefinition.CODEC.forGetter(Enchantment::definition),
                    RegistryCodecs.homogeneousList(Registries.ENCHANTMENT)
                        .optionalFieldOf("exclusive_set", HolderSet.direct())
                        .forGetter(Enchantment::exclusiveSet),
                    EnchantmentEffectComponents.CODEC.optionalFieldOf("effects", DataComponentMap.EMPTY).forGetter(Enchantment::effects)
                )
                .apply(p_344998_, Enchantment::new)
    );
    public static final Codec<Holder<Enchantment>> CODEC = RegistryFixedCodec.create(Registries.ENCHANTMENT);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Enchantment>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT);

    public static Enchantment.Cost constantCost(int p_336195_) {
        return new Enchantment.Cost(p_336195_, 0);
    }

    public static Enchantment.Cost dynamicCost(int p_336066_, int p_336018_) {
        return new Enchantment.Cost(p_336066_, p_336018_);
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> p_345738_,
        HolderSet<Item> p_345428_,
        int p_335506_,
        int p_335598_,
        Enchantment.Cost p_336185_,
        Enchantment.Cost p_335768_,
        int p_335409_,
        EquipmentSlotGroup... p_344907_
    ) {
        return new Enchantment.EnchantmentDefinition(
            p_345738_, Optional.of(p_345428_), p_335506_, p_335598_, p_336185_, p_335768_, p_335409_, List.of(p_344907_)
        );
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> p_345097_,
        int p_335557_,
        int p_336051_,
        Enchantment.Cost p_336176_,
        Enchantment.Cost p_335380_,
        int p_335569_,
        EquipmentSlotGroup... p_345444_
    ) {
        return new Enchantment.EnchantmentDefinition(p_345097_, Optional.empty(), p_335557_, p_336051_, p_336176_, p_335380_, p_335569_, List.of(p_345444_));
    }

    public Map<EquipmentSlot, ItemStack> getSlotItems(LivingEntity p_44685_) {
        Map<EquipmentSlot, ItemStack> map = Maps.newEnumMap(EquipmentSlot.class);

        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            if (this.matchingSlot(equipmentslot)) {
                ItemStack itemstack = p_44685_.getItemBySlot(equipmentslot);
                if (!itemstack.isEmpty()) {
                    map.put(equipmentslot, itemstack);
                }
            }
        }

        return map;
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#supportsEnchantment(Holder)}
     */
    @Deprecated
    public HolderSet<Item> getSupportedItems() {
        return this.definition.supportedItems();
    }

    public boolean matchingSlot(EquipmentSlot p_345146_) {
        return this.definition.slots().stream().anyMatch(p_345027_ -> p_345027_.test(p_345146_));
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#isPrimaryItemFor(Holder)}
     *
     * This method does not respect {@link ItemStack#supportsEnchantment(Holder)} since the {@link Holder} is not available, which makes the result of calling it invalid.
     */
    @Deprecated
    public boolean isPrimaryItem(ItemStack p_336088_) {
        return this.isSupportedItem(p_336088_) && (this.definition.primaryItems.isEmpty() || p_336088_.is(this.definition.primaryItems.get()));
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#supportsEnchantment(Holder)}
     */
    @Deprecated
    public boolean isSupportedItem(ItemStack p_344865_) {
        return p_344865_.is(this.definition.supportedItems);
    }

    public int getWeight() {
        return this.definition.weight();
    }

    public int getAnvilCost() {
        return this.definition.anvilCost();
    }

    public int getMinLevel() {
        return 1;
    }

    public int getMaxLevel() {
        return this.definition.maxLevel();
    }

    public int getMinCost(int p_44679_) {
        return this.definition.minCost().calculate(p_44679_);
    }

    public int getMaxCost(int p_44691_) {
        return this.definition.maxCost().calculate(p_44691_);
    }

    @Override
    public String toString() {
        return "Enchantment " + this.description.getString();
    }

    public static boolean areCompatible(Holder<Enchantment> p_345800_, Holder<Enchantment> p_346143_) {
        return !p_345800_.equals(p_346143_) && !p_345800_.value().exclusiveSet.contains(p_346143_) && !p_346143_.value().exclusiveSet.contains(p_345800_);
    }

    public static Component getFullname(Holder<Enchantment> p_345597_, int p_44701_) {
        MutableComponent mutablecomponent = p_345597_.value().description.copy();
        if (p_345597_.is(EnchantmentTags.CURSE)) {
            ComponentUtils.mergeStyles(mutablecomponent, Style.EMPTY.withColor(ChatFormatting.RED));
        } else {
            ComponentUtils.mergeStyles(mutablecomponent, Style.EMPTY.withColor(ChatFormatting.GRAY));
        }

        if (p_44701_ != 1 || p_345597_.value().getMaxLevel() != 1) {
            mutablecomponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + p_44701_));
        }

        return mutablecomponent;
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#supportsEnchantment(Holder)}
     */
    @Deprecated
    public boolean canEnchant(ItemStack p_44689_) {
        return this.definition.supportedItems().contains(p_44689_.getItemHolder());
    }

    public <T> List<T> getEffects(DataComponentType<List<T>> p_345422_) {
        return this.effects.getOrDefault(p_345422_, List.of());
    }

    public boolean isImmuneToDamage(ServerLevel p_345480_, int p_345043_, Entity p_346344_, DamageSource p_344985_) {
        LootContext lootcontext = damageContext(p_345480_, p_345043_, p_346344_, p_344985_);

        for (ConditionalEffect<DamageImmunity> conditionaleffect : this.getEffects(EnchantmentEffectComponents.DAMAGE_IMMUNITY)) {
            if (conditionaleffect.matches(lootcontext)) {
                return true;
            }
        }

        return false;
    }

    public void modifyDamageProtection(
        ServerLevel p_345336_, int p_345347_, ItemStack p_346270_, Entity p_346395_, DamageSource p_345595_, MutableFloat p_345579_
    ) {
        LootContext lootcontext = damageContext(p_345336_, p_345347_, p_346395_, p_345595_);

        for (ConditionalEffect<EnchantmentValueEffect> conditionaleffect : this.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION)) {
            if (conditionaleffect.matches(lootcontext)) {
                p_345579_.setValue(conditionaleffect.effect().process(p_345347_, p_346395_.getRandom(), p_345579_.floatValue()));
            }
        }
    }

    public void modifyDurabilityChange(ServerLevel p_345412_, int p_344937_, ItemStack p_345854_, MutableFloat p_345948_) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.ITEM_DAMAGE, p_345412_, p_344937_, p_345854_, p_345948_);
    }

    public void modifyAmmoCount(ServerLevel p_346007_, int p_345438_, ItemStack p_345581_, MutableFloat p_345594_) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.AMMO_USE, p_346007_, p_345438_, p_345581_, p_345594_);
    }

    public void modifyPiercingCount(ServerLevel p_346422_, int p_344854_, ItemStack p_345503_, MutableFloat p_345370_) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.PROJECTILE_PIERCING, p_346422_, p_344854_, p_345503_, p_345370_);
    }

    public void modifyBlockExperience(ServerLevel p_344830_, int p_345513_, ItemStack p_344939_, MutableFloat p_344754_) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.BLOCK_EXPERIENCE, p_344830_, p_345513_, p_344939_, p_344754_);
    }

    public void modifyMobExperience(ServerLevel p_346393_, int p_345762_, ItemStack p_345945_, Entity p_345849_, MutableFloat p_345252_) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.MOB_EXPERIENCE, p_346393_, p_345762_, p_345945_, p_345849_, p_345252_);
    }

    public void modifyDurabilityToRepairFromXp(ServerLevel p_346275_, int p_346253_, ItemStack p_346008_, MutableFloat p_344922_) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.REPAIR_WITH_XP, p_346275_, p_346253_, p_346008_, p_344922_);
    }

    public void modifyTridentReturnToOwnerAcceleration(ServerLevel p_345534_, int p_345845_, ItemStack p_345558_, Entity p_345333_, MutableFloat p_344912_) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION, p_345534_, p_345845_, p_345558_, p_345333_, p_344912_);
    }

    public void modifyTridentSpinAttackStrength(RandomSource p_347594_, int p_344845_, MutableFloat p_346377_) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH, p_347594_, p_344845_, p_346377_);
    }

    public void modifyFishingTimeReduction(ServerLevel p_344910_, int p_345466_, ItemStack p_345847_, Entity p_346092_, MutableFloat p_346295_) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_TIME_REDUCTION, p_344910_, p_345466_, p_345847_, p_346092_, p_346295_);
    }

    public void modifyFishingLuckBonus(ServerLevel p_344932_, int p_346280_, ItemStack p_344733_, Entity p_346035_, MutableFloat p_345897_) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_LUCK_BONUS, p_344932_, p_346280_, p_344733_, p_346035_, p_345897_);
    }

    public void modifyDamage(ServerLevel p_345743_, int p_345544_, ItemStack p_345269_, Entity p_346011_, DamageSource p_344755_, MutableFloat p_345551_) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.DAMAGE, p_345743_, p_345544_, p_345269_, p_346011_, p_344755_, p_345551_);
    }

    public void modifyFallBasedDamage(
        ServerLevel p_345164_, int p_344793_, ItemStack p_345159_, Entity p_345653_, DamageSource p_344870_, MutableFloat p_346329_
    ) {
        this.modifyDamageFilteredValue(
            EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK, p_345164_, p_344793_, p_345159_, p_345653_, p_344870_, p_346329_
        );
    }

    public void modifyKnockback(ServerLevel p_346266_, int p_344905_, ItemStack p_345602_, Entity p_346190_, DamageSource p_345911_, MutableFloat p_345127_) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.KNOCKBACK, p_346266_, p_344905_, p_345602_, p_346190_, p_345911_, p_345127_);
    }

    public void modifyArmorEffectivness(
        ServerLevel p_345449_, int p_346129_, ItemStack p_345436_, Entity p_345815_, DamageSource p_346172_, MutableFloat p_345265_
    ) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS, p_345449_, p_346129_, p_345436_, p_345815_, p_346172_, p_345265_);
    }

    public static void doPostAttack(
        TargetedConditionalEffect<EnchantmentEntityEffect> p_346387_,
        ServerLevel p_345844_,
        int p_344928_,
        EnchantedItemInUse p_345486_,
        Entity p_345472_,
        DamageSource p_345022_
    ) {
        if (p_346387_.matches(damageContext(p_345844_, p_344928_, p_345472_, p_345022_))) {
            Entity entity = switch (p_346387_.affected()) {
                case ATTACKER -> p_345022_.getEntity();
                case DAMAGING_ENTITY -> p_345022_.getDirectEntity();
                case VICTIM -> p_345472_;
            };
            if (entity != null) {
                p_346387_.effect().apply(p_345844_, p_344928_, p_345486_, entity, entity.position());
            }
        }
    }

    public void doPostAttack(
        ServerLevel p_344857_, int p_44688_, EnchantedItemInUse p_345323_, EnchantmentTarget p_345287_, Entity p_44687_, DamageSource p_345177_
    ) {
        for (TargetedConditionalEffect<EnchantmentEntityEffect> targetedconditionaleffect : this.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
            if (p_345287_ == targetedconditionaleffect.enchanted()) {
                doPostAttack(targetedconditionaleffect, p_344857_, p_44688_, p_345323_, p_44687_, p_345177_);
            }
        }
    }

    public void modifyProjectileCount(ServerLevel p_345353_, int p_344837_, ItemStack p_346382_, Entity p_345611_, MutableFloat p_344765_) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_COUNT, p_345353_, p_344837_, p_346382_, p_345611_, p_344765_);
    }

    public void modifyProjectileSpread(ServerLevel p_346170_, int p_345846_, ItemStack p_346147_, Entity p_345832_, MutableFloat p_345361_) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_SPREAD, p_346170_, p_345846_, p_346147_, p_345832_, p_345361_);
    }

    public void modifyCrossbowChargeTime(RandomSource p_347712_, int p_345448_, MutableFloat p_345713_) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME, p_347712_, p_345448_, p_345713_);
    }

    public void modifyUnfilteredValue(DataComponentType<EnchantmentValueEffect> p_347468_, RandomSource p_347513_, int p_347494_, MutableFloat p_347716_) {
        EnchantmentValueEffect enchantmentvalueeffect = this.effects.get(p_347468_);
        if (enchantmentvalueeffect != null) {
            p_347716_.setValue(enchantmentvalueeffect.process(p_347494_, p_347513_, p_347716_.floatValue()));
        }
    }

    public void tick(ServerLevel p_345064_, int p_346148_, EnchantedItemInUse p_345056_, Entity p_345139_) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.TICK),
            entityContext(p_345064_, p_346148_, p_345139_, p_345139_.position()),
            p_345592_ -> p_345592_.apply(p_345064_, p_346148_, p_345056_, p_345139_, p_345139_.position())
        );
    }

    public void onProjectileSpawned(ServerLevel p_345440_, int p_346424_, EnchantedItemInUse p_346046_, Entity p_345958_) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED),
            entityContext(p_345440_, p_346424_, p_345958_, p_345958_.position()),
            p_346231_ -> p_346231_.apply(p_345440_, p_346424_, p_346046_, p_345958_, p_345958_.position())
        );
    }

    public void onHitBlock(ServerLevel p_345175_, int p_346193_, EnchantedItemInUse p_344721_, Entity p_345951_, Vec3 p_344878_, BlockState p_351026_) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.HIT_BLOCK),
            blockHitContext(p_345175_, p_346193_, p_345951_, p_344878_, p_351026_),
            p_346325_ -> p_346325_.apply(p_345175_, p_346193_, p_344721_, p_345951_, p_344878_)
        );
    }

    public void modifyItemFilteredCount(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> p_345794_,
        ServerLevel p_345992_,
        int p_345038_,
        ItemStack p_345886_,
        MutableFloat p_345188_
    ) {
        applyEffects(
            this.getEffects(p_345794_),
            itemContext(p_345992_, p_345038_, p_345886_),
            p_347300_ -> p_345188_.setValue(p_347300_.process(p_345038_, p_345992_.getRandom(), p_345188_.getValue()))
        );
    }

    public void modifyEntityFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> p_345986_,
        ServerLevel p_345473_,
        int p_345352_,
        ItemStack p_345076_,
        Entity p_345170_,
        MutableFloat p_345910_
    ) {
        applyEffects(
            this.getEffects(p_345986_),
            entityContext(p_345473_, p_345352_, p_345170_, p_345170_.position()),
            p_347312_ -> p_345910_.setValue(p_347312_.process(p_345352_, p_345170_.getRandom(), p_345910_.floatValue()))
        );
    }

    public void modifyDamageFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> p_345367_,
        ServerLevel p_345784_,
        int p_344796_,
        ItemStack p_345410_,
        Entity p_345673_,
        DamageSource p_345768_,
        MutableFloat p_345664_
    ) {
        applyEffects(
            this.getEffects(p_345367_),
            damageContext(p_345784_, p_344796_, p_345673_, p_345768_),
            p_347304_ -> p_345664_.setValue(p_347304_.process(p_344796_, p_345673_.getRandom(), p_345664_.floatValue()))
        );
    }

    public static LootContext damageContext(ServerLevel p_346018_, int p_345520_, Entity p_345257_, DamageSource p_346340_) {
        LootParams lootparams = new LootParams.Builder(p_346018_)
            .withParameter(LootContextParams.THIS_ENTITY, p_345257_)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, p_345520_)
            .withParameter(LootContextParams.ORIGIN, p_345257_.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, p_346340_)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, p_346340_.getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, p_346340_.getDirectEntity())
            .create(LootContextParamSets.ENCHANTED_DAMAGE);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static LootContext itemContext(ServerLevel p_345514_, int p_345186_, ItemStack p_344997_) {
        LootParams lootparams = new LootParams.Builder(p_345514_)
            .withParameter(LootContextParams.TOOL, p_344997_)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, p_345186_)
            .create(LootContextParamSets.ENCHANTED_ITEM);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static LootContext locationContext(ServerLevel p_345134_, int p_346182_, Entity p_345416_, boolean p_345862_) {
        LootParams lootparams = new LootParams.Builder(p_345134_)
            .withParameter(LootContextParams.THIS_ENTITY, p_345416_)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, p_346182_)
            .withParameter(LootContextParams.ORIGIN, p_345416_.position())
            .withParameter(LootContextParams.ENCHANTMENT_ACTIVE, p_345862_)
            .create(LootContextParamSets.ENCHANTED_LOCATION);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static LootContext entityContext(ServerLevel p_346134_, int p_346059_, Entity p_346146_, Vec3 p_345814_) {
        LootParams lootparams = new LootParams.Builder(p_346134_)
            .withParameter(LootContextParams.THIS_ENTITY, p_346146_)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, p_346059_)
            .withParameter(LootContextParams.ORIGIN, p_345814_)
            .create(LootContextParamSets.ENCHANTED_ENTITY);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static LootContext blockHitContext(ServerLevel p_350329_, int p_350503_, Entity p_350451_, Vec3 p_350874_, BlockState p_350311_) {
        LootParams lootparams = new LootParams.Builder(p_350329_)
            .withParameter(LootContextParams.THIS_ENTITY, p_350451_)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, p_350503_)
            .withParameter(LootContextParams.ORIGIN, p_350874_)
            .withParameter(LootContextParams.BLOCK_STATE, p_350311_)
            .create(LootContextParamSets.HIT_BLOCK);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static <T> void applyEffects(List<ConditionalEffect<T>> p_345860_, LootContext p_345578_, Consumer<T> p_346164_) {
        for (ConditionalEffect<T> conditionaleffect : p_345860_) {
            if (conditionaleffect.matches(p_345578_)) {
                p_346164_.accept(conditionaleffect.effect());
            }
        }
    }

    public void runLocationChangedEffects(ServerLevel p_345522_, int p_345228_, EnchantedItemInUse p_345521_, LivingEntity p_346133_) {
        if (p_345521_.inSlot() != null && !this.matchingSlot(p_345521_.inSlot())) {
            Set<EnchantmentLocationBasedEffect> set1 = p_346133_.activeLocationDependentEnchantments().remove(this);
            if (set1 != null) {
                set1.forEach(p_352862_ -> p_352862_.onDeactivated(p_345521_, p_346133_, p_346133_.position(), p_345228_));
            }
        } else {
            Set<EnchantmentLocationBasedEffect> set = p_346133_.activeLocationDependentEnchantments().get(this);

            for (ConditionalEffect<EnchantmentLocationBasedEffect> conditionaleffect : this.getEffects(EnchantmentEffectComponents.LOCATION_CHANGED)) {
                EnchantmentLocationBasedEffect enchantmentlocationbasedeffect = conditionaleffect.effect();
                boolean flag = set != null && set.contains(enchantmentlocationbasedeffect);
                if (conditionaleffect.matches(locationContext(p_345522_, p_345228_, p_346133_, flag))) {
                    if (!flag) {
                        if (set == null) {
                            set = new ObjectArraySet<>();
                            p_346133_.activeLocationDependentEnchantments().put(this, set);
                        }

                        set.add(enchantmentlocationbasedeffect);
                    }

                    enchantmentlocationbasedeffect.onChangedBlock(p_345522_, p_345228_, p_345521_, p_346133_, p_346133_.position(), !flag);
                } else if (set != null && set.remove(enchantmentlocationbasedeffect)) {
                    enchantmentlocationbasedeffect.onDeactivated(p_345521_, p_346133_, p_346133_.position(), p_345228_);
                }
            }

            if (set != null && set.isEmpty()) {
                p_346133_.activeLocationDependentEnchantments().remove(this);
            }
        }
    }

    public void stopLocationBasedEffects(int p_345030_, EnchantedItemInUse p_345497_, LivingEntity p_344904_) {
        Set<EnchantmentLocationBasedEffect> set = p_344904_.activeLocationDependentEnchantments().remove(this);
        if (set != null) {
            for (EnchantmentLocationBasedEffect enchantmentlocationbasedeffect : set) {
                enchantmentlocationbasedeffect.onDeactivated(p_345497_, p_344904_, p_344904_.position(), p_345030_);
            }
        }
    }

    public static Enchantment.Builder enchantment(Enchantment.EnchantmentDefinition p_345873_) {
        return new Enchantment.Builder(p_345873_);
    }

//    TODO: Reimplement. Not sure if we want to patch EnchantmentDefinition or hack this in as an EnchantmentEffectComponent.
//    /**
//     * Is this enchantment allowed to be enchanted on books via Enchantment Table
//     * @return false to disable the vanilla feature
//     */
//    public boolean isAllowedOnBooks() {
//        return true;
//    }

    public static class Builder {
        private final Enchantment.EnchantmentDefinition definition;
        private HolderSet<Enchantment> exclusiveSet = HolderSet.direct();
        private final Map<DataComponentType<?>, List<?>> effectLists = new HashMap<>();
        private final DataComponentMap.Builder effectMapBuilder = DataComponentMap.builder();

        /**
         * Neo: Allow customizing or changing the {@link Component} created by the enchantment builder.
         */
        protected java.util.function.UnaryOperator<MutableComponent> nameFactory = java.util.function.UnaryOperator.identity();

        public Builder(Enchantment.EnchantmentDefinition p_345317_) {
            this.definition = p_345317_;
        }

        public Enchantment.Builder exclusiveWith(HolderSet<Enchantment> p_346264_) {
            this.exclusiveSet = p_346264_;
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> p_345261_, E p_346342_, LootItemCondition.Builder p_344864_) {
            this.getEffectsList(p_345261_).add(new ConditionalEffect<>(p_346342_, Optional.of(p_344864_.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> p_345254_, E p_346178_) {
            this.getEffectsList(p_345254_).add(new ConditionalEffect<>(p_346178_, Optional.empty()));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> p_345227_,
            EnchantmentTarget p_346076_,
            EnchantmentTarget p_345283_,
            E p_346194_,
            LootItemCondition.Builder p_345933_
        ) {
            this.getEffectsList(p_345227_).add(new TargetedConditionalEffect<>(p_346076_, p_345283_, p_346194_, Optional.of(p_345933_.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> p_346304_, EnchantmentTarget p_346333_, EnchantmentTarget p_345829_, E p_345094_
        ) {
            this.getEffectsList(p_346304_).add(new TargetedConditionalEffect<>(p_346333_, p_345829_, p_345094_, Optional.empty()));
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<List<EnchantmentAttributeEffect>> p_345372_, EnchantmentAttributeEffect p_345754_) {
            this.getEffectsList(p_345372_).add(p_345754_);
            return this;
        }

        public <E> Enchantment.Builder withSpecialEffect(DataComponentType<E> p_346431_, E p_344725_) {
            this.effectMapBuilder.set(p_346431_, p_344725_);
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<Unit> p_345500_) {
            this.effectMapBuilder.set(p_345500_, Unit.INSTANCE);
            return this;
        }

        /**
         * Allows specifying an operator that can customize the default {@link Component} created by {@link #build(ResourceLocation)}.
         *
         * @return this
         */
        public Enchantment.Builder withCustomName(java.util.function.UnaryOperator<MutableComponent> nameFactory) {
            this.nameFactory = nameFactory;
            return this;
        }

        private <E> List<E> getEffectsList(DataComponentType<List<E>> p_344770_) {
            return (List<E>)this.effectLists.computeIfAbsent(p_344770_, p_346247_ -> {
                ArrayList<E> arraylist = new ArrayList<>();
                this.effectMapBuilder.set(p_344770_, arraylist);
                return arraylist;
            });
        }

        public Enchantment build(ResourceLocation p_344988_) {
            return new Enchantment(
                // Neo: permit custom name components instead of a single hardcoded translatable component.
                this.nameFactory.apply(Component.translatable(Util.makeDescriptionId("enchantment", p_344988_))),
                this.definition, this.exclusiveSet, this.effectMapBuilder.build()
            );
        }
    }

    public static record Cost(int base, int perLevelAboveFirst) {
        public static final Codec<Enchantment.Cost> CODEC = RecordCodecBuilder.create(
            p_345979_ -> p_345979_.group(
                        Codec.INT.fieldOf("base").forGetter(Enchantment.Cost::base),
                        Codec.INT.fieldOf("per_level_above_first").forGetter(Enchantment.Cost::perLevelAboveFirst)
                    )
                    .apply(p_345979_, Enchantment.Cost::new)
        );

        public int calculate(int p_335917_) {
            return this.base + this.perLevelAboveFirst * (p_335917_ - 1);
        }
    }

    public static record EnchantmentDefinition(
        HolderSet<Item> supportedItems,
        Optional<HolderSet<Item>> primaryItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        List<EquipmentSlotGroup> slots
    ) {
        public static final MapCodec<Enchantment.EnchantmentDefinition> CODEC = RecordCodecBuilder.mapCodec(
            p_344890_ -> p_344890_.group(
                        RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("supported_items").forGetter(Enchantment.EnchantmentDefinition::supportedItems),
                        RegistryCodecs.homogeneousList(Registries.ITEM)
                            .optionalFieldOf("primary_items")
                            .forGetter(Enchantment.EnchantmentDefinition::primaryItems),
                        ExtraCodecs.intRange(1, 1024).fieldOf("weight").forGetter(Enchantment.EnchantmentDefinition::weight),
                        ExtraCodecs.intRange(1, 255).fieldOf("max_level").forGetter(Enchantment.EnchantmentDefinition::maxLevel),
                        Enchantment.Cost.CODEC.fieldOf("min_cost").forGetter(Enchantment.EnchantmentDefinition::minCost),
                        Enchantment.Cost.CODEC.fieldOf("max_cost").forGetter(Enchantment.EnchantmentDefinition::maxCost),
                        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("anvil_cost").forGetter(Enchantment.EnchantmentDefinition::anvilCost),
                        EquipmentSlotGroup.CODEC.listOf().fieldOf("slots").forGetter(Enchantment.EnchantmentDefinition::slots)
                    )
                    .apply(p_344890_, Enchantment.EnchantmentDefinition::new)
        );
    }
}
