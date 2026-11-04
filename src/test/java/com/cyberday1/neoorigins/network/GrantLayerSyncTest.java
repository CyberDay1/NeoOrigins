package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData.PowerGrant;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The layer each API-granted power is sent to the client with, for the info screen. */
class GrantLayerSyncTest {

    private static final Identifier MOD = Identifier.fromNamespaceAndPath("testmod", "a");
    private static final Identifier OTHER_MOD = Identifier.fromNamespaceAndPath("testmod", "b");
    private static final Identifier CLASS = Identifier.fromNamespaceAndPath("neoorigins", "class");
    private static final Identifier BLINK = Identifier.fromNamespaceAndPath("testmod", "blink");
    private static final Identifier WARD = Identifier.fromNamespaceAndPath("testmod", "ward");

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
