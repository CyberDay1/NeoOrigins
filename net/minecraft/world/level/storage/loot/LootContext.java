package net.minecraft.world.level.storage.loot;

import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LootContext {
    private final LootParams params;
    private final RandomSource random;
    private final HolderGetter.Provider lootDataResolver;
    private final Set<LootContext.VisitedEntry<?>> visitedElements = Sets.newLinkedHashSet();

    LootContext(LootParams p_287722_, RandomSource p_287702_, HolderGetter.Provider p_335850_) {
        this.params = p_287722_;
        this.random = p_287702_;
        this.lootDataResolver = p_335850_;
    }

    public boolean hasParam(LootContextParam<?> p_78937_) {
        return this.params.hasParam(p_78937_);
    }

    public <T> T getParam(LootContextParam<T> p_165125_) {
        return this.params.getParameter(p_165125_);
    }

    public void addDynamicDrops(ResourceLocation p_78943_, Consumer<ItemStack> p_78944_) {
        this.params.addDynamicDrops(p_78943_, p_78944_);
    }

    @Nullable
    public <T> T getParamOrNull(LootContextParam<T> p_78954_) {
        return this.params.getParamOrNull(p_78954_);
    }

    public boolean hasVisitedElement(LootContext.VisitedEntry<?> p_279182_) {
        return this.visitedElements.contains(p_279182_);
    }

    public boolean pushVisitedElement(LootContext.VisitedEntry<?> p_279152_) {
        return this.visitedElements.add(p_279152_);
    }

    public void popVisitedElement(LootContext.VisitedEntry<?> p_279198_) {
        this.visitedElements.remove(p_279198_);
    }

    public HolderGetter.Provider getResolver() {
        return this.lootDataResolver;
    }

    public RandomSource getRandom() {
        return this.random;
    }

    public float getLuck() {
        return this.params.getLuck();
    }

    public ServerLevel getLevel() {
        return this.params.getLevel();
    }

    public static LootContext.VisitedEntry<LootTable> createVisitedEntry(LootTable p_279327_) {
        return new LootContext.VisitedEntry<>(LootDataType.TABLE, p_279327_);
    }

    public static LootContext.VisitedEntry<LootItemCondition> createVisitedEntry(LootItemCondition p_279250_) {
        return new LootContext.VisitedEntry<>(LootDataType.PREDICATE, p_279250_);
    }

    public static LootContext.VisitedEntry<LootItemFunction> createVisitedEntry(LootItemFunction p_279163_) {
        return new LootContext.VisitedEntry<>(LootDataType.MODIFIER, p_279163_);
    }

    // Neo: Keep track of the original loot table ID through modifications
    @org.jetbrains.annotations.Nullable
    private ResourceLocation queriedLootTableId;

    private LootContext(LootParams p_287722_, RandomSource p_287702_, HolderGetter.Provider p_287619_, ResourceLocation queriedLootTableId) {
        this(p_287722_, p_287702_, p_287619_);
        this.queriedLootTableId = queriedLootTableId;
    }

    public void setQueriedLootTableId(@org.jetbrains.annotations.Nullable ResourceLocation queriedLootTableId) {
        if (this.queriedLootTableId == null && queriedLootTableId != null) this.queriedLootTableId = queriedLootTableId;
    }

    public ResourceLocation getQueriedLootTableId() {
        return this.queriedLootTableId == null ? net.neoforged.neoforge.common.loot.LootTableIdCondition.UNKNOWN_LOOT_TABLE : this.queriedLootTableId;
    }

    public static class Builder {
        private final LootParams params;
        @Nullable
        private RandomSource random;
        @Nullable
        private ResourceLocation queriedLootTableId; // Forge: correctly pass around loot table ID with copy constructor

        public Builder(LootParams p_287628_) {
            this.params = p_287628_;
        }

        public Builder(LootContext context) {
            this.params = context.params;
            this.random = context.random;
            this.queriedLootTableId = context.queriedLootTableId;
        }

        public LootContext.Builder withOptionalRandomSeed(long p_78966_) {
            if (p_78966_ != 0L) {
                this.random = RandomSource.create(p_78966_);
            }

            return this;
        }

        public LootContext.Builder withOptionalRandomSource(RandomSource p_347445_) {
            this.random = p_347445_;
            return this;
        }

        public LootContext.Builder withQueriedLootTableId(ResourceLocation queriedLootTableId) {
            this.queriedLootTableId = queriedLootTableId;
            return this;
        }

        public ServerLevel getLevel() {
            return this.params.getLevel();
        }

        public LootContext create(Optional<ResourceLocation> p_298622_) {
            ServerLevel serverlevel = this.getLevel();
            MinecraftServer minecraftserver = serverlevel.getServer();
            RandomSource randomsource = Optional.ofNullable(this.random)
                .or(() -> p_298622_.map(serverlevel::getRandomSequence))
                .orElseGet(serverlevel::getRandom);
            return new LootContext(this.params, randomsource, minecraftserver.reloadableRegistries().lookup(), queriedLootTableId);
        }
    }

    public static enum EntityTarget implements StringRepresentable {
        THIS("this", LootContextParams.THIS_ENTITY),
        ATTACKER("attacker", LootContextParams.ATTACKING_ENTITY),
        DIRECT_ATTACKER("direct_attacker", LootContextParams.DIRECT_ATTACKING_ENTITY),
        ATTACKING_PLAYER("attacking_player", LootContextParams.LAST_DAMAGE_PLAYER);

        public static final StringRepresentable.EnumCodec<LootContext.EntityTarget> CODEC = StringRepresentable.fromEnum(LootContext.EntityTarget::values);
        private final String name;
        private final LootContextParam<? extends Entity> param;

        private EntityTarget(String p_79001_, LootContextParam<? extends Entity> p_79002_) {
            this.name = p_79001_;
            this.param = p_79002_;
        }

        public LootContextParam<? extends Entity> getParam() {
            return this.param;
        }

        // Forge: This method is patched in to expose the same name used in getByName so that ContextNbtProvider#forEntity serializes it properly
        public String getName() {
            return this.name;
        }

        public static LootContext.EntityTarget getByName(String p_79007_) {
            LootContext.EntityTarget lootcontext$entitytarget = CODEC.byName(p_79007_);
            if (lootcontext$entitytarget != null) {
                return lootcontext$entitytarget;
            } else {
                throw new IllegalArgumentException("Invalid entity target " + p_79007_);
            }
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static record VisitedEntry<T>(LootDataType<T> type, T value) {
    }
}
