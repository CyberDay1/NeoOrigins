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

public interface EnchantmentEntityEffect extends EnchantmentLocationBasedEffect {
    Codec<EnchantmentEntityEffect> CODEC = BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE
        .byNameCodec()
        .dispatch(EnchantmentEntityEffect::codec, Function.identity());

    static MapCodec<? extends EnchantmentEntityEffect> bootstrap(Registry<MapCodec<? extends EnchantmentEntityEffect>> p_345205_) {
        Registry.register(p_345205_, "all_of", AllOf.EntityEffects.CODEC);
        Registry.register(p_345205_, "apply_mob_effect", ApplyMobEffect.CODEC);
        Registry.register(p_345205_, "damage_entity", DamageEntity.CODEC);
        Registry.register(p_345205_, "damage_item", DamageItem.CODEC);
        Registry.register(p_345205_, "explode", ExplodeEffect.CODEC);
        Registry.register(p_345205_, "ignite", Ignite.CODEC);
        Registry.register(p_345205_, "play_sound", PlaySoundEffect.CODEC);
        Registry.register(p_345205_, "replace_block", ReplaceBlock.CODEC);
        Registry.register(p_345205_, "replace_disk", ReplaceDisk.CODEC);
        Registry.register(p_345205_, "run_function", RunFunction.CODEC);
        Registry.register(p_345205_, "set_block_properties", SetBlockProperties.CODEC);
        Registry.register(p_345205_, "spawn_particles", SpawnParticlesEffect.CODEC);
        return Registry.register(p_345205_, "summon_entity", SummonEntityEffect.CODEC);
    }

    void apply(ServerLevel p_345106_, int p_346004_, EnchantedItemInUse p_344966_, Entity p_346140_, Vec3 p_345890_);

    @Override
    default void onChangedBlock(ServerLevel p_345419_, int p_345173_, EnchantedItemInUse p_344724_, Entity p_346126_, Vec3 p_345614_, boolean p_346410_) {
        this.apply(p_345419_, p_345173_, p_344724_, p_346126_, p_345614_);
    }

    @Override
    MapCodec<? extends EnchantmentEntityEffect> codec();
}
