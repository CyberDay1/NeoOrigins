package net.minecraft.client.multiplayer.prediction;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface PredictiveAction {
    Packet<ServerGamePacketListener> predict(int p_233886_);
}
