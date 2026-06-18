package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.compat.registry.ActionType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

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
    private static final Map<Identifier, ActionType> DESCRIPTORS = new LinkedHashMap<>();
    /** Canonical {@code "neoorigins:<verb>"} string → descriptor, for hot-path dispatch. */
    private static final Map<String, ActionType> BY_KEY = new java.util.HashMap<>();

    private static void define(String path, ActionType.Factory factory, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        ActionType type = new ActionType(id, factory, fields);
        DESCRIPTORS.put(id, type);
        BY_KEY.put(id.toString(), type);
    }

    /**
     * Define an aliased descriptor: {@code path} is the canonical id; every entry
     * in {@code aliasPaths} dispatches to the same factory. Only the canonical id
     * is registered ({@link #DESCRIPTORS} / the live registry) and counted toward
     * the type total — the aliases are known-verb synonyms (lift-and-shift of a
     * multi-label {@code case "a", "b" ->} switch arm), routed through
     * {@link #BY_KEY} so {@code ActionParser} dispatch accepts them verbatim, and
     * surfaced to {@code SchemaFormCheck} via {@link #aliasIds()} so the
     * {@code KNOWN_TYPES} parity check treats them as handled.
     */
    private static void define(String path, List<String> aliasPaths,
                               ActionType.Factory factory, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        List<Identifier> aliases = aliasPaths.stream()
            .map(p -> Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, p))
            .toList();
        ActionType type = new ActionType(id, factory, fields, aliases);
        DESCRIPTORS.put(id, type);
        BY_KEY.put(id.toString(), type);
        for (Identifier alias : aliases) BY_KEY.put(alias.toString(), type);
    }

    /**
     * Transform an {@code add_velocity} {@code (x,y,z)} authored in an Apoli "space"
     * into world-space delta movement.
     *
     * <p>Faithful port of {@code io.github.apace100.apoli.util.Space#toGlobal} — same matrix
     * construction, transpose-multiply, and length/normalize semantics — so packs that
     * authored {@code "space"} on add_velocity behave exactly as on real Apoli.
     *
     * <p>{@code world} (or unknown) returns the vector unchanged. {@code local*} uses the
     * player's look direction as the forward (+z) base; {@code velocity*} uses current
     * delta movement. {@code _horizontal} zeroes the base's Y; non-{@code _normalized}
     * scales the result by the base vector's length.
     */
    static Vec3 spaceToGlobal(String space, double x, double y, double z, ServerPlayer player) {
        if (space == null || space.equals("world")) return new Vec3(x, y, z);
        Vec3 base;
        boolean normalizeBase;
        switch (space) {
            case "local", "local_horizontal", "local_horizontal_normalized" -> {
                base = player.getLookAngle();
                if (!space.equals("local")) base = new Vec3(base.x, 0, base.z);
                normalizeBase = space.equals("local_horizontal_normalized");
            }
            case "velocity", "velocity_normalized",
                 "velocity_horizontal", "velocity_horizontal_normalized" -> {
                base = player.getDeltaMovement();
                if (space.equals("velocity_horizontal") || space.equals("velocity_horizontal_normalized"))
                    base = new Vec3(base.x, 0, base.z);
                normalizeBase = space.equals("velocity_normalized")
                    || space.equals("velocity_horizontal_normalized");
            }
            default -> { return new Vec3(x, y, z); }
        }
        Vector3f vec = new Vector3f((float) x, (float) y, (float) z);
        transformVectorToBase(base, vec, player.getYRot(), normalizeBase);
        return new Vec3(vec.x(), vec.y(), vec.z());
    }

    /** Apoli {@code Space#transformVectorToBase} — rotate {@code vec} so +z follows {@code base}. */
    private static void transformVectorToBase(Vec3 base, Vector3f vec, float baseYaw, boolean normalizeBase) {
        double baseScaleD = base.length();
        if (baseScaleD <= 0.007D) {
            vec.zero();
            return;
        }
        float baseScale = (float) baseScaleD;
        Vec3 normalizedBase = base.normalize();
        Matrix3f m = baseTransformMatrix(normalizedBase, baseYaw);
        if (!normalizeBase) m.scale(baseScale, baseScale, baseScale);
        vec.mulTranspose(m);
    }

    /** Apoli {@code Space#getBaseTransformMatrixFromNormalizedDirectionVector}. */
    private static Matrix3f baseTransformMatrix(Vec3 vector, float yaw) {
        double xX, xZ, zX = 0.0D, zY = vector.y, zZ = 0.0D;
        if (Math.abs(zY) != 1.0F) {
            zX = vector.x;
            zZ = vector.z;
            xX = vector.z;
            xZ = -vector.x;
            float xFactor = (float) (1 / Math.sqrt(xX * xX + xZ * xZ));
            xX *= xFactor;
            xZ *= xFactor;
        } else {
            float trigonometricYaw = -yaw * 0.0174532925F;
            xX = Mth.cos(trigonometricYaw);
            xZ = -Mth.sin(trigonometricYaw);
        }
        Matrix3f res = new Matrix3f();
        res.set(0, 0, (float) xX);
        res.set(1, 0, 0.0F);
        res.set(2, 0, (float) xZ);
        res.set(0, 1, (float) (zY * xZ));
        res.set(1, 1, (float) (zZ * xX - zX * xZ));
        res.set(2, 1, (float) (-zY * xX));
        res.set(0, 2, (float) zX);
        res.set(1, 2, (float) zY);
        res.set(2, 2, (float) zZ);
        return res;
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
                int ticks = json.has("ticks") ? json.get("ticks").getAsInt()
                          : json.has("duration") ? json.get("duration").getAsInt() : 20;
                return player -> player.setRemainingFireTicks(ticks);
            },
            List.of(new FieldSpec("ticks", FormFieldSpec.Kind.INTEGER, false)
                .def(20)
                .range(0.0, null)
                .doc("Fire duration in ticks (default 20 = 1s). Apoli `duration` is also accepted."),
                new FieldSpec("duration", FormFieldSpec.Kind.INTEGER, false)
                .range(0.0, null)
                .doc("Apoli alias for ticks.")));

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
                String space = json.has("space") ? json.get("space").getAsString() : "world";
                return player -> {
                    Vec3 v = spaceToGlobal(space, x, y, z, player);
                    if (set) player.setDeltaMovement(v.x, v.y, v.z);
                    else player.push(v.x, v.y, v.z);
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
                    .doc("If true, replaces delta movement instead of pushing additively."),
                new FieldSpec("space", FormFieldSpec.Kind.ENUM, false)
                    .options("world", "local", "local_horizontal", "local_horizontal_normalized",
                             "velocity", "velocity_normalized",
                             "velocity_horizontal", "velocity_horizontal_normalized")
                    .def("world")
                    .doc("Coordinate space for x/y/z. world = absolute axes (default); "
                       + "local = relative to look direction (+z = forward); "
                       + "velocity = relative to current movement direction. "
                       + "_horizontal variants ignore vertical look/velocity; "
                       + "_normalized variants ignore the base vector's length.")));

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
                // EntityInteractContext / BlockInteractContext carry the
                // cancellable wrapper event for entity_use, villager_interact,
                // block_use, breed, tame and bonemeal dispatches (Discord
                // report: cancel_event was a no-op on villager_interact).
                if (actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.EntityInteractContext eic
                    && eic.event() != null) {
                    eic.event().setCanceled(true);
                    return;
                }
                if (actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.BlockInteractContext bic
                    && bic.event() != null) {
                    bic.event().setCanceled(true);
                    return;
                }
                // HitTakenContext / KillContext / ProjectileHitContext carry
                // the cancellable wrapper event for the hit_taken, kill and
                // projectile_hit dispatches. The death / land / attack /
                // block_break / block_place dispatches publish the NeoForge
                // event itself and are handled by the generic branch below.
                if (actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc
                    && htc.event() != null) {
                    htc.event().setCanceled(true);
                    return;
                }
                if (actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.KillContext kc
                    && kc.event() != null) {
                    kc.event().setCanceled(true);
                    return;
                }
                if (actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.ProjectileHitContext phc
                    && phc.event() != null) {
                    phc.event().setCanceled(true);
                    return;
                }
                if (actionCtx instanceof net.neoforged.bus.api.ICancellableEvent ce) {
                    ce.setCanceled(true);
                }
            },
            List.of());

        // explode — create an explosion at the player. Lift-and-shift of
        // parseExplode. All fields optional (power 3.0, destruction none, no fire).
        define("explode",
            (json, ctx) -> {
                float power = json.has("power") ? json.get("power").getAsFloat() : 3.0f;
                boolean destructive = json.has("destruction_type")
                    && !"none".equals(json.get("destruction_type").getAsString());
                boolean fire = json.has("create_fire") && json.get("create_fire").getAsBoolean();
                return player -> {
                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
                    var blockInteraction = destructive
                        ? net.minecraft.world.level.Level.ExplosionInteraction.BLOCK
                        : net.minecraft.world.level.Level.ExplosionInteraction.NONE;
                    sl.explode(player, player.getX(), player.getY(), player.getZ(), power,
                        fire, blockInteraction);
                };
            },
            List.of(
                new FieldSpec("power", FormFieldSpec.Kind.NUMBER, false).def(3.0).range(0.0, null)
                    .doc("Explosion strength (default 3.0; TNT = 4)."),
                new FieldSpec("destruction_type", FormFieldSpec.Kind.ENUM, false)
                    .options("none", "break", "destroy").def("none")
                    .doc("Block-interaction mode; 'none' is entity-only (default 'none')."),
                new FieldSpec("create_fire", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                    .doc("Spawn fire on affected blocks (default false).")));

        // modify_food — one-shot food/saturation adjustment. Lift-and-shift of
        // parseModifyFood. All fields optional (deltas default 0), with the
        // food_component_* aliases the parser reads as fallbacks.
        define("modify_food",
            (json, ctx) -> {
                int foodDelta = json.has("food") ? json.get("food").getAsInt()
                              : json.has("food_component_food") ? json.get("food_component_food").getAsInt()
                              : 0;
                float satDelta = json.has("saturation") ? json.get("saturation").getAsFloat()
                               : json.has("food_component_saturation") ? json.get("food_component_saturation").getAsFloat()
                               : 0.0f;
                return player -> {
                    var food = player.getFoodData();
                    int newFood = Math.max(0, Math.min(20, food.getFoodLevel() + foodDelta));
                    float newSat = Math.max(0f, Math.min(newFood, food.getSaturationLevel() + satDelta));
                    food.setFoodLevel(newFood);
                    food.setSaturation(newSat);
                };
            },
            List.of(
                new FieldSpec("food", FormFieldSpec.Kind.INTEGER, false).def(0)
                    .doc("Food points delta."),
                new FieldSpec("food_component_food", FormFieldSpec.Kind.INTEGER, false)
                    .doc("Alias for food."),
                new FieldSpec("saturation", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Saturation delta."),
                new FieldSpec("food_component_saturation", FormFieldSpec.Kind.NUMBER, false)
                    .doc("Alias for saturation.")));

        // damage — hurt the player with a chosen damage source. Lift-and-shift of
        // parseDamage. `amount` optional (parser default 1.0); the source type is
        // read from a nested `source.name` string (parser default "" → generic).
        define("damage",
            (json, ctx) -> {
                float amount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0f;
                String sourceType = "";
                if (json.has("source") && json.get("source").isJsonObject()) {
                    var src = json.getAsJsonObject("source");
                    sourceType = src.has("name") ? src.get("name").getAsString() : "";
                }
                final String fSrc = sourceType;
                return player -> {
                    var dmgSrc = switch (fSrc) {
                        case "fire", "on_fire", "in_fire" -> player.level().damageSources().onFire();
                        case "lava"   -> player.level().damageSources().lava();
                        case "magic"  -> player.level().damageSources().magic();
                        case "starve" -> player.level().damageSources().starve();
                        case "drown"  -> player.level().damageSources().drown();
                        case "freeze" -> player.level().damageSources().freeze();
                        case "wither" -> player.level().damageSources().wither();
                        default       -> player.level().damageSources().generic();
                    };
                    player.hurt(dmgSrc, amount);
                };
            },
            List.of(
                new FieldSpec("amount", FormFieldSpec.Kind.NUMBER, false).def(1.0).range(0.0, null)
                    .doc("Damage dealt in half-hearts (default 1.0)."),
                new FieldSpec("source", FormFieldSpec.Kind.MIXED, false)
                    .doc("Optional damage source; reads `source.name` (fire/lava/magic/starve/drown/freeze/wither; default generic).")));

        // give — give an item stack to the player. Lift-and-shift of parseGive
        // (26.1: Identifier + lambda-internal BuiltInRegistries.ITEM.get lookup).
        // Item id (from `stack.item`/`item`) is the hard requirement; parser
        // no-ops without it. `count` optional (parser default 1).
        define("give",
            (json, ctx) -> {
                var stack = json.has("stack") ? json.getAsJsonObject("stack") : json;
                String itemId = stack.has("item") ? stack.get("item").getAsString() : null;
                if (itemId == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] give: missing item id — action will no-op");
                    return EntityAction.noop();
                }
                // Apoli/Origins item-stack objects use `amount`; accept that first,
                // then vanilla `count`, else default 1. Reading only `count` made
                // every Apoli-format give grant a single item.
                int count = stack.has("amount") ? stack.get("amount").getAsInt()
                          : stack.has("count") ? stack.get("count").getAsInt()
                          : 1;
                net.minecraft.resources.Identifier iid = net.minecraft.resources.Identifier.parse(itemId);
                return player -> {
                    var itemOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(iid);
                    if (itemOpt.isEmpty()) return;
                    var itemStack = new net.minecraft.world.item.ItemStack(itemOpt.get(), count);
                    if (!player.getInventory().add(itemStack)) {
                        var drop = new net.minecraft.world.entity.item.ItemEntity(
                            player.level(), player.getX(), player.getY(), player.getZ(), itemStack);
                        player.level().addFreshEntity(drop);
                    }
                };
            },
            List.of(
                new FieldSpec("item", FormFieldSpec.Kind.STRING, false)
                    .doc("Item id to give (or place under `stack.item`)."),
                new FieldSpec("count", FormFieldSpec.Kind.INTEGER, false).def(1).range(1.0, null)
                    .doc("Stack size (default 1)."),
                new FieldSpec("stack", FormFieldSpec.Kind.OBJECT, false)
                    .doc("Optional nested item-stack object; its item/count override the flat fields.")
                    .children(
                        new FieldSpec("item", FormFieldSpec.Kind.STRING, false)
                            .doc("Item id for the stack (e.g. minecraft:diamond)."),
                        new FieldSpec("count", FormFieldSpec.Kind.INTEGER, false).def(1).range(1.0, null)
                            .doc("Stack size (default 1)."),
                        new FieldSpec("amount", FormFieldSpec.Kind.INTEGER, false).range(1.0, null)
                            .doc("Apoli alias for count (stack size)."))));

        // launch — push the player straight up. Lift-and-shift of parseLaunch.
        // `speed` optional (parser default 1.0).
        define("launch",
            (json, ctx) -> {
                float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.0f;
                return player -> {
                    player.push(0, speed, 0);
                    player.hurtMarked = true;
                };
            },
            List.of(new FieldSpec("speed", FormFieldSpec.Kind.NUMBER, false).def(1.0)
                .doc("Upward impulse magnitude (default 1.0).")));

        // set_block — place a block at the player's position. Lift-and-shift of
        // parseSetBlock (26.1: Identifier + lambda-internal BuiltInRegistries.BLOCK
        // .get().value()). Block id is the hard requirement (parser no-ops without
        // it). `keep` optional (parser default false). The legacy
        // origins:temporary_cobweb id is rewritten to minecraft:cobweb.
        define("set_block",
            (json, ctx) -> {
                String blockId = json.has("block") ? json.get("block").getAsString() : null;
                if (blockId == null) return EntityAction.noop();
                if (blockId.equals("origins:temporary_cobweb")) blockId = "minecraft:cobweb";
                net.minecraft.resources.Identifier bid = net.minecraft.resources.Identifier.parse(blockId);
                boolean keep = json.has("keep") && json.get("keep").getAsBoolean();
                return player -> {
                    var blockOpt = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(bid);
                    if (blockOpt.isEmpty()) return;
                    var block = blockOpt.get().value();
                    var pos = player.blockPosition();
                    if (keep && !player.level().getBlockState(pos).isAir()) return;
                    player.level().setBlock(pos, block.defaultBlockState(), 3);
                };
            },
            List.of(
                new FieldSpec("block", FormFieldSpec.Kind.STRING, true)
                    .doc("Block id to place at the player's position."),
                new FieldSpec("keep", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                    .doc("If true, only place when the target position is air (default false).")));

        // clear_effect — remove one mob effect, or all when `effect` is absent.
        // Lift-and-shift of parseClearEffect (26.1: Identifier + MOB_EFFECT.get
        // returns the holder directly). An unknown id resolves at parse time and
        // no-ops with a warning.
        define("clear_effect",
            (json, ctx) -> {
                String effectId = json.has("effect") ? json.get("effect").getAsString() : null;
                if (effectId == null) {
                    return player -> player.removeAllEffects();
                }
                var effectHolder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .get(net.minecraft.resources.Identifier.parse(effectId)).orElse(null);
                if (effectHolder == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] clear_effect: unknown effect '{}' — action will no-op", effectId);
                    return EntityAction.noop();
                }
                return player -> player.removeEffect(effectHolder);
            },
            List.of(new FieldSpec("effect", FormFieldSpec.Kind.STRING, false)
                .doc("Mob effect id to remove; omit to clear all effects.")));

        // play_sound — play a sound at the player on the server. Lift-and-shift of
        // parsePlaySound (26.1: Identifier + SOUND_EVENT.get holder -> .value()).
        // `sound` is the hard requirement (parser no-ops without it);
        // `volume`/`pitch` optional (parser default 1.0 each).
        define("play_sound",
            (json, ctx) -> {
                String soundId = json.has("sound") ? json.get("sound").getAsString() : null;
                if (soundId == null) return EntityAction.noop();
                float volume = json.has("volume") ? json.get("volume").getAsFloat() : 1.0f;
                float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 1.0f;
                var soundHolder = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                    .get(net.minecraft.resources.Identifier.parse(soundId)).orElse(null);
                if (soundHolder == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] play_sound: unknown sound '{}' — action will no-op", soundId);
                    return EntityAction.noop();
                }
                var sound = soundHolder.value();
                return player -> {
                    if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                            sound, net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
                    }
                };
            },
            List.of(
                new FieldSpec("sound", FormFieldSpec.Kind.STRING, true)
                    .doc("Sound event id to play."),
                new FieldSpec("volume", FormFieldSpec.Kind.NUMBER, false).def(1.0).range(0.0, null)
                    .doc("Playback volume (default 1.0)."),
                new FieldSpec("pitch", FormFieldSpec.Kind.NUMBER, false).def(1.0).range(0.0, null)
                    .doc("Playback pitch (default 1.0).")));

        // emit_game_event — broadcast a vanilla game event from the player.
        // Lift-and-shift of parseEmitGameEvent (26.1: Identifier + GAME_EVENT.get
        // holder passed directly to level().gameEvent). The event id (from `event`
        // or the `game_event` alias) is the hard requirement (parser no-ops
        // without it); unknown ids resolve at parse time and no-op with a warning.
        define("emit_game_event",
            (json, ctx) -> {
                String eventId = json.has("event") ? json.get("event").getAsString()
                               : json.has("game_event") ? json.get("game_event").getAsString() : null;
                if (eventId == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] emit_game_event: missing event id — action will no-op");
                    return EntityAction.noop();
                }
                net.minecraft.resources.Identifier eid = net.minecraft.resources.Identifier.parse(eventId);
                var evOpt = net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.get(eid);
                if (evOpt.isEmpty()) {
                    NeoOrigins.LOGGER.warn("[CompatB] emit_game_event: unknown event '{}' — action will no-op", eid);
                    return EntityAction.noop();
                }
                final var gameEvent = evOpt.get();
                return player -> player.level().gameEvent(player, gameEvent, player.position());
            },
            List.of(
                new FieldSpec("event", FormFieldSpec.Kind.STRING, false)
                    .doc("Game event id to emit (or use the `game_event` alias)."),
                new FieldSpec("game_event", FormFieldSpec.Kind.STRING, false)
                    .doc("Alias for event.")));

        // add_xp — grant experience points and/or levels. Lift-and-shift of
        // parseAddXp. Both fields optional (parser default 0); a zero value is
        // simply not applied.
        define("add_xp",
            (json, ctx) -> {
                int points = json.has("points") ? json.get("points").getAsInt() : 0;
                int levels = json.has("levels") ? json.get("levels").getAsInt() : 0;
                return player -> {
                    if (points != 0) player.giveExperiencePoints(points);
                    if (levels != 0) player.giveExperienceLevels(levels);
                };
            },
            List.of(
                new FieldSpec("points", FormFieldSpec.Kind.INTEGER, false).def(0)
                    .doc("Experience points to grant (default 0)."),
                new FieldSpec("levels", FormFieldSpec.Kind.INTEGER, false).def(0)
                    .doc("Experience levels to grant (default 0).")));

        // grant_power — dynamically grant a power to the player. Lift-and-shift of
        // parseGrantPower (26.1: Identifier id). The power id (from `power` or the
        // `power_id` alias) is the hard requirement (parser no-ops without it).
        define("grant_power",
            (json, ctx) -> {
                String powerId = json.has("power") ? json.get("power").getAsString()
                               : json.has("power_id") ? json.get("power_id").getAsString() : null;
                if (powerId == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] grant_power: missing power id — action will no-op");
                    return EntityAction.noop();
                }
                final net.minecraft.resources.Identifier pid = net.minecraft.resources.Identifier.parse(powerId);
                return player -> {
                    var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                    if (data.hasDynamicGrant(pid)) return;
                    var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(pid);
                    if (holder == null) {
                        NeoOrigins.LOGGER.warn("[CompatB] grant_power: unknown power '{}'", pid);
                        return;
                    }
                    boolean fromOrigin = false;
                    for (var entry : data.getOrigins().entrySet()) {
                        var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(entry.getValue());
                        if (origin != null && origin.powers().contains(pid)) { fromOrigin = true; break; }
                    }
                    if (fromOrigin) {
                        data.addDynamicGrant(pid);
                        return;
                    }
                    if (data.addDynamicGrant(pid)) {
                        holder.onGranted(player);
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new com.cyberday1.neoorigins.api.event.PowerGrantedEvent(player, pid));
                        com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncToPlayer(player);
                    }
                };
            },
            List.of(
                new FieldSpec("power", FormFieldSpec.Kind.STRING, false)
                    .doc("Power id to grant (or use the `power_id` alias)."),
                new FieldSpec("power_id", FormFieldSpec.Kind.STRING, false)
                    .doc("Alias for power."),
                new FieldSpec("source", FormFieldSpec.Kind.STRING, false)
                    .doc("Apoli-style 'source' label; accepted but ignored by this implementation.")));

        // revoke_power — remove a dynamically-granted power from the player.
        // Lift-and-shift of parseRevokePower (26.1: Identifier id). The power id
        // (from `power` or the `power_id` alias) is the hard requirement.
        define("revoke_power",
            (json, ctx) -> {
                String powerId = json.has("power") ? json.get("power").getAsString()
                               : json.has("power_id") ? json.get("power_id").getAsString() : null;
                if (powerId == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] revoke_power: missing power id — action will no-op");
                    return EntityAction.noop();
                }
                final net.minecraft.resources.Identifier pid = net.minecraft.resources.Identifier.parse(powerId);
                return player -> {
                    var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                    if (!data.hasDynamicGrant(pid)) return;
                    var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(pid);
                    if (data.removeDynamicGrant(pid) && holder != null) {
                        boolean stillGranted = false;
                        for (var entry : data.getOrigins().entrySet()) {
                            var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(entry.getValue());
                            if (origin != null && origin.powers().contains(pid)) { stillGranted = true; break; }
                        }
                        if (!stillGranted) {
                            holder.onRevoked(player);
                            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                                new com.cyberday1.neoorigins.api.event.PowerRevokedEvent(player, pid));
                        }
                        com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncToPlayer(player);
                    }
                };
            },
            List.of(
                new FieldSpec("power", FormFieldSpec.Kind.STRING, false)
                    .doc("Power id to revoke (or use the `power_id` alias)."),
                new FieldSpec("power_id", FormFieldSpec.Kind.STRING, false)
                    .doc("Alias for power."),
                new FieldSpec("source", FormFieldSpec.Kind.STRING, false)
                    .doc("Apoli-style 'source' label; accepted but ignored by this implementation.")));

        // teleport_to_marker — teleport to absolute coords (`position`) or by a
        // dx/dy/dz offset. Lift-and-shift of parseTeleportToMarker. All fields
        // optional (offsets default 0); presence of `position` switches to
        // absolute mode.
        define("teleport_to_marker",
            (json, ctx) -> {
                final double dx = json.has("dx") ? json.get("dx").getAsDouble() : 0;
                final double dy = json.has("dy") ? json.get("dy").getAsDouble() : 0;
                final double dz = json.has("dz") ? json.get("dz").getAsDouble() : 0;
                final boolean absolute = json.has("position");
                final double px = absolute ? json.getAsJsonObject("position").get("x").getAsDouble() : 0;
                final double py = absolute ? json.getAsJsonObject("position").get("y").getAsDouble() : 0;
                final double pz = absolute ? json.getAsJsonObject("position").get("z").getAsDouble() : 0;
                return player -> {
                    if (absolute) {
                        player.teleportTo(px, py, pz);
                    } else {
                        player.teleportTo(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
                    }
                };
            },
            List.of(
                new FieldSpec("position", FormFieldSpec.Kind.OBJECT, false)
                    .doc("Absolute target {x, y, z}; when present, overrides the dx/dy/dz offset.")
                    .children(
                        new FieldSpec("x", FormFieldSpec.Kind.NUMBER, true)
                            .doc("Absolute X coordinate."),
                        new FieldSpec("y", FormFieldSpec.Kind.NUMBER, true)
                            .doc("Absolute Y coordinate."),
                        new FieldSpec("z", FormFieldSpec.Kind.NUMBER, true)
                            .doc("Absolute Z coordinate.")),
                new FieldSpec("dx", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("X offset from the player (default 0)."),
                new FieldSpec("dy", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Y offset from the player (default 0)."),
                new FieldSpec("dz", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Z offset from the player (default 0).")));

        // random_teleport — try N random nearby positions and teleport to the
        // first air gap. Lift-and-shift of parseRandomTeleport (26.1: level
        // .getMinY()/.getMaxY()). All fields optional: horizontal_range (or
        // `range` alias) default 16.0, vertical_range default 8.0, attempts 16.
        define("random_teleport",
            (json, ctx) -> {
                final double hRange = json.has("horizontal_range") ? json.get("horizontal_range").getAsDouble()
                                    : json.has("range") ? json.get("range").getAsDouble() : 16.0;
                final double vRange = json.has("vertical_range") ? json.get("vertical_range").getAsDouble() : 8.0;
                final int attempts = json.has("attempts") ? json.get("attempts").getAsInt() : 16;
                return player -> {
                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
                    var rng = player.getRandom();
                    double px = player.getX(), py = player.getY(), pz = player.getZ();
                    for (int i = 0; i < attempts; i++) {
                        double tx = px + (rng.nextDouble() - 0.5) * hRange * 2;
                        double ty = py + (rng.nextDouble() - 0.5) * vRange * 2;
                        double tz = pz + (rng.nextDouble() - 0.5) * hRange * 2;
                        ty = Math.max(level.getMinY(), Math.min(level.getMaxY() - 2, ty));
                        var target = net.minecraft.core.BlockPos.containing(tx, ty, tz);
                        if (level.getBlockState(target).isAir() && level.getBlockState(target.above()).isAir()) {
                            player.teleportTo(tx, ty, tz);
                            return;
                        }
                    }
                };
            },
            List.of(
                new FieldSpec("horizontal_range", FormFieldSpec.Kind.NUMBER, false).def(16.0).range(0.0, null)
                    .doc("Max horizontal teleport distance (or use the `range` alias; default 16.0)."),
                new FieldSpec("range", FormFieldSpec.Kind.NUMBER, false)
                    .doc("Alias for horizontal_range."),
                new FieldSpec("vertical_range", FormFieldSpec.Kind.NUMBER, false).def(8.0).range(0.0, null)
                    .doc("Max vertical teleport distance (default 8.0)."),
                new FieldSpec("attempts", FormFieldSpec.Kind.INTEGER, false).def(16).range(1.0, null)
                    .doc("Number of random positions tried before giving up (default 16).")));

        // mount — start riding the nearest unoccupied entity within `radius`.
        // Lift-and-shift of parseMount (26.1: Identifier + startRiding 3-arg).
        // `entity_type` optional (absent → any entity); `radius` optional
        // (parser default 5.0).
        define("mount",
            (json, ctx) -> {
                String entityId = json.has("entity_type") ? json.get("entity_type").getAsString() : null;
                double radius = json.has("radius") ? json.get("radius").getAsDouble() : 5.0;
                return player -> {
                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
                    net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(radius);
                    for (var entity : sl.getEntities(player, box)) {
                        if (entityId != null && !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                                .equals(net.minecraft.resources.Identifier.parse(entityId))) continue;
                        if (entity.isAlive() && !entity.isPassenger()) {
                            player.startRiding(entity, true, true);
                            return;
                        }
                    }
                };
            },
            List.of(
                new FieldSpec("entity_type", FormFieldSpec.Kind.STRING, false)
                    .doc("Entity type id to mount; omit to mount any nearby entity."),
                new FieldSpec("radius", FormFieldSpec.Kind.NUMBER, false).def(5.0).range(0.0, null)
                    .doc("Search radius around the player (default 5.0).")));

        // spawn_entity — spawn N copies of an entity type at the player. Lift-and-shift
        // of parseSpawnEntity (26.1: Identifier + ENTITY_TYPE.get holder .value(),
        // create(sl, EntitySpawnReason.COMMAND)). `entity_type` is the hard
        // requirement (parser no-ops without it). `quantity` optional (default 1;
        // <1 or non-integer warns + clamps to 1; >1 adds ±0.5-block jitter).
        define("spawn_entity",
            (json, ctx) -> {
                String entityId = json.has("entity_type") ? json.get("entity_type").getAsString() : null;
                if (entityId == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] spawn_entity: missing entity_type — action will no-op");
                    return EntityAction.noop();
                }
                net.minecraft.resources.Identifier eid = net.minecraft.resources.Identifier.parse(entityId);
                var entityTypeOpt = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(eid);
                if (entityTypeOpt.isEmpty()) {
                    NeoOrigins.LOGGER.warn("[CompatB] spawn_entity: unknown entity type '{}' — action will no-op", eid);
                    return EntityAction.noop();
                }
                final net.minecraft.world.entity.EntityType<?> entityType = entityTypeOpt.get().value();
                int q = 1;
                if (json.has("quantity")) {
                    var qEl = json.get("quantity");
                    if (qEl.isJsonPrimitive() && qEl.getAsJsonPrimitive().isNumber()) {
                        int requested = qEl.getAsInt();
                        if (requested < 1) {
                            NeoOrigins.LOGGER.warn("[CompatB] spawn_entity: 'quantity' must be >=1 (got {}), clamping to 1", requested);
                        } else {
                            q = requested;
                        }
                    } else {
                        NeoOrigins.LOGGER.warn("[CompatB] spawn_entity: 'quantity' must be an integer (got {}), clamping to 1", qEl);
                    }
                }
                final int quantity = q;
                return player -> {
                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
                    for (int i = 0; i < quantity; i++) {
                        var entity = entityType.create(sl, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        if (entity == null) continue;
                        double dx = 0.0, dz = 0.0;
                        if (quantity > 1) {
                            var rng = sl.getRandom();
                            dx = rng.nextDouble() - 0.5;
                            dz = rng.nextDouble() - 0.5;
                        }
                        entity.setPos(player.getX() + dx, player.getY(), player.getZ() + dz);
                        sl.addFreshEntity(entity);
                    }
                };
            },
            List.of(
                new FieldSpec("entity_type", FormFieldSpec.Kind.STRING, true)
                    .doc("Entity type id to spawn at the player's position."),
                new FieldSpec("quantity", FormFieldSpec.Kind.INTEGER, false).def(1).range(1.0, null)
                    .doc("Number of copies to spawn (default 1; >1 adds ±0.5-block jitter).")));

        // throw_target — find the entity under the player's crosshair within
        // `max_distance` and push it away. Lift-and-shift of parseThrowTarget
        // (26.1 ProjectileUtil.getEntityHitResult: (Entity, Vec3, Vec3, AABB,
        // Predicate, double) — shooter replaces Level, trailing double² filter).
        // All fields optional (force 1.5, vertical_lift 0.5, max_distance 5.0).
        define("throw_target",
            (json, ctx) -> {
                final float force        = json.has("force")         ? json.get("force").getAsFloat()         : 1.5f;
                final float verticalLift = json.has("vertical_lift") ? json.get("vertical_lift").getAsFloat() : 0.5f;
                final float maxDistance  = json.has("max_distance")  ? json.get("max_distance").getAsFloat()  : 5.0f;
                return player -> {
                    var eye  = player.getEyePosition();
                    var look = player.getLookAngle();
                    var end  = eye.add(look.scale(maxDistance));
                    var searchBox = player.getBoundingBox().expandTowards(look.scale(maxDistance)).inflate(1.0);
                    var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                        player, eye, end, searchBox,
                        e -> e != player && e.isAlive() && e instanceof net.minecraft.world.entity.LivingEntity,
                        (double) maxDistance * maxDistance);
                    if (hit == null) return;
                    if (!(hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity target)) return;
                    double dx = target.getX() - player.getX();
                    double dz = target.getZ() - player.getZ();
                    double horiz = Math.sqrt(dx * dx + dz * dz);
                    if (horiz < 1.0e-4) {
                        dx = look.x;
                        dz = look.z;
                        horiz = Math.sqrt(dx * dx + dz * dz);
                        if (horiz < 1.0e-4) { dx = 1.0; dz = 0.0; horiz = 1.0; }
                    }
                    double ux = dx / horiz;
                    double uz = dz / horiz;
                    target.push(ux * force, verticalLift, uz * force);
                    target.hurtMarked = true;
                };
            },
            List.of(
                new FieldSpec("force", FormFieldSpec.Kind.NUMBER, false).def(1.5)
                    .doc("Horizontal push magnitude applied to the target (default 1.5)."),
                new FieldSpec("vertical_lift", FormFieldSpec.Kind.NUMBER, false).def(0.5)
                    .doc("Upward push applied to the target (default 0.5)."),
                new FieldSpec("max_distance", FormFieldSpec.Kind.NUMBER, false).def(5.0).range(0.0, null)
                    .doc("Max crosshair search distance for a target (default 5.0).")));

        // tame_target — tame the mob resolved from the active dispatch context (a
        // bientity target: the mob hit on_hit, the entity under a raycast's
        // bientity_action, ...), falling back to the mob under the player's
        // crosshair within max_distance. Reuses TameMobPower's taming behaviour so
        // action-tamed and power-tamed pets are identical (follow/defend goals,
        // persistence, MinionTracker despawn + death-damage). Honours the global
        // tame cap (max_tamed) and the boss/blacklist gates; a no-valid-target or
        // capped activation is a silent no-op (no actionbar spam — actions fire
        // from events, not a deliberate keybind). This is the action form Koopa's
        // pack expected when it nested `tame_mob` (the active power id) in an
        // entity_action field and got the unsupported-action no-op.
        define("tame_target",
            (json, ctx) -> {
                final int maxTamed       = json.has("max_tamed")    ? json.get("max_tamed").getAsInt()       : 4;
                final double maxDistance = json.has("max_distance") ? json.get("max_distance").getAsDouble() : 5.0;
                final int despawnTicks   = json.has("despawn_ticks")? json.get("despawn_ticks").getAsInt()   : 36000;
                final float deathDamage  = json.has("death_damage") ? json.get("death_damage").getAsFloat()   : 0.5f;
                final boolean hostileOnly = !json.has("hostile_only") || json.get("hostile_only").getAsBoolean();
                final java.util.List<String> blacklist = new java.util.ArrayList<>();
                if (json.has("entity_blacklist") && json.get("entity_blacklist").isJsonArray()) {
                    for (var el : json.getAsJsonArray("entity_blacklist")) {
                        if (el.isJsonPrimitive()) blacklist.add(el.getAsString());
                    }
                }
                return player -> {
                    int alive = com.cyberday1.neoorigins.service.MinionTracker.countAlive(
                        player.getUUID(),
                        com.cyberday1.neoorigins.power.builtin.TameMobPower.tamedMobKey());
                    if (alive >= maxTamed) return;

                    // Context target first (on-hit / raycast bientity), else crosshair.
                    net.minecraft.world.entity.LivingEntity ctxTarget =
                        ActionParser.extractBientityTarget(
                            com.cyberday1.neoorigins.service.ActionContextHolder.get());
                    net.minecraft.world.entity.Mob mob =
                        ctxTarget instanceof net.minecraft.world.entity.Mob m ? m
                            : com.cyberday1.neoorigins.power.builtin.TameMobPower.lookTargetMob(player, maxDistance);
                    if (mob == null) return;
                    if (!com.cyberday1.neoorigins.power.builtin.TameMobPower.isTameable(mob, hostileOnly, blacklist)) return;

                    com.cyberday1.neoorigins.power.builtin.TameMobPower.applyTame(
                        player, mob, despawnTicks, deathDamage);
                };
            },
            List.of(
                new FieldSpec("max_tamed", FormFieldSpec.Kind.INTEGER, false).def(4).range(0.0, null)
                    .doc("Max tamed mobs alive at once (shared with tame_mob via the MinionTracker) before this action no-ops. Default 4."),
                new FieldSpec("max_distance", FormFieldSpec.Kind.NUMBER, false).def(5.0).range(0.0, null)
                    .doc("Crosshair search distance used only when no context target resolves (default 5.0)."),
                new FieldSpec("despawn_ticks", FormFieldSpec.Kind.INTEGER, false).def(36000)
                    .doc("Ticks a tamed mob lasts before despawning (36000 = 30 min)."),
                new FieldSpec("death_damage", FormFieldSpec.Kind.NUMBER, false).def(0.5)
                    .doc("Damage dealt to the player when a tamed mob dies (half-hearts)."),
                new FieldSpec("hostile_only", FormFieldSpec.Kind.BOOLEAN, false).def(true)
                    .doc("When true (default), only mobs implementing Enemy can be tamed. Set false to allow any non-player Mob."),
                new FieldSpec("entity_blacklist", FormFieldSpec.Kind.ARRAY, false)
                    .itemPattern("^#?[A-Za-z0-9_.:/-]+$")
                    .doc("Entity ids (\"minecraft:warden\") and tag refs (\"#mymod:untameable\") this action can never tame. The Warden, Ender Dragon and Wither plus the tame_scare_entity_blacklist config list are always excluded.")));

        // spawn_effect_cloud — spawn an AreaEffectCloud at the player. Lift-and-shift
        // of parseSpawnEffectCloud (26.1: Identifier). `effect` optional and
        // dual-shape (a bare id string, or an object {effect, duration, amplifier});
        // all numeric fields optional (duration 200, amplifier 0, radius 3.0,
        // wait_time 10). An unknown effect id is silently skipped.
        define("spawn_effect_cloud",
            (json, ctx) -> {
                String effectId = null;
                int duration = json.has("duration") ? json.get("duration").getAsInt() : 200;
                int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;
                if (json.has("effect")) {
                    var effectEl = json.get("effect");
                    if (effectEl.isJsonPrimitive()) {
                        effectId = effectEl.getAsString();
                    } else if (effectEl.isJsonObject()) {
                        var effectObj = effectEl.getAsJsonObject();
                        effectId = effectObj.has("effect") ? effectObj.get("effect").getAsString() : null;
                        if (effectObj.has("duration")) duration = effectObj.get("duration").getAsInt();
                        if (effectObj.has("amplifier")) amplifier = effectObj.get("amplifier").getAsInt();
                    }
                }
                float radius = json.has("radius") ? json.get("radius").getAsFloat() : 3.0f;
                int waitTime = json.has("wait_time") ? json.get("wait_time").getAsInt() : 10;
                final String fEffectId = effectId;
                final int fDuration = duration;
                final int fAmplifier = amplifier;
                return player -> {
                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
                    var cloud = new net.minecraft.world.entity.AreaEffectCloud(sl, player.getX(), player.getY(), player.getZ());
                    cloud.setRadius(radius);
                    cloud.setDuration(fDuration);
                    cloud.setWaitTime(waitTime);
                    cloud.setOwner(player);
                    if (fEffectId != null) {
                        var eff = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(
                            net.minecraft.resources.Identifier.parse(fEffectId));
                        if (eff.isPresent()) {
                            var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(eff.get());
                            cloud.addEffect(new net.minecraft.world.effect.MobEffectInstance(holder, fDuration, fAmplifier));
                        }
                    }
                    sl.addFreshEntity(cloud);
                };
            },
            List.of(
                new FieldSpec("effect", FormFieldSpec.Kind.STRING, false)
                    .doc("Mob effect id (bare string) or object {effect, duration, amplifier}; omit for an empty cloud."),
                new FieldSpec("duration", FormFieldSpec.Kind.INTEGER, false).def(200).range(0.0, null)
                    .doc("Cloud + effect duration in ticks (default 200)."),
                new FieldSpec("amplifier", FormFieldSpec.Kind.INTEGER, false).def(0).range(0.0, null)
                    .doc("Effect amplifier level (default 0)."),
                new FieldSpec("radius", FormFieldSpec.Kind.NUMBER, false).def(3.0).range(0.0, null)
                    .doc("Cloud radius in blocks (default 3.0)."),
                new FieldSpec("wait_time", FormFieldSpec.Kind.INTEGER, false).def(10).range(0.0, null)
                    .doc("Ticks before the cloud starts applying its effect (default 10).")));

        // damage_attacker — hurt the attacker recorded in the current HIT_TAKEN
        // context. Lift-and-shift of parseDamageAttacker. All fields optional:
        // `amount` (default 2.0) is overridden when `amount_ratio` is present
        // (damage = incoming * ratio, min 0.5); the source type reads
        // `source.name` (default "magic"). No-ops outside a HitTakenContext.
        define("damage_attacker",
            (json, ctx) -> {
                final float amount = json.has("amount") ? json.get("amount").getAsFloat() : 2.0f;
                final boolean useRatio = json.has("amount_ratio");
                final float ratio = useRatio ? json.get("amount_ratio").getAsFloat() : 0f;
                final String srcName = json.has("source") && json.get("source").isJsonObject()
                    && json.getAsJsonObject("source").has("name")
                    ? json.getAsJsonObject("source").get("name").getAsString()
                    : "magic";
                return player -> {
                    Object actionCtx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
                    if (!(actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc)) return;
                    var attacker = htc.source().getEntity();
                    if (!(attacker instanceof net.minecraft.world.entity.LivingEntity le)) return;
                    var ds = switch (srcName) {
                        case "fire", "on_fire", "in_fire" -> player.level().damageSources().onFire();
                        case "lava"    -> player.level().damageSources().lava();
                        case "magic"   -> player.level().damageSources().magic();
                        case "generic" -> player.level().damageSources().generic();
                        default        -> player.level().damageSources().magic();
                    };
                    float dmg = useRatio ? Math.max(0.5f, htc.amount() * ratio) : amount;
                    if (!Float.isFinite(dmg)) dmg = Float.MAX_VALUE;
                    le.hurt(ds, dmg);
                };
            },
            List.of(
                new FieldSpec("amount", FormFieldSpec.Kind.NUMBER, false).def(2.0).range(0.0, null)
                    .doc("Flat damage dealt to the attacker (default 2.0; ignored when amount_ratio is set)."),
                new FieldSpec("amount_ratio", FormFieldSpec.Kind.NUMBER, false)
                    .doc("If set, damage = incoming hit * this ratio (min 0.5), overriding amount."),
                new FieldSpec("source", FormFieldSpec.Kind.OBJECT, false)
                    .doc("Optional damage source; reads `source.name` (fire/lava/magic/generic; default magic).")
                    .children(
                        new FieldSpec("name", FormFieldSpec.Kind.STRING, false)
                            .doc("Damage source name: fire, lava, magic, or generic (default magic)."))));

        // ignite_attacker — set the current HIT_TAKEN attacker on fire.
        // Lift-and-shift of parseIgniteAttacker. `ticks` optional (parser default
        // 60). No-ops outside a HitTakenContext or when the attacker is null.
        define("ignite_attacker",
            (json, ctx) -> {
                final int ticks = json.has("ticks") ? json.get("ticks").getAsInt() : 60;
                return player -> {
                    Object actionCtx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
                    if (!(actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc)) return;
                    var attacker = htc.source().getEntity();
                    if (attacker == null) return;
                    attacker.setRemainingFireTicks(ticks);
                };
            },
            List.of(new FieldSpec("ticks", FormFieldSpec.Kind.INTEGER, false).def(60).range(0.0, null)
                .doc("Fire duration applied to the attacker in ticks (default 60).")));

        // effect_on_attacker — apply a mob effect to the current HIT_TAKEN attacker.
        // Lift-and-shift of parseEffectOnAttacker. `effect` is the hard requirement
        // (parser no-ops + warns without it); `duration` (default 100) and
        // `amplifier` (default 0) optional. Unknown ids resolve at parse time and
        // no-op with a warning. No-ops outside a HitTakenContext. (26.1:
        // BuiltInRegistries.MOB_EFFECT.get(Identifier).orElse(null) yields the
        // Holder directly — no getOptional/wrapAsHolder.)
        define("effect_on_attacker",
            (json, ctx) -> {
                String effectId = json.has("effect") ? json.get("effect").getAsString() : null;
                if (effectId == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] effect_on_attacker: missing effect id — no-op");
                    return EntityAction.noop();
                }
                final int duration = json.has("duration") ? json.get("duration").getAsInt() : 100;
                final int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;
                var effectHolder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(
                    net.minecraft.resources.Identifier.parse(effectId)).orElse(null);
                if (effectHolder == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] effect_on_attacker: unknown effect '{}' — no-op", effectId);
                    return EntityAction.noop();
                }
                final var fEffectHolder = effectHolder;
                return player -> {
                    Object actionCtx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
                    if (!(actionCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.HitTakenContext htc)) return;
                    var attacker = htc.source().getEntity();
                    if (!(attacker instanceof net.minecraft.world.entity.LivingEntity le)) return;
                    le.addEffect(new net.minecraft.world.effect.MobEffectInstance(fEffectHolder, duration, amplifier));
                };
            },
            List.of(
                new FieldSpec("effect", FormFieldSpec.Kind.STRING, true)
                    .doc("Mob effect id to apply to the attacker."),
                new FieldSpec("duration", FormFieldSpec.Kind.INTEGER, false).def(100).range(0.0, null)
                    .doc("Effect duration in ticks (default 100)."),
                new FieldSpec("amplifier", FormFieldSpec.Kind.INTEGER, false).def(0).range(0.0, null)
                    .doc("Effect amplifier level (default 0).")));

        // toggle — flip (or set, if `value` is given) the toggle state for a power
        // id. Lift-and-shift of parseToggle. `power` is the hard requirement; a
        // missing/blank id records an unsupported-action warning (via
        // ActionParser.failNoop, contextId "root" to match the original) and no-ops.
        define("toggle",
            (json, ctx) -> {
                String powerId = json.has("power") ? json.get("power").getAsString() : null;
                if (powerId == null || powerId.isBlank()) {
                    return ActionParser.failNoop("neoorigins:toggle", "root", "missing 'power' field");
                }
                final Boolean explicit = json.has("value") ? json.get("value").getAsBoolean() : null;
                final String key = powerId;
                return player -> {
                    if (explicit != null) com.cyberday1.neoorigins.compat.Toggles.setOn(player, key, explicit);
                    else com.cyberday1.neoorigins.compat.Toggles.flip(player, key);
                };
            },
            List.of(
                new FieldSpec("power", FormFieldSpec.Kind.STRING, true)
                    .doc("Power id whose toggle state to flip (or set)."),
                new FieldSpec("value", FormFieldSpec.Kind.BOOLEAN, false)
                    .doc("If present, sets the toggle to this value instead of flipping it.")));

        // add_to_set — add the current bientity target's UUID to a named entity-set
        // on the actor. Lift-and-shift of parseAddToSet. `set` is the hard
        // requirement; a missing/blank name records an unsupported-action warning
        // (via failNoop, using the dispatch contextId) and no-ops. Also no-ops when
        // no bientity context is active.
        define("add_to_set",
            (json, ctx) -> {
                String setName = json.has("set") ? json.get("set").getAsString() : null;
                if (setName == null || setName.isBlank()) {
                    return ActionParser.failNoop("neoorigins:add_to_set", ctx, "missing required field 'set'");
                }
                final String key = setName;
                return player -> {
                    var le = ActionParser.extractBientityTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
                    if (le == null) return;
                    var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                    data.addToEntitySet(player, key, le.getUUID());
                };
            },
            List.of(new FieldSpec("set", FormFieldSpec.Kind.STRING, true)
                .doc("Name of the actor's entity-set to add the bientity target's UUID to.")));

        // remove_from_set — remove the current bientity target's UUID from a named
        // entity-set on the actor. Lift-and-shift of parseRemoveFromSet. `set` is the
        // hard requirement; a missing/blank name records an unsupported-action
        // warning (via failNoop, using the dispatch contextId) and no-ops. Also
        // no-ops when no bientity context is active.
        define("remove_from_set",
            (json, ctx) -> {
                String setName = json.has("set") ? json.get("set").getAsString() : null;
                if (setName == null || setName.isBlank()) {
                    return ActionParser.failNoop("neoorigins:remove_from_set", ctx, "missing required field 'set'");
                }
                final String key = setName;
                return player -> {
                    var le = ActionParser.extractBientityTarget(com.cyberday1.neoorigins.service.ActionContextHolder.get());
                    if (le == null) return;
                    var data = player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                    data.removeFromEntitySet(player, key, le.getUUID());
                };
            },
            List.of(new FieldSpec("set", FormFieldSpec.Kind.STRING, true)
                .doc("Name of the actor's entity-set to remove the bientity target's UUID from.")));

        // and — run every action in `actions` in order. Lift-and-shift of parseAnd.
        // The inner actions are parsed once at load via ActionParser.parse with the
        // same dispatch contextId. `all_of` is the Apoli 2.9+ rename of the `and`
        // meta action (there is no any_of action) — alias, same factory.
        define("and", List.of("all_of"),
            (json, ctx) -> {
                com.google.gson.JsonArray arr = json.has("actions") ? json.getAsJsonArray("actions") : new com.google.gson.JsonArray();
                List<EntityAction> actions = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement el : arr) {
                    if (el.isJsonObject()) actions.add(ActionParser.parse(el.getAsJsonObject(), ctx));
                }
                return player -> { for (EntityAction a : actions) a.execute(player); };
            },
            List.of(new FieldSpec("actions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef("#")
                .doc("List of entity actions to run in order.")));

        // if_else — run `if_action` when `condition` passes, else `else_action`.
        // Lift-and-shift of parseIfElse. A missing/invalid condition is fail-closed
        // (CompatPolicy.FALSE_CONDITION); missing branches default to no-op.
        define("if_else",
            (json, ctx) -> {
                EntityCondition cond = json.has("condition") && json.get("condition").isJsonObject()
                    ? ConditionParser.parse(json.getAsJsonObject("condition"), ctx)
                    : com.cyberday1.neoorigins.compat.CompatPolicy.FALSE_CONDITION;
                // Branches may each be a single object or an array (run sequentially).
                EntityAction ifAction = ActionParser.parseField(json, "if_action", ctx);
                EntityAction elseAction = ActionParser.parseField(json, "else_action", ctx);
                return player -> {
                    if (cond.test(player)) ifAction.execute(player);
                    else elseAction.execute(player);
                };
            },
            List.of(
                new FieldSpec("condition", FormFieldSpec.Kind.REF, false)
                    .ref("condition.schema.json")
                    .doc("Entity condition deciding which branch runs (fail-closed when absent)."),
                new FieldSpec("if_action", FormFieldSpec.Kind.REF, false)
                    .ref("#")
                    .doc("Action run when the condition passes."),
                new FieldSpec("else_action", FormFieldSpec.Kind.REF, false)
                    .ref("#")
                    .doc("Action run when the condition fails.")));

        // if_else_list — run the action of the first branch whose condition passes.
        // Lift-and-shift of parseIfElseList. Each branch's missing/invalid condition
        // is fail-closed; missing actions default to no-op.
        define("if_else_list",
            (json, ctx) -> {
                com.google.gson.JsonArray arr = json.has("actions") ? json.getAsJsonArray("actions") : new com.google.gson.JsonArray();
                record Branch(EntityCondition cond, EntityAction action) {}
                List<Branch> branches = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject obj = el.getAsJsonObject();
                    EntityCondition cond = obj.has("condition") && obj.get("condition").isJsonObject()
                        ? ConditionParser.parse(obj.getAsJsonObject("condition"), ctx)
                        : com.cyberday1.neoorigins.compat.CompatPolicy.FALSE_CONDITION;
                    EntityAction act = ActionParser.parseField(obj, "action", ctx);
                    branches.add(new Branch(cond, act));
                }
                return player -> {
                    for (var branch : branches) {
                        if (branch.cond().test(player)) {
                            branch.action().execute(player);
                            return;
                        }
                    }
                };
            },
            List.of(new FieldSpec("actions", FormFieldSpec.Kind.ARRAY, false)
                .doc("List of {condition, action} branches; the first passing branch runs.")));

        // chance — run `action` with probability `chance`. Lift-and-shift of
        // parseChance. `chance` optional (parser default 0.5); missing action no-ops.
        define("chance",
            (json, ctx) -> {
                float chance = json.has("chance") ? json.get("chance").getAsFloat() : 0.5f;
                EntityAction action = ActionParser.parseField(json, "action", ctx);
                return player -> {
                    if (player.getRandom().nextFloat() < chance) action.execute(player);
                };
            },
            List.of(
                new FieldSpec("chance", FormFieldSpec.Kind.NUMBER, false).def(0.5).range(0.0, 1.0)
                    .doc("Probability in [0,1] that the action runs (default 0.5)."),
                new FieldSpec("action", FormFieldSpec.Kind.REF, false)
                    .ref("#")
                    .doc("Action run when the chance roll succeeds.")));

        // delay — schedule `action` to run `ticks` server-ticks later via the compat
        // tick scheduler. Lift-and-shift of parseDelay. `ticks` optional (parser
        // default 1); only schedules when a server is present.
        define("delay",
            (json, ctx) -> {
                int ticks = json.has("ticks") ? json.get("ticks").getAsInt() : 1;
                EntityAction action = ActionParser.parseField(json, "action", ctx);
                return player -> {
                    if (player.level().getServer() != null) {
                        long target = player.level().getServer().getTickCount() + ticks;
                        com.cyberday1.neoorigins.compat.CompatTickScheduler.schedule(target, player, action::execute);
                    }
                };
            },
            List.of(
                new FieldSpec("ticks", FormFieldSpec.Kind.INTEGER, false).def(1).range(0.0, null)
                    .doc("Server ticks to wait before running the action (default 1)."),
                new FieldSpec("action", FormFieldSpec.Kind.REF, false)
                    .ref("#")
                    .doc("Action run after the delay elapses.")));

        // choice — randomly run one action from `actions`, weighted by each entry's
        // `weight`. Lift-and-shift of parseChoice. Missing/empty list no-ops; each
        // entry's `weight` optional (default 1).
        define("choice",
            (json, ctx) -> {
                if (!json.has("actions") || !json.get("actions").isJsonArray()) return EntityAction.noop();
                com.google.gson.JsonArray arr = json.getAsJsonArray("actions");
                List<EntityAction> actions = new java.util.ArrayList<>();
                List<Integer> weights = new java.util.ArrayList<>();
                int totalWeight = 0;
                for (com.google.gson.JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject entry = el.getAsJsonObject();
                    EntityAction action = ActionParser.parseField(entry, "action", ctx);
                    int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                    actions.add(action);
                    weights.add(weight);
                    totalWeight += weight;
                }
                if (actions.isEmpty()) return EntityAction.noop();
                final int fTotal = totalWeight;
                final List<EntityAction> fActions = List.copyOf(actions);
                final List<Integer> fWeights = List.copyOf(weights);
                return player -> {
                    int roll = player.getRandom().nextInt(fTotal);
                    int cumulative = 0;
                    for (int i = 0; i < fActions.size(); i++) {
                        cumulative += fWeights.get(i);
                        if (roll < cumulative) { fActions.get(i).execute(player); return; }
                    }
                    fActions.getLast().execute(player);
                };
            },
            List.of(new FieldSpec("actions", FormFieldSpec.Kind.ARRAY, false)
                .doc("List of {action, weight} entries; one is picked weighted-randomly.")));

        // target_action — dual-actor retarget. Resolves the "entity on the other side
        // of the interaction" from the active dispatch context via
        // ActionParser.extractBientityTarget (ProjectileHitContext / HitTakenContext /
        // KillContext / EntityInteractContext). The inner `action` then runs against
        // that resolved target rather than the holder:
        //   • player target  → full player-typed EntityAction (with the actor published
        //     to context so sub-actions resolve the actor side);
        //   • non-player mob  → TargetAction subset (entity-general verbs only);
        //   • no target       → no-op.
        // The actor (the holder running the power) remains the EntityAction `player`
        // arg. This is the dual-actor seam: actor_action stays a holder pass-through.
        define("target_action",
            (json, ctx) -> {
                if (!json.has("action") || !json.get("action").isJsonObject()) {
                    return EntityAction.noop();
                }
                com.google.gson.JsonObject inner = json.getAsJsonObject("action");
                EntityAction playerAction = ActionParser.parse(inner, ctx);
                TargetAction mobAction = TargetActionParser.parse(inner, ctx);
                return actor -> {
                    Object holderCtx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
                    net.minecraft.world.entity.LivingEntity target =
                        ActionParser.extractBientityTarget(holderCtx);
                    if (target == null) {
                        // A selector_action over non-living entities (e.g. area_effect_cloud)
                        // publishes a SourceEntityContext rather than a LivingEntity target.
                        // There's nothing to "retarget" onto a non-living entity, but the
                        // inner action still needs to run so a nested fire_projectile reads
                        // that context and fires FROM the selected entity. Pass through.
                        if (holderCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.SourceEntityContext) {
                            playerAction.execute(actor);
                        }
                        return; // otherwise no resolvable target — no-op
                    }
                    if (target instanceof net.minecraft.server.level.ServerPlayer tp) {
                        // Player target: run the full EntityAction on it, with the actor
                        // published so any nested actor-facing sub-action resolves.
                        Object prev = com.cyberday1.neoorigins.service.ActionContextHolder.set(
                            new com.cyberday1.neoorigins.service.EventPowerIndex.EntityInteractContext(actor));
                        try { playerAction.execute(tp); }
                        finally { com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev); }
                    } else if (mobAction != null) {
                        // Non-player mob target: run the entity-general TargetAction subset.
                        mobAction.execute(target, actor);
                    }
                    // Non-player target + non-generalizable verb — skip.
                };
            },
            List.of(new FieldSpec("action", FormFieldSpec.Kind.REF, false)
                .ref("#")
                .doc("Inner action to run on the resolved context target (player → full action; mob → entity-general subset).")));

        // selector_action — resolve a vanilla entity selector relative to the holder's
        // command source and run `bientity_action` once per resolved entity, publishing
        // each as a SourceEntityContext so a nested fire_projectile (directly, or via a
        // target_action wrapper) fires FROM that entity using its position + rotation.
        // This is the seam the Toxophilite "hyper_multishot" pack relies on: it summons
        // rotated area_effect_clouds and fires arrow volleys out of each. sort / limit /
        // tag predicates inside the selector string are honoured by vanilla's parser.
        define("selector_action",
            (json, ctx) -> {
                final String selector = json.has("selector") ? json.get("selector").getAsString() : null;
                if (selector == null || selector.isBlank()) {
                    NeoOrigins.LOGGER.warn("[CompatB] selector_action: missing 'selector' — no-op");
                    return EntityAction.noop();
                }
                // bientity_action / entity_action may each be a single object or
                // an array (run sequentially per selected entity).
                final EntityAction inner =
                    json.has("bientity_action")
                        ? ActionParser.parseField(json, "bientity_action", ctx)
                    : json.has("entity_action")
                        ? ActionParser.parseField(json, "entity_action", ctx)
                        : null;
                if (inner == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] selector_action: missing bientity_action/entity_action — no-op");
                    return EntityAction.noop();
                }
                return player -> {
                    if (player.level().getServer() == null) return;
                    java.util.List<? extends net.minecraft.world.entity.Entity> selected;
                    try {
                        var reader = new com.mojang.brigadier.StringReader(selector);
                        var parser = new net.minecraft.commands.arguments.selector.EntitySelectorParser(reader, true);
                        net.minecraft.commands.arguments.selector.EntitySelector sel = parser.parse();
                        var src = player.createCommandSourceStack()
                            .withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER)
                            .withSuppressedOutput();
                        selected = sel.findEntities(src);
                    } catch (Exception e) {
                        NeoOrigins.LOGGER.warn("[CompatB] selector_action: selector '{}' failed: {}", selector, e.getMessage());
                        return;
                    }
                    for (net.minecraft.world.entity.Entity e : selected) {
                        Object prev = com.cyberday1.neoorigins.service.ActionContextHolder.set(
                            new com.cyberday1.neoorigins.service.EventPowerIndex.SourceEntityContext(e));
                        try {
                            inner.execute(player);
                        } finally {
                            com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev);
                        }
                    }
                };
            },
            List.of(
                new FieldSpec("selector", FormFieldSpec.Kind.STRING, false)
                    .doc("Vanilla entity selector (e.g. @e[type=area_effect_cloud,tag=foo,limit=2,sort=nearest]) resolved relative to the holder."),
                new FieldSpec("bientity_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Action run once per selected entity; the entity is published as the action's source (origin + rotation)."),
                new FieldSpec("entity_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Alias for bientity_action.")));

        // actor_action — bientity unwrapper that runs the inner `action` on the actor.
        // Lift-and-shift of parseActorAction (delegates to the inner action).
        define("actor_action",
            (json, ctx) -> ActionParser.parseField(json, "action", ctx),
            List.of(new FieldSpec("action", FormFieldSpec.Kind.REF, false)
                .ref("#")
                .doc("Inner action to run on the actor (source player).")));

        // passenger_action — run the inner action on every ServerPlayer passenger.
        // Lift-and-shift of parsePassengerAction. Reads `action`, falling back to the
        // `entity_action` alias only when `action` resolved to the shared noop.
        define("passenger_action",
            (json, ctx) -> {
                // action / entity_action may each be a single object or an array
                // (run sequentially on each passenger).
                EntityAction inner = ActionParser.parseField(json, "action", ctx);
                if (inner == EntityAction.noop() && json.has("entity_action")) {
                    inner = ActionParser.parseField(json, "entity_action", ctx);
                }
                final EntityAction fInner = inner;
                return player -> {
                    for (var passenger : player.getPassengers()) {
                        if (passenger instanceof net.minecraft.server.level.ServerPlayer sp) {
                            fInner.execute(sp);
                        }
                    }
                };
            },
            List.of(
                new FieldSpec("action", FormFieldSpec.Kind.REF, false)
                    .ref("#")
                    .doc("Action run on each passenger (or use the `entity_action` alias)."),
                new FieldSpec("entity_action", FormFieldSpec.Kind.REF, false)
                    .ref("#")
                    .doc("Alias for action.")));

        // riding_action — run an entity-action on the entity the holder is riding.
        // Mirror of passenger_action (inverse direction). NeoOrigins entity-actions
        // are ServerPlayer-typed, so this fires only when the vehicle is itself a
        // player; non-player vehicles (boat/horse) can't receive a player-action.
        define("riding_action",
            (json, ctx) -> {
                // action / entity_action may each be a single object or an array
                // (run sequentially on the ridden entity).
                EntityAction inner = ActionParser.parseField(json, "action", ctx);
                if (inner == EntityAction.noop() && json.has("entity_action")) {
                    inner = ActionParser.parseField(json, "entity_action", ctx);
                }
                final EntityAction fInner = inner;
                return player -> {
                    if (player.getVehicle() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        fInner.execute(sp);
                    }
                };
            },
            List.of(
                new FieldSpec("action", FormFieldSpec.Kind.REF, false)
                    .ref("#")
                    .doc("Action run on the entity the holder is riding (or use the `entity_action` alias)."),
                new FieldSpec("entity_action", FormFieldSpec.Kind.REF, false)
                    .ref("#")
                    .doc("Alias for action.")));

        // spawn_particles — broadcast particles from the player's position. Server-side
        // sendParticles, so all nearby clients see them. Simple (data-less) particle
        // types only — data-bearing particles (dust/block/item) aren't reachable from a
        // plain id and no-op with a warning. `force` is accepted but always-on here
        // (the server unconditionally broadcasts).
        define("spawn_particles",
            (json, ctx) -> {
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
                var pid = net.minecraft.resources.Identifier.tryParse(particleId);
                if (pid != null) {
                    var ptypeOpt = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(pid);
                    if (ptypeOpt.isPresent()
                            && ptypeOpt.get().value() instanceof net.minecraft.core.particles.ParticleOptions po) {
                        options = po;
                    }
                }
                if (options == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] spawn_particles: unsupported/unknown particle '{}' — no-op", particleId);
                    return EntityAction.noop();
                }
                final net.minecraft.core.particles.ParticleOptions fOpts = options;
                final double fsx = sx, fsy = sy, fsz = sz, foffY = offsetY, fspeed = speed;
                final int fcount = count;
                return player -> {
                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel lvl)) return;
                    lvl.sendParticles(fOpts, player.getX(), player.getY() + foffY, player.getZ(),
                        fcount, fsx, fsy, fsz, fspeed);
                };
            },
            List.of(
                new FieldSpec("particle", FormFieldSpec.Kind.STRING, false).def("minecraft:poof")
                    .doc("Particle id to spawn (simple/data-less particles only; dust/block/item no-op)."),
                new FieldSpec("count", FormFieldSpec.Kind.INTEGER, false).def(1).range(0.0, null)
                    .doc("Number of particles (default 1)."),
                new FieldSpec("speed", FormFieldSpec.Kind.NUMBER, false).def(0.0).range(0.0, null)
                    .doc("Particle speed / extra-data scalar (default 0)."),
                new FieldSpec("force", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                    .doc("Apoli force-render flag (accepted; the server always broadcasts)."),
                new FieldSpec("offset_y", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Vertical offset from the player's feet (default 0)."),
                new FieldSpec("spread", FormFieldSpec.Kind.OBJECT, false)
                    .doc("{x,y,z} per-axis gaussian spread radius (default 0).")));

        // drop_inventory — drop the holder's vanilla inventory as item entities.
        // The optional `item_condition` filters which stacks drop (Apoli semantics) —
        // e.g. a "drop totems" power passes an item_condition matching totems of
        // undying, so only those leave the inventory. Without a filter, all stacks
        // drop (Apoli treats an absent condition as "everything").
        // The optional `slots` selector restricts the scan to the named Apoli/vanilla
        // slots (e.g. "weapon.mainhand", "weapon.offhand", "armor.head"); absent or
        // empty means "every slot" (Apoli's default). Deano's totem_restrict power
        // relies on this to drop totems from the hands only, not the whole inventory.
        // Apoli "power" inventories (virtual per-power containers) aren't modelled.
        define("drop_inventory",
            (json, ctx) -> {
                String invType = json.has("inventory_type") ? json.get("inventory_type").getAsString() : "inventory";
                boolean throwRandomly = !json.has("throw_randomly") || json.get("throw_randomly").getAsBoolean();
                boolean retainOwnership = json.has("retain_ownership") && json.get("retain_ownership").getAsBoolean();
                if (!"inventory".equalsIgnoreCase(invType)) {
                    NeoOrigins.LOGGER.warn("[CompatB] drop_inventory: inventory_type '{}' unsupported (only 'inventory') — no-op", invType);
                    return EntityAction.noop();
                }
                var itemCond = json.has("item_condition") && json.get("item_condition").isJsonObject()
                    ? com.cyberday1.neoorigins.compat.condition.ItemConditionParser.parse(json.getAsJsonObject("item_condition"))
                    : com.cyberday1.neoorigins.compat.condition.ItemCondition.alwaysTrue();
                // null = no selector → scan the whole inventory; otherwise the resolved
                // command-slot ids (possibly empty if every listed name was unknown).
                final int[] slotIds = parseDropInventorySlots(json);
                return player -> {
                    boolean dropped = false;
                    if (slotIds == null) {
                        var inv = player.getInventory();
                        for (int i = 0; i < inv.getContainerSize(); i++) {
                            net.minecraft.world.item.ItemStack s = inv.getItem(i);
                            if (s.isEmpty() || !itemCond.test(s)) continue;
                            player.drop(s, throwRandomly, retainOwnership);
                            inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                            dropped = true;
                        }
                    } else {
                        for (int id : slotIds) {
                            var acc = player.getSlot(id);
                            net.minecraft.world.item.ItemStack s = acc.get();
                            if (s.isEmpty() || !itemCond.test(s)) continue;
                            player.drop(s, throwRandomly, retainOwnership);
                            acc.set(net.minecraft.world.item.ItemStack.EMPTY);
                            dropped = true;
                        }
                    }
                    if (dropped) player.containerMenu.broadcastChanges();
                };
            },
            List.of(
                new FieldSpec("inventory_type", FormFieldSpec.Kind.STRING, false).def("inventory")
                    .doc("Only 'inventory' (the vanilla player inventory) is supported; 'power' inventories no-op."),
                new FieldSpec("item_condition", FormFieldSpec.Kind.REF, false)
                    .ref("item_condition.schema.json")
                    .doc("Only drop stacks matching this item_condition. Omit to drop everything."),
                new FieldSpec("slots", FormFieldSpec.Kind.ARRAY, false)
                    .itemPattern("^[a-z0-9_.]+$")
                    .doc("Restrict to these vanilla/Apoli slot names (e.g. weapon.mainhand, weapon.offhand, armor.head). Omit or leave empty to scan every slot."),
                new FieldSpec("throw_randomly", FormFieldSpec.Kind.BOOLEAN, false).def(true)
                    .doc("Scatter the dropped items (default true)."),
                new FieldSpec("retain_ownership", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                    .doc("Tag drops with the thrower for pickup priority (default false).")));

        // offset — Apoli-architectural offset wrapper. Lift-and-shift of parseOffset:
        // our actions already read their position from the dispatch context, so the
        // x/y/z offset is a no-op and the inner `action` is returned as-is. The x/y/z
        // fields are kept on the descriptor for schema/editor fidelity.
        define("offset",
            (json, ctx) -> ActionParser.parseField(json, "action", ctx),
            List.of(
                new FieldSpec("action", FormFieldSpec.Kind.REF, false)
                    .ref("#")
                    .doc("Inner action to run (offset is architectural; position comes from context)."),
                new FieldSpec("x", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("X offset (accepted for Apoli parity; no positional effect here)."),
                new FieldSpec("y", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Y offset (accepted for Apoli parity; no positional effect here)."),
                new FieldSpec("z", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Z offset (accepted for Apoli parity; no positional effect here).")));

        // block_action_at — run `block_action` at the entity's current block position,
        // publishing that BlockPos to ActionContextHolder so nested verbs (e.g.
        // execute_command) resolve `~ ~ ~` to the block centre. Lift-and-shift of
        // parseBlockActionAt.
        define("block_action_at",
            (json, ctx) -> {
                EntityAction blockAction = ActionParser.parseField(json, "block_action", ctx);
                return player -> {
                    net.minecraft.core.BlockPos pos = player.blockPosition();
                    Object prev = com.cyberday1.neoorigins.service.ActionContextHolder.set(
                        new ActionParser.RaycastBlockContext(pos));
                    try {
                        blockAction.execute(player);
                    } finally {
                        com.cyberday1.neoorigins.service.ActionContextHolder.restore(prev);
                    }
                };
            },
            List.of(new FieldSpec("block_action", FormFieldSpec.Kind.REF, false).ref("#")
                .doc("Action run at the entity's block position (BlockPos published to context).")));

        // swap_with_entity — swap positions (and look) with the nearest matching
        // living entity within `radius`. Lift-and-shift of parseSwapWithEntity.
        // `radius` optional (parser default 16); `target_condition` optional (parser
        // default always-true) and only applied to ServerPlayer candidates.
        // (Source commit c960b6cd also migrated kubejs_callback; master has no such
        // verb, so it is omitted here.)
        define("swap_with_entity",
            (json, ctx) -> {
                final float radius = json.has("radius") ? json.get("radius").getAsFloat() : 16f;
                EntityCondition tgtCond = json.has("target_condition")
                    ? ConditionParser.parse(json.getAsJsonObject("target_condition"), ctx)
                    : EntityCondition.alwaysTrue();
                final EntityCondition fCond = tgtCond;
                return player -> {
                    var level = player.level();
                    var aabb = player.getBoundingBox().inflate(radius);
                    var candidates = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb,
                        e -> e != player && e.isAlive());
                    net.minecraft.world.entity.LivingEntity best = null;
                    double bestDist = Double.MAX_VALUE;
                    var origin = player.position();
                    for (var e : candidates) {
                        if (e instanceof net.minecraft.server.level.ServerPlayer sp && !fCond.test(sp)) continue;
                        double d = e.position().distanceToSqr(origin);
                        if (d < bestDist) { bestDist = d; best = e; }
                    }
                    if (best == null) return;
                    double px = player.getX(), py = player.getY(), pz = player.getZ();
                    float pyaw = player.getYRot(), ppitch = player.getXRot();
                    player.teleportTo(best.getX(), best.getY(), best.getZ());
                    player.setYRot(best.getYRot());
                    player.setXRot(best.getXRot());
                    best.teleportTo(px, py, pz);
                    best.setYRot(pyaw);
                    best.setXRot(ppitch);
                };
            },
            List.of(
                new FieldSpec("radius", FormFieldSpec.Kind.NUMBER, false).def(16.0).range(0.0, null)
                    .doc("Search radius for swap candidates (default 16)."),
                new FieldSpec("target_condition", FormFieldSpec.Kind.REF, false)
                    .ref("condition.schema.json")
                    .doc("Optional entity condition; applied to player candidates (default always-true).")));

        // swap_positions — dual-actor: atomically swap the actor and the resolved
        // context target's full transform (x,y,z,yaw,pitch). Unlike swap_with_entity
        // (a radius search around the holder), this uses the entity on the other side
        // of the interaction (ActionParser.extractBientityTarget) — e.g. the mob a
        // projectile hit across the room. Snapshot-first (the swap_with_entity
        // pattern): naïve sequential teleports collapse both entities to one point.
        // No-op if no target resolves. No config fields.
        define("swap_positions",
            (json, ctx) -> actor -> {
                net.minecraft.world.entity.LivingEntity target =
                    ActionParser.extractBientityTarget(
                        com.cyberday1.neoorigins.service.ActionContextHolder.get());
                if (target == null) return;
                // Snapshot BOTH transforms before moving either one.
                double ax = actor.getX(), ay = actor.getY(), az = actor.getZ();
                float ayaw = actor.getYRot(), apitch = actor.getXRot();
                double tx = target.getX(), ty = target.getY(), tz = target.getZ();
                float tyaw = target.getYRot(), tpitch = target.getXRot();
                actor.teleportTo(tx, ty, tz);
                actor.setYRot(tyaw);
                actor.setXRot(tpitch);
                target.teleportTo(ax, ay, az);
                target.setYRot(ayaw);
                target.setXRot(apitch);
            },
            List.of());

        // teleport_to_target — dual-actor: move the actor to the resolved context
        // target's position (and look). No-op if no target resolves. No config fields.
        define("teleport_to_target",
            (json, ctx) -> actor -> {
                net.minecraft.world.entity.LivingEntity target =
                    ActionParser.extractBientityTarget(
                        com.cyberday1.neoorigins.service.ActionContextHolder.get());
                if (target == null) return;
                actor.teleportTo(target.getX(), target.getY(), target.getZ());
                actor.setYRot(target.getYRot());
                actor.setXRot(target.getXRot());
            },
            List.of());

        // teleport_target_to_self — dual-actor: move the resolved context target to
        // the actor's position (and look). No-op if no target resolves. No config fields.
        define("teleport_target_to_self",
            (json, ctx) -> actor -> {
                net.minecraft.world.entity.LivingEntity target =
                    ActionParser.extractBientityTarget(
                        com.cyberday1.neoorigins.service.ActionContextHolder.get());
                if (target == null) return;
                target.teleportTo(actor.getX(), actor.getY(), actor.getZ());
                target.setYRot(actor.getYRot());
                target.setXRot(actor.getXRot());
            },
            List.of());

        // ── Item 4: entity-target spells (dual-actor) ───────────────────────────
        // Each resolves the "entity on the other side of the interaction" from the
        // active dispatch context (ActionParser.extractBientityTarget). They're
        // registered BOTH as context-resolving EntityActions here (so an author can
        // write {"type":"neoorigins:shear"} directly as an on_hit_action / target
        // action and have it hit the projectile's victim — the same shape as
        // swap_positions / teleport_to_target above) AND in TargetActionParser (so a
        // target_action wrapper reaches them on an arbitrary mob target). All no-op
        // cleanly when the target is null or not applicable.

        // shear — shear the resolved target the way vanilla/modded shears would,
        // via the NeoForge IShearable seam (sheep drop wool + set sheared, mooshroom
        // → cow + mushrooms, snow golem → drop pumpkin, bogged, modded shearables).
        // No-op when there's no target or the target isn't shearable (e.g. an already-
        // sheared sheep, or a non-shearable mob). No config fields.
        define("shear",
            (json, ctx) -> actor -> {
                net.minecraft.world.entity.LivingEntity target =
                    ActionParser.extractBientityTarget(
                        com.cyberday1.neoorigins.service.ActionContextHolder.get());
                shearTarget(target, actor);
            },
            List.of());

        // dye — set the colour of a dyeable resolved target. Field: `color` (a dye
        // colour name, e.g. "red"). Currently dyes a Sheep's wool colour; other mobs
        // (wolf/cat collars) expose no public colour setter on 26.1, so they no-op.
        // No-op when there's no target, the target is non-dyeable, or `color` is an
        // unknown dye name.
        define("dye",
            (json, ctx) -> {
                String colorName = json.has("color") ? json.get("color").getAsString() : null;
                net.minecraft.world.item.DyeColor color =
                    colorName == null ? null : net.minecraft.world.item.DyeColor.byName(colorName, null);
                if (colorName != null && color == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] dye: unknown dye colour '{}' — action will no-op", colorName);
                }
                final net.minecraft.world.item.DyeColor fColor = color;
                return actor -> {
                    if (fColor == null) return;
                    net.minecraft.world.entity.LivingEntity target =
                        ActionParser.extractBientityTarget(
                            com.cyberday1.neoorigins.service.ActionContextHolder.get());
                    dyeTarget(target, fColor);
                };
            },
            List.of(new FieldSpec("color", FormFieldSpec.Kind.STRING, false)
                .doc("Dye colour name to apply to the target (e.g. \"red\"). Dyes a sheep's wool; non-dyeable targets no-op.")));

        // force_drop — make the resolved target drop the item in a named equipment
        // slot as an item entity and clear the slot. Field: `slot`
        // (mainhand/offhand/head/chest/legs/feet; default mainhand). No-op when
        // there's no target or the slot is empty.
        define("force_drop",
            (json, ctx) -> {
                final net.minecraft.world.entity.EquipmentSlot slot = parseEquipmentSlotOrMain(
                    json.has("slot") ? json.get("slot").getAsString() : "mainhand", "force_drop");
                return actor -> {
                    net.minecraft.world.entity.LivingEntity target =
                        ActionParser.extractBientityTarget(
                            com.cyberday1.neoorigins.service.ActionContextHolder.get());
                    if (target == null) return;
                    // 26.1 delta: Entity.spawnAtLocation gained a leading ServerLevel arg.
                    if (!(target.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
                    net.minecraft.world.item.ItemStack stack = target.getItemBySlot(slot);
                    if (stack.isEmpty()) return;
                    target.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    target.spawnAtLocation(sl, stack);
                };
            },
            List.of(new FieldSpec("slot", FormFieldSpec.Kind.STRING, false).def("mainhand")
                .doc("Equipment slot to drop from: mainhand/offhand/head/chest/legs/feet (default mainhand).")));

        // steal_item — like force_drop, but transfer the dropped stack to the ACTOR
        // (the power holder): added to the actor's inventory, or dropped at the actor
        // if the inventory is full. Field: `slot` (default mainhand). No-op when
        // there's no target or the slot is empty.
        define("steal_item",
            (json, ctx) -> {
                final net.minecraft.world.entity.EquipmentSlot slot = parseEquipmentSlotOrMain(
                    json.has("slot") ? json.get("slot").getAsString() : "mainhand", "steal_item");
                return actor -> {
                    net.minecraft.world.entity.LivingEntity target =
                        ActionParser.extractBientityTarget(
                            com.cyberday1.neoorigins.service.ActionContextHolder.get());
                    if (target == null) return;
                    net.minecraft.world.item.ItemStack stack = target.getItemBySlot(slot);
                    if (stack.isEmpty()) return;
                    target.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    if (!actor.getInventory().add(stack)) {
                        actor.drop(stack, false);
                    }
                };
            },
            List.of(new FieldSpec("slot", FormFieldSpec.Kind.STRING, false).def("mainhand")
                .doc("Equipment slot to steal from: mainhand/offhand/head/chest/legs/feet (default mainhand). Item goes to the actor's inventory, or drops at the actor if full.")));

        // ── Item 5: block-target spells (block-target seam) ─────────────────────
        // Each resolves the impacted BLOCK on the other side of the interaction from
        // the active dispatch context (ActionParser.extractBlockTarget — reads the
        // BlockHitContext / a ProjectileHitContext with a block ray-trace result /
        // the raycast RaycastBlockContext). They're registered BOTH as context-
        // resolving EntityActions here (so an author can write
        // {"type":"neoorigins:strip"} directly as a projectile on_hit_action /
        // raycast block_action and have it hit the impacted block — same shape as the
        // item-4 entity spells above) AND in BlockTargetActionParser (so a
        // block_target_action wrapper reaches them). All no-op cleanly when no block
        // resolves or the block isn't applicable. The implementations live in the
        // applyStrip/applyTill/applyPath/applyGrow/applyTransformBlock helpers so
        // both registration paths share one body.

        // strip — log/wood → stripped (vanilla AxeItem.STRIPPABLES), preserving axis.
        // No fields. No-op when the block has no strip mapping.
        define("strip",
            (json, ctx) -> blockTargetEntityAction(BuiltinActions::applyStrip),
            List.of());

        // till — dirt/grass/coarse-dirt/rooted-dirt → farmland (coarse → dirt),
        // only when the block above is air (vanilla hoe rule). No fields.
        define("till",
            (json, ctx) -> blockTargetEntityAction(BuiltinActions::applyTill),
            List.of());

        // path — grass/dirt/podzol/mycelium → dirt path, only when the block above
        // is air (vanilla shovel rule). No fields.
        define("path",
            (json, ctx) -> blockTargetEntityAction(BuiltinActions::applyPath),
            List.of());

        // grow — bonemeal-style growth on a BonemealableBlock (crops/saplings/grass).
        // No fields. No-op when the block isn't bonemealable / not growable now.
        define("grow",
            (json, ctx) -> blockTargetEntityAction(BuiltinActions::applyGrow),
            List.of());

        // transform_block — generic primitive: set the impacted block to `to`,
        // optionally gated on it currently being `from`. No-op when `to` is
        // missing/unknown or the `from` guard fails.
        define("transform_block",
            (json, ctx) -> {
                final net.minecraft.world.level.block.Block from =
                    resolveBlockOrNull(json.has("from") ? json.get("from").getAsString() : null, "transform_block.from");
                final net.minecraft.world.level.block.Block to =
                    resolveBlockOrNull(json.has("to") ? json.get("to").getAsString() : null, "transform_block.to");
                return blockTargetEntityAction((level, pos, actor) -> applyTransformBlock(level, pos, from, to));
            },
            List.of(
                new FieldSpec("from", FormFieldSpec.Kind.STRING, false)
                    .doc("Optional block id guard; only transform when the impacted block matches (e.g. \"minecraft:stone\")."),
                new FieldSpec("to", FormFieldSpec.Kind.STRING, true)
                    .doc("Block id to set the impacted block to (e.g. \"minecraft:gold_block\").")));

        // block_target_action — block-side analogue of target_action. Resolves the
        // impacted block from the active dispatch context and runs the inner block-
        // target verb against that (level, pos, actor). No-op when no block context
        // resolves or the inner verb isn't a block-target verb (parsed by
        // BlockTargetActionParser). The actor remains the EntityAction `player` arg.
        define("block_target_action",
            (json, ctx) -> {
                if (!json.has("action") || !json.get("action").isJsonObject()) {
                    return EntityAction.noop();
                }
                BlockTargetAction inner = BlockTargetActionParser.parse(json.getAsJsonObject("action"), ctx);
                if (inner == null) return EntityAction.noop();
                return blockTargetEntityAction(inner);
            },
            List.of(new FieldSpec("action", FormFieldSpec.Kind.REF, false)
                .ref("#")
                .doc("Inner block-target verb to run on the resolved context block (strip/till/path/grow/transform_block).")));

        // change_resource / modify_resource — add to or set a resource-bar value.
        // Lift-and-shift of parseChangeResource. `modify_resource` is an alias
        // (lifts the two-label `case "change_resource","modify_resource" ->` arm).
        // `resource` is required-ish but the parser falls back to a silent no-op
        // when absent (mirrors grant_power's contract), so it is modelled optional.
        // `add` (default) clamps within the bar's [min,max]; `set` assigns directly.
        define("change_resource", List.of("modify_resource"),
            (json, ctx) -> {
                String resourceId = json.has("resource") ? json.get("resource").getAsString() : null;
                if (resourceId == null) return EntityAction.noop();
                // Apoli-derivative packs (Origins++, Medieval Origins Revival, etc.)
                // ship origins:modify_resource as a nested {"modifier": {"operation":
                // ..., "amount": ...}} shape rather than the flat {"operation",
                // "change"} we author internally. Accept both layouts so aliased
                // dispatch works without per-pack JSON edits.
                String operation;
                int change;
                if (json.has("modifier") && json.get("modifier").isJsonObject()) {
                    com.google.gson.JsonObject mod = json.getAsJsonObject("modifier");
                    operation = mod.has("operation") ? mod.get("operation").getAsString() : "add";
                    change = mod.has("amount") ? mod.get("amount").getAsInt()
                          : mod.has("value")  ? mod.get("value").getAsInt()
                          : 0;
                } else {
                    operation = json.has("operation") ? json.get("operation").getAsString() : "add";
                    change = json.has("change") ? json.get("change").getAsInt() : 0;
                }
                // Apoli's operation vocabulary maps onto our two ops. add_base_early /
                // add_base / addition / add_value / multiply_* all reduce to additive
                // for an integer resource (multipliers don't make sense at the bar
                // level — Origins authors fold scaling into the amount). set_total /
                // set / set_value snap to absolute. Anything else falls through to
                // additive.
                operation = switch (operation) {
                    case "set_total", "set_value", "set" -> "set";
                    default -> "add";
                };
                final int fChange = change;
                final String key = resourceId;
                // Wildcard write semantics: apply to every key currently matching
                // the selector (`*` glob; see ResourceState.matchingKeys). If no
                // key matches yet, the action is a no-op — pack authors should
                // ensure the matching resource power has been granted (and thus
                // seeded) before the wildcard write fires.
                final boolean wildcard = com.cyberday1.neoorigins.compat.CompatAttachments.ResourceState.isWildcard(key);
                if ("set".equals(operation)) {
                    return player -> {
                        var state = player.getData(com.cyberday1.neoorigins.compat.CompatAttachments.resourceState());
                        if (wildcard) state.setAll(key, fChange);
                        else state.set(key, fChange);
                        com.cyberday1.neoorigins.compat.CompatAttachments.syncResourceValuesToClient(player);
                    };
                }
                return player -> {
                    var state = player.getData(com.cyberday1.neoorigins.compat.CompatAttachments.resourceState());
                    if (wildcard) {
                        for (String k : state.matchingKeys(key)) {
                            var meta = com.cyberday1.neoorigins.compat.CompatAttachments.getResourceMeta(k);
                            int lo = meta != null ? meta.min() : Integer.MIN_VALUE;
                            int hi = meta != null ? meta.max() : Integer.MAX_VALUE;
                            state.clampedAdd(k, fChange, lo, hi);
                        }
                    } else {
                        var meta = com.cyberday1.neoorigins.compat.CompatAttachments.getResourceMeta(key);
                        int lo = meta != null ? meta.min() : Integer.MIN_VALUE;
                        int hi = meta != null ? meta.max() : Integer.MAX_VALUE;
                        state.clampedAdd(key, fChange, lo, hi);
                    }
                    com.cyberday1.neoorigins.compat.CompatAttachments.syncResourceValuesToClient(player);
                };
            },
            List.of(
                new FieldSpec("resource", FormFieldSpec.Kind.STRING, false)
                    .doc("Resource power id (matches the bar's defining power). `*` globs match any stored resource key."),
                new FieldSpec("operation", FormFieldSpec.Kind.ENUM, false).def("add")
                    .options("add", "set")
                    .doc("add (default) clamps within bar bounds; set assigns directly. Apoli synonyms (add_base, set_total, ...) are accepted."),
                new FieldSpec("change", FormFieldSpec.Kind.INTEGER, false).def(0)
                    .doc("Amount to add or value to set (default 0). Apoli's nested `modifier` {operation, amount} shape is also accepted.")));

        // set_resource — assign a resource-bar value directly (no clamp). Lift-and-
        // shift of parseSetResource. `resource` required-ish but parser silently
        // no-ops when absent → modelled optional. Value comes from `value`, falling
        // back to `change`, default 0.
        define("set_resource",
            (json, ctx) -> {
                String resourceId = json.has("resource") ? json.get("resource").getAsString() : null;
                if (resourceId == null) return EntityAction.noop();
                int value = json.has("value") ? json.get("value").getAsInt()
                           : json.has("change") ? json.get("change").getAsInt() : 0;
                final String key = resourceId;
                // `*` glob selectors write every matching stored key (no-op when
                // nothing matches yet) — see ResourceState.matchingKeys.
                final boolean wildcard = com.cyberday1.neoorigins.compat.CompatAttachments.ResourceState.isWildcard(key);
                return player -> {
                    var state = player.getData(com.cyberday1.neoorigins.compat.CompatAttachments.resourceState());
                    if (wildcard) state.setAll(key, value);
                    else state.set(key, value);
                    com.cyberday1.neoorigins.compat.CompatAttachments.syncResourceValuesToClient(player);
                };
            },
            List.of(
                new FieldSpec("resource", FormFieldSpec.Kind.STRING, false)
                    .doc("Resource power id (matches the bar's defining power)."),
                new FieldSpec("value", FormFieldSpec.Kind.INTEGER, false).def(0)
                    .doc("Value to assign (falls back to `change`; default 0)."),
                new FieldSpec("change", FormFieldSpec.Kind.INTEGER, false)
                    .doc("Alias for value.")));

        // spawn_projectile / fire_projectile — spawn a projectile entity aimed
        // along the player's look. Lift-and-shift of parseSpawnProjectile.
        // `fire_projectile` is an alias (lifts the two-label switch arm). Accepts
        // Apoli synonyms: `projectile` (=entity_type), `divergence` (=inaccuracy),
        // `projectile_action` (=on_hit_action). entity_type/projectile is required-
        // ish but the parser warns + no-ops when absent → modelled optional.
        // (26.1: Identifier + BuiltInRegistries.ENTITY_TYPE.get(eid) -> Optional<Holder>
        // .get().value(); EntityType.create(sl, EntitySpawnReason.MOB_SUMMONED).)
        define("spawn_projectile", List.of("fire_projectile"),
            (json, ctx) -> {
                String entityId = json.has("entity_type") ? json.get("entity_type").getAsString()
                                : json.has("projectile") ? json.get("projectile").getAsString() : null;
                if (entityId == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] spawn_projectile: missing entity_type/projectile — no-op");
                    return EntityAction.noop();
                }
                net.minecraft.resources.Identifier eid = net.minecraft.resources.Identifier.parse(entityId);
                var entityTypeOpt = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(eid);
                if (entityTypeOpt.isEmpty()) {
                    NeoOrigins.LOGGER.warn("[CompatB] spawn_projectile: unknown entity '{}' — no-op", eid);
                    return EntityAction.noop();
                }
                final net.minecraft.world.entity.EntityType<?> entityType = entityTypeOpt.get().value();
                final float speed = json.has("speed") ? json.get("speed").getAsFloat() : 1.5f;
                final float inaccuracy = json.has("inaccuracy") ? json.get("inaccuracy").getAsFloat()
                    : json.has("divergence") ? json.get("divergence").getAsFloat() : 0f;
                final float verticalOffset = json.has("vertical_offset") ? json.get("vertical_offset").getAsFloat() : 0f;
                // on_hit_action: NeoOrigins extension — deferred until the
                // projectile impacts (registered in ProjectileActionRegistry).
                com.google.gson.JsonObject hitActionJson =
                    json.has("on_hit_action") && json.get("on_hit_action").isJsonObject()
                        ? json.getAsJsonObject("on_hit_action") : null;
                final EntityAction onHitAction = hitActionJson != null
                    ? ActionParser.parse(hitActionJson, ctx) : null;
                // projectile_action (Apoli): an entity_action applied IMMEDIATELY to
                // the spawned projectile (the projectile is the actor) — NOT on hit
                // and NOT on the shooter. Previously aliased to on_hit_action and run
                // against the firing player, which lit the player on fire instead of
                // the arrow. Kept as raw JSON; applied post-spawn via applyProjectileAction.
                final com.google.gson.JsonObject projectileActionJson =
                    json.has("projectile_action") && json.get("projectile_action").isJsonObject()
                        ? json.getAsJsonObject("projectile_action") : null;
                final String effectType = json.has("effect_type")
                    ? json.get("effect_type").getAsString() : null;
                // Data-driven visuals (2.1). COLOR_UNSET / SIZE_UNSET / -1 / null =
                // "not set in JSON" → the entity + renderer fall back to the
                // effect_type default, then the hardcoded default.
                final int orbColor = parseColor(json.get("orb_color"));
                final int glowColor = parseColor(json.get("glow_color"));
                final float size = json.has("size") ? json.get("size").getAsFloat()
                    : com.cyberday1.neoorigins.content.MagicOrbProjectile.SIZE_UNSET;
                final float glowSize = json.has("glow_size") ? json.get("glow_size").getAsFloat()
                    : com.cyberday1.neoorigins.content.MagicOrbProjectile.SIZE_UNSET;
                final int glowAlpha = json.has("glow_alpha") ? json.get("glow_alpha").getAsInt() : -1;
                final String shape = json.has("shape") ? json.get("shape").getAsString() : null;
                final String trailParticle = json.has("trail_particle") ? json.get("trail_particle").getAsString() : null;
                final int trailCount = json.has("count") ? json.get("count").getAsInt() : 2;
                final float trailSpread = json.has("spread") ? json.get("spread").getAsFloat() : 0.05f;
                final float trailSpeed = json.has("speed_particle") ? json.get("speed_particle").getAsFloat()
                    : json.has("trail_speed") ? json.get("trail_speed").getAsFloat() : 0.0f;
                final boolean noGravity = json.has("no_gravity") && json.get("no_gravity").getAsBoolean();
                // Apoli `count`: number of projectiles to spawn in one fire (shotgun
                // spread when combined with divergence). Only applies to plain
                // projectiles — for MagicOrb the `count` key keeps its trail-particle
                // meaning (one orb, N trail particles), so spell visuals are unchanged.
                final int rawCount = json.has("count") ? json.get("count").getAsInt() : 1;
                // Apoli `tag`: SNBT merged onto each spawned projectile (e.g.
                // "{pickup:1b}" so fired arrows can be picked back up).
                final String nbtTag = json.has("tag") ? json.get("tag").getAsString() : null;
                return player -> {
                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
                    // Spatial origin: selector_action may publish a SourceEntityContext
                    // (e.g. an area_effect_cloud) that the projectile should fire FROM,
                    // using that entity's position + rotation. Owner stays the player so
                    // kill credit / arrow pickup ownership resolve to the caster.
                    Object fpCtx = com.cyberday1.neoorigins.service.ActionContextHolder.get();
                    final net.minecraft.world.entity.Entity origin =
                        fpCtx instanceof com.cyberday1.neoorigins.service.EventPowerIndex.SourceEntityContext sec
                            ? sec.sourceEntity() : player;
                    double ox = origin.getX();
                    double oy = (origin == player ? player.getEyeY() : origin.getY()) + verticalOffset;
                    double oz = origin.getZ();
                    float xRot = origin.getXRot();
                    float yRot = origin.getYRot();
                    var probe = entityType.create(sl, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                    if (probe == null) return;
                    boolean isOrb = probe instanceof com.cyberday1.neoorigins.content.MagicOrbProjectile;
                    int shots = isOrb ? 1 : Math.max(1, rawCount);
                    for (int i = 0; i < shots; i++) {
                        var entity = (i == 0) ? probe : entityType.create(sl, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                        if (entity == null) continue;
                        entity.setPos(ox, oy, oz);
                        // no_gravity: vanilla Entity flag — getGravity() returns 0 when set, so the
                        // projectile flies straight along its launch vector (drag still applies).
                        if (noGravity) entity.setNoGravity(true);
                        if (nbtTag != null) applyEntityTag(entity, nbtTag);
                        if (entity instanceof com.cyberday1.neoorigins.content.MagicOrbProjectile orb) {
                            if (effectType != null) orb.setEffectType(effectType);
                            if (orbColor != com.cyberday1.neoorigins.content.MagicOrbProjectile.COLOR_UNSET) orb.setOrbColor(orbColor);
                            if (glowColor != com.cyberday1.neoorigins.content.MagicOrbProjectile.COLOR_UNSET) orb.setGlowColor(glowColor);
                            if (size >= 0) orb.setSize(size);
                            if (glowSize >= 0) orb.setGlowSize(glowSize);
                            if (glowAlpha >= 0) orb.setGlowAlpha(glowAlpha);
                            if (shape != null && !shape.isEmpty()) orb.setShape(shape);
                            // Trail particle: explicit JSON id, else the effect_type shorthand default.
                            String resolvedTrail = trailParticle;
                            if ((resolvedTrail == null || resolvedTrail.isEmpty())) {
                                String d = com.cyberday1.neoorigins.api.content.vfx.VfxEffectTypes
                                    .defaults(orb.getEffectType()).trailParticle();
                                if (d != null) resolvedTrail = d;
                            }
                            if (resolvedTrail != null && !resolvedTrail.isEmpty()) orb.setTrailParticle(resolvedTrail);
                            orb.setTrailCount(Math.max(0, trailCount));
                            orb.setTrailSpread(trailSpread);
                            orb.setTrailSpeed(trailSpeed);
                        }
                        if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                            proj.setOwner(player);
                            proj.shootFromRotation(origin, xRot, yRot, 0f, speed, inaccuracy);
                        } else {
                            var look = origin.getLookAngle();
                            entity.setDeltaMovement(look.x * speed, look.y * speed, look.z * speed);
                        }
                        sl.addFreshEntity(entity);
                        // Apoli projectile_action: run immediately on the spawned
                        // projectile (actor = projectile), e.g. set_on_fire lights the
                        // ARROW, not the shooter.
                        if (projectileActionJson != null) {
                            applyProjectileAction(entity, player, projectileActionJson, sl);
                        }
                        if (onHitAction != null) {
                            com.cyberday1.neoorigins.service.ProjectileActionRegistry.register(
                                entity.getUUID(), onHitAction, player.tickCount);
                        }
                    }
                };
            },
            List.of(
                new FieldSpec("entity_type", FormFieldSpec.Kind.STRING, false)
                    .doc("Projectile entity type id (e.g. minecraft:arrow)."),
                new FieldSpec("projectile", FormFieldSpec.Kind.STRING, false)
                    .doc("Alias for entity_type."),
                new FieldSpec("speed", FormFieldSpec.Kind.NUMBER, false).def(1.5)
                    .doc("Launch speed (default 1.5)."),
                new FieldSpec("inaccuracy", FormFieldSpec.Kind.NUMBER, false).def(0.0).range(0.0, null)
                    .doc("Random spread; 0 = perfect aim (default 0)."),
                new FieldSpec("divergence", FormFieldSpec.Kind.NUMBER, false).range(0.0, null)
                    .doc("Alias for inaccuracy."),
                new FieldSpec("vertical_offset", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Spawn-Y offset from eye height (default 0)."),
                new FieldSpec("on_hit_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Action run when the projectile hits something."),
                new FieldSpec("projectile_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Entity action applied to the spawned projectile itself (actor = projectile), immediately on launch."),
                new FieldSpec("effect_type", FormFieldSpec.Kind.STRING, false)
                    .doc("Visual palette id for MagicOrb projectiles (client-side). Sets defaults for color/shape/trail_particle; explicit fields below override it."),
                new FieldSpec("orb_color", FormFieldSpec.Kind.STRING, false)
                    .doc("Core orb color. Accepts an RGB array [r,g,b] (0-255) or a hex string \"#RRGGBB\". Overrides the effect_type color."),
                new FieldSpec("glow_color", FormFieldSpec.Kind.STRING, false)
                    .doc("Outer-glow color (RGB array [r,g,b] or hex \"#RRGGBB\"). Defaults to the orb_color when omitted."),
                new FieldSpec("size", FormFieldSpec.Kind.NUMBER, false).range(0.0, null)
                    .doc("Core quad scale (renderer default 0.3)."),
                new FieldSpec("glow_size", FormFieldSpec.Kind.NUMBER, false).range(0.0, null)
                    .doc("Glow base scale (renderer default 0.7)."),
                new FieldSpec("glow_alpha", FormFieldSpec.Kind.INTEGER, false).range(0.0, 255.0)
                    .doc("Glow halo opacity 0-255 (renderer default 140)."),
                new FieldSpec("shape", FormFieldSpec.Kind.ENUM, false)
                    .options("cross", "cube", "ring", "sphere")
                    .doc("Procedural orb shape (default cross / effect_type default)."),
                new FieldSpec("trail_particle", FormFieldSpec.Kind.STRING, false)
                    .pattern("^[a-z0-9_.-]+:[a-z0-9_./-]+$")
                    .doc("Vanilla particle id for the flight trail (e.g. minecraft:witch). Overrides the effect_type trail."),
                new FieldSpec("count", FormFieldSpec.Kind.INTEGER, false).def(1).range(0.0, null)
                    .doc("Number of projectiles to fire (default 1; combine with divergence for a spread). "
                       + "For MagicOrb projectiles this keeps its legacy meaning: trail particles emitted per tick."),
                new FieldSpec("tag", FormFieldSpec.Kind.STRING, false)
                    .doc("SNBT compound merged onto each spawned projectile (e.g. \"{pickup:1b}\" so fired arrows can be picked up)."),
                new FieldSpec("spread", FormFieldSpec.Kind.NUMBER, false).def(0.05).range(0.0, null)
                    .doc("Trail particle position spread (default 0.05)."),
                new FieldSpec("trail_speed", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                    .doc("Trail particle speed/velocity (default 0)."),
                new FieldSpec("no_gravity", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                    .doc("If true the projectile ignores gravity and flies straight along its launch vector (default false).")));

        // execute_command — run a command at server (permission level 2). Lift-and-
        // shift of parseExecuteCommand. `command` defaults to "" and a blank command
        // silently no-ops (parser does not hard-fail), so it is modelled optional.
        // When the dispatch context carries a BlockPos (BLOCK_BREAK/PLACE/USE or a
        // synthetic raycast hit), the command source is repositioned at that block's
        // centre so `~ ~ ~` resolves to "this block".
        // Aliased as `command`: the compat translator emits Apoli's verb name
        // (apoli:command, same field shape — a `command` string), which previously
        // fell through to the unsupported-action no-op. The alias dispatches the
        // SAME factory, so the CommandPowerGuard blacklist applies identically.
        define("execute_command", List.of("command"),
            (json, ctx) -> {
                String command = json.has("command") ? json.get("command").getAsString() : "";
                return player -> {
                    if (player.level().getServer() == null || command.isBlank()) return;
                    try {
                        String finalCmd = com.cyberday1.neoorigins.compat.LegacyCommandRewriter.rewrite(command);
                        if (com.cyberday1.neoorigins.command.CommandPowerGuard.isBlocked(finalCmd)) {
                            com.cyberday1.neoorigins.command.CommandPowerGuard.warnBlocked(finalCmd, "execute_command");
                            return;
                        }
                        net.minecraft.core.BlockPos blockPos = ActionParser.extractCommandBlockPos(
                            com.cyberday1.neoorigins.service.ActionContextHolder.get());
                        var pos = blockPos != null
                            ? net.minecraft.world.phys.Vec3.atCenterOf(blockPos)
                            : player.position();
                        var serverSource = player.level().getServer().createCommandSourceStack()
                            .withSuppressedOutput()
                            .withEntity(player)
                            .withPosition(pos)
                            .withRotation(player.getRotationVector())
                            .withLevel((net.minecraft.server.level.ServerLevel) player.level());
                        player.level().getServer().getCommands().performPrefixedCommand(serverSource, finalCmd);
                    } catch (Exception e) {
                        NeoOrigins.LOGGER.warn("[CompatB] execute_command failed: {}", e.getMessage());
                    }
                };
            },
            List.of(new FieldSpec("command", FormFieldSpec.Kind.STRING, false).def("")
                .doc("Command to run at server authority; `~ ~ ~` resolves to the dispatch block when available.")));

        // drop_items — spawn one or more item stacks at the dispatch position.
        // Lift-and-shift of parseDropItems (see ActionParser's original javadoc for
        // the each/one_of semantics). `items` required-ish but the parser no-ops
        // silently when absent/empty → modelled optional.
        define("drop_items",
            (json, ctx) -> {
                if (!json.has("items") || !json.get("items").isJsonArray()) return EntityAction.noop();
                record Drop(net.minecraft.core.Holder<net.minecraft.world.item.Item> item,
                            int countMin, int countMax, float chance, int weight) {}
                List<Drop> drops = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement el : json.getAsJsonArray("items")) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject obj = el.getAsJsonObject();
                    if (!obj.has("item")) continue;
                    Identifier itemId = Identifier.parse(obj.get("item").getAsString());
                    var resolved = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId).orElse(null);
                    if (resolved == null) {
                        NeoOrigins.LOGGER.warn("[CompatB] drop_items: unknown item '{}' — skipped", itemId);
                        continue;
                    }
                    int countMin = 1, countMax = 1;
                    if (obj.has("count")) {
                        com.google.gson.JsonElement cEl = obj.get("count");
                        if (cEl.isJsonArray() && cEl.getAsJsonArray().size() >= 2) {
                            countMin = cEl.getAsJsonArray().get(0).getAsInt();
                            countMax = cEl.getAsJsonArray().get(1).getAsInt();
                            if (countMax < countMin) { int t = countMin; countMin = countMax; countMax = t; }
                        } else if (cEl.isJsonPrimitive()) {
                            countMin = countMax = cEl.getAsInt();
                        }
                    }
                    float chance = obj.has("chance") ? obj.get("chance").getAsFloat() : 1.0f;
                    int weight = Math.max(1, obj.has("weight") ? obj.get("weight").getAsInt() : 1);
                    drops.add(new Drop(resolved, countMin, countMax, chance, weight));
                }
                if (drops.isEmpty()) return EntityAction.noop();

                String mode = json.has("mode") ? json.get("mode").getAsString().toLowerCase(java.util.Locale.ROOT) : "each";
                int rolls = Math.max(1, json.has("rolls") ? json.get("rolls").getAsInt() : 1);
                boolean oneOf = "one_of".equals(mode) || "single".equals(mode) || "weighted".equals(mode);
                int totalWeight = 0;
                for (Drop d : drops) totalWeight += d.weight();
                final int finalTotalWeight = totalWeight;
                final int finalRolls = rolls;
                return player -> {
                    net.minecraft.core.BlockPos blockPos = ActionParser.extractCommandBlockPos(
                        com.cyberday1.neoorigins.service.ActionContextHolder.get());
                    net.minecraft.world.phys.Vec3 spawn = blockPos != null
                        ? net.minecraft.world.phys.Vec3.atCenterOf(blockPos)
                        : player.position().add(0, 0.5, 0);
                    var random = player.getRandom();

                    if (oneOf) {
                        for (int r = 0; r < finalRolls; r++) {
                            int roll = random.nextInt(finalTotalWeight);
                            int cum = 0;
                            for (Drop d : drops) {
                                cum += d.weight();
                                if (roll < cum) {
                                    int count = d.countMin() == d.countMax() ? d.countMin()
                                        : d.countMin() + random.nextInt(d.countMax() - d.countMin() + 1);
                                    if (count > 0) {
                                        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(player.level(),
                                            spawn.x, spawn.y, spawn.z, new net.minecraft.world.item.ItemStack(d.item(), count));
                                        entity.setDefaultPickUpDelay();
                                        player.level().addFreshEntity(entity);
                                    }
                                    break;
                                }
                            }
                        }
                    } else {
                        for (Drop d : drops) {
                            if (d.chance() < 1.0f && random.nextFloat() >= d.chance()) continue;
                            int count = d.countMin() == d.countMax() ? d.countMin()
                                : d.countMin() + random.nextInt(d.countMax() - d.countMin() + 1);
                            if (count <= 0) continue;
                            net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(player.level(),
                                spawn.x, spawn.y, spawn.z, new net.minecraft.world.item.ItemStack(d.item(), count));
                            entity.setDefaultPickUpDelay();
                            player.level().addFreshEntity(entity);
                        }
                    }
                };
            },
            List.of(
                new FieldSpec("mode", FormFieldSpec.Kind.ENUM, false).def("each")
                    .options("each", "one_of", "single", "weighted")
                    .doc("'each' rolls every entry independently; 'one_of' picks one weighted by 'weight'."),
                new FieldSpec("rolls", FormFieldSpec.Kind.INTEGER, false).def(1).range(1.0, null)
                    .doc("Number of weighted picks in one_of mode (default 1; ignored in each mode)."),
                new FieldSpec("items", FormFieldSpec.Kind.ARRAY, false)
                    .doc("Drop entries — each: {item, count (int|[min,max]), chance (each-mode), weight (one_of-mode)}.")));

        // apply_effect — add a mob effect. DUAL-SHAPE (lift-and-shift of
        // parseApplyEffect; NOT normalized — both shapes parse exactly as before):
        //   (1) effects[] array  — first entry's {effect/id, duration, amplifier,
        //                          is_ambient, show_particles, show_icon} is used.
        //   (2) flat single-effect — effect/id + the same fields read off the root.
        // First entry wins if both `effect` and `effects` are present. The
        // FieldSpec list is the oneOf-style union of both shapes' fields, mirroring
        // the hand-written schema branch; effect id is required-ish but the parser
        // no-ops silently when absent → modelled optional.
        define("apply_effect",
            (json, ctx) -> {
                String effectId = null;
                int duration = 200;
                int amplifier = 0;
                boolean ambient = false;
                boolean particles = true;
                boolean icon = true;

                if (json.has("effects") && json.get("effects").isJsonArray()) {
                    com.google.gson.JsonArray arr = json.getAsJsonArray("effects");
                    if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                        com.google.gson.JsonObject eff = arr.get(0).getAsJsonObject();
                        effectId = resolveEffectId(eff);
                        duration = eff.has("duration") ? eff.get("duration").getAsInt() : duration;
                        amplifier = eff.has("amplifier") ? eff.get("amplifier").getAsInt() : amplifier;
                        ambient = eff.has("is_ambient") && eff.get("is_ambient").getAsBoolean();
                        particles = !eff.has("show_particles") || eff.get("show_particles").getAsBoolean();
                        icon = !eff.has("show_icon") || eff.get("show_icon").getAsBoolean();
                    }
                } else {
                    effectId = resolveEffectId(json);
                    duration = json.has("duration") ? json.get("duration").getAsInt() : duration;
                    amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : amplifier;
                    ambient = json.has("is_ambient") && json.get("is_ambient").getAsBoolean();
                    particles = !json.has("show_particles") || json.get("show_particles").getAsBoolean();
                    icon = !json.has("show_icon") || json.get("show_icon").getAsBoolean();
                }

                if (effectId == null) return EntityAction.noop();
                // Cache mob effect holder at parse time — registry is static.
                var effectHolder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .get(Identifier.parse(effectId)).orElse(null);
                if (effectHolder == null) {
                    NeoOrigins.LOGGER.warn("[CompatB] apply_effect: unknown effect '{}' — action will no-op", effectId);
                    return EntityAction.noop();
                }
                final int fDur = duration;
                final int fAmp = amplifier;
                final boolean fAmb = ambient;
                final boolean fPart = particles;
                final boolean fIcon = icon;
                return player -> player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    effectHolder, fDur, fAmp, fAmb, fPart, fIcon));
            },
            List.of(
                // Shape 1 (flat single-effect):
                new FieldSpec("effect", FormFieldSpec.Kind.STRING, false)
                    .doc("Single effect id (use this OR effects[]; `id` accepted as a synonym)."),
                new FieldSpec("id", FormFieldSpec.Kind.STRING, false)
                    .doc("Alias for effect (flat shape)."),
                new FieldSpec("duration", FormFieldSpec.Kind.INTEGER, false).def(200).range(1.0, null)
                    .doc("Effect duration in ticks (default 200; honoured when using the 'effect' scalar)."),
                new FieldSpec("amplifier", FormFieldSpec.Kind.INTEGER, false).def(0).range(0.0, null)
                    .doc("Effect amplifier (default 0 = level I)."),
                new FieldSpec("is_ambient", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                    .doc("Reduced particle visibility (default false)."),
                new FieldSpec("show_particles", FormFieldSpec.Kind.BOOLEAN, false).def(true)
                    .doc("Show effect particles (default true)."),
                new FieldSpec("show_icon", FormFieldSpec.Kind.BOOLEAN, false).def(true)
                    .doc("Show HUD icon (default true)."),
                // Shape 2 (effects[] array). First entry wins if both are present;
                // each entry carries its own {effect/id, duration, amplifier,
                // is_ambient, show_particles, show_icon}.
                new FieldSpec("effects", FormFieldSpec.Kind.ARRAY, false)
                    .doc("Array of {effect, duration, amplifier, is_ambient, show_particles, show_icon} objects. First entry wins if both 'effect' and 'effects' are present.")));

        // ── Task 5: heavy/complex verbs, LIFTED AS-IS ──────────────────────────
        // Behavior-neutral by construction: the descriptor factory carries the
        // existing ActionParser parse lambda verbatim (delegation, no restructuring)
        // — the recursive fan-out (area_of_effect), synthetic-context publishing
        // (raycast), Item machinery (equipped_item_action / modify_inventory) and
        // entity-spawn factories stay exactly where they are; only dispatch moves
        // from the switch to the descriptor. The parse methods are package-private
        // for this delegation, mirroring failNoop / extractBientityTarget. The
        // FieldSpec lists are transcribed from the hand-written schema branches.

        // area_of_effect — run entity_action on every entity in radius, fanning out
        // apply_effect/damage leaves to mobs. `radius` required-ish (schema requires
        // it) but parser defaults to 16 and never hard-fails → modelled optional.
        define("area_of_effect",
            (json, ctx) -> ActionParser.parseAreaOfEffect(json, ctx),
            List.of(
                new FieldSpec("radius", FormFieldSpec.Kind.NUMBER, false).def(16.0).range(0.0, null)
                    .doc("AoE radius in blocks (default 16)."),
                new FieldSpec("entity_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Action run on every caught entity."),
                new FieldSpec("entity_condition", FormFieldSpec.Kind.REF, false).ref("condition.schema.json")
                    .doc("Optional filter — only entities matching are hit."),
                new FieldSpec("shape", FormFieldSpec.Kind.ENUM, false).def("sphere")
                    .options("sphere", "cube")
                    .doc("AoE shape (default sphere)."),
                new FieldSpec("include_source", FormFieldSpec.Kind.BOOLEAN, false).def(true)
                    .doc("Include the caster in the AoE (default true).")));

        // raycast — cast a ray from the eyes; run block/bientity/miss actions.
        define("raycast",
            (json, ctx) -> ActionParser.parseRaycast(json, ctx),
            List.of(
                new FieldSpec("distance", FormFieldSpec.Kind.NUMBER, false).def(10.0).range(0.0, null)
                    .doc("Max ray length in blocks (default 10)."),
                new FieldSpec("block", FormFieldSpec.Kind.BOOLEAN, false).def(true)
                    .doc("Test for block collisions (default true)."),
                new FieldSpec("entity", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                    .doc("Test for entity collisions (default false)."),
                new FieldSpec("fluid_handling", FormFieldSpec.Kind.ENUM, false).def("none")
                    .options("none", "source_only", "any")
                    .doc("How fluids are treated (default none)."),
                new FieldSpec("shape_type", FormFieldSpec.Kind.ENUM, false).def("visual")
                    .options("visual", "collider")
                    .doc("Block shape for the trace: visual (default) or collider."),
                new FieldSpec("block_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Action when a block is hit; ~ ~ ~ resolves to the hit block."),
                new FieldSpec("bientity_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Action when an entity is hit (with the hit entity as target context)."),
                new FieldSpec("miss_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Action when nothing is hit within range."),
                new FieldSpec("before_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Entity action run once before the ray is cast, regardless of hit outcome (e.g. consume a reagent)."),
                new FieldSpec("command_along_ray", FormFieldSpec.Kind.STRING, false)
                    .doc("Optional command run at each step along the ray (fires regardless of hit)."),
                new FieldSpec("command_step", FormFieldSpec.Kind.NUMBER, false).def(1.0).range(0.0, null)
                    .doc("Block increment between command_along_ray executions (default 1)."),
                new FieldSpec("command_at_hit", FormFieldSpec.Kind.STRING, false)
                    .doc("Optional command run at the precise impact point when the ray hits a block or entity.")));

        // equipped_item_action — read a slot's stack and run an ItemAction on it.
        define("equipped_item_action",
            (json, ctx) -> ActionParser.parseEquippedItemAction(json),
            List.of(
                new FieldSpec("equipment_slot", FormFieldSpec.Kind.ENUM, false).def("mainhand")
                    .options("mainhand", "offhand", "head", "chest", "legs", "feet")
                    .doc("Equipment slot to read the stack from (default mainhand)."),
                new FieldSpec("action", FormFieldSpec.Kind.REF, false)
                    .ref("item_action.schema.json")
                    .doc("ItemAction to run on the stack (consume, damage, set-NBT, etc.).")));

        // modify_inventory — filter inventory stacks and run an ItemAction on each.
        define("modify_inventory",
            (json, ctx) -> ActionParser.parseModifyInventory(json),
            List.of(
                new FieldSpec("item_condition", FormFieldSpec.Kind.REF, false)
                    .ref("item_condition.schema.json")
                    .doc("Optional ItemCondition filtering which stacks to process."),
                new FieldSpec("item_action", FormFieldSpec.Kind.REF, false)
                    .ref("item_action.schema.json")
                    .doc("ItemAction run on each matching stack."),
                new FieldSpec("process_mode", FormFieldSpec.Kind.ENUM, false).def("items")
                    .options("items", "stacks")
                    .doc("items (default) counts individual items; stacks counts whole stacks toward the limit."),
                new FieldSpec("limit", FormFieldSpec.Kind.INTEGER, false).def(0).range(0.0, null)
                    .doc("Cap on items/stacks processed; 0 = no limit (default 0).")));

        // spawn_lingering_area — spawn a recurring effect-cloud entity.
        define("spawn_lingering_area",
            (json, ctx) -> ActionParser.parseSpawnLingeringArea(json, ctx),
            List.of(
                new FieldSpec("radius", FormFieldSpec.Kind.NUMBER, false).def(3.0)
                    .doc("Effect cloud radius (default 3.0)."),
                new FieldSpec("duration_ticks", FormFieldSpec.Kind.INTEGER, false).def(100).range(1.0, null)
                    .doc("Lifetime in ticks (default 100)."),
                new FieldSpec("interval_ticks", FormFieldSpec.Kind.INTEGER, false).def(20).range(1.0, null)
                    .doc("Ticks between entity_action firings (default 20)."),
                new FieldSpec("effect_type", FormFieldSpec.Kind.STRING, false)
                    .doc("Visual effect palette id (client-side; optional)."),
                new FieldSpec("particle_type", FormFieldSpec.Kind.STRING, false).def("minecraft:witch")
                    .doc("Particle id rendered (default minecraft:witch)."),
                new FieldSpec("entity_action", FormFieldSpec.Kind.REF, false).ref("#")
                    .doc("Action run every interval against caught entities.")));

        // spawn_black_hole — spawn a pull-and-damage entity.
        define("spawn_black_hole",
            (json, ctx) -> ActionParser.parseSpawnBlackHole(json, ctx),
            List.of(
                new FieldSpec("radius", FormFieldSpec.Kind.NUMBER, false).def(6.0)
                    .doc("Pull radius (default 6.0)."),
                new FieldSpec("duration_ticks", FormFieldSpec.Kind.INTEGER, false).def(100).range(1.0, null)
                    .doc("Lifetime (default 100)."),
                new FieldSpec("pull_strength", FormFieldSpec.Kind.NUMBER, false).def(1.5)
                    .doc("Per-tick pull magnitude (default 1.5)."),
                new FieldSpec("damage_per_tick", FormFieldSpec.Kind.NUMBER, false).def(2.0)
                    .doc("Damage applied per tick to caught entities (default 2.0)."),
                new FieldSpec("effect_type", FormFieldSpec.Kind.STRING, false)
                    .doc("Visual palette id (client-side; optional).")));

        // spawn_tornado — spawn a pull/lift/spin vortex entity.
        define("spawn_tornado",
            (json, ctx) -> ActionParser.parseSpawnTornado(json, ctx),
            List.of(
                new FieldSpec("radius", FormFieldSpec.Kind.NUMBER, false).def(5.0)
                    .doc("Vortex radius (default 5.0)."),
                new FieldSpec("duration_ticks", FormFieldSpec.Kind.INTEGER, false).def(100).range(1.0, null)
                    .doc("Lifetime (default 100)."),
                new FieldSpec("pull_strength", FormFieldSpec.Kind.NUMBER, false).def(1.0)
                    .doc("Horizontal pull magnitude (default 1.0)."),
                new FieldSpec("lift_strength", FormFieldSpec.Kind.NUMBER, false).def(0.5)
                    .doc("Upward lift magnitude (default 0.5)."),
                new FieldSpec("spin_strength", FormFieldSpec.Kind.NUMBER, false).def(0.5)
                    .doc("Tangential spin magnitude (default 0.5)."),
                new FieldSpec("damage_per_interval", FormFieldSpec.Kind.NUMBER, false).def(2.0)
                    .doc("Damage per interval (default 2.0)."),
                new FieldSpec("damage_interval_ticks", FormFieldSpec.Kind.INTEGER, false).def(10).range(1.0, null)
                    .doc("Ticks between damage applications (default 10)."),
                new FieldSpec("effect_type", FormFieldSpec.Kind.STRING, false)
                    .doc("Visual palette id (client-side; optional).")));

        // chain_to_nearest — pull the player toward the nearest matching entity.
        define("chain_to_nearest",
            (json, ctx) -> ActionParser.parseChainToNearest(json, ctx),
            List.of(
                new FieldSpec("radius", FormFieldSpec.Kind.NUMBER, false).def(16.0)
                    .doc("Search radius (default 16)."),
                new FieldSpec("speed", FormFieldSpec.Kind.NUMBER, false).def(1.0)
                    .doc("Pull speed (default 1.0)."),
                new FieldSpec("target_condition", FormFieldSpec.Kind.REF, false).ref("condition.schema.json")
                    .doc("Optional filter — only entities matching are valid targets.")));

        // pull_entities — pull nearby entities toward the caster.
        define("pull_entities",
            (json, ctx) -> ActionParser.parsePullEntities(json, ctx),
            List.of(
                new FieldSpec("radius", FormFieldSpec.Kind.NUMBER, false).def(8.0)
                    .doc("Pull radius (default 8)."),
                new FieldSpec("strength", FormFieldSpec.Kind.NUMBER, false).def(0.5)
                    .doc("Pull magnitude per entity (default 0.5)."),
                new FieldSpec("include_players", FormFieldSpec.Kind.BOOLEAN, false).def(true)
                    .doc("Whether to pull other players (default true)."),
                new FieldSpec("entity_condition", FormFieldSpec.Kind.REF, false).ref("condition.schema.json")
                    .doc("Optional filter on which entities to pull.")));

        // cast_spell — cast an inline author-baked Build A Spell (buildaspell)
        // spell as the player. Every BaS-typed reference is isolated in
        // compat.buildaspell.BuildASpellBridge and only class-loaded behind the
        // ModList.isLoaded("buildaspell") gate below, so packs that use this
        // action on servers without BaS degrade to a logged no-op rather than a
        // class-load error. The Spell is built ONCE here at parse time (a reusable
        // POJO per the BaS contract) and captured in the returned action; each
        // dispatch is a single cast. Cost is charged on the NeoOrigins power, so the
        // BaS mana pool is never touched (consumeMana=false). Gate the power with
        // "required_mods": ["buildaspell"] so it doesn't even load without BaS.
        define("cast_spell",
            (json, ctx) -> {
                String delivery = json.has("delivery") ? json.get("delivery").getAsString() : "";
                List<String> components = new java.util.ArrayList<>();
                if (json.has("components") && json.get("components").isJsonArray()) {
                    for (com.google.gson.JsonElement el : json.getAsJsonArray("components")) {
                        if (el.isJsonPrimitive()) components.add(el.getAsString());
                    }
                }
                if (delivery.isBlank() || components.isEmpty()) {
                    NeoOrigins.LOGGER.warn(
                        "[BaS] cast_spell in '{}' needs a non-blank 'delivery' and a non-empty 'components' list — power does nothing",
                        ctx);
                    return EntityAction.noop();
                }
                if (!net.neoforged.fml.ModList.get().isLoaded("buildaspell")) {
                    NeoOrigins.LOGGER.warn(
                        "[BaS] cast_spell in '{}' requires Build A Spell (buildaspell), which isn't installed — power does nothing. Gate it with \"required_mods\": [\"buildaspell\"].",
                        ctx);
                    return EntityAction.noop();
                }
                return com.cyberday1.neoorigins.compat.buildaspell.BuildASpellBridge.castSpell(delivery, components, ctx);
            },
            List.of(
                new FieldSpec("delivery", FormFieldSpec.Kind.ENUM, true)
                    .options("rune", "sight", "self", "cast", "tracking")
                    .doc("How the spell is delivered. One of Build A Spell's 5 delivery methods: `rune`, `sight`, `self`, `cast`, `tracking`."),
                new FieldSpec("components", FormFieldSpec.Kind.ARRAY, true)
                    .itemPattern("^(compat:)?[a-z0-9_.]+$")
                    .doc("The ordered component list, EFFECT-FIRST: each effect id is followed by the modifier ids that apply to it (a modifier binds to the PRECEDING effect; one before the first effect is silently dropped). Effect/modifier ids come from Build A Spell; `compat:*` ids pass through verbatim. Easiest path: build the spell in-game and use BaS's \"Export to Power\" button, which emits this list in the correct order.")));
    }

    /**
     * Resolve a mob-effect id from {@code effect} or the {@code id} synonym on the
     * given object. Lift-and-shift of {@code ActionParser.resolveEffectId} — used
     * by the dual-shape {@code apply_effect} descriptor for both the flat root and
     * each {@code effects[]} entry.
     */
    static String resolveEffectId(com.google.gson.JsonObject obj) {
        if (obj.has("effect") && obj.get("effect").isJsonPrimitive()) {
            return obj.get("effect").getAsString();
        }
        if (obj.has("id") && obj.get("id").isJsonPrimitive()) {
            return obj.get("id").getAsString();
        }
        return null;
    }

    /**
     * Parse a color JSON value into a packed {@code 0xRRGGBB} int. Accepts either
     * an RGB array {@code [r,g,b]} with components 0–255, or a hex string
     * {@code "#RRGGBB"} (the leading {@code #} is optional; 3-digit {@code #RGB}
     * shorthand is also expanded). Returns
     * {@link com.cyberday1.neoorigins.content.MagicOrbProjectile#COLOR_UNSET} for a
     * null/absent/unparseable value so callers can fall back to defaults.
     */
    static int parseColor(com.google.gson.JsonElement el) {
        final int UNSET = com.cyberday1.neoorigins.content.MagicOrbProjectile.COLOR_UNSET;
        if (el == null || el.isJsonNull()) return UNSET;
        try {
            if (el.isJsonArray()) {
                com.google.gson.JsonArray arr = el.getAsJsonArray();
                if (arr.size() < 3) return UNSET;
                int r = clamp255(arr.get(0).getAsInt());
                int g = clamp255(arr.get(1).getAsInt());
                int b = clamp255(arr.get(2).getAsInt());
                return (r << 16) | (g << 8) | b;
            }
            if (el.isJsonPrimitive()) {
                String s = el.getAsString().trim();
                if (s.startsWith("#")) s = s.substring(1);
                if (s.length() == 3) { // #RGB shorthand → #RRGGBB
                    s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
                }
                if (s.length() != 6) return UNSET;
                return Integer.parseInt(s, 16) & 0xFFFFFF;
            }
        } catch (RuntimeException e) {
            NeoOrigins.LOGGER.warn("[CompatB] spawn_projectile: bad color value '{}' — ignored", el);
        }
        return UNSET;
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // ── Item 4 entity-target helpers (shared with TargetActionParser) ────────────

    /**
     * Shear {@code target} via the NeoForge {@link net.neoforged.neoforge.common.IShearable}
     * seam — the same path vanilla/modded shears use, so sheep drop wool + go bald,
     * mooshrooms convert to cows + drop mushrooms, snow golems lose their pumpkin,
     * and modded shearables behave correctly. {@code actor} is passed as the
     * triggering player (for sound source / loot attribution). No-op when the target
     * is null, not an {@link net.neoforged.neoforge.common.IShearable}, or not
     * currently shearable (e.g. an already-sheared sheep). Server-side only.
     *
     * <p>26.1 delta: {@code spawnShearedDrop} takes a {@link net.minecraft.server.level.ServerLevel}
     * (it was {@code Level} on 1.21.1), so we pass the resolved ServerLevel rather
     * than the raw {@code target.level()}.
     */
    static void shearTarget(net.minecraft.world.entity.LivingEntity target,
                            net.minecraft.server.level.ServerPlayer actor) {
        if (target == null) return;
        if (!(target.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        if (!(target instanceof net.neoforged.neoforge.common.IShearable shearable)) return;
        net.minecraft.core.BlockPos pos = target.blockPosition();
        net.minecraft.world.item.ItemStack tool = net.minecraft.world.item.ItemStack.EMPTY;
        if (!shearable.isShearable(actor, tool, serverLevel, pos)) return;
        java.util.List<net.minecraft.world.item.ItemStack> drops =
            shearable.onSheared(actor, tool, serverLevel, pos);
        for (net.minecraft.world.item.ItemStack drop : drops) {
            shearable.spawnShearedDrop(serverLevel, pos, drop);
        }
    }

    /**
     * Set the colour of a dyeable {@code target}. On 26.1 the only mob with a
     * public colour setter is {@link net.minecraft.world.entity.animal.sheep.Sheep}
     * (wool colour); wolf/cat collar setters are private, so those mobs no-op
     * cleanly. No-op when the target is null or non-dyeable.
     *
     * <p>26.1 delta: {@code Sheep} moved from {@code net.minecraft.world.entity.animal}
     * to {@code net.minecraft.world.entity.animal.sheep}.
     */
    static void dyeTarget(net.minecraft.world.entity.LivingEntity target,
                          net.minecraft.world.item.DyeColor color) {
        if (target == null || color == null) return;
        if (target instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep) {
            sheep.setColor(color);
        }
        // Other dyeable mobs (wolf/cat collars) expose no public setter on 26.1 — no-op.
    }

    /**
     * Apply an Apoli {@code entity_action} immediately to a freshly-spawned
     * projectile entity, with the projectile as the action's actor — the correct
     * Apoli semantics for {@code fire_projectile.projectile_action}.
     *
     * <p>The projectile (e.g. an {@code Arrow}) is an {@link net.minecraft.world.entity.Entity}
     * but usually <b>not</b> a {@link net.minecraft.world.entity.LivingEntity}, so the
     * player-typed {@link EntityAction} path cannot carry it. The entity-general
     * verbs that touch only {@code Entity} methods ({@code set_on_fire},
     * {@code extinguish}, {@code add_velocity}, {@code execute_command} with
     * {@code @s} bound to the projectile, and {@code and}/{@code nothing}) are
     * handled here directly; any remaining generalizable verb is delegated to
     * {@link TargetActionParser} when the projectile happens to be a
     * {@code LivingEntity}. Unknown verbs are skipped (logged at debug) — never
     * re-routed onto the shooter, which was the bug.
     */
    static void applyProjectileAction(net.minecraft.world.entity.Entity projectile,
                                      net.minecraft.server.level.ServerPlayer actor,
                                      com.google.gson.JsonObject json,
                                      net.minecraft.server.level.ServerLevel sl) {
        if (projectile == null || json == null) return;
        String type = json.has("type") ? json.get("type").getAsString() : "";
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "neoorigins:" + type;
        } else if (type.startsWith("origins:") || type.startsWith("apoli:") || type.startsWith("apace:")) {
            type = "neoorigins:" + type.substring(type.indexOf(':') + 1);
        }
        switch (type) {
            case "neoorigins:nothing" -> { }
            case "neoorigins:and" -> {
                if (json.has("actions") && json.get("actions").isJsonArray()) {
                    for (var el : json.getAsJsonArray("actions")) {
                        if (el.isJsonObject()) applyProjectileAction(projectile, actor, el.getAsJsonObject(), sl);
                    }
                }
            }
            case "neoorigins:set_on_fire" -> {
                int ticks = json.has("ticks") ? json.get("ticks").getAsInt()
                          : json.has("duration") ? json.get("duration").getAsInt() : 20;
                projectile.setRemainingFireTicks(ticks);
            }
            case "neoorigins:extinguish" -> projectile.clearFire();
            case "neoorigins:add_velocity" -> {
                double x = json.has("x") ? json.get("x").getAsDouble() : 0;
                double y = json.has("y") ? json.get("y").getAsDouble() : 0;
                double z = json.has("z") ? json.get("z").getAsDouble() : 0;
                boolean set = json.has("set") && json.get("set").getAsBoolean();
                if (set) projectile.setDeltaMovement(x, y, z);
                else projectile.push(x, y, z);
                projectile.hurtMarked = true;
            }
            case "neoorigins:execute_command", "neoorigins:command" -> {
                String cmd = json.has("command") ? json.get("command").getAsString() : "";
                if (!cmd.isBlank() && sl.getServer() != null) {
                    var src = projectile.createCommandSourceStackForNameResolution(sl).withSuppressedOutput()
                        .withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
                    try {
                        sl.getServer().getCommands().performPrefixedCommand(src, cmd);
                    } catch (Exception ignored) { }
                }
            }
            default -> {
                // Living-only verbs (apply_effect, heal, damage, …) — delegate to
                // TargetActionParser when the projectile is actually a LivingEntity.
                if (projectile instanceof net.minecraft.world.entity.LivingEntity le) {
                    TargetAction ta = TargetActionParser.parse(json, "projectile_action");
                    if (ta != null) { ta.execute(le, actor); return; }
                }
                NeoOrigins.LOGGER.debug(
                    "[CompatB] projectile_action: verb '{}' not applicable to a projectile — skipped", type);
            }
        }
    }

    /**
     * Merge an SNBT {@code tag} string (Apoli {@code fire_projectile.tag}) onto a
     * freshly-created entity before it is added to the world. Saves the entity's
     * current NBT, merges the parsed tag over it (so position/motion already set
     * are preserved unless the tag overrides them), then reloads — mirroring how
     * vanilla {@code /summon ... <tag>} applies its compound.
     *
     * <p>26.1 note: entity save/load now flows through {@code ValueOutput}/{@code ValueInput}
     * rather than raw {@code CompoundTag}; we bridge with {@code TagValueOutput}/{@code TagValueInput}
     * (registry-aware, discarding problem reporter) and {@code TagParser.parseCompoundFully}
     * (1.21.1's {@code TagParser.parseTag} is gone on 26.1).
     */
    static void applyEntityTag(net.minecraft.world.entity.Entity entity, String snbt) {
        if (entity == null || snbt == null || snbt.isBlank()) return;
        try {
            net.minecraft.nbt.CompoundTag parsed = net.minecraft.nbt.TagParser.parseCompoundFully(snbt);
            var provider = entity.level().registryAccess();
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, provider);
            entity.saveWithoutId(out);
            net.minecraft.nbt.CompoundTag full = out.buildResult();
            full.merge(parsed);
            entity.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, provider, full));
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] fire_projectile: unparseable tag NBT '{}': {}", snbt, e.getMessage());
        }
    }

    /**
     * Map a slot name (mainhand/offhand/head/chest/legs/feet) to an
     * {@link net.minecraft.world.entity.EquipmentSlot}, defaulting to MAINHAND on an
     * empty/unknown name (with a one-shot warning for genuinely unknown names).
     */
    static net.minecraft.world.entity.EquipmentSlot parseEquipmentSlotOrMain(String name, String verb) {
        if (name == null || name.isEmpty()) return net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "head"    -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chest"   -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "legs"    -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "feet"    -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case "offhand" -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case "mainhand" -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            default -> {
                NeoOrigins.LOGGER.warn("[CompatB] {}: unknown slot '{}' — defaulting to mainhand", verb, name);
                yield net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            }
        };
    }

    /**
     * Resolve a {@code drop_inventory} {@code slots} selector to vanilla command-slot
     * ids ({@link net.minecraft.world.entity.Entity#getSlot(int)} numbering, shared
     * with Apoli's slot names via {@link net.minecraft.world.inventory.SlotRanges}).
     * Returns {@code null} when no selector is present or the list is empty (Apoli
     * treats that as "every slot"); otherwise the resolved ids, which may be empty if
     * every listed name was unknown (drop nothing — safer than falling back to all).
     */
    static int[] parseDropInventorySlots(com.google.gson.JsonObject json) {
        if (!json.has("slots") || !json.get("slots").isJsonArray()) return null;
        com.google.gson.JsonArray arr = json.getAsJsonArray("slots");
        if (arr.isEmpty()) return null;
        it.unimi.dsi.fastutil.ints.IntList ids = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (com.google.gson.JsonElement el : arr) {
            if (!el.isJsonPrimitive()) continue;
            String name = el.getAsString();
            net.minecraft.world.inventory.SlotRange range =
                net.minecraft.world.inventory.SlotRanges.nameToIds(name);
            if (range == null) {
                NeoOrigins.LOGGER.warn("[CompatB] drop_inventory: unknown slot '{}' — ignored", name);
                continue;
            }
            ids.addAll(range.slots());
        }
        return ids.toIntArray();
    }

    // ── Item 5: block-target verb implementations ───────────────────────────
    // Shared by the context-resolving EntityAction registrations (so they can be
    // used directly as an on_hit_action and self-resolve the impacted block from
    // ActionContextHolder) and by BlockTargetActionParser (so block_target_action
    // reaches them). Each no-ops cleanly when the block isn't applicable.

    /**
     * Wrap a {@link BlockTargetAction} as a context-resolving {@link EntityAction}:
     * resolve the impacted block ({@link ActionParser#extractBlockTarget}) from the
     * active dispatch context — using the actor's level as the fallback level for
     * the raycast context — and run the verb against {@code (level, pos, actor)}.
     * No-op when no block context resolves. This is the bridge that lets every
     * block-target verb be used directly as a projectile {@code on_hit_action} /
     * raycast {@code block_action} (self-resolving the block) and is reused by the
     * {@code block_target_action} wrapper.
     */
    private static EntityAction blockTargetEntityAction(BlockTargetAction verb) {
        return actor -> {
            net.minecraft.server.level.ServerLevel actorLevel =
                actor.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null;
            ActionParser.BlockTarget bt = ActionParser.extractBlockTarget(
                com.cyberday1.neoorigins.service.ActionContextHolder.get(), actorLevel);
            if (bt == null) return;
            verb.execute(bt.level(), bt.pos(), actor);
        };
    }

    /**
     * Resolve a block id string to a {@link net.minecraft.world.level.block.Block},
     * or {@code null} when the string is null/blank or unknown (one-shot warning
     * for genuinely-unknown ids). Used by {@code transform_block}'s from/to fields.
     *
     * <p>26.1: {@code BuiltInRegistries.BLOCK.get(Identifier)} returns an
     * {@code Optional<Holder<Block>>}, so unwrap via {@code .get().value()}
     * (1.21.1 returned the {@code Block} directly).
     */
    static net.minecraft.world.level.block.Block resolveBlockOrNull(String id, String where) {
        if (id == null || id.isBlank()) return null;
        net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.tryParse(id);
        if (rl == null) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: malformed block id '{}' — will no-op", where, id);
            return null;
        }
        var opt = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl);
        if (opt.isEmpty()) {
            NeoOrigins.LOGGER.warn("[CompatB] {}: unknown block '{}' — will no-op", where, id);
            return null;
        }
        return opt.get().value();
    }

    /**
     * Log/wood → stripped-variant map for {@code strip}. Vanilla's
     * {@code AxeItem.STRIPPABLES} is {@code protected} (not accessible), so this
     * mirrors it for the wood/log family (the strip case authors actually want).
     * Built lazily on first use. Axis/orientation is carried over by
     * {@link #applyStrip} so pillar logs keep their rotation.
     */
    private static java.util.Map<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block> STRIP_MAP;

    private static java.util.Map<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block> stripMap() {
        if (STRIP_MAP != null) return STRIP_MAP;
        var m = new java.util.HashMap<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block>();
        // Vanilla wood family (logs + wood + mangrove roots).
        m.put(net.minecraft.world.level.block.Blocks.OAK_LOG, net.minecraft.world.level.block.Blocks.STRIPPED_OAK_LOG);
        m.put(net.minecraft.world.level.block.Blocks.SPRUCE_LOG, net.minecraft.world.level.block.Blocks.STRIPPED_SPRUCE_LOG);
        m.put(net.minecraft.world.level.block.Blocks.BIRCH_LOG, net.minecraft.world.level.block.Blocks.STRIPPED_BIRCH_LOG);
        m.put(net.minecraft.world.level.block.Blocks.JUNGLE_LOG, net.minecraft.world.level.block.Blocks.STRIPPED_JUNGLE_LOG);
        m.put(net.minecraft.world.level.block.Blocks.ACACIA_LOG, net.minecraft.world.level.block.Blocks.STRIPPED_ACACIA_LOG);
        m.put(net.minecraft.world.level.block.Blocks.DARK_OAK_LOG, net.minecraft.world.level.block.Blocks.STRIPPED_DARK_OAK_LOG);
        m.put(net.minecraft.world.level.block.Blocks.MANGROVE_LOG, net.minecraft.world.level.block.Blocks.STRIPPED_MANGROVE_LOG);
        m.put(net.minecraft.world.level.block.Blocks.CHERRY_LOG, net.minecraft.world.level.block.Blocks.STRIPPED_CHERRY_LOG);
        m.put(net.minecraft.world.level.block.Blocks.CRIMSON_STEM, net.minecraft.world.level.block.Blocks.STRIPPED_CRIMSON_STEM);
        m.put(net.minecraft.world.level.block.Blocks.WARPED_STEM, net.minecraft.world.level.block.Blocks.STRIPPED_WARPED_STEM);
        m.put(net.minecraft.world.level.block.Blocks.BAMBOO_BLOCK, net.minecraft.world.level.block.Blocks.STRIPPED_BAMBOO_BLOCK);
        // Wood (bark-on-all-sides) variants.
        m.put(net.minecraft.world.level.block.Blocks.OAK_WOOD, net.minecraft.world.level.block.Blocks.STRIPPED_OAK_WOOD);
        m.put(net.minecraft.world.level.block.Blocks.SPRUCE_WOOD, net.minecraft.world.level.block.Blocks.STRIPPED_SPRUCE_WOOD);
        m.put(net.minecraft.world.level.block.Blocks.BIRCH_WOOD, net.minecraft.world.level.block.Blocks.STRIPPED_BIRCH_WOOD);
        m.put(net.minecraft.world.level.block.Blocks.JUNGLE_WOOD, net.minecraft.world.level.block.Blocks.STRIPPED_JUNGLE_WOOD);
        m.put(net.minecraft.world.level.block.Blocks.ACACIA_WOOD, net.minecraft.world.level.block.Blocks.STRIPPED_ACACIA_WOOD);
        m.put(net.minecraft.world.level.block.Blocks.DARK_OAK_WOOD, net.minecraft.world.level.block.Blocks.STRIPPED_DARK_OAK_WOOD);
        m.put(net.minecraft.world.level.block.Blocks.MANGROVE_WOOD, net.minecraft.world.level.block.Blocks.STRIPPED_MANGROVE_WOOD);
        m.put(net.minecraft.world.level.block.Blocks.CHERRY_WOOD, net.minecraft.world.level.block.Blocks.STRIPPED_CHERRY_WOOD);
        m.put(net.minecraft.world.level.block.Blocks.CRIMSON_HYPHAE, net.minecraft.world.level.block.Blocks.STRIPPED_CRIMSON_HYPHAE);
        m.put(net.minecraft.world.level.block.Blocks.WARPED_HYPHAE, net.minecraft.world.level.block.Blocks.STRIPPED_WARPED_HYPHAE);
        STRIP_MAP = java.util.Collections.unmodifiableMap(m);
        return STRIP_MAP;
    }

    /**
     * strip — axe-strip the block at {@code pos} (log → stripped log, wood →
     * stripped wood) using {@link #stripMap()} (mirroring vanilla's protected
     * {@code AxeItem.STRIPPABLES}), preserving block state (axis/orientation).
     * No-op when the block has no strip mapping. {@code actor} unused.
     */
    static void applyStrip(net.minecraft.server.level.ServerLevel level,
                           net.minecraft.core.BlockPos pos,
                           net.minecraft.server.level.ServerPlayer actor) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        net.minecraft.world.level.block.Block stripped = stripMap().get(block);
        if (stripped == null) return;
        net.minecraft.world.level.block.state.BlockState out = stripped.defaultBlockState();
        // Preserve the rotational axis for pillar-style logs.
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
            && out.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
            out = out.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS));
        }
        level.setBlockAndUpdate(pos, out);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AXE_STRIP,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * till — hoe-till dirt / grass / coarse-dirt / rooted-dirt → farmland (and
     * grass-path → dirt) at {@code pos}, but only when the block above is air
     * (vanilla tilling rule), matching hoe behaviour. No-op otherwise.
     */
    static void applyTill(net.minecraft.server.level.ServerLevel level,
                          net.minecraft.core.BlockPos pos,
                          net.minecraft.server.level.ServerPlayer actor) {
        net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
        net.minecraft.world.level.block.state.BlockState out;
        if (block == net.minecraft.world.level.block.Blocks.GRASS_BLOCK
            || block == net.minecraft.world.level.block.Blocks.DIRT
            || block == net.minecraft.world.level.block.Blocks.DIRT_PATH
            || block == net.minecraft.world.level.block.Blocks.ROOTED_DIRT) {
            out = net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState();
        } else if (block == net.minecraft.world.level.block.Blocks.COARSE_DIRT) {
            out = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        } else {
            return;
        }
        // Vanilla only tills when there's space above the block.
        if (!level.getBlockState(pos.above()).isAir()) return;
        level.setBlockAndUpdate(pos, out);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.HOE_TILL,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * path — shovel a grass block (and the other flattenables: dirt, podzol,
     * mycelium, coarse/rooted dirt) into a dirt path at {@code pos}, only when the
     * block above is air (vanilla shovel rule). No-op otherwise.
     */
    static void applyPath(net.minecraft.server.level.ServerLevel level,
                          net.minecraft.core.BlockPos pos,
                          net.minecraft.server.level.ServerPlayer actor) {
        net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
        if (block != net.minecraft.world.level.block.Blocks.GRASS_BLOCK
            && block != net.minecraft.world.level.block.Blocks.DIRT
            && block != net.minecraft.world.level.block.Blocks.PODZOL
            && block != net.minecraft.world.level.block.Blocks.MYCELIUM
            && block != net.minecraft.world.level.block.Blocks.COARSE_DIRT
            && block != net.minecraft.world.level.block.Blocks.ROOTED_DIRT) {
            return;
        }
        if (!level.getBlockState(pos.above()).isAir()) return;
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.DIRT_PATH.defaultBlockState());
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SHOVEL_FLATTEN,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * grow — bonemeal-style growth on the block at {@code pos}: if it's a
     * {@link net.minecraft.world.level.block.BonemealableBlock} ready to grow,
     * apply one bonemeal tick (crops/saplings/grass/etc.) via the vanilla
     * {@code performBonemeal} path, with the green growth particles. No-op when
     * the block isn't bonemealable or isn't valid for growth right now.
     */
    static void applyGrow(net.minecraft.server.level.ServerLevel level,
                          net.minecraft.core.BlockPos pos,
                          net.minecraft.server.level.ServerPlayer actor) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock bonemealable)) return;
        if (!bonemealable.isValidBonemealTarget(level, pos, state)) return;
        if (!bonemealable.isBonemealSuccess(level, level.getRandom(), pos, state)) return;
        bonemealable.performBonemeal(level, level.getRandom(), pos, state);
        level.levelEvent(1505 /* BONEMEAL_USE growth particles */, pos, 15);
    }

    /**
     * transform_block — the generic primitive. When {@code from} is non-null, only
     * transform if the block at {@code pos} matches it; set the block to {@code to}.
     * No-op when {@code to} is null (unknown/missing) or the {@code from} guard
     * fails. Used as the fallback for any state swap not covered by the verbs above.
     */
    static void applyTransformBlock(net.minecraft.server.level.ServerLevel level,
                                    net.minecraft.core.BlockPos pos,
                                    net.minecraft.world.level.block.Block from,
                                    net.minecraft.world.level.block.Block to) {
        if (to == null) return;
        if (from != null && level.getBlockState(pos).getBlock() != from) return;
        level.setBlockAndUpdate(pos, to.defaultBlockState());
    }

    /** Descriptor for the given canonical {@code "neoorigins:<verb>"} id, or {@code null}. */
    public static ActionType get(String canonicalType) {
        return BY_KEY.get(canonicalType);
    }

    /** All built-in action descriptors, in registration order. */
    public static Map<Identifier, ActionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }

    /**
     * Canonical {@code neoorigins:<verb>} id strings for every descriptor — the
     * type total the audit counts (aliases excluded, since an alias is not a
     * separate type).
     */
    public static java.util.Set<String> canonicalIds() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (Identifier rl : DESCRIPTORS.keySet()) ids.add(rl.toString());
        return ids;
    }

    /**
     * Alias id strings across all descriptors (synonyms that dispatch to a
     * canonical verb). Surfaced so {@code SchemaFormCheck} can treat them as known
     * verbs in the {@code KNOWN_TYPES} parity check without counting them as
     * separate types.
     */
    public static java.util.Set<String> aliasIds() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (ActionType t : DESCRIPTORS.values()) {
            for (Identifier alias : t.aliases()) ids.add(alias.toString());
        }
        return ids;
    }
}
