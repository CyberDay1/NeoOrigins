package com.cyberday1.neoorigins.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@code generic.} attribute-prefix rule to the contexts an attribute
 * id can actually appear in.
 *
 * <p>26.x really did drop the prefix — its
 * {@code net.minecraft.world.entity.ai.attributes.Attributes} registers
 * {@code armor}, {@code max_health}, {@code movement_speed} — so legacy packs
 * writing {@code generic.armor} do need repairing here. (1.21.1 does <em>not</em>:
 * it still registers {@code generic.armor}, the drop landed in 1.21.2, and that
 * branch carries no rule 1 at all.)
 *
 * <p>What the rule may not do is fire anywhere else. It used to be a bare
 * {@code (minecraft:)?generic\.(\w+)} swept over the whole line, which turned
 * {@code playsound minecraft:entity.generic.extinguish_fire} into
 * {@code playsound minecraft:entity.minecraft:extinguish_fire} and would happily
 * edit {@code tellraw} prose.
 */
class LegacyAttributeAnchorTest {

    /** The attribute-id argument of {@code /attribute}, direct and under {@code execute}. */
    @Test
    void attributeCommandIdIsMigrated() {
        assertEquals("attribute @s minecraft:armor base set 30",
            LegacyCommandRewriter.rewrite("attribute @s generic.armor base set 30"));

        assertEquals("attribute @s minecraft:max_health base set 40",
            LegacyCommandRewriter.rewrite("attribute @s minecraft:generic.max_health base set 40"));

        assertEquals("execute as @a at @s run attribute @s minecraft:movement_speed base set 0.2",
            LegacyCommandRewriter.rewrite(
                "execute as @a at @s run attribute @s minecraft:generic.movement_speed base set 0.2"));

        assertEquals("execute store result score @s Minion_Armor run attribute @s minecraft:armor get",
            LegacyCommandRewriter.rewrite(
                "execute store result score @s Minion_Armor run attribute @s generic.armor get"));

        // A bracketed selector may carry spaces; the target must still be skipped whole.
        assertEquals("attribute @e[type=zombie, limit=1] minecraft:armor modifier value get neoorigins:x",
            LegacyCommandRewriter.rewrite(
                "attribute @e[type=zombie, limit=1] generic.armor modifier value get neoorigins:x"));
    }

    /** The NBT fields that hold an attribute id rather than free text. */
    @Test
    void attributeIdsInNbtAreMigrated() {
        assertEquals(
            "data modify entity @s attributes set value [{id:\"minecraft:armor\",base:10.0d}]",
            LegacyCommandRewriter.rewrite(
                "data modify entity @s attributes set value [{id:\"minecraft:generic.armor\",base:10.0d}]"));

        assertEquals(
            "summon zombie ~ ~ ~ {Attributes:[{AttributeName:\"minecraft:max_health\",Base:40}]}",
            LegacyCommandRewriter.rewrite(
                "summon zombie ~ ~ ~ {Attributes:[{AttributeName:\"generic.max_health\",Base:40}]}"));
    }

    /**
     * Everything the unanchored sweep used to damage. These are not hypothetical:
     * origins-plus-plus ships the {@code playsound} line.
     */
    @Test
    void nonAttributeContextsAreUntouched() {
        for (String command : new String[] {
            "playsound minecraft:entity.generic.extinguish_fire master @a ~ ~ ~ 1 1 0",
            "stopsound @a master minecraft:entity.generic.explode",
            "tellraw @a {\"text\":\"attribute @s generic.armor get\"}",
            "say the generic.armor attribute is fine",
            "summon armor_stand ~ ~ ~ {CustomName:'{\"text\":\"generic.armor\"}'}",
        }) {
            assertEquals(command, LegacyCommandRewriter.rewrite(command),
                "rule 1 must only fire where an attribute id can legally sit");
        }
    }

    /**
     * The unconditional tier never carries rule 1, on any branch — the resource
     * layer runs it over lines that already parse.
     */
    @Test
    void compileTierNeverTouchesAttributeIds() {
        for (String command : new String[] {
            "attribute @s generic.armor base set 30",
            "playsound minecraft:entity.generic.extinguish_fire master @a ~ ~ ~ 1 1 0",
            "execute store result score @s Minion_Armor run attribute @s generic.armor get",
        }) {
            assertEquals(command, LegacyCommandRewriter.rewriteForCompile(command));
        }
    }
}
