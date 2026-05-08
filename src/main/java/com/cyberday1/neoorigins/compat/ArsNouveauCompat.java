package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.EntityGroupPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ars Nouveau compat: inverts {@code EffectHarm} damage into healing for
 * undead-origin players, matching vanilla undead potion behaviour.
 *
 * <p>Uses reflection — Ars Nouveau is <b>not</b> required at compile time.
 * Only activates when {@code ars_nouveau} is on the mod list.
 *
 * <h3>Two-phase intercept</h3>
 * <ol>
 *   <li><b>Phase 1</b> — reflected listener on {@code SpellResolveEvent.Pre}.
 *       If the spell contains {@code EffectHarm} and the target is an
 *       undead-origin player, the player's UUID is flagged.</li>
 *   <li><b>Phase 2</b> — standard {@code LivingIncomingDamageEvent} listener.
 *       If the player is flagged, cancel the damage and apply it as healing
 *       via {@code setHealth} (bypasses the heal/damage pipeline so our
 *       {@code isInvertedHealAndHarm} mixin doesn't re-invert it).</li>
 * </ol>
 */
public final class ArsNouveauCompat {

    private ArsNouveauCompat() {}

    /** Players about to receive EffectHarm damage that should be healed. */
    private static final Set<UUID> PENDING_HARM_HEAL = ConcurrentHashMap.newKeySet();

    private static Class<?> effectHarmClass;

    @SuppressWarnings("unchecked")
    public static void register() {
        try {
            effectHarmClass = Class.forName(
                "com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm");

            Class<? extends Event> preEvent = (Class<? extends Event>) Class.forName(
                "com.hollingsworth.arsnouveau.api.event.SpellResolveEvent$Pre");

            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, preEvent,
                ArsNouveauCompat::onSpellResolvePre);

            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                LivingIncomingDamageEvent.class, ArsNouveauCompat::onIncomingDamage);

            NeoOrigins.LOGGER.info("[Compat] Ars Nouveau undead harm-inversion active");
        } catch (ClassNotFoundException e) {
            NeoOrigins.LOGGER.debug("[Compat] Ars Nouveau classes not found — skipping");
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[Compat] Ars Nouveau compat failed to register", e);
        }
    }

    // -- Phase 1: flag undead-origin targets of EffectHarm --

    private static void onSpellResolvePre(Event event) {
        try {
            // Resolve the HitResult -> target entity
            Object hitResult = reflectGet(event, "rayTraceResult", "result", "hitResult");
            if (!(hitResult instanceof EntityHitResult ehr)) return;
            Entity target = ehr.getEntity();
            if (!(target instanceof ServerPlayer sp)) return;
            if (!ActiveOriginService.has(sp, EntityGroupPower.class,
                    EntityGroupPower.Config::isUndead)) return;

            // Check if the spell contains EffectHarm
            Object spell = reflectGet(event, "spell");
            if (spell == null) return;
            Object recipe = reflectGet(spell, "recipe", "spellParts");
            if (recipe instanceof java.util.List<?> parts) {
                for (Object part : parts) {
                    if (effectHarmClass.isInstance(part)) {
                        PENDING_HARM_HEAL.add(sp.getUUID());
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
            // Best-effort — never crash for compat
        }
    }

    // -- Phase 2: convert flagged damage into healing --

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!PENDING_HARM_HEAL.remove(sp.getUUID())) return;

        float amount = event.getAmount();
        event.setCanceled(true);
        sp.setHealth(Math.min(sp.getHealth() + amount, sp.getMaxHealth()));
    }

    // -- Reflection helpers --

    /** Try multiple field/method names to extract a value reflectively. */
    private static Object reflectGet(Object obj, String... names) {
        Class<?> clz = obj.getClass();
        for (String name : names) {
            try {
                Method m = findMethod(clz, "get" + capitalize(name));
                if (m != null) { m.setAccessible(true); return m.invoke(obj); }
            } catch (Exception ignored) {}
            try {
                Method m = findMethod(clz, name);
                if (m != null) { m.setAccessible(true); return m.invoke(obj); }
            } catch (Exception ignored) {}
            try {
                Field f = findField(clz, name);
                if (f != null) { f.setAccessible(true); return f.get(obj); }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Method findMethod(Class<?> clz, String name) {
        for (Class<?> c = clz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
            }
        }
        return null;
    }

    private static Field findField(Class<?> clz, String name) {
        for (Class<?> c = clz; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
