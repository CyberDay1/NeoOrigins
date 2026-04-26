package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class MacroFunction<T extends ExecutionCommandSource<T>> implements CommandFunction<T> {
    private static final DecimalFormat DECIMAL_FORMAT = Util.make(new DecimalFormat("#"), p_305932_ -> {
        p_305932_.setMaximumFractionDigits(15);
        p_305932_.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
    });
    private static final int MAX_CACHE_ENTRIES = 8;
    private final List<String> parameters;
    private final Object2ObjectLinkedOpenHashMap<List<String>, InstantiatedFunction<T>> cache = new Object2ObjectLinkedOpenHashMap<>(8, 0.25F);
    private final ResourceLocation id;
    private final List<MacroFunction.Entry<T>> entries;

    public MacroFunction(ResourceLocation p_305933_, List<MacroFunction.Entry<T>> p_305814_, List<String> p_306148_) {
        this.id = p_305933_;
        this.entries = p_305814_;
        this.parameters = p_306148_;
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public InstantiatedFunction<T> instantiate(@Nullable CompoundTag p_305810_, CommandDispatcher<T> p_306149_) throws FunctionInstantiationException {
        if (p_305810_ == null) {
            throw new FunctionInstantiationException(Component.translatable("commands.function.error.missing_arguments", Component.translationArg(this.id())));
        } else {
            List<String> list = new ArrayList<>(this.parameters.size());

            for (String s : this.parameters) {
                Tag tag = p_305810_.get(s);
                if (tag == null) {
                    throw new FunctionInstantiationException(
                        Component.translatable("commands.function.error.missing_argument", Component.translationArg(this.id()), s)
                    );
                }

                list.add(stringify(tag));
            }

            InstantiatedFunction<T> instantiatedfunction = this.cache.getAndMoveToLast(list);
            if (instantiatedfunction != null) {
                return instantiatedfunction;
            } else {
                if (this.cache.size() >= 8) {
                    this.cache.removeFirst();
                }

                InstantiatedFunction<T> instantiatedfunction1 = this.substituteAndParse(this.parameters, list, p_306149_);
                this.cache.put(list, instantiatedfunction1);
                return instantiatedfunction1;
            }
        }
    }

    private static String stringify(Tag p_305920_) {
        if (p_305920_ instanceof FloatTag floattag) {
            return DECIMAL_FORMAT.format((double)floattag.getAsFloat());
        } else if (p_305920_ instanceof DoubleTag doubletag) {
            return DECIMAL_FORMAT.format(doubletag.getAsDouble());
        } else if (p_305920_ instanceof ByteTag bytetag) {
            return String.valueOf(bytetag.getAsByte());
        } else if (p_305920_ instanceof ShortTag shorttag) {
            return String.valueOf(shorttag.getAsShort());
        } else {
            return p_305920_ instanceof LongTag longtag ? String.valueOf(longtag.getAsLong()) : p_305920_.getAsString();
        }
    }

    private static void lookupValues(List<String> p_305843_, IntList p_305967_, List<String> p_305797_) {
        p_305797_.clear();
        p_305967_.forEach(p_305951_ -> p_305797_.add(p_305843_.get(p_305951_)));
    }

    private InstantiatedFunction<T> substituteAndParse(List<String> p_306226_, List<String> p_306024_, CommandDispatcher<T> p_306281_) throws FunctionInstantiationException {
        List<UnboundEntryAction<T>> list = new ArrayList<>(this.entries.size());
        List<String> list1 = new ArrayList<>(p_306024_.size());

        for (MacroFunction.Entry<T> entry : this.entries) {
            lookupValues(p_306024_, entry.parameters(), list1);
            list.add(entry.instantiate(list1, p_306281_, this.id));
        }

        return new PlainTextFunction<>(this.id().withPath(p_305803_ -> p_305803_ + "/" + p_306226_.hashCode()), list);
    }

    interface Entry<T> {
        IntList parameters();

        UnboundEntryAction<T> instantiate(List<String> p_305908_, CommandDispatcher<T> p_305826_, ResourceLocation p_305778_) throws FunctionInstantiationException;
    }

    static class MacroEntry<T extends ExecutionCommandSource<T>> implements MacroFunction.Entry<T> {
        private final StringTemplate template;
        private final IntList parameters;
        private final T compilationContext;

        public MacroEntry(StringTemplate p_306131_, IntList p_306124_, T p_316385_) {
            this.template = p_306131_;
            this.parameters = p_306124_;
            this.compilationContext = p_316385_;
        }

        @Override
        public IntList parameters() {
            return this.parameters;
        }

        @Override
        public UnboundEntryAction<T> instantiate(List<String> p_306061_, CommandDispatcher<T> p_306059_, ResourceLocation p_306158_) throws FunctionInstantiationException {
            String s = this.template.substitute(p_306061_);

            try {
                return CommandFunction.parseCommand(p_306059_, this.compilationContext, new StringReader(s));
            } catch (CommandSyntaxException commandsyntaxexception) {
                throw new FunctionInstantiationException(
                    Component.translatable("commands.function.error.parse", Component.translationArg(p_306158_), s, commandsyntaxexception.getMessage())
                );
            }
        }
    }

    static class PlainTextEntry<T> implements MacroFunction.Entry<T> {
        private final UnboundEntryAction<T> compiledAction;

        public PlainTextEntry(UnboundEntryAction<T> p_306163_) {
            this.compiledAction = p_306163_;
        }

        @Override
        public IntList parameters() {
            return IntLists.emptyList();
        }

        @Override
        public UnboundEntryAction<T> instantiate(List<String> p_306111_, CommandDispatcher<T> p_305941_, ResourceLocation p_305903_) {
            return this.compiledAction;
        }
    }
}
