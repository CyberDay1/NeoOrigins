package net.minecraft.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

public class PlayerAdvancements {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final PlayerList playerList;
    private final Path playerSavePath;
    private AdvancementTree tree;
    private final Map<AdvancementHolder, AdvancementProgress> progress = new LinkedHashMap<>();
    private final Set<AdvancementHolder> visible = new HashSet<>();
    private final Set<AdvancementHolder> progressChanged = new HashSet<>();
    private final Set<AdvancementNode> rootsToUpdate = new HashSet<>();
    private ServerPlayer player;
    @Nullable
    private AdvancementHolder lastSelectedTab;
    private boolean isFirstPacket = true;
    private final Codec<PlayerAdvancements.Data> codec;

    public PlayerAdvancements(DataFixer p_265655_, PlayerList p_265703_, ServerAdvancementManager p_265166_, Path p_265268_, ServerPlayer p_265673_) {
        this.playerList = p_265703_;
        this.playerSavePath = p_265268_;
        this.player = p_265673_;
        this.tree = p_265166_.tree();
        int i = 1343;
        this.codec = DataFixTypes.ADVANCEMENTS.wrapCodec(PlayerAdvancements.Data.CODEC, p_265655_, 1343);
        this.load(p_265166_);
    }

    public void setPlayer(ServerPlayer p_135980_) {
        this.player = p_135980_;
    }

    public void stopListening() {
        for (CriterionTrigger<?> criteriontrigger : BuiltInRegistries.TRIGGER_TYPES) {
            criteriontrigger.removePlayerListeners(this);
        }
    }

    public void reload(ServerAdvancementManager p_135982_) {
        this.stopListening();
        this.progress.clear();
        this.visible.clear();
        this.rootsToUpdate.clear();
        this.progressChanged.clear();
        this.isFirstPacket = true;
        this.lastSelectedTab = null;
        this.tree = p_135982_.tree();
        this.load(p_135982_);
    }

    private void registerListeners(ServerAdvancementManager p_135995_) {
        for (AdvancementHolder advancementholder : p_135995_.getAllAdvancements()) {
            this.registerListeners(advancementholder);
        }
    }

    private void checkForAutomaticTriggers(ServerAdvancementManager p_136003_) {
        for (AdvancementHolder advancementholder : p_136003_.getAllAdvancements()) {
            Advancement advancement = advancementholder.value();
            if (advancement.criteria().isEmpty()) {
                this.award(advancementholder, "");
                advancement.rewards().grant(this.player);
            }
        }
    }

    private void load(ServerAdvancementManager p_136007_) {
        if (Files.isRegularFile(this.playerSavePath)) {
            try (JsonReader jsonreader = new JsonReader(Files.newBufferedReader(this.playerSavePath, StandardCharsets.UTF_8))) {
                jsonreader.setLenient(false);
                JsonElement jsonelement = Streams.parse(jsonreader);
                PlayerAdvancements.Data playeradvancements$data = this.codec.parse(JsonOps.INSTANCE, jsonelement).getOrThrow(JsonParseException::new);
                this.applyFrom(p_136007_, playeradvancements$data);
            } catch (JsonIOException | IOException ioexception) {
                LOGGER.error("Couldn't access player advancements in {}", this.playerSavePath, ioexception);
            } catch (JsonParseException jsonparseexception) {
                LOGGER.error("Couldn't parse player advancements in {}", this.playerSavePath, jsonparseexception);
            }
        }

        this.checkForAutomaticTriggers(p_136007_);
        this.registerListeners(p_136007_);
    }

    public void save() {
        JsonElement jsonelement = this.codec.encodeStart(JsonOps.INSTANCE, this.asData()).getOrThrow();

        try {
            FileUtil.createDirectoriesSafe(this.playerSavePath.getParent());

            try (Writer writer = Files.newBufferedWriter(this.playerSavePath, StandardCharsets.UTF_8)) {
                GSON.toJson(jsonelement, GSON.newJsonWriter(writer));
            }
        } catch (JsonIOException | IOException ioexception) {
            LOGGER.error("Couldn't save player advancements to {}", this.playerSavePath, ioexception);
        }
    }

    private void applyFrom(ServerAdvancementManager p_301166_, PlayerAdvancements.Data p_301198_) {
        p_301198_.forEach((p_300732_, p_300733_) -> {
            AdvancementHolder advancementholder = p_301166_.get(p_300732_);
            if (advancementholder == null) {
                LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?", p_300732_, this.playerSavePath);
            } else {
                this.startProgress(advancementholder, p_300733_);
                this.progressChanged.add(advancementholder);
                this.markForVisibilityUpdate(advancementholder);
            }
        });
    }

    private PlayerAdvancements.Data asData() {
        Map<ResourceLocation, AdvancementProgress> map = new LinkedHashMap<>();
        this.progress.forEach((p_300724_, p_300725_) -> {
            if (p_300725_.hasProgress()) {
                map.put(p_300724_.id(), p_300725_);
            }
        });
        return new PlayerAdvancements.Data(map);
    }

    public boolean award(AdvancementHolder p_300979_, String p_135990_) {
        // Forge: don't grant advancements for fake players
        if (this.player instanceof net.neoforged.neoforge.common.util.FakePlayer) return false;
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(p_300979_);
        boolean flag1 = advancementprogress.isDone();
        if (advancementprogress.grantProgress(p_135990_)) {
            this.unregisterListeners(p_300979_);
            this.progressChanged.add(p_300979_);
            flag = true;
            net.neoforged.neoforge.event.EventHooks.onAdvancementProgressedEvent(this.player, p_300979_, advancementprogress, p_135990_, net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT);
            if (!flag1 && advancementprogress.isDone()) {
                p_300979_.value().rewards().grant(this.player);
                p_300979_.value().display().ifPresent(p_352686_ -> {
                    if (p_352686_.shouldAnnounceChat() && this.player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
                        this.playerList.broadcastSystemMessage(p_352686_.getType().createAnnouncement(p_300979_, this.player), false);
                    }
                    net.neoforged.neoforge.event.EventHooks.onAdvancementEarnedEvent(this.player, p_300979_);
                });
            }
        }

        if (!flag1 && advancementprogress.isDone()) {
            this.markForVisibilityUpdate(p_300979_);
        }

        return flag;
    }

    public boolean revoke(AdvancementHolder p_301073_, String p_136000_) {
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(p_301073_);
        boolean flag1 = advancementprogress.isDone();
        if (advancementprogress.revokeProgress(p_136000_)) {
            this.registerListeners(p_301073_);
            this.progressChanged.add(p_301073_);
            flag = true;
            net.neoforged.neoforge.event.EventHooks.onAdvancementProgressedEvent(this.player, p_301073_, advancementprogress, p_136000_, net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent.ProgressType.REVOKE);
        }

        if (flag1 && !advancementprogress.isDone()) {
            this.markForVisibilityUpdate(p_301073_);
        }

        return flag;
    }

    private void markForVisibilityUpdate(AdvancementHolder p_301097_) {
        AdvancementNode advancementnode = this.tree.get(p_301097_);
        if (advancementnode != null) {
            this.rootsToUpdate.add(advancementnode.root());
        }
    }

    private void registerListeners(AdvancementHolder p_301053_) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(p_301053_);
        if (!advancementprogress.isDone()) {
            for (Entry<String, Criterion<?>> entry : p_301053_.value().criteria().entrySet()) {
                CriterionProgress criterionprogress = advancementprogress.getCriterion(entry.getKey());
                if (criterionprogress != null && !criterionprogress.isDone()) {
                    this.registerListener(p_301053_, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private <T extends CriterionTriggerInstance> void registerListener(AdvancementHolder p_301059_, String p_300946_, Criterion<T> p_300887_) {
        p_300887_.trigger().addPlayerListener(this, new CriterionTrigger.Listener<>(p_300887_.triggerInstance(), p_301059_, p_300946_));
    }

    private void unregisterListeners(AdvancementHolder p_301082_) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(p_301082_);

        for (Entry<String, Criterion<?>> entry : p_301082_.value().criteria().entrySet()) {
            CriterionProgress criterionprogress = advancementprogress.getCriterion(entry.getKey());
            if (criterionprogress != null && (criterionprogress.isDone() || advancementprogress.isDone())) {
                this.removeListener(p_301082_, entry.getKey(), entry.getValue());
            }
        }
    }

    private <T extends CriterionTriggerInstance> void removeListener(AdvancementHolder p_301213_, String p_301253_, Criterion<T> p_301311_) {
        p_301311_.trigger().removePlayerListener(this, new CriterionTrigger.Listener<>(p_301311_.triggerInstance(), p_301213_, p_301253_));
    }

    public void flushDirty(ServerPlayer p_135993_) {
        if (this.isFirstPacket || !this.rootsToUpdate.isEmpty() || !this.progressChanged.isEmpty()) {
            Map<ResourceLocation, AdvancementProgress> map = new HashMap<>();
            Set<AdvancementHolder> set = new HashSet<>();
            Set<ResourceLocation> set1 = new HashSet<>();

            for (AdvancementNode advancementnode : this.rootsToUpdate) {
                this.updateTreeVisibility(advancementnode, set, set1);
            }

            this.rootsToUpdate.clear();

            for (AdvancementHolder advancementholder : this.progressChanged) {
                if (this.visible.contains(advancementholder)) {
                    map.put(advancementholder.id(), this.progress.get(advancementholder));
                }
            }

            this.progressChanged.clear();
            if (!map.isEmpty() || !set.isEmpty() || !set1.isEmpty()) {
                p_135993_.connection.send(new ClientboundUpdateAdvancementsPacket(this.isFirstPacket, set, set1, map));
            }
        }

        this.isFirstPacket = false;
    }

    public void setSelectedTab(@Nullable AdvancementHolder p_301180_) {
        AdvancementHolder advancementholder = this.lastSelectedTab;
        if (p_301180_ != null && p_301180_.value().isRoot() && p_301180_.value().display().isPresent()) {
            this.lastSelectedTab = p_301180_;
        } else {
            this.lastSelectedTab = null;
        }

        if (advancementholder != this.lastSelectedTab) {
            this.player.connection.send(new ClientboundSelectAdvancementsTabPacket(this.lastSelectedTab == null ? null : this.lastSelectedTab.id()));
        }
    }

    public AdvancementProgress getOrStartProgress(AdvancementHolder p_301185_) {
        AdvancementProgress advancementprogress = this.progress.get(p_301185_);
        if (advancementprogress == null) {
            advancementprogress = new AdvancementProgress();
            this.startProgress(p_301185_, advancementprogress);
        }

        return advancementprogress;
    }

    private void startProgress(AdvancementHolder p_300931_, AdvancementProgress p_135987_) {
        p_135987_.update(p_300931_.value().requirements());
        this.progress.put(p_300931_, p_135987_);
    }

    private void updateTreeVisibility(AdvancementNode p_301178_, Set<AdvancementHolder> p_265206_, Set<ResourceLocation> p_265593_) {
        AdvancementVisibilityEvaluator.evaluateVisibility(
            p_301178_, p_300726_ -> this.getOrStartProgress(p_300726_.holder()).isDone(), (p_300729_, p_300730_) -> {
                AdvancementHolder advancementholder = p_300729_.holder();
                if (p_300730_) {
                    if (this.visible.add(advancementholder)) {
                        p_265206_.add(advancementholder);
                        if (this.progress.containsKey(advancementholder)) {
                            this.progressChanged.add(advancementholder);
                        }
                    }
                } else if (this.visible.remove(advancementholder)) {
                    p_265593_.add(advancementholder.id());
                }
            }
        );
    }

    static record Data(Map<ResourceLocation, AdvancementProgress> map) {
        public static final Codec<PlayerAdvancements.Data> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, AdvancementProgress.CODEC)
            .xmap(PlayerAdvancements.Data::new, PlayerAdvancements.Data::map);

        public void forEach(BiConsumer<ResourceLocation, AdvancementProgress> p_300973_) {
            this.map.entrySet().stream().sorted(Entry.comparingByValue()).forEach(p_301323_ -> p_300973_.accept(p_301323_.getKey(), p_301323_.getValue()));
        }
    }
}
