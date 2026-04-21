package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.EffectImmunityPower;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class CombatPowerEvents {

    /** Re-entry guard: prevents LongerPotionsPower from recursing into MobEffectEvent.Added. */
    private static final ThreadLocal<Boolean> LENGTHENING_EFFECT = ThreadLocal.withInitial(() -> false);

    // Run at HIGH so our ModifyDamagePower multipliers apply before other mods' handlers,
    // and so VitalsGuards (at LOWEST) stays the final sanitizer regardless of mod order.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // Native InvulnerabilityPower — cancels damage whose source matches configured filters.
        // Runs before any other damage logic so the event is short-circuited cleanly.
        if (ActiveOriginService.has(sp, InvulnerabilityPower.class,
                cfg -> cfg.matches(event.getSource()))) {
            event.setCanceled(true);
            return;
        }

        if (event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.ON_FIRE)
                || event.getSource().is(DamageTypes.LAVA)) {
            if (ActiveOriginService.has(sp, PreventActionPower.class,
                    config -> config.action() == PreventActionPower.Action.FIRE)) {
                event.setCanceled(true);
                return;
            }
        }
        if (event.getSource().is(DamageTypes.DROWN)) {
            if (ActiveOriginService.has(sp, PreventActionPower.class,
                    config -> config.action() == PreventActionPower.Action.DROWN)) {
                event.setCanceled(true);
                return;
            }
        }

        // Route B conditioned_modify_damage — compiled lambdas that can gate on
        // arbitrary conditions, so they run before the un-conditioned native pass.
        ActiveOriginService.forEachOfType(sp, com.cyberday1.neoorigins.compat.CompatPower.class, cfg -> {
            if (cfg.onIncomingDamage() != null) cfg.onIncomingDamage().accept(event);
        });
        if (event.isCanceled()) return;

        ActiveOriginService.forEachOfType(sp, ModifyDamagePower.class, config -> {
            if (config.direction() == ModifyDamagePower.Direction.IN) {
                if (config.damageType().isEmpty() ||
                        event.getSource().getMsgId().equalsIgnoreCase(config.damageType().get())) {
                    // Clamp the result to Float.MAX_VALUE. If we let the
                    // multiplication overflow to +Infinity (which happens
                    // when /kill deals Float.MAX_VALUE damage to a player
                    // whose multiplier is >1), vanilla's damage pipeline
                    // produces NaN absorption/health from `Infinity -
                    // Infinity`, and the player ends up stuck in a
                    // shaking, undying state with NaN health persisted
                    // to NBT — the save-bricking bug reported against
                    // Feline on v1.3.0.
                    float scaled = event.getAmount() * config.multiplier();
                    if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
                    event.setAmount(scaled);
                }
            }
        });

        // ActionOnHitPower — when the attacker is a ServerPlayer, fire its
        // configured self/target actions against the damaged entity.
        var attackerEntity = event.getSource().getEntity();
        if (attackerEntity instanceof ServerPlayer attackerSp && attackerSp != sp) {
            LivingEntity target = event.getEntity();
            final float hitAmount = event.getAmount();
            ActiveOriginService.forEachOfType(attackerSp, ActionOnHitPower.class, config -> {
                if (hitAmount < config.minDamage()) return;
                if (config.damageType().isPresent()
                        && !event.getSource().getMsgId().equalsIgnoreCase(config.damageType().get())) return;
                if (config.targetGroup().isPresent() && !matchesEntityGroup(target, config.targetGroup().get())) return;
                if (config.targetType().isPresent()
                        && !matchesEntityIdOrTag(target, config.targetType().get())) return;
                if (!ActionOnHitPower.rollChance(config)) return;
                ActionOnHitPower.execute(attackerSp, config, target);
            });
        }

        if (!event.isCanceled()) {
            float amount = event.getAmount();
            ActiveOriginService.forEach(sp, holder -> holder.onHit(sp, amount));
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                sp,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.HIT_TAKEN,
                new com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext(amount, event.getSource()));
            // thorns_aura moved to action_on_event with a damage_attacker
            // entity_action (reads HitTakenContext.amount × amount_ratio).
            // The HIT_TAKEN dispatch above runs any such powers.
        }
    }

    /**
     * Matches an entity against an Origins-style "entity group" name (e.g. "undead",
     * "arthropod", "illager", "aquatic") by looking up {@code minecraft:<group>} as
     * a vanilla entity-type tag. Vanilla ships these tags so canonical Origins
     * groups translate directly.
     */
    private static boolean matchesEntityGroup(LivingEntity target, String group) {
        TagKey<EntityType<?>> tag = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("minecraft", group));
        return target.getType().getTags().anyMatch(t -> t.equals(tag));
    }

    /**
     * Matches an entity against a target identifier string. Supports both raw IDs
     * ({@code "minecraft:zombie"}) and tag references ({@code "#mymod:my_tag"}).
     * Shared by ActionOnHit, MobsIgnorePlayer, and ScareEntities filter sites so
     * pack-author JSON is uniform.
     */
    public static boolean matchesEntityIdOrTag(LivingEntity target, String idOrTag) {
        if (idOrTag.startsWith("#")) {
            TagKey<EntityType<?>> tag = TagKey.create(
                Registries.ENTITY_TYPE, Identifier.parse(idOrTag.substring(1)));
            return target.getType().getTags().anyMatch(t -> t.equals(tag));
        }
        return Identifier.parse(idOrTag).equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Check if the dying entity is a tracked minion (notify summoner)
        com.cyberday1.neoorigins.service.MinionTracker.onEntityDeath(event.getEntity());

        // Dispatch DEATH event for the dying player (if applicable)
        if (event.getEntity() instanceof ServerPlayer dyingSp) {
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                dyingSp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.DEATH);
        }

        var killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer sp)) return;
        LivingEntity killed = event.getEntity();
        ActiveOriginService.forEach(sp, holder -> holder.onKill(sp, killed));
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.KILL,
            new com.cyberday1.neoorigins.service.EventPowerIndex.KillContext(killed));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // knockback_modifier moved to action_on_event (MOD_KNOCKBACK). A
        // multiplier <= 0 cancels the knockback outright; otherwise scale
        // event.getStrength(). Clamp non-finite to MAX_VALUE.
        float chained = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_KNOCKBACK, null, event.getStrength());
        if (chained != event.getStrength()) {
            if (chained <= 0.0f) { event.setCanceled(true); return; }
            if (!Float.isFinite(chained)) chained = Float.MAX_VALUE;
            event.setStrength(chained);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        var proj = event.getProjectile();

        // Dispatch PROJECTILE_HIT to the projectile's owning player (if any).
        if (proj.getOwner() instanceof ServerPlayer ownerSp) {
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                ownerSp,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.PROJECTILE_HIT,
                new com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext(
                    proj, event.getRayTraceResult()));
        }

        if (!(event.getRayTraceResult() instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof ServerPlayer sp)) return;
        if (ActiveOriginService.has(sp, ProjectileImmunityPower.class, cfg -> cfg.blocks(proj))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.ATTACK,
            event.getTarget());
    }

    @SubscribeEvent
    public static void onLivingJump(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.JUMP);
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        var killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer sp)) return;
        LivingEntity killed = event.getEntity();
        if (!(killed instanceof Animal)) return;

        float mult = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_HARVEST_DROPS, killed, 1.0f);

        // Duplicate existing drops based on multiplier
        int extraCopies = Math.max(0, Math.round(mult) - 1);
        if (extraCopies <= 0) return;

        var originalDrops = new java.util.ArrayList<>(event.getDrops());
        for (var drop : originalDrops) {
            for (int i = 0; i < extraCopies; i++) {
                var copy = drop.getItem().copy();
                var newDrop = new net.minecraft.world.entity.item.ItemEntity(
                    killed.level(), killed.getX(), killed.getY(), killed.getZ(), copy);
                event.getDrops().add(newDrop);
            }
        }
    }

    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (LENGTHENING_EFFECT.get()) return;
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null) return;

        // longer_potions moved to action_on_event (MOD_POTION_DURATION).
        float dMult = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_POTION_DURATION, inst, 1.0f);
        if (dMult != 1.0f) {
            int newDuration = (int)(inst.getDuration() * dMult);
            LENGTHENING_EFFECT.set(true);
            try {
                sp.addEffect(new MobEffectInstance(inst.getEffect(), newDuration,
                    inst.getAmplifier(), inst.isAmbient(), inst.isVisible()));
            } finally {
                LENGTHENING_EFFECT.set(false);
            }
        }

        // TamedPotionDiffusalPower — share positive effects to nearby tamed animals
        if (inst.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
            ActiveOriginService.forEachOfType(sp, TamedPotionDiffusalPower.class, cfg -> {
                AABB area = sp.getBoundingBox().inflate(cfg.radius());
                var animals = sp.level().getEntitiesOfClass(Animal.class, area);
                for (Animal animal : animals) {
                    if (!(animal instanceof OwnableEntity ownable)) continue;
                    var owner = ownable.getOwner();
                    if (owner == null || !sp.getUUID().equals(owner.getUUID())) continue;
                    animal.addEffect(new MobEffectInstance(inst.getEffect(),
                        inst.getDuration(), inst.getAmplifier(), inst.isAmbient(), inst.isVisible()));
                }
            });
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
        if (ActiveOriginService.has(sp, EffectImmunityPower.class,
                config -> config.effects().contains(effectId))) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (effectId.equals("minecraft:poison") &&
                ActiveOriginService.has(sp, EntityGroupPower.class, EntityGroupPower.Config::isUndead)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
