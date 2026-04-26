package net.minecraft.core;

import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public interface Holder<T> extends net.neoforged.neoforge.common.extensions.IHolderExtension<T> {
    T value();

    boolean isBound();

    boolean is(ResourceLocation p_205713_);

    boolean is(ResourceKey<T> p_205712_);

    boolean is(Predicate<ResourceKey<T>> p_205711_);

    boolean is(TagKey<T> p_205705_);

    @Deprecated
    boolean is(Holder<T> p_316447_);

    Stream<TagKey<T>> tags();

    Either<ResourceKey<T>, T> unwrap();

    Optional<ResourceKey<T>> unwrapKey();

    Holder.Kind kind();

    boolean canSerializeIn(HolderOwner<T> p_255833_);

    default String getRegisteredName() {
        return this.unwrapKey().map(p_316542_ -> p_316542_.location().toString()).orElse("[unregistered]");
    }

    static <T> Holder<T> direct(T p_205710_) {
        return new Holder.Direct<>(p_205710_);
    }

    public static record Direct<T>(T value) implements Holder<T> {
        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean is(ResourceLocation p_205727_) {
            return false;
        }

        @Override
        public boolean is(ResourceKey<T> p_205725_) {
            return false;
        }

        @Override
        public boolean is(TagKey<T> p_205719_) {
            return false;
        }

        @Override
        public boolean is(Holder<T> p_316277_) {
            return this.value.equals(p_316277_.value());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> p_205723_) {
            return false;
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.right(this.value);
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.empty();
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.DIRECT;
        }

        @Override
        public String toString() {
            return "Direct{" + this.value + "}";
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> p_256328_) {
            return true;
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return Stream.of();
        }
    }

    public static enum Kind {
        REFERENCE,
        DIRECT;
    }

    public static class Reference<T> implements Holder<T> {
        private final HolderOwner<T> owner;
        private Set<TagKey<T>> tags = Set.of();
        private final Holder.Reference.Type type;
        @Nullable
        private ResourceKey<T> key;
        @Nullable
        private T value;

        protected Reference(Holder.Reference.Type p_256425_, HolderOwner<T> p_256562_, @Nullable ResourceKey<T> p_256636_, @Nullable T p_255889_) {
            this.owner = p_256562_;
            this.type = p_256425_;
            this.key = p_256636_;
            this.value = p_255889_;
        }

        public static <T> Holder.Reference<T> createStandAlone(HolderOwner<T> p_255955_, ResourceKey<T> p_255958_) {
            return new Holder.Reference<>(Holder.Reference.Type.STAND_ALONE, p_255955_, p_255958_, null);
        }

        @Deprecated
        public static <T> Holder.Reference<T> createIntrusive(HolderOwner<T> p_256106_, @Nullable T p_255948_) {
            return new Holder.Reference<>(Holder.Reference.Type.INTRUSIVE, p_256106_, null, p_255948_);
        }

        public ResourceKey<T> key() {
            if (this.key == null) {
                throw new IllegalStateException("Trying to access unbound value '" + this.value + "' from registry " + this.owner);
            } else {
                return this.key;
            }
        }

        @Override
        public T value() {
            if (this.value == null) {
                throw new IllegalStateException("Trying to access unbound value '" + this.key + "' from registry " + this.owner);
            } else {
                return this.value;
            }
        }

        @Override
        public boolean is(ResourceLocation p_205779_) {
            return this.key().location().equals(p_205779_);
        }

        @Override
        public boolean is(ResourceKey<T> p_205774_) {
            return this.key() == p_205774_;
        }

        @Override
        public boolean is(TagKey<T> p_205760_) {
            return this.tags.contains(p_205760_);
        }

        @Override
        public boolean is(Holder<T> p_316168_) {
            return p_316168_.is(this.key());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> p_205772_) {
            return p_205772_.test(this.key());
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> p_256521_) {
            return this.owner.canSerializeIn(p_256521_);
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.left(this.key());
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.of(this.key());
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.REFERENCE;
        }

        @Override
        public boolean isBound() {
            return this.key != null && this.value != null;
        }

        void bindKey(ResourceKey<T> p_251943_) {
            if (this.key != null && p_251943_ != this.key) {
                throw new IllegalStateException("Can't change holder key: existing=" + this.key + ", new=" + p_251943_);
            } else {
                this.key = p_251943_;
            }
        }

        protected void bindValue(T p_249418_) {
            if (this.type == Holder.Reference.Type.INTRUSIVE && this.value != p_249418_) {
                throw new IllegalStateException("Can't change holder " + this.key + " value: existing=" + this.value + ", new=" + p_249418_);
            } else {
                this.value = p_249418_;
            }
        }

        @org.jetbrains.annotations.Nullable
        public <A> A getData(net.neoforged.neoforge.registries.datamaps.DataMapType<T, A> type) {
            if (owner instanceof HolderLookup.RegistryLookup<T> lookup) {
                return lookup.getData(type, key());
            }
            return null;
        }

        void bindTags(Collection<TagKey<T>> p_205770_) {
            this.tags = Set.copyOf(p_205770_);
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return this.tags.stream();
        }

        @Override
        public String toString() {
            return "Reference{" + this.key + "=" + this.value + "}";
        }

        // Neo: Add key getter that doesn't allocate
        @Override
        @org.jetbrains.annotations.Nullable
        public ResourceKey<T> getKey() {
            return this.key;
        }

        // Neo: Add DeferredHolder-compatible hashCode() overrides
        @Override
        public int hashCode() {
            return key().hashCode();
        }

        // Neo: Add DeferredHolder-compatible equals() overrides
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            return obj instanceof Holder<?> h && h.kind() == Kind.REFERENCE && h.getKey() == this.key();
        }

        // Neo: Helper method to get the registry lookup from a holder
        @Override
        @org.jetbrains.annotations.Nullable
        public HolderLookup.RegistryLookup<T> unwrapLookup() {
            return this.owner instanceof HolderLookup.RegistryLookup<T> rl ? rl : null;
        }

        protected static enum Type {
            STAND_ALONE,
            INTRUSIVE;
        }
    }
}
