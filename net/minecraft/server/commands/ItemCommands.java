package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class ItemCommands {
    static final Dynamic3CommandExceptionType ERROR_TARGET_NOT_A_CONTAINER = new Dynamic3CommandExceptionType(
        (p_304251_, p_304252_, p_304253_) -> Component.translatableEscape("commands.item.target.not_a_container", p_304251_, p_304252_, p_304253_)
    );
    static final Dynamic3CommandExceptionType ERROR_SOURCE_NOT_A_CONTAINER = new Dynamic3CommandExceptionType(
        (p_304247_, p_304248_, p_304249_) -> Component.translatableEscape("commands.item.source.not_a_container", p_304247_, p_304248_, p_304249_)
    );
    static final DynamicCommandExceptionType ERROR_TARGET_INAPPLICABLE_SLOT = new DynamicCommandExceptionType(
        p_304250_ -> Component.translatableEscape("commands.item.target.no_such_slot", p_304250_)
    );
    private static final DynamicCommandExceptionType ERROR_SOURCE_INAPPLICABLE_SLOT = new DynamicCommandExceptionType(
        p_304246_ -> Component.translatableEscape("commands.item.source.no_such_slot", p_304246_)
    );
    private static final DynamicCommandExceptionType ERROR_TARGET_NO_CHANGES = new DynamicCommandExceptionType(
        p_304245_ -> Component.translatableEscape("commands.item.target.no_changes", p_304245_)
    );
    private static final Dynamic2CommandExceptionType ERROR_TARGET_NO_CHANGES_KNOWN_ITEM = new Dynamic2CommandExceptionType(
        (p_304254_, p_304255_) -> Component.translatableEscape("commands.item.target.no_changed.known_item", p_304254_, p_304255_)
    );
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MODIFIER = (p_335220_, p_335221_) -> {
        ReloadableServerRegistries.Holder reloadableserverregistries$holder = p_335220_.getSource().getServer().reloadableRegistries();
        return SharedSuggestionProvider.suggestResource(reloadableserverregistries$holder.getKeys(Registries.ITEM_MODIFIER), p_335221_);
    };

    public static void register(CommandDispatcher<CommandSourceStack> p_214449_, CommandBuildContext p_214450_) {
        p_214449_.register(
            Commands.literal("item")
                .requires(p_180256_ -> p_180256_.hasPermission(2))
                .then(
                    Commands.literal("replace")
                        .then(
                            Commands.literal("block")
                                .then(
                                    Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(
                                            Commands.argument("slot", SlotArgument.slot())
                                                .then(
                                                    Commands.literal("with")
                                                        .then(
                                                            Commands.argument("item", ItemArgument.item(p_214450_))
                                                                .executes(
                                                                    p_180383_ -> setBlockItem(
                                                                            p_180383_.getSource(),
                                                                            BlockPosArgument.getLoadedBlockPos(p_180383_, "pos"),
                                                                            SlotArgument.getSlot(p_180383_, "slot"),
                                                                            ItemArgument.getItem(p_180383_, "item").createItemStack(1, false)
                                                                        )
                                                                )
                                                                .then(
                                                                    Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                                                        .executes(
                                                                            p_180381_ -> setBlockItem(
                                                                                    p_180381_.getSource(),
                                                                                    BlockPosArgument.getLoadedBlockPos(p_180381_, "pos"),
                                                                                    SlotArgument.getSlot(p_180381_, "slot"),
                                                                                    ItemArgument.getItem(p_180381_, "item")
                                                                                        .createItemStack(
                                                                                            IntegerArgumentType.getInteger(p_180381_, "count"), true
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                                .then(
                                                    Commands.literal("from")
                                                        .then(
                                                            Commands.literal("block")
                                                                .then(
                                                                    Commands.argument("source", BlockPosArgument.blockPos())
                                                                        .then(
                                                                            Commands.argument("sourceSlot", SlotArgument.slot())
                                                                                .executes(
                                                                                    p_180379_ -> blockToBlock(
                                                                                            p_180379_.getSource(),
                                                                                            BlockPosArgument.getLoadedBlockPos(p_180379_, "source"),
                                                                                            SlotArgument.getSlot(p_180379_, "sourceSlot"),
                                                                                            BlockPosArgument.getLoadedBlockPos(p_180379_, "pos"),
                                                                                            SlotArgument.getSlot(p_180379_, "slot")
                                                                                        )
                                                                                )
                                                                                .then(
                                                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(p_214450_))
                                                                                        .suggests(SUGGEST_MODIFIER)
                                                                                        .executes(
                                                                                            p_335226_ -> blockToBlock(
                                                                                                    (CommandSourceStack)p_335226_.getSource(),
                                                                                                    BlockPosArgument.getLoadedBlockPos(p_335226_, "source"),
                                                                                                    SlotArgument.getSlot(p_335226_, "sourceSlot"),
                                                                                                    BlockPosArgument.getLoadedBlockPos(p_335226_, "pos"),
                                                                                                    SlotArgument.getSlot(p_335226_, "slot"),
                                                                                                    ResourceOrIdArgument.getLootModifier(p_335226_, "modifier")
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                        .then(
                                                            Commands.literal("entity")
                                                                .then(
                                                                    Commands.argument("source", EntityArgument.entity())
                                                                        .then(
                                                                            Commands.argument("sourceSlot", SlotArgument.slot())
                                                                                .executes(
                                                                                    p_180375_ -> entityToBlock(
                                                                                            p_180375_.getSource(),
                                                                                            EntityArgument.getEntity(p_180375_, "source"),
                                                                                            SlotArgument.getSlot(p_180375_, "sourceSlot"),
                                                                                            BlockPosArgument.getLoadedBlockPos(p_180375_, "pos"),
                                                                                            SlotArgument.getSlot(p_180375_, "slot")
                                                                                        )
                                                                                )
                                                                                .then(
                                                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(p_214450_))
                                                                                        .suggests(SUGGEST_MODIFIER)
                                                                                        .executes(
                                                                                            p_335223_ -> entityToBlock(
                                                                                                    (CommandSourceStack)p_335223_.getSource(),
                                                                                                    EntityArgument.getEntity(p_335223_, "source"),
                                                                                                    SlotArgument.getSlot(p_335223_, "sourceSlot"),
                                                                                                    BlockPosArgument.getLoadedBlockPos(p_335223_, "pos"),
                                                                                                    SlotArgument.getSlot(p_335223_, "slot"),
                                                                                                    ResourceOrIdArgument.getLootModifier(p_335223_, "modifier")
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("entity")
                                .then(
                                    Commands.argument("targets", EntityArgument.entities())
                                        .then(
                                            Commands.argument("slot", SlotArgument.slot())
                                                .then(
                                                    Commands.literal("with")
                                                        .then(
                                                            Commands.argument("item", ItemArgument.item(p_214450_))
                                                                .executes(
                                                                    p_180371_ -> setEntityItem(
                                                                            p_180371_.getSource(),
                                                                            EntityArgument.getEntities(p_180371_, "targets"),
                                                                            SlotArgument.getSlot(p_180371_, "slot"),
                                                                            ItemArgument.getItem(p_180371_, "item").createItemStack(1, false)
                                                                        )
                                                                )
                                                                .then(
                                                                    Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                                                        .executes(
                                                                            p_180369_ -> setEntityItem(
                                                                                    p_180369_.getSource(),
                                                                                    EntityArgument.getEntities(p_180369_, "targets"),
                                                                                    SlotArgument.getSlot(p_180369_, "slot"),
                                                                                    ItemArgument.getItem(p_180369_, "item")
                                                                                        .createItemStack(
                                                                                            IntegerArgumentType.getInteger(p_180369_, "count"), true
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                                .then(
                                                    Commands.literal("from")
                                                        .then(
                                                            Commands.literal("block")
                                                                .then(
                                                                    Commands.argument("source", BlockPosArgument.blockPos())
                                                                        .then(
                                                                            Commands.argument("sourceSlot", SlotArgument.slot())
                                                                                .executes(
                                                                                    p_180367_ -> blockToEntities(
                                                                                            p_180367_.getSource(),
                                                                                            BlockPosArgument.getLoadedBlockPos(p_180367_, "source"),
                                                                                            SlotArgument.getSlot(p_180367_, "sourceSlot"),
                                                                                            EntityArgument.getEntities(p_180367_, "targets"),
                                                                                            SlotArgument.getSlot(p_180367_, "slot")
                                                                                        )
                                                                                )
                                                                                .then(
                                                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(p_214450_))
                                                                                        .suggests(SUGGEST_MODIFIER)
                                                                                        .executes(
                                                                                            p_335224_ -> blockToEntities(
                                                                                                    (CommandSourceStack)p_335224_.getSource(),
                                                                                                    BlockPosArgument.getLoadedBlockPos(p_335224_, "source"),
                                                                                                    SlotArgument.getSlot(p_335224_, "sourceSlot"),
                                                                                                    EntityArgument.getEntities(p_335224_, "targets"),
                                                                                                    SlotArgument.getSlot(p_335224_, "slot"),
                                                                                                    ResourceOrIdArgument.getLootModifier(p_335224_, "modifier")
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                        .then(
                                                            Commands.literal("entity")
                                                                .then(
                                                                    Commands.argument("source", EntityArgument.entity())
                                                                        .then(
                                                                            Commands.argument("sourceSlot", SlotArgument.slot())
                                                                                .executes(
                                                                                    p_180363_ -> entityToEntities(
                                                                                            p_180363_.getSource(),
                                                                                            EntityArgument.getEntity(p_180363_, "source"),
                                                                                            SlotArgument.getSlot(p_180363_, "sourceSlot"),
                                                                                            EntityArgument.getEntities(p_180363_, "targets"),
                                                                                            SlotArgument.getSlot(p_180363_, "slot")
                                                                                        )
                                                                                )
                                                                                .then(
                                                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(p_214450_))
                                                                                        .suggests(SUGGEST_MODIFIER)
                                                                                        .executes(
                                                                                            p_335225_ -> entityToEntities(
                                                                                                    (CommandSourceStack)p_335225_.getSource(),
                                                                                                    EntityArgument.getEntity(p_335225_, "source"),
                                                                                                    SlotArgument.getSlot(p_335225_, "sourceSlot"),
                                                                                                    EntityArgument.getEntities(p_335225_, "targets"),
                                                                                                    SlotArgument.getSlot(p_335225_, "slot"),
                                                                                                    ResourceOrIdArgument.getLootModifier(p_335225_, "modifier")
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("modify")
                        .then(
                            Commands.literal("block")
                                .then(
                                    Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(
                                            Commands.argument("slot", SlotArgument.slot())
                                                .then(
                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(p_214450_))
                                                        .suggests(SUGGEST_MODIFIER)
                                                        .executes(
                                                            p_335222_ -> modifyBlockItem(
                                                                    (CommandSourceStack)p_335222_.getSource(),
                                                                    BlockPosArgument.getLoadedBlockPos(p_335222_, "pos"),
                                                                    SlotArgument.getSlot(p_335222_, "slot"),
                                                                    ResourceOrIdArgument.getLootModifier(p_335222_, "modifier")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("entity")
                                .then(
                                    Commands.argument("targets", EntityArgument.entities())
                                        .then(
                                            Commands.argument("slot", SlotArgument.slot())
                                                .then(
                                                    Commands.argument("modifier", ResourceOrIdArgument.lootModifier(p_214450_))
                                                        .suggests(SUGGEST_MODIFIER)
                                                        .executes(
                                                            p_335227_ -> modifyEntityItem(
                                                                    (CommandSourceStack)p_335227_.getSource(),
                                                                    EntityArgument.getEntities(p_335227_, "targets"),
                                                                    SlotArgument.getSlot(p_335227_, "slot"),
                                                                    ResourceOrIdArgument.getLootModifier(p_335227_, "modifier")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int modifyBlockItem(CommandSourceStack p_180297_, BlockPos p_180298_, int p_180299_, Holder<LootItemFunction> p_335627_) throws CommandSyntaxException {
        Container container = getContainer(p_180297_, p_180298_, ERROR_TARGET_NOT_A_CONTAINER);
        if (p_180299_ >= 0 && p_180299_ < container.getContainerSize()) {
            ItemStack itemstack = applyModifier(p_180297_, p_335627_, container.getItem(p_180299_));
            container.setItem(p_180299_, itemstack);
            p_180297_.sendSuccess(
                () -> Component.translatable(
                        "commands.item.block.set.success", p_180298_.getX(), p_180298_.getY(), p_180298_.getZ(), itemstack.getDisplayName()
                    ),
                true
            );
            return 1;
        } else {
            throw ERROR_TARGET_INAPPLICABLE_SLOT.create(p_180299_);
        }
    }

    private static int modifyEntityItem(CommandSourceStack p_180337_, Collection<? extends Entity> p_180338_, int p_180339_, Holder<LootItemFunction> p_335904_) throws CommandSyntaxException {
        Map<Entity, ItemStack> map = Maps.newHashMapWithExpectedSize(p_180338_.size());

        for (Entity entity : p_180338_) {
            SlotAccess slotaccess = entity.getSlot(p_180339_);
            if (slotaccess != SlotAccess.NULL) {
                ItemStack itemstack = applyModifier(p_180337_, p_335904_, slotaccess.get().copy());
                if (slotaccess.set(itemstack)) {
                    map.put(entity, itemstack);
                    if (entity instanceof ServerPlayer) {
                        ((ServerPlayer)entity).containerMenu.broadcastChanges();
                    }
                }
            }
        }

        if (map.isEmpty()) {
            throw ERROR_TARGET_NO_CHANGES.create(p_180339_);
        } else {
            if (map.size() == 1) {
                Entry<Entity, ItemStack> entry = map.entrySet().iterator().next();
                p_180337_.sendSuccess(
                    () -> Component.translatable("commands.item.entity.set.success.single", entry.getKey().getDisplayName(), entry.getValue().getDisplayName()),
                    true
                );
            } else {
                p_180337_.sendSuccess(() -> Component.translatable("commands.item.entity.set.success.multiple", map.size()), true);
            }

            return map.size();
        }
    }

    private static int setBlockItem(CommandSourceStack p_180292_, BlockPos p_180293_, int p_180294_, ItemStack p_180295_) throws CommandSyntaxException {
        Container container = getContainer(p_180292_, p_180293_, ERROR_TARGET_NOT_A_CONTAINER);
        if (p_180294_ >= 0 && p_180294_ < container.getContainerSize()) {
            container.setItem(p_180294_, p_180295_);
            p_180292_.sendSuccess(
                () -> Component.translatable(
                        "commands.item.block.set.success", p_180293_.getX(), p_180293_.getY(), p_180293_.getZ(), p_180295_.getDisplayName()
                    ),
                true
            );
            return 1;
        } else {
            throw ERROR_TARGET_INAPPLICABLE_SLOT.create(p_180294_);
        }
    }

    static Container getContainer(CommandSourceStack p_180328_, BlockPos p_180329_, Dynamic3CommandExceptionType p_180330_) throws CommandSyntaxException {
        BlockEntity blockentity = p_180328_.getLevel().getBlockEntity(p_180329_);
        if (!(blockentity instanceof Container)) {
            throw p_180330_.create(p_180329_.getX(), p_180329_.getY(), p_180329_.getZ());
        } else {
            return (Container)blockentity;
        }
    }

    private static int setEntityItem(CommandSourceStack p_180332_, Collection<? extends Entity> p_180333_, int p_180334_, ItemStack p_180335_) throws CommandSyntaxException {
        List<Entity> list = Lists.newArrayListWithCapacity(p_180333_.size());

        for (Entity entity : p_180333_) {
            SlotAccess slotaccess = entity.getSlot(p_180334_);
            if (slotaccess != SlotAccess.NULL && slotaccess.set(p_180335_.copy())) {
                list.add(entity);
                if (entity instanceof ServerPlayer) {
                    ((ServerPlayer)entity).containerMenu.broadcastChanges();
                }
            }
        }

        if (list.isEmpty()) {
            throw ERROR_TARGET_NO_CHANGES_KNOWN_ITEM.create(p_180335_.getDisplayName(), p_180334_);
        } else {
            if (list.size() == 1) {
                p_180332_.sendSuccess(
                    () -> Component.translatable("commands.item.entity.set.success.single", list.iterator().next().getDisplayName(), p_180335_.getDisplayName()),
                    true
                );
            } else {
                p_180332_.sendSuccess(() -> Component.translatable("commands.item.entity.set.success.multiple", list.size(), p_180335_.getDisplayName()), true);
            }

            return list.size();
        }
    }

    private static int blockToEntities(CommandSourceStack p_180315_, BlockPos p_180316_, int p_180317_, Collection<? extends Entity> p_180318_, int p_180319_) throws CommandSyntaxException {
        return setEntityItem(p_180315_, p_180318_, p_180319_, getBlockItem(p_180315_, p_180316_, p_180317_));
    }

    private static int blockToEntities(
        CommandSourceStack p_180321_,
        BlockPos p_180322_,
        int p_180323_,
        Collection<? extends Entity> p_180324_,
        int p_180325_,
        Holder<LootItemFunction> p_336168_
    ) throws CommandSyntaxException {
        return setEntityItem(p_180321_, p_180324_, p_180325_, applyModifier(p_180321_, p_336168_, getBlockItem(p_180321_, p_180322_, p_180323_)));
    }

    private static int blockToBlock(CommandSourceStack p_180302_, BlockPos p_180303_, int p_180304_, BlockPos p_180305_, int p_180306_) throws CommandSyntaxException {
        return setBlockItem(p_180302_, p_180305_, p_180306_, getBlockItem(p_180302_, p_180303_, p_180304_));
    }

    private static int blockToBlock(
        CommandSourceStack p_180308_, BlockPos p_180309_, int p_180310_, BlockPos p_180311_, int p_180312_, Holder<LootItemFunction> p_335838_
    ) throws CommandSyntaxException {
        return setBlockItem(p_180308_, p_180311_, p_180312_, applyModifier(p_180308_, p_335838_, getBlockItem(p_180308_, p_180309_, p_180310_)));
    }

    private static int entityToBlock(CommandSourceStack p_180258_, Entity p_180259_, int p_180260_, BlockPos p_180261_, int p_180262_) throws CommandSyntaxException {
        return setBlockItem(p_180258_, p_180261_, p_180262_, getEntityItem(p_180259_, p_180260_));
    }

    private static int entityToBlock(
        CommandSourceStack p_180264_, Entity p_180265_, int p_180266_, BlockPos p_180267_, int p_180268_, Holder<LootItemFunction> p_335965_
    ) throws CommandSyntaxException {
        return setBlockItem(p_180264_, p_180267_, p_180268_, applyModifier(p_180264_, p_335965_, getEntityItem(p_180265_, p_180266_)));
    }

    private static int entityToEntities(CommandSourceStack p_180271_, Entity p_180272_, int p_180273_, Collection<? extends Entity> p_180274_, int p_180275_) throws CommandSyntaxException {
        return setEntityItem(p_180271_, p_180274_, p_180275_, getEntityItem(p_180272_, p_180273_));
    }

    private static int entityToEntities(
        CommandSourceStack p_180277_,
        Entity p_180278_,
        int p_180279_,
        Collection<? extends Entity> p_180280_,
        int p_180281_,
        Holder<LootItemFunction> p_335469_
    ) throws CommandSyntaxException {
        return setEntityItem(p_180277_, p_180280_, p_180281_, applyModifier(p_180277_, p_335469_, getEntityItem(p_180278_, p_180279_)));
    }

    private static ItemStack applyModifier(CommandSourceStack p_180284_, Holder<LootItemFunction> p_335682_, ItemStack p_180286_) {
        ServerLevel serverlevel = p_180284_.getLevel();
        LootParams lootparams = new LootParams.Builder(serverlevel)
            .withParameter(LootContextParams.ORIGIN, p_180284_.getPosition())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, p_180284_.getEntity())
            .create(LootContextParamSets.COMMAND);
        LootContext lootcontext = new LootContext.Builder(lootparams).create(Optional.empty());
        lootcontext.pushVisitedElement(LootContext.createVisitedEntry(p_335682_.value()));
        ItemStack itemstack = p_335682_.value().apply(p_180286_, lootcontext);
        itemstack.limitSize(itemstack.getMaxStackSize());
        return itemstack;
    }

    private static ItemStack getEntityItem(Entity p_180246_, int p_180247_) throws CommandSyntaxException {
        SlotAccess slotaccess = p_180246_.getSlot(p_180247_);
        if (slotaccess == SlotAccess.NULL) {
            throw ERROR_SOURCE_INAPPLICABLE_SLOT.create(p_180247_);
        } else {
            return slotaccess.get().copy();
        }
    }

    private static ItemStack getBlockItem(CommandSourceStack p_180288_, BlockPos p_180289_, int p_180290_) throws CommandSyntaxException {
        Container container = getContainer(p_180288_, p_180289_, ERROR_SOURCE_NOT_A_CONTAINER);
        if (p_180290_ >= 0 && p_180290_ < container.getContainerSize()) {
            return container.getItem(p_180290_).copy();
        } else {
            throw ERROR_SOURCE_INAPPLICABLE_SLOT.create(p_180290_);
        }
    }
}
