package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.network.payload.ActivatePowerPayload;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public class NeoOriginsClientEvents {

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer)) return;
        if (NeoOriginsKeybindings.SKILL_1.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(0));
        if (NeoOriginsKeybindings.SKILL_2.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(1));
        if (NeoOriginsKeybindings.SKILL_3.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(2));
        if (NeoOriginsKeybindings.SKILL_4.consumeClick()) ClientPacketDistributor.sendToServer(new ActivatePowerPayload(3));
    }
}
