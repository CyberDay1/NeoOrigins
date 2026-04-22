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
 * <p>{@code minion_owner} — UUID of the player who summoned or tamed this mob.
 * Persists through dimension changes and server restarts via entity NBT, so
 * the targeting guard and drop-cancellation stay correct even after the
 * in-memory {@link com.cyberday1.neoorigins.service.MinionTracker} state is
 * lost. The in-memory tracker still handles caps and despawn timers; this
 * attachment is only for "is this mob anybody's minion?" queries.
 */
public class EntityAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NeoOrigins.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MinionOwner>> MINION_OWNER =
        ATTACHMENT_TYPES.register("minion_owner", () ->
            AttachmentType.builder(MinionOwner::empty)
                .serialize(MinionOwner.CODEC)
                .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static AttachmentType<MinionOwner> minionOwner() {
        return MINION_OWNER.get();
    }

    public record MinionOwner(Optional<UUID> ownerUuid) {
        public static final Codec<MinionOwner> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(MinionOwner::ownerUuid)
        ).apply(inst, MinionOwner::new));

        public static MinionOwner empty() {
            return new MinionOwner(Optional.empty());
        }

        public static MinionOwner of(UUID uuid) {
            return new MinionOwner(Optional.of(uuid));
        }

        public boolean isOwnedBy(UUID uuid) {
            return ownerUuid.isPresent() && ownerUuid.get().equals(uuid);
        }

        public boolean isOwned() {
            return ownerUuid.isPresent();
        }
    }
}
