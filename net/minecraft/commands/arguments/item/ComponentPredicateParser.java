package net.minecraft.commands.arguments.item;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.Term;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.util.parsing.packrat.commands.ResourceLocationParseRule;
import net.minecraft.util.parsing.packrat.commands.ResourceLookupRule;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;
import net.minecraft.util.parsing.packrat.commands.TagParseRule;

public class ComponentPredicateParser {
    public static <T, C, P> Grammar<List<T>> createGrammar(ComponentPredicateParser.Context<T, C, P> p_335727_) {
        Atom<List<T>> atom = Atom.of("top");
        Atom<Optional<T>> atom1 = Atom.of("type");
        Atom<Unit> atom2 = Atom.of("any_type");
        Atom<T> atom3 = Atom.of("element_type");
        Atom<T> atom4 = Atom.of("tag_type");
        Atom<List<T>> atom5 = Atom.of("conditions");
        Atom<List<T>> atom6 = Atom.of("alternatives");
        Atom<T> atom7 = Atom.of("term");
        Atom<T> atom8 = Atom.of("negation");
        Atom<T> atom9 = Atom.of("test");
        Atom<C> atom10 = Atom.of("component_type");
        Atom<P> atom11 = Atom.of("predicate_type");
        Atom<ResourceLocation> atom12 = Atom.of("id");
        Atom<Tag> atom13 = Atom.of("tag");
        Dictionary<StringReader> dictionary = new Dictionary<>();
        dictionary.put(
            atom,
            Term.alternative(
                Term.sequence(
                    Term.named(atom1), StringReaderTerms.character('['), Term.cut(), Term.optional(Term.named(atom5)), StringReaderTerms.character(']')
                ),
                Term.named(atom1)
            ),
            p_336103_ -> {
                Builder<T> builder = ImmutableList.builder();
                p_336103_.getOrThrow(atom1).ifPresent(builder::add);
                List<T> list = p_336103_.get(atom5);
                if (list != null) {
                    builder.addAll(list);
                }

                return builder.build();
            }
        );
        dictionary.put(
            atom1,
            Term.alternative(Term.named(atom3), Term.sequence(StringReaderTerms.character('#'), Term.cut(), Term.named(atom4)), Term.named(atom2)),
            p_335800_ -> Optional.ofNullable(p_335800_.getAny(atom3, atom4))
        );
        dictionary.put(atom2, StringReaderTerms.character('*'), p_335962_ -> Unit.INSTANCE);
        dictionary.put(atom3, new ComponentPredicateParser.ElementLookupRule<>(atom12, p_335727_));
        dictionary.put(atom4, new ComponentPredicateParser.TagLookupRule<>(atom12, p_335727_));
        dictionary.put(
            atom5, Term.sequence(Term.named(atom6), Term.optional(Term.sequence(StringReaderTerms.character(','), Term.named(atom5)))), p_336093_ -> {
                T t = p_335727_.anyOf(p_336093_.getOrThrow(atom6));
                return Optional.ofNullable(p_336093_.get(atom5)).map(p_335806_ -> Util.copyAndAdd(t, (List<T>)p_335806_)).orElse(List.of(t));
            }
        );
        dictionary.put(
            atom6, Term.sequence(Term.named(atom7), Term.optional(Term.sequence(StringReaderTerms.character('|'), Term.named(atom6)))), p_335542_ -> {
                T t = p_335542_.getOrThrow(atom7);
                return Optional.ofNullable(p_335542_.get(atom6)).map(p_335939_ -> Util.copyAndAdd(t, (List<T>)p_335939_)).orElse(List.of(t));
            }
        );
        dictionary.put(
            atom7,
            Term.alternative(Term.named(atom9), Term.sequence(StringReaderTerms.character('!'), Term.named(atom8))),
            p_335647_ -> p_335647_.getAnyOrThrow(atom9, atom8)
        );
        dictionary.put(atom8, Term.named(atom9), p_335793_ -> p_335727_.negate(p_335793_.getOrThrow(atom9)));
        dictionary.put(
            atom9,
            Term.alternative(
                Term.sequence(Term.named(atom10), StringReaderTerms.character('='), Term.cut(), Term.named(atom13)),
                Term.sequence(Term.named(atom11), StringReaderTerms.character('~'), Term.cut(), Term.named(atom13)),
                Term.named(atom10)
            ),
            (p_335570_, p_336062_) -> {
                P p = p_336062_.get(atom11);

                try {
                    if (p != null) {
                        Tag tag1 = p_336062_.getOrThrow(atom13);
                        return Optional.of(p_335727_.createPredicateTest(p_335570_.input(), p, tag1));
                    } else {
                        C c = p_336062_.getOrThrow(atom10);
                        Tag tag = p_336062_.get(atom13);
                        return Optional.of(
                            tag != null ? p_335727_.createComponentTest(p_335570_.input(), c, tag) : p_335727_.createComponentTest(p_335570_.input(), c)
                        );
                    }
                } catch (CommandSyntaxException commandsyntaxexception) {
                    p_335570_.errorCollector().store(p_335570_.mark(), commandsyntaxexception);
                    return Optional.empty();
                }
            }
        );
        dictionary.put(atom10, new ComponentPredicateParser.ComponentLookupRule<>(atom12, p_335727_));
        dictionary.put(atom11, new ComponentPredicateParser.PredicateLookupRule<>(atom12, p_335727_));
        dictionary.put(atom13, TagParseRule.INSTANCE);
        dictionary.put(atom12, ResourceLocationParseRule.INSTANCE);
        return new Grammar<>(dictionary, atom);
    }

    static class ComponentLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, C> {
        ComponentLookupRule(Atom<ResourceLocation> p_335669_, ComponentPredicateParser.Context<T, C, P> p_335692_) {
            super(p_335669_, p_335692_);
        }

        @Override
        protected C validateElement(ImmutableStringReader p_335604_, ResourceLocation p_335964_) throws Exception {
            return this.context.lookupComponentType(p_335604_, p_335964_);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listComponentTypes();
        }
    }

    public interface Context<T, C, P> {
        T forElementType(ImmutableStringReader p_335757_, ResourceLocation p_336070_) throws CommandSyntaxException;

        Stream<ResourceLocation> listElementTypes();

        T forTagType(ImmutableStringReader p_335809_, ResourceLocation p_335925_) throws CommandSyntaxException;

        Stream<ResourceLocation> listTagTypes();

        C lookupComponentType(ImmutableStringReader p_335515_, ResourceLocation p_335733_) throws CommandSyntaxException;

        Stream<ResourceLocation> listComponentTypes();

        T createComponentTest(ImmutableStringReader p_336142_, C p_336094_, Tag p_336057_) throws CommandSyntaxException;

        T createComponentTest(ImmutableStringReader p_335521_, C p_335579_);

        P lookupPredicateType(ImmutableStringReader p_336079_, ResourceLocation p_335954_) throws CommandSyntaxException;

        Stream<ResourceLocation> listPredicateTypes();

        T createPredicateTest(ImmutableStringReader p_335898_, P p_335609_, Tag p_335661_) throws CommandSyntaxException;

        T negate(T p_335446_);

        T anyOf(List<T> p_336010_);
    }

    static class ElementLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, T> {
        ElementLookupRule(Atom<ResourceLocation> p_335457_, ComponentPredicateParser.Context<T, C, P> p_335797_) {
            super(p_335457_, p_335797_);
        }

        @Override
        protected T validateElement(ImmutableStringReader p_335629_, ResourceLocation p_335530_) throws Exception {
            return this.context.forElementType(p_335629_, p_335530_);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listElementTypes();
        }
    }

    static class PredicateLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, P> {
        PredicateLookupRule(Atom<ResourceLocation> p_335495_, ComponentPredicateParser.Context<T, C, P> p_335572_) {
            super(p_335495_, p_335572_);
        }

        @Override
        protected P validateElement(ImmutableStringReader p_336035_, ResourceLocation p_335697_) throws Exception {
            return this.context.lookupPredicateType(p_336035_, p_335697_);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listPredicateTypes();
        }
    }

    static class TagLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, T> {
        TagLookupRule(Atom<ResourceLocation> p_335960_, ComponentPredicateParser.Context<T, C, P> p_335934_) {
            super(p_335960_, p_335934_);
        }

        @Override
        protected T validateElement(ImmutableStringReader p_335858_, ResourceLocation p_335888_) throws Exception {
            return this.context.forTagType(p_335858_, p_335888_);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listTagTypes();
        }
    }
}
