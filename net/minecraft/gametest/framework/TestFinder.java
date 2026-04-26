package net.minecraft.gametest.framework;

import com.mojang.brigadier.context.CommandContext;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

public class TestFinder<T> implements StructureBlockPosFinder, TestFunctionFinder {
    static final TestFunctionFinder NO_FUNCTIONS = Stream::empty;
    static final StructureBlockPosFinder NO_STRUCTURES = Stream::empty;
    private final TestFunctionFinder testFunctionFinder;
    private final StructureBlockPosFinder structureBlockPosFinder;
    private final CommandSourceStack source;
    private final Function<TestFinder<T>, T> contextProvider;

    @Override
    public Stream<BlockPos> findStructureBlockPos() {
        return this.structureBlockPosFinder.findStructureBlockPos();
    }

    TestFinder(CommandSourceStack p_320004_, Function<TestFinder<T>, T> p_320489_, TestFunctionFinder p_320808_, StructureBlockPosFinder p_320448_) {
        this.source = p_320004_;
        this.contextProvider = p_320489_;
        this.testFunctionFinder = p_320808_;
        this.structureBlockPosFinder = p_320448_;
    }

    T get() {
        return this.contextProvider.apply(this);
    }

    public CommandSourceStack source() {
        return this.source;
    }

    @Override
    public Stream<TestFunction> findTestFunctions() {
        return this.testFunctionFinder.findTestFunctions();
    }

    public static class Builder<T> {
        private final Function<TestFinder<T>, T> contextProvider;
        private final UnaryOperator<Supplier<Stream<TestFunction>>> testFunctionFinderWrapper;
        private final UnaryOperator<Supplier<Stream<BlockPos>>> structureBlockPosFinderWrapper;

        public Builder(Function<TestFinder<T>, T> p_320939_) {
            this.contextProvider = p_320939_;
            this.testFunctionFinderWrapper = p_329857_ -> p_329857_;
            this.structureBlockPosFinderWrapper = p_329858_ -> p_329858_;
        }

        private Builder(
            Function<TestFinder<T>, T> p_331181_, UnaryOperator<Supplier<Stream<TestFunction>>> p_331301_, UnaryOperator<Supplier<Stream<BlockPos>>> p_331612_
        ) {
            this.contextProvider = p_331181_;
            this.testFunctionFinderWrapper = p_331301_;
            this.structureBlockPosFinderWrapper = p_331612_;
        }

        public TestFinder.Builder<T> createMultipleCopies(int p_330482_) {
            return new TestFinder.Builder<>(this.contextProvider, createCopies(p_330482_), createCopies(p_330482_));
        }

        private static <Q> UnaryOperator<Supplier<Stream<Q>>> createCopies(int p_330804_) {
            return p_329860_ -> {
                List<Q> list = new LinkedList<>();
                List<Q> list1 = ((Stream)p_329860_.get()).toList();

                for (int i = 0; i < p_330804_; i++) {
                    list.addAll(list1);
                }

                return list::stream;
            };
        }

        private T build(CommandSourceStack p_330622_, TestFunctionFinder p_330437_, StructureBlockPosFinder p_331860_) {
            return new TestFinder<>(
                    p_330622_,
                    this.contextProvider,
                    this.testFunctionFinderWrapper.apply(p_330437_::findTestFunctions)::get,
                    this.structureBlockPosFinderWrapper.apply(p_331860_::findStructureBlockPos)::get
                )
                .get();
        }

        public T radius(CommandContext<CommandSourceStack> p_320307_, int p_320811_) {
            CommandSourceStack commandsourcestack = p_320307_.getSource();
            BlockPos blockpos = BlockPos.containing(commandsourcestack.getPosition());
            return this.build(
                commandsourcestack, TestFinder.NO_FUNCTIONS, () -> StructureUtils.findStructureBlocks(blockpos, p_320811_, commandsourcestack.getLevel())
            );
        }

        public T nearest(CommandContext<CommandSourceStack> p_320944_) {
            CommandSourceStack commandsourcestack = p_320944_.getSource();
            BlockPos blockpos = BlockPos.containing(commandsourcestack.getPosition());
            return this.build(
                commandsourcestack,
                TestFinder.NO_FUNCTIONS,
                () -> StructureUtils.findNearestStructureBlock(blockpos, 15, commandsourcestack.getLevel()).stream()
            );
        }

        public T allNearby(CommandContext<CommandSourceStack> p_320216_) {
            CommandSourceStack commandsourcestack = p_320216_.getSource();
            BlockPos blockpos = BlockPos.containing(commandsourcestack.getPosition());
            return this.build(
                commandsourcestack, TestFinder.NO_FUNCTIONS, () -> StructureUtils.findStructureBlocks(blockpos, 200, commandsourcestack.getLevel())
            );
        }

        public T lookedAt(CommandContext<CommandSourceStack> p_320178_) {
            CommandSourceStack commandsourcestack = p_320178_.getSource();
            return this.build(
                commandsourcestack,
                TestFinder.NO_FUNCTIONS,
                () -> StructureUtils.lookedAtStructureBlockPos(
                        BlockPos.containing(commandsourcestack.getPosition()), commandsourcestack.getPlayer().getCamera(), commandsourcestack.getLevel()
                    )
            );
        }

        public T allTests(CommandContext<CommandSourceStack> p_320902_) {
            return this.build(
                p_320902_.getSource(),
                () -> GameTestRegistry.getAllTestFunctions().stream().filter(p_329855_ -> !p_329855_.manualOnly()),
                TestFinder.NO_STRUCTURES
            );
        }

        public T allTestsInClass(CommandContext<CommandSourceStack> p_320256_, String p_320231_) {
            return this.build(
                p_320256_.getSource(),
                () -> GameTestRegistry.getTestFunctionsForClassName(p_320231_).filter(p_329856_ -> !p_329856_.manualOnly()),
                TestFinder.NO_STRUCTURES
            );
        }

        public T failedTests(CommandContext<CommandSourceStack> p_320960_, boolean p_320352_) {
            return this.build(
                p_320960_.getSource(),
                () -> GameTestRegistry.getLastFailedTests().filter(p_320430_ -> !p_320352_ || p_320430_.required()),
                TestFinder.NO_STRUCTURES
            );
        }

        public T byArgument(CommandContext<CommandSourceStack> p_320475_, String p_320707_) {
            return this.build(p_320475_.getSource(), () -> Stream.of(TestFunctionArgument.getTestFunction(p_320475_, p_320707_)), TestFinder.NO_STRUCTURES);
        }

        public T locateByName(CommandContext<CommandSourceStack> p_341208_, String p_341401_) {
            CommandSourceStack commandsourcestack = p_341208_.getSource();
            BlockPos blockpos = BlockPos.containing(commandsourcestack.getPosition());
            return this.build(
                commandsourcestack,
                TestFinder.NO_FUNCTIONS,
                () -> StructureUtils.findStructureByTestFunction(blockpos, 1024, commandsourcestack.getLevel(), p_341401_)
            );
        }

        public T failedTests(CommandContext<CommandSourceStack> p_320220_) {
            return this.failedTests(p_320220_, false);
        }
    }
}
