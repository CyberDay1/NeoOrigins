package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, NeoOrigins.MOD_ID);

    // Decaying cobweb used by legacy Apoli packs (origins:temporary_cobweb).
    // Intentionally has no BlockItem: only placed by powers/actions.
    public static final DeferredHolder<Block, TemporaryCobwebBlock> TEMPORARY_COBWEB =
        BLOCKS.register("temporary_cobweb", () -> new TemporaryCobwebBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOL)
                .forceSolidOn()
                .noCollission()
                .requiresCorrectToolForDrops()
                .strength(4.0F)
                .noLootTable()));

    // Compat alias in the legacy namespace. Vanilla command parsing (e.g. the
    // `setblock ~ ~ ~ origins:temporary_cobweb` inside Origins++'s
    // broodmother/cobweb.mcfunction) resolves block ids straight from the
    // registry, where LegacyBlockIds can't intercept — the id itself has to
    // exist or the whole function fails to load. Separate instance because a
    // Block can only be registered once.
    public static final DeferredRegister<Block> LEGACY_ORIGINS_BLOCKS =
        DeferredRegister.create(Registries.BLOCK, "origins");

    public static final DeferredHolder<Block, TemporaryCobwebBlock> LEGACY_TEMPORARY_COBWEB =
        LEGACY_ORIGINS_BLOCKS.register("temporary_cobweb", () -> new TemporaryCobwebBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOL)
                .forceSolidOn()
                .noCollission()
                .requiresCorrectToolForDrops()
                .strength(4.0F)
                .noLootTable()));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        LEGACY_ORIGINS_BLOCKS.register(modEventBus);
    }
}
