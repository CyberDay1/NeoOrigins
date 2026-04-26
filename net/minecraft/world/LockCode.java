package net.minecraft.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record LockCode(String key) {
    public static final LockCode NO_LOCK = new LockCode("");
    public static final Codec<LockCode> CODEC = Codec.STRING.xmap(LockCode::new, LockCode::key);
    public static final String TAG_LOCK = "Lock";

    public boolean unlocksWith(ItemStack p_19108_) {
        if (this.key.isEmpty()) {
            return true;
        } else {
            Component component = p_19108_.get(DataComponents.CUSTOM_NAME);
            return component != null && this.key.equals(component.getString());
        }
    }

    public void addToTag(CompoundTag p_19110_) {
        if (!this.key.isEmpty()) {
            p_19110_.putString("Lock", this.key);
        }
    }

    public static LockCode fromTag(CompoundTag p_19112_) {
        return p_19112_.contains("Lock", 8) ? new LockCode(p_19112_.getString("Lock")) : NO_LOCK;
    }
}
