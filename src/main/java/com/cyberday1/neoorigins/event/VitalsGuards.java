package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Last-line-of-defence sanity guards for numeric fields in the
 * vanilla damage / heal / knockback / mining-speed events. Each
 * guard runs at {@link EventPriority#LOWEST} so it fires AFTER every
 * other handler — ours, other mods', datapacks — has had its turn.
 * If anything still left a non-finite value in the event, we clamp
 * it back into the finite float range before vanilla resumes
 * processing and (e.g.) uses it for health math.
 *
 * <p>The save-bricking {@code /kill} bug on v1.3.0 was rooted in a
 * single unclamped multiply in one of our own handlers; the fix in
 * {@code CombatPowerEvents} prevents the specific case. These
 * guards exist to make sure <em>any</em> future edge case — a
 * misconfigured datapack, a third-party mod's damage multiplier,
 * a vanilla quirk we haven't seen yet — can't propagate a {@code
 * NaN} or {@code Infinity} into vanilla's absorption / health
 * pipeline, because that's the exact shape that corrupts the
 * player .dat and prevents the world from loading back in.
 *
 * <p>Each clamp replaces non-finite values with {@link
 * Float#MAX_VALUE} (or clamps to non-negative) and logs a
 * {@code WARN} so we can see when a guard actually tripped in
 * production.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class VitalsGuards {

    private VitalsGuards() {}

    /**
     * Clamp damage amount on {@link LivingIncomingDamageEvent} at
     * LOWEST priority. Runs after every other mod handler and before
     * vanilla actuallyHurt processes the amount.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        float amount = event.getAmount();
        float clamped = sanitize(amount);
        if (clamped != amount) {
            logClamp("LivingIncomingDamageEvent#amount", event.getEntity(), amount, clamped);
            event.setAmount(clamped);
        }
    }

    /**
     * Clamp knockback strength on {@link LivingKnockBackEvent} at
     * LOWEST priority.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        float strength = event.getStrength();
        float clamped = sanitize(strength);
        if (clamped != strength) {
            logClamp("LivingKnockBackEvent#strength", event.getEntity(), strength, clamped);
            event.setStrength(clamped);
        }
    }

    /**
     * Clamp heal amount on {@link LivingHealEvent} at LOWEST priority.
     * A non-finite heal would land in {@code setHealth(current + amount)},
     * which vanilla's clamp preserves as {@code NaN} — same save-bricking
     * shape as the damage path.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        float amount = event.getAmount();
        float clamped = sanitize(amount);
        if (clamped != amount) {
            logClamp("LivingHealEvent#amount", event.getEntity(), amount, clamped);
            event.setAmount(clamped);
        }
    }

    /**
     * Clamp mining speed on {@link PlayerEvent.BreakSpeed} at LOWEST
     * priority. Non-finite dig speed doesn't brick saves directly but
     * can cause client/server desync and is never intentional.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        float speed = event.getNewSpeed();
        float clamped = sanitize(speed);
        if (clamped != speed) {
            logClamp("PlayerEvent.BreakSpeed#newSpeed", event.getEntity(), speed, clamped);
            event.setNewSpeed(clamped);
        }
    }

    /**
     * Clamp a single float into a safe finite range.
     *
     * <ul>
     *   <li>{@code NaN} → {@code 0.0f} (NaN is almost never what anyone
     *       meant; zeroing out is the least-damaging default)</li>
     *   <li>{@code +Infinity} → {@link Float#MAX_VALUE}</li>
     *   <li>{@code -Infinity} → {@code -Float.MAX_VALUE}</li>
     *   <li>Anything finite → unchanged</li>
     * </ul>
     */
    private static float sanitize(float value) {
        if (Float.isNaN(value)) return 0.0f;
        if (value == Float.POSITIVE_INFINITY) return Float.MAX_VALUE;
        if (value == Float.NEGATIVE_INFINITY) return -Float.MAX_VALUE;
        return value;
    }

    private static void logClamp(String field, LivingEntity entity, float before, float after) {
        NeoOrigins.LOGGER.warn(
            "VitalsGuards: clamped {} on {} ({}): {} → {}",
            field, entity.getName().getString(), entity.getUUID(), before, after);
    }
}
