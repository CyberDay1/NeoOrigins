package com.cyberday1.neoorigins.compat.top;

import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.rig.HeadlessWorld;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Drives the TOP provider against a real entity, compiled and run against TOP's own
 * api jar. The probe side is mocked; the entity and its mob-origin attachment are not.
 */
// hub: neoorigins/upstream-compat-blocks.md
class TopProviderTest {

    private final HeadlessWorld world = new HeadlessWorld();

    @Test
    void anEntityWithAMobOriginShowsItInTheProbe() {
        var entity = world.spawnAt(0, 64, 0);
        entity.getData(EntityAttachments.mobOriginData())
            .setOriginId(Identifier.fromNamespaceAndPath("neoorigins", "blazeborn"));
        IProbeInfo probe = Mockito.mock(IProbeInfo.class);

        new NeoOriginsTopProvider().addProbeEntityInfo(
            ProbeMode.NORMAL, probe, entity, world.level(), entity, null);

        ArgumentCaptor<Component> line = ArgumentCaptor.forClass(Component.class);
        Mockito.verify(probe).text(line.capture());
        var contents = assertInstanceOf(TranslatableContents.class, line.getValue().getContents());
        assertEquals("tooltip.neoorigins.origin", contents.getKey());
        assertArrayEquals(new Object[] {"neoorigins:blazeborn"}, contents.getArgs());
    }

    @Test
    void anEntityWithNoMobOriginAddsNothing() {
        var entity = world.spawnAt(0, 64, 0);
        IProbeInfo probe = Mockito.mock(IProbeInfo.class);

        new NeoOriginsTopProvider().addProbeEntityInfo(
            ProbeMode.NORMAL, probe, entity, world.level(), entity, null);

        Mockito.verifyNoInteractions(probe);
    }

    @Test
    void theImcFunctionRegistersTheProvider() {
        ITheOneProbe top = Mockito.mock(ITheOneProbe.class);

        TopIntegration.registrar().apply(top);

        Mockito.verify(top).registerEntityProvider(Mockito.any(NeoOriginsTopProvider.class));
    }
}
