package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.config.GameplayConfig;
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
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class CombatPowerEvents {

    // (Previously held a ThreadLocal re-entry guard for the longer-potions
    // re-add path. Removed — the handler now mutates the incoming effect
    // instance in place via update() instead of calling addEffect recursively,
    // which sidesteps the race documented in onMobEffectAdded below.)

    // Filter-string caches for the hot matcher helpers below. Filter strings
    // come from pack JSON (ModifyDamagePower damage_type, ActionOnHit target_type,
    // ScareEntities entity_types, etc.) and have low cardinality — a few tens
    // of distinct strings per loaded pack. Pre-cached so damage events don't
    // re-parse identifiers and allocate TagKeys on every hit.
    private static final java.util.concurrent.ConcurrentHashMap<String, TagKey<net.minecraft.world.damagesource.DamageType>> DAMAGE_TAG_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, TagKey<EntityType<?>>> ENTITY_TAG_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Identifier> IDENTIFIER_CACHE =
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
                float scaled = config.apply(event.getAmount());
                event.setAmount(scaled);
            });

            // ActionOnHitPower — fire when the attacker's filters match any target (mob or player).
            final float outHitAmount = event.getAmount();
            ActiveOriginService.forEachOfType(outAttacker, ActionOnHitPower.class, config -> {
                if (outHitAmount < config.minDamage()) return;
                if (config.damageType().isPresent()
                        && !matchesDamageType(event.getSource(), config.damageType().get())) return;
                if (config.targetGroup().isPresent() && !matchesEntityGroup(outTarget, config.targetGroup().get())) return;
                if (config.targetType().isPresent()
                        && !matchesEntityIdOrTag(outTarget, config.targetType().get())) return;
                if (!ActionOnHitPower.rollChance(config)) return;
                ActionOnHitPower.execute(outAttacker, config, outTarget);
            });

            // Route B self_action_on_hit / action_on_hit — fires when the holder
            // deals damage; passes the victim directly so the parsed
            // bientity_action can resolve (actor=holder, target=victim).
            final LivingEntity dealTarget = outTarget;
            ActiveOriginService.forEachOfType(outAttacker,
                com.cyberday1.neoorigins.compat.CompatPower.class, cfg -> {
                    if (cfg.onDealDamage() != null) cfg.onDealDamage().accept(outAttacker, dealTarget);
                });
        }

        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // Record "last damaged" tick for the out_of_combat condition gate
        // (explorer campfire rest, future vampire peaceful-feed, etc.).
        // Runs before any damage-cancel checks so even cancelled damage
        // counts as being attacked — players in a fight shouldn't get
        // out-of-combat buffs just because invulnerability consumed the hit.
        com.cyberday1.neoorigins.service.CombatTracker.markDamaged(sp);

        // First-pick invulnerability — the player is still choosing their
        // origin/class on first login. Cancel damage so they can't die or get
        // shoved around while the picker is open. Orb-of-Origin re-picks
        // (orbUseCount > 0) don't qualify — they chose to re-enter the flow.
        // If the player dismissed the picker without committing any origin,
        // `pickerAbandoned` is set by the client and invulnerability drops so
        // they can't stay immortal forever by escaping.
        //
        // Invuln covers the ENTIRE initial pick — every layer (origin THEN
        // class, etc.), not just up to the first committed layer. Keying off
        // hadAllOrigins alone means a player on a multi-layer server stays
        // protected while they're still choosing their class. The two edge
        // cases are handled elsewhere: the post-pick grace window below keeps
        // them safe for a few seconds after the pick truly completes (so the
        // handleChooseOrigin allFilled signal doesn't have to race the live
        // damage check), and `pickerAbandoned` (set by the client when the
        // picker is escaped with an incomplete pick) drops invuln so a player
        // can't escape the picker and stay immortal forever.
        com.cyberday1.neoorigins.attachment.PlayerOriginData pod =
            sp.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
        boolean stillFirstPicking = !pod.isHadAllOrigins();
        if (stillFirstPicking && pod.getOrbUseCount() == 0 && !pod.isPickerAbandoned()) {
            event.setCanceled(true);
            return;
        }

        // 5-second post-pick grace: keep the player invulnerable briefly after they
        // finish the initial on-join pick so they don't eat a hit the instant they
        // spawn in (e.g. teleported into a hostile spawn_location). Orb re-picks
        // don't qualify (grace is only granted on the initial join flow).
        if (com.cyberday1.neoorigins.service.FirstPickGraceTracker.isActive(sp)) {
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

        // DodgeChancePower — percentage chance to avoid damage entirely
        final boolean[] dodged = {false};
        ActiveOriginService.forEachOfType(sp, DodgeChancePower.class, cfg -> {
            if (!dodged[0] && sp.getRandom().nextFloat() < cfg.chance()) {
                dodged[0] = true;
            }
        });
        if (dodged[0]) {
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
                    sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.SLOWNESS, 20 + 10 * baneLvl, 3));
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
                    float scaled = config.apply(event.getAmount());
                    if (scaled <= 0.0f) {
                        event.setCanceled(true);
                        return;
                    }
                    event.setAmount(scaled);
                }
            }
        });

        if (!event.isCanceled()) {
            float amount = event.getAmount();
            // Publish the HitTakenContext to ActionContextHolder so any
            // bi-entity actions parsed in OriginsCompatPowerLoader.parseSelfActionWhenHit
            // can read source.getEntity() back from inside CompatPower.onHit.
            // Pass the cancellable LivingIncomingDamageEvent through the context
            // so neoorigins:cancel_event can negate the hit (tester report:
            // cancel_event was a no-op on hit_taken).
            var hitCtx = new com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext(
                amount, event.getSource(), event);
            Object prev = com.cyberday1.neoorigins.service.ActionContextHolder.set(hitCtx);
            try {
                ActiveOriginService.forEach(sp, holder -> holder.onHit(sp, amount));
            } finally {
                com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev);
            }
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                sp,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.HIT_TAKEN,
                hitCtx);
            // A HIT_TAKEN handler may have cancelled the event via
            // neoorigins:cancel_event — the hit never landed, so skip the
            // post-hit reactions (thorns) too.
            if (event.isCanceled()) return;
            // thorns_aura moved to action_on_event with a damage_attacker
            // entity_action (reads HitTakenContext.amount × amount_ratio).
            // The HIT_TAKEN dispatch above runs any such powers.

            // ThornsOnHitPower — reflect damage + optional fire to melee attacker
            if (event.getSource().getEntity() instanceof LivingEntity attacker
                    && attacker != sp) {
                ActiveOriginService.forEachOfType(sp, ThornsOnHitPower.class, cfg -> {
                    attacker.hurt(sp.damageSources().thorns(sp), cfg.damage());
                    if (cfg.fireTicks() > 0) attacker.setRemainingFireTicks(cfg.fireTicks());
                });
            }
        }
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
                Registries.DAMAGE_TYPE, Identifier.parse(f.substring(1))));
            return source.is(tag);
        }
        var typeKey = source.typeHolder().unwrapKey();
        // If the filter explicitly specifies a namespace (e.g. "irons_spellbooks:fire_magic"),
        // compare against the full registry-key Identifier so cross-mod damage types
        // match unambiguously. Detect an explicit namespace by the presence of a ':' BEFORE
        // parsing, since Identifier.parse would inject the default "minecraft:" namespace.
        if (filter.indexOf(':') >= 0) {
            var parsed = Identifier.tryParse(filter);
            return parsed != null
                && typeKey.isPresent()
                && typeKey.get().identifier().equals(parsed);
        }
        // Bare path: match against both the msgId (camelCase, e.g. "flyIntoWall")
        // and the registry key path (snake_case, e.g. "fly_into_wall") so pack
        // authors can use either convention.
        if (source.getMsgId().equalsIgnoreCase(filter)) return true;
        return typeKey.isPresent()
            && typeKey.get().identifier().getPath().equalsIgnoreCase(filter);
    }

    /**
     * Matches an entity against an Origins-style "entity group" name (e.g. "undead",
     * "arthropod", "illager", "aquatic") by looking up {@code minecraft:<group>} as
     * a vanilla entity-type tag. Vanilla ships these tags so canonical Origins
     * groups translate directly.
     */
    private static boolean matchesEntityGroup(LivingEntity target, String group) {
        TagKey<EntityType<?>> tag = ENTITY_TAG_CACHE.computeIfAbsent("minecraft:" + group, k -> TagKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("minecraft", group)));
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
            TagKey<EntityType<?>> tag = ENTITY_TAG_CACHE.computeIfAbsent(idOrTag, f -> TagKey.create(
                Registries.ENTITY_TYPE, Identifier.parse(f.substring(1))));
            return target.getType().getTags().anyMatch(t -> t.equals(tag));
        }
        Identifier id = IDENTIFIER_CACHE.computeIfAbsent(idOrTag, Identifier::parse);
        return id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
    }

    /**
     * Attacker-side HIT_DEALT dispatch. The HIGH-priority {@link #onLivingDamage}
     * runs first and applies any modify_damage_dealt scaling, so reading the
     * amount here (LOW priority) gives the final damage the player actually dealt
     * to the victim. Fires for ANY living victim (player-vs-mob included), keyed
     * on the attacker being a ServerPlayer — the mirror of HIT_TAKEN, which is
     * keyed on the victim. Feeds {@code neoorigins:hit_dealt_amount}.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerDealDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        var hitDealt = new com.cyberday1.neoorigins.service.EventPowerIndex.HitDealtContext(
            event.getAmount(), event.getEntity(), event.getSource());
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            attacker,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.HIT_DEALT,
            hitDealt);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Check if the dying entity is a tracked minion (notify summoner)
        com.cyberday1.neoorigins.service.MinionTracker.onEntityDeath(event.getEntity());

        // Slime death save — if the player has the power and enough moisture,
        // cancel the death and trigger the split mechanic instead.
        if (event.getEntity() instanceof ServerPlayer dyingSp) {
            final boolean[] saved = {false};
            ActiveOriginService.forEachOfType(dyingSp,
                com.cyberday1.neoorigins.power.builtin.SlimeDeathSavePower.class, cfg -> {
                    if (!saved[0] && com.cyberday1.neoorigins.power.builtin.SlimeDeathSavePower
                            .shouldPreventDeath(dyingSp, cfg)) {
                        saved[0] = true;
                    }
                });
            if (saved[0]) {
                event.setCanceled(true);
                return;
            }
        }

        // Native prevent_death — cancel the lethal blow if the player has the
        // power and its condition / damage-type filter / cooldown all allow it.
        if (event.getEntity() instanceof ServerPlayer dyingSp) {
            final boolean[] saved = {false};
            ActiveOriginService.forEachOfType(dyingSp,
                com.cyberday1.neoorigins.power.builtin.PreventDeathPower.class, cfg -> {
                    if (!saved[0] && com.cyberday1.neoorigins.power.builtin.PreventDeathPower
                            .shouldPreventDeath(dyingSp, cfg, event.getSource())) {
                        saved[0] = true;
                    }
                });
            if (saved[0]) {
                event.setCanceled(true);
                return;
            }
        }

        // Dispatch DEATH event for the dying player (if applicable). The
        // cancellable LivingDeathEvent itself is the context, so
        // neoorigins:cancel_event can veto the death (tester report: the
        // dispatch used to carry no context, making cancel_event a silent
        // no-op on event=death).
        if (event.getEntity() instanceof ServerPlayer dyingSp) {
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                dyingSp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.DEATH, event);
            if (event.isCanceled()) {
                // Death was vetoed but the player is still at <= 0 HP — patch
                // health to a small positive value (totem/prevent_death
                // pattern, see PreventDeathPower.shouldPreventDeath) so they
                // don't sit dead-at-0 and re-die next tick.
                if (dyingSp.getHealth() <= 0.0f) dyingSp.setHealth(1.0f);
                return;
            }
            // ExtraInventoryPower drop-on-death: drop or clear extra inventory contents.
            handleExtraInventoryDeath(dyingSp);
        }

        var killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer sp)) return;
        LivingEntity killed = event.getEntity();
        ActiveOriginService.forEach(sp, holder -> holder.onKill(sp, killed));
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.KILL,
            new com.cyberday1.neoorigins.service.EventPowerIndex.KillContext(killed, event));
        // A KILL handler may have spared the victim via cancel_event — patch
        // its health like the DEATH path above and skip the kill bookkeeping.
        if (event.isCanceled()) {
            if (killed.getHealth() <= 0.0f) killed.setHealth(1.0f);
            return;
        }

        // ── Essence Evolution: track mob kills ─────────────────────────
        if (!(killed instanceof net.minecraft.world.entity.player.Player)
                && GameplayConfig.isEvolutionEnabled()) {
            com.cyberday1.neoorigins.evolution.EssenceEvolutionManager.onMobKill(sp);
        }
    }

    private static void handleExtraInventoryDeath(ServerPlayer dyingPlayer) {
        ActiveOriginService.forEachOfType(dyingPlayer,
            com.cyberday1.neoorigins.power.builtin.ExtraInventoryPower.class, cfg -> {
                com.cyberday1.neoorigins.power.builtin.ExtraInventoryPower
                    .onPlayerDeath(dyingPlayer, cfg.dropOnDeath());
            });
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
                    proj, event.getRayTraceResult(), event);

            com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
                ownerSp,
                com.cyberday1.neoorigins.service.EventPowerIndex.Event.PROJECTILE_HIT,
                ctx);

            // Drain any on_hit_action registered by spawn_projectile when this
            // projectile was fired. Invoked with the ProjectileHitContext installed
            // on ActionContextHolder so area_of_effect can center on the impact
            // point rather than the (now-stale) player position.
            //
            // The on_hit path fires for BOTH entity and block impacts. On a block
            // impact the ray-trace result is a BlockHitResult, which doubles as the
            // block-target seam (item 5): ActionParser.extractBlockTarget reads the
            // impacted BlockPos straight off this ProjectileHitContext, so the
            // block-target verbs (strip/till/path/grow/transform_block) and the
            // block_target_action wrapper resolve the hit block when used directly
            // as an on_hit_action — no separate on_block_hit_action plumbing needed.
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
        // AttackEntityEvent is cancellable, so wrap target + event in an
        // EntityInteractContext (the established cancellable-context shape) —
        // this both lets neoorigins:cancel_event veto the swing and lets
        // bientity target_action resolve the attacked entity. Non-living
        // targets (boats, item frames) fall back to the raw event so
        // cancel_event still works via the generic ICancellableEvent branch.
        Object ctx = event.getTarget() instanceof LivingEntity livingTarget
            ? new com.cyberday1.neoorigins.service.EventPowerIndex.EntityInteractContext(livingTarget, event)
            : event;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.ATTACK,
            ctx);
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
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null) return;

        // longer_potions moved to action_on_event (MOD_POTION_DURATION).
        float dMult = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_POTION_DURATION, inst, 1.0f);

        // modify_status_effect_amplifier (Route B) — boost amplifier for matching effects.
        var effectKey = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value());
        int ampBoost = effectKey != null
            ? com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader.getAmplifierBoost(sp.getUUID(), effectKey) : 0;

        if (dMult != 1.0f || ampBoost != 0) {
            int newDuration = (int)(inst.getDuration() * dMult);
            int newAmplifier = inst.getAmplifier() + ampBoost;
            // MobEffectEvent.Added fires *before* vanilla LivingEntity#addEffect
            // puts the instance into activeEffects (LivingEntity.java:956-958).
            // Re-calling sp.addEffect(new MobEffectInstance(...)) from here would
            // run the nested put first, then be overwritten by the outer call's
            // put with the original (un-extended) instance — which is exactly
            // what made the first-dose extension silently fail while subsequent
            // doses worked through the merge/update path.
            //
            // Mutating `inst` in place via update() avoids the race entirely:
            // vanilla's subsequent put() / update() sees the already-extended
            // duration and stores it. MobEffectInstance.update only strengthens
            // (longer duration, higher amplifier), so duration modifiers < 1.0
            // or negative amp boosts are silently ignored — matches the
            // longer_potions / amplifier_boost intent.
            inst.update(new MobEffectInstance(inst.getEffect(), newDuration,
                newAmplifier, inst.isAmbient(), inst.isVisible()));
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
        // Undead entity group — poison/regen immunity (vanilla undead behaviour).
        // Instant Health/Damage inversion is handled by the isInvertedHealAndHarm
        // mixin (LivingEntityUndeadPotionMixin) since instant effects bypass addEffect().
        if (ActiveOriginService.has(sp, EntityGroupPower.class, EntityGroupPower.Config::isUndead)) {
            if (effectId.equals("minecraft:poison") || effectId.equals("minecraft:regeneration")) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                return;
            }
        }
        // Post-cleanse grace window: if a prior action_on_event cleanse opened
        // a per-effect immunity window via `immunity_ticks`, short-circuit
        // here so the user's condition (e.g. random_chance) doesn't roll again
        // until the window closes. Keeps probabilistic resistance from feeling
        // like a per-tick coin-flip.
        long now = sp.level().getGameTime();
        if (com.cyberday1.neoorigins.service.EventPowerIndex.hasEffectGrace(
                sp.getUUID(), effectKey, now)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        // Generic action_on_event hook — fires AFTER hard-coded immunity rules
        // above so pack authors only see effects those rules let through, and
        // can apply probabilistic or conditional cancellation via cancel_event.
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(sp,
            com.cyberday1.neoorigins.service.EventPowerIndex.Event.EFFECT_APPLIED,
            new com.cyberday1.neoorigins.service.EventPowerIndex.EffectAppliedContext(
                effectInstance, effectKey, event));
    }
}
