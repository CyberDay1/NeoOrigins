package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;

/**
 * Passive power that keeps multiple mob effects continuously active on the player,
 * each with its own amplifier / ambient / show_particles flags.
 *
 * <p>Replaces the previous lossy translation of {@code origins:stacking_status_effect},
 * which kept only the first effect from the source array. This power runs every
 * server tick and re-applies each configured effect at 300-tick duration
 * (matching the existing {@code neoorigins:status_effect} cadence) whenever the
 * player lacks it at the configured amplifier.
 *
 * <p>Unlike {@link StatusEffectPower}, this is NOT a toggle power — it doesn't
 * consume a keybind slot, because packs using stacking_status_effect typically
 * expect a purely passive buff array.
 */
public class StackingStatusEffectsPower extends PowerType<StackingStatusEffectsPower.Config> {

    public record Entry(Identifier effect, int amplifier, boolean ambient, boolean showParticles) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("effect").forGetter(Entry::effect),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Entry::amplifier),
            Codec.BOOL.optionalFieldOf("ambient", true).forGetter(Entry::ambient),
            Codec.BOOL.optionalFieldOf("show_particles", false).forGetter(Entry::showParticles)
        ).apply(inst, Entry::new));
    }

    public record Config(List<Entry> effects, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Entry.CODEC.listOf().fieldOf("effects").forGetter(Config::effects),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        for (Entry entry : config.effects()) {
            var holderOpt = BuiltInRegistries.MOB_EFFECT.get(entry.effect());
            if (holderOpt.isEmpty()) continue;
            var holder = holderOpt.get();
            var existing = player.getEffect(holder);
            if (existing == null || existing.getAmplifier() < entry.amplifier()) {
                player.addEffect(new MobEffectInstance(
                    holder, 300, entry.amplifier(), entry.ambient(), entry.showParticles()));
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        for (Entry entry : config.effects()) {
            var holderOpt = BuiltInRegistries.MOB_EFFECT.get(entry.effect());
            holderOpt.ifPresent(player::removeEffect);
        }
    }
}
