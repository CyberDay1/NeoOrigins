package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires the KubeJS {@code mountEnded} event for every code path that
 * removes a passenger from its vehicle — vanilla sneak-dismount, vehicle
 * death, server stop, world unload, force-mount swap onto a new vehicle,
 * and the {@code neoorigins:mount} power's own toggle/onRevoked path.
 *
 * <p>Only fires for {@link ServerPlayer} passengers that were mounted via
 * the mount power (signalled by a non-empty {@code mountPosition}
 * attachment). This keeps {@code mountStarted} / {@code mountEnded}
 * symmetric: both are scoped to mounts that originated from our system,
 * so KubeJS scripts don't have to filter out vanilla horse dismounts.
 *
 * <p>The attachment is cleared after firing so subsequent dismount-like
 * events (e.g. respawn) don't re-fire.
 */
@Mixin(Entity.class)
public abstract class EntityMountEndMixin {

    @Inject(
        method = "removePassenger(Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD")
    )
    private void neoorigins$fireMountEnded(Entity passenger, CallbackInfo ci) {
        if (!(passenger instanceof ServerPlayer sp)) return;
        if (!sp.hasData(EntityAttachments.mountPosition())) return;
        String position = sp.getData(EntityAttachments.mountPosition());
        if (position == null || position.isEmpty()) return;

        Entity vehicle = (Entity) (Object) this;
        KubeJSEventBridge.fireMountEnded(sp, vehicle);

        // Clear the marker so we don't re-fire on subsequent removePassenger
        // calls (e.g. force-mount swap clears the new vehicle's passenger list
        // before adding ours).
        sp.setData(EntityAttachments.mountPosition(), "");
    }
}
