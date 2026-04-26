package net.minecraft.util.profiling.jfr.stats;

import jdk.jfr.consumer.RecordedEvent;

public record PacketIdentification(String direction, String protocolId, String packetId) {
    public static PacketIdentification from(RecordedEvent p_326266_) {
        return new PacketIdentification(p_326266_.getString("packetDirection"), p_326266_.getString("protocolId"), p_326266_.getString("packetId"));
    }
}
