package net.minecraft.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public interface ProblemReporter {
    ProblemReporter forChild(String p_312211_);

    void report(String p_312410_);

    public static class Collector implements ProblemReporter {
        private final Multimap<String, String> problems;
        private final Supplier<String> path;
        @Nullable
        private String pathCache;

        public Collector() {
            this(HashMultimap.create(), () -> "");
        }

        private Collector(Multimap<String, String> p_312167_, Supplier<String> p_312423_) {
            this.problems = p_312167_;
            this.path = p_312423_;
        }

        private String getPath() {
            if (this.pathCache == null) {
                this.pathCache = this.path.get();
            }

            return this.pathCache;
        }

        @Override
        public ProblemReporter forChild(String p_312763_) {
            return new ProblemReporter.Collector(this.problems, () -> this.getPath() + p_312763_);
        }

        @Override
        public void report(String p_312850_) {
            this.problems.put(this.getPath(), p_312850_);
        }

        public Multimap<String, String> get() {
            return ImmutableMultimap.copyOf(this.problems);
        }

        public Optional<String> getReport() {
            Multimap<String, String> multimap = this.get();
            if (!multimap.isEmpty()) {
                String s = multimap.asMap()
                    .entrySet()
                    .stream()
                    .map(p_344277_ -> " at " + p_344277_.getKey() + ": " + String.join("; ", p_344277_.getValue()))
                    .collect(Collectors.joining("\n"));
                return Optional.of(s);
            } else {
                return Optional.empty();
            }
        }
    }
}
