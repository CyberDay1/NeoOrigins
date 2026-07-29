package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.data.MorphDataManager;
import com.cyberday1.neoorigins.power.morph.MorphSkin;
import com.cyberday1.neoorigins.power.morph.MorphSounds;
import com.cyberday1.neoorigins.power.morph.MorphSpec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;

/**
 * Overrides the player's rendered model with another entity's model — a
 * client-side visual "morph". The server resolves the config into a
 * {@link MorphSpec} and broadcasts it to every client tracking the player,
 * which then renders a dummy entity of that type in place of the player.
 *
 * <p>The target can be named inline ({@code entity_type}) or referenced from a
 * shared definition ({@code morph}, loaded by {@link MorphDataManager} from
 * {@code data/<ns>/neoorigins/morphs/<name>.json}). When both are given the
 * inline fields win, so a pack can reuse a definition and tweak one detail.
 *
 * <p>Variants — sheep colour, cat type, villager profession, slime size — come
 * from partial {@code nbt} applied to the render dummy, so no per-mob code is
 * needed to support them.
 *
 * <p>A {@code skin} block is the other half of the power and works by a
 * completely different route: instead of drawing a stand-in entity, it swaps
 * the textures on the player's own model. That keeps arms, animations and
 * armour working, so it's the right choice for anything humanoid.
 *
 * <p>A morph is audible as well as visible: naming an {@code entity_type} also
 * hands the player that mob's hurt, death, fall and swim sounds. Set
 * {@code entity_sounds} false to keep the player's own voice, or name individual
 * sounds in a {@code sounds} block to override them.
 *
 * <p>The morph is tangible too: a player morphed into an {@code entity_type}
 * collides at that mob's size, eyes included, so a slime fits where a player
 * doesn't and a spider is short enough to walk under a slab. Set
 * {@code hitbox} false for a look-only morph. A morph that would grow the
 * player into a space that isn't there waits until there is one, rather than
 * pushing them through the wall — so squeezing into a one-block gap as a slime
 * works, and growing back inside it politely does nothing until they step out.
 *
 * <pre>{@code
 * { "type": "neoorigins:entity_model", "entity_type": "minecraft:slime" }
 * { "type": "neoorigins:entity_model", "morph": "neoorigins:sheep",
 *   "nbt": { "Color": 14 }, "scale": 1.2 }
 * { "type": "neoorigins:entity_model",
 *   "skin": { "texture": "neoorigins:morph/fox", "model": "slim" } }
 * }</pre>
 */
public class EntityModelPower extends PowerType<EntityModelPower.Config> {

    /** Capability-tag prefix emitted by this power. */
    public static final String CAP_PREFIX = "entity_model:";

    /**
     * Authoring shape of the power. Every morph field is {@link Optional} so an
     * unset field can be told apart from one explicitly set to its default —
     * that distinction is what lets inline fields override a referenced
     * {@code morph} definition. {@link #resolve()} collapses it all into the
     * concrete {@link MorphSpec} the renderer consumes.
     */
    public record Config(
        Optional<String> morph,
        Optional<ResourceLocation> entityType,
        Optional<CompoundTag> nbt,
        Optional<Float> scale,
        Optional<Boolean> hitbox,
        Optional<Boolean> renderHeldItem,
        Optional<Boolean> renderArmor,
        Optional<String> firstPerson,
        Optional<String> arm,
        Optional<MorphSkin> skin,
        Optional<Boolean> entitySounds,
        Optional<MorphSounds> sounds,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("morph").forGetter(Config::morph),
            ResourceLocation.CODEC.optionalFieldOf("entity_type").forGetter(Config::entityType),
            CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(Config::nbt),
            Codec.FLOAT.optionalFieldOf("scale").forGetter(Config::scale),
            Codec.BOOL.optionalFieldOf("hitbox").forGetter(Config::hitbox),
            Codec.BOOL.optionalFieldOf("render_held_item").forGetter(Config::renderHeldItem),
            Codec.BOOL.optionalFieldOf("render_armor").forGetter(Config::renderArmor),
            Codec.STRING.optionalFieldOf("first_person").forGetter(Config::firstPerson),
            Codec.STRING.optionalFieldOf("arm").forGetter(Config::arm),
            MorphSkin.CODEC.optionalFieldOf("skin").forGetter(Config::skin),
            Codec.BOOL.optionalFieldOf("entity_sounds").forGetter(Config::entitySounds),
            MorphSounds.CODEC.optionalFieldOf("sounds").forGetter(Config::sounds),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));

        /**
         * Collapse the referenced morph definition (if any) and the inline
         * overrides into one concrete spec. Inline fields win over the
         * definition; anything neither supplies falls back to
         * {@link MorphSpec#EMPTY}'s defaults.
         */
        public MorphSpec resolve() {
            MorphSpec base = morph.map(MorphDataManager.INSTANCE::resolve).orElse(MorphSpec.EMPTY);
            return new MorphSpec(
                entityType.isPresent() ? entityType : base.entityType(),
                nbt.isPresent() ? nbt : base.nbt(),
                scale.orElseGet(base::scale),
                hitbox.orElseGet(base::hitbox),
                renderHeldItem.orElseGet(base::renderHeldItem),
                renderArmor.orElseGet(base::renderArmor),
                firstPerson.orElseGet(base::firstPerson),
                arm.isPresent() ? arm : base.arm(),
                skin.isPresent() ? skin : base.skin(),
                entitySounds.orElseGet(base::entitySounds),
                sounds.isPresent() ? sounds : base.sounds());
        }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Publishes {@code entity_model:<entity id>} so gameplay conditions can ask
     * what a player is morphed into. The morph's full appearance travels on its
     * own payload, not through this tag.
     */
    @Override
    public Set<String> capabilities(Config config) {
        return config.resolve().entityType()
            .<Set<String>>map(type -> Set.of(CAP_PREFIX + type))
            .orElse(Set.of());
    }
}
