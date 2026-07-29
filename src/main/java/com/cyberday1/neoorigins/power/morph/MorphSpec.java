package com.cyberday1.neoorigins.power.morph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * A fully resolved description of what a morphed player should look like —
 * the single object shared by the {@code neoorigins:entity_model} power, the
 * datapack morph registry ({@code MorphDataManager}) and the client sync
 * payload.
 *
 * <p>The power's own {@code Config} is the <em>authoring</em> shape: every
 * field there is {@link Optional} so "unset" can be told apart from "set to
 * the default", which is what lets an inline field override a named morph
 * definition. This record is the <em>resolved</em> shape: concrete values
 * only, produced once on the server and broadcast to every viewer.
 *
 * @param entityType     entity whose model replaces the player's; empty means
 *                       "no model swap" (the morph then only carries the
 *                       non-model tweaks below)
 * @param nbt            partial NBT applied to the render dummy once at
 *                       creation, to pick a variant (sheep colour, cat type,
 *                       villager profession, slime size, …)
 * @param scale          uniform scale applied to the morph model, and to the
 *                       collision box along with it when {@code hitbox} is set
 * @param hitbox         whether the player collides at the morph target's size
 *                       rather than their own
 * @param renderHeldItem whether the player's held items are drawn on the morph
 * @param renderArmor    whether the player's worn armour is drawn on the morph
 * @param firstPerson    what the morphed player sees in first person —
 *                       {@link #FIRST_PERSON_ITEM}, {@link #FIRST_PERSON_ARM}
 *                       or {@link #FIRST_PERSON_HIDDEN}
 * @param arm            name of the model bone to treat as the morph's arm;
 *                       empty means "work it out from the model"
 * @param skin           textures layered over the player's own skin; independent
 *                       of {@code entityType}, since it restyles the player's
 *                       real model rather than replacing it
 * @param entitySounds   whether the player borrows the voice of {@code entityType}
 * @param sounds         explicit sound overrides, layered over that voice
 */
public record MorphSpec(
    Optional<Identifier> entityType,
    Optional<CompoundTag> nbt,
    float scale,
    boolean hitbox,
    boolean renderHeldItem,
    boolean renderArmor,
    String firstPerson,
    Optional<String> arm,
    Optional<MorphSkin> skin,
    boolean entitySounds,
    Optional<MorphSounds> sounds
) {

    /** First-person mode: draw the held item, without any arm. */
    public static final String FIRST_PERSON_ITEM = "item";
    /** First-person mode: draw the morph's own arm, holding the item. */
    public static final String FIRST_PERSON_ARM = "arm";
    /** First-person mode: draw nothing at all. */
    public static final String FIRST_PERSON_HIDDEN = "hidden";

    /** Neutral base a morph definition or inline config is resolved against. */
    public static final MorphSpec EMPTY =
        new MorphSpec(Optional.empty(), Optional.empty(), 1.0f, true, true, true, FIRST_PERSON_ITEM,
            Optional.empty(), Optional.empty(), true, Optional.empty());

    public static final Codec<MorphSpec> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.optionalFieldOf("entity_type").forGetter(MorphSpec::entityType),
        CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(MorphSpec::nbt),
        Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(MorphSpec::scale),
        Codec.BOOL.optionalFieldOf("hitbox", true).forGetter(MorphSpec::hitbox),
        Codec.BOOL.optionalFieldOf("render_held_item", true).forGetter(MorphSpec::renderHeldItem),
        Codec.BOOL.optionalFieldOf("render_armor", true).forGetter(MorphSpec::renderArmor),
        Codec.STRING.optionalFieldOf("first_person", FIRST_PERSON_ITEM).forGetter(MorphSpec::firstPerson),
        Codec.STRING.optionalFieldOf("arm").forGetter(MorphSpec::arm),
        MorphSkin.CODEC.optionalFieldOf("skin").forGetter(MorphSpec::skin),
        Codec.BOOL.optionalFieldOf("entity_sounds", true).forGetter(MorphSpec::entitySounds),
        MorphSounds.CODEC.optionalFieldOf("sounds").forGetter(MorphSpec::sounds)
    ).apply(inst, MorphSpec::new));

    /** True when this morph actually swaps the player's model for an entity. */
    public boolean hasModel() {
        return entityType.isPresent();
    }

    /** The skin override to layer onto the player, if this morph carries one. */
    public Optional<MorphSkin> activeSkin() {
        return skin.filter(s -> !s.isEmpty());
    }

    /** True when the morphed player's own view should show nothing in hand. */
    public boolean hidesFirstPerson() {
        return FIRST_PERSON_HIDDEN.equals(firstPerson);
    }

    /**
     * True when the morphed player should see the morph's own arm rather than a
     * bare floating item. Only a request: a morph whose model has no arm bone to
     * draw falls back to the item, since showing nothing would be worse.
     */
    public boolean wantsFirstPersonArm() {
        return FIRST_PERSON_ARM.equals(firstPerson);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeOptional(entityType, FriendlyByteBuf::writeIdentifier);
        buf.writeNbt(nbt.orElse(null));
        buf.writeFloat(scale);
        buf.writeBoolean(hitbox);
        buf.writeBoolean(renderHeldItem);
        buf.writeBoolean(renderArmor);
        buf.writeUtf(firstPerson);
        buf.writeOptional(arm, FriendlyByteBuf::writeUtf);
        buf.writeOptional(skin, (b, s) -> s.write(b));
        buf.writeBoolean(entitySounds);
        buf.writeOptional(sounds, (b, s) -> s.write(b));
    }

    public static MorphSpec read(FriendlyByteBuf buf) {
        Optional<Identifier> entityType = buf.readOptional(FriendlyByteBuf::readIdentifier);
        Optional<CompoundTag> nbt = Optional.ofNullable(buf.readNbt());
        float scale = buf.readFloat();
        boolean hitbox = buf.readBoolean();
        boolean renderHeldItem = buf.readBoolean();
        boolean renderArmor = buf.readBoolean();
        String firstPerson = buf.readUtf();
        Optional<String> arm = buf.readOptional(FriendlyByteBuf::readUtf);
        Optional<MorphSkin> skin = buf.readOptional(MorphSkin::read);
        boolean entitySounds = buf.readBoolean();
        Optional<MorphSounds> sounds = buf.readOptional(MorphSounds::read);
        return new MorphSpec(entityType, nbt, scale, hitbox, renderHeldItem, renderArmor, firstPerson,
            arm, skin, entitySounds, sounds);
    }
}
