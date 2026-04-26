package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class JukeboxSongPlayer {
    public static final int PLAY_EVENT_INTERVAL_TICKS = 20;
    private long ticksSinceSongStarted;
    @Nullable
    private Holder<JukeboxSong> song;
    private final BlockPos blockPos;
    private final JukeboxSongPlayer.OnSongChanged onSongChanged;

    public JukeboxSongPlayer(JukeboxSongPlayer.OnSongChanged p_350439_, BlockPos p_350395_) {
        this.onSongChanged = p_350439_;
        this.blockPos = p_350395_;
    }

    public boolean isPlaying() {
        return this.song != null;
    }

    @Nullable
    public JukeboxSong getSong() {
        return this.song == null ? null : this.song.value();
    }

    public long getTicksSinceSongStarted() {
        return this.ticksSinceSongStarted;
    }

    public void setSongWithoutPlaying(Holder<JukeboxSong> p_350889_, long p_350896_) {
        if (!p_350889_.value().hasFinished(p_350896_)) {
            this.song = p_350889_;
            this.ticksSinceSongStarted = p_350896_;
        }
    }

    public void play(LevelAccessor p_350839_, Holder<JukeboxSong> p_350771_) {
        this.song = p_350771_;
        this.ticksSinceSongStarted = 0L;
        int i = p_350839_.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).getId(this.song.value());
        p_350839_.levelEvent(null, 1010, this.blockPos, i);
        this.onSongChanged.notifyChange();
    }

    public void stop(LevelAccessor p_350694_, @Nullable BlockState p_350611_) {
        if (this.song != null) {
            this.song = null;
            this.ticksSinceSongStarted = 0L;
            p_350694_.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, this.blockPos, GameEvent.Context.of(p_350611_));
            p_350694_.levelEvent(1011, this.blockPos, 0);
            this.onSongChanged.notifyChange();
        }
    }

    public void tick(LevelAccessor p_350845_, @Nullable BlockState p_350953_) {
        if (this.song != null) {
            if (this.song.value().hasFinished(this.ticksSinceSongStarted)) {
                this.stop(p_350845_, p_350953_);
            } else {
                if (this.shouldEmitJukeboxPlayingEvent()) {
                    p_350845_.gameEvent(GameEvent.JUKEBOX_PLAY, this.blockPos, GameEvent.Context.of(p_350953_));
                    spawnMusicParticles(p_350845_, this.blockPos);
                }

                this.ticksSinceSongStarted++;
            }
        }
    }

    private boolean shouldEmitJukeboxPlayingEvent() {
        return this.ticksSinceSongStarted % 20L == 0L;
    }

    private static void spawnMusicParticles(LevelAccessor p_350908_, BlockPos p_350387_) {
        if (p_350908_ instanceof ServerLevel serverlevel) {
            Vec3 vec3 = Vec3.atBottomCenterOf(p_350387_).add(0.0, 1.2F, 0.0);
            float f = (float)p_350908_.getRandom().nextInt(4) / 24.0F;
            serverlevel.sendParticles(ParticleTypes.NOTE, vec3.x(), vec3.y(), vec3.z(), 0, (double)f, 0.0, 0.0, 1.0);
        }
    }

    @FunctionalInterface
    public interface OnSongChanged {
        void notifyChange();
    }
}
