package net.minecraft.world.item.component;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

public record ResolvableProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties, GameProfile gameProfile) {
    private static final Codec<ResolvableProfile> FULL_CODEC = RecordCodecBuilder.create(
        p_337950_ -> p_337950_.group(
                    ExtraCodecs.PLAYER_NAME.optionalFieldOf("name").forGetter(ResolvableProfile::name),
                    UUIDUtil.CODEC.optionalFieldOf("id").forGetter(ResolvableProfile::id),
                    ExtraCodecs.PROPERTY_MAP.optionalFieldOf("properties", new PropertyMap()).forGetter(ResolvableProfile::properties)
                )
                .apply(p_337950_, ResolvableProfile::new)
    );
    public static final Codec<ResolvableProfile> CODEC = Codec.withAlternative(
        FULL_CODEC, ExtraCodecs.PLAYER_NAME, p_332623_ -> new ResolvableProfile(Optional.of(p_332623_), Optional.empty(), new PropertyMap())
    );
    public static final StreamCodec<ByteBuf, ResolvableProfile> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(16).apply(ByteBufCodecs::optional),
        ResolvableProfile::name,
        UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional),
        ResolvableProfile::id,
        ByteBufCodecs.GAME_PROFILE_PROPERTIES,
        ResolvableProfile::properties,
        ResolvableProfile::new
    );

    public ResolvableProfile(Optional<String> p_330853_, Optional<UUID> p_332668_, PropertyMap p_331206_) {
        this(p_330853_, p_332668_, p_331206_, createProfile(p_330853_, p_332668_, p_331206_));
    }

    public ResolvableProfile(GameProfile p_332009_) {
        this(Optional.of(p_332009_.getName()), Optional.of(p_332009_.getId()), p_332009_.getProperties(), p_332009_);
    }

    public CompletableFuture<ResolvableProfile> resolve() {
        if (this.isResolved()) {
            return CompletableFuture.completedFuture(this);
        } else {
            return this.id.isPresent() ? SkullBlockEntity.fetchGameProfile(this.id.get()).thenApply(p_332081_ -> {
                GameProfile gameprofile = p_332081_.orElseGet(() -> new GameProfile(this.id.get(), this.name.orElse("")));
                return new ResolvableProfile(gameprofile);
            }) : SkullBlockEntity.fetchGameProfile(this.name.orElseThrow()).thenApply(p_339530_ -> {
                GameProfile gameprofile = p_339530_.orElseGet(() -> new GameProfile(Util.NIL_UUID, this.name.get()));
                return new ResolvableProfile(gameprofile);
            });
        }
    }

    private static GameProfile createProfile(Optional<String> p_331092_, Optional<UUID> p_332678_, PropertyMap p_331544_) {
        GameProfile gameprofile = new GameProfile(p_332678_.orElse(Util.NIL_UUID), p_331092_.orElse(""));
        gameprofile.getProperties().putAll(p_331544_);
        return gameprofile;
    }

    public boolean isResolved() {
        return !this.properties.isEmpty() ? true : this.id.isPresent() == this.name.isPresent();
    }
}
