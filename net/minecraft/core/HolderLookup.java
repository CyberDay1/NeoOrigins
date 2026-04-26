package net.minecraft.core;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlagSet;

public interface HolderLookup<T> extends HolderGetter<T> {
    Stream<Holder.Reference<T>> listElements();

    default Stream<ResourceKey<T>> listElementIds() {
        return this.listElements().map(Holder.Reference::key);
    }

    Stream<HolderSet.Named<T>> listTags();

    default Stream<TagKey<T>> listTagIds() {
        return this.listTags().map(HolderSet.Named::key);
    }

    public interface Provider extends net.neoforged.neoforge.common.extensions.IHolderLookupProviderExtension {
        Stream<ResourceKey<? extends Registry<?>>> listRegistries();

        <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> p_256285_);

        default <T> HolderLookup.RegistryLookup<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> p_255957_) {
            return this.lookup(p_255957_).orElseThrow(() -> new IllegalStateException("Registry " + p_255957_.location() + " not found"));
        }

        default <V> RegistryOps<V> createSerializationContext(DynamicOps<V> p_326817_) {
            return RegistryOps.create(p_326817_, this);
        }

        default HolderGetter.Provider asGetterLookup() {
            return new HolderGetter.Provider() {
                @Override
                public <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> p_256379_) {
                    return Provider.this.lookup(p_256379_).map(p_255952_ -> (HolderGetter<T>)p_255952_);
                }
            };
        }

        static HolderLookup.Provider create(Stream<HolderLookup.RegistryLookup<?>> p_256054_) {
            final Map<ResourceKey<? extends Registry<?>>, HolderLookup.RegistryLookup<?>> map = p_256054_.collect(
                Collectors.toUnmodifiableMap(HolderLookup.RegistryLookup::key, p_256335_ -> p_256335_)
            );
            return new HolderLookup.Provider() {
                @Override
                public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
                    return map.keySet().stream();
                }

                @Override
                public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> p_255663_) {
                    return Optional.ofNullable((HolderLookup.RegistryLookup<T>)map.get(p_255663_));
                }
            };
        }
    }

    public interface RegistryLookup<T> extends HolderLookup<T>, HolderOwner<T> {
        ResourceKey<? extends Registry<? extends T>> key();

        Lifecycle registryLifecycle();

        default HolderLookup.RegistryLookup<T> filterFeatures(FeatureFlagSet p_249397_) {
            return FeatureElement.FILTERED_REGISTRIES.contains(this.key())
                ? this.filterElements(p_250240_ -> ((FeatureElement)p_250240_).isEnabled(p_249397_))
                : this;
        }

        default HolderLookup.RegistryLookup<T> filterElements(final Predicate<T> p_324043_) {
            return new HolderLookup.RegistryLookup.Delegate<T>() {
                @Override
                public HolderLookup.RegistryLookup<T> parent() {
                    return RegistryLookup.this;
                }

                @Override
                public Optional<Holder.Reference<T>> get(ResourceKey<T> p_323784_) {
                    return this.parent().get(p_323784_).filter(p_324360_ -> p_324043_.test(p_324360_.value()));
                }

                @Override
                public Stream<Holder.Reference<T>> listElements() {
                    return this.parent().listElements().filter(p_324273_ -> p_324043_.test(p_324273_.value()));
                }
            };
        }

        @org.jetbrains.annotations.Nullable
        default <A> A getData(net.neoforged.neoforge.registries.datamaps.DataMapType<T, A> attachment, ResourceKey<T> key) {
            return null;
        }

        public interface Delegate<T> extends HolderLookup.RegistryLookup<T> {
            HolderLookup.RegistryLookup<T> parent();

            @Override
            default ResourceKey<? extends Registry<? extends T>> key() {
                return this.parent().key();
            }

            @Override
            default Lifecycle registryLifecycle() {
                return this.parent().registryLifecycle();
            }

            @Override
            default Optional<Holder.Reference<T>> get(ResourceKey<T> p_255619_) {
                return this.parent().get(p_255619_);
            }

            @Override
            default Stream<Holder.Reference<T>> listElements() {
                return this.parent().listElements();
            }

            @Override
            default Optional<HolderSet.Named<T>> get(TagKey<T> p_256245_) {
                return this.parent().get(p_256245_);
            }

            @Override
            default Stream<HolderSet.Named<T>> listTags() {
                return this.parent().listTags();
            }

            @Override
            @org.jetbrains.annotations.Nullable
            default <A> A getData(net.neoforged.neoforge.registries.datamaps.DataMapType<T, A> attachment, ResourceKey<T> key) {
                return parent().getData(attachment, key);
            }
        }
    }
}
