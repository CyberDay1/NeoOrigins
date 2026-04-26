package net.minecraft.util.parsing.packrat;

import java.util.ArrayList;
import java.util.List;

public interface ErrorCollector<S> {
    void store(int p_335897_, SuggestionSupplier<S> p_336077_, Object p_335991_);

    default void store(int p_335680_, Object p_335827_) {
        this.store(p_335680_, SuggestionSupplier.empty(), p_335827_);
    }

    void finish(int p_335723_);

    public static class LongestOnly<S> implements ErrorCollector<S> {
        private final List<ErrorEntry<S>> entries = new ArrayList<>();
        private int lastCursor = -1;

        private void discardErrorsFromShorterParse(int p_335634_) {
            if (p_335634_ > this.lastCursor) {
                this.lastCursor = p_335634_;
                this.entries.clear();
            }
        }

        @Override
        public void finish(int p_335534_) {
            this.discardErrorsFromShorterParse(p_335534_);
        }

        @Override
        public void store(int p_335763_, SuggestionSupplier<S> p_336144_, Object p_335736_) {
            this.discardErrorsFromShorterParse(p_335763_);
            if (p_335763_ == this.lastCursor) {
                this.entries.add(new ErrorEntry<>(p_335763_, p_336144_, p_335736_));
            }
        }

        public List<ErrorEntry<S>> entries() {
            return this.entries;
        }

        public int cursor() {
            return this.lastCursor;
        }
    }
}
