package com.cyberday1.neoorigins.attachment;

import com.cyberday1.neoorigins.attachment.EntityAttachments.MinionOwner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip coverage for the {@code minion_owner} entity attachment — the
 * on-disk carrier of vanilla-pet persistence for tame_mob / tame_target pets.
 * The attachment is serialized into entity NBT via {@link MinionOwner#CODEC},
 * so a save/load cycle is exactly an NbtOps encode → decode: these tests prove
 * the tame state (owner UUID, tracker type key, despawn duration, death
 * backlash) survives that cycle byte-for-byte, and that saves written BEFORE
 * pet persistence (owner-only shape) still decode with safe defaults.
 */
class MinionOwnerCodecTest {

    private static final UUID OWNER = UUID.fromString("d9aad2c9-1f04-4c6f-9c4e-3a1f2b6d8e01");
    private static final String TAMED_KEY = "tamer:tamed";

    @Test
    void fullTameStateRoundTripsThroughNbt() {
        MinionOwner in = MinionOwner.of(OWNER, TAMED_KEY, 36000, 0.5f);
        Tag tag = MinionOwner.CODEC.encodeStart(NbtOps.INSTANCE, in).getOrThrow();
        MinionOwner out = MinionOwner.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(in, out);
        assertTrue(out.isOwnedBy(OWNER));
        assertEquals(Optional.of(TAMED_KEY), out.mobType());
        assertEquals(36000, out.despawnTicks());
        assertEquals(0.5f, out.deathDamage());
    }

    @Test
    void legacyOwnerOnlyShapeDecodesWithDefaults() {
        // Saves written before pet persistence carried ONLY the "owner" field.
        // Simulate one by encoding a full record and stripping the new fields.
        CompoundTag tag = (CompoundTag) MinionOwner.CODEC
            .encodeStart(NbtOps.INSTANCE, MinionOwner.of(OWNER, TAMED_KEY, 36000, 0.5f))
            .getOrThrow();
        tag.remove("mob_type");
        tag.remove("despawn_ticks");
        tag.remove("death_damage");

        MinionOwner out = MinionOwner.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertTrue(out.isOwnedBy(OWNER), "legacy owner field must still resolve");
        // No mob_type → not identifiable as a tamed pet → never rehydrated,
        // matching the pre-persistence behavior for old-world minions.
        assertEquals(Optional.empty(), out.mobType());
        assertEquals(0, out.despawnTicks());
        assertEquals(0.0f, out.deathDamage());
    }

    @Test
    void emptyDefaultRoundTrips() {
        MinionOwner in = MinionOwner.empty();
        Tag tag = MinionOwner.CODEC.encodeStart(NbtOps.INSTANCE, in).getOrThrow();
        MinionOwner out = MinionOwner.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(in, out);
        assertTrue(out.ownerUuid().isEmpty());
    }
}
