package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.power.builtin.FortuneWhenEffectPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Optional;

/**
 * Applies Fortune-style drop multiplication on {@link BlockDropsEvent} for
 * players with an active {@link FortuneWhenEffectPower} whose gating
 * {@code MobEffect} is present.
 *
 * <p>Drop math mirrors vanilla {@code ApplyBonusCount.ORE_DROPS}:
 * {@code count = count * (max(0, random(level + 2) - 1) + 1)}. This gives
 * the same rolling distribution as a real Fortune N pickaxe — Fortune I can
 * give 1 or 2×, Fortune III can give 1–4×, etc.
 *
 * <p>Target selection uses the configured block tag (default
 * {@code #c:ores}). Ancient debris is hardcoded-excluded because netherite
 * is the single vanilla ore that ignores Fortune.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class FortuneEffectEvents {

    private FortuneEffectEvents() {}

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer sp)) return;
        BlockState state = event.getState();
        if (state.is(Blocks.ANCIENT_DEBRIS)) return;

        for (PowerHolder<?> holder : ActiveOriginService.allPowers(sp)) {
            if (!(holder.type() instanceof FortuneWhenEffectPower)) continue;
            FortuneWhenEffectPower.Config config = (FortuneWhenEffectPower.Config) holder.config();
            if (config.level() <= 0) continue;
            if (!state.is(parseBlockTag(config.target()))) continue;
            if (!hasEffect(sp, config.effect())) continue;

            applyFortuneBonus(sp, event, config.level());
            return;  // one power handles the drops; stacking is intentional single-apply
        }
    }

    private static void applyFortuneBonus(ServerPlayer sp, BlockDropsEvent event, int level) {
        var rand = sp.getRandom();
        for (var dropItem : event.getDrops()) {
            ItemStack stack = dropItem.getItem();
            if (stack.isEmpty()) continue;
            int bonus = Math.max(0, rand.nextInt(level + 2) - 1);
            if (bonus > 0) {
                stack.setCount(stack.getCount() * (bonus + 1));
            }
        }
    }

    private static TagKey<Block> parseBlockTag(String raw) {
        String trimmed = raw.startsWith("#") ? raw.substring(1) : raw;
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(trimmed));
    }

    private static boolean hasEffect(ServerPlayer sp, ResourceLocation effectId) {
        Optional<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getOptional(effectId);
        if (effect.isEmpty()) return false;
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect.get());
        return sp.hasEffect(holder);
    }
}
