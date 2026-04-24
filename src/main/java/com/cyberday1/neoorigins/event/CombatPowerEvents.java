package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.EffectImmunityPower;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
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
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class CombatPowerEvents {

    /** Re-entry guard: prevents LongerPotionsPower from recursing into MobEffectEvent.Added. */
    private static final ThreadLocal<Boolean> LENGTHENING_EFFECT = ThreadLocal.withInitial(() -> false);

    // Filter-string caches for the hot matcher helpers below. Filter strings
    // come from pack JSON (ModifyDamagePower damage_type, ActionOnHit target_type,
    // ScareEntities entity_types, etc.) and have low cardinality — a few tens
    // of distinct strings per loaded pack. Pre-cached so damage events don't
    // re-parse identifiers and allocate TagKeys on every hit.
    private static final java.util.concurrent.ConcurrentHashMap<String, TagKey<net.minecraft.world.damagesource.DamageType>> DAMAGE_TAG_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, TagKey<EntityType<?>>> ENTITY_TAG_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, ResourceLocation> IDENTIFIER_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    // Run at HIGH so our ModifyDamagePower multipliers apply before other mods' handlers,
    // and so VitalsGuards (at LOWEST) stays the final sanitizer regardless of mod order.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        // Player-as-attacker OUT-direction modifiers must fire regardless of
        // whether the victim is a player — ModifyDamagePower{direction:out}
        // powers like Gravity Mage Frail, Water Mage Fragile, and Monster
        // Tamer Lone Weakness exist to reduce damage the player deals, which
        // is almost always dealt to mobs, not other players. Running this
        // before the ServerPlayer-victim early-return catches the mob-victim
        // case that the PvP-only block below would miss.
        if (event.getSource().getEntity() instanceof ServerPlayer outAttacker
            && outAttacker != event.getEntity()) {
            LivingEntity outTarget = event.getEntity();
            ActiveOriginService.forEachOfType(outAttacker, ModifyDamagePower.class, config -> {
                if (config.direction() != ModifyDamagePower.Direction.OUT) return;
                if (config.condition().isPresent() && !config.condition().get().test(outAttacker)) return;
                if (config.damageType().isPresent()
                        && !matchesDamageType(event.getSource(), config.damageType().get())) return;
                if (config.targetGroup().isPresent() && !matchesEntityGroup(outTarget, config.targetGroup().get())) return;
                float scaled = event.getAmount() * config.multiplier();
                if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
                event.setAmount(scaled);
            });
        }

        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // First-pick invulnerability — the player is still choosing their
        // origin/class on first login. Cancel damage so they can't die or get
        // shoved around while the picker is open. Orb-of-Origin re-picks
        // (orbUseCount > 0) don't qualify — they chose to re-enter the flow.
        // If the player dismissed the picker without committing any origin,
        // `pickerAbandoned` is set by the client and invulnerability drops so
        // they can't stay immortal forever by escaping.
        com.cyberday1.neoorigins.attachment.PlayerOriginData pod =
            sp.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
        if (!pod.isHadAllOrigins() && pod.getOrbUseCount() == 0 && !pod.isPickerAbandoned()) {
            event.setCanceled(true);
            return;
        }

        // Immunity to the player's own minions: guardians / skeletons / wolves
        // that the player summoned shouldn't be able to damage them, including
        // via THORNS (Guardian's "aura of thorns" reflect). Covers direct
        // attacks and any indirect owner damage like thorn/AoE reflects.
        var directEntity = event.getSource().getDirectEntity();
        if (directEntity != null
            && com.cyberday1.neoorigins.service.MinionTracker.isTrackedMinionOf(directEntity, sp.getUUID())) {
            event.setCanceled(true);
            return;
        }

        // Native InvulnerabilityPower — cancels damage whose source matches configured filters.
        // Runs before any other damage logic so the event is short-circuited cleanly.
        if (ActiveOriginService.has(sp, InvulnerabilityPower.class,
                cfg -> cfg.matches(event.getSource()))) {
            event.setCanceled(true);
            return;
        }

        if (event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.ON_FIRE)
                || event.getSource().is(DamageTypes.LAVA) || event.getSource().is(DamageTypes.HOT_FLOOR)) {
            if (ActiveOriginService.has(sp, PreventActionPower.class,
                    config -> config.action() == PreventActionPower.Action.FIRE
                           && PreventActionPower.isGateOpen(sp, config))) {
                event.setCanceled(true);
                return;
            }
        }
        if (event.getSource().is(DamageTypes.DROWN)) {
            if (ActiveOriginService.has(sp, PreventActionPower.class,
                    config -> config.action() == PreventActionPower.Action.DROWN
                           && PreventActionPower.isGateOpen(sp, config))) {
                event.setCanceled(true);
                return;
            }
        }
        if (event.getSource().is(DamageTypes.FREEZE)) {
            if (ActiveOriginService.has(sp, PreventActionPower.class,
                    config -> config.action() == PreventActionPower.Action.FREEZE
                           && PreventActionPower.isGateOpen(sp, config))) {
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

        // EntityGroupPower damage integration: a player tagged as an entity
        // group should take the corresponding enchantment bonus from the
        // attacker's weapon (Bane of Arthropods vs. arthropod, Impaling vs.
        // water, Smite vs. undead). Vanilla only consults EntityType tags so
        // the player never qualifies without this hook.
        if (event.getSource().getEntity() instanceof LivingEntity groupAttacker) {
            var weapon = groupAttacker.getMainHandItem();
            if (!weapon.isEmpty()) {
                var enchLookup = sp.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                int baneLvl = ActiveOriginService.has(sp, EntityGroupPower.class, EntityGroupPower.Config::isArthropod)
                    ? enchLookup.get(net.minecraft.world.item.enchantment.Enchantments.BANE_OF_ARTHROPODS)
                        .map(h -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(h, weapon))
                        .orElse(0) : 0;
                int smiteLvl = ActiveOriginService.has(sp, EntityGroupPower.class, EntityGroupPower.Config::isUndead)
                    ? enchLookup.get(net.minecraft.world.item.enchantment.Enchantments.SMITE)
                        .map(h -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(h, weapon))
                        .orElse(0) : 0;
                int impaleLvl = ActiveOriginService.has(sp, EntityGroupPower.class, EntityGroupPower.Config::isWater)
                    ? enchLookup.get(net.minecraft.world.item.enchantment.Enchantments.IMPALING)
                        .map(h -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(h, weapon))
                        .orElse(0) : 0;
                int totalLvl = baneLvl + smiteLvl + impaleLvl;
                if (totalLvl > 0) {
                    event.setAmount(event.getAmount() + totalLvl * 2.5f);
                }
                if (baneLvl > 0) {
                    // Vanilla Bane applies slowness IV for 1–1.5s at level 1+.
                    sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20 + 10 * baneLvl, 3));
                }
            }
        }

        ActiveOriginService.forEachOfType(sp, ModifyDamagePower.class, config -> {
            if (config.direction() == ModifyDamagePower.Direction.IN) {
                if (config.condition().isPresent() && !config.condition().get().test(sp)) return;
                if (config.damageType().isEmpty() ||
                        matchesDamageType(event.getSource(), config.damageType().get())) {
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

        // ActionOnHitPower still runs here — it fires only in the player-victim
        // PvP path (the attacker's hit-reaction powers should trigger on any
        // target, mob or player, but for now the pattern is unchanged to avoid
        // scope creep). The OUT ModifyDamagePower block has moved to the top of
        // this method so mob victims get the attacker's OUT multipliers too.
        var attackerEntity = event.getSource().getEntity();
        if (attackerEntity instanceof ServerPlayer attackerSp && attackerSp != sp) {
            LivingEntity target = event.getEntity();
            // ActionOnHitPower — fire self/target actions when the attacker's filters match.
            final float hitAmount = event.getAmount();
            ActiveOriginService.forEachOfType(attackerSp, ActionOnHitPower.class, config -> {
                if (hitAmount < config.minDamage()) return;
                if (config.damageType().isPresent()
                        && !matchesDamageType(event.getSource(), config.damageType().get())) return;
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
     * a vanilla entity-type tag. Vanilla 1.21 ships these tags so the canonical
     * Origins groups translate directly.
     */
    private static boolean matchesEntityGroup(LivingEntity target, String group) {
        TagKey<EntityType<?>> tag = ENTITY_TAG_CACHE.computeIfAbsent("minecraft:" + group, k -> TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", group)));
        return target.getType().is(tag);
    }

    /**
     * Matches a DamageSource against a damage-type filter string. Supports both
     * the vanilla msgId string ({@code "onFire"}, {@code "lava"}) for backwards
     * compatibility and damage-type-tag references ({@code "#minecraft:is_fire"},
     * {@code "#minecraft:is_projectile"}) — tags are the right tool for
     * "immune/vulnerable to fire" powers because they aggregate IN_FIRE +
     * ON_FIRE + LAVA + HOT_FLOOR + FIREBALL + CAMPFIRE in one filter.
     */
    public static boolean matchesDamageType(net.minecraft.world.damagesource.DamageSource source, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        if (filter.startsWith("#")) {
            var tag = DAMAGE_TAG_CACHE.computeIfAbsent(filter, f -> TagKey.create(
                Registries.DAMAGE_TYPE, ResourceLocation.parse(f.substring(1))));
            return source.is(tag);
        }
        return source.getMsgId().equalsIgnoreCase(filter);
    }

    /**
     * Matches an entity against a target identifier string. Supports both raw IDs
     * ({@code "minecraft:zombie"}) and tag references ({@code "#mymod:my_tag"}).
     */
    public static boolean matchesEntityIdOrTag(LivingEntity target, String idOrTag) {
        if (idOrTag.startsWith("#")) {
            TagKey<EntityType<?>> tag = ENTITY_TAG_CACHE.computeIfAbsent(idOrTag, f -> TagKey.create(
                Registries.ENTITY_TYPE, ResourceLocation.parse(f.substring(1))));
            return target.getType().is(tag);
        }
        ResourceLocation id = IDENTIFIER_CACHE.computeIfAbsent(idOrTag, ResourceLocation::parse);
        return id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
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
            com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext ctx =
                new com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext(
                    proj, event.getRayTraceResult());

            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                ownerSp,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.PROJECTILE_HIT,
                ctx);

            // Drain any on_hit_action registered by spawn_projectile when this
            // projectile was fired. Invoked with the ProjectileHitContext installed
            // on ActionContextHolder so area_of_effect can center on the impact
            // point rather than the (now-stale) player position.
            var onHit = com.cyberday1.neoorigins.service.ProjectileActionRegistry.drain(proj.getUUID());
            if (onHit != null) {
                Object prev = com.cyberday1.neoorigins.service.ActionContextHolder.set(ctx);
                try {
                    onHit.execute(ownerSp);
                } finally {
                    com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev);
                }
            }
        }

        if (!(event.getRayTraceResult() instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof ServerPlayer sp)) return;
        final boolean[] handled = {false};
        ActiveOriginService.forEachOfType(sp, ProjectileImmunityPower.class, cfg -> {
            if (handled[0]) return;
            if (!cfg.blocks(proj)) return;
            if (cfg.chance() < 1.0f && sp.getRandom().nextFloat() >= cfg.chance()) return;
            event.setCanceled(true);
            handled[0] = true;
            if (cfg.teleport()) {
                // Enderman-style random teleport on successful dodge. Try up to
                // 16 candidate positions within the configured range; first safe
                // one wins. Silently no-op if nothing works — the dodge already
                // cancelled the damage, so the teleport is pure flavour.
                var random = sp.getRandom();
                double baseX = sp.getX();
                double baseY = sp.getY();
                double baseZ = sp.getZ();
                int range = cfg.teleportRange();
                for (int attempt = 0; attempt < 16; attempt++) {
                    double nx = baseX + (random.nextDouble() - 0.5) * 2 * range;
                    double ny = baseY + (random.nextInt(range) - range / 2);
                    double nz = baseZ + (random.nextDouble() - 0.5) * 2 * range;
                    if (sp.randomTeleport(nx, ny, nz, true)) {
                        sp.level().playSound(null, baseX, baseY, baseZ,
                            net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                        break;
                    }
                }
            }
        });
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
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        // Summoned minions must not be an XP farm.
        if (com.cyberday1.neoorigins.service.MinionTracker.isAnyTrackedMinion(event.getEntity())) {
            event.setDroppedExperience(0);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        // Summoned/tamed minions never leave loot — their gear and drops were
        // conjured for free, so letting them drop would let the summoner farm
        // iron helmets (Necromancer skeletons) or infinite meat (Abyssal guardians).
        if (com.cyberday1.neoorigins.service.MinionTracker.isAnyTrackedMinion(event.getEntity())) {
            event.setCanceled(true);
            return;
        }

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
                // Push the owner-match filter into the query predicate so the
                // spatial index skips non-matching entities before they reach us.
                var animals = sp.level().getEntitiesOfClass(Animal.class, area, a -> {
                    if (!(a instanceof OwnableEntity ownable)) return false;
                    var owner = ownable.getOwner();
                    return owner != null && sp.getUUID().equals(owner.getUUID());
                });
                for (Animal animal : animals) {
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
        // Undead entity group — classic Origins undead: immune to Poison + not
        // helped by Regeneration or Instant Health. (Instant Damage → damage
        // is the vanilla default, so we don't need to add it.) Beacon / splash
        // potion / command-driven LivingHealEvent healing is also blocked in
        // onLivingHealUndead below, which covers heal sources that bypass the
        // MobEffect.Applicable gate.
        if (ActiveOriginService.has(sp, EntityGroupPower.class, EntityGroupPower.Config::isUndead)) {
            if (effectId.equals("minecraft:poison")
                || effectId.equals("minecraft:regeneration")
                || effectId.equals("minecraft:instant_health")) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHealUndead(net.neoforged.neoforge.event.entity.living.LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // Beacon / splash potion / command / mod heal on an undead player:
        // cancel — they shouldn't benefit from normal heal sources. Natural
        // regen exhaustion is handled separately by FoodDataTickMixin.
        if (ActiveOriginService.has(sp, EntityGroupPower.class, EntityGroupPower.Config::isUndead)) {
            event.setCanceled(true);
        }
    }
}
