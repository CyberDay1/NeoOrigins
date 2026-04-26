package net.minecraft.commands.functions;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.resources.ResourceLocation;

class FunctionBuilder<T extends ExecutionCommandSource<T>> {
    @Nullable
    private List<UnboundEntryAction<T>> plainEntries = new ArrayList<>();
    @Nullable
    private List<MacroFunction.Entry<T>> macroEntries;
    private final List<String> macroArguments = new ArrayList<>();

    public void addCommand(UnboundEntryAction<T> p_305910_) {
        if (this.macroEntries != null) {
            this.macroEntries.add(new MacroFunction.PlainTextEntry<>(p_305910_));
        } else {
            this.plainEntries.add(p_305910_);
        }
    }

    private int getArgumentIndex(String p_305915_) {
        int i = this.macroArguments.indexOf(p_305915_);
        if (i == -1) {
            i = this.macroArguments.size();
            this.macroArguments.add(p_305915_);
        }

        return i;
    }

    private IntList convertToIndices(List<String> p_306261_) {
        IntArrayList intarraylist = new IntArrayList(p_306261_.size());

        for (String s : p_306261_) {
            intarraylist.add(this.getArgumentIndex(s));
        }

        return intarraylist;
    }

    public void addMacro(String p_305989_, int p_306173_, T p_316255_) {
        StringTemplate stringtemplate = StringTemplate.fromString(p_305989_, p_306173_);
        if (this.plainEntries != null) {
            this.macroEntries = new ArrayList<>(this.plainEntries.size() + 1);

            for (UnboundEntryAction<T> unboundentryaction : this.plainEntries) {
                this.macroEntries.add(new MacroFunction.PlainTextEntry<>(unboundentryaction));
            }

            this.plainEntries = null;
        }

        this.macroEntries.add(new MacroFunction.MacroEntry<>(stringtemplate, this.convertToIndices(stringtemplate.variables()), p_316255_));
    }

    public CommandFunction<T> build(ResourceLocation p_306219_) {
        return (CommandFunction<T>)(this.macroEntries != null
            ? new MacroFunction<>(p_306219_, this.macroEntries, this.macroArguments)
            : new PlainTextFunction<>(p_306219_, this.plainEntries));
    }
}
