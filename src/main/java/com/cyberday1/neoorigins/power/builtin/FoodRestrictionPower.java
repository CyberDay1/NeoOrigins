package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Restricts what the player can eat based on an item tag.
 * mode=whitelist: player can ONLY eat items matching the tag.
 * mode=blacklist: player CANNOT eat items matching the tag.
 * Event handling via LivingEntityUseItemEvent in OriginEventHandler.
 */
public class FoodRestrictionPower extends PowerType<FoodRestrictionPower.Config> {

    public record Config(String mode, Identifier itemTag, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("mode", "blacklist").forGetter(Config::mode),
            Identifier.CODEC.fieldOf("item_tag").forGetter(Config::itemTag),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));

        public boolean isWhitelist() { return "whitelist".equalsIgnoreCase(mode); }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
