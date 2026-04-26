package net.minecraft.world.inventory;

@net.neoforged.fml.common.asm.enumextension.NetworkedEnum(net.neoforged.fml.common.asm.enumextension.NetworkedEnum.NetworkCheck.CLIENTBOUND)
public enum RecipeBookType implements net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
    CRAFTING,
    FURNACE,
    BLAST_FURNACE,
    SMOKER;

    public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
        return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(RecipeBookType.class);
    }
}
