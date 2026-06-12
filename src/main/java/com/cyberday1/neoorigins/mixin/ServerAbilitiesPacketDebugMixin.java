package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.config.AdminConfig;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pure diagnostics, no behavior change: when the {@code debug_hud} admin
 * config flag is on, logs every client→server flight-toggle packet and the
 * server's verdict. Vanilla's handler sets
 * {@code flying = packet.isFlying() && mayfly}, so the log shows exactly
 * whether a "spam jump grants flight" report came from the server still
 * advertising {@code mayfly=true} (NeoOrigins failed to clear it) or from a
 * client-only desync. Logs only on the FLY-ON request path to avoid spam.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerAbilitiesPacketDebugMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerAbilities", at = @At("TAIL"))
    private void neoorigins$logAbilitiesPacket(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (!packet.isFlying() || !AdminConfig.isDebugHud()) return;
        var abilities = player.getAbilities();
        NeoOrigins.LOGGER.info(
            "[debug_hud] abilities packet from {}: wantFlying=true, server mayfly={} -> flying={} ({})",
            player.getName().getString(), abilities.mayfly, abilities.flying,
            abilities.flying ? "ACCEPTED" : "REJECTED");
    }
}
