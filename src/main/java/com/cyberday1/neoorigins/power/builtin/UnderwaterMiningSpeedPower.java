package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * "Aqua affinity" — removes the underwater (submerged) mining-speed penalty.
 * In 1.21+ that penalty lives in the {@code minecraft:submerged_mining_speed}
 * attribute (base 0.2 = the 5× slowdown), which vanilla only applies while the
 * player's eye is in water — so a permanent modifier raising it to 1.0 is
 * automatically positional and needs no event handling.
 */
public class UnderwaterMiningSpeedPower extends PowerType<UnderwaterMiningSpeedPower.Config> {

    /**
     * Capability tag published so the client can answer "does this player mine at
     * full speed underwater?" without the server power configs.
     * {@link com.cyberday1.neoorigins.event.SubmergedMiningEvents} needs the answer
     * on both sides: block-break progress is predicted client-side, so a server-only
     * check would leave the local player watching the un-corrected animation.
     */
    public static final String CAPABILITY = "underwater_mining_speed";

    /** Per-power modifier id so multiple underwater_mining_speed powers stack additively. */
    private static Identifier modId() {
        Identifier powerId = PowerHolder.currentDispatchId();
        String key = powerId != null
            ? (powerId.getNamespace() + "_" + powerId.getPath()).replace('/', '_')
            : "anon";
        return Identifier.fromNamespaceAndPath("neoorigins", "underwater_mining_" + key + "_cancel_penalty");
    }

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public java.util.Set<String> capabilities(Config config) {
        return java.util.Set.of(CAPABILITY);
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        AttributeInstance instance = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        if (instance == null) return;
        Identifier modId = modId();
        if (instance.getModifier(modId) != null) return;
        instance.addPermanentModifier(new AttributeModifier(
            modId, 4.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        AttributeInstance instance = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        if (instance == null) return;
        instance.removeModifier(modId());
    }
}
