package net.minecraft.server.level;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;

public record ClientInformation(
    String language,
    int viewDistance,
    ChatVisiblity chatVisibility,
    boolean chatColors,
    int modelCustomisation,
    HumanoidArm mainHand,
    boolean textFilteringEnabled,
    boolean allowsListing
) {
    public static final int MAX_LANGUAGE_LENGTH = 16;

    public ClientInformation(FriendlyByteBuf p_302026_) {
        this(
            p_302026_.readUtf(16),
            p_302026_.readByte(),
            p_302026_.readEnum(ChatVisiblity.class),
            p_302026_.readBoolean(),
            p_302026_.readUnsignedByte(),
            p_302026_.readEnum(HumanoidArm.class),
            p_302026_.readBoolean(),
            p_302026_.readBoolean()
        );
    }

    public void write(FriendlyByteBuf p_302000_) {
        p_302000_.writeUtf(this.language);
        p_302000_.writeByte(this.viewDistance);
        p_302000_.writeEnum(this.chatVisibility);
        p_302000_.writeBoolean(this.chatColors);
        p_302000_.writeByte(this.modelCustomisation);
        p_302000_.writeEnum(this.mainHand);
        p_302000_.writeBoolean(this.textFilteringEnabled);
        p_302000_.writeBoolean(this.allowsListing);
    }

    public static ClientInformation createDefault() {
        return new ClientInformation("en_us", 2, ChatVisiblity.FULL, true, 0, Player.DEFAULT_MAIN_HAND, false, false);
    }
}
