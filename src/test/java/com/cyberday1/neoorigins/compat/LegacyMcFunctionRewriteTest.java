package com.cyberday1.neoorigins.compat;

import com.mojang.brigadier.StringReader;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for issue #118: the Fairytale pack ships 1.20-era command syntax
 * inside its {@code .mcfunction} files, and 1.21 retired both forms outright —
 * so the functions failed to <em>compile</em> during reload rather than failing
 * at run time, which is why the {@code CommandEvent} rewrite hook never saw them.
 *
 * <p>Both new rules are asserted by feeding the rewritten text through the real
 * vanilla parsers ({@link ParticleArgument}, {@link ItemParser}) rather than by
 * string comparison, so the emitted syntax is proven rather than assumed.
 */
class LegacyMcFunctionRewriteTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
    }

    private static ParticleOptions parseParticle(String command) {
        StringReader reader = new StringReader(command);
        reader.setCursor("particle ".length());
        try {
            return ParticleArgument.readParticle(reader, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        } catch (Exception e) {
            throw new AssertionError("vanilla rejected the rewritten particle: " + command, e);
        }
    }

    // ── rule 6: positional particle arguments ────────────────────────────

    /** Anchored on the exact line 11 of fairytale:powers/remove_vine_segment. */
    @Test
    void positionalParticleArgumentsBecomeInlineOptions() {
        String rewritten = LegacyCommandRewriter.rewrite(
            "particle minecraft:block minecraft:twisting_vines ~ ~ ~ 0.2 0.2 0.2 0.05 5");
        assertEquals(
            "particle minecraft:block{block_state:\"minecraft:twisting_vines\"} ~ ~ ~ 0.2 0.2 0.2 0.05 5",
            rewritten);

        BlockParticleOption opts = assertInstanceOf(BlockParticleOption.class, parseParticle(rewritten));
        assertEquals(Blocks.TWISTING_VINES.defaultBlockState(), opts.getState());

        // The rest of the family: the other block-arg types, the blockstate
        // variant, and the item-arg type — all the same positional shape.
        BlockParticleOption falling = assertInstanceOf(BlockParticleOption.class,
            parseParticle(LegacyCommandRewriter.rewrite("particle minecraft:falling_dust minecraft:sand ~ ~ ~")));
        assertEquals(Blocks.SAND.defaultBlockState(), falling.getState());

        BlockParticleOption stated = assertInstanceOf(BlockParticleOption.class,
            parseParticle(LegacyCommandRewriter.rewrite(
                "particle minecraft:block_marker minecraft:twisting_vines[age=5] ~ ~ ~")));
        assertEquals(5, stated.getState().getValue(
            net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_25));

        ItemParticleOption item = assertInstanceOf(ItemParticleOption.class,
            parseParticle(LegacyCommandRewriter.rewrite("particle minecraft:item minecraft:apple ~ ~ ~")));
        assertEquals(Items.APPLE, item.getItem().getItem());
    }

    /**
     * The dust family — 26 of the 28 origins-plus-plus load failures.
     *
     * <p>Two traps are asserted deliberately. Leading-dot floats ({@code .5},
     * {@code .7}) are legal in a 1.20 positional argument and legal to
     * {@code TagParser} too, but they type as <em>doubles</em> — and NBT lists
     * are homogeneous, so the verbatim {@code [0,0.8,1]} of glacier/ray would be
     * a mixed int/double list that never parses. Every component therefore has
     * to be re-emitted with the same explicit float suffix.
     */
    @Test
    void positionalDustArgumentsBecomeInlineOptions() {
        // The exact line 1 of origins-plus-plus:ice-king/frostwalker_fancy.
        String rewritten = LegacyCommandRewriter.rewriteForCompile(
            "execute at @s run particle minecraft:dust .5 .7 1 1 ~ ~ ~ 0.3 0.2 0.3 0 20 normal");
        assertEquals(
            "execute at @s run particle minecraft:dust{color:[0.5f,0.7f,1.0f],scale:1.0f}"
                + " ~ ~ ~ 0.3 0.2 0.3 0 20 normal",
            rewritten);

        DustParticleOptions opts = assertInstanceOf(DustParticleOptions.class,
            parseParticle(rewritten.substring("execute at @s run ".length())));
        assertEquals(new Vector3f(0.5F, 0.7F, 1.0F), opts.getColor());
        assertEquals(1.0F, opts.getScale());

        // glacier/ray line 5 — the int/decimal mix that would break a verbatim list.
        DustParticleOptions mixed = assertInstanceOf(DustParticleOptions.class,
            parseParticle(LegacyCommandRewriter.rewriteForCompile(
                "particle minecraft:dust 0 0.8 1 1 ~ ~ ~ 0.5 0.1 0.5 1 1 normal")));
        assertEquals(new Vector3f(0.0F, 0.8F, 1.0F), mixed.getColor());

        // ScalableParticleOptionsBase's codec *validates* [0.01,4.0] before its
        // constructor would clamp, so an out-of-range legacy size has to be
        // clamped on the way out or the line still fails to parse.
        DustParticleOptions clamped = assertInstanceOf(DustParticleOptions.class,
            parseParticle(LegacyCommandRewriter.rewriteForCompile("particle minecraft:dust 1 0 0 9 ~ ~ ~")));
        assertEquals(4.0F, clamped.getScale());
    }

    /**
     * {@code dust_color_transition}, whose 1.20 order puts the size <em>between</em>
     * the two colours. No pack in the test set ships one, so this is
     * generalisation rather than a reproduction — which is exactly why it is
     * parsed rather than string-compared.
     */
    @Test
    void positionalDustColorTransitionArgumentsBecomeInlineOptions() {
        String rewritten = LegacyCommandRewriter.rewriteForCompile(
            "particle minecraft:dust_color_transition 1 0 0 .5 0 0 1 ~ ~ ~ 0 0 0 0 10");
        DustColorTransitionOptions opts =
            assertInstanceOf(DustColorTransitionOptions.class, parseParticle(rewritten));
        assertEquals(new Vector3f(1.0F, 0.0F, 0.0F), opts.getFromColor());
        assertEquals(new Vector3f(0.0F, 0.0F, 1.0F), opts.getToColor());
        assertEquals(0.5F, opts.getScale());

        // dust_pillar is a *block* particle and must keep going down rule 6's
        // block-argument path rather than being eaten by the dust pattern.
        assertInstanceOf(BlockParticleOption.class, parseParticle(
            LegacyCommandRewriter.rewriteForCompile("particle minecraft:dust_pillar minecraft:sand ~ ~ ~")));
    }

    /**
     * {@code entity_effect} took no arguments at all on 1.20 and takes a required
     * colour on 1.21, so there is nothing positional to carry over — the value is
     * invented (see {@code ENTITY_EFFECT_COLOR}). All this can prove is that the
     * emitted line parses and that the legacy trailing arguments stay put.
     */
    @Test
    void legacyEntityEffectGainsARequiredColour() {
        // The exact line 20 of mrt_chemist:quick_drink/splash_detonate.
        String rewritten = LegacyCommandRewriter.rewriteForCompile(
            "particle minecraft:entity_effect ~ ~ ~ 1.5 0.5 1.5 0.1 80");
        assertEquals(
            "particle minecraft:entity_effect{color:[1.0f,1.0f,1.0f,1.0f]} ~ ~ ~ 1.5 0.5 1.5 0.1 80",
            rewritten);
        assertInstanceOf(ColorParticleOption.class, parseParticle(rewritten));

        // ambient_entity_effect is still a SimpleParticleType on 1.21.1 and must
        // not be caught by the entity_effect anchor.
        String ambient = "particle minecraft:ambient_entity_effect ~ ~ ~ 1 1 1 0 5";
        assertEquals(ambient, LegacyCommandRewriter.rewriteForCompile(ambient));
    }

    // ── rule 5b: legacy item NBT after `clear` → item predicate ──────────

    /**
     * {@code clear} takes an {@code ItemPredicateArgument}, not an item stack, so
     * the components form rule 5 emits would be an equality test per component
     * instead of the subset match the pack meant. All 7 mrt_chemist NBT failures
     * and origins-plus-plus:deathsworn/loop are this shape.
     */
    @Test
    void clearItemNbtBecomesACustomDataPredicate() throws Exception {
        // The exact line 3 of mrt_chemist:brewmaster/cinder_flour/replace_potion.
        String rewritten = LegacyCommandRewriter.rewriteForCompile(
            "clear @s minecraft:potion{ChemistBrewmasterPendingCinderFlour:1b} 1");
        assertEquals(
            "clear @s minecraft:potion[minecraft:custom_data~{ChemistBrewmasterPendingCinderFlour:1b}] 1",
            rewritten);

        // Prove it against the real argument type, and prove it still *matches*.
        StringReader reader = new StringReader(rewritten.substring("clear @s ".length()));
        ItemPredicateArgument.Result predicate =
            ItemPredicateArgument.itemPredicate(CommandBuildContext.simple(registries, FeatureFlags.VANILLA_SET))
                .parse(reader);
        assertEquals(" 1", reader.getRemaining(), "the parser must stop at the maxCount argument");

        CompoundTag marker = new CompoundTag();
        marker.putByte("ChemistBrewmasterPendingCinderFlour", (byte) 1);
        ItemStack tagged = new ItemStack(Items.POTION);
        tagged.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        assertTrue(predicate.test(tagged), "the rewritten predicate must match the tagged stack");
        assertFalse(predicate.test(new ItemStack(Items.POTION)), "and must not match an untagged one");

        // A blob whose keys moved to real components on 1.21 would not be found
        // under custom_data, so it is left alone rather than mis-translated.
        String componentBacked = "clear @s minecraft:potion{Potion:\"minecraft:healing\"} 1";
        assertEquals(componentBacked, LegacyCommandRewriter.rewriteForCompile(componentBacked));
    }

    // ── rule 5: item NBT → components ────────────────────────────────────

    /** The exact line 5 of fairytale:powers/magic_beans. */
    @Test
    void giveItemNbtBecomesComponents() throws Exception {
        String rewritten = LegacyCommandRewriter.rewrite(
            "give @s minecraft:warped_fungus{display:{Name:'{\"text\":\"Magic Bean\",\"color\":\"aqua\",\"italic\":false}',"
                + "Lore:['{\"text\":\"A mysterious bean with magical properties\",\"color\":\"gray\",\"italic\":false}']},"
                + "Enchantments:[{id:\"minecraft:unbreaking\",lvl:1}],HideFlags:1} 1");

        assertFalse(rewritten.contains("{display:"), "the trailing-NBT form must be gone: " + rewritten);
        assertTrue(rewritten.endsWith(" 1"), "the item count must survive: " + rewritten);

        // Prove it against the real parser, not against a golden string.
        StringReader reader = new StringReader(rewritten.substring("give @s ".length()));
        ItemParser.ItemResult result = new ItemParser(registries).parse(reader);
        assertEquals(Items.WARPED_FUNGUS, result.item().value());
        assertEquals(" 1", reader.getRemaining(), "the parser must stop at the count argument");

        var patch = result.components();
        assertEquals("Magic Bean", patch.get(DataComponents.CUSTOM_NAME).orElseThrow().getString());
        ItemLore lore = patch.get(DataComponents.LORE).orElseThrow();
        assertEquals(1, lore.lines().size());
        assertEquals("A mysterious bean with magical properties", lore.lines().getFirst().getString());

        ItemEnchantments ench = patch.get(DataComponents.ENCHANTMENTS).orElseThrow();
        assertEquals(1, ench.getLevel(registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING)));
        // ItemEnchantments has no showInTooltip getter; equality covers the flag.
        assertEquals(ench.withTooltip(false), ench,
            "HideFlags bit 1 must become show_in_tooltip:false");
    }

    // ── no regressions on anything already valid ─────────────────────────

    /**
     * A rewrite that breaks a working command is worse than the bug it fixes,
     * and the resource layer rewrites <em>unconditionally</em> — so every
     * already-1.21 shape here must come back byte-identical from
     * {@code rewriteForCompile}.
     *
     * <p>The last two entries are not hypothetical: origins-plus-plus ships both,
     * and routing the resource layer through the full {@code rewrite} chain broke
     * four of its functions that had loaded fine before. Rule 1 strips
     * {@code generic.} from anything it sees, which mangles the sound id and
     * un-prefixes an attribute id that 1.21.1 still spells {@code generic.armor}
     * (the un-prefixing landed in 1.21.2, not here).
     */
    @Test
    void modernAndUnrelatedCommandsAreUntouched() {
        for (String command : new String[] {
            "particle minecraft:block{block_state:\"minecraft:stone\"} ~ ~ ~ 1 1 1 0 5",
            "particle minecraft:warped_spore ~ ~ ~ 0.2 0.2 0.2 0 5",
            "particle minecraft:dust{color:[1.0f,0.0f,0.0f],scale:1.0f} ~ ~ ~",
            "particle minecraft:dust_color_transition{from_color:[1.0f,0.0f,0.0f],"
                + "to_color:[0.0f,0.0f,1.0f],scale:1.0f} ~ ~ ~",
            "particle minecraft:entity_effect{color:[1.0f,1.0f,1.0f,1.0f]} ~ ~ ~ 1 1 1 0 5",
            "particle minecraft:dust_pillar{block_state:\"minecraft:sand\"} ~ ~ ~",
            "give @s minecraft:apple[minecraft:custom_name={\"text\":\"Hi\"}] 1",
            "give @s minecraft:apple 3",
            "clear @s minecraft:potion[minecraft:custom_data~{Foo:1b}] 1",
            "clear @s minecraft:stone",
            "clear @s",
            "setblock ~ ~ ~ minecraft:twisting_vines[age=25]",
            "summon minecraft:marker ~ ~ ~ {Tags:[\"vine_marker\"]}",
            "execute as @a[scores={vine_height=0..14}] at @s run function fairytale:powers/place_vine_segment",
            "scoreboard players set #-1 const -1",
            "tellraw @a {\"text\":\"Fairytale Origins Loaded\",\"color\":\"green\"}",
            "playsound minecraft:entity.generic.extinguish_fire master @a ~ ~ ~ 1 1 0",
            "execute store result score @s Minion_Armor run attribute @s generic.armor get",
        }) {
            assertEquals(command, LegacyCommandRewriter.rewriteForCompile(command),
                "already-valid command must not be rewritten");
        }
        // Unparseable SNBT is left alone rather than half-converted.
        String broken = "give @s minecraft:apple{this is not: snbt,,} 1";
        assertEquals(broken, LegacyCommandRewriter.rewriteForCompile(broken));
    }
}
