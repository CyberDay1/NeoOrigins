package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
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
    private final ResourceLocation previousOriginId;

    public MobOriginClearedKubeEvent(LivingEntity mob, ResourceLocation previousOriginId) {
        this.mob = mob;
        this.previousOriginId = previousOriginId;
    }

    public LivingEntity getMob() { return mob; }
    public ResourceLocation getPreviousOriginId() { return previousOriginId; }
}
