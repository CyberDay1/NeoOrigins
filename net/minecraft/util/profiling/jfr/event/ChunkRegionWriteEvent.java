package net.minecraft.util.profiling.jfr.event;

import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

@Name("minecraft.ChunkRegionWrite")
@Label("Region File Write")
@DontObfuscate
public class ChunkRegionWriteEvent extends ChunkRegionIoEvent {
    public static final String EVENT_NAME = "minecraft.ChunkRegionWrite";
    public static final EventType TYPE = EventType.getEventType(ChunkRegionWriteEvent.class);

    public ChunkRegionWriteEvent(RegionStorageInfo p_326040_, ChunkPos p_326006_, RegionFileVersion p_326456_, int p_326368_) {
        super(p_326040_, p_326006_, p_326456_, p_326368_);
    }
}
