package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

@net.neoforged.fml.common.asm.enumextension.IndexedEnum
@net.neoforged.fml.common.asm.enumextension.NamedEnum(1)
@net.neoforged.fml.common.asm.enumextension.NetworkedEnum(net.neoforged.fml.common.asm.enumextension.NetworkedEnum.NetworkCheck.CLIENTBOUND)
public enum ItemDisplayContext implements StringRepresentable, net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
    NONE(0, "none"),
    THIRD_PERSON_LEFT_HAND(1, "thirdperson_lefthand"),
    THIRD_PERSON_RIGHT_HAND(2, "thirdperson_righthand"),
    FIRST_PERSON_LEFT_HAND(3, "firstperson_lefthand"),
    FIRST_PERSON_RIGHT_HAND(4, "firstperson_righthand"),
    HEAD(5, "head"),
    GUI(6, "gui"),
    GROUND(7, "ground"),
    FIXED(8, "fixed");

    public static final Codec<ItemDisplayContext> CODEC = StringRepresentable.fromEnum(ItemDisplayContext::values);
    public static final IntFunction<ItemDisplayContext> BY_ID = ByIdMap.continuous(ItemDisplayContext::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    private final byte id;
    private final String name;
    private final boolean isModded;
    private final java.util.function.Supplier<ItemDisplayContext> fallback;

    @net.neoforged.fml.common.asm.enumextension.ReservedConstructor
    private ItemDisplayContext(int p_270624_, String p_270851_) {
        this.name = p_270851_;
        this.id = (byte)p_270624_;
        this.isModded = false;
        this.fallback = () -> null;
    }

    private ItemDisplayContext(int id, String name, @org.jetbrains.annotations.Nullable String fallbackName) {
        this.id = (byte)id;
        this.name = name;
        this.isModded = true;
        this.fallback = fallbackName == null ? () -> null : com.google.common.base.Suppliers.memoize(() -> ItemDisplayContext.valueOf(fallbackName));
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public byte getId() {
        return this.id;
    }

    public boolean firstPerson() {
        return this == FIRST_PERSON_LEFT_HAND || this == FIRST_PERSON_RIGHT_HAND;
    }

    public boolean isModded() {
        return isModded;
    }

    @org.jetbrains.annotations.Nullable
    public ItemDisplayContext fallback() {
        return fallback.get();
    }

    public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
        return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(ItemDisplayContext.class);
    }
}
