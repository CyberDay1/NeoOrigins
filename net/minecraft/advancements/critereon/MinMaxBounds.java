package net.minecraft.advancements.critereon;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public interface MinMaxBounds<T extends Number> {
    SimpleCommandExceptionType ERROR_EMPTY = new SimpleCommandExceptionType(Component.translatable("argument.range.empty"));
    SimpleCommandExceptionType ERROR_SWAPPED = new SimpleCommandExceptionType(Component.translatable("argument.range.swapped"));

    Optional<T> min();

    Optional<T> max();

    default boolean isAny() {
        return this.min().isEmpty() && this.max().isEmpty();
    }

    default Optional<T> unwrapPoint() {
        Optional<T> optional = this.min();
        Optional<T> optional1 = this.max();
        return optional.equals(optional1) ? optional : Optional.empty();
    }

    static <T extends Number, R extends MinMaxBounds<T>> Codec<R> createCodec(Codec<T> p_298521_, MinMaxBounds.BoundsFactory<T, R> p_298688_) {
        Codec<R> codec = RecordCodecBuilder.create(
            p_337383_ -> p_337383_.group(
                        p_298521_.optionalFieldOf("min").forGetter(MinMaxBounds::min), p_298521_.optionalFieldOf("max").forGetter(MinMaxBounds::max)
                    )
                    .apply(p_337383_, p_298688_::create)
        );
        return Codec.either(codec, p_298521_)
            .xmap(
                p_298558_ -> p_298558_.map(p_299210_ -> (R)p_299210_, p_298935_ -> p_298688_.create(Optional.of((T)p_298935_), Optional.of((T)p_298935_))),
                p_298447_ -> {
                    Optional<T> optional = p_298447_.unwrapPoint();
                    return optional.isPresent() ? Either.right(optional.get()) : Either.left((R)p_298447_);
                }
            );
    }

    static <T extends Number, R extends MinMaxBounds<T>> R fromReader(
        StringReader p_55314_,
        MinMaxBounds.BoundsFromReaderFactory<T, R> p_55315_,
        Function<String, T> p_55316_,
        Supplier<DynamicCommandExceptionType> p_55317_,
        Function<T, T> p_55318_
    ) throws CommandSyntaxException {
        if (!p_55314_.canRead()) {
            throw ERROR_EMPTY.createWithContext(p_55314_);
        } else {
            int i = p_55314_.getCursor();

            try {
                Optional<T> optional = readNumber(p_55314_, p_55316_, p_55317_).map(p_55318_);
                Optional<T> optional1;
                if (p_55314_.canRead(2) && p_55314_.peek() == '.' && p_55314_.peek(1) == '.') {
                    p_55314_.skip();
                    p_55314_.skip();
                    optional1 = readNumber(p_55314_, p_55316_, p_55317_).map(p_55318_);
                    if (optional.isEmpty() && optional1.isEmpty()) {
                        throw ERROR_EMPTY.createWithContext(p_55314_);
                    }
                } else {
                    optional1 = optional;
                }

                if (optional.isEmpty() && optional1.isEmpty()) {
                    throw ERROR_EMPTY.createWithContext(p_55314_);
                } else {
                    return p_55315_.create(p_55314_, optional, optional1);
                }
            } catch (CommandSyntaxException commandsyntaxexception) {
                p_55314_.setCursor(i);
                throw new CommandSyntaxException(commandsyntaxexception.getType(), commandsyntaxexception.getRawMessage(), commandsyntaxexception.getInput(), i);
            }
        }
    }

    private static <T extends Number> Optional<T> readNumber(
        StringReader p_55320_, Function<String, T> p_55321_, Supplier<DynamicCommandExceptionType> p_55322_
    ) throws CommandSyntaxException {
        int i = p_55320_.getCursor();

        while (p_55320_.canRead() && isAllowedInputChat(p_55320_)) {
            p_55320_.skip();
        }

        String s = p_55320_.getString().substring(i, p_55320_.getCursor());
        if (s.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(p_55321_.apply(s));
            } catch (NumberFormatException numberformatexception) {
                throw p_55322_.get().createWithContext(p_55320_, s);
            }
        }
    }

    private static boolean isAllowedInputChat(StringReader p_55312_) {
        char c0 = p_55312_.peek();
        if ((c0 < '0' || c0 > '9') && c0 != '-') {
            return c0 != '.' ? false : !p_55312_.canRead(2) || p_55312_.peek(1) != '.';
        } else {
            return true;
        }
    }

    @FunctionalInterface
    public interface BoundsFactory<T extends Number, R extends MinMaxBounds<T>> {
        R create(Optional<T> p_298287_, Optional<T> p_299044_);
    }

    @FunctionalInterface
    public interface BoundsFromReaderFactory<T extends Number, R extends MinMaxBounds<T>> {
        R create(StringReader p_55333_, Optional<T> p_298885_, Optional<T> p_298613_) throws CommandSyntaxException;
    }

    public static record Doubles(Optional<Double> min, Optional<Double> max, Optional<Double> minSq, Optional<Double> maxSq) implements MinMaxBounds<Double> {
        public static final MinMaxBounds.Doubles ANY = new MinMaxBounds.Doubles(Optional.empty(), Optional.empty());
        public static final Codec<MinMaxBounds.Doubles> CODEC = MinMaxBounds.<Double, MinMaxBounds.Doubles>createCodec(Codec.DOUBLE, MinMaxBounds.Doubles::new);

        private Doubles(Optional<Double> p_298243_, Optional<Double> p_299159_) {
            this(p_298243_, p_299159_, squareOpt(p_298243_), squareOpt(p_299159_));
        }

        private static MinMaxBounds.Doubles create(StringReader p_154796_, Optional<Double> p_298478_, Optional<Double> p_298476_) throws CommandSyntaxException {
            if (p_298478_.isPresent() && p_298476_.isPresent() && p_298478_.get() > p_298476_.get()) {
                throw ERROR_SWAPPED.createWithContext(p_154796_);
            } else {
                return new MinMaxBounds.Doubles(p_298478_, p_298476_);
            }
        }

        private static Optional<Double> squareOpt(Optional<Double> p_298534_) {
            return p_298534_.map(p_297908_ -> p_297908_ * p_297908_);
        }

        public static MinMaxBounds.Doubles exactly(double p_154787_) {
            return new MinMaxBounds.Doubles(Optional.of(p_154787_), Optional.of(p_154787_));
        }

        public static MinMaxBounds.Doubles between(double p_154789_, double p_154790_) {
            return new MinMaxBounds.Doubles(Optional.of(p_154789_), Optional.of(p_154790_));
        }

        public static MinMaxBounds.Doubles atLeast(double p_154805_) {
            return new MinMaxBounds.Doubles(Optional.of(p_154805_), Optional.empty());
        }

        public static MinMaxBounds.Doubles atMost(double p_154809_) {
            return new MinMaxBounds.Doubles(Optional.empty(), Optional.of(p_154809_));
        }

        public boolean matches(double p_154811_) {
            return this.min.isPresent() && this.min.get() > p_154811_ ? false : this.max.isEmpty() || !(this.max.get() < p_154811_);
        }

        public boolean matchesSqr(double p_154813_) {
            return this.minSq.isPresent() && this.minSq.get() > p_154813_ ? false : this.maxSq.isEmpty() || !(this.maxSq.get() < p_154813_);
        }

        public static MinMaxBounds.Doubles fromReader(StringReader p_154794_) throws CommandSyntaxException {
            return fromReader(p_154794_, p_154807_ -> p_154807_);
        }

        public static MinMaxBounds.Doubles fromReader(StringReader p_154800_, Function<Double, Double> p_154801_) throws CommandSyntaxException {
            return MinMaxBounds.fromReader(
                p_154800_, MinMaxBounds.Doubles::create, Double::parseDouble, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidDouble, p_154801_
            );
        }
    }

    public static record Ints(Optional<Integer> min, Optional<Integer> max, Optional<Long> minSq, Optional<Long> maxSq) implements MinMaxBounds<Integer> {
        public static final MinMaxBounds.Ints ANY = new MinMaxBounds.Ints(Optional.empty(), Optional.empty());
        public static final Codec<MinMaxBounds.Ints> CODEC = MinMaxBounds.<Integer, MinMaxBounds.Ints>createCodec(Codec.INT, MinMaxBounds.Ints::new);

        private Ints(Optional<Integer> p_298275_, Optional<Integer> p_298272_) {
            this(p_298275_, p_298272_, p_298275_.map(p_297910_ -> p_297910_.longValue() * p_297910_.longValue()), squareOpt(p_298272_));
        }

        private static MinMaxBounds.Ints create(StringReader p_55378_, Optional<Integer> p_298250_, Optional<Integer> p_298579_) throws CommandSyntaxException {
            if (p_298250_.isPresent() && p_298579_.isPresent() && p_298250_.get() > p_298579_.get()) {
                throw ERROR_SWAPPED.createWithContext(p_55378_);
            } else {
                return new MinMaxBounds.Ints(p_298250_, p_298579_);
            }
        }

        private static Optional<Long> squareOpt(Optional<Integer> p_298733_) {
            return p_298733_.map(p_297909_ -> p_297909_.longValue() * p_297909_.longValue());
        }

        public static MinMaxBounds.Ints exactly(int p_55372_) {
            return new MinMaxBounds.Ints(Optional.of(p_55372_), Optional.of(p_55372_));
        }

        public static MinMaxBounds.Ints between(int p_154815_, int p_154816_) {
            return new MinMaxBounds.Ints(Optional.of(p_154815_), Optional.of(p_154816_));
        }

        public static MinMaxBounds.Ints atLeast(int p_55387_) {
            return new MinMaxBounds.Ints(Optional.of(p_55387_), Optional.empty());
        }

        public static MinMaxBounds.Ints atMost(int p_154820_) {
            return new MinMaxBounds.Ints(Optional.empty(), Optional.of(p_154820_));
        }

        public boolean matches(int p_55391_) {
            return this.min.isPresent() && this.min.get() > p_55391_ ? false : this.max.isEmpty() || this.max.get() >= p_55391_;
        }

        public boolean matchesSqr(long p_154818_) {
            return this.minSq.isPresent() && this.minSq.get() > p_154818_ ? false : this.maxSq.isEmpty() || this.maxSq.get() >= p_154818_;
        }

        public static MinMaxBounds.Ints fromReader(StringReader p_55376_) throws CommandSyntaxException {
            return fromReader(p_55376_, p_55389_ -> p_55389_);
        }

        public static MinMaxBounds.Ints fromReader(StringReader p_55382_, Function<Integer, Integer> p_55383_) throws CommandSyntaxException {
            return MinMaxBounds.fromReader(
                p_55382_, MinMaxBounds.Ints::create, Integer::parseInt, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidInt, p_55383_
            );
        }
    }
}
