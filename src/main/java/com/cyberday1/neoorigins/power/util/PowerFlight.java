package com.cyberday1.neoorigins.power.util;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Grants power flight through NeoForge's {@code creative_flight} attribute
 * instead of {@code Abilities.mayfly}.
 *
 * <p>Other mods clear {@code mayfly} on survival players every tick (Create
 * Buzzy Beez does), which used to cancel our flight. {@code Player.mayFly()}
 * reads the field OR the attribute, and both the server's abilities handler and
 * the client's double-tap check go through it, so leaving the field alone puts
 * us out of their reach.
 *
 * <p>Modifiers are transient (never saved) and keyed per power, so revoking
 * one power's flight leaves another's in place.
 */
public final class PowerFlight {

    private PowerFlight() {}

    static final String PREFIX = "power_flight";

    /** Modifier id for a power: {@code neoorigins:power_flight/<ns>/<path>}. */
    public static Identifier modifierId(@Nullable Identifier powerId) {
        if (powerId == null) return Identifier.fromNamespaceAndPath("neoorigins", PREFIX);
        return Identifier.fromNamespaceAndPath("neoorigins",
            PREFIX + "/" + powerId.getNamespace() + "/" + powerId.getPath());
    }

    static boolean isOurs(Identifier id) {
        return id.getNamespace().equals("neoorigins")
            && (id.getPath().equals(PREFIX) || id.getPath().startsWith(PREFIX + "/"));
    }

    /** Adds the power's modifier. Returns true if it was not already there. */
    public static boolean grant(AttributeInstance inst, @Nullable Identifier powerId) {
        Identifier id = modifierId(powerId);
        if (inst.hasModifier(id)) return false;
        inst.addTransientModifier(new AttributeModifier(id, 1.0, AttributeModifier.Operation.ADD_VALUE));
        return true;
    }

    /**
     * Removes the power's modifier. A null id (caller outside a power dispatch)
     * removes every NeoOrigins flight modifier. Returns true if any was removed.
     */
    public static boolean revoke(AttributeInstance inst, @Nullable Identifier powerId) {
        if (powerId != null) return inst.removeModifier(modifierId(powerId));
        List<Identifier> ours = new ArrayList<>();
        for (AttributeModifier m : inst.getModifiers()) {
            if (isOurs(m.id())) ours.add(m.id());
        }
        ours.forEach(inst::removeModifier);
        return !ours.isEmpty();
    }

    public static boolean hasAny(AttributeInstance inst) {
        for (AttributeModifier m : inst.getModifiers()) {
            if (isOurs(m.id())) return true;
        }
        return false;
    }

    public static boolean grant(Player player, @Nullable Identifier powerId) {
        AttributeInstance inst = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        return inst != null && grant(inst, powerId);
    }

    /** True if any NeoOrigins power is currently granting this player flight. */
    public static boolean hasAny(Player player) {
        AttributeInstance inst = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        return inst != null && hasAny(inst);
    }

    /**
     * Removes the power's flight. If that leaves the player unable to fly, drops
     * them out of flight now and clears fall distance so they land safely.
     * Creative/spectator keep flying: their {@code mayfly} is never touched.
     */
    public static boolean revoke(ServerPlayer player, @Nullable Identifier powerId) {
        AttributeInstance inst = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (inst == null || !revoke(inst, powerId)) return false;
        var abilities = player.getAbilities();
        if (abilities.flying && !player.mayFly()) {
            abilities.flying = false;
            player.onUpdateAbilities();
        }
        player.fallDistance = 0.0F;
        return true;
    }
}
