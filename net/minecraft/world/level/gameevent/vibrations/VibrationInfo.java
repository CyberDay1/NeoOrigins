package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public record VibrationInfo(
    Holder<GameEvent> gameEvent, float distance, Vec3 pos, @Nullable UUID uuid, @Nullable UUID projectileOwnerUuid, @Nullable Entity entity
) {
    public static final Codec<VibrationInfo> CODEC = RecordCodecBuilder.create(
        p_348454_ -> p_348454_.group(
                    GameEvent.CODEC.fieldOf("game_event").forGetter(VibrationInfo::gameEvent),
                    Codec.floatRange(0.0F, Float.MAX_VALUE).fieldOf("distance").forGetter(VibrationInfo::distance),
                    Vec3.CODEC.fieldOf("pos").forGetter(VibrationInfo::pos),
                    UUIDUtil.CODEC.lenientOptionalFieldOf("source").forGetter(p_250608_ -> Optional.ofNullable(p_250608_.uuid())),
                    UUIDUtil.CODEC.lenientOptionalFieldOf("projectile_owner").forGetter(p_250607_ -> Optional.ofNullable(p_250607_.projectileOwnerUuid()))
                )
                .apply(
                    p_348454_,
                    (p_316093_, p_316094_, p_316095_, p_316096_, p_316097_) -> new VibrationInfo(
                            p_316093_, p_316094_, p_316095_, p_316096_.orElse(null), p_316097_.orElse(null)
                        )
                )
    );

    public VibrationInfo(Holder<GameEvent> p_316546_, float p_251086_, Vec3 p_250935_, @Nullable UUID p_316193_, @Nullable UUID p_316157_) {
        this(p_316546_, p_251086_, p_250935_, p_316193_, p_316157_, null);
    }

    public VibrationInfo(Holder<GameEvent> p_316851_, float p_250190_, Vec3 p_251692_, @Nullable Entity p_316868_) {
        this(p_316851_, p_250190_, p_251692_, p_316868_ == null ? null : p_316868_.getUUID(), getProjectileOwner(p_316868_), p_316868_);
    }

    @Nullable
    private static UUID getProjectileOwner(@Nullable Entity p_251531_) {
        if (p_251531_ instanceof Projectile projectile && projectile.getOwner() != null) {
            return projectile.getOwner().getUUID();
        }

        return null;
    }

    public Optional<Entity> getEntity(ServerLevel p_249184_) {
        return Optional.ofNullable(this.entity).or(() -> Optional.ofNullable(this.uuid).map(p_249184_::getEntity));
    }

    public Optional<Entity> getProjectileOwner(ServerLevel p_249217_) {
        return this.getEntity(p_249217_)
            .filter(p_249829_ -> p_249829_ instanceof Projectile)
            .map(p_249388_ -> (Projectile)p_249388_)
            .map(Projectile::getOwner)
            .or(() -> Optional.ofNullable(this.projectileOwnerUuid).map(p_249217_::getEntity));
    }
}
