package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fired after a mob has been assigned a mob-origin via the 2.1 Mob Origin
 * System (admin {@code /neoorigins mob apply}, {@code /neoorigins mob egg},
 * or a JSON spawn rule).
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.mobOriginAssigned(event => {
 *     console.log(`${event.mob.type} got ${event.originId}`)
 *   })
 * </pre>
 */
public class MobOriginAssignedKubeEvent implements KubeEvent {
    private final LivingEntity mob;
    private final ResourceLocation originId;

    public MobOriginAssignedKubeEvent(LivingEntity mob, ResourceLocation originId) {
        this.mob = mob;
        this.originId = originId;
    }

    public LivingEntity getMob() { return mob; }
    public ResourceLocation getOriginId() { return originId; }
}
