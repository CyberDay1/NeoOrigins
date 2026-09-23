package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.PowerLayers;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A toggle-off flag is persisted, so it outlives death, relog and world reload. The
 * reset that frees it is the class/origin swap itself: every layer reset revokes the
 * old origin's powers, and each toggle power clears its own flag on revoke. This pins
 * that on the Rogue's Stealth, whose Class Skill key 2.2.28 hands to Step Assist.
 */
// hub: neoorigins/lifecycle-rig.md
class ToggleResetLifecycleTest {

    private static final ResourceLocation ROGUE = ResourceLocation.fromNamespaceAndPath("neoorigins", "class_rogue");
    private static final String STEALTH = "neoorigins:class_rogue_stealth";
    /** Pre-2.2.24 saves keyed every Stealth flag by class name. */
    private static final String STEALTH_LEGACY = "com.cyberday1.neoorigins.power.builtin.StealthPower";
    private static final String UNRELATED = "neoorigins:some_origin_layer_toggle";

    @BeforeAll
    static void load() {
        RealDatapack.load();
    }

    @Test
    void aToggleSurvivesASaveAndIsFreedByTheClassReset() {
        ServerPlayer sp = rogue();
        data(sp).setPowerToggledOff(STEALTH, true);
        data(sp).setPowerToggledOff(UNRELATED, true);

        reload(sp);
        assertTrue(data(sp).isPowerToggledOff(STEALTH), "the flag must persist, or none of this is a trap");

        // The Orb of Class / layer picker revoke leg (NeoOriginsNetwork#beginLayerPicker).
        ActiveOriginService.applyOriginPowers(sp, PowerLayers.CLASS_LAYER, ROGUE, null);
        assertFalse(data(sp).isPowerToggledOff(STEALTH), "class reset must free the Rogue's stealth flag");
        assertTrue(data(sp).isPowerToggledOff(UNRELATED), "a choice outside the reset layer must survive it");
    }

    @Test
    void aPreUpdateLegacyFlagIsFreedByTheClassReset() {
        ServerPlayer sp = rogue();
        data(sp).setPowerToggledOff(STEALTH_LEGACY, true);
        reload(sp);
        assertTrue(data(sp).isPowerToggledOff(STEALTH, STEALTH_LEGACY));

        ActiveOriginService.applyOriginPowers(sp, PowerLayers.CLASS_LAYER, ROGUE, null);
        assertFalse(data(sp).isPowerToggledOff(STEALTH, STEALTH_LEGACY));
    }

    @Test
    void aFullResetFreesEveryFlag() {
        ServerPlayer sp = rogue();
        data(sp).setPowerToggledOff(STEALTH, true);
        data(sp).setPowerToggledOff(UNRELATED, true);
        reload(sp);

        // /neoorigins reset <player> and reapplyOrigins both end in clear().
        data(sp).clear();
        assertFalse(data(sp).isPowerToggledOff(STEALTH));
        assertFalse(data(sp).isPowerToggledOff(UNRELATED));
    }

    private static ServerPlayer rogue() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        data(sp).setOrigin(PowerLayers.CLASS_LAYER, ROGUE);
        ActiveOriginService.applyOriginPowers(sp, PowerLayers.CLASS_LAYER, null, ROGUE);
        return sp;
    }

    /** Serialise and deserialise the attachment, as a relog or world reload does. */
    private static void reload(ServerPlayer sp) {
        Tag saved = PlayerOriginData.CODEC.encodeStart(NbtOps.INSTANCE, data(sp)).getOrThrow();
        sp.setData(OriginAttachments.originData(), PlayerOriginData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow());
    }

    private static PlayerOriginData data(ServerPlayer sp) {
        return sp.getData(OriginAttachments.originData());
    }
}
