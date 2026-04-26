package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableObject;

public class ItemParser {
    static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(
        p_335608_ -> Component.translatableEscape("argument.item.id.invalid", p_335608_)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(
        p_335852_ -> Component.translatableEscape("arguments.item.component.unknown", p_335852_)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType(
        (p_336012_, p_335885_) -> Component.translatableEscape("arguments.item.component.malformed", p_336012_, p_335885_)
    );
    static final SimpleCommandExceptionType ERROR_EXPECTED_COMPONENT = new SimpleCommandExceptionType(
        Component.translatable("arguments.item.component.expected")
    );
    static final DynamicCommandExceptionType ERROR_REPEATED_COMPONENT = new DynamicCommandExceptionType(
        p_335753_ -> Component.translatableEscape("arguments.item.component.repeated", p_335753_)
    );
    private static final DynamicCommandExceptionType ERROR_MALFORMED_ITEM = new DynamicCommandExceptionType(
        p_340618_ -> Component.translatableEscape("arguments.item.malformed", p_340618_)
    );
    public static final char SYNTAX_START_COMPONENTS = '[';
    public static final char SYNTAX_END_COMPONENTS = ']';
    public static final char SYNTAX_COMPONENT_SEPARATOR = ',';
    public static final char SYNTAX_COMPONENT_ASSIGNMENT = '=';
    public static final char SYNTAX_REMOVED_COMPONENT = '!';
    static final Function<SuggestionsBuilder, CompletableFuture<Suggestions>> SUGGEST_NOTHING = SuggestionsBuilder::buildFuture;
    final HolderLookup.RegistryLookup<Item> items;
    final DynamicOps<Tag> registryOps;

    public ItemParser(HolderLookup.Provider p_324404_) {
        this.items = p_324404_.lookupOrThrow(Registries.ITEM);
        this.registryOps = p_324404_.createSerializationContext(NbtOps.INSTANCE);
    }

    public ItemParser.ItemResult parse(StringReader p_324270_) throws CommandSyntaxException {
        final MutableObject<Holder<Item>> mutableobject = new MutableObject<>();
        final DataComponentPatch.Builder datacomponentpatch$builder = DataComponentPatch.builder();
        this.parse(p_324270_, new ItemParser.Visitor() {
            @Override
            public void visitItem(Holder<Item> p_324335_) {
                mutableobject.setValue(p_324335_);
            }

            @Override
            public <T> void visitComponent(DataComponentType<T> p_330699_, T p_330996_) {
                datacomponentpatch$builder.set(p_330699_, p_330996_);
            }

            @Override
            public <T> void visitRemovedComponent(DataComponentType<T> p_345362_) {
                datacomponentpatch$builder.remove(p_345362_);
            }
        });
        Holder<Item> holder = Objects.requireNonNull(mutableobject.getValue(), "Parser gave no item");
        DataComponentPatch datacomponentpatch = datacomponentpatch$builder.build();
        validateComponents(p_324270_, holder, datacomponentpatch);
        return new ItemParser.ItemResult(holder, datacomponentpatch);
    }

    private static void validateComponents(StringReader p_341137_, Holder<Item> p_341139_, DataComponentPatch p_345848_) throws CommandSyntaxException {
        DataComponentMap datacomponentmap = PatchedDataComponentMap.fromPatch(p_341139_.value().components(), p_345848_);
        DataResult<Unit> dataresult = ItemStack.validateComponents(datacomponentmap);
        dataresult.getOrThrow(p_340620_ -> ERROR_MALFORMED_ITEM.createWithContext(p_341137_, p_340620_));
    }

    public void parse(StringReader p_336039_, ItemParser.Visitor p_335987_) throws CommandSyntaxException {
        int i = p_336039_.getCursor();

        try {
            new ItemParser.State(p_336039_, p_335987_).parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
            p_336039_.setCursor(i);
            throw commandsyntaxexception;
        }
    }

    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder p_235310_) {
        StringReader stringreader = new StringReader(p_235310_.getInput());
        stringreader.setCursor(p_235310_.getStart());
        ItemParser.SuggestionsVisitor itemparser$suggestionsvisitor = new ItemParser.SuggestionsVisitor();
        ItemParser.State itemparser$state = new ItemParser.State(stringreader, itemparser$suggestionsvisitor);

        try {
            itemparser$state.parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
        }

        return itemparser$suggestionsvisitor.resolveSuggestions(p_235310_, stringreader);
    }

    public static record ItemResult(Holder<Item> item, DataComponentPatch components) {
    }

    class State {
        private final StringReader reader;
        private final ItemParser.Visitor visitor;

        State(StringReader p_335807_, ItemParser.Visitor p_336013_) {
            this.reader = p_335807_;
            this.visitor = p_336013_;
        }

        public void parse() throws CommandSyntaxException {
            this.visitor.visitSuggestions(this::suggestItem);
            this.readItem();
            this.visitor.visitSuggestions(this::suggestStartComponents);
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                this.readComponents();
            }
        }

        private void readItem() throws CommandSyntaxException {
            int i = this.reader.getCursor();
            ResourceLocation resourcelocation = ResourceLocation.read(this.reader);
            this.visitor.visitItem(ItemParser.this.items.get(ResourceKey.create(Registries.ITEM, resourcelocation)).orElseThrow(() -> {
                this.reader.setCursor(i);
                return ItemParser.ERROR_UNKNOWN_ITEM.createWithContext(this.reader, resourcelocation);
            }));
        }

        private void readComponents() throws CommandSyntaxException {
            this.reader.expect('[');
            this.visitor.visitSuggestions(this::suggestComponentAssignmentOrRemoval);
            Set<DataComponentType<?>> set = new ReferenceArraySet<>();

            while (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                if (this.reader.canRead() && this.reader.peek() == '!') {
                    this.reader.skip();
                    this.visitor.visitSuggestions(this::suggestComponent);
                    DataComponentType<?> datacomponenttype1 = readComponentType(this.reader);
                    if (!set.add(datacomponenttype1)) {
                        throw ItemParser.ERROR_REPEATED_COMPONENT.create(datacomponenttype1);
                    }

                    this.visitor.visitRemovedComponent(datacomponenttype1);
                    this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                    this.reader.skipWhitespace();
                } else {
                    DataComponentType<?> datacomponenttype = readComponentType(this.reader);
                    if (!set.add(datacomponenttype)) {
                        throw ItemParser.ERROR_REPEATED_COMPONENT.create(datacomponenttype);
                    }

                    this.visitor.visitSuggestions(this::suggestAssignment);
                    this.reader.skipWhitespace();
                    this.reader.expect('=');
                    this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                    this.reader.skipWhitespace();
                    this.readComponent(datacomponenttype);
                    this.reader.skipWhitespace();
                }

                this.visitor.visitSuggestions(this::suggestNextOrEndComponents);
                if (!this.reader.canRead() || this.reader.peek() != ',') {
                    break;
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.visitor.visitSuggestions(this::suggestComponentAssignmentOrRemoval);
                if (!this.reader.canRead()) {
                    throw ItemParser.ERROR_EXPECTED_COMPONENT.createWithContext(this.reader);
                }
            }

            this.reader.expect(']');
            this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
        }

        public static DataComponentType<?> readComponentType(StringReader p_335663_) throws CommandSyntaxException {
            if (!p_335663_.canRead()) {
                throw ItemParser.ERROR_EXPECTED_COMPONENT.createWithContext(p_335663_);
            } else {
                int i = p_335663_.getCursor();
                ResourceLocation resourcelocation = ResourceLocation.read(p_335663_);
                DataComponentType<?> datacomponenttype = BuiltInRegistries.DATA_COMPONENT_TYPE.get(resourcelocation);
                if (datacomponenttype != null && !datacomponenttype.isTransient()) {
                    return datacomponenttype;
                } else {
                    p_335663_.setCursor(i);
                    throw ItemParser.ERROR_UNKNOWN_COMPONENT.createWithContext(p_335663_, resourcelocation);
                }
            }
        }

        private <T> void readComponent(DataComponentType<T> p_335594_) throws CommandSyntaxException {
            int i = this.reader.getCursor();
            Tag tag = new TagParser(this.reader).readValue();
            DataResult<T> dataresult = p_335594_.codecOrThrow().parse(ItemParser.this.registryOps, tag);
            this.visitor.visitComponent(p_335594_, dataresult.getOrThrow(p_339324_ -> {
                this.reader.setCursor(i);
                return ItemParser.ERROR_MALFORMED_COMPONENT.createWithContext(this.reader, p_335594_.toString(), p_339324_);
            }));
        }

        private CompletableFuture<Suggestions> suggestStartComponents(SuggestionsBuilder p_335464_) {
            if (p_335464_.getRemaining().isEmpty()) {
                p_335464_.suggest(String.valueOf('['));
            }

            return p_335464_.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestNextOrEndComponents(SuggestionsBuilder p_335894_) {
            if (p_335894_.getRemaining().isEmpty()) {
                p_335894_.suggest(String.valueOf(','));
                p_335894_.suggest(String.valueOf(']'));
            }

            return p_335894_.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestAssignment(SuggestionsBuilder p_335975_) {
            if (p_335975_.getRemaining().isEmpty()) {
                p_335975_.suggest(String.valueOf('='));
            }

            return p_335975_.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestItem(SuggestionsBuilder p_336095_) {
            return SharedSuggestionProvider.suggestResource(ItemParser.this.items.listElementIds().map(ResourceKey::location), p_336095_);
        }

        private CompletableFuture<Suggestions> suggestComponentAssignmentOrRemoval(SuggestionsBuilder p_345256_) {
            p_345256_.suggest(String.valueOf('!'));
            return this.suggestComponent(p_345256_, String.valueOf('='));
        }

        private CompletableFuture<Suggestions> suggestComponent(SuggestionsBuilder p_345045_) {
            return this.suggestComponent(p_345045_, "");
        }

        private CompletableFuture<Suggestions> suggestComponent(SuggestionsBuilder p_346056_, String p_346259_) {
            String s = p_346056_.getRemaining().toLowerCase(Locale.ROOT);
            SharedSuggestionProvider.filterResources(
                BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet(), s, p_336071_ -> p_336071_.getKey().location(), p_344177_ -> {
                    DataComponentType<?> datacomponenttype = p_344177_.getValue();
                    if (datacomponenttype.codec() != null) {
                        ResourceLocation resourcelocation = p_344177_.getKey().location();
                        p_346056_.suggest(resourcelocation + p_346259_);
                    }
                }
            );
            return p_346056_.buildFuture();
        }
    }

    static class SuggestionsVisitor implements ItemParser.Visitor {
        private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions = ItemParser.SUGGEST_NOTHING;

        @Override
        public void visitSuggestions(Function<SuggestionsBuilder, CompletableFuture<Suggestions>> p_335625_) {
            this.suggestions = p_335625_;
        }

        public CompletableFuture<Suggestions> resolveSuggestions(SuggestionsBuilder p_336050_, StringReader p_335952_) {
            return this.suggestions.apply(p_336050_.createOffset(p_335952_.getCursor()));
        }
    }

    public interface Visitor {
        default void visitItem(Holder<Item> p_336184_) {
        }

        default <T> void visitComponent(DataComponentType<T> p_336083_, T p_335499_) {
        }

        default <T> void visitRemovedComponent(DataComponentType<T> p_344835_) {
        }

        default void visitSuggestions(Function<SuggestionsBuilder, CompletableFuture<Suggestions>> p_335635_) {
        }
    }
}
