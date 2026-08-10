package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fired after a mob's mob-origin has been cleared via
 * {@code /neoorigins mob clear} or programmatic reset.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.mobOriginCleared(event => {
 *     console.log(`${event.mob.type} lost ${event.previousOriginId}`)
 *   })
 * </pre>
 */
public class MobOriginClearedKubeEvent implements KubeEvent {
    private final LivingEntity mob;
    private final Identifier previousOriginId;

    public MobOriginClearedKubeEvent(LivingEntity mob, Identifier previousOriginId) {
        this.mob = mob;
        this.previousOriginId = previousOriginId;
    }

    public LivingEntity getMob() { return mob; }
    public Identifier getPreviousOriginId() { return previousOriginId; }
}
