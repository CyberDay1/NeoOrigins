package net.minecraft.world.scores;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

public class Scoreboard {
    public static final String HIDDEN_SCORE_PREFIX = "#";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Object2ObjectMap<String, Objective> objectivesByName = new Object2ObjectOpenHashMap<>(16, 0.5F);
    private final Reference2ObjectMap<ObjectiveCriteria, List<Objective>> objectivesByCriteria = new Reference2ObjectOpenHashMap<>();
    private final Map<String, PlayerScores> playerScores = new Object2ObjectOpenHashMap<>(16, 0.5F);
    private final Map<DisplaySlot, Objective> displayObjectives = new EnumMap<>(DisplaySlot.class);
    private final Object2ObjectMap<String, PlayerTeam> teamsByName = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, PlayerTeam> teamsByPlayer = new Object2ObjectOpenHashMap<>();

    @Nullable
    public Objective getObjective(@Nullable String p_83478_) {
        return this.objectivesByName.get(p_83478_);
    }

    public Objective addObjective(
        String p_83437_,
        ObjectiveCriteria p_83438_,
        Component p_83439_,
        ObjectiveCriteria.RenderType p_83440_,
        boolean p_313815_,
        @Nullable NumberFormat p_313909_
    ) {
        if (this.objectivesByName.containsKey(p_83437_)) {
            throw new IllegalArgumentException("An objective with the name '" + p_83437_ + "' already exists!");
        } else {
            Objective objective = new Objective(this, p_83437_, p_83438_, p_83439_, p_83440_, p_313815_, p_313909_);
            this.objectivesByCriteria.computeIfAbsent(p_83438_, p_314722_ -> Lists.newArrayList()).add(objective);
            this.objectivesByName.put(p_83437_, objective);
            this.onObjectiveAdded(objective);
            return objective;
        }
    }

    public final void forAllObjectives(ObjectiveCriteria p_83428_, ScoreHolder p_313805_, Consumer<ScoreAccess> p_83430_) {
        this.objectivesByCriteria
            .getOrDefault(p_83428_, Collections.emptyList())
            .forEach(p_313676_ -> p_83430_.accept(this.getOrCreatePlayerScore(p_313805_, p_313676_, true)));
    }

    private PlayerScores getOrCreatePlayerInfo(String p_313892_) {
        return this.playerScores.computeIfAbsent(p_313892_, p_313683_ -> new PlayerScores());
    }

    public ScoreAccess getOrCreatePlayerScore(ScoreHolder p_313714_, Objective p_313948_) {
        return this.getOrCreatePlayerScore(p_313714_, p_313948_, false);
    }

    public ScoreAccess getOrCreatePlayerScore(final ScoreHolder p_313717_, final Objective p_83473_, boolean p_313939_) {
        final boolean flag = p_313939_ || !p_83473_.getCriteria().isReadOnly();
        PlayerScores playerscores = this.getOrCreatePlayerInfo(p_313717_.getScoreboardName());
        final MutableBoolean mutableboolean = new MutableBoolean();
        final Score score = playerscores.getOrCreate(p_83473_, p_313682_ -> mutableboolean.setTrue());
        return new ScoreAccess() {
            @Override
            public int get() {
                return score.value();
            }

            @Override
            public void set(int p_313831_) {
                if (!flag) {
                    throw new IllegalStateException("Cannot modify read-only score");
                } else {
                    boolean flag1 = mutableboolean.isTrue();
                    if (p_83473_.displayAutoUpdate()) {
                        Component component = p_313717_.getDisplayName();
                        if (component != null && !component.equals(score.display())) {
                            score.display(component);
                            flag1 = true;
                        }
                    }

                    if (p_313831_ != score.value()) {
                        score.value(p_313831_);
                        flag1 = true;
                    }

                    if (flag1) {
                        this.sendScoreToPlayers();
                    }
                }
            }

            @Nullable
            @Override
            public Component display() {
                return score.display();
            }

            @Override
            public void display(@Nullable Component p_313826_) {
                if (mutableboolean.isTrue() || !Objects.equals(p_313826_, score.display())) {
                    score.display(p_313826_);
                    this.sendScoreToPlayers();
                }
            }

            @Override
            public void numberFormatOverride(@Nullable NumberFormat p_313875_) {
                score.numberFormat(p_313875_);
                this.sendScoreToPlayers();
            }

            @Override
            public boolean locked() {
                return score.isLocked();
            }

            @Override
            public void unlock() {
                this.setLocked(false);
            }

            @Override
            public void lock() {
                this.setLocked(true);
            }

            private void setLocked(boolean p_313822_) {
                score.setLocked(p_313822_);
                if (mutableboolean.isTrue()) {
                    this.sendScoreToPlayers();
                }

                Scoreboard.this.onScoreLockChanged(p_313717_, p_83473_);
            }

            private void sendScoreToPlayers() {
                Scoreboard.this.onScoreChanged(p_313717_, p_83473_, score);
                mutableboolean.setFalse();
            }
        };
    }

    @Nullable
    public ReadOnlyScoreInfo getPlayerScoreInfo(ScoreHolder p_313711_, Objective p_313813_) {
        PlayerScores playerscores = this.playerScores.get(p_313711_.getScoreboardName());
        return playerscores != null ? playerscores.get(p_313813_) : null;
    }

    public Collection<PlayerScoreEntry> listPlayerScores(Objective p_313802_) {
        List<PlayerScoreEntry> list = new ArrayList<>();
        this.playerScores.forEach((p_313669_, p_313670_) -> {
            Score score = p_313670_.get(p_313802_);
            if (score != null) {
                list.add(new PlayerScoreEntry(p_313669_, score.value(), score.display(), score.numberFormat()));
            }
        });
        return list;
    }

    public Collection<Objective> getObjectives() {
        return this.objectivesByName.values();
    }

    public Collection<String> getObjectiveNames() {
        return this.objectivesByName.keySet();
    }

    public Collection<ScoreHolder> getTrackedPlayers() {
        return this.playerScores.keySet().stream().map(ScoreHolder::forNameOnly).toList();
    }

    public void resetAllPlayerScores(ScoreHolder p_313823_) {
        PlayerScores playerscores = this.playerScores.remove(p_313823_.getScoreboardName());
        if (playerscores != null) {
            this.onPlayerRemoved(p_313823_);
        }
    }

    public void resetSinglePlayerScore(ScoreHolder p_313783_, Objective p_313928_) {
        PlayerScores playerscores = this.playerScores.get(p_313783_.getScoreboardName());
        if (playerscores != null) {
            boolean flag = playerscores.remove(p_313928_);
            if (!playerscores.hasScores()) {
                PlayerScores playerscores1 = this.playerScores.remove(p_313783_.getScoreboardName());
                if (playerscores1 != null) {
                    this.onPlayerRemoved(p_313783_);
                }
            } else if (flag) {
                this.onPlayerScoreRemoved(p_313783_, p_313928_);
            }
        }
    }

    public Object2IntMap<Objective> listPlayerScores(ScoreHolder p_313893_) {
        PlayerScores playerscores = this.playerScores.get(p_313893_.getScoreboardName());
        return playerscores != null ? playerscores.listScores() : Object2IntMaps.emptyMap();
    }

    public void removeObjective(Objective p_83503_) {
        this.objectivesByName.remove(p_83503_.getName());

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displayslot) == p_83503_) {
                this.setDisplayObjective(displayslot, null);
            }
        }

        List<Objective> list = this.objectivesByCriteria.get(p_83503_.getCriteria());
        if (list != null) {
            list.remove(p_83503_);
        }

        for (PlayerScores playerscores : this.playerScores.values()) {
            playerscores.remove(p_83503_);
        }

        this.onObjectiveRemoved(p_83503_);
    }

    public void setDisplayObjective(DisplaySlot p_296205_, @Nullable Objective p_83419_) {
        this.displayObjectives.put(p_296205_, p_83419_);
    }

    @Nullable
    public Objective getDisplayObjective(DisplaySlot p_295165_) {
        return this.displayObjectives.get(p_295165_);
    }

    @Nullable
    public PlayerTeam getPlayerTeam(String p_83490_) {
        return this.teamsByName.get(p_83490_);
    }

    public PlayerTeam addPlayerTeam(String p_83493_) {
        PlayerTeam playerteam = this.getPlayerTeam(p_83493_);
        if (playerteam != null) {
            LOGGER.warn("Requested creation of existing team '{}'", p_83493_);
            return playerteam;
        } else {
            playerteam = new PlayerTeam(this, p_83493_);
            this.teamsByName.put(p_83493_, playerteam);
            this.onTeamAdded(playerteam);
            return playerteam;
        }
    }

    public void removePlayerTeam(PlayerTeam p_83476_) {
        this.teamsByName.remove(p_83476_.getName());

        for (String s : p_83476_.getPlayers()) {
            this.teamsByPlayer.remove(s);
        }

        this.onTeamRemoved(p_83476_);
    }

    public boolean addPlayerToTeam(String p_83434_, PlayerTeam p_83435_) {
        if (this.getPlayersTeam(p_83434_) != null) {
            this.removePlayerFromTeam(p_83434_);
        }

        this.teamsByPlayer.put(p_83434_, p_83435_);
        return p_83435_.getPlayers().add(p_83434_);
    }

    public boolean removePlayerFromTeam(String p_83496_) {
        PlayerTeam playerteam = this.getPlayersTeam(p_83496_);
        if (playerteam != null) {
            this.removePlayerFromTeam(p_83496_, playerteam);
            return true;
        } else {
            return false;
        }
    }

    public void removePlayerFromTeam(String p_83464_, PlayerTeam p_83465_) {
        if (this.getPlayersTeam(p_83464_) != p_83465_) {
            throw new IllegalStateException("Player is either on another team or not on any team. Cannot remove from team '" + p_83465_.getName() + "'.");
        } else {
            this.teamsByPlayer.remove(p_83464_);
            p_83465_.getPlayers().remove(p_83464_);
        }
    }

    public Collection<String> getTeamNames() {
        return this.teamsByName.keySet();
    }

    public Collection<PlayerTeam> getPlayerTeams() {
        return this.teamsByName.values();
    }

    @Nullable
    public PlayerTeam getPlayersTeam(String p_83501_) {
        return this.teamsByPlayer.get(p_83501_);
    }

    public void onObjectiveAdded(Objective p_83422_) {
    }

    public void onObjectiveChanged(Objective p_83455_) {
    }

    public void onObjectiveRemoved(Objective p_83467_) {
    }

    protected void onScoreChanged(ScoreHolder p_313729_, Objective p_313930_, Score p_83424_) {
    }

    protected void onScoreLockChanged(ScoreHolder p_313727_, Objective p_313708_) {
    }

    public void onPlayerRemoved(ScoreHolder p_313917_) {
    }

    public void onPlayerScoreRemoved(ScoreHolder p_313899_, Objective p_83433_) {
    }

    public void onTeamAdded(PlayerTeam p_83423_) {
    }

    public void onTeamChanged(PlayerTeam p_83456_) {
    }

    public void onTeamRemoved(PlayerTeam p_83468_) {
    }

    public void entityRemoved(Entity p_83421_) {
        if (!(p_83421_ instanceof Player) && !p_83421_.isAlive()) {
            this.resetAllPlayerScores(p_83421_);
            this.removePlayerFromTeam(p_83421_.getScoreboardName());
        }
    }

    protected ListTag savePlayerScores(HolderLookup.Provider p_331535_) {
        ListTag listtag = new ListTag();
        this.playerScores.forEach((p_330203_, p_330204_) -> p_330204_.listRawScores().forEach((p_330199_, p_330200_) -> {
                CompoundTag compoundtag = p_330200_.write(p_331535_);
                compoundtag.putString("Name", p_330203_);
                compoundtag.putString("Objective", p_330199_.getName());
                listtag.add(compoundtag);
            }));
        return listtag;
    }

    protected void loadPlayerScores(ListTag p_83446_, HolderLookup.Provider p_330625_) {
        for (int i = 0; i < p_83446_.size(); i++) {
            CompoundTag compoundtag = p_83446_.getCompound(i);
            Score score = Score.read(compoundtag, p_330625_);
            String s = compoundtag.getString("Name");
            String s1 = compoundtag.getString("Objective");
            Objective objective = this.getObjective(s1);
            if (objective == null) {
                LOGGER.error("Unknown objective {} for name {}, ignoring", s1, s);
            } else {
                this.getOrCreatePlayerInfo(s).setScore(objective, score);
            }
        }
    }
}
