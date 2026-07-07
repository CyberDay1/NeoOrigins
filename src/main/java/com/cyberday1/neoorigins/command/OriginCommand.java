package com.cyberday1.neoorigins.command;

import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.MobOriginDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.evolution.EssenceEvolutionManager;
import com.cyberday1.neoorigins.service.MountConsentManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.MobOriginService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

import java.util.TreeMap;
import java.util.function.Predicate;

public class OriginCommand {

    private static final String[] TIER_NAMES = {"base", "evolved", "ascended", "apex"};

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TIERS =
        (ctx, builder) -> SharedSuggestionProvider.suggest(TIER_NAMES, builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_LAYERS =
        (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            LayerDataManager.INSTANCE.getLayers().keySet(), builder);

    /** Suggests layer ids for the last token of a comma/space-separated list. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_LAYERS_CSV =
        (ctx, builder) -> {
            String input = builder.getRemaining();
            int start = input.lastIndexOf(',') + 1;
            while (start < input.length() && input.charAt(start) == ' ') start++;
            var offset = builder.createOffset(builder.getStart() + start);
            return SharedSuggestionProvider.suggestResource(
                LayerDataManager.INSTANCE.getLayers().keySet(), offset);
        };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ORIGINS =
        (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            OriginDataManager.INSTANCE.getOrigins().keySet(), builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_POWERS =
        (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            PowerDataManager.INSTANCE.getAllPowers().keySet(), builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MOB_ORIGINS =
        (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            MobOriginDataManager.INSTANCE.getMobOrigins().keySet(), builder);

    /** Admin gate (gamemaster / permission level 2 equivalent). */
    private static final Predicate<CommandSourceStack> REQUIRE_GM =
        Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Primary command tree.
        dispatcher.register(buildCommandTree("neoorigins"));
        // `/origin` alias — registered as a REAL command literal (identical tree),
        // not just rewritten on chat parse-fail by LegacyCommandRewriter. That
        // rewriter never fires during datapack-load function compilation, so an
        // `origin ...` line inside a vanilla .mcfunction (common in imported Fabric
        // Origins packs) would fail to compile and take the whole function down.
        // Registering the literal makes those functions compile. Safe on NeoForge:
        // the Fabric Origins mod (the original `/origin` owner) cannot coexist here,
        // and NeoOrigins IS the Origins implementation for this platform.
        dispatcher.register(buildCommandTree("origin"));
        OriginsCompatCommands.register(dispatcher);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildCommandTree(String name) {
        // Permission model mirrors the 1.21.1 port: no root-level gate,
        // each admin subcommand gated individually, player-facing commands
        // (evolve accept/decline, bare gui, editor) open. `get` is gated by
        // REQUIRE_GM OR the public_origin_get config toggle.
        return Commands.literal(name)
                .then(Commands.literal("evolve")
                    .then(Commands.literal("accept")
                        .executes(OriginCommand::executeEvolveAccept))
                    .then(Commands.literal("decline")
                        .executes(OriginCommand::executeEvolveDecline))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(REQUIRE_GM)
                        .then(Commands.argument("tier", StringArgumentType.word())
                            .suggests(SUGGEST_TIERS)
                            .executes(OriginCommand::executeEvolveSet)))
                    .then(Commands.literal("query")
                        .requires(REQUIRE_GM)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(OriginCommand::executeEvolveQuery))))
                // ── Mount consent commands (permission 0) ─────────────────
                .then(Commands.literal("mount")
                    // /neoorigins mount accept — accept a pending mount request
                    .then(Commands.literal("accept")
                        .executes(OriginCommand::executeMountAccept))
                    // /neoorigins mount decline — decline a pending mount request
                    .then(Commands.literal("decline")
                        .executes(OriginCommand::executeMountDecline)))
                .then(Commands.literal("get")
                    // OPs always; other players only when public_origin_get is on.
                    .requires(REQUIRE_GM.or(src -> AdminConfig.isPublicOriginGetAllowed()))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeGet(ctx, null))
                        .then(Commands.argument("layer", IdentifierArgument.id())
                            .suggests(SUGGEST_LAYERS)
                            .executes(ctx -> executeGet(ctx, IdentifierArgument.getId(ctx, "layer"))))))
                .then(Commands.literal("set")
                    .requires(REQUIRE_GM)
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("layer", IdentifierArgument.id())
                            .suggests(SUGGEST_LAYERS)
                            .then(Commands.argument("origin", IdentifierArgument.id())
                                .suggests(SUGGEST_ORIGINS)
                                .executes(ctx -> executeSet(ctx))))))
                .then(Commands.literal("reset")
                    .requires(REQUIRE_GM)
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeReset(ctx, null))
                        .then(Commands.argument("layer", IdentifierArgument.id())
                            .suggests(SUGGEST_LAYERS)
                            .executes(ctx -> executeReset(ctx, IdentifierArgument.getId(ctx, "layer"))))))
                .then(Commands.literal("list")
                    .requires(REQUIRE_GM)
                    .executes(ctx -> executeList(ctx, null))
                    .then(Commands.argument("layer", IdentifierArgument.id())
                        .suggests(SUGGEST_LAYERS)
                        .executes(ctx -> executeList(ctx, IdentifierArgument.getId(ctx, "layer")))))
                .then(Commands.literal("has")
                    .requires(REQUIRE_GM)
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("power", IdentifierArgument.id())
                            .suggests(SUGGEST_POWERS)
                            .executes(ctx -> executeHas(ctx)))))
                .then(Commands.literal("gui")
                    .executes(ctx -> executeGui(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(REQUIRE_GM)
                        .executes(ctx -> executeGui(ctx, EntityArgument.getPlayer(ctx, "player")))
                        .then(Commands.argument("layers", StringArgumentType.greedyString())
                            .suggests(SUGGEST_LAYERS_CSV)
                            .executes(ctx -> {
                                java.util.List<Identifier> scoped =
                                    parseLayers(StringArgumentType.getString(ctx, "layers"));
                                if (scoped.isEmpty()) {
                                    ctx.getSource().sendFailure(Component.literal("No valid layer ids given."));
                                    return 0;
                                }
                                return executeGui(ctx, EntityArgument.getPlayer(ctx, "player"), scoped);
                            }))))
                .then(Commands.literal("editor")
                    .requires(REQUIRE_GM)
                    .executes(ctx -> executeEditor(ctx)))
                // ── Mob-origin commands ───────────────────────────────────────
                .then(Commands.literal("mob")
                    .requires(REQUIRE_GM)
                    .then(Commands.literal("editor")
                        .requires(REQUIRE_GM)
                        .executes(ctx -> executeMobEditor(ctx)))
                    .then(Commands.literal("apply")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("origin", IdentifierArgument.id())
                                .suggests(SUGGEST_MOB_ORIGINS)
                                .executes(OriginCommand::executeMobApply))))
                    .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(OriginCommand::executeMobClear)))
                    .then(Commands.literal("get")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(OriginCommand::executeMobGet)))
                    .then(Commands.literal("egg")
                        .then(Commands.argument("origin", IdentifierArgument.id())
                            .suggests(SUGGEST_MOB_ORIGINS)
                            .executes(ctx -> executeMobEgg(ctx, null, 1))
                            .then(Commands.argument("entity_type", IdentifierArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet(), b))
                                .executes(ctx -> executeMobEgg(ctx,
                                    IdentifierArgument.getId(ctx, "entity_type"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                    .executes(ctx -> executeMobEgg(ctx,
                                        IdentifierArgument.getId(ctx, "entity_type"),
                                        IntegerArgumentType.getInteger(ctx, "count"))))))))
                .then(Commands.literal("reload")
                    .requires(REQUIRE_GM)
                    .executes(ctx -> executeReload(ctx)))
                // ── Developer harness (permission 2) ──────────────────────────
                .then(DebugCommand.build());
    }

    // ── Evolution commands ──────────────────────────────────────────────

    private static int executeEvolveAccept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EssenceEvolutionManager.acceptEvolution(player);
        return 1;
    }

    private static int executeEvolveDecline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EssenceEvolutionManager.declineEvolution(player);
        return 1;
    }

    // ── Mount consent commands ──────────────────────────────────────────

    private static int executeMountAccept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MountConsentManager.acceptRequest(player);
        return 1;
    }

    private static int executeMountDecline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MountConsentManager.declineRequest(player);
        return 1;
    }

    private static int executeEvolveSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String tierArg = StringArgumentType.getString(ctx, "tier");

        int tier = parseTier(tierArg);
        if (tier < 0) {
            ctx.getSource().sendFailure(Component.literal(
                "Invalid tier: " + tierArg + ". Use base/evolved/ascended/apex or 0-3."));
            return 0;
        }

        PlayerOriginData data = player.getData(OriginAttachments.originData());
        int oldTier = data.getEvolutionTier();
        data.setEvolutionTier(tier);
        // Grant/revoke tier-overlay powers and reconcile health, same as the
        // accept-prompt path. Without this the force-set only changed the tier
        // field and the attribute modifiers (e.g. evolution HP) wouldn't apply
        // until a relog rebuilt the power cache (and a reset never revoked them).
        if (oldTier != tier) {
            EssenceEvolutionManager.applyTierPowerChange(player, oldTier, tier);
        }
        NeoOriginsNetwork.syncEvolutionToPlayer(player);
        NeoOriginsNetwork.syncToPlayer(player);

        String tierName = tier > 0 ? EssenceEvolutionManager.TIER_NAMES[tier] : "Base";
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Set " + player.getName().getString() + "'s evolution tier to " + tierName + " (" + tier + ")"), true);

        if (tier > 0) {
            player.sendSystemMessage(Component.literal("You have been elevated to ")
                .append(Component.literal(tierName)
                    .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD))
                .append(Component.literal(" tier!")));
        } else {
            player.sendSystemMessage(Component.literal("Your evolution has been reset to base tier.")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }

        return 1;
    }

    private static int executeEvolveQuery(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        PlayerOriginData data = player.getData(OriginAttachments.originData());

        int kills = data.getEssenceKills();
        int tier = data.getEvolutionTier();
        String tierName = tier > 0 ? EssenceEvolutionManager.TIER_NAMES[tier] : "Base";

        ctx.getSource().sendSuccess(() -> Component.literal(
            player.getName().getString() + " - Tier: " + tierName + " (" + tier + "), Kills: " + kills), false);
        return 1;
    }

    private static int parseTier(String input) {
        return switch (input.toLowerCase()) {
            case "base", "0" -> 0;
            case "evolved", "1" -> 1;
            case "ascended", "2" -> 2;
            case "apex", "3" -> 3;
            default -> -1;
        };
    }

    // ── Origin management commands ──────────────────────────────────────

    private static int executeGet(CommandContext<CommandSourceStack> ctx, Identifier layerId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        PlayerOriginData data = player.getData(OriginAttachments.originData());

        if (layerId != null) {
            Identifier originId = data.getOrigin(layerId);
            if (originId == null) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    player.getName().getString() + " has no origin in layer " + layerId), false);
            } else {
                var origin = OriginDataManager.INSTANCE.getOrigin(originId);
                String name = origin != null ? origin.name().getString() : originId.toString();
                ctx.getSource().sendSuccess(() -> Component.literal(
                    player.getName().getString() + "'s origin in " + layerId + ": " + name + " (" + originId + ")"), false);
            }
        } else {
            var origins = data.getOrigins();
            if (origins.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    player.getName().getString() + " has no origins selected."), false);
            } else {
                StringBuilder sb = new StringBuilder(player.getName().getString() + "'s origins:\n");
                new TreeMap<>(origins).forEach((layer, origin) -> {
                    var originData = OriginDataManager.INSTANCE.getOrigin(origin);
                    String name = originData != null ? originData.name().getString() : origin.toString();
                    sb.append("  ").append(layer).append(": ").append(name).append("\n");
                });
                ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
            }
        }
        return 1;
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Identifier layerId = IdentifierArgument.getId(ctx, "layer");
        Identifier originId = IdentifierArgument.getId(ctx, "origin");

        if (!LayerDataManager.INSTANCE.hasLayer(layerId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown layer: " + layerId));
            return 0;
        }
        if (!OriginDataManager.INSTANCE.hasOrigin(originId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown origin: " + originId));
            return 0;
        }

        PlayerOriginData data = player.getData(OriginAttachments.originData());
        Identifier oldOrigin = data.getOrigin(layerId);
        data.setOrigin(layerId, originId);
        ActiveOriginService.applyOriginPowers(player, layerId, oldOrigin, originId);
        NeoOriginsNetwork.syncToPlayer(player);

        var origin = OriginDataManager.INSTANCE.getOrigin(originId);
        String name = origin != null ? origin.name().getString() : originId.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Set " + player.getName().getString() + "'s origin in " + layerId + " to " + name), true);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx, Identifier layerId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        PlayerOriginData data = player.getData(OriginAttachments.originData());

        ActiveOriginService.revokeAllPowers(player);
        if (layerId != null) {
            data.removeOrigin(layerId);
        } else {
            data.clear();
        }
        // revokeAllPowers cleared the global-power ledger; re-grant any matching
        // global power sets so a reset doesn't strip apoli:global powers.
        com.cyberday1.neoorigins.service.GlobalPowerService.reconcilePlayer(player);

        NeoOriginsNetwork.syncRegistryToPlayer(player);
        NeoOriginsNetwork.syncToPlayer(player);
        NeoOriginsNetwork.openSelectionScreen(player, false);

        String scope = layerId != null ? "layer " + layerId : "all layers";
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Reset " + player.getName().getString() + "'s origin for " + scope), true);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx, Identifier layerId) {
        if (layerId != null) {
            var layer = LayerDataManager.INSTANCE.getLayer(layerId);
            if (layer == null) {
                ctx.getSource().sendFailure(Component.literal("Unknown layer: " + layerId));
                return 0;
            }
            StringBuilder sb = new StringBuilder("Origins in layer " + layerId + ":\n");
            for (var condOrigin : layer.origins()) {
                var origin = OriginDataManager.INSTANCE.getOrigin(condOrigin.origin());
                String name = origin != null ? origin.name().getString() : condOrigin.origin().toString();
                sb.append("  ").append(condOrigin.origin()).append(" - ").append(name).append("\n");
            }
            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        } else {
            StringBuilder sb = new StringBuilder("All registered origins:\n");
            new TreeMap<>(OriginDataManager.INSTANCE.getOrigins()).forEach((id, origin) -> {
                sb.append("  ").append(id).append(" - ").append(origin.name().getString()).append("\n");
            });
            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        }
        return 1;
    }

    private static int executeHas(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Identifier powerId = IdentifierArgument.getId(ctx, "power");

        PlayerOriginData data = player.getData(OriginAttachments.originData());
        boolean hasPower = false;
        for (var entry : data.getOrigins().entrySet()) {
            var origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin != null && origin.powers().contains(powerId)) {
                hasPower = true;
                break;
            }
        }
        final boolean result = hasPower;
        ctx.getSource().sendSuccess(() -> Component.literal(
            player.getName().getString() + (result ? " has" : " does not have") + " power: " + powerId), false);
        return result ? 1 : 0;
    }

    private static int executeGui(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        return executeGui(ctx, player, java.util.List.of());
    }

    /** Parse a comma/space-separated list of layer ids; blank or malformed tokens are skipped. */
    private static java.util.List<Identifier> parseLayers(String raw) {
        java.util.List<Identifier> out = new java.util.ArrayList<>();
        for (String tok : raw.split("[,\\s]+")) {
            if (tok.isBlank()) continue;
            Identifier id = Identifier.tryParse(tok.trim());
            if (id != null) out.add(id);
        }
        return out;
    }

    private static int executeGui(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                  java.util.List<Identifier> scopedLayers) throws CommandSyntaxException {
        ServerPlayer target = player != null ? player : ctx.getSource().getPlayerOrException();
        NeoOriginsNetwork.syncRegistryToPlayer(target);
        if (player != null) {
            // OP opened the picker for another player — authorize that
            // (non-OP) player to re-select for this picker session. The
            // self path (permission 0) intentionally gets no grant, so a
            // normal player cannot reset their own origin for free.
            target.getData(OriginAttachments.originData()).setPendingAdminReselect(true);
        }
        NeoOriginsNetwork.openSelectionScreen(target, false, true,
            scopedLayers == null ? java.util.List.of() : scopedLayers);
        if (player != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Opened origin selection for " + target.getName().getString()), true);
        }
        return 1;
    }

    private static int executeEditor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        NeoOriginsNetwork.openCreatorFor(target);
        return 1;
    }

    private static int executeMobEditor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        NeoOriginsNetwork.openMobCreatorFor(target);
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Use /reload to reload datapacks (origins reload automatically)."), false);
        return 1;
    }

    // ── Mob-origin testing handlers ─────────────────────────────────────────
    // Players are skipped (they have their own origin system). Operates on
    // non-player LivingEntity targets only.

    private static int executeMobApply(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier originId = IdentifierArgument.getId(ctx, "origin");
        if (!MobOriginDataManager.INSTANCE.hasMobOrigin(originId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown mob origin: " + originId));
            return 0;
        }
        int count = 0;
        for (var entity : EntityArgument.getEntities(ctx, "targets")) {
            if (!(entity instanceof LivingEntity mob) || entity instanceof ServerPlayer) continue;
            var data = mob.getData(EntityAttachments.mobOriginData());
            Identifier old = data.getOriginId().orElse(null);
            data.setOriginId(originId);
            MobOriginService.applyMobOriginPowers(mob, old, originId);
            count++;
        }
        final int n = count;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Applied mob origin " + originId + " to " + n + " entit" + (n == 1 ? "y" : "ies")), true);
        return count;
    }

    private static int executeMobClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = 0;
        for (var entity : EntityArgument.getEntities(ctx, "targets")) {
            if (!(entity instanceof LivingEntity mob) || entity instanceof ServerPlayer) continue;
            var data = mob.getData(EntityAttachments.mobOriginData());
            if (!data.hasOrigin()) continue;
            MobOriginService.applyMobOriginPowers(mob, data.getOriginId().orElse(null), null);
            data.clear();
            count++;
        }
        final int n = count;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Cleared mob origin from " + n + " entit" + (n == 1 ? "y" : "ies")), true);
        return count;
    }

    private static int executeMobEgg(CommandContext<CommandSourceStack> ctx,
                                     Identifier entityOverride, int count) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        Identifier originId = IdentifierArgument.getId(ctx, "origin");
        var result = com.cyberday1.neoorigins.service.MobOriginSpawnEggService
            .buildEgg(originId, entityOverride, count);
        if (!result.ok()) {
            ctx.getSource().sendFailure(Component.literal(result.error()));
            return 0;
        }
        if (!sp.getInventory().add(result.stack())) {
            sp.drop(result.stack(), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Gave " + count + " × " + originId + " spawn egg"), false);
        return 1;
    }

    private static int executeMobGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (var entity : EntityArgument.getEntities(ctx, "targets")) {
            if (!(entity instanceof LivingEntity mob) || entity instanceof ServerPlayer) continue;
            var data = mob.getData(EntityAttachments.mobOriginData());
            sb.append("\n  ")
              .append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))
              .append(" → ").append(data.getOriginId().map(Identifier::toString).orElse("(none)"));
            count++;
        }
        final String report = count == 0 ? "No non-player entities matched." : sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(report), false);
        return count;
    }
}
