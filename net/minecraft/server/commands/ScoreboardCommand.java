package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.ObjectiveCriteriaArgument;
import net.minecraft.commands.arguments.OperationArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.ScoreboardSlotArgument;
import net.minecraft.commands.arguments.StyleArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class ScoreboardCommand {
    private static final SimpleCommandExceptionType ERROR_OBJECTIVE_ALREADY_EXISTS = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.add.duplicate")
    );
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_EMPTY = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.display.alreadyEmpty")
    );
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_SET = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.display.alreadySet")
    );
    private static final SimpleCommandExceptionType ERROR_TRIGGER_ALREADY_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.players.enable.failed")
    );
    private static final SimpleCommandExceptionType ERROR_NOT_TRIGGER = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.players.enable.invalid")
    );
    private static final Dynamic2CommandExceptionType ERROR_NO_VALUE = new Dynamic2CommandExceptionType(
        (p_304296_, p_304297_) -> Component.translatableEscape("commands.scoreboard.players.get.null", p_304296_, p_304297_)
    );

    public static void register(CommandDispatcher<CommandSourceStack> p_138469_, CommandBuildContext p_324608_) {
        p_138469_.register(
            Commands.literal("scoreboard")
                .requires(p_138552_ -> p_138552_.hasPermission(2))
                .then(
                    Commands.literal("objectives")
                        .then(Commands.literal("list").executes(p_138585_ -> listObjectives(p_138585_.getSource())))
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("objective", StringArgumentType.word())
                                        .then(
                                            Commands.argument("criteria", ObjectiveCriteriaArgument.criteria())
                                                .executes(
                                                    p_138583_ -> addObjective(
                                                            p_138583_.getSource(),
                                                            StringArgumentType.getString(p_138583_, "objective"),
                                                            ObjectiveCriteriaArgument.getCriteria(p_138583_, "criteria"),
                                                            Component.literal(StringArgumentType.getString(p_138583_, "objective"))
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("displayName", ComponentArgument.textComponent(p_324608_))
                                                        .executes(
                                                            p_138581_ -> addObjective(
                                                                    p_138581_.getSource(),
                                                                    StringArgumentType.getString(p_138581_, "objective"),
                                                                    ObjectiveCriteriaArgument.getCriteria(p_138581_, "criteria"),
                                                                    ComponentArgument.getComponent(p_138581_, "displayName")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("modify")
                                .then(
                                    Commands.argument("objective", ObjectiveArgument.objective())
                                        .then(
                                            Commands.literal("displayname")
                                                .then(
                                                    Commands.argument("displayName", ComponentArgument.textComponent(p_324608_))
                                                        .executes(
                                                            p_138579_ -> setDisplayName(
                                                                    p_138579_.getSource(),
                                                                    ObjectiveArgument.getObjective(p_138579_, "objective"),
                                                                    ComponentArgument.getComponent(p_138579_, "displayName")
                                                                )
                                                        )
                                                )
                                        )
                                        .then(createRenderTypeModify())
                                        .then(
                                            Commands.literal("displayautoupdate")
                                                .then(
                                                    Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(
                                                            p_313527_ -> setDisplayAutoUpdate(
                                                                    p_313527_.getSource(),
                                                                    ObjectiveArgument.getObjective(p_313527_, "objective"),
                                                                    BoolArgumentType.getBool(p_313527_, "value")
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            addNumberFormats(
                                                p_324608_,
                                                Commands.literal("numberformat"),
                                                (p_313531_, p_313532_) -> setObjectiveFormat(
                                                        p_313531_.getSource(), ObjectiveArgument.getObjective(p_313531_, "objective"), p_313532_
                                                    )
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.argument("objective", ObjectiveArgument.objective())
                                        .executes(p_138577_ -> removeObjective(p_138577_.getSource(), ObjectiveArgument.getObjective(p_138577_, "objective")))
                                )
                        )
                        .then(
                            Commands.literal("setdisplay")
                                .then(
                                    Commands.argument("slot", ScoreboardSlotArgument.displaySlot())
                                        .executes(
                                            p_293788_ -> clearDisplaySlot(p_293788_.getSource(), ScoreboardSlotArgument.getDisplaySlot(p_293788_, "slot"))
                                        )
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    p_293785_ -> setDisplaySlot(
                                                            p_293785_.getSource(),
                                                            ScoreboardSlotArgument.getDisplaySlot(p_293785_, "slot"),
                                                            ObjectiveArgument.getObjective(p_293785_, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("players")
                        .then(
                            Commands.literal("list")
                                .executes(p_138571_ -> listTrackedPlayers(p_138571_.getSource()))
                                .then(
                                    Commands.argument("target", ScoreHolderArgument.scoreHolder())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(p_313550_ -> listTrackedPlayerScores(p_313550_.getSource(), ScoreHolderArgument.getName(p_313550_, "target")))
                                )
                        )
                        .then(
                            Commands.literal("set")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer())
                                                        .executes(
                                                            p_138567_ -> setScore(
                                                                    p_138567_.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138567_, "targets"),
                                                                    ObjectiveArgument.getWritableObjective(p_138567_, "objective"),
                                                                    IntegerArgumentType.getInteger(p_138567_, "score")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("get")
                                .then(
                                    Commands.argument("target", ScoreHolderArgument.scoreHolder())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    p_313543_ -> getScore(
                                                            p_313543_.getSource(),
                                                            ScoreHolderArgument.getName(p_313543_, "target"),
                                                            ObjectiveArgument.getObjective(p_313543_, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            p_138563_ -> addScore(
                                                                    p_138563_.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138563_, "targets"),
                                                                    ObjectiveArgument.getWritableObjective(p_138563_, "objective"),
                                                                    IntegerArgumentType.getInteger(p_138563_, "score")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            p_138561_ -> removeScore(
                                                                    p_138561_.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138561_, "targets"),
                                                                    ObjectiveArgument.getWritableObjective(p_138561_, "objective"),
                                                                    IntegerArgumentType.getInteger(p_138561_, "score")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("reset")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(
                                            p_138559_ -> resetScores(
                                                    p_138559_.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(p_138559_, "targets")
                                                )
                                        )
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    p_138550_ -> resetScore(
                                                            p_138550_.getSource(),
                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(p_138550_, "targets"),
                                                            ObjectiveArgument.getObjective(p_138550_, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("enable")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .suggests(
                                                    (p_138473_, p_138474_) -> suggestTriggers(
                                                            p_138473_.getSource(),
                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(p_138473_, "targets"),
                                                            p_138474_
                                                        )
                                                )
                                                .executes(
                                                    p_138537_ -> enableTrigger(
                                                            p_138537_.getSource(),
                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(p_138537_, "targets"),
                                                            ObjectiveArgument.getObjective(p_138537_, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("display")
                                .then(
                                    Commands.literal("name")
                                        .then(
                                            Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                .then(
                                                    Commands.argument("objective", ObjectiveArgument.objective())
                                                        .then(
                                                            Commands.argument("name", ComponentArgument.textComponent(p_324608_))
                                                                .executes(
                                                                    p_313517_ -> setScoreDisplay(
                                                                            p_313517_.getSource(),
                                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(p_313517_, "targets"),
                                                                            ObjectiveArgument.getObjective(p_313517_, "objective"),
                                                                            ComponentArgument.getComponent(p_313517_, "name")
                                                                        )
                                                                )
                                                        )
                                                        .executes(
                                                            p_313555_ -> setScoreDisplay(
                                                                    p_313555_.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_313555_, "targets"),
                                                                    ObjectiveArgument.getObjective(p_313555_, "objective"),
                                                                    null
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("numberformat")
                                        .then(
                                            Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                .then(
                                                    addNumberFormats(
                                                        p_324608_,
                                                        Commands.argument("objective", ObjectiveArgument.objective()),
                                                        (p_313512_, p_313513_) -> setScoreNumberFormat(
                                                                p_313512_.getSource(),
                                                                ScoreHolderArgument.getNamesWithDefaultWildcard(p_313512_, "targets"),
                                                                ObjectiveArgument.getObjective(p_313512_, "objective"),
                                                                p_313513_
                                                            )
                                                    )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("operation")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("targetObjective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("operation", OperationArgument.operation())
                                                        .then(
                                                            Commands.argument("source", ScoreHolderArgument.scoreHolders())
                                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                                .then(
                                                                    Commands.argument("sourceObjective", ObjectiveArgument.objective())
                                                                        .executes(
                                                                            p_138471_ -> performOperation(
                                                                                    p_138471_.getSource(),
                                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138471_, "targets"),
                                                                                    ObjectiveArgument.getWritableObjective(p_138471_, "targetObjective"),
                                                                                    OperationArgument.getOperation(p_138471_, "operation"),
                                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138471_, "source"),
                                                                                    ObjectiveArgument.getObjective(p_138471_, "sourceObjective")
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addNumberFormats(
        CommandBuildContext p_323655_, ArgumentBuilder<CommandSourceStack, ?> p_313757_, ScoreboardCommand.NumberFormatCommandExecutor p_313912_
    ) {
        return p_313757_.then(Commands.literal("blank").executes(p_313547_ -> p_313912_.run(p_313547_, BlankFormat.INSTANCE)))
            .then(Commands.literal("fixed").then(Commands.argument("contents", ComponentArgument.textComponent(p_323655_)).executes(p_313560_ -> {
                Component component = ComponentArgument.getComponent(p_313560_, "contents");
                return p_313912_.run(p_313560_, new FixedFormat(component));
            })))
            .then(Commands.literal("styled").then(Commands.argument("style", StyleArgument.style(p_323655_)).executes(p_313511_ -> {
                Style style = StyleArgument.getStyle(p_313511_, "style");
                return p_313912_.run(p_313511_, new StyledFormat(style));
            })))
            .executes(p_313549_ -> p_313912_.run(p_313549_, null));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRenderTypeModify() {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("rendertype");

        for (ObjectiveCriteria.RenderType objectivecriteria$rendertype : ObjectiveCriteria.RenderType.values()) {
            literalargumentbuilder.then(
                Commands.literal(objectivecriteria$rendertype.getId())
                    .executes(
                        p_138532_ -> setRenderType(p_138532_.getSource(), ObjectiveArgument.getObjective(p_138532_, "objective"), objectivecriteria$rendertype)
                    )
            );
        }

        return literalargumentbuilder;
    }

    private static CompletableFuture<Suggestions> suggestTriggers(CommandSourceStack p_138511_, Collection<ScoreHolder> p_138512_, SuggestionsBuilder p_138513_) {
        List<String> list = Lists.newArrayList();
        Scoreboard scoreboard = p_138511_.getServer().getScoreboard();

        for (Objective objective : scoreboard.getObjectives()) {
            if (objective.getCriteria() == ObjectiveCriteria.TRIGGER) {
                boolean flag = false;

                for (ScoreHolder scoreholder : p_138512_) {
                    ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);
                    if (readonlyscoreinfo == null || readonlyscoreinfo.isLocked()) {
                        flag = true;
                        break;
                    }
                }

                if (flag) {
                    list.add(objective.getName());
                }
            }
        }

        return SharedSuggestionProvider.suggest(list, p_138513_);
    }

    private static int getScore(CommandSourceStack p_138499_, ScoreHolder p_313820_, Objective p_138501_) throws CommandSyntaxException {
        Scoreboard scoreboard = p_138499_.getServer().getScoreboard();
        ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(p_313820_, p_138501_);
        if (readonlyscoreinfo == null) {
            throw ERROR_NO_VALUE.create(p_138501_.getName(), p_313820_.getFeedbackDisplayName());
        } else {
            p_138499_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.get.success",
                        p_313820_.getFeedbackDisplayName(),
                        readonlyscoreinfo.value(),
                        p_138501_.getFormattedDisplayName()
                    ),
                false
            );
            return readonlyscoreinfo.value();
        }
    }

    private static Component getFirstTargetName(Collection<ScoreHolder> p_313737_) {
        return p_313737_.iterator().next().getFeedbackDisplayName();
    }

    private static int performOperation(
        CommandSourceStack p_138524_,
        Collection<ScoreHolder> p_138525_,
        Objective p_138526_,
        OperationArgument.Operation p_138527_,
        Collection<ScoreHolder> p_138528_,
        Objective p_138529_
    ) throws CommandSyntaxException {
        Scoreboard scoreboard = p_138524_.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreholder : p_138525_) {
            ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, p_138526_);

            for (ScoreHolder scoreholder1 : p_138528_) {
                ScoreAccess scoreaccess1 = scoreboard.getOrCreatePlayerScore(scoreholder1, p_138529_);
                p_138527_.apply(scoreaccess, scoreaccess1);
            }

            i += scoreaccess.get();
        }

        if (p_138525_.size() == 1) {
            int j = i;
            p_138524_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.operation.success.single", p_138526_.getFormattedDisplayName(), getFirstTargetName(p_138525_), j
                    ),
                true
            );
        } else {
            p_138524_.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.operation.success.multiple", p_138526_.getFormattedDisplayName(), p_138525_.size()),
                true
            );
        }

        return i;
    }

    private static int enableTrigger(CommandSourceStack p_138515_, Collection<ScoreHolder> p_138516_, Objective p_138517_) throws CommandSyntaxException {
        if (p_138517_.getCriteria() != ObjectiveCriteria.TRIGGER) {
            throw ERROR_NOT_TRIGGER.create();
        } else {
            Scoreboard scoreboard = p_138515_.getServer().getScoreboard();
            int i = 0;

            for (ScoreHolder scoreholder : p_138516_) {
                ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, p_138517_);
                if (scoreaccess.locked()) {
                    scoreaccess.unlock();
                    i++;
                }
            }

            if (i == 0) {
                throw ERROR_TRIGGER_ALREADY_ENABLED.create();
            } else {
                if (p_138516_.size() == 1) {
                    p_138515_.sendSuccess(
                        () -> Component.translatable(
                                "commands.scoreboard.players.enable.success.single", p_138517_.getFormattedDisplayName(), getFirstTargetName(p_138516_)
                            ),
                        true
                    );
                } else {
                    p_138515_.sendSuccess(
                        () -> Component.translatable(
                                "commands.scoreboard.players.enable.success.multiple", p_138517_.getFormattedDisplayName(), p_138516_.size()
                            ),
                        true
                    );
                }

                return i;
            }
        }
    }

    private static int resetScores(CommandSourceStack p_138508_, Collection<ScoreHolder> p_138509_) {
        Scoreboard scoreboard = p_138508_.getServer().getScoreboard();

        for (ScoreHolder scoreholder : p_138509_) {
            scoreboard.resetAllPlayerScores(scoreholder);
        }

        if (p_138509_.size() == 1) {
            p_138508_.sendSuccess(() -> Component.translatable("commands.scoreboard.players.reset.all.single", getFirstTargetName(p_138509_)), true);
        } else {
            p_138508_.sendSuccess(() -> Component.translatable("commands.scoreboard.players.reset.all.multiple", p_138509_.size()), true);
        }

        return p_138509_.size();
    }

    private static int resetScore(CommandSourceStack p_138541_, Collection<ScoreHolder> p_138542_, Objective p_138543_) {
        Scoreboard scoreboard = p_138541_.getServer().getScoreboard();

        for (ScoreHolder scoreholder : p_138542_) {
            scoreboard.resetSinglePlayerScore(scoreholder, p_138543_);
        }

        if (p_138542_.size() == 1) {
            p_138541_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.reset.specific.single", p_138543_.getFormattedDisplayName(), getFirstTargetName(p_138542_)
                    ),
                true
            );
        } else {
            p_138541_.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.reset.specific.multiple", p_138543_.getFormattedDisplayName(), p_138542_.size()),
                true
            );
        }

        return p_138542_.size();
    }

    private static int setScore(CommandSourceStack p_138519_, Collection<ScoreHolder> p_138520_, Objective p_138521_, int p_138522_) {
        Scoreboard scoreboard = p_138519_.getServer().getScoreboard();

        for (ScoreHolder scoreholder : p_138520_) {
            scoreboard.getOrCreatePlayerScore(scoreholder, p_138521_).set(p_138522_);
        }

        if (p_138520_.size() == 1) {
            p_138519_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.set.success.single", p_138521_.getFormattedDisplayName(), getFirstTargetName(p_138520_), p_138522_
                    ),
                true
            );
        } else {
            p_138519_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.set.success.multiple", p_138521_.getFormattedDisplayName(), p_138520_.size(), p_138522_
                    ),
                true
            );
        }

        return p_138522_ * p_138520_.size();
    }

    private static int setScoreDisplay(CommandSourceStack p_313937_, Collection<ScoreHolder> p_313923_, Objective p_313702_, @Nullable Component p_313807_) {
        Scoreboard scoreboard = p_313937_.getServer().getScoreboard();

        for (ScoreHolder scoreholder : p_313923_) {
            scoreboard.getOrCreatePlayerScore(scoreholder, p_313702_).display(p_313807_);
        }

        if (p_313807_ == null) {
            if (p_313923_.size() == 1) {
                p_313937_.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.name.clear.success.single", getFirstTargetName(p_313923_), p_313702_.getFormattedDisplayName()
                        ),
                    true
                );
            } else {
                p_313937_.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.name.clear.success.multiple", p_313923_.size(), p_313702_.getFormattedDisplayName()
                        ),
                    true
                );
            }
        } else if (p_313923_.size() == 1) {
            p_313937_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.name.set.success.single",
                        p_313807_,
                        getFirstTargetName(p_313923_),
                        p_313702_.getFormattedDisplayName()
                    ),
                true
            );
        } else {
            p_313937_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.name.set.success.multiple", p_313807_, p_313923_.size(), p_313702_.getFormattedDisplayName()
                    ),
                true
            );
        }

        return p_313923_.size();
    }

    private static int setScoreNumberFormat(
        CommandSourceStack p_313794_, Collection<ScoreHolder> p_313780_, Objective p_313752_, @Nullable NumberFormat p_313869_
    ) {
        Scoreboard scoreboard = p_313794_.getServer().getScoreboard();

        for (ScoreHolder scoreholder : p_313780_) {
            scoreboard.getOrCreatePlayerScore(scoreholder, p_313752_).numberFormatOverride(p_313869_);
        }

        if (p_313869_ == null) {
            if (p_313780_.size() == 1) {
                p_313794_.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.numberFormat.clear.success.single",
                            getFirstTargetName(p_313780_),
                            p_313752_.getFormattedDisplayName()
                        ),
                    true
                );
            } else {
                p_313794_.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.numberFormat.clear.success.multiple", p_313780_.size(), p_313752_.getFormattedDisplayName()
                        ),
                    true
                );
            }
        } else if (p_313780_.size() == 1) {
            p_313794_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.numberFormat.set.success.single",
                        getFirstTargetName(p_313780_),
                        p_313752_.getFormattedDisplayName()
                    ),
                true
            );
        } else {
            p_313794_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.numberFormat.set.success.multiple", p_313780_.size(), p_313752_.getFormattedDisplayName()
                    ),
                true
            );
        }

        return p_313780_.size();
    }

    private static int addScore(CommandSourceStack p_138545_, Collection<ScoreHolder> p_138546_, Objective p_138547_, int p_138548_) {
        Scoreboard scoreboard = p_138545_.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreholder : p_138546_) {
            ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, p_138547_);
            scoreaccess.set(scoreaccess.get() + p_138548_);
            i += scoreaccess.get();
        }

        if (p_138546_.size() == 1) {
            int j = i;
            p_138545_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.add.success.single", p_138548_, p_138547_.getFormattedDisplayName(), getFirstTargetName(p_138546_), j
                    ),
                true
            );
        } else {
            p_138545_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.add.success.multiple", p_138548_, p_138547_.getFormattedDisplayName(), p_138546_.size()
                    ),
                true
            );
        }

        return i;
    }

    private static int removeScore(CommandSourceStack p_138554_, Collection<ScoreHolder> p_138555_, Objective p_138556_, int p_138557_) {
        Scoreboard scoreboard = p_138554_.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreholder : p_138555_) {
            ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, p_138556_);
            scoreaccess.set(scoreaccess.get() - p_138557_);
            i += scoreaccess.get();
        }

        if (p_138555_.size() == 1) {
            int j = i;
            p_138554_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.remove.success.single", p_138557_, p_138556_.getFormattedDisplayName(), getFirstTargetName(p_138555_), j
                    ),
                true
            );
        } else {
            p_138554_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.remove.success.multiple", p_138557_, p_138556_.getFormattedDisplayName(), p_138555_.size()
                    ),
                true
            );
        }

        return i;
    }

    private static int listTrackedPlayers(CommandSourceStack p_138476_) {
        Collection<ScoreHolder> collection = p_138476_.getServer().getScoreboard().getTrackedPlayers();
        if (collection.isEmpty()) {
            p_138476_.sendSuccess(() -> Component.translatable("commands.scoreboard.players.list.empty"), false);
        } else {
            p_138476_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.list.success",
                        collection.size(),
                        ComponentUtils.formatList(collection, ScoreHolder::getFeedbackDisplayName)
                    ),
                false
            );
        }

        return collection.size();
    }

    private static int listTrackedPlayerScores(CommandSourceStack p_138496_, ScoreHolder p_313835_) {
        Object2IntMap<Objective> object2intmap = p_138496_.getServer().getScoreboard().listPlayerScores(p_313835_);
        if (object2intmap.isEmpty()) {
            p_138496_.sendSuccess(() -> Component.translatable("commands.scoreboard.players.list.entity.empty", p_313835_.getFeedbackDisplayName()), false);
        } else {
            p_138496_.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.list.entity.success", p_313835_.getFeedbackDisplayName(), object2intmap.size()),
                false
            );
            Object2IntMaps.fastForEach(
                object2intmap,
                p_313504_ -> p_138496_.sendSuccess(
                        () -> Component.translatable(
                                "commands.scoreboard.players.list.entity.entry",
                                ((Objective)p_313504_.getKey()).getFormattedDisplayName(),
                                p_313504_.getIntValue()
                            ),
                        false
                    )
            );
        }

        return object2intmap.size();
    }

    private static int clearDisplaySlot(CommandSourceStack p_138478_, DisplaySlot p_294251_) throws CommandSyntaxException {
        Scoreboard scoreboard = p_138478_.getServer().getScoreboard();
        if (scoreboard.getDisplayObjective(p_294251_) == null) {
            throw ERROR_DISPLAY_SLOT_ALREADY_EMPTY.create();
        } else {
            scoreboard.setDisplayObjective(p_294251_, null);
            p_138478_.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.display.cleared", p_294251_.getSerializedName()), true);
            return 0;
        }
    }

    private static int setDisplaySlot(CommandSourceStack p_138481_, DisplaySlot p_294651_, Objective p_138483_) throws CommandSyntaxException {
        Scoreboard scoreboard = p_138481_.getServer().getScoreboard();
        if (scoreboard.getDisplayObjective(p_294651_) == p_138483_) {
            throw ERROR_DISPLAY_SLOT_ALREADY_SET.create();
        } else {
            scoreboard.setDisplayObjective(p_294651_, p_138483_);
            p_138481_.sendSuccess(
                () -> Component.translatable("commands.scoreboard.objectives.display.set", p_294651_.getSerializedName(), p_138483_.getDisplayName()), true
            );
            return 0;
        }
    }

    private static int setDisplayName(CommandSourceStack p_138492_, Objective p_138493_, Component p_138494_) {
        if (!p_138493_.getDisplayName().equals(p_138494_)) {
            p_138493_.setDisplayName(p_138494_);
            p_138492_.sendSuccess(
                () -> Component.translatable("commands.scoreboard.objectives.modify.displayname", p_138493_.getName(), p_138493_.getFormattedDisplayName()),
                true
            );
        }

        return 0;
    }

    private static int setDisplayAutoUpdate(CommandSourceStack p_313915_, Objective p_313747_, boolean p_313790_) {
        if (p_313747_.displayAutoUpdate() != p_313790_) {
            p_313747_.setDisplayAutoUpdate(p_313790_);
            if (p_313790_) {
                p_313915_.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.objectives.modify.displayAutoUpdate.enable", p_313747_.getName(), p_313747_.getFormattedDisplayName()
                        ),
                    true
                );
            } else {
                p_313915_.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.objectives.modify.displayAutoUpdate.disable", p_313747_.getName(), p_313747_.getFormattedDisplayName()
                        ),
                    true
                );
            }
        }

        return 0;
    }

    private static int setObjectiveFormat(CommandSourceStack p_313788_, Objective p_313769_, @Nullable NumberFormat p_313731_) {
        p_313769_.setNumberFormat(p_313731_);
        if (p_313731_ != null) {
            p_313788_.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.set", p_313769_.getName()), true);
        } else {
            p_313788_.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.clear", p_313769_.getName()), true);
        }

        return 0;
    }

    private static int setRenderType(CommandSourceStack p_138488_, Objective p_138489_, ObjectiveCriteria.RenderType p_138490_) {
        if (p_138489_.getRenderType() != p_138490_) {
            p_138489_.setRenderType(p_138490_);
            p_138488_.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.rendertype", p_138489_.getFormattedDisplayName()), true);
        }

        return 0;
    }

    private static int removeObjective(CommandSourceStack p_138485_, Objective p_138486_) {
        Scoreboard scoreboard = p_138485_.getServer().getScoreboard();
        scoreboard.removeObjective(p_138486_);
        p_138485_.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.remove.success", p_138486_.getFormattedDisplayName()), true);
        return scoreboard.getObjectives().size();
    }

    private static int addObjective(CommandSourceStack p_138503_, String p_138504_, ObjectiveCriteria p_138505_, Component p_138506_) throws CommandSyntaxException {
        Scoreboard scoreboard = p_138503_.getServer().getScoreboard();
        if (scoreboard.getObjective(p_138504_) != null) {
            throw ERROR_OBJECTIVE_ALREADY_EXISTS.create();
        } else {
            scoreboard.addObjective(p_138504_, p_138505_, p_138506_, p_138505_.getDefaultRenderType(), false, null);
            Objective objective = scoreboard.getObjective(p_138504_);
            p_138503_.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.add.success", objective.getFormattedDisplayName()), true);
            return scoreboard.getObjectives().size();
        }
    }

    private static int listObjectives(CommandSourceStack p_138539_) {
        Collection<Objective> collection = p_138539_.getServer().getScoreboard().getObjectives();
        if (collection.isEmpty()) {
            p_138539_.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.list.empty"), false);
        } else {
            p_138539_.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.objectives.list.success",
                        collection.size(),
                        ComponentUtils.formatList(collection, Objective::getFormattedDisplayName)
                    ),
                false
            );
        }

        return collection.size();
    }

    @FunctionalInterface
    public interface NumberFormatCommandExecutor {
        int run(CommandContext<CommandSourceStack> p_313745_, @Nullable NumberFormat p_313763_) throws CommandSyntaxException;
    }
}
