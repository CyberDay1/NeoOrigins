package net.minecraft.world.entity.projectile.windcharge;

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;

public class WindCharge extends AbstractWindCharge {
    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(
        true, false, Optional.of(1.22F), BuiltInRegistries.BLOCK.getTag(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())
    );
    private static final float RADIUS = 1.2F;
    private int noDeflectTicks = 5;

    public WindCharge(EntityType<? extends AbstractWindCharge> p_326226_, Level p_326464_) {
        super(p_326226_, p_326464_);
    }

    public WindCharge(Player p_326044_, Level p_326101_, double p_326183_, double p_326157_, double p_325928_) {
        super(EntityType.WIND_CHARGE, p_326101_, p_326044_, p_326183_, p_326157_, p_325928_);
    }

    public WindCharge(Level p_326007_, double p_326331_, double p_326001_, double p_325990_, Vec3 p_347497_) {
        super(EntityType.WIND_CHARGE, p_326331_, p_326001_, p_325990_, p_347497_, p_326007_);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.noDeflectTicks > 0) {
            this.noDeflectTicks--;
        }
    }

    @Override
    public boolean deflect(ProjectileDeflection p_350880_, @Nullable Entity p_350728_, @Nullable Entity p_350716_, boolean p_350553_) {
        return this.noDeflectTicks > 0 ? false : super.deflect(p_350880_, p_350728_, p_350716_, p_350553_);
    }

    @Override
    protected void explode(Vec3 p_352393_) {
        this.level()
            .explode(
                this,
                null,
                EXPLOSION_DAMAGE_CALCULATOR,
                p_352393_.x(),
                p_352393_.y(),
                p_352393_.z(),
                1.2F,
                false,
                Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE,
                SoundEvents.WIND_CHARGE_BURST
            );
    }
}
