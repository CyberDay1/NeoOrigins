package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.net.SocketAddress;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.slf4j.Logger;

public interface JvmProfiler {
    JvmProfiler INSTANCE = (JvmProfiler)(Runtime.class.getModule().getLayer().findModule("jdk.jfr").isPresent()
        ? JfrProfiler.getInstance()
        : new JvmProfiler.NoOpProfiler());

    boolean start(Environment p_185347_);

    Path stop();

    boolean isRunning();

    boolean isAvailable();

    void onServerTick(float p_185342_);

    void onPacketReceived(ConnectionProtocol p_294356_, PacketType<?> p_320354_, SocketAddress p_185345_, int p_185343_);

    void onPacketSent(ConnectionProtocol p_295578_, PacketType<?> p_320775_, SocketAddress p_185353_, int p_185351_);

    void onRegionFileRead(RegionStorageInfo p_325986_, ChunkPos p_326233_, RegionFileVersion p_325994_, int p_326396_);

    void onRegionFileWrite(RegionStorageInfo p_326304_, ChunkPos p_326296_, RegionFileVersion p_326215_, int p_326501_);

    @Nullable
    ProfiledDuration onWorldLoadedStarted();

    @Nullable
    ProfiledDuration onChunkGenerate(ChunkPos p_185348_, ResourceKey<Level> p_185349_, String p_185350_);

    public static class NoOpProfiler implements JvmProfiler {
        private static final Logger LOGGER = LogUtils.getLogger();
        static final ProfiledDuration noOpCommit = () -> {
        };

        @Override
        public boolean start(Environment p_185368_) {
            LOGGER.warn("Attempted to start Flight Recorder, but it's not supported on this JVM");
            return false;
        }

        @Override
        public Path stop() {
            throw new IllegalStateException("Attempted to stop Flight Recorder, but it's not supported on this JVM");
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void onPacketReceived(ConnectionProtocol p_296240_, PacketType<?> p_320930_, SocketAddress p_185365_, int p_185363_) {
        }

        @Override
        public void onPacketSent(ConnectionProtocol p_294630_, PacketType<?> p_319941_, SocketAddress p_185377_, int p_185375_) {
        }

        @Override
        public void onRegionFileRead(RegionStorageInfo p_326355_, ChunkPos p_325955_, RegionFileVersion p_326373_, int p_326370_) {
        }

        @Override
        public void onRegionFileWrite(RegionStorageInfo p_326307_, ChunkPos p_326173_, RegionFileVersion p_325998_, int p_326021_) {
        }

        @Override
        public void onServerTick(float p_185361_) {
        }

        @Override
        public ProfiledDuration onWorldLoadedStarted() {
            return noOpCommit;
        }

        @Nullable
        @Override
        public ProfiledDuration onChunkGenerate(ChunkPos p_185370_, ResourceKey<Level> p_185371_, String p_185372_) {
            return null;
        }
    }
}
