package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public class ServerboundPlaceRecipePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPlaceRecipePacket> STREAM_CODEC = Packet.codec(
        ServerboundPlaceRecipePacket::write, ServerboundPlaceRecipePacket::new
    );
    private final int containerId;
    private final ResourceLocation recipe;
    private final boolean shiftDown;

    public ServerboundPlaceRecipePacket(int p_134240_, RecipeHolder<?> p_300889_, boolean p_134242_) {
        this.containerId = p_134240_;
        this.recipe = p_300889_.id();
        this.shiftDown = p_134242_;
    }

    private ServerboundPlaceRecipePacket(FriendlyByteBuf p_179706_) {
        this.containerId = p_179706_.readByte();
        this.recipe = p_179706_.readResourceLocation();
        this.shiftDown = p_179706_.readBoolean();
    }

    private void write(FriendlyByteBuf p_134251_) {
        p_134251_.writeByte(this.containerId);
        p_134251_.writeResourceLocation(this.recipe);
        p_134251_.writeBoolean(this.shiftDown);
    }

    @Override
    public PacketType<ServerboundPlaceRecipePacket> type() {
        return GamePacketTypes.SERVERBOUND_PLACE_RECIPE;
    }

    public void handle(ServerGamePacketListener p_134248_) {
        p_134248_.handlePlaceRecipe(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public ResourceLocation getRecipe() {
        return this.recipe;
    }

    public boolean isShiftDown() {
        return this.shiftDown;
    }
}
