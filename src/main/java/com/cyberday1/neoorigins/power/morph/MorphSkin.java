package com.cyberday1.neoorigins.power.morph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * A player-skin morph: the player keeps their own body, and only the textures
 * drawn on it are swapped. This is the {@code skin} half of the
 * {@code neoorigins:entity_model} power, and it is a completely separate
 * mechanism from the {@code entity_type} half — there is no stand-in entity
 * here, because the player IS already being rendered by the right renderer.
 *
 * <p>Every field is optional and layered over whatever the player's real skin
 * resolves to, so a morph can restyle just the cape, or just switch the arm
 * width, without having to restate the body texture.
 *
 * <p>This is a plain data record on purpose: it is authored in a datapack,
 * resolved on the server and synced down, so it cannot mention the client-only
 * {@code PlayerSkin} type. The client turns it into one at render time.
 *
 * @param texture body texture, as an <em>asset id</em> — {@code ns:foo/bar}
 *                resolves to {@code ns:textures/foo/bar.png}, matching how
 *                vanilla addresses skins in data on 26.x
 * @param cape    cape texture, same addressing. Left alone when absent rather
 *                than cleared, so a morph doesn't silently strip a real cape
 * @param elytra  elytra texture, same addressing. When absent the cape texture
 *                is what vanilla falls back to
 * @param model   arm width: {@link #MODEL_SLIM} or {@link #MODEL_WIDE}. Absent
 *                keeps the player's own, which is usually what a texture-only
 *                reskin wants
 */
public record MorphSkin(
    Optional<Identifier> texture,
    Optional<Identifier> cape,
    Optional<Identifier> elytra,
    Optional<String> model
) {

    /** Three-pixel arms (the "Alex" proportions). */
    public static final String MODEL_SLIM = "slim";
    /** Four-pixel arms (the "Steve" proportions). */
    public static final String MODEL_WIDE = "wide";

    public static final Codec<MorphSkin> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.optionalFieldOf("texture").forGetter(MorphSkin::texture),
        Identifier.CODEC.optionalFieldOf("cape").forGetter(MorphSkin::cape),
        Identifier.CODEC.optionalFieldOf("elytra").forGetter(MorphSkin::elytra),
        Codec.STRING.optionalFieldOf("model").forGetter(MorphSkin::model)
    ).apply(inst, MorphSkin::new));

    /** True when this skin would change nothing, and can be skipped entirely. */
    public boolean isEmpty() {
        return texture.isEmpty() && cape.isEmpty() && elytra.isEmpty() && model.isEmpty();
    }

    /**
     * Turn an authored asset id into the texture path it addresses:
     * {@code ns:foo/bar} → {@code ns:textures/foo/bar.png}. Kept here rather
     * than in the client code because 1.21.1 and 26.x need the same answer and
     * only one of them derives it in vanilla.
     */
    public static Identifier texturePath(Identifier assetId) {
        return assetId.withPath(path -> "textures/" + path + ".png");
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeOptional(texture, FriendlyByteBuf::writeIdentifier);
        buf.writeOptional(cape, FriendlyByteBuf::writeIdentifier);
        buf.writeOptional(elytra, FriendlyByteBuf::writeIdentifier);
        buf.writeOptional(model, FriendlyByteBuf::writeUtf);
    }

    public static MorphSkin read(FriendlyByteBuf buf) {
        return new MorphSkin(
            buf.readOptional(FriendlyByteBuf::readIdentifier),
            buf.readOptional(FriendlyByteBuf::readIdentifier),
            buf.readOptional(FriendlyByteBuf::readIdentifier),
            buf.readOptional(FriendlyByteBuf::readUtf));
    }
}
