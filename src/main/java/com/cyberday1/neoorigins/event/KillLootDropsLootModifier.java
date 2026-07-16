package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.power.builtin.KillLootDropsPower;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.List;

/**
 * Global loot modifier that layers a KILLER's {@code kill_loot_drops} rules
 * onto the vanilla loot of the mob they just killed — so the extra items flow
 * through the real loot pipeline (fortune, looting, drop-events) as genuine mob
 * drops rather than inventory inserts.
 *
 * <p>Sibling of {@link MobOriginDropsLootModifier}, but keyed on the
 * {@link LootContextParams#LAST_DAMAGE_PLAYER} (the killer) instead of the
 * dying mob's origin. The active rules are published per-player by
 * {@link KillLootDropsPower} while the power is granted; this modifier only
 * reads that in-memory registry, so its carrier file is data-free (see
 * {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}).
 *
 * <p>Fast-path: no player killer, no active rules for that killer, or a
 * non-living / player victim all return {@code generatedLoot} unchanged with
 * zero allocations, mirroring the mob-origin modifier's early returns.
 */
public final class KillLootDropsLootModifier extends LootModifier {

    public static final MapCodec<KillLootDropsLootModifier> CODEC =
        RecordCodecBuilder.mapCodec(inst -> codecStart(inst)
            .apply(inst, KillLootDropsLootModifier::new));

    // 26.x LootModifier carries a `priority` field added in its codecStart's P2.
    public KillLootDropsLootModifier(LootItemCondition[] conditions, int priority) {
        super(conditions, priority);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                 LootContext context) {
        // 1. Resolve the killer as a Player. Prefer LAST_DAMAGE_PLAYER; fall
        //    back to ATTACKING_ENTITY only when it is itself a Player.
        Player killer = resolveKiller(context);
        if (killer == null) return generatedLoot;

        // 2. Look up the killer's active rules; bail on none.
        List<KillLootDropsPower.Rule> rules =
            KillLootDropsPower.activeRules(killer.getUUID());
        if (rules == null || rules.isEmpty()) return generatedLoot;

        // 3. Resolve the dying mob (skip players).
        // 26.x: LootContext.getParamOrNull → getOptionalParameter.
        Entity victim = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (!(victim instanceof LivingEntity living) || victim instanceof Player) return generatedLoot;

        // 4. Roll each matching rule independently.
        RandomSource random = context.getRandom();
        for (KillLootDropsPower.Rule rule : rules) {
            if (!rule.target().matches(living.getType())) continue;
            if (rule.chance() < 1.0 && random.nextDouble() >= rule.chance()) continue;
            if (rule.item() == null) continue;
            int count = Math.max(1, rule.count());
            generatedLoot.add(new ItemStack(rule.item(), count));
        }
        return generatedLoot;
    }

    private static Player resolveKiller(LootContext context) {
        Player last = context.getOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER);
        if (last != null) return last;
        // ATTACKING_ENTITY is the entity that dealt the killing blow. Use it only
        // when it is itself a Player.
        Entity attacker = context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY);
        if (attacker instanceof Player p) return p;
        return null;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return MobOriginLootModifiers.KILL_LOOT_DROPS.get();
    }
}
