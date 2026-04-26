package net.minecraft.world.level.block.entity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Services;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class SkullBlockEntity extends BlockEntity {
    private static final String TAG_PROFILE = "profile";
    private static final String TAG_NOTE_BLOCK_SOUND = "note_block_sound";
    private static final String TAG_CUSTOM_NAME = "custom_name";
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static Executor mainThreadExecutor;
    @Nullable
    private static LoadingCache<String, CompletableFuture<Optional<GameProfile>>> profileCacheByName;
    @Nullable
    private static LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> profileCacheById;
    public static final Executor CHECKED_MAIN_THREAD_EXECUTOR = p_294078_ -> {
        Executor executor = mainThreadExecutor;
        if (executor != null) {
            executor.execute(p_294078_);
        }
    };
    @Nullable
    private ResolvableProfile owner;
    @Nullable
    private ResourceLocation noteBlockSound;
    private int animationTickCount;
    private boolean isAnimating;
    @Nullable
    private Component customName;

    public SkullBlockEntity(BlockPos p_155731_, BlockState p_155732_) {
        super(BlockEntityType.SKULL, p_155731_, p_155732_);
    }

    public static void setup(final Services p_222886_, Executor p_222887_) {
        mainThreadExecutor = p_222887_;
        final BooleanSupplier booleansupplier = () -> profileCacheById == null;
        profileCacheByName = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10L))
            .maximumSize(256L)
            .build(new CacheLoader<String, CompletableFuture<Optional<GameProfile>>>() {
                public CompletableFuture<Optional<GameProfile>> load(String p_304652_) {
                    return SkullBlockEntity.fetchProfileByName(p_304652_, p_222886_);
                }
            });
        profileCacheById = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10L))
            .maximumSize(256L)
            .build(new CacheLoader<UUID, CompletableFuture<Optional<GameProfile>>>() {
                public CompletableFuture<Optional<GameProfile>> load(UUID p_339657_) {
                    return SkullBlockEntity.fetchProfileById(p_339657_, p_222886_, booleansupplier);
                }
            });
    }

    static CompletableFuture<Optional<GameProfile>> fetchProfileByName(String p_339683_, Services p_339592_) {
        return p_339592_.profileCache()
            .getAsync(p_339683_)
            .thenCompose(
                p_339545_ -> {
                    LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> loadingcache = profileCacheById;
                    return loadingcache != null && !p_339545_.isEmpty()
                        ? loadingcache.getUnchecked(p_339545_.get().getId()).thenApply(p_339543_ -> p_339543_.or(() -> p_339545_))
                        : CompletableFuture.completedFuture(Optional.empty());
                }
            );
    }

    static CompletableFuture<Optional<GameProfile>> fetchProfileById(UUID p_339663_, Services p_339676_, BooleanSupplier p_339631_) {
        return CompletableFuture.supplyAsync(() -> {
            if (p_339631_.getAsBoolean()) {
                return Optional.empty();
            } else {
                ProfileResult profileresult = p_339676_.sessionService().fetchProfile(p_339663_, true);
                return Optional.ofNullable(profileresult).map(ProfileResult::profile);
            }
        }, Util.backgroundExecutor());
    }

    public static void clear() {
        mainThreadExecutor = null;
        profileCacheByName = null;
        profileCacheById = null;
    }

    @Override
    protected void saveAdditional(CompoundTag p_187518_, HolderLookup.Provider p_324418_) {
        super.saveAdditional(p_187518_, p_324418_);
        if (this.owner != null) {
            p_187518_.put("profile", ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, this.owner).getOrThrow());
        }

        if (this.noteBlockSound != null) {
            p_187518_.putString("note_block_sound", this.noteBlockSound.toString());
        }

        if (this.customName != null) {
            p_187518_.putString("custom_name", Component.Serializer.toJson(this.customName, p_324418_));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag p_155745_, HolderLookup.Provider p_323876_) {
        super.loadAdditional(p_155745_, p_323876_);
        if (p_155745_.contains("profile")) {
            ResolvableProfile.CODEC
                .parse(NbtOps.INSTANCE, p_155745_.get("profile"))
                .resultOrPartial(p_332637_ -> LOGGER.error("Failed to load profile from player head: {}", p_332637_))
                .ifPresent(this::setOwner);
        }

        if (p_155745_.contains("note_block_sound", 8)) {
            this.noteBlockSound = ResourceLocation.tryParse(p_155745_.getString("note_block_sound"));
        }

        if (p_155745_.contains("custom_name", 8)) {
            this.customName = parseCustomNameSafe(p_155745_.getString("custom_name"), p_323876_);
        } else {
            this.customName = null;
        }
    }

    public static void animation(Level p_261710_, BlockPos p_262153_, BlockState p_262021_, SkullBlockEntity p_261594_) {
        if (p_262021_.hasProperty(SkullBlock.POWERED) && p_262021_.getValue(SkullBlock.POWERED)) {
            p_261594_.isAnimating = true;
            p_261594_.animationTickCount++;
        } else {
            p_261594_.isAnimating = false;
        }
    }

    public float getAnimation(float p_262053_) {
        return this.isAnimating ? (float)this.animationTickCount + p_262053_ : (float)this.animationTickCount;
    }

    @Nullable
    public ResolvableProfile getOwnerProfile() {
        return this.owner;
    }

    @Nullable
    public ResourceLocation getNoteBlockSound() {
        return this.noteBlockSound;
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p_323711_) {
        return this.saveCustomOnly(p_323711_);
    }

    public void setOwner(@Nullable ResolvableProfile p_332738_) {
        synchronized (this) {
            this.owner = p_332738_;
        }

        this.updateOwnerProfile();
    }

    private void updateOwnerProfile() {
        if (this.owner != null && !this.owner.isResolved()) {
            this.owner.resolve().thenAcceptAsync(p_332638_ -> {
                this.owner = p_332638_;
                this.setChanged();
            }, CHECKED_MAIN_THREAD_EXECUTOR);
        } else {
            this.setChanged();
        }
    }

    public static CompletableFuture<Optional<GameProfile>> fetchGameProfile(String p_295932_) {
        LoadingCache<String, CompletableFuture<Optional<GameProfile>>> loadingcache = profileCacheByName;
        return loadingcache != null && StringUtil.isValidPlayerName(p_295932_)
            ? loadingcache.getUnchecked(p_295932_)
            : CompletableFuture.completedFuture(Optional.empty());
    }

    public static CompletableFuture<Optional<GameProfile>> fetchGameProfile(UUID p_339604_) {
        LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> loadingcache = profileCacheById;
        return loadingcache != null ? loadingcache.getUnchecked(p_339604_) : CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput p_338654_) {
        super.applyImplicitComponents(p_338654_);
        this.setOwner(p_338654_.get(DataComponents.PROFILE));
        this.noteBlockSound = p_338654_.get(DataComponents.NOTE_BLOCK_SOUND);
        this.customName = p_338654_.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder p_338880_) {
        super.collectImplicitComponents(p_338880_);
        p_338880_.set(DataComponents.PROFILE, this.owner);
        p_338880_.set(DataComponents.NOTE_BLOCK_SOUND, this.noteBlockSound);
        p_338880_.set(DataComponents.CUSTOM_NAME, this.customName);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag p_331773_) {
        super.removeComponentsFromTag(p_331773_);
        p_331773_.remove("profile");
        p_331773_.remove("note_block_sound");
        p_331773_.remove("custom_name");
    }
}
