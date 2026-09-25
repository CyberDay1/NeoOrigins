package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The mod jar is one pack serving both sides, so its {@code pack.mcmeta} must cover this version's
 * resource and data formats. Read through the loader NeoForge uses for mod packs.
 */
class ModPackMcmetaCompatibilityTest {

    @ParameterizedTest
    @EnumSource(PackType.class)
    void theModPackIsCompatibleOnBothSides(PackType type) throws Exception {
        SharedConstants.tryDetectVersion();
        Pack pack = ResourcePackLoader.readWithOptionalMeta(
            new PackLocationInfo("mod/neoorigins", Component.literal("neoorigins"), PackSource.DEFAULT, Optional.empty()),
            new PathPackResources.PathResourcesSupplier(RealDatapack.projectPath("src/main/resources")),
            type,
            new PackSelectionConfig(false, Pack.Position.TOP, false));
        assertEquals(PackCompatibility.COMPATIBLE, pack.getCompatibility(),
            type + " is format " + SharedConstants.getCurrentVersion().getPackVersion(type) + " on this version");
    }
}
