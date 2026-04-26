package net.minecraft.world.level.storage.loot.providers.number;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootContext;

public record StorageValue(ResourceLocation storage, NbtPathArgument.NbtPath path) implements NumberProvider {
    public static final MapCodec<StorageValue> CODEC = RecordCodecBuilder.mapCodec(
        p_335586_ -> p_335586_.group(
                    ResourceLocation.CODEC.fieldOf("storage").forGetter(StorageValue::storage),
                    NbtPathArgument.NbtPath.CODEC.fieldOf("path").forGetter(StorageValue::path)
                )
                .apply(p_335586_, StorageValue::new)
    );

    @Override
    public LootNumberProviderType getType() {
        return NumberProviders.STORAGE;
    }

    private Optional<NumericTag> getNumericTag(LootContext p_336045_) {
        CompoundTag compoundtag = p_336045_.getLevel().getServer().getCommandStorage().get(this.storage);

        try {
            List<Tag> list = this.path.get(compoundtag);
            if (list.size() == 1 && list.get(0) instanceof NumericTag numerictag) {
                return Optional.of(numerictag);
            }
        } catch (CommandSyntaxException commandsyntaxexception) {
        }

        return Optional.empty();
    }

    @Override
    public float getFloat(LootContext p_335884_) {
        return this.getNumericTag(p_335884_).map(NumericTag::getAsFloat).orElse(0.0F);
    }

    @Override
    public int getInt(LootContext p_335703_) {
        return this.getNumericTag(p_335703_).map(NumericTag::getAsInt).orElse(0);
    }
}
