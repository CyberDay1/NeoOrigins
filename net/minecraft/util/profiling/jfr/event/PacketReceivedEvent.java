package net.minecraft.util.profiling.jfr.event;

import java.net.SocketAddress;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import net.minecraft.obfuscate.DontObfuscate;

@Name("minecraft.PacketReceived")
@Label("Network Packet Received")
@DontObfuscate
public class PacketReceivedEvent extends PacketEvent {
    public static final String NAME = "minecraft.PacketReceived";
    public static final EventType TYPE = EventType.getEventType(PacketReceivedEvent.class);

    public PacketReceivedEvent(String p_295637_, String p_320289_, String p_320603_, SocketAddress p_195587_, int p_195585_) {
        super(p_295637_, p_320289_, p_320603_, p_195587_, p_195585_);
    }
}
