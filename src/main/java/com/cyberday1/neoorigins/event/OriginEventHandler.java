package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.event.PowerGrantedEvent;
import com.cyberday1.neoorigins.api.event.PowerRevokedEvent;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.EffectImmunityPower;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.power.builtin.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
        // Re-apply active powers after respawn
        forEachActivePower(sp, holder -> holder.onGranted(sp));
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
        }
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
