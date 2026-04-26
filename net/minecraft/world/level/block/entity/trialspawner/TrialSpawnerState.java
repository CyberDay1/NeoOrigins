package net.minecraft.world.level.block.entity.trialspawner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public enum TrialSpawnerState implements StringRepresentable {
    INACTIVE("inactive", 0, TrialSpawnerState.ParticleEmission.NONE, -1.0, false),
    WAITING_FOR_PLAYERS("waiting_for_players", 4, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, 200.0, true),
    ACTIVE("active", 8, TrialSpawnerState.ParticleEmission.FLAMES_AND_SMOKE, 1000.0, true),
    WAITING_FOR_REWARD_EJECTION("waiting_for_reward_ejection", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0, false),
    EJECTING_REWARD("ejecting_reward", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0, false),
    COOLDOWN("cooldown", 0, TrialSpawnerState.ParticleEmission.SMOKE_INSIDE_AND_TOP_FACE, -1.0, false);

    private static final float DELAY_BEFORE_EJECT_AFTER_KILLING_LAST_MOB = 40.0F;
    private static final int TIME_BETWEEN_EACH_EJECTION = Mth.floor(30.0F);
    private final String name;
    private final int lightLevel;
    private final double spinningMobSpeed;
    private final TrialSpawnerState.ParticleEmission particleEmission;
    private final boolean isCapableOfSpawning;

    private TrialSpawnerState(String p_312098_, int p_312873_, TrialSpawnerState.ParticleEmission p_312259_, double p_312005_, boolean p_312451_) {
        this.name = p_312098_;
        this.lightLevel = p_312873_;
        this.particleEmission = p_312259_;
        this.spinningMobSpeed = p_312005_;
        this.isCapableOfSpawning = p_312451_;
    }

    TrialSpawnerState tickAndGetNext(BlockPos p_312221_, TrialSpawner p_311912_, ServerLevel p_311974_) {
        TrialSpawnerData trialspawnerdata = p_311912_.getData();
        TrialSpawnerConfig trialspawnerconfig = p_311912_.getConfig();

        return switch (this) {
            case INACTIVE -> trialspawnerdata.getOrCreateDisplayEntity(p_311912_, p_311974_, WAITING_FOR_PLAYERS) == null ? this : WAITING_FOR_PLAYERS;
            case WAITING_FOR_PLAYERS -> {
                if (!p_311912_.canSpawnInLevel(p_311974_)) {
                    trialspawnerdata.reset();
                    yield this;
                } else if (!trialspawnerdata.hasMobToSpawn(p_311912_, p_311974_.random)) {
                    yield INACTIVE;
                } else {
                    trialspawnerdata.tryDetectPlayers(p_311974_, p_312221_, p_311912_);
                    yield trialspawnerdata.detectedPlayers.isEmpty() ? this : ACTIVE;
                }
            }
            case ACTIVE -> {
                if (!p_311912_.canSpawnInLevel(p_311974_)) {
                    trialspawnerdata.reset();
                    yield WAITING_FOR_PLAYERS;
                } else if (!trialspawnerdata.hasMobToSpawn(p_311912_, p_311974_.random)) {
                    yield INACTIVE;
                } else {
                    int i = trialspawnerdata.countAdditionalPlayers(p_312221_);
                    trialspawnerdata.tryDetectPlayers(p_311974_, p_312221_, p_311912_);
                    if (p_311912_.isOminous()) {
                        this.spawnOminousOminousItemSpawner(p_311974_, p_312221_, p_311912_);
                    }

                    if (trialspawnerdata.hasFinishedSpawningAllMobs(trialspawnerconfig, i)) {
                        if (trialspawnerdata.haveAllCurrentMobsDied()) {
                            trialspawnerdata.cooldownEndsAt = p_311974_.getGameTime() + (long)p_311912_.getTargetCooldownLength();
                            trialspawnerdata.totalMobsSpawned = 0;
                            trialspawnerdata.nextMobSpawnsAt = 0L;
                            yield WAITING_FOR_REWARD_EJECTION;
                        }
                    } else if (trialspawnerdata.isReadyToSpawnNextMob(p_311974_, trialspawnerconfig, i)) {
                        p_311912_.spawnMob(p_311974_, p_312221_).ifPresent(p_340800_ -> {
                            trialspawnerdata.currentMobs.add(p_340800_);
                            trialspawnerdata.totalMobsSpawned++;
                            trialspawnerdata.nextMobSpawnsAt = p_311974_.getGameTime() + (long)trialspawnerconfig.ticksBetweenSpawn();
                            trialspawnerconfig.spawnPotentialsDefinition().getRandom(p_311974_.getRandom()).ifPresent(p_338048_ -> {
                                trialspawnerdata.nextSpawnData = Optional.of(p_338048_.data());
                                p_311912_.markUpdated();
                            });
                        });
                    }

                    yield this;
                }
            }
            case WAITING_FOR_REWARD_EJECTION -> {
                if (trialspawnerdata.isReadyToOpenShutter(p_311974_, 40.0F, p_311912_.getTargetCooldownLength())) {
                    p_311974_.playSound(null, p_312221_, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS);
                    yield EJECTING_REWARD;
                } else {
                    yield this;
                }
            }
            case EJECTING_REWARD -> {
                if (!trialspawnerdata.isReadyToEjectItems(p_311974_, (float)TIME_BETWEEN_EACH_EJECTION, p_311912_.getTargetCooldownLength())) {
                    yield this;
                } else if (trialspawnerdata.detectedPlayers.isEmpty()) {
                    p_311974_.playSound(null, p_312221_, SoundEvents.TRIAL_SPAWNER_CLOSE_SHUTTER, SoundSource.BLOCKS);
                    trialspawnerdata.ejectingLootTable = Optional.empty();
                    yield COOLDOWN;
                } else {
                    if (trialspawnerdata.ejectingLootTable.isEmpty()) {
                        trialspawnerdata.ejectingLootTable = trialspawnerconfig.lootTablesToEject().getRandomValue(p_311974_.getRandom());
                    }

                    trialspawnerdata.ejectingLootTable.ifPresent(p_335304_ -> p_311912_.ejectReward(p_311974_, p_312221_, (ResourceKey<LootTable>)p_335304_));
                    trialspawnerdata.detectedPlayers.remove(trialspawnerdata.detectedPlayers.iterator().next());
                    yield this;
                }
            }
            case COOLDOWN -> {
                trialspawnerdata.tryDetectPlayers(p_311974_, p_312221_, p_311912_);
                if (!trialspawnerdata.detectedPlayers.isEmpty()) {
                    trialspawnerdata.totalMobsSpawned = 0;
                    trialspawnerdata.nextMobSpawnsAt = 0L;
                    yield ACTIVE;
                } else if (trialspawnerdata.isCooldownFinished(p_311974_)) {
                    p_311912_.removeOminous(p_311974_, p_312221_);
                    trialspawnerdata.reset();
                    yield WAITING_FOR_PLAYERS;
                } else {
                    yield this;
                }
            }
        };
    }

    private void spawnOminousOminousItemSpawner(ServerLevel p_338483_, BlockPos p_338824_, TrialSpawner p_338767_) {
        TrialSpawnerData trialspawnerdata = p_338767_.getData();
        TrialSpawnerConfig trialspawnerconfig = p_338767_.getConfig();
        ItemStack itemstack = trialspawnerdata.getDispensingItems(p_338483_, trialspawnerconfig, p_338824_)
            .getRandomValue(p_338483_.random)
            .orElse(ItemStack.EMPTY);
        if (!itemstack.isEmpty()) {
            if (this.timeToSpawnItemSpawner(p_338483_, trialspawnerdata)) {
                calculatePositionToSpawnSpawner(p_338483_, p_338824_, p_338767_, trialspawnerdata).ifPresent(p_338064_ -> {
                    OminousItemSpawner ominousitemspawner = OminousItemSpawner.create(p_338483_, itemstack);
                    ominousitemspawner.moveTo(p_338064_);
                    p_338483_.addFreshEntity(ominousitemspawner);
                    float f = (p_338483_.getRandom().nextFloat() - p_338483_.getRandom().nextFloat()) * 0.2F + 1.0F;
                    p_338483_.playSound(null, BlockPos.containing(p_338064_), SoundEvents.TRIAL_SPAWNER_SPAWN_ITEM_BEGIN, SoundSource.BLOCKS, 1.0F, f);
                    trialspawnerdata.cooldownEndsAt = p_338483_.getGameTime() + p_338767_.getOminousConfig().ticksBetweenItemSpawners();
                });
            }
        }
    }

    private static Optional<Vec3> calculatePositionToSpawnSpawner(ServerLevel p_338436_, BlockPos p_338583_, TrialSpawner p_338226_, TrialSpawnerData p_338461_) {
        List<Player> list = p_338461_.detectedPlayers
            .stream()
            .map(p_338436_::getPlayerByUUID)
            .filter(Objects::nonNull)
            .filter(
                p_350236_ -> !p_350236_.isCreative()
                        && !p_350236_.isSpectator()
                        && p_350236_.isAlive()
                        && p_350236_.distanceToSqr(p_338583_.getCenter()) <= (double)Mth.square(p_338226_.getRequiredPlayerRange())
            )
            .toList();
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            Entity entity = selectEntityToSpawnItemAbove(list, p_338461_.currentMobs, p_338226_, p_338583_, p_338436_);
            return entity == null ? Optional.empty() : calculatePositionAbove(entity, p_338436_);
        }
    }

    private static Optional<Vec3> calculatePositionAbove(Entity p_338791_, ServerLevel p_338807_) {
        Vec3 vec3 = p_338791_.position();
        Vec3 vec31 = vec3.relative(Direction.UP, (double)(p_338791_.getBbHeight() + 2.0F + (float)p_338807_.random.nextInt(4)));
        BlockHitResult blockhitresult = p_338807_.clip(new ClipContext(vec3, vec31, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 vec32 = blockhitresult.getBlockPos().getCenter().relative(Direction.DOWN, 1.0);
        BlockPos blockpos = BlockPos.containing(vec32);
        return !p_338807_.getBlockState(blockpos).getCollisionShape(p_338807_, blockpos).isEmpty() ? Optional.empty() : Optional.of(vec32);
    }

    @Nullable
    private static Entity selectEntityToSpawnItemAbove(
        List<Player> p_338467_, Set<UUID> p_338224_, TrialSpawner p_338687_, BlockPos p_338268_, ServerLevel p_338524_
    ) {
        Stream<Entity> stream = p_338224_.stream()
            .map(p_338524_::getEntity)
            .filter(Objects::nonNull)
            .filter(
                p_338051_ -> p_338051_.isAlive() && p_338051_.distanceToSqr(p_338268_.getCenter()) <= (double)Mth.square(p_338687_.getRequiredPlayerRange())
            );
        List<? extends Entity> list = p_338524_.random.nextBoolean() ? stream.toList() : p_338467_;
        if (list.isEmpty()) {
            return null;
        } else {
            return list.size() == 1 ? list.getFirst() : Util.getRandom(list, p_338524_.random);
        }
    }

    private boolean timeToSpawnItemSpawner(ServerLevel p_338741_, TrialSpawnerData p_338296_) {
        return p_338741_.getGameTime() >= p_338296_.cooldownEndsAt;
    }

    public int lightLevel() {
        return this.lightLevel;
    }

    public double spinningMobSpeed() {
        return this.spinningMobSpeed;
    }

    public boolean hasSpinningMob() {
        return this.spinningMobSpeed >= 0.0;
    }

    public boolean isCapableOfSpawning() {
        return this.isCapableOfSpawning;
    }

    public void emitParticles(Level p_312507_, BlockPos p_312610_, boolean p_338615_) {
        this.particleEmission.emit(p_312507_, p_312507_.getRandom(), p_312610_, p_338615_);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    static class LightLevel {
        private static final int UNLIT = 0;
        private static final int HALF_LIT = 4;
        private static final int LIT = 8;

        private LightLevel() {
        }
    }

    interface ParticleEmission {
        TrialSpawnerState.ParticleEmission NONE = (p_311998_, p_311983_, p_312351_, p_338371_) -> {
        };
        TrialSpawnerState.ParticleEmission SMALL_FLAMES = (p_338069_, p_338070_, p_338071_, p_338072_) -> {
            if (p_338070_.nextInt(2) == 0) {
                Vec3 vec3 = p_338071_.getCenter().offsetRandom(p_338070_, 0.9F);
                addParticle(p_338072_ ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME, vec3, p_338069_);
            }
        };
        TrialSpawnerState.ParticleEmission FLAMES_AND_SMOKE = (p_338065_, p_338066_, p_338067_, p_338068_) -> {
            Vec3 vec3 = p_338067_.getCenter().offsetRandom(p_338066_, 1.0F);
            addParticle(ParticleTypes.SMOKE, vec3, p_338065_);
            addParticle(p_338068_ ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, vec3, p_338065_);
        };
        TrialSpawnerState.ParticleEmission SMOKE_INSIDE_AND_TOP_FACE = (p_311899_, p_311762_, p_312096_, p_338301_) -> {
            Vec3 vec3 = p_312096_.getCenter().offsetRandom(p_311762_, 0.9F);
            if (p_311762_.nextInt(3) == 0) {
                addParticle(ParticleTypes.SMOKE, vec3, p_311899_);
            }

            if (p_311899_.getGameTime() % 20L == 0L) {
                Vec3 vec31 = p_312096_.getCenter().add(0.0, 0.5, 0.0);
                int i = p_311899_.getRandom().nextInt(4) + 20;

                for (int j = 0; j < i; j++) {
                    addParticle(ParticleTypes.SMOKE, vec31, p_311899_);
                }
            }
        };

        private static void addParticle(SimpleParticleType p_312519_, Vec3 p_312023_, Level p_311937_) {
            p_311937_.addParticle(p_312519_, p_312023_.x(), p_312023_.y(), p_312023_.z(), 0.0, 0.0, 0.0);
        }

        void emit(Level p_312730_, RandomSource p_312474_, BlockPos p_312127_, boolean p_338742_);
    }

    static class SpinningMob {
        private static final double NONE = -1.0;
        private static final double SLOW = 200.0;
        private static final double FAST = 1000.0;

        private SpinningMob() {
        }
    }
}
