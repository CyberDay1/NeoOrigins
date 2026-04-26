package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;

public record EntityPredicate(
    Optional<EntityTypePredicate> entityType,
    Optional<DistancePredicate> distanceToPlayer,
    Optional<MovementPredicate> movement,
    EntityPredicate.LocationWrapper location,
    Optional<MobEffectsPredicate> effects,
    Optional<NbtPredicate> nbt,
    Optional<EntityFlagsPredicate> flags,
    Optional<EntityEquipmentPredicate> equipment,
    Optional<EntitySubPredicate> subPredicate,
    Optional<Integer> periodicTick,
    Optional<EntityPredicate> vehicle,
    Optional<EntityPredicate> passenger,
    Optional<EntityPredicate> targetedEntity,
    Optional<String> team,
    Optional<SlotsPredicate> slots
) {
    public static final Codec<EntityPredicate> CODEC = Codec.recursive(
        "EntityPredicate",
        p_297888_ -> RecordCodecBuilder.create(
                p_348088_ -> p_348088_.group(
                            EntityTypePredicate.CODEC.optionalFieldOf("type").forGetter(EntityPredicate::entityType),
                            DistancePredicate.CODEC.optionalFieldOf("distance").forGetter(EntityPredicate::distanceToPlayer),
                            MovementPredicate.CODEC.optionalFieldOf("movement").forGetter(EntityPredicate::movement),
                            EntityPredicate.LocationWrapper.CODEC.forGetter(EntityPredicate::location),
                            MobEffectsPredicate.CODEC.optionalFieldOf("effects").forGetter(EntityPredicate::effects),
                            NbtPredicate.CODEC.optionalFieldOf("nbt").forGetter(EntityPredicate::nbt),
                            EntityFlagsPredicate.CODEC.optionalFieldOf("flags").forGetter(EntityPredicate::flags),
                            EntityEquipmentPredicate.CODEC.optionalFieldOf("equipment").forGetter(EntityPredicate::equipment),
                            EntitySubPredicate.CODEC.optionalFieldOf("type_specific").forGetter(EntityPredicate::subPredicate),
                            ExtraCodecs.POSITIVE_INT.optionalFieldOf("periodic_tick").forGetter(EntityPredicate::periodicTick),
                            p_297888_.optionalFieldOf("vehicle").forGetter(EntityPredicate::vehicle),
                            p_297888_.optionalFieldOf("passenger").forGetter(EntityPredicate::passenger),
                            p_297888_.optionalFieldOf("targeted_entity").forGetter(EntityPredicate::targetedEntity),
                            Codec.STRING.optionalFieldOf("team").forGetter(EntityPredicate::team),
                            SlotsPredicate.CODEC.optionalFieldOf("slots").forGetter(EntityPredicate::slots)
                        )
                        .apply(p_348088_, EntityPredicate::new)
            )
    );
    public static final Codec<ContextAwarePredicate> ADVANCEMENT_CODEC = Codec.withAlternative(ContextAwarePredicate.CODEC, CODEC, EntityPredicate::wrap);

    public static ContextAwarePredicate wrap(EntityPredicate.Builder p_298222_) {
        return wrap(p_298222_.build());
    }

    public static Optional<ContextAwarePredicate> wrap(Optional<EntityPredicate> p_298604_) {
        return p_298604_.map(EntityPredicate::wrap);
    }

    public static List<ContextAwarePredicate> wrap(EntityPredicate.Builder... p_298584_) {
        return Stream.of(p_298584_).map(EntityPredicate::wrap).toList();
    }

    public static ContextAwarePredicate wrap(EntityPredicate p_286570_) {
        LootItemCondition lootitemcondition = LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS, p_286570_).build();
        return new ContextAwarePredicate(List.of(lootitemcondition));
    }

    public boolean matches(ServerPlayer p_36612_, @Nullable Entity p_36613_) {
        return this.matches(p_36612_.serverLevel(), p_36612_.position(), p_36613_);
    }

    public boolean matches(ServerLevel p_36608_, @Nullable Vec3 p_36609_, @Nullable Entity p_36610_) {
        if (p_36610_ == null) {
            return false;
        } else if (this.entityType.isPresent() && !this.entityType.get().matches(p_36610_.getType())) {
            return false;
        } else {
            if (p_36609_ == null) {
                if (this.distanceToPlayer.isPresent()) {
                    return false;
                }
            } else if (this.distanceToPlayer.isPresent()
                && !this.distanceToPlayer.get().matches(p_36609_.x, p_36609_.y, p_36609_.z, p_36610_.getX(), p_36610_.getY(), p_36610_.getZ())) {
                return false;
            }

            if (this.movement.isPresent()) {
                Vec3 vec3 = p_36610_.getKnownMovement();
                Vec3 vec31 = vec3.scale(20.0);
                if (!this.movement.get().matches(vec31.x, vec31.y, vec31.z, (double)p_36610_.fallDistance)) {
                    return false;
                }
            }

            if (this.location.located.isPresent() && !this.location.located.get().matches(p_36608_, p_36610_.getX(), p_36610_.getY(), p_36610_.getZ())) {
                return false;
            } else {
                if (this.location.steppingOn.isPresent()) {
                    Vec3 vec32 = Vec3.atCenterOf(p_36610_.getOnPos());
                    if (!this.location.steppingOn.get().matches(p_36608_, vec32.x(), vec32.y(), vec32.z())) {
                        return false;
                    }
                }

                if (this.location.affectsMovement.isPresent()) {
                    Vec3 vec33 = Vec3.atCenterOf(p_36610_.getBlockPosBelowThatAffectsMyMovement());
                    if (!this.location.affectsMovement.get().matches(p_36608_, vec33.x(), vec33.y(), vec33.z())) {
                        return false;
                    }
                }

                if (this.effects.isPresent() && !this.effects.get().matches(p_36610_)) {
                    return false;
                } else if (this.flags.isPresent() && !this.flags.get().matches(p_36610_)) {
                    return false;
                } else if (this.equipment.isPresent() && !this.equipment.get().matches(p_36610_)) {
                    return false;
                } else if (this.subPredicate.isPresent() && !this.subPredicate.get().matches(p_36610_, p_36608_, p_36609_)) {
                    return false;
                } else if (this.vehicle.isPresent() && !this.vehicle.get().matches(p_36608_, p_36609_, p_36610_.getVehicle())) {
                    return false;
                } else if (this.passenger.isPresent()
                    && p_36610_.getPassengers().stream().noneMatch(p_297893_ -> this.passenger.get().matches(p_36608_, p_36609_, p_297893_))) {
                    return false;
                } else if (this.targetedEntity.isPresent()
                    && !this.targetedEntity.get().matches(p_36608_, p_36609_, p_36610_ instanceof Mob ? ((Mob)p_36610_).getTarget() : null)) {
                    return false;
                } else if (this.periodicTick.isPresent() && p_36610_.tickCount % this.periodicTick.get() != 0) {
                    return false;
                } else {
                    if (this.team.isPresent()) {
                        Team team = p_36610_.getTeam();
                        if (team == null || !this.team.get().equals(team.getName())) {
                            return false;
                        }
                    }

                    return this.slots.isPresent() && !this.slots.get().matches(p_36610_) ? false : !this.nbt.isPresent() || this.nbt.get().matches(p_36610_);
                }
            }
        }
    }

    public static LootContext createContext(ServerPlayer p_36617_, Entity p_36618_) {
        LootParams lootparams = new LootParams.Builder(p_36617_.serverLevel())
            .withParameter(LootContextParams.THIS_ENTITY, p_36618_)
            .withParameter(LootContextParams.ORIGIN, p_36617_.position())
            .create(LootContextParamSets.ADVANCEMENT_ENTITY);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static class Builder {
        private Optional<EntityTypePredicate> entityType = Optional.empty();
        private Optional<DistancePredicate> distanceToPlayer = Optional.empty();
        private Optional<DistancePredicate> fallDistance = Optional.empty();
        private Optional<MovementPredicate> movement = Optional.empty();
        private Optional<EntityPredicate.LocationWrapper> location = Optional.empty();
        private Optional<LocationPredicate> located = Optional.empty();
        private Optional<LocationPredicate> steppingOnLocation = Optional.empty();
        private Optional<LocationPredicate> movementAffectedBy = Optional.empty();
        private Optional<MobEffectsPredicate> effects = Optional.empty();
        private Optional<NbtPredicate> nbt = Optional.empty();
        private Optional<EntityFlagsPredicate> flags = Optional.empty();
        private Optional<EntityEquipmentPredicate> equipment = Optional.empty();
        private Optional<EntitySubPredicate> subPredicate = Optional.empty();
        private Optional<Integer> periodicTick = Optional.empty();
        private Optional<EntityPredicate> vehicle = Optional.empty();
        private Optional<EntityPredicate> passenger = Optional.empty();
        private Optional<EntityPredicate> targetedEntity = Optional.empty();
        private Optional<String> team = Optional.empty();
        private Optional<SlotsPredicate> slots = Optional.empty();

        public static EntityPredicate.Builder entity() {
            return new EntityPredicate.Builder();
        }

        public EntityPredicate.Builder of(EntityType<?> p_36637_) {
            this.entityType = Optional.of(EntityTypePredicate.of(p_36637_));
            return this;
        }

        public EntityPredicate.Builder of(TagKey<EntityType<?>> p_204078_) {
            this.entityType = Optional.of(EntityTypePredicate.of(p_204078_));
            return this;
        }

        public EntityPredicate.Builder entityType(EntityTypePredicate p_36647_) {
            this.entityType = Optional.of(p_36647_);
            return this;
        }

        public EntityPredicate.Builder distance(DistancePredicate p_36639_) {
            this.distanceToPlayer = Optional.of(p_36639_);
            return this;
        }

        public EntityPredicate.Builder moving(MovementPredicate p_346088_) {
            this.movement = Optional.of(p_346088_);
            return this;
        }

        public EntityPredicate.Builder located(LocationPredicate.Builder p_298230_) {
            this.located = Optional.of(p_298230_.build());
            return this;
        }

        public EntityPredicate.Builder steppingOn(LocationPredicate.Builder p_298232_) {
            this.steppingOnLocation = Optional.of(p_298232_.build());
            return this;
        }

        public EntityPredicate.Builder movementAffectedBy(LocationPredicate.Builder p_348473_) {
            this.movementAffectedBy = Optional.of(p_348473_.build());
            return this;
        }

        public EntityPredicate.Builder effects(MobEffectsPredicate.Builder p_298981_) {
            this.effects = p_298981_.build();
            return this;
        }

        public EntityPredicate.Builder nbt(NbtPredicate p_36655_) {
            this.nbt = Optional.of(p_36655_);
            return this;
        }

        public EntityPredicate.Builder flags(EntityFlagsPredicate.Builder p_298456_) {
            this.flags = Optional.of(p_298456_.build());
            return this;
        }

        public EntityPredicate.Builder equipment(EntityEquipmentPredicate.Builder p_299134_) {
            this.equipment = Optional.of(p_299134_.build());
            return this;
        }

        public EntityPredicate.Builder equipment(EntityEquipmentPredicate p_36641_) {
            this.equipment = Optional.of(p_36641_);
            return this;
        }

        public EntityPredicate.Builder subPredicate(EntitySubPredicate p_218801_) {
            this.subPredicate = Optional.of(p_218801_);
            return this;
        }

        public EntityPredicate.Builder periodicTick(int p_345599_) {
            this.periodicTick = Optional.of(p_345599_);
            return this;
        }

        public EntityPredicate.Builder vehicle(EntityPredicate.Builder p_299103_) {
            this.vehicle = Optional.of(p_299103_.build());
            return this;
        }

        public EntityPredicate.Builder passenger(EntityPredicate.Builder p_298577_) {
            this.passenger = Optional.of(p_298577_.build());
            return this;
        }

        public EntityPredicate.Builder targetedEntity(EntityPredicate.Builder p_299292_) {
            this.targetedEntity = Optional.of(p_299292_.build());
            return this;
        }

        public EntityPredicate.Builder team(String p_36659_) {
            this.team = Optional.of(p_36659_);
            return this;
        }

        public EntityPredicate.Builder slots(SlotsPredicate p_332780_) {
            this.slots = Optional.of(p_332780_);
            return this;
        }

        public EntityPredicate build() {
            return new EntityPredicate(
                this.entityType,
                this.distanceToPlayer,
                this.movement,
                new EntityPredicate.LocationWrapper(this.located, this.steppingOnLocation, this.movementAffectedBy),
                this.effects,
                this.nbt,
                this.flags,
                this.equipment,
                this.subPredicate,
                this.periodicTick,
                this.vehicle,
                this.passenger,
                this.targetedEntity,
                this.team,
                this.slots
            );
        }
    }

    public static record LocationWrapper(
        Optional<LocationPredicate> located, Optional<LocationPredicate> steppingOn, Optional<LocationPredicate> affectsMovement
    ) {
        public static final MapCodec<EntityPredicate.LocationWrapper> CODEC = RecordCodecBuilder.mapCodec(
            p_348475_ -> p_348475_.group(
                        LocationPredicate.CODEC.optionalFieldOf("location").forGetter(EntityPredicate.LocationWrapper::located),
                        LocationPredicate.CODEC.optionalFieldOf("stepping_on").forGetter(EntityPredicate.LocationWrapper::steppingOn),
                        LocationPredicate.CODEC.optionalFieldOf("movement_affected_by").forGetter(EntityPredicate.LocationWrapper::affectsMovement)
                    )
                    .apply(p_348475_, EntityPredicate.LocationWrapper::new)
        );
    }
}
