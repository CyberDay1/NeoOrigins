package net.minecraft.world.level.block.entity.trialspawner;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class TrialSpawner implements net.neoforged.neoforge.common.extensions.IOwnedSpawner {
    public static final String NORMAL_CONFIG_TAG_NAME = "normal_config";
    public static final String OMINOUS_CONFIG_TAG_NAME = "ominous_config";
    public static final int DETECT_PLAYER_SPAWN_BUFFER = 40;
    private static final int DEFAULT_TARGET_COOLDOWN_LENGTH = 36000;
    private static final int DEFAULT_PLAYER_SCAN_RANGE = 14;
    private static final int MAX_MOB_TRACKING_DISTANCE = 47;
    private static final int MAX_MOB_TRACKING_DISTANCE_SQR = Mth.square(47);
    private static final float SPAWNING_AMBIENT_SOUND_CHANCE = 0.02F;
    private final TrialSpawnerConfig normalConfig;
    private final TrialSpawnerConfig ominousConfig;
    private final TrialSpawnerData data;
    private final int requiredPlayerRange;
    private final int targetCooldownLength;
    private final TrialSpawner.StateAccessor stateAccessor;
    private PlayerDetector playerDetector;
    private final PlayerDetector.EntitySelector entitySelector;
    private boolean overridePeacefulAndMobSpawnRule;
    private boolean isOminous;

    public Codec<TrialSpawner> codec() {
        return RecordCodecBuilder.create(
            p_338040_ -> p_338040_.group(
                        TrialSpawnerConfig.CODEC.optionalFieldOf("normal_config", TrialSpawnerConfig.DEFAULT).forGetter(TrialSpawner::getNormalConfig),
                        TrialSpawnerConfig.CODEC
                            .optionalFieldOf("ominous_config", TrialSpawnerConfig.DEFAULT)
                            .forGetter(TrialSpawner::getOminousConfigForSerialization),
                        TrialSpawnerData.MAP_CODEC.forGetter(TrialSpawner::getData),
                        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("target_cooldown_length", 36000).forGetter(TrialSpawner::getTargetCooldownLength),
                        Codec.intRange(1, 128).optionalFieldOf("required_player_range", 14).forGetter(TrialSpawner::getRequiredPlayerRange)
                    )
                    .apply(
                        p_338040_,
                        (p_338035_, p_338036_, p_338037_, p_338038_, p_338039_) -> new TrialSpawner(
                                p_338035_, p_338036_, p_338037_, p_338038_, p_338039_, this.stateAccessor, this.playerDetector, this.entitySelector
                            )
                    )
        );
    }

    public TrialSpawner(TrialSpawner.StateAccessor p_312676_, PlayerDetector p_312004_, PlayerDetector.EntitySelector p_323917_) {
        this(TrialSpawnerConfig.DEFAULT, TrialSpawnerConfig.DEFAULT, new TrialSpawnerData(), 36000, 14, p_312676_, p_312004_, p_323917_);
    }

    public TrialSpawner(
        TrialSpawnerConfig p_323942_,
        TrialSpawnerConfig p_338891_,
        TrialSpawnerData p_323470_,
        int p_338747_,
        int p_338885_,
        TrialSpawner.StateAccessor p_312198_,
        PlayerDetector p_312690_,
        PlayerDetector.EntitySelector p_323834_
    ) {
        this.normalConfig = p_323942_;
        this.ominousConfig = p_338891_;
        this.data = p_323470_;
        this.targetCooldownLength = p_338747_;
        this.requiredPlayerRange = p_338885_;
        this.stateAccessor = p_312198_;
        this.playerDetector = p_312690_;
        this.entitySelector = p_323834_;
    }

    public TrialSpawnerConfig getConfig() {
        return this.isOminous ? this.ominousConfig : this.normalConfig;
    }

    @VisibleForTesting
    public TrialSpawnerConfig getNormalConfig() {
        return this.normalConfig;
    }

    @VisibleForTesting
    public TrialSpawnerConfig getOminousConfig() {
        return this.ominousConfig;
    }

    private TrialSpawnerConfig getOminousConfigForSerialization() {
        return !this.ominousConfig.equals(this.normalConfig) ? this.ominousConfig : TrialSpawnerConfig.DEFAULT;
    }

    public void applyOminous(ServerLevel p_338315_, BlockPos p_338416_) {
        p_338315_.setBlock(p_338416_, p_338315_.getBlockState(p_338416_).setValue(TrialSpawnerBlock.OMINOUS, Boolean.valueOf(true)), 3);
        p_338315_.levelEvent(3020, p_338416_, 1);
        this.isOminous = true;
        this.data.resetAfterBecomingOminous(this, p_338315_);
    }

    public void removeOminous(ServerLevel p_338672_, BlockPos p_338318_) {
        p_338672_.setBlock(p_338318_, p_338672_.getBlockState(p_338318_).setValue(TrialSpawnerBlock.OMINOUS, Boolean.valueOf(false)), 3);
        this.isOminous = false;
    }

    public boolean isOminous() {
        return this.isOminous;
    }

    public TrialSpawnerData getData() {
        return this.data;
    }

    public int getTargetCooldownLength() {
        return this.targetCooldownLength;
    }

    public int getRequiredPlayerRange() {
        return this.requiredPlayerRange;
    }

    public TrialSpawnerState getState() {
        return this.stateAccessor.getState();
    }

    public void setState(Level p_312688_, TrialSpawnerState p_312718_) {
        this.stateAccessor.setState(p_312688_, p_312718_);
    }

    public void markUpdated() {
        this.stateAccessor.markUpdated();
    }

    public PlayerDetector getPlayerDetector() {
        return this.playerDetector;
    }

    public PlayerDetector.EntitySelector getEntitySelector() {
        return this.entitySelector;
    }

    public boolean canSpawnInLevel(Level p_312615_) {
        if (this.overridePeacefulAndMobSpawnRule) {
            return true;
        } else {
            return p_312615_.getDifficulty() == Difficulty.PEACEFUL ? false : p_312615_.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        }
    }

    public Optional<UUID> spawnMob(ServerLevel p_312582_, BlockPos p_312518_) {
        RandomSource randomsource = p_312582_.getRandom();
        SpawnData spawndata = this.data.getOrCreateNextSpawnData(this, p_312582_.getRandom());
        CompoundTag compoundtag = spawndata.entityToSpawn();
        ListTag listtag = compoundtag.getList("Pos", 6);
        Optional<EntityType<?>> optional = EntityType.by(compoundtag);
        if (optional.isEmpty()) {
            return Optional.empty();
        } else {
            int i = listtag.size();
            double d0 = i >= 1
                ? listtag.getDouble(0)
                : (double)p_312518_.getX() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double)this.getConfig().spawnRange() + 0.5;
            double d1 = i >= 2 ? listtag.getDouble(1) : (double)(p_312518_.getY() + randomsource.nextInt(3) - 1);
            double d2 = i >= 3
                ? listtag.getDouble(2)
                : (double)p_312518_.getZ() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double)this.getConfig().spawnRange() + 0.5;
            if (!p_312582_.noCollision(optional.get().getSpawnAABB(d0, d1, d2))) {
                return Optional.empty();
            } else {
                Vec3 vec3 = new Vec3(d0, d1, d2);
                if (!inLineOfSight(p_312582_, p_312518_.getCenter(), vec3)) {
                    return Optional.empty();
                } else {
                    BlockPos blockpos = BlockPos.containing(vec3);
                    if (!SpawnPlacements.checkSpawnRules(optional.get(), p_312582_, MobSpawnType.TRIAL_SPAWNER, blockpos, p_312582_.getRandom())) {
                        return Optional.empty();
                    } else {
                        if (spawndata.getCustomSpawnRules().isPresent()) {
                            SpawnData.CustomSpawnRules spawndata$customspawnrules = spawndata.getCustomSpawnRules().get();
                            if (!spawndata$customspawnrules.isValidPosition(blockpos, p_312582_)) {
                                return Optional.empty();
                            }
                        }

                        Entity entity = EntityType.loadEntityRecursive(compoundtag, p_312582_, p_312375_ -> {
                            p_312375_.moveTo(d0, d1, d2, randomsource.nextFloat() * 360.0F, 0.0F);
                            return p_312375_;
                        });
                        if (entity == null) {
                            return Optional.empty();
                        } else {
                            if (entity instanceof Mob mob) {
                                if (!mob.checkSpawnObstruction(p_312582_)) {
                                    return Optional.empty();
                                }

                                boolean flag = spawndata.getEntityToSpawn().size() == 1 && spawndata.getEntityToSpawn().contains("id", 8);
                                // Neo: Patch in FinalizeSpawn for spawners so it may be fired unconditionally, instead of only when vanilla would normally call it.
                                // The local flag is the conditions under which the spawner will normally call Mob#finalizeSpawn.
                                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawnSpawner(mob, p_312582_, p_312582_.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.TRIAL_SPAWNER, null, this, flag);

                                mob.setPersistenceRequired();
                                spawndata.getEquipment().ifPresent(mob::equip);
                            }

                            if (!p_312582_.tryAddFreshEntityWithPassengers(entity)) {
                                return Optional.empty();
                            } else {
                                TrialSpawner.FlameParticle trialspawner$flameparticle = this.isOminous
                                    ? TrialSpawner.FlameParticle.OMINOUS
                                    : TrialSpawner.FlameParticle.NORMAL;
                                p_312582_.levelEvent(3011, p_312518_, trialspawner$flameparticle.encode());
                                p_312582_.levelEvent(3012, blockpos, trialspawner$flameparticle.encode());
                                p_312582_.gameEvent(entity, GameEvent.ENTITY_PLACE, blockpos);
                                return Optional.of(entity.getUUID());
                            }
                        }
                    }
                }
            }
        }
    }

    public void ejectReward(ServerLevel p_312892_, BlockPos p_312668_, ResourceKey<LootTable> p_335942_) {
        LootTable loottable = p_312892_.getServer().reloadableRegistries().getLootTable(p_335942_);
        LootParams lootparams = new LootParams.Builder(p_312892_).create(LootContextParamSets.EMPTY);
        ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams);
        if (!objectarraylist.isEmpty()) {
            for (ItemStack itemstack : objectarraylist) {
                DefaultDispenseItemBehavior.spawnItem(p_312892_, itemstack, 2, Direction.UP, Vec3.atBottomCenterOf(p_312668_).relative(Direction.UP, 1.2));
            }

            p_312892_.levelEvent(3014, p_312668_, 0);
        }
    }

    public void tickClient(Level p_312771_, BlockPos p_312484_, boolean p_338382_) {
        TrialSpawnerState trialspawnerstate = this.getState();
        trialspawnerstate.emitParticles(p_312771_, p_312484_, p_338382_);
        if (trialspawnerstate.hasSpinningMob()) {
            double d0 = (double)Math.max(0L, this.data.nextMobSpawnsAt - p_312771_.getGameTime());
            this.data.oSpin = this.data.spin;
            this.data.spin = (this.data.spin + trialspawnerstate.spinningMobSpeed() / (d0 + 200.0)) % 360.0;
        }

        if (trialspawnerstate.isCapableOfSpawning()) {
            RandomSource randomsource = p_312771_.getRandom();
            if (randomsource.nextFloat() <= 0.02F) {
                SoundEvent soundevent = p_338382_ ? SoundEvents.TRIAL_SPAWNER_AMBIENT_OMINOUS : SoundEvents.TRIAL_SPAWNER_AMBIENT;
                p_312771_.playLocalSound(
                    p_312484_, soundevent, SoundSource.BLOCKS, randomsource.nextFloat() * 0.25F + 0.75F, randomsource.nextFloat() + 0.5F, false
                );
            }
        }
    }

    public void tickServer(ServerLevel p_312132_, BlockPos p_312062_, boolean p_338207_) {
        this.isOminous = p_338207_;
        TrialSpawnerState trialspawnerstate = this.getState();
        if (this.data.currentMobs.removeIf(p_312870_ -> shouldMobBeUntracked(p_312132_, p_312062_, p_312870_))) {
            this.data.nextMobSpawnsAt = p_312132_.getGameTime() + (long)this.getConfig().ticksBetweenSpawn();
        }

        TrialSpawnerState trialspawnerstate1 = trialspawnerstate.tickAndGetNext(p_312062_, this, p_312132_);
        if (trialspawnerstate1 != trialspawnerstate) {
            this.setState(p_312132_, trialspawnerstate1);
        }
    }

    private static boolean shouldMobBeUntracked(ServerLevel p_312908_, BlockPos p_312807_, UUID p_311952_) {
        Entity entity = p_312908_.getEntity(p_311952_);
        return entity == null
            || !entity.isAlive()
            || !entity.level().dimension().equals(p_312908_.dimension())
            || entity.blockPosition().distSqr(p_312807_) > (double)MAX_MOB_TRACKING_DISTANCE_SQR;
    }

    private static boolean inLineOfSight(Level p_312623_, Vec3 p_312390_, Vec3 p_312652_) {
        BlockHitResult blockhitresult = p_312623_.clip(
            new ClipContext(p_312652_, p_312390_, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty())
        );
        return blockhitresult.getBlockPos().equals(BlockPos.containing(p_312390_)) || blockhitresult.getType() == HitResult.Type.MISS;
    }

    public static void addSpawnParticles(Level p_312303_, BlockPos p_312392_, RandomSource p_312825_, SimpleParticleType p_338426_) {
        for (int i = 0; i < 20; i++) {
            double d0 = (double)p_312392_.getX() + 0.5 + (p_312825_.nextDouble() - 0.5) * 2.0;
            double d1 = (double)p_312392_.getY() + 0.5 + (p_312825_.nextDouble() - 0.5) * 2.0;
            double d2 = (double)p_312392_.getZ() + 0.5 + (p_312825_.nextDouble() - 0.5) * 2.0;
            p_312303_.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0, 0.0, 0.0);
            p_312303_.addParticle(p_338426_, d0, d1, d2, 0.0, 0.0, 0.0);
        }
    }

    public static void addBecomeOminousParticles(Level p_338657_, BlockPos p_338406_, RandomSource p_338527_) {
        for (int i = 0; i < 20; i++) {
            double d0 = (double)p_338406_.getX() + 0.5 + (p_338527_.nextDouble() - 0.5) * 2.0;
            double d1 = (double)p_338406_.getY() + 0.5 + (p_338527_.nextDouble() - 0.5) * 2.0;
            double d2 = (double)p_338406_.getZ() + 0.5 + (p_338527_.nextDouble() - 0.5) * 2.0;
            double d3 = p_338527_.nextGaussian() * 0.02;
            double d4 = p_338527_.nextGaussian() * 0.02;
            double d5 = p_338527_.nextGaussian() * 0.02;
            p_338657_.addParticle(ParticleTypes.TRIAL_OMEN, d0, d1, d2, d3, d4, d5);
            p_338657_.addParticle(ParticleTypes.SOUL_FIRE_FLAME, d0, d1, d2, d3, d4, d5);
        }
    }

    public static void addDetectPlayerParticles(Level p_312225_, BlockPos p_311759_, RandomSource p_312553_, int p_312188_, ParticleOptions p_338522_) {
        for (int i = 0; i < 30 + Math.min(p_312188_, 10) * 5; i++) {
            double d0 = (double)(2.0F * p_312553_.nextFloat() - 1.0F) * 0.65;
            double d1 = (double)(2.0F * p_312553_.nextFloat() - 1.0F) * 0.65;
            double d2 = (double)p_311759_.getX() + 0.5 + d0;
            double d3 = (double)p_311759_.getY() + 0.1 + (double)p_312553_.nextFloat() * 0.8;
            double d4 = (double)p_311759_.getZ() + 0.5 + d1;
            p_312225_.addParticle(p_338522_, d2, d3, d4, 0.0, 0.0, 0.0);
        }
    }

    public static void addEjectItemParticles(Level p_312009_, BlockPos p_312583_, RandomSource p_312715_) {
        for (int i = 0; i < 20; i++) {
            double d0 = (double)p_312583_.getX() + 0.4 + p_312715_.nextDouble() * 0.2;
            double d1 = (double)p_312583_.getY() + 0.4 + p_312715_.nextDouble() * 0.2;
            double d2 = (double)p_312583_.getZ() + 0.4 + p_312715_.nextDouble() * 0.2;
            double d3 = p_312715_.nextGaussian() * 0.02;
            double d4 = p_312715_.nextGaussian() * 0.02;
            double d5 = p_312715_.nextGaussian() * 0.02;
            p_312009_.addParticle(ParticleTypes.SMALL_FLAME, d0, d1, d2, d3, d4, d5 * 0.25);
            p_312009_.addParticle(ParticleTypes.SMOKE, d0, d1, d2, d3, d4, d5);
        }
    }

    @Deprecated(
        forRemoval = true
    )
    @VisibleForTesting
    public void setPlayerDetector(PlayerDetector p_312510_) {
        this.playerDetector = p_312510_;
    }

    @Deprecated(
        forRemoval = true
    )
    @VisibleForTesting
    public void overridePeacefulAndMobSpawnRule() {
        this.overridePeacefulAndMobSpawnRule = true;
    }

    public static enum FlameParticle {
        NORMAL(ParticleTypes.FLAME),
        OMINOUS(ParticleTypes.SOUL_FIRE_FLAME);

        public final SimpleParticleType particleType;

        private FlameParticle(SimpleParticleType p_338374_) {
            this.particleType = p_338374_;
        }

        public static TrialSpawner.FlameParticle decode(int p_338695_) {
            TrialSpawner.FlameParticle[] atrialspawner$flameparticle = values();
            return p_338695_ <= atrialspawner$flameparticle.length && p_338695_ >= 0 ? atrialspawner$flameparticle[p_338695_] : NORMAL;
        }

        public int encode() {
            return this.ordinal();
        }
    }

    public interface StateAccessor {
        void setState(Level p_312617_, TrialSpawnerState p_312288_);

        TrialSpawnerState getState();

        void markUpdated();
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public com.mojang.datafixers.util.Either<net.minecraft.world.level.block.entity.BlockEntity, Entity> getOwner() {
        if (this.stateAccessor instanceof net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity be) {
            return com.mojang.datafixers.util.Either.left(be);
        }
        return null;
    }
}
