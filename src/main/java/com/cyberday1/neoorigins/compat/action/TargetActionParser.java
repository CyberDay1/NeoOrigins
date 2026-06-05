package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Parses an entity-action JSON into a {@link TargetAction} for the subset of
 * verbs whose effect is <em>entity-general</em> — they call only
 * {@link net.minecraft.world.entity.LivingEntity}/{@code Entity} methods and
 * never a player-only subsystem (powers, xp, resources, food, inventory,
 * commands). These can therefore run against a non-player mob target.
 *
 * <p>Returns {@code null} for any verb that is NOT generalizable. The caller
 * ({@link BiEntityActionParser}) then keeps the existing player-only behaviour
 * for that verb: it runs when the bientity target is a player and is skipped on
 * a non-player target, exactly as before.
 *
 * <p><b>Additive by design.</b> This does not touch the player-targeted
 * {@link EntityAction} dispatch in {@link BuiltinActions}/{@link ActionParser},
 * so existing player-target behaviour is unchanged and the golden-master /
 * SchemaFormCheck audits over that path are unaffected. The per-verb JSON
 * contracts below mirror their {@code BuiltinActions} counterparts so an author
 * sees identical fields whether the target is a player or a mob.
 */
public final class TargetActionParser {

    private TargetActionParser() {}

    /**
     * @return a {@link TargetAction} for a generalizable verb, or {@code null}
     *         if the verb is not generalizable to a non-player target.
     */
    public static TargetAction parse(JsonObject json, String contextId) {
        if (json == null) return null;
        String type = json.has("type") ? json.get("type").getAsString() : "";
        // Same canonicalization as ActionParser (bare -> neoorigins:, and the
        // origins:/apace:/apoli: ecosystem aliases -> neoorigins:). No legacy
        // warning here: for a mob target only this path runs, and ActionParser
        // already warns on the player path, so we avoid double-warning.
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "neoorigins:" + type;
        } else if (type.startsWith("origins:") || type.startsWith("apace:") || type.startsWith("apoli:")) {
            type = "neoorigins:" + type.substring(type.indexOf(':') + 1);
        }

        return switch (type) {
            case "neoorigins:nothing"     -> (t, a) -> {};
            case "neoorigins:extinguish"  -> (t, a) -> t.clearFire();
            case "neoorigins:dismount"    -> (t, a) -> t.stopRiding();
            case "neoorigins:swing_hand"  -> (t, a) -> t.swing(InteractionHand.MAIN_HAND);

            case "neoorigins:heal" -> {
                float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
                yield (t, a) -> t.heal(amount);
            }
            case "neoorigins:set_fall_distance" -> {
                float distance = json.has("fall_distance") ? json.get("fall_distance").getAsFloat() : 0.0f;
                yield (t, a) -> t.fallDistance = distance;
            }
            case "neoorigins:set_on_fire" -> {
                int ticks = json.has("ticks") ? json.get("ticks").getAsInt()
                          : json.has("duration") ? json.get("duration").getAsInt() : 20;
                yield (t, a) -> t.setRemainingFireTicks(ticks);
            }
            case "neoorigins:add_velocity" -> {
                double x = json.has("x") ? json.get("x").getAsDouble() : 0;
                double y = json.has("y") ? json.get("y").getAsDouble() : 0;
                double z = json.has("z") ? json.get("z").getAsDouble() : 0;
                boolean set = json.has("set") && json.get("set").getAsBoolean();
                yield (t, a) -> {
                    if (set) t.setDeltaMovement(x, y, z);
                    else t.push(x, y, z);
                    t.hurtMarked = true;
                };
            }

            case "neoorigins:apply_effect" -> parseApplyEffect(json);
            case "neoorigins:clear_effect" -> parseClearEffect(json);
            case "neoorigins:play_sound"   -> parsePlaySound(json);
            case "neoorigins:damage"       -> parseDamage(json);

            // Item 4 entity-target spells. These are entity-general (they touch only
            // the LivingEntity target + the actor that TargetAction already supplies),
            // so target_action can run them on an arbitrary mob target. The actual
            // behaviour lives in BuiltinActions so the EntityAction-with-context
            // registration and this TargetAction registration share one implementation.
            case "neoorigins:shear" -> (t, a) -> BuiltinActions.shearTarget(t, a);
            case "neoorigins:dye" -> {
                String colorName = json.has("color") ? json.get("color").getAsString() : null;
                net.minecraft.world.item.DyeColor color =
                    colorName == null ? null : net.minecraft.world.item.DyeColor.byName(colorName, null);
                if (colorName != null && color == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] dye (target): unknown dye colour '{}' — action will no-op", colorName);
                }
                final net.minecraft.world.item.DyeColor fColor = color;
                yield (t, a) -> BuiltinActions.dyeTarget(t, fColor);
            }
            case "neoorigins:force_drop" -> {
                final net.minecraft.world.entity.EquipmentSlot slot = BuiltinActions.parseEquipmentSlotOrMain(
                    json.has("slot") ? json.get("slot").getAsString() : "mainhand", "force_drop");
                yield (t, a) -> {
                    // 26.1 delta: Entity.spawnAtLocation gained a leading ServerLevel arg.
                    if (!(t.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
                    net.minecraft.world.item.ItemStack stack = t.getItemBySlot(slot);
                    if (stack.isEmpty()) return;
                    t.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    t.spawnAtLocation(sl, stack);
                };
            }
            case "neoorigins:steal_item" -> {
                final net.minecraft.world.entity.EquipmentSlot slot = BuiltinActions.parseEquipmentSlotOrMain(
                    json.has("slot") ? json.get("slot").getAsString() : "mainhand", "steal_item");
                yield (t, a) -> {
                    net.minecraft.world.item.ItemStack stack = t.getItemBySlot(slot);
                    if (stack.isEmpty()) return;
                    t.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    if (!a.getInventory().add(stack)) {
                        a.drop(stack, false);
                    }
                };
            }

            // Not generalizable (player-only systems, or needs richer context) —
            // caller keeps the existing player-target / skip behaviour.
            default -> null;
        };
    }

    /** Mirrors {@code BuiltinActions.apply_effect}: flat shape or {@code effects[]} first entry. */
    private static TargetAction parseApplyEffect(JsonObject json) {
        String effectId;
        int duration = 200;
        int amplifier = 0;
        boolean ambient = false;
        boolean particles = true;
        boolean icon = true;

        if (json.has("effects") && json.get("effects").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("effects");
            if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                JsonObject eff = arr.get(0).getAsJsonObject();
                effectId = BuiltinActions.resolveEffectId(eff);
                duration = eff.has("duration") ? eff.get("duration").getAsInt() : duration;
                amplifier = eff.has("amplifier") ? eff.get("amplifier").getAsInt() : amplifier;
                ambient = eff.has("is_ambient") && eff.get("is_ambient").getAsBoolean();
                particles = !eff.has("show_particles") || eff.get("show_particles").getAsBoolean();
                icon = !eff.has("show_icon") || eff.get("show_icon").getAsBoolean();
            } else {
                effectId = null;
            }
        } else {
            effectId = BuiltinActions.resolveEffectId(json);
            duration = json.has("duration") ? json.get("duration").getAsInt() : duration;
            amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : amplifier;
            ambient = json.has("is_ambient") && json.get("is_ambient").getAsBoolean();
            particles = !json.has("show_particles") || json.get("show_particles").getAsBoolean();
            icon = !json.has("show_icon") || json.get("show_icon").getAsBoolean();
        }

        if (effectId == null) return (t, a) -> {};
        var effectHolder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId)).orElse(null);
        if (effectHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] apply_effect (target): unknown mob effect '{}' — action will no-op", effectId);
            return (t, a) -> {};
        }
        final int fDur = duration;
        final int fAmp = amplifier;
        final boolean fAmb = ambient;
        final boolean fPart = particles;
        final boolean fIcon = icon;
        return (t, a) -> t.addEffect(new MobEffectInstance(effectHolder, fDur, fAmp, fAmb, fPart, fIcon));
    }

    /** Mirrors {@code BuiltinActions.clear_effect}: one effect, or all when absent. */
    private static TargetAction parseClearEffect(JsonObject json) {
        String effectId = json.has("effect") ? json.get("effect").getAsString() : null;
        if (effectId == null) {
            return (t, a) -> t.removeAllEffects();
        }
        var effectHolder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId)).orElse(null);
        if (effectHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] clear_effect (target): unknown mob effect '{}' — action will no-op", effectId);
            return (t, a) -> {};
        }
        return (t, a) -> t.removeEffect(effectHolder);
    }

    /** Mirrors {@code BuiltinActions.play_sound}, emitted at the target's position. */
    private static TargetAction parsePlaySound(JsonObject json) {
        String soundId = json.has("sound") ? json.get("sound").getAsString() : null;
        if (soundId == null) return (t, a) -> {};
        float volume = json.has("volume") ? json.get("volume").getAsFloat() : 1.0f;
        float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 1.0f;
        var soundHolder = BuiltInRegistries.SOUND_EVENT.get(Identifier.parse(soundId)).orElse(null);
        if (soundHolder == null) {
            NeoOrigins.LOGGER.warn("[CompatB] play_sound (target): unknown sound event '{}' — action will no-op", soundId);
            return (t, a) -> {};
        }
        var sound = soundHolder.value();
        return (t, a) -> {
            if (t.level() instanceof ServerLevel sl) {
                sl.playSound(null, t.getX(), t.getY(), t.getZ(),
                    sound, SoundSource.PLAYERS, volume, pitch);
            }
        };
    }

    /** Mirrors {@code BuiltinActions.damage}, dealt to the target via its own level's sources. */
    private static TargetAction parseDamage(JsonObject json) {
        float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
        String sourceType = "";
        if (json.has("source") && json.get("source").isJsonObject()) {
            var src = json.getAsJsonObject("source");
            sourceType = src.has("name") ? src.get("name").getAsString() : "";
        }
        final String fSrc = sourceType;
        return (t, a) -> {
            var sources = t.level().damageSources();
            var dmgSrc = switch (fSrc) {
                case "fire", "on_fire", "in_fire" -> sources.onFire();
                case "lava"   -> sources.lava();
                case "magic"  -> sources.magic();
                case "starve" -> sources.starve();
                case "drown"  -> sources.drown();
                case "freeze" -> sources.freeze();
                case "wither" -> sources.wither();
                // Attribute unnamed damage to the actor when it's a player, so
                // AoE / single-target damage grants kill credit (mirrors the old
                // AoE fan-out hack, which used playerAttack(caster) on the default
                // arm). Falls back to generic() when the actor isn't a player.
                default       -> a instanceof net.minecraft.server.level.ServerPlayer sp
                    ? t.level().damageSources().playerAttack(sp)
                    : sources.generic();
            };
            t.hurt(dmgSrc, amount);
        };
    }
}
