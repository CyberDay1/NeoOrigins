package net.minecraft.world.scores;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;

public interface ScoreAccess {
    int get();

    void set(int p_313946_);

    default int add(int p_313920_) {
        int i = this.get() + p_313920_;
        this.set(i);
        return i;
    }

    default int increment() {
        return this.add(1);
    }

    default void reset() {
        this.set(0);
    }

    boolean locked();

    void unlock();

    void lock();

    @Nullable
    Component display();

    void display(@Nullable Component p_313897_);

    void numberFormatOverride(@Nullable NumberFormat p_313696_);
}
