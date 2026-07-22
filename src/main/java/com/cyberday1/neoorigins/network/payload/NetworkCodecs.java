package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashSet;
import java.util.Set;

/**
 * Reusable read/write fragments for payload shapes that several payloads
 * hand-roll identically.
 *
 * <p>Wire format is deliberately byte-identical to the inlined loops these
 * replace: a {@code VarInt} length prefix followed by that many {@code Utf}
 * strings. Do not change the encoding here without versioning the payloads that
 * use it.
 */
public final class NetworkCodecs {

    private NetworkCodecs() {}

    /**
     * Writes a {@code Set<String>} as a {@code VarInt} size prefix followed by
     * one {@code Utf} per element, in the set's iteration order. Byte-identical to
     * the previous inline {@code writeVarInt(size); for (…) writeUtf(…)} loops in
     * {@code SyncActivePowersPayload} / {@code SyncPlayerPowersPayload}.
     */
    public static void writeStringSet(FriendlyByteBuf buf, Set<String> set) {
        buf.writeVarInt(set.size());
        for (String s : set) {
            buf.writeUtf(s);
        }
    }

    /**
     * Reads a {@code Set<String>} written by {@link #writeStringSet}: a
     * {@code VarInt} count then that many {@code Utf} strings, into a
     * {@link HashSet} pre-sized to the count (matching the originals).
     */
    public static Set<String> readStringSet(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Set<String> set = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            set.add(buf.readUtf());
        }
        return set;
    }
}
