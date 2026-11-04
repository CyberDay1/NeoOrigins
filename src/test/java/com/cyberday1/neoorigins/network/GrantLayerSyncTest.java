package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData.PowerGrant;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The layer each API-granted power is sent to the client with, for the info screen. */
class GrantLayerSyncTest {

    private static final ResourceLocation MOD = ResourceLocation.fromNamespaceAndPath("testmod", "a");
    private static final ResourceLocation OTHER_MOD = ResourceLocation.fromNamespaceAndPath("testmod", "b");
    private static final ResourceLocation CLASS = ResourceLocation.fromNamespaceAndPath("neoorigins", "class");
    private static final ResourceLocation BLINK = ResourceLocation.fromNamespaceAndPath("testmod", "blink");
    private static final ResourceLocation WARD = ResourceLocation.fromNamespaceAndPath("testmod", "ward");

    @Test
    void eachGrantedPowerCarriesItsGrantsLayer() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        var data = sp.getData(OriginAttachments.originData());
        data.addPowerGrant(new PowerGrant(BLINK, MOD, null));
        data.addPowerGrant(new PowerGrant(BLINK, OTHER_MOD, CLASS));
        data.addPowerGrant(new PowerGrant(WARD, MOD, null));

        assertEquals(Map.of(BLINK, Optional.of(CLASS), WARD, Optional.empty()),
            NeoOriginsNetwork.apiGrantLayers(sp),
            "a layer-tied grant wins over an untied one; an untied grant names no layer");
    }
}
