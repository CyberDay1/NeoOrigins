package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class JukeboxTicksSinceSongStartedFix extends NamedEntityFix {
    public JukeboxTicksSinceSongStartedFix(Schema p_351035_) {
        super(p_351035_, false, "JukeboxTicksSinceSongStartedFix", References.BLOCK_ENTITY, "minecraft:jukebox");
    }

    public Dynamic<?> fixTag(Dynamic<?> p_350721_) {
        long i = p_350721_.get("TickCount").asLong(0L) - p_350721_.get("RecordStartTick").asLong(0L);
        Dynamic<?> dynamic = p_350721_.remove("IsPlaying").remove("TickCount").remove("RecordStartTick");
        return i > 0L ? dynamic.set("ticks_since_song_started", p_350721_.createLong(i)) : dynamic;
    }

    @Override
    protected Typed<?> fix(Typed<?> p_351057_) {
        return p_351057_.update(DSL.remainderFinder(), this::fixTag);
    }
}
