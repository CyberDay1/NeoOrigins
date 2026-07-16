package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * DeferredRegister for the mob-origin global-loot-modifier codec. The single
 * registered type is {@code neoorigins:mob_origin_drops}; pack authors (or the
 * generator {@link com.cyberday1.neoorigins.service.MobLootModifierGenerator})
 * activate it by referencing the id in
 * {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}.
 */
public final class MobOriginLootModifiers {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
            NeoOrigins.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>,
            MapCodec<MobOriginDropsLootModifier>> MOB_ORIGIN_DROPS =
        SERIALIZERS.register("mob_origin_drops", () -> MobOriginDropsLootModifier.CODEC);

    /**
     * {@code neoorigins:kill_loot_drops} — keyed on the KILLER's active
     * {@link com.cyberday1.neoorigins.power.builtin.KillLootDropsPower} rules,
     * layering extra drops onto the killed mob's vanilla loot. Activated by the
     * same {@code global_loot_modifiers.json} carrier mechanism as
     * {@code mob_origin_drops}.
     */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>,
            MapCodec<KillLootDropsLootModifier>> KILL_LOOT_DROPS =
        SERIALIZERS.register("kill_loot_drops", () -> KillLootDropsLootModifier.CODEC);

    private MobOriginLootModifiers() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
