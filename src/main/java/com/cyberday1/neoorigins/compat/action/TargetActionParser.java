package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;

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

            case "neoorigins:apply_effect"    -> parseApplyEffect(json);
            case "neoorigins:clear_effect"    -> parseClearEffect(json);
            case "neoorigins:play_sound"      -> parsePlaySound(json);
            case "neoorigins:damage"          -> parseDamage(json);
            case "neoorigins:spawn_particles" -> parseSpawnParticles(json);

            // and — compose child target-actions. Generalizable ONLY when every
            // child is generalizable; if any child is player-only we return null
            // so the caller keeps the player-only EntityAction path for the whole
            // `and` (players run the full list, mobs are skipped — legacy behaviour).
            // Without this, AoE powers that wrap their leaves in `and` (e.g. Golem
            // Ground Slam = and[damage, apply_effect]) produced a null TargetAction
            // and silently did nothing to mobs.
            case "neoorigins:and" -> parseAnd(json, contextId);

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

    /**
     * Compose {@code neoorigins:and} into a single {@link TargetAction}. Returns
     * {@code null} unless EVERY child verb is itself generalizable — that keeps
     * the player-only path intact for any `and` containing a player-only verb
     * (the caller then runs the full list on players and skips mobs, as before).
     */
    private static TargetAction parseAnd(JsonObject json, String contextId) {
        if (!json.has("actions") || !json.get("actions").isJsonArray()) return null;
        JsonArray arr = json.getAsJsonArray("actions");
        java.util.List<TargetAction> children = new java.util.ArrayList<>(arr.size());
        for (var el : arr) {
            if (!el.isJsonObject()) return null;
            TargetAction child = parse(el.getAsJsonObject(), contextId);
            if (child == null) return null; // any non-generalizable child → stay player-only
            children.add(child);
        }
        if (children.isEmpty()) return null;
        final java.util.List<TargetAction> fChildren = children;
        return (t, a) -> {
            for (TargetAction c : fChildren) c.execute(t, a);
        };
    }

    /**
     * Mirrors {@code BuiltinActions.apply_effect} by sharing its resolver, so the
     * target path accepts the same three shapes — nested {@code effect} object,
     * {@code effects[]} (all entries), flat {@code effect}/{@code id} — and applies
     * every effect asked for rather than only the first.
     */
    private static TargetAction parseApplyEffect(JsonObject json) {
        var specs = BuiltinActions.resolveEffectSpecs(json, "apply_effect (target)");
        if (specs.isEmpty()) return (t, a) -> {};
        if (specs.size() == 1) {
            final var only = specs.getFirst();
            return (t, a) -> t.addEffect(only.toInstance());
        }
        final var all = java.util.List.copyOf(specs);
        return (t, a) -> {
            for (var spec : all) t.addEffect(spec.toInstance());
        };
    }

    /**
     * Mirrors {@code BuiltinActions.spawn_particles}, but emits at the TARGET's
     * position instead of the actor's. This is what lets an {@code area_of_effect}
     * aura paint custom particles onto every entity it touches (the player-path
     * version only ever draws on the holder). Same field contract as the
     * BuiltinActions counterpart: simple/data-less particles only.
     */
    private static TargetAction parseSpawnParticles(JsonObject json) {
        String particleId = json.has("particle") ? json.get("particle").getAsString() : "minecraft:poof";
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        double speed = json.has("speed") ? json.get("speed").getAsDouble() : 0.0;
        double offsetY = json.has("offset_y") ? json.get("offset_y").getAsDouble() : 0.0;
        double sx = 0, sy = 0, sz = 0;
        if (json.has("spread") && json.get("spread").isJsonObject()) {
            var sp = json.getAsJsonObject("spread");
            sx = sp.has("x") ? sp.get("x").getAsDouble() : 0.0;
            sy = sp.has("y") ? sp.get("y").getAsDouble() : 0.0;
            sz = sp.has("z") ? sp.get("z").getAsDouble() : 0.0;
        }
        net.minecraft.core.particles.ParticleOptions options = null;
        var pid = Identifier.tryParse(particleId);
        if (pid != null) {
            var ptypeOpt = BuiltInRegistries.PARTICLE_TYPE.get(pid);
            if (ptypeOpt.isPresent()
                    && ptypeOpt.get().value() instanceof net.minecraft.core.particles.ParticleOptions po) {
                options = po;
            }
        }
        if (options == null) {
            NeoOrigins.LOGGER.warn("[CompatB] spawn_particles (target): unsupported/unknown particle '{}' — no-op", particleId);
            return (t, a) -> {};
        }
        final net.minecraft.core.particles.ParticleOptions fOpts = options;
        final double fsx = sx, fsy = sy, fsz = sz, foffY = offsetY, fspeed = speed;
        final int fcount = count;
        return (t, a) -> {
            if (t.level() instanceof ServerLevel lvl) {
                lvl.sendParticles(fOpts, t.getX(), t.getY() + foffY, t.getZ(),
                    fcount, fsx, fsy, fsz, fspeed);
            }
        };
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
