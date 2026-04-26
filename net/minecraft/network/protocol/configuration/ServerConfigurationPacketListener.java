package net.minecraft.network.protocol.configuration;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;

public interface ServerConfigurationPacketListener extends ServerCommonPacketListener, net.neoforged.neoforge.common.extensions.IServerConfigurationPacketListenerExtension {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.CONFIGURATION;
    }

    void handleConfigurationFinished(ServerboundFinishConfigurationPacket p_295218_);

    void handleSelectKnownPacks(ServerboundSelectKnownPacks p_326510_);
}
