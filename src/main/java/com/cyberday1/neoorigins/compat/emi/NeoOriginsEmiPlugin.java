package com.cyberday1.neoorigins.compat.emi;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.content.ModItems;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * EMI recipe-viewer integration — soft dependency, dormant if EMI is absent.
 *
 * <p>Mirrors the JEI plugin: adds an information panel for the Orb of Origin,
 * reusing the same English copy via the {@code emi.neoorigins.orb_of_origin.info}
 * lang key. Only classloaded when EMI is installed (EMI discovers it via the
 * {@link EmiEntrypoint} annotation).
 */
@EmiEntrypoint
public class NeoOriginsEmiPlugin implements EmiPlugin {

    private static final ResourceLocation ORB_INFO_ID =
        ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "orb_of_origin_info");

    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipe(new EmiInfoRecipe(
            List.<EmiIngredient>of(EmiStack.of(ModItems.ORB_OF_ORIGIN.get())),
            List.of(Component.translatable("emi.neoorigins.orb_of_origin.info")),
            ORB_INFO_ID
        ));
    }
}
