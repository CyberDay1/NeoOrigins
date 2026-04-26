package net.minecraft.client.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class SkinManager {
    static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftSessionService sessionService;
    private final LoadingCache<SkinManager.CacheKey, CompletableFuture<PlayerSkin>> skinCache;
    private final SkinManager.TextureCache skinTextures;
    private final SkinManager.TextureCache capeTextures;
    private final SkinManager.TextureCache elytraTextures;

    public SkinManager(TextureManager p_118812_, Path p_294690_, final MinecraftSessionService p_118814_, final Executor p_294105_) {
        this.sessionService = p_118814_;
        this.skinTextures = new SkinManager.TextureCache(p_118812_, p_294690_, Type.SKIN);
        this.capeTextures = new SkinManager.TextureCache(p_118812_, p_294690_, Type.CAPE);
        this.elytraTextures = new SkinManager.TextureCache(p_118812_, p_294690_, Type.ELYTRA);
        this.skinCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofSeconds(15L))
            .build(new CacheLoader<SkinManager.CacheKey, CompletableFuture<PlayerSkin>>() {
                public CompletableFuture<PlayerSkin> load(SkinManager.CacheKey p_296373_) {
                    return CompletableFuture.<MinecraftProfileTextures>supplyAsync(() -> {
                        Property property = p_296373_.packedTextures();
                        if (property == null) {
                            return MinecraftProfileTextures.EMPTY;
                        } else {
                            MinecraftProfileTextures minecraftprofiletextures = p_118814_.unpackTextures(property);
                            if (minecraftprofiletextures.signatureState() == SignatureState.INVALID) {
                                SkinManager.LOGGER.warn("Profile contained invalid signature for textures property (profile id: {})", p_296373_.profileId());
                            }

                            return minecraftprofiletextures;
                        }
                    }, Util.backgroundExecutor()).thenComposeAsync(p_307130_ -> SkinManager.this.registerTextures(p_296373_.profileId(), p_307130_), p_294105_);
                }
            });
    }

    public Supplier<PlayerSkin> lookupInsecure(GameProfile p_295432_) {
        CompletableFuture<PlayerSkin> completablefuture = this.getOrLoad(p_295432_);
        PlayerSkin playerskin = DefaultPlayerSkin.get(p_295432_);
        return () -> completablefuture.getNow(playerskin);
    }

    public PlayerSkin getInsecureSkin(GameProfile p_294261_) {
        PlayerSkin playerskin = this.getOrLoad(p_294261_).getNow(null);
        return playerskin != null ? playerskin : DefaultPlayerSkin.get(p_294261_);
    }

    public CompletableFuture<PlayerSkin> getOrLoad(GameProfile p_294865_) {
        Property property = this.sessionService.getPackedTextures(p_294865_);
        return this.skinCache.getUnchecked(new SkinManager.CacheKey(p_294865_.getId(), property));
    }

    CompletableFuture<PlayerSkin> registerTextures(UUID p_307544_, MinecraftProfileTextures p_307606_) {
        MinecraftProfileTexture minecraftprofiletexture = p_307606_.skin();
        CompletableFuture<ResourceLocation> completablefuture;
        PlayerSkin.Model playerskin$model;
        if (minecraftprofiletexture != null) {
            completablefuture = this.skinTextures.getOrLoad(minecraftprofiletexture);
            playerskin$model = PlayerSkin.Model.byName(minecraftprofiletexture.getMetadata("model"));
        } else {
            PlayerSkin playerskin = DefaultPlayerSkin.get(p_307544_);
            completablefuture = CompletableFuture.completedFuture(playerskin.texture());
            playerskin$model = playerskin.model();
        }

        String s = Optionull.map(minecraftprofiletexture, MinecraftProfileTexture::getUrl);
        MinecraftProfileTexture minecraftprofiletexture1 = p_307606_.cape();
        CompletableFuture<ResourceLocation> completablefuture1 = minecraftprofiletexture1 != null
            ? this.capeTextures.getOrLoad(minecraftprofiletexture1)
            : CompletableFuture.completedFuture(null);
        MinecraftProfileTexture minecraftprofiletexture2 = p_307606_.elytra();
        CompletableFuture<ResourceLocation> completablefuture2 = minecraftprofiletexture2 != null
            ? this.elytraTextures.getOrLoad(minecraftprofiletexture2)
            : CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(completablefuture, completablefuture1, completablefuture2)
            .thenApply(
                p_307126_ -> new PlayerSkin(
                        completablefuture.join(),
                        s,
                        completablefuture1.join(),
                        completablefuture2.join(),
                        playerskin$model,
                        p_307606_.signatureState() == SignatureState.SIGNED
                    )
            );
    }

    @OnlyIn(Dist.CLIENT)
    static record CacheKey(UUID profileId, @Nullable Property packedTextures) {
    }

    @OnlyIn(Dist.CLIENT)
    static class TextureCache {
        private final TextureManager textureManager;
        private final Path root;
        private final Type type;
        private final Map<String, CompletableFuture<ResourceLocation>> textures = new Object2ObjectOpenHashMap<>();

        TextureCache(TextureManager p_295278_, Path p_294453_, Type p_294220_) {
            this.textureManager = p_295278_;
            this.root = p_294453_;
            this.type = p_294220_;
        }

        public CompletableFuture<ResourceLocation> getOrLoad(MinecraftProfileTexture p_294862_) {
            String s = p_294862_.getHash();
            CompletableFuture<ResourceLocation> completablefuture = this.textures.get(s);
            if (completablefuture == null) {
                completablefuture = this.registerTexture(p_294862_);
                this.textures.put(s, completablefuture);
            }

            return completablefuture;
        }

        private CompletableFuture<ResourceLocation> registerTexture(MinecraftProfileTexture p_295647_) {
            String s = Hashing.sha1().hashUnencodedChars(p_295647_.getHash()).toString();
            ResourceLocation resourcelocation = this.getTextureLocation(s);
            Path path = this.root.resolve(s.length() > 2 ? s.substring(0, 2) : "xx").resolve(s);
            CompletableFuture<ResourceLocation> completablefuture = new CompletableFuture<>();
            HttpTexture httptexture = new HttpTexture(
                path.toFile(),
                p_295647_.getUrl(),
                DefaultPlayerSkin.getDefaultTexture(),
                this.type == Type.SKIN,
                () -> completablefuture.complete(resourcelocation)
            );
            this.textureManager.register(resourcelocation, httptexture);
            return completablefuture;
        }

        private ResourceLocation getTextureLocation(String p_295006_) {
            String s = switch (this.type) {
                case SKIN -> "skins";
                case CAPE -> "capes";
                case ELYTRA -> "elytra";
            };
            return ResourceLocation.withDefaultNamespace(s + "/" + p_295006_);
        }
    }
}
