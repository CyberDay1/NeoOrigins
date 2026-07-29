package com.cyberday1.neoorigins.power.morph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Explicit sound overrides for a morph — the {@code sounds} block of the
 * {@code neoorigins:entity_model} power.
 *
 * <p>These sit <em>on top of</em> whatever the morph's {@code entity_type}
 * already supplies, exactly the way {@link MorphSkin} layers over a player's
 * real skin. A morph that names an entity type gets that mob's voice for free;
 * this block is for the cases the mob can't cover — a silent morph, a custom
 * pack sound, or a {@code skin}-only morph that has no mob to borrow from.
 *
 * <p>Every value is a sound-event id ({@code minecraft:entity.fox.hurt}), not a
 * sound file. Ids that aren't registered are reported once and then ignored,
 * because a typo in a cosmetic field must not silence a player permanently.
 *
 * <p>Step sounds are deliberately absent: those come from the block being walked
 * on, not from the entity, so there is nothing here to override. Ambient sounds
 * are absent too — {@code Player} has no ambient-sound path at all, so a morph
 * cannot idle-croak without a ticker of its own.
 *
 * @param hurt            played when the player takes damage
 * @param death           played once when the player dies
 * @param fallSmall       short fall (four blocks or less)
 * @param fallBig         long fall
 * @param swim            looping sound while swimming
 * @param splash          entering or leaving water
 * @param splashHighSpeed the faster splash used when hitting water hard
 */
public record MorphSounds(
    Optional<Identifier> hurt,
    Optional<Identifier> death,
    Optional<Identifier> fallSmall,
    Optional<Identifier> fallBig,
    Optional<Identifier> swim,
    Optional<Identifier> splash,
    Optional<Identifier> splashHighSpeed
) {

    public static final Codec<MorphSounds> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.optionalFieldOf("hurt").forGetter(MorphSounds::hurt),
        Identifier.CODEC.optionalFieldOf("death").forGetter(MorphSounds::death),
        Identifier.CODEC.optionalFieldOf("fall_small").forGetter(MorphSounds::fallSmall),
        Identifier.CODEC.optionalFieldOf("fall_big").forGetter(MorphSounds::fallBig),
        Identifier.CODEC.optionalFieldOf("swim").forGetter(MorphSounds::swim),
        Identifier.CODEC.optionalFieldOf("splash").forGetter(MorphSounds::splash),
        Identifier.CODEC.optionalFieldOf("splash_high_speed").forGetter(MorphSounds::splashHighSpeed)
    ).apply(inst, MorphSounds::new));

    public void write(FriendlyByteBuf buf) {
        buf.writeOptional(hurt, FriendlyByteBuf::writeIdentifier);
        buf.writeOptional(death, FriendlyByteBuf::writeIdentifier);
        buf.writeOptional(fallSmall, FriendlyByteBuf::writeIdentifier);
        buf.writeOptional(fallBig, FriendlyByteBuf::writeIdentifier);
        buf.writeOptional(swim, FriendlyByteBuf::writeIdentifier);
        buf.writeOptional(splash, FriendlyByteBuf::writeIdentifier);
        buf.writeOptional(splashHighSpeed, FriendlyByteBuf::writeIdentifier);
    }

    public static MorphSounds read(FriendlyByteBuf buf) {
        return new MorphSounds(
            buf.readOptional(FriendlyByteBuf::readIdentifier),
            buf.readOptional(FriendlyByteBuf::readIdentifier),
            buf.readOptional(FriendlyByteBuf::readIdentifier),
            buf.readOptional(FriendlyByteBuf::readIdentifier),
            buf.readOptional(FriendlyByteBuf::readIdentifier),
            buf.readOptional(FriendlyByteBuf::readIdentifier),
            buf.readOptional(FriendlyByteBuf::readIdentifier));
    }
}
