package com.cyberday1.neoorigins.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.List;

/**
 * Codec-based serializer for {@link OriginGatedRecipe}.
 *
 * <p>JSON layout:
 * <pre>
 * {
 *   "type":  "neoorigins:origin_gated_crafting",
 *   "gates": [ OriginGate, ... ],
 *   "inner": Recipe   // must be a CraftingRecipe (RecipeType.CRAFTING)
 * }
 * </pre>
 *
 * <p>The {@code inner} field uses {@link Recipe#CODEC} for the dispatch on
 * {@code RecipeSerializer}, and we narrow the resulting {@link Recipe} to a
 * {@link CraftingRecipe} (failing fast at load time if it isn't one). This
 * mirrors how vanilla composes {@code crafting_shaped} / {@code crafting_shapeless}
 * payloads and lets pack authors reuse any registered crafting serializer as
 * the wrapped form (shaped, shapeless, smithing, third-party).
 *
 * <p>Stream codec syncs the gate list + the inner recipe via
 * {@link Recipe#STREAM_CODEC}; the recipe-book payload picks this up on the
 * recipe-sync packet sent at login + datapack reload, so clients evaluate the
 * gates locally for the recipe-book completion overlay too (see
 * {@link OriginGate.HasPower#test} for the client-side best-effort path).
 */
public final class OriginGatedRecipeSerializer implements RecipeSerializer<OriginGatedRecipe> {

    private static final MapCodec<OriginGatedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        OriginGate.CODEC.listOf().fieldOf("gates").forGetter(OriginGatedRecipe::gates),
        Recipe.CODEC.fieldOf("inner").forGetter(r -> r.inner())
    ).apply(inst, (gates, inner) -> {
        if (!(inner instanceof CraftingRecipe craftingRecipe)) {
            throw new IllegalArgumentException(
                "neoorigins:origin_gated_crafting requires an inner recipe of RecipeType.CRAFTING; got "
                    + inner.getType());
        }
        return new OriginGatedRecipe(gates, craftingRecipe);
    }));

    // Recipe.CODEC operates on Recipe<?> with a wildcard. Recipe.STREAM_CODEC
    // is on Recipe<?>; we map the inner via a narrowing cast on decode.
    private static final StreamCodec<RegistryFriendlyByteBuf, OriginGatedRecipe> STREAM_CODEC =
        StreamCodec.composite(
            OriginGate.STREAM_CODEC.apply(ByteBufCodecs.list()),
            OriginGatedRecipe::gates,
            Recipe.STREAM_CODEC,
            r -> (Recipe<?>) r.inner(),
            (gates, inner) -> {
                if (!(inner instanceof CraftingRecipe cr)) {
                    throw new IllegalStateException(
                        "Received non-crafting inner recipe for origin_gated_crafting over network: "
                            + inner.getType());
                }
                return new OriginGatedRecipe((List<OriginGate>) gates, cr);
            }
        );

    @Override
    public MapCodec<OriginGatedRecipe> codec() {
        return MAP_CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, OriginGatedRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
