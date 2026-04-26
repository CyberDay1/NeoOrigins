package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public class ClientboundPlaceGhostRecipePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundPlaceGhostRecipePacket> STREAM_CODEC = Packet.codec(
        ClientboundPlaceGhostRecipePacket::write, ClientboundPlaceGhostRecipePacket::new
    );
    private final int containerId;
    private final ResourceLocation recipe;

    public ClientboundPlaceGhostRecipePacket(int p_132647_, RecipeHolder<?> p_301206_) {
        this.containerId = p_132647_;
        this.recipe = p_301206_.id();
    }

    private ClientboundPlaceGhostRecipePacket(FriendlyByteBuf p_179027_) {
        this.containerId = p_179027_.readByte();
        this.recipe = p_179027_.readResourceLocation();
    }

    private void write(FriendlyByteBuf p_132657_) {
        p_132657_.writeByte(this.containerId);
        p_132657_.writeResourceLocation(this.recipe);
    }

    @Override
    public PacketType<ClientboundPlaceGhostRecipePacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLACE_GHOST_RECIPE;
    }

    public void handle(ClientGamePacketListener p_132654_) {
        p_132654_.handlePlaceRecipe(this);
    }

    public ResourceLocation getRecipe() {
        return this.recipe;
    }

    public int getContainerId() {
        return this.containerId;
    }
}
