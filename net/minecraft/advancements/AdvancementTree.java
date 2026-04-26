package net.minecraft.advancements;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AdvancementTree {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<ResourceLocation, AdvancementNode> nodes = new Object2ObjectOpenHashMap<>();
    private final Set<AdvancementNode> roots = new ObjectLinkedOpenHashSet<>();
    private final Set<AdvancementNode> tasks = new ObjectLinkedOpenHashSet<>();
    @Nullable
    private AdvancementTree.Listener listener;

    private void remove(AdvancementNode p_301231_) {
        for (AdvancementNode advancementnode : p_301231_.children()) {
            this.remove(advancementnode);
        }

        LOGGER.info("Forgot about advancement {}", p_301231_.holder());
        this.nodes.remove(p_301231_.holder().id());
        if (p_301231_.parent() == null) {
            this.roots.remove(p_301231_);
            if (this.listener != null) {
                this.listener.onRemoveAdvancementRoot(p_301231_);
            }
        } else {
            this.tasks.remove(p_301231_);
            if (this.listener != null) {
                this.listener.onRemoveAdvancementTask(p_301231_);
            }
        }
    }

    public void remove(Set<ResourceLocation> p_300917_) {
        for (ResourceLocation resourcelocation : p_300917_) {
            AdvancementNode advancementnode = this.nodes.get(resourcelocation);
            if (advancementnode == null) {
                LOGGER.warn("Told to remove advancement {} but I don't know what that is", resourcelocation);
            } else {
                this.remove(advancementnode);
            }
        }
    }

    public void addAll(Collection<AdvancementHolder> p_301192_) {
        List<AdvancementHolder> list = new ArrayList<>(p_301192_);

        while (!list.isEmpty()) {
            if (!list.removeIf(this::tryInsert)) {
                LOGGER.error("Couldn't load advancements: {}", list);
                break;
            }
        }

        LOGGER.info("Loaded {} advancements", this.nodes.size());
    }

    private boolean tryInsert(AdvancementHolder p_301290_) {
        Optional<ResourceLocation> optional = p_301290_.value().parent();
        AdvancementNode advancementnode = optional.map(this.nodes::get).orElse(null);
        if (advancementnode == null && optional.isPresent()) {
            return false;
        } else {
            AdvancementNode advancementnode1 = new AdvancementNode(p_301290_, advancementnode);
            if (advancementnode != null) {
                advancementnode.addChild(advancementnode1);
            }

            this.nodes.put(p_301290_.id(), advancementnode1);
            if (advancementnode == null) {
                this.roots.add(advancementnode1);
                if (this.listener != null) {
                    this.listener.onAddAdvancementRoot(advancementnode1);
                }
            } else {
                this.tasks.add(advancementnode1);
                if (this.listener != null) {
                    this.listener.onAddAdvancementTask(advancementnode1);
                }
            }

            return true;
        }
    }

    public void clear() {
        this.nodes.clear();
        this.roots.clear();
        this.tasks.clear();
        if (this.listener != null) {
            this.listener.onAdvancementsCleared();
        }
    }

    public Iterable<AdvancementNode> roots() {
        return this.roots;
    }

    public Collection<AdvancementNode> nodes() {
        return this.nodes.values();
    }

    @Nullable
    public AdvancementNode get(ResourceLocation p_301217_) {
        return this.nodes.get(p_301217_);
    }

    @Nullable
    public AdvancementNode get(AdvancementHolder p_301246_) {
        return this.nodes.get(p_301246_.id());
    }

    public void setListener(@Nullable AdvancementTree.Listener p_301114_) {
        this.listener = p_301114_;
        if (p_301114_ != null) {
            for (AdvancementNode advancementnode : this.roots) {
                p_301114_.onAddAdvancementRoot(advancementnode);
            }

            for (AdvancementNode advancementnode1 : this.tasks) {
                p_301114_.onAddAdvancementTask(advancementnode1);
            }
        }
    }

    public interface Listener {
        void onAddAdvancementRoot(AdvancementNode p_301125_);

        void onRemoveAdvancementRoot(AdvancementNode p_300944_);

        void onAddAdvancementTask(AdvancementNode p_301259_);

        void onRemoveAdvancementTask(AdvancementNode p_301064_);

        void onAdvancementsCleared();
    }
}
