package com.cyberday1.neoorigins.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Where a {@code personal_dimension} power sends its holder back to: the exact
 * dimension, position and facing they left from. An empty {@code dimension}
 * means no return point is saved.
 */
public record PersonalSpaceReturn(String dimension, double x, double y, double z, float yRot, float xRot) {

    public static final PersonalSpaceReturn NONE = new PersonalSpaceReturn("", 0, 0, 0, 0, 0);

    public static final MapCodec<PersonalSpaceReturn> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        Codec.STRING.optionalFieldOf("dimension", "").forGetter(PersonalSpaceReturn::dimension),
        Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(PersonalSpaceReturn::x),
        Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(PersonalSpaceReturn::y),
        Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(PersonalSpaceReturn::z),
        Codec.FLOAT.optionalFieldOf("y_rot", 0.0f).forGetter(PersonalSpaceReturn::yRot),
        Codec.FLOAT.optionalFieldOf("x_rot", 0.0f).forGetter(PersonalSpaceReturn::xRot)
    ).apply(inst, PersonalSpaceReturn::new));

    public boolean isPresent() {
        return !dimension.isEmpty();
    }
}
