package net.minecraft.world;

import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public interface RandomizableContainer extends Container {
    String LOOT_TABLE_TAG = "LootTable";
    String LOOT_TABLE_SEED_TAG = "LootTableSeed";

    @Nullable
    ResourceKey<LootTable> getLootTable();

    void setLootTable(@Nullable ResourceKey<LootTable> p_335578_);

    default void setLootTable(ResourceKey<LootTable> p_335762_, long p_335967_) {
        this.setLootTable(p_335762_);
        this.setLootTableSeed(p_335967_);
    }

    long getLootTableSeed();

    void setLootTableSeed(long p_309559_);

    BlockPos getBlockPos();

    @Nullable
    Level getLevel();

    static void setBlockEntityLootTable(BlockGetter p_309623_, RandomSource p_309643_, BlockPos p_309644_, ResourceKey<LootTable> p_335924_) {
        if (p_309623_.getBlockEntity(p_309644_) instanceof RandomizableContainer randomizablecontainer) {
            randomizablecontainer.setLootTable(p_335924_, p_309643_.nextLong());
        }
    }

    default boolean tryLoadLootTable(CompoundTag p_309695_) {
        if (p_309695_.contains("LootTable", 8)) {
            this.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(p_309695_.getString("LootTable"))));
            if (p_309695_.contains("LootTableSeed", 4)) {
                this.setLootTableSeed(p_309695_.getLong("LootTableSeed"));
            } else {
                this.setLootTableSeed(0L);
            }

            return true;
        } else {
            return false;
        }
    }

    default boolean trySaveLootTable(CompoundTag p_309634_) {
        ResourceKey<LootTable> resourcekey = this.getLootTable();
        if (resourcekey == null) {
            return false;
        } else {
            p_309634_.putString("LootTable", resourcekey.location().toString());
            long i = this.getLootTableSeed();
            if (i != 0L) {
                p_309634_.putLong("LootTableSeed", i);
            }

            return true;
        }
    }

    default void unpackLootTable(@Nullable Player p_309628_) {
        Level level = this.getLevel();
        BlockPos blockpos = this.getBlockPos();
        ResourceKey<LootTable> resourcekey = this.getLootTable();
        if (resourcekey != null && level != null && level.getServer() != null) {
            LootTable loottable = level.getServer().reloadableRegistries().getLootTable(resourcekey);
            if (p_309628_ instanceof ServerPlayer) {
                CriteriaTriggers.GENERATE_LOOT.trigger((ServerPlayer)p_309628_, resourcekey);
            }

            this.setLootTable(null);
            LootParams.Builder lootparams$builder = new LootParams.Builder((ServerLevel)level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockpos));
            if (p_309628_ != null) {
                lootparams$builder.withLuck(p_309628_.getLuck()).withParameter(LootContextParams.THIS_ENTITY, p_309628_);
            }

            loottable.fill(this, lootparams$builder.create(LootContextParamSets.CHEST), this.getLootTableSeed());
        }
    }
}
