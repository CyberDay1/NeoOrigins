package net.minecraft.util.parsing.packrat;

import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.mutable.MutableBoolean;

public interface Term<S> {
    boolean parse(ParseState<S> p_335458_, Scope p_335411_, Control p_336015_);

    static <S> Term<S> named(Atom<?> p_335922_) {
        return new Term.Reference<>(p_335922_);
    }

    static <S, T> Term<S> marker(Atom<T> p_335833_, T p_335787_) {
        return new Term.Marker<>(p_335833_, p_335787_);
    }

    @SafeVarargs
    static <S> Term<S> sequence(Term<S>... p_336132_) {
        return new Term.Sequence<>(List.of(p_336132_));
    }

    @SafeVarargs
    static <S> Term<S> alternative(Term<S>... p_335455_) {
        return new Term.Alternative<>(List.of(p_335455_));
    }

    static <S> Term<S> optional(Term<S> p_335471_) {
        return new Term.Maybe<>(p_335471_);
    }

    static <S> Term<S> cut() {
        return new Term<S>() {
            @Override
            public boolean parse(ParseState<S> p_335490_, Scope p_335377_, Control p_336074_) {
                p_336074_.cut();
                return true;
            }

            @Override
            public String toString() {
                return "\u2191";
            }
        };
    }

    static <S> Term<S> empty() {
        return new Term<S>() {
            @Override
            public boolean parse(ParseState<S> p_335978_, Scope p_335744_, Control p_335881_) {
                return true;
            }

            @Override
            public String toString() {
                return "\u03b5";
            }
        };
    }

    public static record Alternative<S>(List<Term<S>> elements) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> p_336147_, Scope p_335902_, Control p_335396_) {
            MutableBoolean mutableboolean = new MutableBoolean();
            Control control = mutableboolean::setTrue;
            int i = p_336147_.mark();

            for (Term<S> term : this.elements) {
                if (mutableboolean.isTrue()) {
                    break;
                }

                Scope scope = new Scope();
                if (term.parse(p_336147_, scope, control)) {
                    p_335902_.putAll(scope);
                    return true;
                }

                p_336147_.restore(i);
            }

            return false;
        }
    }

    public static record Marker<S, T>(Atom<T> name, T value) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> p_335600_, Scope p_335485_, Control p_335375_) {
            p_335485_.put(this.name, this.value);
            return true;
        }
    }

    public static record Maybe<S>(Term<S> term) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> p_335415_, Scope p_335550_, Control p_336000_) {
            int i = p_335415_.mark();
            if (!this.term.parse(p_335415_, p_335550_, p_336000_)) {
                p_335415_.restore(i);
            }

            return true;
        }
    }

    public static record Reference<S, T>(Atom<T> name) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> p_335637_, Scope p_336134_, Control p_336055_) {
            Optional<T> optional = p_335637_.parse(this.name);
            if (optional.isEmpty()) {
                return false;
            } else {
                p_336134_.put(this.name, optional.get());
                return true;
            }
        }
    }

    public static record Sequence<S>(List<Term<S>> elements) implements Term<S> {
        @Override
        public boolean parse(ParseState<S> p_336111_, Scope p_335911_, Control p_336188_) {
            int i = p_336111_.mark();

            for (Term<S> term : this.elements) {
                if (!term.parse(p_336111_, p_335911_, p_336188_)) {
                    p_336111_.restore(i);
                    return false;
                }
            }

            return true;
        }
    }
}
