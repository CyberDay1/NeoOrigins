package net.minecraft.server.packs;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;

public record PackLocationInfo(String id, Component title, PackSource source, Optional<KnownPack> knownPackInfo) {
    public Component createChatLink(boolean p_326100_, Component p_325972_) {
        return ComponentUtils.wrapInSquareBrackets(this.source.decorate(Component.literal(this.id)))
            .withStyle(
                p_325937_ -> p_325937_.withColor(p_326100_ ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .withInsertion(StringArgumentType.escapeIfRequired(this.id))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.empty().append(this.title).append("\n").append(p_325972_)))
            );
    }
}
