package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public interface CommandFunction<T> {
    ResourceLocation id();

    InstantiatedFunction<T> instantiate(@Nullable CompoundTag p_306231_, CommandDispatcher<T> p_305923_) throws FunctionInstantiationException;

    private static boolean shouldConcatenateNextLine(CharSequence p_306338_) {
        int i = p_306338_.length();
        return i > 0 && p_306338_.charAt(i - 1) == '\\';
    }

    static <T extends ExecutionCommandSource<T>> CommandFunction<T> fromLines(
        ResourceLocation p_306082_, CommandDispatcher<T> p_306046_, T p_305973_, List<String> p_306307_
    ) {
        FunctionBuilder<T> functionbuilder = new FunctionBuilder<>();

        for (int i = 0; i < p_306307_.size(); i++) {
            int j = i + 1;
            String s = p_306307_.get(i).trim();
            String s1;
            if (shouldConcatenateNextLine(s)) {
                StringBuilder stringbuilder = new StringBuilder(s);

                do {
                    if (++i == p_306307_.size()) {
                        throw new IllegalArgumentException("Line continuation at end of file");
                    }

                    stringbuilder.deleteCharAt(stringbuilder.length() - 1);
                    String s2 = p_306307_.get(i).trim();
                    stringbuilder.append(s2);
                    checkCommandLineLength(stringbuilder);
                } while (shouldConcatenateNextLine(stringbuilder));

                s1 = stringbuilder.toString();
            } else {
                s1 = s;
            }

            checkCommandLineLength(s1);
            StringReader stringreader = new StringReader(s1);
            if (stringreader.canRead() && stringreader.peek() != '#') {
                if (stringreader.peek() == '/') {
                    stringreader.skip();
                    if (stringreader.peek() == '/') {
                        throw new IllegalArgumentException(
                            "Unknown or invalid command '" + s1 + "' on line " + j + " (if you intended to make a comment, use '#' not '//')"
                        );
                    }

                    String s3 = stringreader.readUnquotedString();
                    throw new IllegalArgumentException(
                        "Unknown or invalid command '" + s1 + "' on line " + j + " (did you mean '" + s3 + "'? Do not use a preceding forwards slash.)"
                    );
                }

                if (stringreader.peek() == '$') {
                    functionbuilder.addMacro(s1.substring(1), j, p_305973_);
                } else {
                    try {
                        functionbuilder.addCommand(parseCommand(p_306046_, p_305973_, stringreader));
                    } catch (CommandSyntaxException commandsyntaxexception) {
                        throw new IllegalArgumentException("Whilst parsing command on line " + j + ": " + commandsyntaxexception.getMessage());
                    }
                }
            }
        }

        return functionbuilder.build(p_306082_);
    }

    static void checkCommandLineLength(CharSequence p_326091_) {
        if (p_326091_.length() > 2000000) {
            CharSequence charsequence = p_326091_.subSequence(0, Math.min(512, 2000000));
            throw new IllegalStateException("Command too long: " + p_326091_.length() + " characters, contents: " + charsequence + "...");
        }
    }

    static <T extends ExecutionCommandSource<T>> UnboundEntryAction<T> parseCommand(CommandDispatcher<T> p_306213_, T p_305785_, StringReader p_306140_) throws CommandSyntaxException {
        ParseResults<T> parseresults = p_306213_.parse(p_306140_, p_305785_);
        Commands.validateParseResults(parseresults);
        Optional<ContextChain<T>> optional = ContextChain.tryFlatten(parseresults.getContext().build(p_306140_.getString()));
        if (optional.isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseresults.getReader());
        } else {
            return new BuildContexts.Unbound<>(p_306140_.getString(), optional.get());
        }
    }
}
