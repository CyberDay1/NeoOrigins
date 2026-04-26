package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public interface EnchantmentLocationBasedEffect {
    Codec<EnchantmentLocationBasedEffect> CODEC = BuiltInRegistries.ENCHANTMENT_LOCATION_BASED_EFFECT_TYPE
        .byNameCodec()
        .dispatch(EnchantmentLocationBasedEffect::codec, Function.identity());

    static MapCodec<? extends EnchantmentLocationBasedEffect> bootstrap(Registry<MapCodec<? extends EnchantmentLocationBasedEffect>> p_344955_) {
        Registry.register(p_344955_, "all_of", AllOf.LocationBasedEffects.CODEC);
        Registry.register(p_344955_, "apply_mob_effect", ApplyMobEffect.CODEC);
        Registry.register(p_344955_, "attribute", EnchantmentAttributeEffect.CODEC);
        Registry.register(p_344955_, "damage_entity", DamageEntity.CODEC);
        Registry.register(p_344955_, "damage_item", DamageItem.CODEC);
        Registry.register(p_344955_, "explode", ExplodeEffect.CODEC);
        Registry.register(p_344955_, "ignite", Ignite.CODEC);
        Registry.register(p_344955_, "play_sound", PlaySoundEffect.CODEC);
        Registry.register(p_344955_, "replace_block", ReplaceBlock.CODEC);
        Registry.register(p_344955_, "replace_disk", ReplaceDisk.CODEC);
        Registry.register(p_344955_, "run_function", RunFunction.CODEC);
        Registry.register(p_344955_, "set_block_properties", SetBlockProperties.CODEC);
        Registry.register(p_344955_, "spawn_particles", SpawnParticlesEffect.CODEC);
        return Registry.register(p_344955_, "summon_entity", SummonEntityEffect.CODEC);
    }

    void onChangedBlock(ServerLevel p_345577_, int p_345675_, EnchantedItemInUse p_345807_, Entity p_345378_, Vec3 p_344844_, boolean p_345637_);

    default void onDeactivated(EnchantedItemInUse p_346131_, Entity p_345920_, Vec3 p_345758_, int p_344750_) {
    }

    MapCodec<? extends EnchantmentLocationBasedEffect> codec();
}
