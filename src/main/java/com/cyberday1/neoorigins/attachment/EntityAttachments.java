package com.cyberday1.neoorigins.attachment;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.UUID;

/**
 * Entity-level attachments.
 *
 * <p>{@code minion_owner} — UUID of the player who summoned or tamed this mob,
 * plus the tracker state needed to rebuild a {@code MinionTracker} entry from
 * disk. Persists through dimension changes and server restarts via entity NBT,
 * so the targeting guard and drop-cancellation stay correct even after the
 * in-memory {@link com.cyberday1.neoorigins.service.MinionTracker} state is
 * lost. For tamed pets ({@code mob_type == "tamer:tamed"}) this is the source
 * of truth for vanilla-pet persistence: the tracker rehydrates the entry and
 * reinstalls the owner AI goals whenever the entity loads
 * ({@code MinionTracker.rehydrateTamed}). Summoned minions carry the
 * attachment too (for ownership queries) but are intentionally NOT rehydrated
 * — summons stay session-scoped and die with their summoner's logout/death.
 */
public class EntityAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NeoOrigins.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MinionOwner>> MINION_OWNER =
        ATTACHMENT_TYPES.register("minion_owner", () ->
            AttachmentType.builder(MinionOwner::empty)
                .serialize(MinionOwner.CODEC)
                .build());

    /** Per-LivingEntity mob-origin state. No {@code copyOnDeath} on purpose —
     *  a mob that dies and respawns re-rolls (mob death is final). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MobOriginData>> MOB_ORIGIN_DATA =
        ATTACHMENT_TYPES.register("mob_origin_data", () ->
            AttachmentType.builder(MobOriginData::new)
                .serialize(MobOriginData.CODEC)
                .build());

    /** Transient mount position for the mount power ("centered" or "shoulder").
     *  Not serialized — cleared on dismount or server restart. Attached to the
     *  passenger (rider) entity to inform the positionRider mixin. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> MOUNT_POSITION =
        ATTACHMENT_TYPES.register("mount_position", () ->
            AttachmentType.builder(() -> "")
                .build());

    /** Per-chunk set of player-placed log positions. Attached to {@code ChunkAccess}
     *  (registry is type-agnostic; the chunk holder reads it via {@code getData}).
     *  Drives the player-placed-log exclusion in CropHarvestBonusPower (GitHub #91). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlacedLogs>> PLACED_LOGS =
        ATTACHMENT_TYPES.register("placed_logs", () ->
            AttachmentType.builder(PlacedLogs::empty)
                .serialize(PlacedLogs.CODEC)
                .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static AttachmentType<MinionOwner> minionOwner() {
        return MINION_OWNER.get();
    }

    public static AttachmentType<MobOriginData> mobOriginData() {
        return MOB_ORIGIN_DATA.get();
    }

    public static AttachmentType<PlacedLogs> placedLogs() {
        return PLACED_LOGS.get();
    }

    public static AttachmentType<String> mountPosition() {
        return MOUNT_POSITION.get();
    }

    /**
     * @param ownerUuid    the summoner/tamer, or empty for the default "unowned" value
     * @param mobType      MinionTracker type key ({@code "tamer:tamed"} for pets);
     *                     empty on pre-persistence saves, which therefore never rehydrate
     * @param despawnTicks full despawn duration from the taming power's config —
     *                     re-armed from scratch when the entry is rebuilt on entity load
     * @param deathDamage  backlash damage the owner takes when the minion dies in combat
     */
    public record MinionOwner(Optional<UUID> ownerUuid, Optional<String> mobType,
                              int despawnTicks, float deathDamage) {
        public static final Codec<MinionOwner> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(MinionOwner::ownerUuid),
            // Optional WITHOUT defaults collapsing into the uuid-only shape: saves
            // written before pet persistence carry only "owner" and must decode
            // (mobType empty → not rehydratable, matching the old behavior).
            Codec.STRING.optionalFieldOf("mob_type").forGetter(MinionOwner::mobType),
            Codec.INT.optionalFieldOf("despawn_ticks", 0).forGetter(MinionOwner::despawnTicks),
            Codec.FLOAT.optionalFieldOf("death_damage", 0.0f).forGetter(MinionOwner::deathDamage)
        ).apply(inst, MinionOwner::new));

        public static MinionOwner empty() {
            return new MinionOwner(Optional.empty(), Optional.empty(), 0, 0.0f);
        }

        public static MinionOwner of(UUID uuid, String mobType, int despawnTicks, float deathDamage) {
            return new MinionOwner(Optional.of(uuid), Optional.of(mobType), despawnTicks, deathDamage);
        }

        public boolean isOwnedBy(UUID uuid) {
            return ownerUuid.isPresent() && ownerUuid.get().equals(uuid);
        }

        public boolean isOwned() {
            return ownerUuid.isPresent();
        }
    }
}
