package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.event.PowerGrantedEvent;
import com.cyberday1.neoorigins.api.event.PowerRevokedEvent;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.compat.EffectImmunityPower;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.power.builtin.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class OriginEventHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        CompatTickScheduler.tick(sp);
        forEachActivePower(sp, holder -> holder.onTick(sp));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        // Check if player needs origin selection
        boolean needsOrigin = false;
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (!data.hasOriginForLayer(layer.id())) {
                needsOrigin = true;
                break;
            }
        }
        // Re-apply all powers on login
        forEachActivePower(sp, holder -> holder.onLogin(sp));
        // Send sync
        NeoOriginsNetwork.syncToPlayer(sp);
        // Open selection screen if needed
        if (needsOrigin) {
            NeoOriginsNetwork.openSelectionScreen(sp, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // Use onRespawn: Route A powers re-apply modifiers; Route B powers run respawn actions.
        forEachActivePower(sp, holder -> holder.onRespawn(sp));
        NeoOriginsNetwork.syncToPlayer(sp);
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (hasPowerOfType(sp, PreventActionPower.class, config -> config.action() == PreventActionPower.Action.FALL_DAMAGE)) {
            event.setCanceled(true);
        }
        // Conditional no fall damage (e.g. Arachnid while climbing)
        if (sp.onClimbable() && hasPowerOfType(sp, ConditionalPower.class, config ->
                config.condition() == ConditionalPower.Condition.CLIMBING &&
                config.innerPower().getPath().contains("no_fall"))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // Prevent fire damage
        if (event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.ON_FIRE) || event.getSource().is(DamageTypes.LAVA)) {
            if (hasPowerOfType(sp, PreventActionPower.class, config -> config.action() == PreventActionPower.Action.FIRE)) {
                event.setCanceled(true);
                return;
            }
        }

        // Water damage (Blazeling)
        if (event.getSource().is(DamageTypes.DROWN)) {
            if (hasPowerOfType(sp, PreventActionPower.class, config -> config.action() == PreventActionPower.Action.DROWN)) {
                event.setCanceled(true);
                return;
            }
        }

        // Apply damage multipliers
        forEachActivePowerOfType(sp, ModifyDamagePower.class, (holder) -> {
            ModifyDamagePower.Config config = (ModifyDamagePower.Config) holder.config();
            if (config.direction() == ModifyDamagePower.Direction.IN) {
                // Check damage type filter
                if (config.damageType().isEmpty() || event.getSource().getMsgId().equalsIgnoreCase(config.damageType().get())) {
                    event.setAmount(event.getAmount() * config.multiplier());
                }
            }
        });

        // Notify Route B self_action_when_hit powers and new built-ins if damage was not blocked
        if (!event.isCanceled()) {
            float amount = event.getAmount();
            forEachActivePower(sp, holder -> holder.onHit(sp, amount));

            // Thorns aura — reflect melee damage back to attacker
            var attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity le) {
                forEachActivePowerOfType(sp, ThornsAuraPower.class, holder -> {
                    ThornsAuraPower.Config cfg = (ThornsAuraPower.Config) holder.config();
                    le.hurt(sp.damageSources().magic(), amount * cfg.returnRatio());
                });
            }
        }
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MobEffectInstance effectInstance = event.getEffectInstance();
        if (effectInstance == null) return;
        Holder<MobEffect> effectHolder = effectInstance.getEffect();
        var effectKey = BuiltInRegistries.MOB_EFFECT.getKey(effectHolder.value());
        if (effectKey == null) return;
        String effectId = effectKey.toString();
        if (hasPowerOfType(sp, EffectImmunityPower.class,
                config -> config.effects().contains(effectId))) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        // EntityGroupPower: undead players are immune to Poison
        if (effectId.equals("minecraft:poison") &&
                hasPowerOfType(sp, EntityGroupPower.class, EntityGroupPower.Config::isUndead)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // ---------- New event handlers ----------

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        var killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer sp)) return;
        LivingEntity killed = event.getEntity();
        forEachActivePower(sp, holder -> holder.onKill(sp, killed));
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        forEachActivePowerOfType(sp, KnockbackModifierPower.class, holder -> {
            KnockbackModifierPower.Config cfg = (KnockbackModifierPower.Config) holder.config();
            if (cfg.multiplier() <= 0.0f) {
                event.setCanceled(true);
            } else {
                event.setStrength(event.getStrength() * cfg.multiplier());
            }
        });
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        forEachActivePowerOfType(sp, BreakSpeedModifierPower.class, holder -> {
            BreakSpeedModifierPower.Config cfg = (BreakSpeedModifierPower.Config) holder.config();
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, cfg.blockTag());
            if (event.getState().is(tag)) {
                event.setNewSpeed(event.getNewSpeed() * cfg.multiplier());
            }
        });
        if (hasPowerOfType(sp, UnderwaterMiningSpeedPower.class, c -> true)
                && sp.isInWater() && !sp.onGround()) {
            event.setNewSpeed(event.getNewSpeed() * 5.0f);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof ServerPlayer sp)) return;
        var proj = event.getProjectile();
        if (hasPowerOfType(sp, ProjectileImmunityPower.class, cfg -> cfg.blocks(proj))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer sp)) return;
        Identifier mobTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (mobTypeId == null) return;
        if (hasPowerOfType(sp, MobsIgnorePlayerPower.class,
                cfg -> cfg.entityTypes().isEmpty() || cfg.entityTypes().contains(mobTypeId))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Mob mob = event.getEntity();
        for (ServerPlayer sp : sl.players()) {
            if (!hasPowerOfType(sp, NoMobSpawnsNearbyPower.class, c -> true)) continue;
            forEachActivePowerOfType(sp, NoMobSpawnsNearbyPower.class, holder -> {
                NoMobSpawnsNearbyPower.Config cfg = (NoMobSpawnsNearbyPower.Config) holder.config();
                if (sp.distanceTo(mob) > cfg.radius()) return;
                if (matchesSpawnCategory(cfg, mob)) {
                    event.setSpawnCancelled(true);
                }
            });
        }
    }

    private static boolean matchesSpawnCategory(NoMobSpawnsNearbyPower.Config cfg, Mob mob) {
        if (cfg.coversAll()) return true;
        MobCategory cat = mob.getType().getCategory();
        for (String s : cfg.categories()) {
            if ("monster".equalsIgnoreCase(s) && cat == MobCategory.MONSTER) return true;
            if ("creature".equalsIgnoreCase(s) && cat == MobCategory.CREATURE) return true;
            if ("ambient".equalsIgnoreCase(s) && cat == MobCategory.AMBIENT) return true;
            if ("water_creature".equalsIgnoreCase(s) && cat == MobCategory.WATER_CREATURE) return true;
            if ("water_ambient".equalsIgnoreCase(s) && cat == MobCategory.WATER_AMBIENT) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        final float[] mult = {1.0f};
        forEachActivePowerOfType(sp, NaturalRegenModifierPower.class, holder ->
            mult[0] *= ((NaturalRegenModifierPower.Config) holder.config()).multiplier());
        if (mult[0] != 1.0f) {
            event.setAmount(event.getAmount() * mult[0]);
        }
    }

    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack item = event.getItem();
        forEachActivePowerOfType(sp, FoodRestrictionPower.class, holder -> {
            FoodRestrictionPower.Config cfg = (FoodRestrictionPower.Config) holder.config();
            var tag = TagKey.create(Registries.ITEM, cfg.itemTag());
            boolean inTag = item.is(tag);
            boolean shouldBlock = cfg.isWhitelist() ? !inTag : inTag;
            if (shouldBlock) event.setCanceled(true);
        });
    }

    // ---------- Power lifecycle ----------

    /**
     * Called by NeoOriginsNetwork when a player chooses an origin.
     * Revokes powers from the old origin, grants powers from the new origin.
     */
    public static void applyOriginPowers(ServerPlayer player, Identifier layerId,
                                          Identifier oldOriginId, Identifier newOriginId) {
        // Revoke old powers
        if (oldOriginId != null) {
            Origin oldOrigin = OriginDataManager.INSTANCE.getOrigin(oldOriginId);
            if (oldOrigin != null) {
                for (Identifier powerId : oldOrigin.powers()) {
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null) {
                        holder.onRevoked(player);
                        NeoForge.EVENT_BUS.post(new PowerRevokedEvent(player, powerId));
                    }
                }
            }
        }
        // Grant new powers
        Origin newOrigin = OriginDataManager.INSTANCE.getOrigin(newOriginId);
        if (newOrigin != null) {
            for (Identifier powerId : newOrigin.powers()) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) {
                    holder.onGranted(player);
                    NeoForge.EVENT_BUS.post(new PowerGrantedEvent(player, powerId));
                }
            }
        }
    }

    /** Revoke all powers for all origins. Called on reset. */
    public static void revokeAllPowers(ServerPlayer player) {
        forEachActivePower(player, holder -> holder.onRevoked(player));
    }

    // ---------- Helpers ----------

    /**
     * Returns an ordered list of active (keybind-triggerable) power holders for the player.
     * Slot 0 = first, slot 1 = second, etc. Passive powers are excluded.
     */
    public static List<PowerHolder<?>> getActivePowers(ServerPlayer player) {
        List<PowerHolder<?>> result = new ArrayList<>();
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            for (Identifier powerId : origin.powers()) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null && holder.isActive()) {
                    result.add(holder);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static void forEachActivePower(ServerPlayer player, java.util.function.Consumer<PowerHolder<?>> action) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            for (Identifier powerId : origin.powers()) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) {
                    action.accept(holder);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <C extends com.cyberday1.neoorigins.api.power.PowerConfiguration>
    boolean hasPowerOfType(ServerPlayer player, Class<? extends com.cyberday1.neoorigins.api.power.PowerType<C>> typeClass,
                           java.util.function.Predicate<C> configPredicate) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            for (Identifier powerId : origin.powers()) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null && typeClass.isInstance(holder.type())) {
                    @SuppressWarnings("unchecked")
                    C config = (C) holder.config();
                    if (configPredicate.test(config)) return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static <C extends com.cyberday1.neoorigins.api.power.PowerConfiguration>
    void forEachActivePowerOfType(ServerPlayer player, Class<? extends com.cyberday1.neoorigins.api.power.PowerType<C>> typeClass,
                                   java.util.function.Consumer<PowerHolder<?>> action) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        for (var entry : data.getOrigins().entrySet()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            for (Identifier powerId : origin.powers()) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null && typeClass.isInstance(holder.type())) {
                    action.accept(holder);
                }
            }
        }
    }
}
