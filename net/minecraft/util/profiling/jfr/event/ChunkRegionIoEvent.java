package net.minecraft.util.profiling.jfr.event;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

@Category({"Minecraft", "Storage"})
@StackTrace(false)
@Enabled(false)
public abstract class ChunkRegionIoEvent extends Event {
    @Name("regionPosX")
    @Label("Region X Position")
    public final int regionPosX;
    @Name("regionPosZ")
    @Label("Region Z Position")
    public final int regionPosZ;
    @Name("localPosX")
    @Label("Local X Position")
    public final int localChunkPosX;
    @Name("localPosZ")
    @Label("Local Z Position")
    public final int localChunkPosZ;
    @Name("chunkPosX")
    @Label("Chunk X Position")
    public final int chunkPosX;
    @Name("chunkPosZ")
    @Label("Chunk Z Position")
    public final int chunkPosZ;
    @Name("level")
    @Label("Level Id")
    public final String levelId;
    @Name("dimension")
    @Label("Dimension")
    public final String dimension;
    @Name("type")
    @Label("Type")
    public final String type;
    @Name("compression")
    @Label("Compression")
    public final String compression;
    @Name("bytes")
    @Label("Bytes")
    public final int bytes;

    public ChunkRegionIoEvent(RegionStorageInfo p_326442_, ChunkPos p_326257_, RegionFileVersion p_326471_, int p_325982_) {
        this.regionPosX = p_326257_.getRegionX();
        this.regionPosZ = p_326257_.getRegionZ();
        this.localChunkPosX = p_326257_.getRegionLocalX();
        this.localChunkPosZ = p_326257_.getRegionLocalZ();
        this.chunkPosX = p_326257_.x;
        this.chunkPosZ = p_326257_.z;
        this.levelId = p_326442_.level();
        this.dimension = p_326442_.dimension().location().toString();
        this.type = p_326442_.type();
        this.compression = "standard:" + p_326471_.getId();
        this.bytes = p_325982_;
    }

    public static class Fields {
        public static final String REGION_POS_X = "regionPosX";
        public static final String REGION_POS_Z = "regionPosZ";
        public static final String LOCAL_POS_X = "localPosX";
        public static final String LOCAL_POS_Z = "localPosZ";
        public static final String CHUNK_POS_X = "chunkPosX";
        public static final String CHUNK_POS_Z = "chunkPosZ";
        public static final String LEVEL = "level";
        public static final String DIMENSION = "dimension";
        public static final String TYPE = "type";
        public static final String COMPRESSION = "compression";
        public static final String BYTES = "bytes";

        private Fields() {
        }
    }
}
