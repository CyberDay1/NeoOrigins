package net.minecraft.util.parsing.packrat;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Objects;
import javax.annotation.Nullable;

public final class Scope {
    private final Object2ObjectMap<Atom<?>, Object> values = new Object2ObjectArrayMap<>();

    public <T> void put(Atom<T> p_335874_, @Nullable T p_335802_) {
        this.values.put(p_335874_, p_335802_);
    }

    @Nullable
    public <T> T get(Atom<T> p_335532_) {
        return (T)this.values.get(p_335532_);
    }

    public <T> T getOrThrow(Atom<T> p_335438_) {
        return Objects.requireNonNull(this.get(p_335438_));
    }

    public <T> T getOrDefault(Atom<T> p_336076_, T p_336135_) {
        return Objects.requireNonNullElse(this.get(p_336076_), p_336135_);
    }

    @Nullable
    @SafeVarargs
    public final <T> T getAny(Atom<T>... p_335905_) {
        for (Atom<T> atom : p_335905_) {
            T t = this.get(atom);
            if (t != null) {
                return t;
            }
        }

        return null;
    }

    @SafeVarargs
    public final <T> T getAnyOrThrow(Atom<T>... p_336028_) {
        return Objects.requireNonNull(this.getAny(p_336028_));
    }

    @Override
    public String toString() {
        return this.values.toString();
    }

    public void putAll(Scope p_335805_) {
        this.values.putAll(p_335805_.values);
    }

    @Override
    public boolean equals(Object p_335524_) {
        if (this == p_335524_) {
            return true;
        } else {
            return p_335524_ instanceof Scope scope ? this.values.equals(scope.values) : false;
        }
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }
}
