package com.cyberday1.neoorigins.compat.jei;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.content.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class NeoOriginsJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(
            new ItemStack(ModItems.ORB_OF_ORIGIN.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("jei.neoorigins.orb_of_origin.info")
        );
    }
}
