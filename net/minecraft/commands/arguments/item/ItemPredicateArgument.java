package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemPredicateArgument implements ArgumentType<ItemPredicateArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("stick", "minecraft:stick", "#stick", "#stick{foo:'bar'}");
    static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(
        p_335502_ -> Component.translatableEscape("argument.item.id.invalid", p_335502_)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(
        p_335527_ -> Component.translatableEscape("arguments.item.tag.unknown", p_335527_)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(
        p_335843_ -> Component.translatableEscape("arguments.item.component.unknown", p_335843_)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType(
        (p_335483_, p_335643_) -> Component.translatableEscape("arguments.item.component.malformed", p_335483_, p_335643_)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_PREDICATE = new DynamicCommandExceptionType(
        p_335658_ -> Component.translatableEscape("arguments.item.predicate.unknown", p_335658_)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_PREDICATE = new Dynamic2CommandExceptionType(
        (p_336040_, p_335526_) -> Component.translatableEscape("arguments.item.predicate.malformed", p_336040_, p_335526_)
    );
    private static final ResourceLocation COUNT_ID = ResourceLocation.withDefaultNamespace("count");
    static final Map<ResourceLocation, ItemPredicateArgument.ComponentWrapper> PSEUDO_COMPONENTS = Stream.of(
            new ItemPredicateArgument.ComponentWrapper(
                COUNT_ID, p_335429_ -> true, MinMaxBounds.Ints.CODEC.map(p_335378_ -> p_336161_ -> p_335378_.matches(p_336161_.getCount()))
            )
        )
        .collect(Collectors.toUnmodifiableMap(ItemPredicateArgument.ComponentWrapper::id, p_335476_ -> (ItemPredicateArgument.ComponentWrapper)p_335476_));
    static final Map<ResourceLocation, ItemPredicateArgument.PredicateWrapper> PSEUDO_PREDICATES = Stream.of(
            new ItemPredicateArgument.PredicateWrapper(COUNT_ID, MinMaxBounds.Ints.CODEC.map(p_335489_ -> p_335603_ -> p_335489_.matches(p_335603_.getCount())))
        )
        .collect(Collectors.toUnmodifiableMap(ItemPredicateArgument.PredicateWrapper::id, p_335496_ -> (ItemPredicateArgument.PredicateWrapper)p_335496_));
    private final Grammar<List<Predicate<ItemStack>>> grammarWithContext;

    public ItemPredicateArgument(CommandBuildContext p_235352_) {
        ItemPredicateArgument.Context itempredicateargument$context = new ItemPredicateArgument.Context(p_235352_);
        this.grammarWithContext = ComponentPredicateParser.createGrammar(itempredicateargument$context);
    }

    public static ItemPredicateArgument itemPredicate(CommandBuildContext p_235354_) {
        return new ItemPredicateArgument(p_235354_);
    }

    public ItemPredicateArgument.Result parse(StringReader p_121039_) throws CommandSyntaxException {
        return Util.allOf(this.grammarWithContext.parseForCommands(p_121039_))::test;
    }

    public static ItemPredicateArgument.Result getItemPredicate(CommandContext<CommandSourceStack> p_121041_, String p_121042_) {
        return p_121041_.getArgument(p_121042_, ItemPredicateArgument.Result.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> p_121054_, SuggestionsBuilder p_121055_) {
        return this.grammarWithContext.parseForSuggestions(p_121055_);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    static record ComponentWrapper(ResourceLocation id, Predicate<ItemStack> presenceChecker, Decoder<? extends Predicate<ItemStack>> valueChecker) {
        public static <T> ItemPredicateArgument.ComponentWrapper create(
            ImmutableStringReader p_335592_, ResourceLocation p_335737_, DataComponentType<T> p_335860_
        ) throws CommandSyntaxException {
            Codec<T> codec = p_335860_.codec();
            if (codec == null) {
                throw ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(p_335592_, p_335737_);
            } else {
                return new ItemPredicateArgument.ComponentWrapper(p_335737_, p_335659_ -> p_335659_.has(p_335860_), codec.map(p_335913_ -> p_335541_ -> {
                        T t = p_335541_.get(p_335860_);
                        return Objects.equals(p_335913_, t);
                    }));
            }
        }

        public Predicate<ItemStack> decode(ImmutableStringReader p_335970_, RegistryOps<Tag> p_335855_, Tag p_335892_) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataresult = this.valueChecker.parse(p_335855_, p_335892_);
            return (Predicate<ItemStack>)dataresult.getOrThrow(
                p_335410_ -> ItemPredicateArgument.ERROR_MALFORMED_COMPONENT.createWithContext(p_335970_, this.id.toString(), p_335410_)
            );
        }
    }

    static class Context
        implements ComponentPredicateParser.Context<Predicate<ItemStack>, ItemPredicateArgument.ComponentWrapper, ItemPredicateArgument.PredicateWrapper> {
        private final HolderLookup.RegistryLookup<Item> items;
        private final HolderLookup.RegistryLookup<DataComponentType<?>> components;
        private final HolderLookup.RegistryLookup<ItemSubPredicate.Type<?>> predicates;
        private final RegistryOps<Tag> registryOps;

        Context(HolderLookup.Provider p_335856_) {
            this.items = p_335856_.lookupOrThrow(Registries.ITEM);
            this.components = p_335856_.lookupOrThrow(Registries.DATA_COMPONENT_TYPE);
            this.predicates = p_335856_.lookupOrThrow(Registries.ITEM_SUB_PREDICATE_TYPE);
            this.registryOps = p_335856_.createSerializationContext(NbtOps.INSTANCE);
        }

        public Predicate<ItemStack> forElementType(ImmutableStringReader p_335407_, ResourceLocation p_335837_) throws CommandSyntaxException {
            Holder.Reference<Item> reference = this.items
                .get(ResourceKey.create(Registries.ITEM, p_335837_))
                .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_ITEM.createWithContext(p_335407_, p_335837_));
            return p_335935_ -> p_335935_.is(reference);
        }

        public Predicate<ItemStack> forTagType(ImmutableStringReader p_335801_, ResourceLocation p_336117_) throws CommandSyntaxException {
            HolderSet<Item> holderset = this.items
                .get(TagKey.create(Registries.ITEM, p_336117_))
                .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_TAG.createWithContext(p_335801_, p_336117_));
            return p_336090_ -> p_336090_.is(holderset);
        }

        public ItemPredicateArgument.ComponentWrapper lookupComponentType(ImmutableStringReader p_336180_, ResourceLocation p_335872_) throws CommandSyntaxException {
            ItemPredicateArgument.ComponentWrapper itempredicateargument$componentwrapper = ItemPredicateArgument.PSEUDO_COMPONENTS.get(p_335872_);
            if (itempredicateargument$componentwrapper != null) {
                return itempredicateargument$componentwrapper;
            } else {
                DataComponentType<?> datacomponenttype = this.components
                    .get(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, p_335872_))
                    .map(Holder::value)
                    .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(p_336180_, p_335872_));
                return ItemPredicateArgument.ComponentWrapper.create(p_336180_, p_335872_, datacomponenttype);
            }
        }

        public Predicate<ItemStack> createComponentTest(ImmutableStringReader p_335953_, ItemPredicateArgument.ComponentWrapper p_335824_, Tag p_335973_) throws CommandSyntaxException {
            return p_335824_.decode(p_335953_, this.registryOps, p_335973_);
        }

        public Predicate<ItemStack> createComponentTest(ImmutableStringReader p_335602_, ItemPredicateArgument.ComponentWrapper p_335607_) {
            return p_335607_.presenceChecker;
        }

        public ItemPredicateArgument.PredicateWrapper lookupPredicateType(ImmutableStringReader p_335514_, ResourceLocation p_335709_) throws CommandSyntaxException {
            ItemPredicateArgument.PredicateWrapper itempredicateargument$predicatewrapper = ItemPredicateArgument.PSEUDO_PREDICATES.get(p_335709_);
            return itempredicateargument$predicatewrapper != null
                ? itempredicateargument$predicatewrapper
                : this.predicates
                    .get(ResourceKey.create(Registries.ITEM_SUB_PREDICATE_TYPE, p_335709_))
                    .map(ItemPredicateArgument.PredicateWrapper::new)
                    .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_PREDICATE.createWithContext(p_335514_, p_335709_));
        }

        public Predicate<ItemStack> createPredicateTest(ImmutableStringReader p_335414_, ItemPredicateArgument.PredicateWrapper p_335561_, Tag p_336073_) throws CommandSyntaxException {
            return p_335561_.decode(p_335414_, this.registryOps, p_336073_);
        }

        @Override
        public Stream<ResourceLocation> listElementTypes() {
            return this.items.listElementIds().map(ResourceKey::location);
        }

        @Override
        public Stream<ResourceLocation> listTagTypes() {
            return this.items.listTagIds().map(TagKey::location);
        }

        @Override
        public Stream<ResourceLocation> listComponentTypes() {
            return Stream.concat(
                ItemPredicateArgument.PSEUDO_COMPONENTS.keySet().stream(),
                this.components.listElements().filter(p_335558_ -> !p_335558_.value().isTransient()).map(p_335650_ -> p_335650_.key().location())
            );
        }

        @Override
        public Stream<ResourceLocation> listPredicateTypes() {
            return Stream.concat(ItemPredicateArgument.PSEUDO_PREDICATES.keySet().stream(), this.predicates.listElementIds().map(ResourceKey::location));
        }

        public Predicate<ItemStack> negate(Predicate<ItemStack> p_335412_) {
            return p_335412_.negate();
        }

        public Predicate<ItemStack> anyOf(List<Predicate<ItemStack>> p_336064_) {
            return Util.anyOf(p_336064_);
        }
    }

    static record PredicateWrapper(ResourceLocation id, Decoder<? extends Predicate<ItemStack>> type) {
        public PredicateWrapper(Holder.Reference<ItemSubPredicate.Type<?>> p_336100_) {
            this(p_336100_.key().location(), p_336100_.value().codec().map(p_335814_ -> p_335814_::matches));
        }

        public Predicate<ItemStack> decode(ImmutableStringReader p_335497_, RegistryOps<Tag> p_335845_, Tag p_335487_) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataresult = this.type.parse(p_335845_, p_335487_);
            return (Predicate<ItemStack>)dataresult.getOrThrow(
                p_336129_ -> ItemPredicateArgument.ERROR_MALFORMED_PREDICATE.createWithContext(p_335497_, this.id.toString(), p_336129_)
            );
        }
    }

    public interface Result extends Predicate<ItemStack> {
    }
}
