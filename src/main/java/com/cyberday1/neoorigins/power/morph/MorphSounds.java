package com.cyberday1.neoorigins.power.morph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

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
    Optional<ResourceLocation> hurt,
    Optional<ResourceLocation> death,
    Optional<ResourceLocation> fallSmall,
    Optional<ResourceLocation> fallBig,
    Optional<ResourceLocation> swim,
    Optional<ResourceLocation> splash,
    Optional<ResourceLocation> splashHighSpeed
) {

    public static final Codec<MorphSounds> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.optionalFieldOf("hurt").forGetter(MorphSounds::hurt),
        ResourceLocation.CODEC.optionalFieldOf("death").forGetter(MorphSounds::death),
        ResourceLocation.CODEC.optionalFieldOf("fall_small").forGetter(MorphSounds::fallSmall),
        ResourceLocation.CODEC.optionalFieldOf("fall_big").forGetter(MorphSounds::fallBig),
        ResourceLocation.CODEC.optionalFieldOf("swim").forGetter(MorphSounds::swim),
        ResourceLocation.CODEC.optionalFieldOf("splash").forGetter(MorphSounds::splash),
        ResourceLocation.CODEC.optionalFieldOf("splash_high_speed").forGetter(MorphSounds::splashHighSpeed)
    ).apply(inst, MorphSounds::new));

    public void write(FriendlyByteBuf buf) {
        buf.writeOptional(hurt, FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(death, FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(fallSmall, FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(fallBig, FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(swim, FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(splash, FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(splashHighSpeed, FriendlyByteBuf::writeResourceLocation);
    }

    public static MorphSounds read(FriendlyByteBuf buf) {
        return new MorphSounds(
            buf.readOptional(FriendlyByteBuf::readResourceLocation),
            buf.readOptional(FriendlyByteBuf::readResourceLocation),
            buf.readOptional(FriendlyByteBuf::readResourceLocation),
            buf.readOptional(FriendlyByteBuf::readResourceLocation),
            buf.readOptional(FriendlyByteBuf::readResourceLocation),
            buf.readOptional(FriendlyByteBuf::readResourceLocation),
            buf.readOptional(FriendlyByteBuf::readResourceLocation));
    }
}
