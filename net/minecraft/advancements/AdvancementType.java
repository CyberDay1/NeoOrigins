package net.minecraft.advancements;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;

public enum AdvancementType implements StringRepresentable {
    TASK("task", ChatFormatting.GREEN),
    CHALLENGE("challenge", ChatFormatting.DARK_PURPLE),
    GOAL("goal", ChatFormatting.GREEN);

    public static final Codec<AdvancementType> CODEC = StringRepresentable.fromEnum(AdvancementType::values);
    private final String name;
    private final ChatFormatting chatColor;
    private final Component displayName;

    private AdvancementType(String p_312210_, ChatFormatting p_312089_) {
        this.name = p_312210_;
        this.chatColor = p_312089_;
        this.displayName = Component.translatable("advancements.toast." + p_312210_);
    }

    public ChatFormatting getChatColor() {
        return this.chatColor;
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public MutableComponent createAnnouncement(AdvancementHolder p_311791_, ServerPlayer p_312517_) {
        return Component.translatable("chat.type.advancement." + this.name, p_312517_.getDisplayName(), Advancement.name(p_311791_));
    }
}
