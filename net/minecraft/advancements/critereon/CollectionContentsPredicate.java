package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public interface CollectionContentsPredicate<T, P extends Predicate<T>> extends Predicate<Iterable<T>> {
    List<P> unpack();

    static <T, P extends Predicate<T>> Codec<CollectionContentsPredicate<T, P>> codec(Codec<P> p_341117_) {
        return p_341117_.listOf().xmap(CollectionContentsPredicate::of, CollectionContentsPredicate::unpack);
    }

    @SafeVarargs
    static <T, P extends Predicate<T>> CollectionContentsPredicate<T, P> of(P... p_341163_) {
        return of(List.of(p_341163_));
    }

    static <T, P extends Predicate<T>> CollectionContentsPredicate<T, P> of(List<P> p_341230_) {
        return (CollectionContentsPredicate<T, P>)(switch (p_341230_.size()) {
            case 0 -> new CollectionContentsPredicate.Zero();
            case 1 -> new CollectionContentsPredicate.Single(p_341230_.getFirst());
            default -> new CollectionContentsPredicate.Multiple(p_341230_);
        });
    }

    public static record Multiple<T, P extends Predicate<T>>(List<P> tests) implements CollectionContentsPredicate<T, P> {
        public boolean test(Iterable<T> p_340977_) {
            List<Predicate<T>> list = new ArrayList<>(this.tests);

            for (T t : p_340977_) {
                list.removeIf(p_341085_ -> p_341085_.test(t));
                if (list.isEmpty()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public List<P> unpack() {
            return this.tests;
        }
    }

    public static record Single<T, P extends Predicate<T>>(P test) implements CollectionContentsPredicate<T, P> {
        public boolean test(Iterable<T> p_340831_) {
            for (T t : p_340831_) {
                if (this.test.test(t)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public List<P> unpack() {
            return List.of(this.test);
        }
    }

    public static class Zero<T, P extends Predicate<T>> implements CollectionContentsPredicate<T, P> {
        public boolean test(Iterable<T> p_341091_) {
            return true;
        }

        @Override
        public List<P> unpack() {
            return List.of();
        }
    }
}
