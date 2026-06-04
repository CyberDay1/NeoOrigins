package com.cyberday1.neoorigins.compat.top;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import mcjty.theoneprobe.api.IProbeHitEntityData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoEntityProvider;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The One Probe entity provider — soft dependency, dormant if TOP is absent.
 *
 * <p>Shows the looked-at living entity's NeoOrigins mob origin in the probe
 * overlay. All TOP-typed code lives in this {@code compat/top} package so it is
 * only classloaded when TOP is present; {@code NeoOrigins} registers it through
 * a lambda (see {@link TopIntegration}) and never imports TOP types directly.
 */
public class NeoOriginsTopProvider implements IProbeInfoEntityProvider {

    private static final String ID = NeoOrigins.MOD_ID + ":origin";

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public void addProbeEntityInfo(ProbeMode mode, IProbeInfo probeInfo, Player player,
                                   Level level, Entity entity, IProbeHitEntityData data) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        Optional<ResourceLocation> origin =
            living.getData(EntityAttachments.mobOriginData()).getOriginId();
        origin.ifPresent(id ->
            probeInfo.text(Component.translatable("tooltip.neoorigins.origin", id.toString())));
    }
}
