package com.cyberday1.neoorigins.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the in-progress block-break that {@link ServerPlayerGameMode} keeps
 * private, for the {@code using_effective_tool} condition.
 *
 * <p>Both fields are {@code private} with no getter, and the state is genuinely
 * only knowable from here: it is set when the player starts destroying a block
 * and cleared when they stop or finish, so there is no event to latch onto that
 * would answer "is this player mining <em>right now</em>" at the arbitrary
 * moment a power evaluates its condition. Reconstructing it from the break
 * events would mean shadowing vanilla state we would then have to keep in sync
 * with every abort path.
 *
 * <p>Accessors rather than an access transformer because this is a read-only
 * peek at two fields, and an AT would widen them for everything.
 *
 * <p>Server side only. Upstream Apoli's condition has a second branch for
 * {@code LocalPlayer} that reads the equivalent fields on
 * {@code MultiPlayerGameMode}; NeoOrigins evaluates conditions server-side only,
 * so that branch has no reachable caller here and no client mirror is kept.
 */
@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeMiningAccessor {

    @Accessor("isDestroyingBlock")
    boolean neoorigins$isDestroyingBlock();

    @Accessor("destroyPos")
    BlockPos neoorigins$getDestroyPos();
}
