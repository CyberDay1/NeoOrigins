package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.util.TimeUtil;

public class TickCommand {
    private static final float MAX_TICKRATE = 10000.0F;
    private static final String DEFAULT_TICKRATE = String.valueOf(20);

    public static void register(CommandDispatcher<CommandSourceStack> p_309001_) {
        p_309001_.register(
            Commands.literal("tick")
                .requires(p_308941_ -> p_308941_.hasPermission(3))
                .then(Commands.literal("query").executes(p_308950_ -> tickQuery(p_308950_.getSource())))
                .then(
                    Commands.literal("rate")
                        .then(
                            Commands.argument("rate", FloatArgumentType.floatArg(1.0F, 10000.0F))
                                .suggests((p_308897_, p_308880_) -> SharedSuggestionProvider.suggest(new String[]{DEFAULT_TICKRATE}, p_308880_))
                                .executes(p_309119_ -> setTickingRate(p_309119_.getSource(), FloatArgumentType.getFloat(p_309119_, "rate")))
                        )
                )
                .then(
                    Commands.literal("step")
                        .executes(p_309496_ -> step(p_309496_.getSource(), 1))
                        .then(Commands.literal("stop").executes(p_309035_ -> stopStepping(p_309035_.getSource())))
                        .then(
                            Commands.argument("time", TimeArgument.time(1))
                                .suggests((p_309113_, p_309105_) -> SharedSuggestionProvider.suggest(new String[]{"1t", "1s"}, p_309105_))
                                .executes(p_308930_ -> step(p_308930_.getSource(), IntegerArgumentType.getInteger(p_308930_, "time")))
                        )
                )
                .then(
                    Commands.literal("sprint")
                        .then(Commands.literal("stop").executes(p_309190_ -> stopSprinting(p_309190_.getSource())))
                        .then(
                            Commands.argument("time", TimeArgument.time(1))
                                .suggests((p_308987_, p_309101_) -> SharedSuggestionProvider.suggest(new String[]{"60s", "1d", "3d"}, p_309101_))
                                .executes(p_308904_ -> sprint(p_308904_.getSource(), IntegerArgumentType.getInteger(p_308904_, "time")))
                        )
                )
                .then(Commands.literal("unfreeze").executes(p_309184_ -> setFreeze(p_309184_.getSource(), false)))
                .then(Commands.literal("freeze").executes(p_309070_ -> setFreeze(p_309070_.getSource(), true)))
        );
    }

    private static String nanosToMilisString(long p_308883_) {
        return String.format("%.1f", (float)p_308883_ / (float)TimeUtil.NANOSECONDS_PER_MILLISECOND);
    }

    private static int setTickingRate(CommandSourceStack p_309112_, float p_309104_) {
        ServerTickRateManager servertickratemanager = p_309112_.getServer().tickRateManager();
        servertickratemanager.setTickRate(p_309104_);
        String s = String.format("%.1f", p_309104_);
        p_309112_.sendSuccess(() -> Component.translatable("commands.tick.rate.success", s), true);
        return (int)p_309104_;
    }

    private static int tickQuery(CommandSourceStack p_309091_) {
        ServerTickRateManager servertickratemanager = p_309091_.getServer().tickRateManager();
        String s = nanosToMilisString(p_309091_.getServer().getAverageTickTimeNanos());
        float f = servertickratemanager.tickrate();
        String s1 = String.format("%.1f", f);
        if (servertickratemanager.isSprinting()) {
            p_309091_.sendSuccess(() -> Component.translatable("commands.tick.status.sprinting"), false);
            p_309091_.sendSuccess(() -> Component.translatable("commands.tick.query.rate.sprinting", s1, s), false);
        } else {
            if (servertickratemanager.isFrozen()) {
                p_309091_.sendSuccess(() -> Component.translatable("commands.tick.status.frozen"), false);
            } else if (servertickratemanager.nanosecondsPerTick() < p_309091_.getServer().getAverageTickTimeNanos()) {
                p_309091_.sendSuccess(() -> Component.translatable("commands.tick.status.lagging"), false);
            } else {
                p_309091_.sendSuccess(() -> Component.translatable("commands.tick.status.running"), false);
            }

            String s2 = nanosToMilisString(servertickratemanager.nanosecondsPerTick());
            p_309091_.sendSuccess(() -> Component.translatable("commands.tick.query.rate.running", s1, s, s2), false);
        }

        long[] along = Arrays.copyOf(p_309091_.getServer().getTickTimesNanos(), p_309091_.getServer().getTickTimesNanos().length);
        Arrays.sort(along);
        String s3 = nanosToMilisString(along[along.length / 2]);
        String s4 = nanosToMilisString(along[(int)((double)along.length * 0.95)]);
        String s5 = nanosToMilisString(along[(int)((double)along.length * 0.99)]);
        p_309091_.sendSuccess(() -> Component.translatable("commands.tick.query.percentiles", s3, s4, s5, along.length), false);
        return (int)f;
    }

    private static int sprint(CommandSourceStack p_309049_, int p_308892_) {
        boolean flag = p_309049_.getServer().tickRateManager().requestGameToSprint(p_308892_);
        if (flag) {
            p_309049_.sendSuccess(() -> Component.translatable("commands.tick.sprint.stop.success"), true);
        }

        p_309049_.sendSuccess(() -> Component.translatable("commands.tick.status.sprinting"), true);
        return 1;
    }

    private static int setFreeze(CommandSourceStack p_309056_, boolean p_309141_) {
        ServerTickRateManager servertickratemanager = p_309056_.getServer().tickRateManager();
        if (p_309141_) {
            if (servertickratemanager.isSprinting()) {
                servertickratemanager.stopSprinting();
            }

            if (servertickratemanager.isSteppingForward()) {
                servertickratemanager.stopStepping();
            }
        }

        servertickratemanager.setFrozen(p_309141_);
        if (p_309141_) {
            p_309056_.sendSuccess(() -> Component.translatable("commands.tick.status.frozen"), true);
        } else {
            p_309056_.sendSuccess(() -> Component.translatable("commands.tick.status.running"), true);
        }

        return p_309141_ ? 1 : 0;
    }

    private static int step(CommandSourceStack p_309024_, int p_309080_) {
        ServerTickRateManager servertickratemanager = p_309024_.getServer().tickRateManager();
        boolean flag = servertickratemanager.stepGameIfPaused(p_309080_);
        if (flag) {
            p_309024_.sendSuccess(() -> Component.translatable("commands.tick.step.success", p_309080_), true);
        } else {
            p_309024_.sendFailure(Component.translatable("commands.tick.step.fail"));
        }

        return 1;
    }

    private static int stopStepping(CommandSourceStack p_308875_) {
        ServerTickRateManager servertickratemanager = p_308875_.getServer().tickRateManager();
        boolean flag = servertickratemanager.stopStepping();
        if (flag) {
            p_308875_.sendSuccess(() -> Component.translatable("commands.tick.step.stop.success"), true);
            return 1;
        } else {
            p_308875_.sendFailure(Component.translatable("commands.tick.step.stop.fail"));
            return 0;
        }
    }

    private static int stopSprinting(CommandSourceStack p_308870_) {
        ServerTickRateManager servertickratemanager = p_308870_.getServer().tickRateManager();
        boolean flag = servertickratemanager.stopSprinting();
        if (flag) {
            p_308870_.sendSuccess(() -> Component.translatable("commands.tick.sprint.stop.success"), true);
            return 1;
        } else {
            p_308870_.sendFailure(Component.translatable("commands.tick.sprint.stop.fail"));
            return 0;
        }
    }
}
