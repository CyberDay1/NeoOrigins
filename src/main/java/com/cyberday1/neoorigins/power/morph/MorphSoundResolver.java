package com.cyberday1.neoorigins.power.morph;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.mixin.EntitySwimSoundInvoker;
import com.cyberday1.neoorigins.mixin.LivingEntitySoundInvoker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Works out what a morphed player should sound like: the mob's voice for a
 * morph that names an {@code entity_type}, with any explicit {@code sounds}
 * override layered on top.
 *
 * <p>Consulted from {@code PlayerSoundMixin}, which patches the sound getters on
 * {@code Player} itself. Those getters run on <em>both</em> logical sides, which
 * is exactly why this is common code and why the morph is looked up through
 * {@link MorphState} rather than either side's own store.
 *
 * <p><b>What gets cached, and why it isn't the mob.</b> Asking a mob for its
 * voice means building one, and the obvious move — keep that instance around —
 * would have a static map holding a live {@code Level} long after the world was
 * unloaded. So the throwaway is read once and dropped, and what's cached is the
 * handful of {@link SoundEvent}s it named. Those are registry singletons: no
 * world reference, no side to get wrong in single-player, nothing to clear on
 * shutdown.
 *
 * <p>The one thing that costs is the hurt sound, which vanilla lets a mob pick
 * per damage source. Caching means probing it once, with a generic source, so a
 * mob whose hurt sound varies by damage type always gives its plain answer. In
 * vanilla that is the armoured wolf and nothing else, and a morphed player has
 * no wolf armour to speak of.
 */
public final class MorphSoundResolver {

    /** Everything a morph target has to say, resolved once and kept. */
    private record Voice(
        @Nullable SoundEvent hurt,
        @Nullable SoundEvent death,
        @Nullable SoundEvent fallSmall,
        @Nullable SoundEvent fallBig,
        @Nullable SoundEvent swim,
        @Nullable SoundEvent splash,
        @Nullable SoundEvent splashHighSpeed
    ) {}

    /** Keyed by entity type plus variant nbt, so a small slime squeaks. */
    private static final Map<String, Voice> VOICES = new ConcurrentHashMap<>();

    /** Types that turned out to have no voice; logged once, then never retried. */
    private static final Set<ResourceLocation> VOICELESS = ConcurrentHashMap.newKeySet();

    /** Sound ids a pack asked for that aren't registered; logged once each. */
    private static final Set<ResourceLocation> UNKNOWN_SOUNDS = ConcurrentHashMap.newKeySet();

    private MorphSoundResolver() {}

    // ── What the mixin asks ─────────────────────────────────────────────────

    @Nullable
    public static SoundEvent hurt(Player player) {
        return resolve(player, MorphSounds::hurt, Voice::hurt);
    }

    @Nullable
    public static SoundEvent death(Player player) {
        return resolve(player, MorphSounds::death, Voice::death);
    }

    @Nullable
    public static SoundEvent swim(Player player) {
        return resolve(player, MorphSounds::swim, Voice::swim);
    }

    @Nullable
    public static SoundEvent splash(Player player) {
        return resolve(player, MorphSounds::splash, Voice::splash);
    }

    @Nullable
    public static SoundEvent splashHighSpeed(Player player) {
        return resolve(player, MorphSounds::splashHighSpeed, Voice::splashHighSpeed);
    }

    /**
     * Fall sounds come in a pair, and the two halves resolve independently: a
     * morph may name only the heavy landing and inherit the light one. Whatever
     * neither the override nor the morph target supplies falls back to
     * {@code vanilla}, the pair the player was about to use.
     *
     * @return null when the morph changes neither half, so vanilla's own pair stands
     */
    @Nullable
    public static LivingEntity.Fallsounds fall(Player player, LivingEntity.Fallsounds vanilla) {
        MorphSpec spec = specFor(player);
        if (spec == null) return null;

        SoundEvent small = override(spec, MorphSounds::fallSmall);
        SoundEvent big = override(spec, MorphSounds::fallBig);
        if (small == null || big == null) {
            Voice voice = voiceFor(player, spec);
            if (voice != null) {
                if (small == null) small = voice.fallSmall();
                if (big == null) big = voice.fallBig();
            }
        }
        if (small == null && big == null) return null;
        return new LivingEntity.Fallsounds(
            small == null ? vanilla.small() : small,
            big == null ? vanilla.big() : big);
    }

    // ── Resolution ──────────────────────────────────────────────────────────

    /**
     * The shared shape of every single-sound getter: an explicit override wins,
     * then the morph target's own voice, then null to leave vanilla alone.
     */
    @Nullable
    private static SoundEvent resolve(Player player,
                                      Function<MorphSounds, Optional<ResourceLocation>> field,
                                      Function<Voice, SoundEvent> fromTarget) {
        MorphSpec spec = specFor(player);
        if (spec == null) return null;
        SoundEvent explicit = override(spec, field);
        if (explicit != null) return explicit;
        Voice voice = voiceFor(player, spec);
        return voice == null ? null : fromTarget.apply(voice);
    }

    /**
     * The player's current morph. The client learned it from a sync payload; the
     * server remembers the one it last resolved and broadcast.
     */
    @Nullable
    private static MorphSpec specFor(Player player) {
        return MorphState.of(player);
    }

    @Nullable
    private static SoundEvent override(MorphSpec spec,
                                       Function<MorphSounds, Optional<ResourceLocation>> field) {
        return spec.sounds()
            .flatMap(field)
            .map(MorphSoundResolver::lookup)
            .orElse(null);
    }

    /**
     * A sound-event id as the event itself. An id that isn't registered is a
     * typo in a cosmetic field, so it is reported once and then treated as
     * absent — silencing a player for the rest of the session over it would be a
     * far worse outcome than ignoring it.
     */
    @Nullable
    private static SoundEvent lookup(ResourceLocation id) {
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(id);
        if (event == null && UNKNOWN_SOUNDS.add(id)) {
            NeoOrigins.LOGGER.warn(
                "entity_model: no sound event is registered as '{}' — ignoring that sounds override", id);
        }
        return event;
    }

    @Nullable
    private static Voice voiceFor(Player player, MorphSpec spec) {
        if (!spec.entitySounds()) return null;
        ResourceLocation typeId = spec.entityType().orElse(null);
        if (typeId == null || VOICELESS.contains(typeId)) return null;

        String key = typeId + "|" + spec.nbt().map(CompoundTag::hashCode).orElse(0);
        Voice cached = VOICES.get(key);
        if (cached != null) return cached;

        Voice built = build(player, spec, typeId);
        if (built == null) return null;
        VOICES.put(key, built);
        return built;
    }

    /**
     * Build a throwaway of the morph target, read every sound off it, and let it
     * go. Any failure is permanent for that type: a morph target that can't be
     * built or isn't alive has no voice, and retrying on every point of damage
     * would turn one bad datapack entry into a per-hit cost.
     */
    @Nullable
    private static Voice build(Player player, MorphSpec spec, ResourceLocation typeId) {
        if (player.level() == null) return null;

        Entity donor = MorphDonor.create(player.level(), spec, typeId,
            reason -> markVoiceless(typeId, reason));
        if (donor == null) return null;
        if (!(donor instanceof LivingEntity living)) {
            markVoiceless(typeId, "it is not a living entity, so it has no voice");
            return null;
        }

        try {
            LivingEntitySoundInvoker voice = (LivingEntitySoundInvoker) living;
            EntitySwimSoundInvoker water = (EntitySwimSoundInvoker) living;
            LivingEntity.Fallsounds falls = living.getFallSounds();
            return new Voice(
                voice.neoorigins$getHurtSound(player.damageSources().generic()),
                voice.neoorigins$getDeathSound(),
                falls.small(),
                falls.big(),
                water.neoorigins$getSwimSound(),
                water.neoorigins$getSwimSplashSound(),
                water.neoorigins$getSwimHighSpeedSplashSound());
        } catch (Exception e) {
            // A modded mob is free to assume it is in a world when asked. One
            // that trips over a stand-in gets to keep the player's own voice.
            markVoiceless(typeId, "asking it for its sounds failed: " + e.getMessage());
            return null;
        }
    }

    private static void markVoiceless(ResourceLocation typeId, String reason) {
        if (VOICELESS.add(typeId)) {
            NeoOrigins.LOGGER.warn(
                "entity_model: cannot take sounds from '{}' — {}. Keeping the player's own sounds.",
                typeId, reason);
        }
        VOICES.keySet().removeIf(k -> k.startsWith(typeId + "|"));
    }
}
