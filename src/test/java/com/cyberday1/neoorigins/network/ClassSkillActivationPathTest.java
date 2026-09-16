package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.api.PowerLayers;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Which power the Class Skill key actually fires, driven through the production
 * selection path rather than by re-reading the JSON.
 *
 * <p>{@code ClassSkillSlotBindingTest} states the ordering rule over the raw files.
 * This one loads the shipped origins and powers into the real
 * {@code OriginDataManager} / {@code PowerDataManager} and asks
 * {@link NeoOriginsNetwork#classPowerForKeypress} — the exact call
 * {@code handleActivateClassPower} makes — which holder answers. If the JSON order,
 * the {@code occupiesHotkeySlot} filter, the holder build or the {@code get(0)} were
 * wrong in any combination, this is where it shows.
 */
// hub: neoorigins/class-skill-slot.md
class ClassSkillActivationPathTest {

    private static final String NS = "neoorigins";

    @BeforeAll
    static void loadRealData() {
        RealDatapack.load();
    }

    private static ServerPlayer playerWithClass(String classOrigin) {
        PlayerOriginData data = new PlayerOriginData();
        data.setOrigin(PowerLayers.CLASS_LAYER, ResourceLocation.fromNamespaceAndPath(NS, classOrigin));

        ServerLevel level = Mockito.mock(ServerLevel.class);
        Mockito.when(level.dimension()).thenReturn(Level.OVERWORLD);

        ServerPlayer player = Mockito.mock(ServerPlayer.class);
        Mockito.when(player.level()).thenReturn(level);
        Mockito.when(player.getUUID()).thenReturn(UUID.randomUUID());
        Mockito.when(player.getData(OriginAttachments.originData())).thenReturn(data);
        return player;
    }

    private static ResourceLocation boundPowerOf(String classOrigin) {
        PowerHolder<?> holder = NeoOriginsNetwork.classPowerForKeypress(playerWithClass(classOrigin));
        assertNotNull(holder, classOrigin + " resolved no class-skill power at all");
        return holder.id();
    }

    @Test
    void theRoguesClassSkillKeyFiresStepAssist() {
        // Issue #134's other half: Stealth used to be listed first and took the one
        // slot, leaving Step Assist with no way to be activated.
        assertEquals(ResourceLocation.fromNamespaceAndPath(NS, "step_assist_switch"),
            boundPowerOf("class_rogue"));
    }

    @Test
    void theOtherTwoStepAssistClassesFireStepAssistToo() {
        assertEquals(ResourceLocation.fromNamespaceAndPath(NS, "step_assist_switch"),
            boundPowerOf("class_explorer"));
        assertEquals(ResourceLocation.fromNamespaceAndPath(NS, "step_assist_switch"),
            boundPowerOf("class_scout"));
    }

    @Test
    void theHiddenToggleStateHolderNeverTakesTheSlot() {
        // step_assist_toggle is listed AHEAD of the switch in all three classes and
        // is only harmless because TogglePower is not an active power. If that ever
        // changed, the state holder would eat the key in three classes at once.
        ResourceLocation toggle = ResourceLocation.fromNamespaceAndPath(NS, "step_assist_toggle");
        for (String cls : new String[]{"class_rogue", "class_explorer", "class_scout"}) {
            assertNotEquals(toggle, boundPowerOf(cls),
                cls + " bound the hidden toggle state holder to the Class Skill key");
        }
    }
}
