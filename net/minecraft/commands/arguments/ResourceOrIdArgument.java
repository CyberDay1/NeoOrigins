package net.minecraft.commands.arguments;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ResourceOrIdArgument<T> implements ArgumentType<Holder<T>> {
    private static final Collection<String> EXAMPLES = List.of("foo", "foo:bar", "012", "{}", "true");
    public static final DynamicCommandExceptionType ERROR_FAILED_TO_PARSE = new DynamicCommandExceptionType(
        p_335811_ -> Component.translatableEscape("argument.resource_or_id.failed_to_parse", p_335811_)
    );
    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.resource_or_id.invalid"));
    private final HolderLookup.Provider registryLookup;
    private final boolean hasRegistry;
    private final Codec<Holder<T>> codec;

    protected ResourceOrIdArgument(CommandBuildContext p_335864_, ResourceKey<Registry<T>> p_335475_, Codec<Holder<T>> p_335632_) {
        this.registryLookup = p_335864_;
        this.hasRegistry = p_335864_.lookup(p_335475_).isPresent();
        this.codec = p_335632_;
    }

    public static ResourceOrIdArgument.LootTableArgument lootTable(CommandBuildContext p_335938_) {
        return new ResourceOrIdArgument.LootTableArgument(p_335938_);
    }

    public static Holder<LootTable> getLootTable(CommandContext<CommandSourceStack> p_335373_, String p_336017_) throws CommandSyntaxException {
        return getResource(p_335373_, p_336017_);
    }

    public static ResourceOrIdArgument.LootModifierArgument lootModifier(CommandBuildContext p_335731_) {
        return new ResourceOrIdArgument.LootModifierArgument(p_335731_);
    }

    public static Holder<LootItemFunction> getLootModifier(CommandContext<CommandSourceStack> p_335678_, String p_336178_) {
        return getResource(p_335678_, p_336178_);
    }

    public static ResourceOrIdArgument.LootPredicateArgument lootPredicate(CommandBuildContext p_335891_) {
        return new ResourceOrIdArgument.LootPredicateArgument(p_335891_);
    }

    public static Holder<LootItemCondition> getLootPredicate(CommandContext<CommandSourceStack> p_336183_, String p_336098_) {
        return getResource(p_336183_, p_336098_);
    }

    private static <T> Holder<T> getResource(CommandContext<CommandSourceStack> p_336122_, String p_335417_) {
        return p_336122_.getArgument(p_335417_, Holder.class);
    }

    @Nullable
    public Holder<T> parse(StringReader p_335906_) throws CommandSyntaxException {
        Tag tag = parseInlineOrId(p_335906_);
        if (!this.hasRegistry) {
            return null;
        } else {
            RegistryOps<Tag> registryops = this.registryLookup.createSerializationContext(NbtOps.INSTANCE);
            return this.codec.parse(registryops, tag).getOrThrow(p_335883_ -> ERROR_FAILED_TO_PARSE.createWithContext(p_335906_, p_335883_));
        }
    }

    @VisibleForTesting
    static Tag parseInlineOrId(StringReader p_335947_) throws CommandSyntaxException {
        int i = p_335947_.getCursor();
        Tag tag = new TagParser(p_335947_).readValue();
        if (hasConsumedWholeArg(p_335947_)) {
            return tag;
        } else {
            p_335947_.setCursor(i);
            ResourceLocation resourcelocation = ResourceLocation.read(p_335947_);
            if (hasConsumedWholeArg(p_335947_)) {
                return StringTag.valueOf(resourcelocation.toString());
            } else {
                p_335947_.setCursor(i);
                throw ERROR_INVALID.createWithContext(p_335947_);
            }
        }
    }

    private static boolean hasConsumedWholeArg(StringReader p_335624_) {
        return !p_335624_.canRead() || p_335624_.peek() == ' ';
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class LootModifierArgument extends ResourceOrIdArgument<LootItemFunction> {
        protected LootModifierArgument(CommandBuildContext p_335547_) {
            super(p_335547_, Registries.ITEM_MODIFIER, LootItemFunctions.CODEC);
        }
    }

    public static class LootPredicateArgument extends ResourceOrIdArgument<LootItemCondition> {
        protected LootPredicateArgument(CommandBuildContext p_336020_) {
            super(p_336020_, Registries.PREDICATE, LootItemCondition.CODEC);
        }
    }

    public static class LootTableArgument extends ResourceOrIdArgument<LootTable> {
        protected LootTableArgument(CommandBuildContext p_335769_) {
            super(p_335769_, Registries.LOOT_TABLE, LootTable.CODEC);
        }
    }
}
