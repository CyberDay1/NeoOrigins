package net.minecraft.world.item;

import java.util.OptionalInt;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

public interface ProjectileItem {
    Projectile asProjectile(Level p_338867_, Position p_338379_, ItemStack p_338543_, Direction p_338380_);

    default ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.DEFAULT;
    }

    default void shoot(Projectile p_338559_, double p_338418_, double p_338827_, double p_338653_, float p_338287_, float p_338314_) {
        p_338559_.shoot(p_338418_, p_338827_, p_338653_, p_338287_, p_338314_);
    }

    public static record DispenseConfig(ProjectileItem.PositionFunction positionFunction, float uncertainty, float power, OptionalInt overrideDispenseEvent) {
        public static final ProjectileItem.DispenseConfig DEFAULT = builder().build();

        public static ProjectileItem.DispenseConfig.Builder builder() {
            return new ProjectileItem.DispenseConfig.Builder();
        }

        public static class Builder {
            private ProjectileItem.PositionFunction positionFunction = (p_338429_, p_338348_) -> DispenserBlock.getDispensePosition(
                    p_338429_, 0.7, new Vec3(0.0, 0.1, 0.0)
                );
            private float uncertainty = 6.0F;
            private float power = 1.1F;
            private OptionalInt overrideDispenseEvent = OptionalInt.empty();

            public ProjectileItem.DispenseConfig.Builder positionFunction(ProjectileItem.PositionFunction p_338644_) {
                this.positionFunction = p_338644_;
                return this;
            }

            public ProjectileItem.DispenseConfig.Builder uncertainty(float p_338449_) {
                this.uncertainty = p_338449_;
                return this;
            }

            public ProjectileItem.DispenseConfig.Builder power(float p_338328_) {
                this.power = p_338328_;
                return this;
            }

            public ProjectileItem.DispenseConfig.Builder overrideDispenseEvent(int p_338272_) {
                this.overrideDispenseEvent = OptionalInt.of(p_338272_);
                return this;
            }

            public ProjectileItem.DispenseConfig build() {
                return new ProjectileItem.DispenseConfig(this.positionFunction, this.uncertainty, this.power, this.overrideDispenseEvent);
            }
        }
    }

    @FunctionalInterface
    public interface PositionFunction {
        Position getDispensePosition(BlockSource p_338784_, Direction p_338422_);
    }
}
