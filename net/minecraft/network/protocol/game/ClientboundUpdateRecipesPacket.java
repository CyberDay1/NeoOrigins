package net.minecraft.network.protocol.game;

import java.util.Collection;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.item.crafting.RecipeHolder;

public class ClientboundUpdateRecipesPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateRecipesPacket> STREAM_CODEC = StreamCodec.composite(
        RecipeHolder.STREAM_CODEC.apply(ByteBufCodecs.list()), p_319556_ -> p_319556_.recipes, ClientboundUpdateRecipesPacket::new
    );
    private final List<RecipeHolder<?>> recipes;

    public ClientboundUpdateRecipesPacket(Collection<RecipeHolder<?>> p_133632_) {
        this.recipes = List.copyOf(p_133632_);
    }

    @Override
    public PacketType<ClientboundUpdateRecipesPacket> type() {
        return GamePacketTypes.CLIENTBOUND_UPDATE_RECIPES;
    }

    public void handle(ClientGamePacketListener p_133641_) {
        p_133641_.handleUpdateRecipes(this);
    }

    public List<RecipeHolder<?>> getRecipes() {
        return this.recipes;
    }
}
