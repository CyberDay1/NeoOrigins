package com.cyberday1.neoorigins.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression for issue #110 fix 2: Origins++'s Broodmother web_shot fires an
 * origins:fire_projectile with an SNBT {@code tag} whose falling_block
 * BlockState names {@code origins:temporary_cobweb} — a block the original
 * Origins mod registered in code. parseFireProjectile used to drop the tag
 * entirely (spawning a bare SAND falling_block); it now parses the SNBT at
 * load time and runs it through {@link LegacyBlockIds#remapNbt} so the id
 * resolves to NeoOrigins' own temporary_cobweb block.
 */
class LegacyBlockIdsTest {

    @Test
    void remapsTemporaryCobwebId() {
        assertEquals("neoorigins:temporary_cobweb",
            LegacyBlockIds.remap("origins:temporary_cobweb"));
    }

    @Test
    void unmappedIdsPassThroughUnchanged() {
        assertEquals("minecraft:cobweb", LegacyBlockIds.remap("minecraft:cobweb"));
        assertEquals("origins:not_a_real_block", LegacyBlockIds.remap("origins:not_a_real_block"));
        assertNull(LegacyBlockIds.remap(null));
    }

    /**
     * The exact web_shot tag from Origins++ 2.4 (broodmother/master_of_webs
     * sub-power), run through the same TagParser + remapNbt pipeline as
     * parseFireProjectile's load-time path.
     */
    @Test
    void webShotSnbtRemapsBlockStateName() throws Exception {
        CompoundTag tag = TagParser.parseCompoundFully(
            "{BlockState:{Name:\"origins:temporary_cobweb\"},NoGravity:0b,Time:10,HurtEntities:1b}");
        LegacyBlockIds.remapNbt(tag);

        assertEquals("neoorigins:temporary_cobweb",
            tag.getCompoundOrEmpty("BlockState").getStringOr("Name", ""),
            "nested BlockState.Name must be remapped in place");
        // Sibling fields ride along untouched.
        assertEquals(10, tag.getIntOr("Time", 0));
        assertEquals((byte) 1, tag.getByteOr("HurtEntities", (byte) 0));
        assertEquals((byte) 0, tag.getByteOr("NoGravity", (byte) -1));
    }

    @Test
    void remapNbtWalksListsAndLeavesOtherStringsAlone() {
        CompoundTag inner = new CompoundTag();
        inner.putString("Name", "origins:temporary_cobweb");
        ListTag compounds = new ListTag();
        compounds.add(inner);
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("origins:temporary_cobweb"));
        strings.add(StringTag.valueOf("minecraft:stone"));
        CompoundTag root = new CompoundTag();
        root.put("Compounds", compounds);
        root.put("Strings", strings);
        root.putString("CustomName", "origins:web_shot"); // not a mapped id

        LegacyBlockIds.remapNbt(root);

        assertEquals("neoorigins:temporary_cobweb",
            ((CompoundTag) ((ListTag) root.get("Compounds")).get(0)).getStringOr("Name", ""));
        ListTag outStrings = (ListTag) root.get("Strings");
        assertEquals("neoorigins:temporary_cobweb", ((StringTag) outStrings.get(0)).value());
        assertEquals("minecraft:stone", ((StringTag) outStrings.get(1)).value());
        assertEquals("origins:web_shot", root.getStringOr("CustomName", ""));
    }
}
