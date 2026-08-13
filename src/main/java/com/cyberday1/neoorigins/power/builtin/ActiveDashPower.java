package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ActiveDashPower extends AbstractActivePower<ActiveDashPower.Config> {

    public record Config(
        float power,
        int cooldownTicks,
        boolean allowVertical,
        boolean setVelocity,
        float damage,
        float damageRadius,
        float weaponDamageScale,
        EntityCondition condition,
        String type,
        String cooldownIcon,
        boolean cooldownCountdown,
        boolean alwaysShowIcon
    ) implements AbstractActivePower.Config {
        // Hand-rolled rather than a RecordCodecBuilder group because `condition`
        // compiles through ConditionParser, which reads raw JSON.
        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "active_dash: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "active_dash: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:active_dash";

                // A malformed field (say "power": "fast") must come back as a
                // DataResult error the loader logs and skips, the way the
                // RecordCodecBuilder group this replaced did. Left unguarded,
                // getAsFloat's NumberFormatException would escape decode() and
                // take the whole power reload down with it.
                try {
                    float power = obj.has("power") ? obj.get("power").getAsFloat() : 1.5f;
                    int cooldown = obj.has("cooldown_ticks") ? obj.get("cooldown_ticks").getAsInt() : 40;
                    boolean allowVertical = obj.has("allow_vertical") && obj.get("allow_vertical").getAsBoolean();
                    boolean setVelocity = obj.has("set_velocity") && obj.get("set_velocity").getAsBoolean();
                    float damage = obj.has("damage") ? obj.get("damage").getAsFloat() : 0f;
                    float damageRadius = obj.has("damage_radius") ? obj.get("damage_radius").getAsFloat() : 2.0f;
                    float weaponScale = obj.has("weapon_damage_scale")
                        ? obj.get("weapon_damage_scale").getAsFloat() : 0f;
                    String cooldownIcon = obj.has("cooldown_icon") && obj.get("cooldown_icon").isJsonPrimitive()
                        ? obj.get("cooldown_icon").getAsString() : "";
                    boolean cooldownCountdown = !obj.has("cooldown_countdown")
                        || obj.get("cooldown_countdown").getAsBoolean();
                    boolean alwaysShowIcon = obj.has("always_show_icon") && obj.get("always_show_icon").getAsBoolean();

                    // Absent condition parses to alwaysTrue(), i.e. an ungated dash.
                    EntityCondition cond = ConditionParser.parseField(obj, "condition", t);

                    return DataResult.success(Pair.of(
                        new Config(power, cooldown, allowVertical, setVelocity, damage, damageRadius,
                            weaponScale, cond, t, cooldownIcon, cooldownCountdown, alwaysShowIcon),
                        ops.empty()));
                } catch (RuntimeException e) {
                    return DataResult.error(() -> "active_dash: malformed field: " + e.getMessage());
                }
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        // Returning false keeps the cooldown un-consumed (base-class contract), so
        // a blocked dash costs the player nothing.
        if (!config.condition().test(player)) return false;

        Vec3 look = player.getLookAngle();
        Vec3 dash = config.allowVertical()
            ? look.scale(config.power())
            : new Vec3(look.x, 0.2, look.z).normalize().scale(config.power());
        if (config.setVelocity()) {
            player.setDeltaMovement(dash);
        } else {
            player.setDeltaMovement(player.getDeltaMovement().add(dash));
        }
        player.hurtMarked = true;

        if (config.damage() > 0f || config.weaponDamageScale() > 0f) {
            dealPathDamage(player, config, dash);
        }
        return true;
    }

    /**
     * Damage every living entity within {@code damage_radius} of the dash path.
     * The dash itself is an instantaneous velocity push, so we approximate the
     * swept volume as the segment from the player's mid-height out along the
     * dash vector and hit anything whose centre is close to that segment.
     */
    private void dealPathDamage(ServerPlayer player, Config config, Vec3 dash) {
        Vec3 start = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 dir = dash.lengthSqr() < 1.0e-6 ? player.getLookAngle() : dash.normalize();
        double length = Math.max(1.0, dash.length());
        Vec3 end = start.add(dir.scale(length));
        float radius = config.damageRadius();

        float damage = config.damage();
        if (config.weaponDamageScale() > 0f) {
            double atk = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            damage += (float) (config.weaponDamageScale() * atk);
        }

        AABB box = new AABB(start, end).inflate(radius + 1.0);
        var source = player.damageSources().playerAttack(player);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == player) continue;
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (distanceToSegment(c, start, end) <= radius) {
                target.hurt(source, damage);
            }
        }
    }

    /** Shortest distance from point {@code p} to the segment [a, b]. */
    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double abLenSq = ab.lengthSqr();
        double t = abLenSq < 1.0e-6 ? 0.0 : p.subtract(a).dot(ab) / abLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        return p.distanceTo(a.add(ab.scale(t)));
    }
}
