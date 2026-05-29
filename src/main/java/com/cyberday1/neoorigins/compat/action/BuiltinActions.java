package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.ActionType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in {@link ActionType} descriptors — the registry refactor's static,
 * class-load-time source of truth for action verbs that have been migrated off
 * the {@code ActionParser} switch.
 *
 * <p><b>Why static, not just the NeoForge registry?</b> {@link com.cyberday1.neoorigins.compat.registry.CompatRegistries}
 * exposes the verb set via {@code actionKeys()}, but that reads the live registry
 * which only populates after {@code NewRegistryEvent} fires — i.e. never in the
 * headless harnesses ({@code compatTest}, {@code goldenMaster}, {@code schemaFormCheck}).
 * This table is available the moment the class loads, with or without a running
 * NeoForge, so it can back both the parser's dispatch and {@code KNOWN_TYPES}
 * auditing headlessly. At mod init {@code CompatRegistries.register} copies every
 * entry into the DeferredRegister, so runtime lookups and addon contributions
 * see the same descriptors through the registry.
 *
 * <p>Migration is verb-by-verb (locked decision D1): each entry added here lets
 * its {@code case} arm be deleted from {@code ActionParser}, gated on the
 * golden-master staying byte-identical and {@code SchemaFormCheck} green.
 */
public final class BuiltinActions {

    private BuiltinActions() {}

    /** Insertion-ordered so registration/audit output is deterministic. */
    private static final Map<ResourceLocation, ActionType> DESCRIPTORS = new LinkedHashMap<>();
    /** Canonical {@code "neoorigins:<verb>"} string → descriptor, for hot-path dispatch. */
    private static final Map<String, ActionType> BY_KEY = new java.util.HashMap<>();

    private static void define(String path, ActionType.Factory factory, List<FieldSpec> fields) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        ActionType type = new ActionType(id, factory, fields);
        DESCRIPTORS.put(id, type);
        BY_KEY.put(id.toString(), type);
    }

    static {
        // nothing — explicit no-op. Lift-and-shift of `case "neoorigins:nothing"
        // -> EntityAction.noop()`. No config fields.
        define("nothing", (json, ctx) -> EntityAction.noop(), List.of());

        // extinguish — clear the player's fire. No config fields.
        define("extinguish", (json, ctx) -> player -> player.clearFire(), List.of());

        // dismount — stop riding the current vehicle. No config fields.
        define("dismount", (json, ctx) -> player -> player.stopRiding(), List.of());

        // heal — restore health. Lift-and-shift of parseHeal. `amount` is
        // optional at parse time (parser falls back to 1.0), so it's modelled
        // optional-with-default rather than required — the FieldSpec reflects the
        // parser's actual contract, which collapses the two redundant schema
        // branches (shared "amount-only" vs. the per-verb branch) into one shape.
        define("heal",
            (json, ctx) -> {
                float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
                return player -> player.heal(amount);
            },
            List.of(new FieldSpec("amount", FormFieldSpec.Kind.NUMBER, false)
                .def(1.0)
                .doc("Health points to restore (1.0 = half a heart; default 1.0).")));

        // exhaust — add food exhaustion. Lift-and-shift of parseExhaust. `amount`
        // is optional at parse time (parser falls back to 1.0), so it is modelled
        // optional-with-default; the parser is the contract, not the schema's
        // shared amount-only `required` branch.
        define("exhaust",
            (json, ctx) -> {
                float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
                return player -> player.getFoodData().addExhaustion(amount);
            },
            List.of(new FieldSpec("amount", FormFieldSpec.Kind.NUMBER, false)
                .def(1.0)
                .range(0.0, null)
                .doc("Exhaustion points added (default 1.0).")));

        // gain_air — restore air supply, clamped to max. Lift-and-shift of
        // parseGainAir. `amount` optional (parser default 10).
        define("gain_air",
            (json, ctx) -> {
                int amount = json.has("amount") ? json.get("amount").getAsInt() : 10;
                return player -> player.setAirSupply(
                    Math.min(player.getMaxAirSupply(), player.getAirSupply() + amount));
            },
            List.of(new FieldSpec("amount", FormFieldSpec.Kind.INTEGER, false)
                .def(10)
                .doc("Air-supply ticks to add, clamped to max (default 10).")));

        // feed — eat food + saturation. Lift-and-shift of parseFeed. Both fields
        // optional (parser defaults food=1, saturation=0.0).
        define("feed",
            (json, ctx) -> {
                int food = json.has("food") ? json.get("food").getAsInt() : 1;
                float saturation = json.has("saturation") ? json.get("saturation").getAsFloat() : 0.0f;
                return player -> player.getFoodData().eat(food, saturation);
            },
            List.of(
                new FieldSpec("food", FormFieldSpec.Kind.INTEGER, false)
                    .def(1)
                    .range(0.0, null)
                    .doc("Food points added (default 1)."),
                new FieldSpec("saturation", FormFieldSpec.Kind.NUMBER, false)
                    .def(0.0)
                    .range(0.0, null)
                    .doc("Saturation points added (default 0.0).")));

        // set_fall_distance — overwrite the player's fall distance. Lift-and-shift
        // of parseSetFallDistance. `fall_distance` optional (parser default 0.0).
        define("set_fall_distance",
            (json, ctx) -> {
                float distance = json.has("fall_distance") ? json.get("fall_distance").getAsFloat() : 0.0f;
                return player -> player.fallDistance = distance;
            },
            List.of(new FieldSpec("fall_distance", FormFieldSpec.Kind.NUMBER, false)
                .def(0.0)
                .doc("New fall distance value (default 0.0 — resets fall damage).")));

        // set_on_fire — set remaining fire ticks. Lift-and-shift of parseSetOnFire.
        // `ticks` optional (parser default 20).
        define("set_on_fire",
            (json, ctx) -> {
                int ticks = json.has("ticks") ? json.get("ticks").getAsInt() : 20;
                return player -> player.setRemainingFireTicks(ticks);
            },
            List.of(new FieldSpec("ticks", FormFieldSpec.Kind.INTEGER, false)
                .def(20)
                .range(0.0, null)
                .doc("Fire duration in ticks (default 20 = 1s).")));

        // trigger_cooldown — start a power's cooldown. Lift-and-shift of
        // parseTriggerCooldown. `power` is the only hard requirement (parser
        // no-ops when absent); `cooldown` optional (parser default 20).
        define("trigger_cooldown",
            (json, ctx) -> {
                int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 20;
                String powerId = json.has("power") ? json.get("power").getAsString() : null;
                if (powerId == null) return EntityAction.noop();
                return player -> {
                    var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                    data.setCooldown(powerId, player.tickCount, cooldown);
                };
            },
            List.of(
                new FieldSpec("power", FormFieldSpec.Kind.STRING, true)
                    .doc("Power id whose cooldown to start."),
                new FieldSpec("cooldown", FormFieldSpec.Kind.INTEGER, false)
                    .def(20)
                    .range(0.0, null)
                    .doc("Cooldown duration in ticks (20 = 1s; default 20).")));

        // add_velocity — push (or set) the player's delta movement. Lift-and-shift
        // of parseAddVelocity. All fields optional (x/y/z default 0, set false).
        // hurtMarked is set so the client doesn't discard the server velocity.
        define("add_velocity",
            (json, ctx) -> {
                double x = json.has("x") ? json.get("x").getAsDouble() : 0;
                double y = json.has("y") ? json.get("y").getAsDouble() : 0;
                double z = json.has("z") ? json.get("z").getAsDouble() : 0;
                boolean set = json.has("set") && json.get("set").getAsBoolean();
                return player -> {
                    if (set) player.setDeltaMovement(x, y, z);
                    else player.push(x, y, z);
                    player.hurtMarked = true;
                };
            },
            List.of(
                new FieldSpec("x", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("X velocity component (default 0)."),
                new FieldSpec("y", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Y velocity component (default 0)."),
                new FieldSpec("z", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Z velocity component (default 0)."),
                new FieldSpec("set", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                    .doc("If true, replaces delta movement instead of pushing additively.")));

        // dash — impulse along the player's look vector. Lift-and-shift of
        // parseDash. All fields optional (strength 1.5, allow_vertical true,
        // set_velocity false).
        define("dash",
            (json, ctx) -> {
                float strength = json.has("strength") ? json.get("strength").getAsFloat() : 1.5f;
                boolean allowVertical = !json.has("allow_vertical") || json.get("allow_vertical").getAsBoolean();
                boolean setVelocity = json.has("set_velocity") && json.get("set_velocity").getAsBoolean();
                return player -> {
                    net.minecraft.world.phys.Vec3 look = player.getLookAngle();
                    double dx = look.x * strength;
                    double dy = allowVertical ? look.y * strength : 0.0;
                    double dz = look.z * strength;
                    if (setVelocity) {
                        player.setDeltaMovement(dx, dy, dz);
                    } else {
                        player.push(dx, dy, dz);
                    }
                    player.hurtMarked = true;
                };
            },
            List.of(
                new FieldSpec("strength", FormFieldSpec.Kind.NUMBER, false).def(1.5)
                    .doc("Impulse magnitude along player look vector (default 1.5)."),
                new FieldSpec("allow_vertical", FormFieldSpec.Kind.BOOLEAN, false).def(true)
                    .doc("If false, dash is pinned to horizontal (default true)."),
                new FieldSpec("set_velocity", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                    .doc("If true, replaces delta movement instead of pushing additively.")));

        // swing_hand — animate the main-hand swing. Lift-and-shift of the inline
        // case arm. No config fields.
        define("swing_hand",
            (json, ctx) -> player -> player.swing(net.minecraft.world.InteractionHand.MAIN_HAND),
            List.of());

        // crafting_table — open a crafting menu at the player's position.
        // Lift-and-shift of the inline case arm. No config fields.
        define("crafting_table",
            (json, ctx) -> player -> player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new net.minecraft.world.inventory.CraftingMenu(
                    id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(p.level(), p.blockPosition())),
                net.minecraft.network.chat.Component.translatable("container.crafting"))),
            List.of());

        // invert — no-op: modifier inversion has no entity-action equivalent.
        // Lift-and-shift of the inline case arm. No config fields.
        define("invert", (json, ctx) -> EntityAction.noop(), List.of());

        // cancel_event — cancel the current dispatch if its context is cancellable.
        // Lift-and-shift of parseCancelEvent (reads ActionContextHolder, no JSON).
        define("cancel_event",
            (json, ctx) -> player -> {
                Object actionCtx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
                if (actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.FoodContext fc
                    && fc.event() != null) {
                    fc.event().setCanceled(true);
                    return;
                }
                if (actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.EffectAppliedContext ec
                    && ec.event() != null) {
                    ec.event().setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent
                        .Applicable.Result.DO_NOT_APPLY);
                    return;
                }
                if (actionCtx instanceof net.neoforged.bus.api.ICancellableEvent ce) {
                    ce.setCanceled(true);
                }
            },
            List.of());
    }

    /** Descriptor for the given canonical {@code "neoorigins:<verb>"} id, or {@code null}. */
    public static ActionType get(String canonicalType) {
        return BY_KEY.get(canonicalType);
    }

    /** All built-in action descriptors, in registration order. */
    public static Map<ResourceLocation, ActionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }
}
