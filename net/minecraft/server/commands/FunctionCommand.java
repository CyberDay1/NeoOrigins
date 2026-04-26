package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;

public class FunctionCommand {
    private static final DynamicCommandExceptionType ERROR_ARGUMENT_NOT_COMPOUND = new DynamicCommandExceptionType(
        p_304240_ -> Component.translatableEscape("commands.function.error.argument_not_compound", p_304240_)
    );
    static final DynamicCommandExceptionType ERROR_NO_FUNCTIONS = new DynamicCommandExceptionType(
        p_305708_ -> Component.translatableEscape("commands.function.scheduled.no_functions", p_305708_)
    );
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType(
        (p_305709_, p_305710_) -> Component.translatableEscape("commands.function.instantiationFailure", p_305709_, p_305710_)
    );
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_FUNCTION = (p_137719_, p_137720_) -> {
        ServerFunctionManager serverfunctionmanager = p_137719_.getSource().getServer().getFunctions();
        SharedSuggestionProvider.suggestResource(serverfunctionmanager.getTagNames(), p_137720_, "#");
        return SharedSuggestionProvider.suggestResource(serverfunctionmanager.getFunctionNames(), p_137720_);
    };
    static final FunctionCommand.Callbacks<CommandSourceStack> FULL_CONTEXT_CALLBACKS = new FunctionCommand.Callbacks<CommandSourceStack>() {
        public void signalResult(CommandSourceStack p_305828_, ResourceLocation p_306288_, int p_306112_) {
            p_305828_.sendSuccess(() -> Component.translatable("commands.function.result", Component.translationArg(p_306288_), p_306112_), true);
        }
    };

    public static void register(CommandDispatcher<CommandSourceStack> p_137715_) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("with");

        for (DataCommands.DataProvider datacommands$dataprovider : DataCommands.SOURCE_PROVIDERS) {
            datacommands$dataprovider.wrap(literalargumentbuilder, p_305702_ -> p_305702_.executes(new FunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> p_306295_) throws CommandSyntaxException {
                        return datacommands$dataprovider.access(p_306295_).getData();
                    }
                }).then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(new FunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> p_306208_) throws CommandSyntaxException {
                        return FunctionCommand.getArgumentTag(NbtPathArgument.getPath(p_306208_, "path"), datacommands$dataprovider.access(p_306208_));
                    }
                })));
        }

        p_137715_.register(
            Commands.literal("function")
                .requires(p_137722_ -> p_137722_.hasPermission(2))
                .then(
                    Commands.argument("name", FunctionArgument.functions()).suggests(SUGGEST_FUNCTION).executes(new FunctionCommand.FunctionCustomExecutor() {
                        @Nullable
                        @Override
                        protected CompoundTag arguments(CommandContext<CommandSourceStack> p_306232_) {
                            return null;
                        }
                    }).then(Commands.argument("arguments", CompoundTagArgument.compoundTag()).executes(new FunctionCommand.FunctionCustomExecutor() {
                        @Override
                        protected CompoundTag arguments(CommandContext<CommandSourceStack> p_305935_) {
                            return CompoundTagArgument.getCompoundTag(p_305935_, "arguments");
                        }
                    })).then(literalargumentbuilder)
                )
        );
    }

    static CompoundTag getArgumentTag(NbtPathArgument.NbtPath p_295114_, DataAccessor p_296132_) throws CommandSyntaxException {
        Tag tag = DataCommands.getSingleTag(p_295114_, p_296132_);
        if (tag instanceof CompoundTag) {
            return (CompoundTag)tag;
        } else {
            throw ERROR_ARGUMENT_NOT_COMPOUND.create(tag.getType().getName());
        }
    }

    public static CommandSourceStack modifySenderForExecution(CommandSourceStack p_305783_) {
        return p_305783_.withSuppressedOutput().withMaximumPermission(2);
    }

    public static <T extends ExecutionCommandSource<T>> void queueFunctions(
        Collection<CommandFunction<T>> p_305938_,
        @Nullable CompoundTag p_306214_,
        T p_306048_,
        T p_305876_,
        ExecutionControl<T> p_305902_,
        FunctionCommand.Callbacks<T> p_306230_,
        ChainModifiers p_309589_
    ) throws CommandSyntaxException {
        if (p_309589_.isReturn()) {
            queueFunctionsAsReturn(p_305938_, p_306214_, p_306048_, p_305876_, p_305902_, p_306230_);
        } else {
            queueFunctionsNoReturn(p_305938_, p_306214_, p_306048_, p_305876_, p_305902_, p_306230_);
        }
    }

    private static <T extends ExecutionCommandSource<T>> void instantiateAndQueueFunctions(
        @Nullable CompoundTag p_309636_,
        ExecutionControl<T> p_309536_,
        CommandDispatcher<T> p_309639_,
        T p_309605_,
        CommandFunction<T> p_309610_,
        ResourceLocation p_309719_,
        CommandResultCallback p_309544_,
        boolean p_309542_
    ) throws CommandSyntaxException {
        try {
            InstantiatedFunction<T> instantiatedfunction = p_309610_.instantiate(p_309636_, p_309639_);
            p_309536_.queueNext(new CallFunction<>(instantiatedfunction, p_309544_, p_309542_).bind(p_309605_));
        } catch (FunctionInstantiationException functioninstantiationexception) {
            throw ERROR_FUNCTION_INSTANTATION_FAILURE.create(p_309719_, functioninstantiationexception.messageComponent());
        }
    }

    private static <T extends ExecutionCommandSource<T>> CommandResultCallback decorateOutputIfNeeded(
        T p_309706_, FunctionCommand.Callbacks<T> p_309672_, ResourceLocation p_309690_, CommandResultCallback p_309608_
    ) {
        return p_309706_.isSilent() ? p_309608_ : (p_315913_, p_315914_) -> {
            p_309672_.signalResult(p_309706_, p_309690_, p_315914_);
            p_309608_.onResult(p_315913_, p_315914_);
        };
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsAsReturn(
        Collection<CommandFunction<T>> p_309534_,
        @Nullable CompoundTag p_309696_,
        T p_309655_,
        T p_309547_,
        ExecutionControl<T> p_309663_,
        FunctionCommand.Callbacks<T> p_309585_
    ) throws CommandSyntaxException {
        CommandDispatcher<T> commanddispatcher = p_309655_.dispatcher();
        T t = p_309547_.clearCallbacks();
        CommandResultCallback commandresultcallback = CommandResultCallback.chain(p_309655_.callback(), p_309663_.currentFrame().returnValueConsumer());

        for (CommandFunction<T> commandfunction : p_309534_) {
            ResourceLocation resourcelocation = commandfunction.id();
            CommandResultCallback commandresultcallback1 = decorateOutputIfNeeded(p_309655_, p_309585_, resourcelocation, commandresultcallback);
            instantiateAndQueueFunctions(p_309696_, p_309663_, commanddispatcher, t, commandfunction, resourcelocation, commandresultcallback1, true);
        }

        p_309663_.queueNext(FallthroughTask.instance());
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsNoReturn(
        Collection<CommandFunction<T>> p_309573_,
        @Nullable CompoundTag p_309637_,
        T p_309693_,
        T p_309593_,
        ExecutionControl<T> p_309574_,
        FunctionCommand.Callbacks<T> p_309570_
    ) throws CommandSyntaxException {
        CommandDispatcher<T> commanddispatcher = p_309693_.dispatcher();
        T t = p_309593_.clearCallbacks();
        CommandResultCallback commandresultcallback = p_309693_.callback();
        if (!p_309573_.isEmpty()) {
            if (p_309573_.size() == 1) {
                CommandFunction<T> commandfunction = p_309573_.iterator().next();
                ResourceLocation resourcelocation = commandfunction.id();
                CommandResultCallback commandresultcallback1 = decorateOutputIfNeeded(p_309693_, p_309570_, resourcelocation, commandresultcallback);
                instantiateAndQueueFunctions(p_309637_, p_309574_, commanddispatcher, t, commandfunction, resourcelocation, commandresultcallback1, false);
            } else if (commandresultcallback == CommandResultCallback.EMPTY) {
                for (CommandFunction<T> commandfunction1 : p_309573_) {
                    ResourceLocation resourcelocation2 = commandfunction1.id();
                    CommandResultCallback commandresultcallback2 = decorateOutputIfNeeded(p_309693_, p_309570_, resourcelocation2, commandresultcallback);
                    instantiateAndQueueFunctions(p_309637_, p_309574_, commanddispatcher, t, commandfunction1, resourcelocation2, commandresultcallback2, false);
                }
            } else {
                class Accumulator {
                    boolean anyResult;
                    int sum;

                    public void add(int p_309590_) {
                        this.anyResult = true;
                        this.sum += p_309590_;
                    }
                }

                Accumulator functioncommand$1accumulator = new Accumulator();
                CommandResultCallback commandresultcallback4 = (p_309467_, p_309468_) -> functioncommand$1accumulator.add(p_309468_);

                for (CommandFunction<T> commandfunction2 : p_309573_) {
                    ResourceLocation resourcelocation1 = commandfunction2.id();
                    CommandResultCallback commandresultcallback3 = decorateOutputIfNeeded(p_309693_, p_309570_, resourcelocation1, commandresultcallback4);
                    instantiateAndQueueFunctions(p_309637_, p_309574_, commanddispatcher, t, commandfunction2, resourcelocation1, commandresultcallback3, false);
                }

                p_309574_.queueNext((p_309471_, p_309472_) -> {
                    if (functioncommand$1accumulator.anyResult) {
                        commandresultcallback.onSuccess(functioncommand$1accumulator.sum);
                    }
                });
            }
        }
    }

    public interface Callbacks<T> {
        void signalResult(T p_306084_, ResourceLocation p_306003_, int p_305926_);
    }

    abstract static class FunctionCustomExecutor
        extends CustomCommandExecutor.WithErrorHandling<CommandSourceStack>
        implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {
        @Nullable
        protected abstract CompoundTag arguments(CommandContext<CommandSourceStack> p_306010_) throws CommandSyntaxException;

        public void runGuarded(
            CommandSourceStack p_305800_, ContextChain<CommandSourceStack> p_305848_, ChainModifiers p_309662_, ExecutionControl<CommandSourceStack> p_306013_
        ) throws CommandSyntaxException {
            CommandContext<CommandSourceStack> commandcontext = p_305848_.getTopContext().copyFor(p_305800_);
            Pair<ResourceLocation, Collection<CommandFunction<CommandSourceStack>>> pair = FunctionArgument.getFunctionCollection(commandcontext, "name");
            Collection<CommandFunction<CommandSourceStack>> collection = pair.getSecond();
            if (collection.isEmpty()) {
                throw FunctionCommand.ERROR_NO_FUNCTIONS.create(Component.translationArg(pair.getFirst()));
            } else {
                CompoundTag compoundtag = this.arguments(commandcontext);
                CommandSourceStack commandsourcestack = FunctionCommand.modifySenderForExecution(p_305800_);
                if (collection.size() == 1) {
                    p_305800_.sendSuccess(
                        () -> Component.translatable("commands.function.scheduled.single", Component.translationArg(collection.iterator().next().id())), true
                    );
                } else {
                    p_305800_.sendSuccess(
                        () -> Component.translatable(
                                "commands.function.scheduled.multiple",
                                ComponentUtils.formatList(collection.stream().map(CommandFunction::id).toList(), Component::translationArg)
                            ),
                        true
                    );
                }

                FunctionCommand.queueFunctions(
                    collection, compoundtag, p_305800_, commandsourcestack, p_306013_, FunctionCommand.FULL_CONTEXT_CALLBACKS, p_309662_
                );
            }
        }
    }
}
