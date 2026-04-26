package net.minecraft.resources;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.util.ExtraCodecs;

public class RegistryOps<T> extends DelegatingOps<T> {
    public final RegistryOps.RegistryInfoLookup lookupProvider;

    public static <T> RegistryOps<T> create(DynamicOps<T> p_256342_, HolderLookup.Provider p_255950_) {
        return create(p_256342_, new RegistryOps.HolderLookupAdapter(p_255950_));
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> p_256278_, RegistryOps.RegistryInfoLookup p_256479_) {
        return new RegistryOps<>(p_256278_, p_256479_);
    }

    public static <T> Dynamic<T> injectRegistryContext(Dynamic<T> p_323625_, HolderLookup.Provider p_324054_) {
        return new Dynamic<>(p_324054_.createSerializationContext(p_323625_.getOps()), p_323625_.getValue());
    }

    protected RegistryOps(DynamicOps<T> p_256313_, RegistryOps.RegistryInfoLookup p_255799_) {
        super(p_256313_);
        this.lookupProvider = p_255799_;
    }

    protected RegistryOps(RegistryOps<T> other) {
        super(other);
        this.lookupProvider = other.lookupProvider;
    }

    public <U> RegistryOps<U> withParent(DynamicOps<U> p_330654_) {
        return (RegistryOps<U>)(p_330654_ == this.delegate ? this : new RegistryOps<>(p_330654_, this.lookupProvider));
    }

    public <E> Optional<HolderOwner<E>> owner(ResourceKey<? extends Registry<? extends E>> p_255757_) {
        return this.lookupProvider.lookup(p_255757_).map(RegistryOps.RegistryInfo::owner);
    }

    public <E> Optional<HolderGetter<E>> getter(ResourceKey<? extends Registry<? extends E>> p_256031_) {
        return this.lookupProvider.lookup(p_256031_).map(RegistryOps.RegistryInfo::getter);
    }

    @Override
    public boolean equals(Object p_341917_) {
        if (this == p_341917_) {
            return true;
        } else if (p_341917_ != null && this.getClass() == p_341917_.getClass()) {
            RegistryOps<?> registryops = (RegistryOps<?>)p_341917_;
            return this.delegate.equals(registryops.delegate) && this.lookupProvider.equals(registryops.lookupProvider);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode() * 31 + this.lookupProvider.hashCode();
    }

    public static <E, O> RecordCodecBuilder<O, HolderGetter<E>> retrieveGetter(ResourceKey<? extends Registry<? extends E>> p_206833_) {
        return ExtraCodecs.retrieveContext(
                p_274811_ -> p_274811_ instanceof RegistryOps<?> registryops
                        ? registryops.lookupProvider
                            .lookup(p_206833_)
                            .map(p_255527_ -> DataResult.success(p_255527_.getter(), p_255527_.elementsLifecycle()))
                            .orElseGet(() -> DataResult.error(() -> "Unknown registry: " + p_206833_))
                        : DataResult.error(() -> "Not a registry ops")
            )
            .forGetter(p_255526_ -> null);
    }

    public static <E> com.mojang.serialization.MapCodec<HolderLookup.RegistryLookup<E>> retrieveRegistryLookup(ResourceKey<? extends Registry<? extends E>> resourceKey) {
        return ExtraCodecs.retrieveContext(ops -> {
            if (!(ops instanceof RegistryOps<?> registryOps))
                return DataResult.error(() -> "Not a registry ops");

            return registryOps.lookupProvider.lookup(resourceKey).map(registryInfo -> {
                if (!(registryInfo.owner() instanceof HolderLookup.RegistryLookup<E> registryLookup))
                    return DataResult.<HolderLookup.RegistryLookup<E>>error(() -> "Found holder getter but was not a registry lookup for " + resourceKey);

                return DataResult.success(registryLookup, registryInfo.elementsLifecycle());
            }).orElseGet(() -> DataResult.error(() -> "Unknown registry: " + resourceKey));
        });
    }

    public static <E, O> RecordCodecBuilder<O, Holder.Reference<E>> retrieveElement(ResourceKey<E> p_256347_) {
        ResourceKey<? extends Registry<E>> resourcekey = ResourceKey.createRegistryKey(p_256347_.registry());
        return ExtraCodecs.retrieveContext(
                p_274808_ -> p_274808_ instanceof RegistryOps<?> registryops
                        ? registryops.lookupProvider
                            .lookup(resourcekey)
                            .flatMap(p_255518_ -> p_255518_.getter().get(p_256347_))
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "Can't find value: " + p_256347_))
                        : DataResult.error(() -> "Not a registry ops")
            )
            .forGetter(p_255524_ -> null);
    }

    public static final class HolderLookupAdapter implements RegistryOps.RegistryInfoLookup {
        public final HolderLookup.Provider lookupProvider;
        private final Map<ResourceKey<? extends Registry<?>>, Optional<? extends RegistryOps.RegistryInfo<?>>> lookups = new ConcurrentHashMap<>();

        public HolderLookupAdapter(HolderLookup.Provider p_341886_) {
            this.lookupProvider = p_341886_;
        }

        @Override
        public <E> Optional<RegistryOps.RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> p_341913_) {
            return (Optional<RegistryOps.RegistryInfo<E>>)this.lookups.computeIfAbsent(p_341913_, this::createLookup);
        }

        private Optional<RegistryOps.RegistryInfo<Object>> createLookup(ResourceKey<? extends Registry<?>> p_341910_) {
            return this.lookupProvider.lookup(p_341910_).map(RegistryOps.RegistryInfo::fromRegistryLookup);
        }

        @Override
        public boolean equals(Object p_341924_) {
            if (this == p_341924_) {
                return true;
            } else {
                if (p_341924_ instanceof RegistryOps.HolderLookupAdapter registryops$holderlookupadapter
                    && this.lookupProvider.equals(registryops$holderlookupadapter.lookupProvider)) {
                    return true;
                }

                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.lookupProvider.hashCode();
        }
    }

    public static record RegistryInfo<T>(HolderOwner<T> owner, HolderGetter<T> getter, Lifecycle elementsLifecycle) {
        public static <T> RegistryOps.RegistryInfo<T> fromRegistryLookup(HolderLookup.RegistryLookup<T> p_326797_) {
            return new RegistryOps.RegistryInfo<>(p_326797_, p_326797_, p_326797_.registryLifecycle());
        }
    }

    public interface RegistryInfoLookup {
        <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> p_256623_);
    }
}
