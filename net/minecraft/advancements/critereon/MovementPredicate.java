package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.Mth;

public record MovementPredicate(
    MinMaxBounds.Doubles x,
    MinMaxBounds.Doubles y,
    MinMaxBounds.Doubles z,
    MinMaxBounds.Doubles speed,
    MinMaxBounds.Doubles horizontalSpeed,
    MinMaxBounds.Doubles verticalSpeed,
    MinMaxBounds.Doubles fallDistance
) {
    public static final Codec<MovementPredicate> CODEC = RecordCodecBuilder.create(
        p_345089_ -> p_345089_.group(
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("x", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::x),
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("y", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::y),
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("z", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::z),
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("speed", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::speed),
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("horizontal_speed", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::horizontalSpeed),
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("vertical_speed", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::verticalSpeed),
                    MinMaxBounds.Doubles.CODEC.optionalFieldOf("fall_distance", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::fallDistance)
                )
                .apply(p_345089_, MovementPredicate::new)
    );

    public static MovementPredicate speed(MinMaxBounds.Doubles p_345901_) {
        return new MovementPredicate(
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            p_345901_,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY
        );
    }

    public static MovementPredicate horizontalSpeed(MinMaxBounds.Doubles p_345197_) {
        return new MovementPredicate(
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            p_345197_,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY
        );
    }

    public static MovementPredicate verticalSpeed(MinMaxBounds.Doubles p_345809_) {
        return new MovementPredicate(
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            p_345809_,
            MinMaxBounds.Doubles.ANY
        );
    }

    public static MovementPredicate fallDistance(MinMaxBounds.Doubles p_344924_) {
        return new MovementPredicate(
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            p_344924_
        );
    }

    public boolean matches(double p_346097_, double p_344727_, double p_346309_, double p_346374_) {
        if (this.x.matches(p_346097_) && this.y.matches(p_344727_) && this.z.matches(p_346309_)) {
            double d0 = Mth.lengthSquared(p_346097_, p_344727_, p_346309_);
            if (!this.speed.matchesSqr(d0)) {
                return false;
            } else {
                double d1 = Mth.lengthSquared(p_346097_, p_346309_);
                if (!this.horizontalSpeed.matchesSqr(d1)) {
                    return false;
                } else {
                    double d2 = Math.abs(p_344727_);
                    return !this.verticalSpeed.matches(d2) ? false : this.fallDistance.matches(p_346374_);
                }
            }
        } else {
            return false;
        }
    }
}
