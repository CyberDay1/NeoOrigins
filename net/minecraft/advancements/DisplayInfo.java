package net.minecraft.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class DisplayInfo {
    public static final Codec<DisplayInfo> CODEC = RecordCodecBuilder.create(
        p_312917_ -> p_312917_.group(
                    ItemStack.STRICT_CODEC.fieldOf("icon").forGetter(DisplayInfo::getIcon),
                    ComponentSerialization.CODEC.fieldOf("title").forGetter(DisplayInfo::getTitle),
                    ComponentSerialization.CODEC.fieldOf("description").forGetter(DisplayInfo::getDescription),
                    ResourceLocation.CODEC.optionalFieldOf("background").forGetter(DisplayInfo::getBackground),
                    AdvancementType.CODEC.optionalFieldOf("frame", AdvancementType.TASK).forGetter(DisplayInfo::getType),
                    Codec.BOOL.optionalFieldOf("show_toast", Boolean.valueOf(true)).forGetter(DisplayInfo::shouldShowToast),
                    Codec.BOOL.optionalFieldOf("announce_to_chat", Boolean.valueOf(true)).forGetter(DisplayInfo::shouldAnnounceChat),
                    Codec.BOOL.optionalFieldOf("hidden", Boolean.valueOf(false)).forGetter(DisplayInfo::isHidden)
                )
                .apply(p_312917_, DisplayInfo::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayInfo> STREAM_CODEC = StreamCodec.ofMember(
        DisplayInfo::serializeToNetwork, DisplayInfo::fromNetwork
    );
    private final Component title;
    private final Component description;
    private final ItemStack icon;
    private final Optional<ResourceLocation> background;
    private final AdvancementType type;
    private final boolean showToast;
    private final boolean announceChat;
    private final boolean hidden;
    private float x;
    private float y;

    public DisplayInfo(
        ItemStack p_14969_,
        Component p_14970_,
        Component p_14971_,
        Optional<ResourceLocation> p_312452_,
        AdvancementType p_312497_,
        boolean p_14974_,
        boolean p_14975_,
        boolean p_14976_
    ) {
        this.title = p_14970_;
        this.description = p_14971_;
        this.icon = p_14969_;
        this.background = p_312452_;
        this.type = p_312497_;
        this.showToast = p_14974_;
        this.announceChat = p_14975_;
        this.hidden = p_14976_;
    }

    public void setLocation(float p_14979_, float p_14980_) {
        this.x = p_14979_;
        this.y = p_14980_;
    }

    public Component getTitle() {
        return this.title;
    }

    public Component getDescription() {
        return this.description;
    }

    public ItemStack getIcon() {
        return this.icon;
    }

    public Optional<ResourceLocation> getBackground() {
        return this.background;
    }

    public AdvancementType getType() {
        return this.type;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public boolean shouldShowToast() {
        return this.showToast;
    }

    public boolean shouldAnnounceChat() {
        return this.announceChat;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    private void serializeToNetwork(RegistryFriendlyByteBuf p_319889_) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(p_319889_, this.title);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(p_319889_, this.description);
        ItemStack.STREAM_CODEC.encode(p_319889_, this.icon);
        p_319889_.writeEnum(this.type);
        int i = 0;
        if (this.background.isPresent()) {
            i |= 1;
        }

        if (this.showToast) {
            i |= 2;
        }

        if (this.hidden) {
            i |= 4;
        }

        p_319889_.writeInt(i);
        this.background.ifPresent(p_319889_::writeResourceLocation);
        p_319889_.writeFloat(this.x);
        p_319889_.writeFloat(this.y);
    }

    private static DisplayInfo fromNetwork(RegistryFriendlyByteBuf p_319903_) {
        Component component = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(p_319903_);
        Component component1 = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(p_319903_);
        ItemStack itemstack = ItemStack.STREAM_CODEC.decode(p_319903_);
        AdvancementType advancementtype = p_319903_.readEnum(AdvancementType.class);
        int i = p_319903_.readInt();
        Optional<ResourceLocation> optional = (i & 1) != 0 ? Optional.of(p_319903_.readResourceLocation()) : Optional.empty();
        boolean flag = (i & 2) != 0;
        boolean flag1 = (i & 4) != 0;
        DisplayInfo displayinfo = new DisplayInfo(itemstack, component, component1, optional, advancementtype, flag, false, flag1);
        displayinfo.setLocation(p_319903_.readFloat(), p_319903_.readFloat());
        return displayinfo;
    }
}
