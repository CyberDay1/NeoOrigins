package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.PowerLayers;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.effect.ModEffects;
import com.cyberday1.neoorigins.effect.SuppressionEffect;
import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import com.cyberday1.neoorigins.power.builtin.base.OffFlagToggle;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A toggle no key can reach has its off-flag cleared at login. Since 2.2.28
 * the Rogue's one class slot fires Step Assist, so a Stealth switched off before
 * then had nothing left to switch it on.
 */
// hub: neoorigins/class-skill-slot.md
class KeylessToggleReleaseTest {

    private static final ResourceLocation ORIGIN_LAYER = id("origin");
    private static final ResourceLocation ROGUE = id("class_rogue");
    private static final ResourceLocation WRAITH = id("wraith");
    private static final String STEALTH = "neoorigins:class_rogue_stealth";
    private static final String STEALTH_LEGACY = "com.cyberday1.neoorigins.power.builtin.StealthPower";
    private static final String PHASE = "neoorigins:wraith_phase";
    private static final String APEX_PHASE = "neoorigins:wraith_apex_phase";
    private static final String SLOW_FALL = "neoorigins:avian_slow_fall";
    private static final String PHASE_LEGACY = "com.cyberday1.neoorigins.power.builtin.WraithPhasePower";

    @BeforeAll
    static void load() {
        RealDatapack.load();
    }

    @Test
    void theRoguesKeylessStealthIsBackOnAfterLogin() {
        ServerPlayer sp = player(null, ROGUE);
        assertNotEquals(STEALTH, ActiveOriginService.activeClassPowers(sp).get(0).id().toString(),
            "Stealth must not hold the class slot, or this is not the stuck shape");
        data(sp).setPowerToggledOff(STEALTH, true);
        sp = reload(sp);

        login(sp);
        assertFalse(data(sp).isPowerToggledOff(STEALTH));
    }

    @Test
    void aPreUpdateLegacyStealthFlagIsFreedToo() {
        ServerPlayer sp = player(null, ROGUE);
        data(sp).setPowerToggledOff(STEALTH_LEGACY, true);
        sp = reload(sp);

        login(sp);
        assertFalse(data(sp).isPowerToggledOff(STEALTH, STEALTH_LEGACY));
    }

    @Test
    void aToggleWithAKeyKeepsThePlayersChoice() {
        ServerPlayer sp = player(WRAITH, ROGUE);
        data(sp).setPowerToggledOff(PHASE, true);
        data(sp).setPowerToggledOff(STEALTH, true);
        sp = reload(sp);

        login(sp);
        assertTrue(data(sp).isPowerToggledOff(PHASE), "Wraith Phase has a skill key, so off is the player's choice");
        assertFalse(data(sp).isPowerToggledOff(STEALTH));
    }

    /** Past the sixth skill slot a toggle is keyless, but its pre-2.2.24 key is shared. */
    @Test
    void aSharedLegacyKeyStaysOffForTheToggleThatStillHasAKey() {
        ServerPlayer sp = player(WRAITH, null);
        grantPastTheSixthSlot(sp, APEX_PHASE);
        assertEquals(0, slotOf(sp, PHASE));
        data(sp).setPowerToggledOff(PHASE_LEGACY, true);

        assertEquals(List.of(ResourceLocation.parse(APEX_PHASE)), KeylessToggleRelease.release(sp));
        assertTrue(data(sp).isPowerToggledOff(PHASE, PHASE_LEGACY));
        assertFalse(data(sp).isPowerToggledOff(APEX_PHASE, PHASE_LEGACY));
    }

    /** The ruling covers any keyless toggle, so a default_off one is cleared too. */
    @Test
    void aKeylessDefaultOffToggleIsClearedToo() {
        ServerPlayer sp = player(WRAITH, null);
        grantPastTheSixthSlot(sp, SLOW_FALL);
        data(sp).setPowerToggledOff(SLOW_FALL, true); // what its onGranted seeds

        assertEquals(List.of(ResourceLocation.parse(SLOW_FALL)), KeylessToggleRelease.release(sp));
        assertFalse(data(sp).isPowerToggledOff(SLOW_FALL));
    }

    /** Before 2.2.28 Stealth held the Rogue's class key, so switching it off was a choice. */
    @Test
    void aToggleOnTheClassSkillKeyKeepsThePlayersChoice() {
        Map<ResourceLocation, Origin> shipped = OriginDataManager.INSTANCE.getOrigins();
        Origin rogue = shipped.get(ROGUE);
        List<ResourceLocation> stealthFirst = new ArrayList<>(rogue.powers());
        stealthFirst.remove(ResourceLocation.parse(STEALTH));
        stealthFirst.add(0, ResourceLocation.parse(STEALTH));
        ResourceLocation oldRogue = id("test_rogue_pre_2_2_28");
        Map<ResourceLocation, Origin> withOld = new HashMap<>(shipped);
        withOld.put(oldRogue, new Origin(oldRogue, stealthFirst, rogue.icon(), rogue.impact(), rogue.order(),
            rogue.unchoosable(), rogue.special(), rogue.name(), rogue.description(), rogue.upgrades(),
            rogue.spawnLocation(), rogue.tierPowers(), rogue.figuraModel(), rogue.figuraModels()));
        OriginDataManager.INSTANCE.setClientData(withOld);
        try {
            ServerPlayer sp = player(null, oldRogue);
            assertEquals(STEALTH, ActiveOriginService.activeClassPowers(sp).get(0).id().toString());
            data(sp).setPowerToggledOff(STEALTH, true);

            assertEquals(List.of(), KeylessToggleRelease.release(sp));
            assertTrue(data(sp).isPowerToggledOff(STEALTH));
        } finally {
            OriginDataManager.INSTANCE.setClientData(shipped);
        }
    }

    /** Explorer and Scout carry the same Step Assist pair; only the Rogue has a toggle behind it. */
    @Test
    void theRoguesStealthIsTheOnlyKeylessToggleInAnyShippedClass() {
        List<String> released = new ArrayList<>();
        for (ResourceLocation cls : OriginDataManager.INSTANCE.getOrigins().keySet()) {
            if (!cls.getNamespace().equals("neoorigins") || !cls.getPath().startsWith("class_")) continue;
            ServerPlayer sp = player(null, cls);
            for (PowerHolder<?> h : ActiveOriginService.allPowers(sp)) {
                data(sp).setPowerToggledOff(h.id().toString(), true);
            }
            KeylessToggleRelease.release(sp).forEach(r -> released.add(cls.getPath() + " -> " + r));
        }
        assertEquals(List.of("class_rogue -> " + STEALTH), released);
    }

    @Test
    void suppressionKeepsTheFlagUntilALoginWithoutIt() {
        ServerPlayer sp = Mockito.spy(player(null, ROGUE));
        Mockito.doReturn(true).when(sp).hasEffect(ModEffects.SUPPRESSION);
        data(sp).setPowerToggledOff(STEALTH, true);

        assertEquals(List.of(), KeylessToggleRelease.release(sp));
        assertTrue(data(sp).isPowerToggledOff(STEALTH));
    }

    /** Suppression forces toggles off and refuses them while held; a relog must not switch one back on. */
    @Test
    void aRelogWhileSuppressedLeavesTheForcedOffInPlace() {
        ServerPlayer sp = Mockito.spy(player(null, ROGUE));
        Mockito.doReturn(true).when(sp).hasEffect(ModEffects.SUPPRESSION);
        assertFalse(data(sp).isPowerToggledOff(STEALTH, STEALTH_LEGACY));
        new SuppressionEffect().onEffectStarted(sp, 0);
        assertTrue(data(sp).isPowerToggledOff(STEALTH, STEALTH_LEGACY), "Suppression must force Stealth off");

        assertEquals(List.of(), KeylessToggleRelease.release(sp));
        assertTrue(data(sp).isPowerToggledOff(STEALTH, STEALTH_LEGACY));
    }

    private static ServerPlayer player(ResourceLocation origin, ResourceLocation cls) {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        if (origin != null) {
            data(sp).setOrigin(ORIGIN_LAYER, origin);
            ActiveOriginService.applyOriginPowers(sp, ORIGIN_LAYER, null, origin);
        }
        if (cls != null) {
            data(sp).setOrigin(PowerLayers.CLASS_LAYER, cls);
            ActiveOriginService.applyOriginPowers(sp, PowerLayers.CLASS_LAYER, null, cls);
        }
        return sp;
    }

    /** Slot-occupying powers that are not toggles, to push a toggle past the sixth slot. */
    private static List<PowerHolder<?>> slotFillers() {
        return PowerDataManager.INSTANCE.getPowers().values().stream()
            .filter(h -> h.occupiesHotkeySlot() && !(h.type() instanceof OffFlagToggle<?>))
            .sorted(java.util.Comparator.comparing(h -> h.id().toString()))
            .toList();
    }

    /** Dynamic grants are a hash set, so add fillers until the power lands past the sixth slot. */
    private static void grantPastTheSixthSlot(ServerPlayer sp, String powerId) {
        data(sp).addDynamicGrant(ResourceLocation.parse(powerId));
        for (PowerHolder<?> filler : slotFillers()) {
            if (slotOf(sp, powerId) >= 6) break;
            data(sp).addDynamicGrant(filler.id());
        }
        assertTrue(slotOf(sp, powerId) >= 6, powerId + " must sit past the sixth skill slot");
    }

    private static int slotOf(ServerPlayer sp, String powerId) {
        List<PowerHolder<?>> actives = ActiveOriginService.activePowers(sp);
        for (int i = 0; i < actives.size(); i++) if (actives.get(i).id().toString().equals(powerId)) return i;
        return -1;
    }

    /**
     * The real login handler, on a player whose packets go nowhere. The registry sync
     * after the powers are applied reads a content config the rig never loads.
     */
    private static void login(ServerPlayer sp) {
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        try {
            PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(sp));
        } catch (IllegalStateException e) {
            if (!String.valueOf(e.getMessage()).contains("before config is loaded")) throw e;
        }
    }

    /** A fresh player carrying a round-tripped copy of the attachment, as after a relog. */
    private static ServerPlayer reload(ServerPlayer before) {
        Tag saved = PlayerOriginData.CODEC.encodeStart(NbtOps.INSTANCE, data(before)).getOrThrow();
        ServerPlayer after = PlayerLifecycle.realPlayer();
        after.setData(OriginAttachments.originData(), PlayerOriginData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow());
        return after;
    }

    private static PlayerOriginData data(ServerPlayer sp) {
        return sp.getData(OriginAttachments.originData());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("neoorigins", path);
    }
}
