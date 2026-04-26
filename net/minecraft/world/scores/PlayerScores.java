package net.minecraft.world.scores;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;

class PlayerScores {
    private final Reference2ObjectOpenHashMap<Objective, Score> scores = new Reference2ObjectOpenHashMap<>(16, 0.5F);

    @Nullable
    public Score get(Objective p_313840_) {
        return this.scores.get(p_313840_);
    }

    public Score getOrCreate(Objective p_313864_, Consumer<Score> p_313800_) {
        return this.scores.computeIfAbsent(p_313864_, p_314724_ -> {
            Score score = new Score();
            p_313800_.accept(score);
            return score;
        });
    }

    public boolean remove(Objective p_313911_) {
        return this.scores.remove(p_313911_) != null;
    }

    public boolean hasScores() {
        return !this.scores.isEmpty();
    }

    public Object2IntMap<Objective> listScores() {
        Object2IntMap<Objective> object2intmap = new Object2IntOpenHashMap<>();
        this.scores.forEach((p_313743_, p_313919_) -> object2intmap.put(p_313743_, p_313919_.value()));
        return object2intmap;
    }

    void setScore(Objective p_313733_, Score p_313927_) {
        this.scores.put(p_313733_, p_313927_);
    }

    Map<Objective, Score> listRawScores() {
        return Collections.unmodifiableMap(this.scores);
    }
}
