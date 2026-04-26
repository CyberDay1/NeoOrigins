package net.minecraft.world.level.block.entity.vault;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class VaultBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final VaultServerData serverData = new VaultServerData();
    private final VaultSharedData sharedData = new VaultSharedData();
    private final VaultClientData clientData = new VaultClientData();
    private VaultConfig config = VaultConfig.DEFAULT;

    public VaultBlockEntity(BlockPos p_324016_, BlockState p_324022_) {
        super(BlockEntityType.VAULT, p_324016_, p_324022_);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p_324118_) {
        return Util.make(new CompoundTag(), p_330145_ -> p_330145_.put("shared_data", encode(VaultSharedData.CODEC, this.sharedData, p_324118_)));
    }

    @Override
    protected void saveAdditional(CompoundTag p_323998_, HolderLookup.Provider p_324356_) {
        super.saveAdditional(p_323998_, p_324356_);
        p_323998_.put("config", encode(VaultConfig.CODEC, this.config, p_324356_));
        p_323998_.put("shared_data", encode(VaultSharedData.CODEC, this.sharedData, p_324356_));
        p_323998_.put("server_data", encode(VaultServerData.CODEC, this.serverData, p_324356_));
    }

    private static <T> Tag encode(Codec<T> p_324465_, T p_324338_, HolderLookup.Provider p_331118_) {
        return p_324465_.encodeStart(p_331118_.createSerializationContext(NbtOps.INSTANCE), p_324338_).getOrThrow();
    }

    @Override
    protected void loadAdditional(CompoundTag p_324011_, HolderLookup.Provider p_324430_) {
        super.loadAdditional(p_324011_, p_324430_);
        DynamicOps<Tag> dynamicops = p_324430_.createSerializationContext(NbtOps.INSTANCE);
        if (p_324011_.contains("server_data")) {
            VaultServerData.CODEC.parse(dynamicops, p_324011_.get("server_data")).resultOrPartial(LOGGER::error).ifPresent(this.serverData::set);
        }

        if (p_324011_.contains("config")) {
            VaultConfig.CODEC.parse(dynamicops, p_324011_.get("config")).resultOrPartial(LOGGER::error).ifPresent(p_324546_ -> this.config = p_324546_);
        }

        if (p_324011_.contains("shared_data")) {
            VaultSharedData.CODEC.parse(dynamicops, p_324011_.get("shared_data")).resultOrPartial(LOGGER::error).ifPresent(this.sharedData::set);
        }
    }

    @Nullable
    public VaultServerData getServerData() {
        return this.level != null && !this.level.isClientSide ? this.serverData : null;
    }

    public VaultSharedData getSharedData() {
        return this.sharedData;
    }

    public VaultClientData getClientData() {
        return this.clientData;
    }

    public VaultConfig getConfig() {
        return this.config;
    }

    @VisibleForTesting
    public void setConfig(VaultConfig p_324010_) {
        this.config = p_324010_;
    }

    public static final class Client {
        private static final int PARTICLE_TICK_RATE = 20;
        private static final float IDLE_PARTICLE_CHANCE = 0.5F;
        private static final float AMBIENT_SOUND_CHANCE = 0.02F;
        private static final int ACTIVATION_PARTICLE_COUNT = 20;
        private static final int DEACTIVATION_PARTICLE_COUNT = 20;

        public static void tick(Level p_324312_, BlockPos p_323810_, BlockState p_323862_, VaultClientData p_324314_, VaultSharedData p_323914_) {
            p_324314_.updateDisplayItemSpin();
            if (p_324312_.getGameTime() % 20L == 0L) {
                emitConnectionParticlesForNearbyPlayers(p_324312_, p_323810_, p_323862_, p_323914_);
            }

            emitIdleParticles(
                p_324312_, p_323810_, p_323914_, p_323862_.getValue(VaultBlock.OMINOUS) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME
            );
            playIdleSounds(p_324312_, p_323810_, p_323914_);
        }

        public static void emitActivationParticles(
            Level p_323761_, BlockPos p_324150_, BlockState p_324527_, VaultSharedData p_324466_, ParticleOptions p_338551_
        ) {
            emitConnectionParticlesForNearbyPlayers(p_323761_, p_324150_, p_324527_, p_324466_);
            RandomSource randomsource = p_323761_.random;

            for (int i = 0; i < 20; i++) {
                Vec3 vec3 = randomPosInsideCage(p_324150_, randomsource);
                p_323761_.addParticle(ParticleTypes.SMOKE, vec3.x(), vec3.y(), vec3.z(), 0.0, 0.0, 0.0);
                p_323761_.addParticle(p_338551_, vec3.x(), vec3.y(), vec3.z(), 0.0, 0.0, 0.0);
            }
        }

        public static void emitDeactivationParticles(Level p_324419_, BlockPos p_324587_, ParticleOptions p_338582_) {
            RandomSource randomsource = p_324419_.random;

            for (int i = 0; i < 20; i++) {
                Vec3 vec3 = randomPosCenterOfCage(p_324587_, randomsource);
                Vec3 vec31 = new Vec3(randomsource.nextGaussian() * 0.02, randomsource.nextGaussian() * 0.02, randomsource.nextGaussian() * 0.02);
                p_324419_.addParticle(p_338582_, vec3.x(), vec3.y(), vec3.z(), vec31.x(), vec31.y(), vec31.z());
            }
        }

        private static void emitIdleParticles(Level p_324004_, BlockPos p_324516_, VaultSharedData p_324173_, ParticleOptions p_338823_) {
            RandomSource randomsource = p_324004_.getRandom();
            if (randomsource.nextFloat() <= 0.5F) {
                Vec3 vec3 = randomPosInsideCage(p_324516_, randomsource);
                p_324004_.addParticle(ParticleTypes.SMOKE, vec3.x(), vec3.y(), vec3.z(), 0.0, 0.0, 0.0);
                if (shouldDisplayActiveEffects(p_324173_)) {
                    p_324004_.addParticle(p_338823_, vec3.x(), vec3.y(), vec3.z(), 0.0, 0.0, 0.0);
                }
            }
        }

        private static void emitConnectionParticlesForPlayer(Level p_323629_, Vec3 p_324592_, Player p_324428_) {
            RandomSource randomsource = p_323629_.random;
            Vec3 vec3 = p_324592_.vectorTo(p_324428_.position().add(0.0, (double)(p_324428_.getBbHeight() / 2.0F), 0.0));
            int i = Mth.nextInt(randomsource, 2, 5);

            for (int j = 0; j < i; j++) {
                Vec3 vec31 = vec3.offsetRandom(randomsource, 1.0F);
                p_323629_.addParticle(ParticleTypes.VAULT_CONNECTION, p_324592_.x(), p_324592_.y(), p_324592_.z(), vec31.x(), vec31.y(), vec31.z());
            }
        }

        private static void emitConnectionParticlesForNearbyPlayers(Level p_324593_, BlockPos p_324162_, BlockState p_324246_, VaultSharedData p_323584_) {
            Set<UUID> set = p_323584_.getConnectedPlayers();
            if (!set.isEmpty()) {
                Vec3 vec3 = keyholePos(p_324162_, p_324246_.getValue(VaultBlock.FACING));

                for (UUID uuid : set) {
                    Player player = p_324593_.getPlayerByUUID(uuid);
                    if (player != null && isWithinConnectionRange(p_324162_, p_323584_, player)) {
                        emitConnectionParticlesForPlayer(p_324593_, vec3, player);
                    }
                }
            }
        }

        private static boolean isWithinConnectionRange(BlockPos p_324081_, VaultSharedData p_323688_, Player p_324438_) {
            return p_324438_.blockPosition().distSqr(p_324081_) <= Mth.square(p_323688_.connectedParticlesRange());
        }

        private static void playIdleSounds(Level p_323802_, BlockPos p_323510_, VaultSharedData p_324457_) {
            if (shouldDisplayActiveEffects(p_324457_)) {
                RandomSource randomsource = p_323802_.getRandom();
                if (randomsource.nextFloat() <= 0.02F) {
                    p_323802_.playLocalSound(
                        p_323510_,
                        SoundEvents.VAULT_AMBIENT,
                        SoundSource.BLOCKS,
                        randomsource.nextFloat() * 0.25F + 0.75F,
                        randomsource.nextFloat() + 0.5F,
                        false
                    );
                }
            }
        }

        public static boolean shouldDisplayActiveEffects(VaultSharedData p_323681_) {
            return p_323681_.hasDisplayItem();
        }

        private static Vec3 randomPosCenterOfCage(BlockPos p_323493_, RandomSource p_324481_) {
            return Vec3.atLowerCornerOf(p_323493_)
                .add(Mth.nextDouble(p_324481_, 0.4, 0.6), Mth.nextDouble(p_324481_, 0.4, 0.6), Mth.nextDouble(p_324481_, 0.4, 0.6));
        }

        private static Vec3 randomPosInsideCage(BlockPos p_324080_, RandomSource p_324532_) {
            return Vec3.atLowerCornerOf(p_324080_)
                .add(Mth.nextDouble(p_324532_, 0.1, 0.9), Mth.nextDouble(p_324532_, 0.25, 0.75), Mth.nextDouble(p_324532_, 0.1, 0.9));
        }

        private static Vec3 keyholePos(BlockPos p_323572_, Direction p_324503_) {
            return Vec3.atBottomCenterOf(p_323572_).add((double)p_324503_.getStepX() * 0.5, 1.75, (double)p_324503_.getStepZ() * 0.5);
        }
    }

    public static final class Server {
        private static final int UNLOCKING_DELAY_TICKS = 14;
        private static final int DISPLAY_CYCLE_TICK_RATE = 20;
        private static final int INSERT_FAIL_SOUND_BUFFER_TICKS = 15;

        public static void tick(
            ServerLevel p_323841_, BlockPos p_324265_, BlockState p_324343_, VaultConfig p_324129_, VaultServerData p_324579_, VaultSharedData p_324317_
        ) {
            VaultState vaultstate = p_324343_.getValue(VaultBlock.STATE);
            if (shouldCycleDisplayItem(p_323841_.getGameTime(), vaultstate)) {
                cycleDisplayItemFromLootTable(p_323841_, vaultstate, p_324129_, p_324317_, p_324265_);
            }

            BlockState blockstate = p_324343_;
            if (p_323841_.getGameTime() >= p_324579_.stateUpdatingResumesAt()) {
                blockstate = p_324343_.setValue(VaultBlock.STATE, vaultstate.tickAndGetNext(p_323841_, p_324265_, p_324129_, p_324579_, p_324317_));
                if (!p_324343_.equals(blockstate)) {
                    setVaultState(p_323841_, p_324265_, p_324343_, blockstate, p_324129_, p_324317_);
                }
            }

            if (p_324579_.isDirty || p_324317_.isDirty) {
                VaultBlockEntity.setChanged(p_323841_, p_324265_, p_324343_);
                if (p_324317_.isDirty) {
                    p_323841_.sendBlockUpdated(p_324265_, p_324343_, blockstate, 2);
                }

                p_324579_.isDirty = false;
                p_324317_.isDirty = false;
            }
        }

        public static void tryInsertKey(
            ServerLevel p_323533_,
            BlockPos p_323777_,
            BlockState p_324589_,
            VaultConfig p_323660_,
            VaultServerData p_323829_,
            VaultSharedData p_324341_,
            Player p_324373_,
            ItemStack p_324551_
        ) {
            VaultState vaultstate = p_324589_.getValue(VaultBlock.STATE);
            if (canEjectReward(p_323660_, vaultstate)) {
                if (!isValidToInsert(p_323660_, p_324551_)) {
                    playInsertFailSound(p_323533_, p_323829_, p_323777_, SoundEvents.VAULT_INSERT_ITEM_FAIL);
                } else if (p_323829_.hasRewardedPlayer(p_324373_)) {
                    playInsertFailSound(p_323533_, p_323829_, p_323777_, SoundEvents.VAULT_REJECT_REWARDED_PLAYER);
                } else {
                    List<ItemStack> list = resolveItemsToEject(p_323533_, p_323660_, p_323777_, p_324373_);
                    if (!list.isEmpty()) {
                        p_324373_.awardStat(Stats.ITEM_USED.get(p_324551_.getItem()));
                        p_324551_.consume(p_323660_.keyItem().getCount(), p_324373_);
                        unlock(p_323533_, p_324589_, p_323777_, p_323660_, p_323829_, p_324341_, list);
                        p_323829_.addToRewardedPlayers(p_324373_);
                        p_324341_.updateConnectedPlayersWithinRange(p_323533_, p_323777_, p_323829_, p_323660_, p_323660_.deactivationRange());
                    }
                }
            }
        }

        static void setVaultState(
            ServerLevel p_324091_, BlockPos p_324620_, BlockState p_323759_, BlockState p_324027_, VaultConfig p_324140_, VaultSharedData p_323624_
        ) {
            VaultState vaultstate = p_323759_.getValue(VaultBlock.STATE);
            VaultState vaultstate1 = p_324027_.getValue(VaultBlock.STATE);
            p_324091_.setBlock(p_324620_, p_324027_, 3);
            vaultstate.onTransition(p_324091_, p_324620_, vaultstate1, p_324140_, p_323624_, p_324027_.getValue(VaultBlock.OMINOUS));
        }

        static void cycleDisplayItemFromLootTable(
            ServerLevel p_323551_, VaultState p_324221_, VaultConfig p_324332_, VaultSharedData p_323644_, BlockPos p_323602_
        ) {
            if (!canEjectReward(p_324332_, p_324221_)) {
                p_323644_.setDisplayItem(ItemStack.EMPTY);
            } else {
                ItemStack itemstack = getRandomDisplayItemFromLootTable(
                    p_323551_, p_323602_, p_324332_.overrideLootTableToDisplay().orElse(p_324332_.lootTable())
                );
                p_323644_.setDisplayItem(itemstack);
            }
        }

        private static ItemStack getRandomDisplayItemFromLootTable(ServerLevel p_323781_, BlockPos p_324109_, ResourceKey<LootTable> p_336119_) {
            LootTable loottable = p_323781_.getServer().reloadableRegistries().getLootTable(p_336119_);
            LootParams lootparams = new LootParams.Builder(p_323781_)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(p_324109_))
                .create(LootContextParamSets.VAULT);
            List<ItemStack> list = loottable.getRandomItems(lootparams, p_323781_.getRandom());
            return list.isEmpty() ? ItemStack.EMPTY : Util.getRandom(list, p_323781_.getRandom());
        }

        private static void unlock(
            ServerLevel p_323501_,
            BlockState p_323758_,
            BlockPos p_323773_,
            VaultConfig p_324195_,
            VaultServerData p_324600_,
            VaultSharedData p_324277_,
            List<ItemStack> p_324574_
        ) {
            p_324600_.setItemsToEject(p_324574_);
            p_324277_.setDisplayItem(p_324600_.getNextItemToEject());
            p_324600_.pauseStateUpdatingUntil(p_323501_.getGameTime() + 14L);
            setVaultState(p_323501_, p_323773_, p_323758_, p_323758_.setValue(VaultBlock.STATE, VaultState.UNLOCKING), p_324195_, p_324277_);
        }

        private static List<ItemStack> resolveItemsToEject(ServerLevel p_323877_, VaultConfig p_324041_, BlockPos p_324255_, Player p_324347_) {
            LootTable loottable = p_323877_.getServer().reloadableRegistries().getLootTable(p_324041_.lootTable());
            LootParams lootparams = new LootParams.Builder(p_323877_)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(p_324255_))
                .withLuck(p_324347_.getLuck())
                .withParameter(LootContextParams.THIS_ENTITY, p_324347_)
                .create(LootContextParamSets.VAULT);
            return loottable.getRandomItems(lootparams);
        }

        private static boolean canEjectReward(VaultConfig p_323595_, VaultState p_324160_) {
            return p_323595_.lootTable() != BuiltInLootTables.EMPTY && !p_323595_.keyItem().isEmpty() && p_324160_ != VaultState.INACTIVE;
        }

        private static boolean isValidToInsert(VaultConfig p_323488_, ItemStack p_324101_) {
            return ItemStack.isSameItemSameComponents(p_324101_, p_323488_.keyItem()) && p_324101_.getCount() >= p_323488_.keyItem().getCount();
        }

        private static boolean shouldCycleDisplayItem(long p_323548_, VaultState p_323823_) {
            return p_323548_ % 20L == 0L && p_323823_ == VaultState.ACTIVE;
        }

        private static void playInsertFailSound(ServerLevel p_324555_, VaultServerData p_324017_, BlockPos p_324186_, SoundEvent p_350407_) {
            if (p_324555_.getGameTime() >= p_324017_.getLastInsertFailTimestamp() + 15L) {
                p_324555_.playSound(null, p_324186_, p_350407_, SoundSource.BLOCKS);
                p_324017_.setLastInsertFailTimestamp(p_324555_.getGameTime());
            }
        }
    }
}
