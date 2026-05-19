package com.cyberday1.neoorigins.api.mob_origin;

import com.cyberday1.neoorigins.api.origin.ComponentCodecHelper;
import com.cyberday1.neoorigins.api.origin.IconCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A bundle of powers (and, from later phases, spawn rules + drops) that can be
 * attached to a non-player {@link net.minecraft.world.entity.LivingEntity}.
 * The mob-side analogue of {@link com.cyberday1.neoorigins.api.origin.Origin},
 * authored at {@code data/<ns>/origins/mob_origins/<name>.json}.
 *
 * <p>{@code id} is parsed from the field but injected by
 * {@link com.cyberday1.neoorigins.data.MobOriginDataManager} from the file
 * path (mirrors {@code OriginDataManager}), so authored JSON omits it.
 *
 * <p>{@code spawnRules}/{@code dropRules} are present in the codec from Phase 1
 * (shape stability) but only evaluated in Phases 2 / 5.
 */
public record MobOrigin(
    ResourceLocation id,
    Component name,
    Component description,
    ItemStack icon,
    EntityTargetSpec target,
    List<ResourceLocation> powers,
    SpawnRules spawnRules,
    DropRules dropRules,
    boolean hidden
) {
    public static final Codec<MobOrigin> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.fieldOf("id").forGetter(MobOrigin::id),
        ComponentCodecHelper.CODEC.fieldOf("name").forGetter(MobOrigin::name),
        ComponentCodecHelper.CODEC.optionalFieldOf("description", Component.empty())
            .forGetter(MobOrigin::description),
        IconCodec.CODEC.optionalFieldOf("icon", ItemStack.EMPTY).forGetter(MobOrigin::icon),
        EntityTargetSpec.CODEC.fieldOf("target").forGetter(MobOrigin::target),
        ResourceLocation.CODEC.listOf().optionalFieldOf("powers", List.of())
            .forGetter(MobOrigin::powers),
        SpawnRules.CODEC.optionalFieldOf("spawn_rules", SpawnRules.NEVER)
            .forGetter(MobOrigin::spawnRules),
        DropRules.CODEC.optionalFieldOf("drops", DropRules.NONE).forGetter(MobOrigin::dropRules),
        Codec.BOOL.optionalFieldOf("hidden", false).forGetter(MobOrigin::hidden)
    ).apply(inst, MobOrigin::new));
}
