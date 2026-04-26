package net.minecraft.client;

import net.minecraft.obfuscate.DontObfuscate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientBrandRetriever {
    public static final String VANILLA_NAME = "vanilla";

    @DontObfuscate
    public static String getClientModName() {
        return net.neoforged.neoforge.internal.BrandingControl.getClientBranding();
    }
}
