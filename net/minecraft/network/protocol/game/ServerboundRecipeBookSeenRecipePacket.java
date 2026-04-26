package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public class ServerboundRecipeBookSeenRecipePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundRecipeBookSeenRecipePacket> STREAM_CODEC = Packet.codec(
        ServerboundRecipeBookSeenRecipePacket::write, ServerboundRecipeBookSeenRecipePacket::new
    );
    private final ResourceLocation recipe;

    public ServerboundRecipeBookSeenRecipePacket(RecipeHolder<?> p_301152_) {
        this.recipe = p_301152_.id();
    }

    private ServerboundRecipeBookSeenRecipePacket(FriendlyByteBuf p_179736_) {
        this.recipe = p_179736_.readResourceLocation();
    }

    private void write(FriendlyByteBuf p_134392_) {
        p_134392_.writeResourceLocation(this.recipe);
    }

    @Override
    public PacketType<ServerboundRecipeBookSeenRecipePacket> type() {
        return GamePacketTypes.SERVERBOUND_RECIPE_BOOK_SEEN_RECIPE;
    }

    public void handle(ServerGamePacketListener p_134389_) {
        p_134389_.handleRecipeBookSeenRecipePacket(this);
    }

    public ResourceLocation getRecipe() {
        return this.recipe;
    }
}
