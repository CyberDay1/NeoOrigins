package com.mojang.realmsclient.util;

import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record WorldGenerationInfo(String seed, LevelType levelType, boolean generateStructures, Set<String> experiments) {
}
