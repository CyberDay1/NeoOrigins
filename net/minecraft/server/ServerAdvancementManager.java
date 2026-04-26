package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.TreeNodePosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ServerAdvancementManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private Map<ResourceLocation, AdvancementHolder> advancements = Map.of();
    private AdvancementTree tree = new AdvancementTree();
    private final HolderLookup.Provider registries;

    public ServerAdvancementManager(HolderLookup.Provider p_323943_) {
        super(GSON, Registries.elementsDirPath(Registries.ADVANCEMENT));
        this.registries = p_323943_;
    }

    protected void apply(Map<ResourceLocation, JsonElement> p_136034_, ResourceManager p_136035_, ProfilerFiller p_136036_) {
        RegistryOps<JsonElement> registryops = this.makeConditionalOps(); // Neo: add condition context
        Builder<ResourceLocation, AdvancementHolder> builder = ImmutableMap.builder();
        p_136034_.forEach((p_337529_, p_337530_) -> {
            try {
                Advancement advancement = net.neoforged.neoforge.common.conditions.ICondition.getWithWithConditionsCodec(Advancement.CONDITIONAL_CODEC, registryops, p_337530_).orElse(null);
                if (advancement == null) {
                    LOGGER.debug("Skipping loading advancement {} as its conditions were not met", p_337529_);
                    return;
                }
                this.validate(p_337529_, advancement);
                builder.put(p_337529_, new AdvancementHolder(p_337529_, advancement));
            } catch (Exception exception) {
                LOGGER.error("Parsing error loading custom advancement {}: {}", p_337529_, exception.getMessage());
            }
        });
        this.advancements = builder.buildOrThrow();
        AdvancementTree advancementtree = new AdvancementTree();
        advancementtree.addAll(this.advancements.values());

        for (AdvancementNode advancementnode : advancementtree.roots()) {
            if (advancementnode.holder().value().display().isPresent()) {
                TreeNodePosition.run(advancementnode);
            }
        }

        this.tree = advancementtree;
    }

    private void validate(ResourceLocation p_312696_, Advancement p_312110_) {
        ProblemReporter.Collector problemreporter$collector = new ProblemReporter.Collector();
        p_312110_.validate(problemreporter$collector, this.registries.asGetterLookup());
        problemreporter$collector.getReport().ifPresent(p_344260_ -> LOGGER.warn("Found validation problems in advancement {}: \n{}", p_312696_, p_344260_));
    }

    @Nullable
    public AdvancementHolder get(ResourceLocation p_301079_) {
        return this.advancements.get(p_301079_);
    }

    public AdvancementTree tree() {
        return this.tree;
    }

    public Collection<AdvancementHolder> getAllAdvancements() {
        return this.advancements.values();
    }
}
