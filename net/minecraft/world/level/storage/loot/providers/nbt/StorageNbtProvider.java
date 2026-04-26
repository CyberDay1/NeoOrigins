package net.minecraft.world.level.storage.loot.providers.nbt;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;

public record StorageNbtProvider(ResourceLocation id) implements NbtProvider {
    public static final MapCodec<StorageNbtProvider> CODEC = RecordCodecBuilder.mapCodec(
        p_298341_ -> p_298341_.group(ResourceLocation.CODEC.fieldOf("source").forGetter(StorageNbtProvider::id)).apply(p_298341_, StorageNbtProvider::new)
    );

    @Override
    public LootNbtProviderType getType() {
        return NbtProviders.STORAGE;
    }

    @Nullable
    @Override
    public Tag get(LootContext p_165636_) {
        return p_165636_.getLevel().getServer().getCommandStorage().get(this.id);
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of();
    }
}
