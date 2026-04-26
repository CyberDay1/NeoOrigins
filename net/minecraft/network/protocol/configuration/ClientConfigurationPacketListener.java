package net.minecraft.network.protocol.configuration;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;

public interface ClientConfigurationPacketListener extends ClientCommonPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.CONFIGURATION;
    }

    void handleConfigurationFinished(ClientboundFinishConfigurationPacket p_295371_);

    void handleRegistryData(ClientboundRegistryDataPacket p_295674_);

    void handleEnabledFeatures(ClientboundUpdateEnabledFeaturesPacket p_296241_);

    void handleSelectKnownPacks(ClientboundSelectKnownPacks p_326313_);

    void handleResetChat(ClientboundResetChatPacket p_338667_);
}
