package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeCommand {
    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.recipe.give.failed"));
    private static final SimpleCommandExceptionType ERROR_TAKE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.recipe.take.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> p_138201_) {
        p_138201_.register(
            Commands.literal("recipe")
                .requires(p_138205_ -> p_138205_.hasPermission(2))
                .then(
                    Commands.literal("give")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.argument("recipe", ResourceLocationArgument.id())
                                        .suggests(SuggestionProviders.ALL_RECIPES)
                                        .executes(
                                            p_300765_ -> giveRecipes(
                                                    p_300765_.getSource(),
                                                    EntityArgument.getPlayers(p_300765_, "targets"),
                                                    Collections.singleton(ResourceLocationArgument.getRecipe(p_300765_, "recipe"))
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("*")
                                        .executes(
                                            p_138217_ -> giveRecipes(
                                                    p_138217_.getSource(),
                                                    EntityArgument.getPlayers(p_138217_, "targets"),
                                                    p_138217_.getSource().getServer().getRecipeManager().getRecipes()
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("take")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.argument("recipe", ResourceLocationArgument.id())
                                        .suggests(SuggestionProviders.ALL_RECIPES)
                                        .executes(
                                            p_300766_ -> takeRecipes(
                                                    p_300766_.getSource(),
                                                    EntityArgument.getPlayers(p_300766_, "targets"),
                                                    Collections.singleton(ResourceLocationArgument.getRecipe(p_300766_, "recipe"))
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("*")
                                        .executes(
                                            p_138203_ -> takeRecipes(
                                                    p_138203_.getSource(),
                                                    EntityArgument.getPlayers(p_138203_, "targets"),
                                                    p_138203_.getSource().getServer().getRecipeManager().getRecipes()
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int giveRecipes(CommandSourceStack p_138207_, Collection<ServerPlayer> p_138208_, Collection<RecipeHolder<?>> p_138209_) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverplayer : p_138208_) {
            i += serverplayer.awardRecipes(p_138209_);
        }

        if (i == 0) {
            throw ERROR_GIVE_FAILED.create();
        } else {
            if (p_138208_.size() == 1) {
                p_138207_.sendSuccess(
                    () -> Component.translatable("commands.recipe.give.success.single", p_138209_.size(), p_138208_.iterator().next().getDisplayName()), true
                );
            } else {
                p_138207_.sendSuccess(() -> Component.translatable("commands.recipe.give.success.multiple", p_138209_.size(), p_138208_.size()), true);
            }

            return i;
        }
    }

    private static int takeRecipes(CommandSourceStack p_138213_, Collection<ServerPlayer> p_138214_, Collection<RecipeHolder<?>> p_138215_) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverplayer : p_138214_) {
            i += serverplayer.resetRecipes(p_138215_);
        }

        if (i == 0) {
            throw ERROR_TAKE_FAILED.create();
        } else {
            if (p_138214_.size() == 1) {
                p_138213_.sendSuccess(
                    () -> Component.translatable("commands.recipe.take.success.single", p_138215_.size(), p_138214_.iterator().next().getDisplayName()), true
                );
            } else {
                p_138213_.sendSuccess(() -> Component.translatable("commands.recipe.take.success.multiple", p_138215_.size(), p_138214_.size()), true);
            }

            return i;
        }
    }
}
