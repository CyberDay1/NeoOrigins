package net.minecraft.util.parsing.packrat;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class Dictionary<S> {
    private final Map<Atom<?>, Rule<S, ?>> terms = new HashMap<>();

    public <T> void put(Atom<T> p_335994_, Rule<S, T> p_336190_) {
        Rule<S, ?> rule = this.terms.putIfAbsent(p_335994_, p_336190_);
        if (rule != null) {
            throw new IllegalArgumentException("Trying to override rule: " + p_335994_);
        }
    }

    public <T> void put(Atom<T> p_335571_, Term<S> p_336089_, Rule.RuleAction<S, T> p_336052_) {
        this.put(p_335571_, Rule.fromTerm(p_336089_, p_336052_));
    }

    public <T> void put(Atom<T> p_336163_, Term<S> p_335447_, Rule.SimpleRuleAction<T> p_335981_) {
        this.put(p_336163_, Rule.fromTerm(p_335447_, p_335981_));
    }

    @Nullable
    public <T> Rule<S, T> get(Atom<T> p_336153_) {
        return (Rule<S, T>)this.terms.get(p_336153_);
    }
}
