package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public interface EnchantmentEffectComponents {
    Codec<DataComponentType<?>> COMPONENT_CODEC = Codec.lazyInitialized(() -> BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.byNameCodec());
    Codec<DataComponentMap> CODEC = DataComponentMap.makeCodec(COMPONENT_CODEC);
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> DAMAGE_PROTECTION = register(
        "damage_protection",
        p_346197_ -> p_346197_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf())
    );
    DataComponentType<List<ConditionalEffect<DamageImmunity>>> DAMAGE_IMMUNITY = register(
        "damage_immunity", p_345263_ -> p_345263_.persistent(ConditionalEffect.codec(DamageImmunity.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> DAMAGE = register(
        "damage", p_345923_ -> p_345923_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> SMASH_DAMAGE_PER_FALLEN_BLOCK = register(
        "smash_damage_per_fallen_block",
        p_346058_ -> p_346058_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> KNOCKBACK = register(
        "knockback", p_345971_ -> p_345971_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> ARMOR_EFFECTIVENESS = register(
        "armor_effectiveness",
        p_344808_ -> p_344808_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf())
    );
    DataComponentType<List<TargetedConditionalEffect<EnchantmentEntityEffect>>> POST_ATTACK = register(
        "post_attack",
        p_345098_ -> p_345098_.persistent(TargetedConditionalEffect.codec(EnchantmentEntityEffect.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentEntityEffect>>> HIT_BLOCK = register(
        "hit_block", p_350170_ -> p_350170_.persistent(ConditionalEffect.codec(EnchantmentEntityEffect.CODEC, LootContextParamSets.HIT_BLOCK).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> ITEM_DAMAGE = register(
        "item_damage", p_346356_ -> p_346356_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ITEM).listOf())
    );
    DataComponentType<List<EnchantmentAttributeEffect>> ATTRIBUTES = register(
        "attributes", p_345468_ -> p_345468_.persistent(EnchantmentAttributeEffect.CODEC.codec().listOf())
    );
    DataComponentType<List<TargetedConditionalEffect<EnchantmentValueEffect>>> EQUIPMENT_DROPS = register(
        "equipment_drops",
        p_345869_ -> p_345869_.persistent(
                TargetedConditionalEffect.equipmentDropsCodec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf()
            )
    );
    DataComponentType<List<ConditionalEffect<EnchantmentLocationBasedEffect>>> LOCATION_CHANGED = register(
        "location_changed",
        p_346078_ -> p_346078_.persistent(ConditionalEffect.codec(EnchantmentLocationBasedEffect.CODEC, LootContextParamSets.ENCHANTED_LOCATION).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentEntityEffect>>> TICK = register(
        "tick", p_345270_ -> p_345270_.persistent(ConditionalEffect.codec(EnchantmentEntityEffect.CODEC, LootContextParamSets.ENCHANTED_ENTITY).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> AMMO_USE = register(
        "ammo_use", p_345561_ -> p_345561_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ITEM).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> PROJECTILE_PIERCING = register(
        "projectile_piercing",
        p_345446_ -> p_345446_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ITEM).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentEntityEffect>>> PROJECTILE_SPAWNED = register(
        "projectile_spawned",
        p_344930_ -> p_344930_.persistent(ConditionalEffect.codec(EnchantmentEntityEffect.CODEC, LootContextParamSets.ENCHANTED_ENTITY).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> PROJECTILE_SPREAD = register(
        "projectile_spread",
        p_345532_ -> p_345532_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ENTITY).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> PROJECTILE_COUNT = register(
        "projectile_count",
        p_345055_ -> p_345055_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ENTITY).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> TRIDENT_RETURN_ACCELERATION = register(
        "trident_return_acceleration",
        p_345548_ -> p_345548_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ENTITY).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> FISHING_TIME_REDUCTION = register(
        "fishing_time_reduction",
        p_345828_ -> p_345828_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ENTITY).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> FISHING_LUCK_BONUS = register(
        "fishing_luck_bonus",
        p_345008_ -> p_345008_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ENTITY).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> BLOCK_EXPERIENCE = register(
        "block_experience",
        p_344756_ -> p_344756_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ITEM).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> MOB_EXPERIENCE = register(
        "mob_experience",
        p_345613_ -> p_345613_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ENTITY).listOf())
    );
    DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> REPAIR_WITH_XP = register(
        "repair_with_xp",
        p_344828_ -> p_344828_.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_ITEM).listOf())
    );
    DataComponentType<EnchantmentValueEffect> CROSSBOW_CHARGE_TIME = register(
        "crossbow_charge_time", p_347314_ -> p_347314_.persistent(EnchantmentValueEffect.CODEC)
    );
    DataComponentType<List<CrossbowItem.ChargingSounds>> CROSSBOW_CHARGING_SOUNDS = register(
        "crossbow_charging_sounds", p_345990_ -> p_345990_.persistent(CrossbowItem.ChargingSounds.CODEC.listOf())
    );
    DataComponentType<List<Holder<SoundEvent>>> TRIDENT_SOUND = register("trident_sound", p_345208_ -> p_345208_.persistent(SoundEvent.CODEC.listOf()));
    DataComponentType<Unit> PREVENT_EQUIPMENT_DROP = register("prevent_equipment_drop", p_346368_ -> p_346368_.persistent(Unit.CODEC));
    DataComponentType<Unit> PREVENT_ARMOR_CHANGE = register("prevent_armor_change", p_345721_ -> p_345721_.persistent(Unit.CODEC));
    DataComponentType<EnchantmentValueEffect> TRIDENT_SPIN_ATTACK_STRENGTH = register(
        "trident_spin_attack_strength", p_347313_ -> p_347313_.persistent(EnchantmentValueEffect.CODEC)
    );

    static DataComponentType<?> bootstrap(Registry<DataComponentType<?>> p_345744_) {
        return DAMAGE_PROTECTION;
    }

    private static <T> DataComponentType<T> register(String p_346249_, UnaryOperator<DataComponentType.Builder<T>> p_345843_) {
        return Registry.register(BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, p_346249_, p_345843_.apply(DataComponentType.builder()).build());
    }
}
