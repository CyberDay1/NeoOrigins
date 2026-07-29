package com.cyberday1.neoorigins.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@code generic.} attribute-prefix handling on the 1.21.1 branch,
 * where the correct answer is <em>do nothing</em>.
 *
 * <p>1.21.1's {@code net.minecraft.world.entity.ai.attributes.Attributes} still
 * registers {@code generic.armor}, {@code generic.max_health} and
 * {@code generic.movement_speed}; the prefix drop landed in 1.21.2. So the old
 * rule 1 was not just unanchored — on this branch it took a <em>valid</em>
 * attribute id and produced an invalid one. The 26.x branches carry an anchored
 * version of the rule; this branch carries none, and these assertions are what
 * stops it being ported back by reflex.
 *
 * <p>The unanchored damage is pinned here too, because it was never about
 * attributes: a bare {@code (minecraft:)?generic\.(\w+)} sweep also ate sound
 * ids and any {@code generic.} sitting in pack prose.
 */
class LegacyAttributeAnchorTest {

    /** Every one of these must come back byte-identical from the semantic tier. */
    @Test
    void genericPrefixIsNeverStrippedOn1211() {
        for (String command : new String[] {
            // (a) genuine attribute commands — `generic.armor` IS the 1.21.1 id
            "attribute @s generic.armor base set 30",
            "attribute @s minecraft:generic.max_health base set 40",
            "execute as @a at @s run attribute @s minecraft:generic.movement_speed base set 0.2",
            "execute store result score @s Minion_Armor run attribute @s generic.armor get",
            "attribute @e[type=zombie, limit=1] generic.armor modifier value get neoorigins:x",
            // (b) collateral the unanchored sweep used to cause
            "playsound minecraft:entity.generic.extinguish_fire master @a ~ ~ ~ 1 1 0",
            "stopsound @a master minecraft:entity.generic.explode",
            "tellraw @a {\"text\":\"attribute @s generic.armor get\"}",
            "say the generic.armor attribute is fine",
            // (c) attribute ids inside NBT
            "data modify entity @s attributes set value [{id:\"minecraft:generic.armor\",base:10.0d}]",
            "summon zombie ~ ~ ~ {Attributes:[{AttributeName:\"generic.max_health\",Base:40}]}",
        }) {
            assertEquals(command, LegacyCommandRewriter.rewrite(command),
                "1.21.1 spells attributes `generic.x` — nothing here may be rewritten");
            assertEquals(command, LegacyCommandRewriter.rewriteForCompile(command),
                "the unconditional tier must be a no-op here as well");
        }
    }

    /**
     * Rule 2 still fires — 1.21 really did rename the entity attribute list's
     * per-entry key from {@code Name} to {@code id} — but it must carry the
     * {@code generic.} prefix through untouched, for the same reason rule 1 is
     * absent.
     */
    @Test
    void attributeNbtPathRenamesTheKeyAndKeepsThePrefix() {
        assertEquals(
            "data modify entity @s Attributes[{id:\"minecraft:generic.armor\"}].Base set value 30",
            LegacyCommandRewriter.rewrite(
                "data modify entity @s Attributes[{Name:\"generic.armor\"}].Base set value 30"));
    }
}
