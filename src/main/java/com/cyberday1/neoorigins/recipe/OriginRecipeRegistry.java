package com.cyberday1.neoorigins.recipe;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Vanilla recipe-serializer registration for origin-gated recipes. Hooks the
 * standard {@link BuiltInRegistries#RECIPE_SERIALIZER} via {@link DeferredRegister},
 * so the serializer is available on BOTH the dedicated server (recipe loading
 * + match evaluation) AND the client (recipe-book sync deserialization) — both
 * sides resolve {@code neoorigins:origin_gated_crafting} to the same class.
 *
 * <p>{@link com.cyberday1.neoorigins.NeoOrigins#NeoOrigins} calls
 * {@link #register(IEventBus)} during mod construction.
 */
public final class OriginRecipeRegistry {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, NeoOrigins.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, OriginGatedRecipeSerializer>
        ORIGIN_GATED_CRAFTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("origin_gated_crafting", OriginGatedRecipeSerializer::new);

    private OriginRecipeRegistry() {}

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
