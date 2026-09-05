package com.cyberday1.neoorigins.compat;

import com.mojang.brigadier.StringReader;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
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
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.TagParser;
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
 * string comparison, so the emitted syntax is proven rather than assumed. That
 * matters more here than on 1.21.1: the dust/entity_effect colours are stored as
 * packed ints on 26.x, and only {@code ExtraCodecs.RGB_COLOR_CODEC}'s
 * {@code VECTOR3F} alternative keeps the float-list literal legal. Parsing it is
 * the proof; a golden string would not be.
 */
class LegacyMcFunctionRewriteTest {

    /**
     * Colours survive a round trip through {@code ARGB.as8BitChannel}, which
     * <em>floors</em> value*255 — so a re-read component can sit up to 1/255
     * below what was written. Exact float equality is not available on 26.x.
     */
    private static final float CHANNEL = 1.0F / 255.0F;

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
        // 26.x moved item default components out of registration and into a
        // datapack-reload step (ReloadableServerResources binds them via
        // DataComponentInitializers), so after a bare Bootstrap every Item holder
        // is unbound and `new ItemStack(...)` throws "Components not bound yet".
        // Only the stacks built here are used, and only their custom_data is
        // read, so an empty default map is bound rather than running the whole
        // initializer pass — which NeoForge's component validator rejects outside
        // a real reload anyway.
        Items.POTION.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.PAPER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
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

    private static void assertColor(Vector3f expected, Vector3f actual, String what) {
        assertEquals(expected.x(), actual.x(), CHANNEL, what + " red");
        assertEquals(expected.y(), actual.y(), CHANNEL, what + " green");
        assertEquals(expected.z(), actual.z(), CHANNEL, what + " blue");
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

        // 26.x: ItemParticleOption carries an ItemStackTemplate, and only
        // ItemStackTemplate.CODEC's bare-Item.CODEC alternative keeps the plain
        // id legal where 1.21.1 took a bare item outright.
        ItemParticleOption item = assertInstanceOf(ItemParticleOption.class,
            parseParticle(LegacyCommandRewriter.rewrite("particle minecraft:item minecraft:apple ~ ~ ~")));
        assertEquals(Items.APPLE, item.getItem().item().value());
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
     *
     * <p>On 26.x there is a third: {@code DustParticleOptions} stores a packed
     * RGB int, so the float list is only accepted at all because
     * {@code RGB_COLOR_CODEC} is {@code withAlternative(INT, VECTOR3F)}. The
     * emitted literal is therefore the same as 1.21.1's, but for a different
     * reason, and the value comes back 8-bit quantised.
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
        assertColor(new Vector3f(0.5F, 0.7F, 1.0F), opts.getColor(), "dust");
        assertEquals(1.0F, opts.getScale());

        // glacier/ray line 5 — the int/decimal mix that would break a verbatim list.
        DustParticleOptions mixed = assertInstanceOf(DustParticleOptions.class,
            parseParticle(LegacyCommandRewriter.rewriteForCompile(
                "particle minecraft:dust 0 0.8 1 1 ~ ~ ~ 0.5 0.1 0.5 1 1 normal")));
        assertColor(new Vector3f(0.0F, 0.8F, 1.0F), mixed.getColor(), "mixed dust");

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
        assertColor(new Vector3f(1.0F, 0.0F, 0.0F), opts.getFromColor(), "from");
        assertColor(new Vector3f(0.0F, 0.0F, 1.0F), opts.getToColor(), "to");
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
     *
     * <p>26.x packs the colour into an ARGB int; the four-float literal survives
     * only through {@code ARGB_COLOR_CODEC}'s {@code VECTOR4F} alternative, so
     * the alpha channel is read back to confirm the list order is right.
     */
    @Test
    void legacyEntityEffectGainsARequiredColour() {
        // The exact line 20 of mrt_chemist:quick_drink/splash_detonate.
        String rewritten = LegacyCommandRewriter.rewriteForCompile(
            "particle minecraft:entity_effect ~ ~ ~ 1.5 0.5 1.5 0.1 80");
        assertEquals(
            "particle minecraft:entity_effect{color:[1.0f,1.0f,1.0f,1.0f]} ~ ~ ~ 1.5 0.5 1.5 0.1 80",
            rewritten);
        ColorParticleOption colored = assertInstanceOf(ColorParticleOption.class, parseParticle(rewritten));
        assertEquals(1.0F, colored.getAlpha(), CHANNEL);
        assertEquals(1.0F, colored.getRed(), CHANNEL);

        // ambient_entity_effect is still a SimpleParticleType and must not be
        // caught by the entity_effect anchor.
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
        ItemInput result = new ItemParser(registries).parse(reader);
        assertEquals(Items.WARPED_FUNGUS, result.item().value());
        assertEquals(" 1", reader.getRemaining(), "the parser must stop at the count argument");

        // 26.x: DataComponentPatch.get resolves against a prototype rather than
        // returning an Optional, so the patch is read against an empty one —
        // every component asserted here comes from the rewrite, not the item.
        var patch = result.components();
        assertEquals("Magic Bean", patch.get(DataComponentMap.EMPTY, DataComponents.CUSTOM_NAME).getString());
        ItemLore lore = patch.get(DataComponentMap.EMPTY, DataComponents.LORE);
        assertEquals(1, lore.lines().size());
        assertEquals("A mysterious bean with magical properties", lore.lines().getFirst().getString());

        ItemEnchantments ench = patch.get(DataComponentMap.EMPTY, DataComponents.ENCHANTMENTS);
        assertEquals(1, ench.getLevel(registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING)));
        // 26.x: ItemEnchantments lost its show_in_tooltip flag entirely — hiding
        // moved to the separate tooltip_display component, so that is what the
        // HideFlags bit has to land in.
        TooltipDisplay display = patch.get(DataComponentMap.EMPTY, DataComponents.TOOLTIP_DISPLAY);
        assertTrue(display.hiddenComponents().contains(DataComponents.ENCHANTMENTS),
            "HideFlags bit 1 must hide the enchantment list");
    }

    // ── tier three: dead item→tag paths ──────────────────────────────────

    /**
     * The somniabh Sculk Rune: the pack spawns the rune as an item entity and
     * then finds it again by {@code nbt={Item:{tag:{…}}}}, and reads its id back
     * through {@code SelectedItem.tag.…}. On 1.21 an item stack has no
     * {@code tag} field at all, so both spellings parse, run, and resolve to
     * nothing — the rune stays at y=1000 forever and the id reads 0.
     *
     * <p>Asserted against the real {@link NbtPathArgument} and
     * {@link NbtUtils#compareNbt} rather than against golden strings alone, so
     * the emitted path is proven to resolve and the emitted selector blob is
     * proven to match a genuinely 1.21-shaped stack.
     */
    @Test
    void deadItemTagPathsArePointedAtCustomData() throws Exception {
        // A 1.21-shaped rune: free-form pack NBT lives under custom_data.
        CompoundTag custom = new CompoundTag();
        custom.putInt("SomniaBH", 1);
        custom.putInt("SomniaRuneID", 7);
        ItemStack rune = new ItemStack(Items.PAPER);
        rune.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        CompoundTag holder = new CompoundTag();
        // 26.2: ItemStack#save(Provider) is gone; encode through ItemStack.CODEC.
        var nbtOps = registries.createSerializationContext(NbtOps.INSTANCE);
        holder.put("SelectedItem", ItemStack.CODEC.encodeStart(nbtOps, rune).getOrThrow());
        holder.put("Item", ItemStack.CODEC.encodeStart(nbtOps, rune).getOrThrow());

        // 1. the dot form — powers/global/rune.json's execute_command, verbatim.
        String dot = LegacyCommandRewriter.rewriteDeadItemTagPaths(
            "execute store result score @s SomniaBH_Link run data get entity @s SelectedItem.tag.SomniaRuneID");
        assertEquals("execute store result score @s SomniaBH_Link run data get entity @s "
                + "SelectedItem.components.\"minecraft:custom_data\".SomniaRuneID", dot);
        NbtPathArgument.NbtPath path = new NbtPathArgument().parse(new StringReader(
            dot.substring(dot.lastIndexOf(' ') + 1)));
        assertEquals(7, ((NumericTag) path.get(holder).getFirst()).intValue(),
            "the rewritten path must actually resolve against a 1.21 stack");

        // 2. the brace form — carve/rune.mcfunction line 3, verbatim.
        String brace = LegacyCommandRewriter.rewriteDeadItemTagPaths(
            "execute positioned ~ 1000 ~ run tp @e[type=item,sort=nearest,limit=1,"
                + "nbt={Item:{tag:{SomniaBH:1}}}] @s");
        assertEquals("execute positioned ~ 1000 ~ run tp @e[type=item,sort=nearest,limit=1,"
                + "nbt={Item:{components:{\"minecraft:custom_data\":{SomniaBH:1}}}}] @s", brace);
        // 26.2: TagParser.parseTag is gone; use parseCompoundFully.
        CompoundTag selector = TagParser.parseCompoundFully(
            brace.substring(brace.indexOf("nbt=") + 4, brace.indexOf("] @s")));
        assertTrue(NbtUtils.compareNbt(selector, holder, true),
            "the rewritten selector must still match the item entity it was written for");

        // Both spellings on one line — carve/rune.mcfunction line 2, verbatim.
        assertEquals("execute positioned ~ 1000 ~ store result entity @e[type=item,sort=nearest,limit=1,"
                + "nbt={Item:{components:{\"minecraft:custom_data\":{SomniaBH:1}}}}] "
                + "Item.components.\"minecraft:custom_data\".SomniaRuneID int 1 "
                + "run scoreboard players get @s SomniaBH_ID",
            LegacyCommandRewriter.rewriteDeadItemTagPaths(
                "execute positioned ~ 1000 ~ store result entity @e[type=item,sort=nearest,limit=1,"
                    + "nbt={Item:{tag:{SomniaBH:1}}}] Item.tag.SomniaRuneID int 1 "
                    + "run scoreboard players get @s SomniaBH_ID"));

        // 3. a component-backed key would not be found under custom_data, so the
        //    blob is left alone rather than swapped for a differently-dead path.
        String componentBacked = "tp @e[nbt={Item:{tag:{Damage:5,SomniaBH:1}}}] @s";
        assertEquals(componentBacked, LegacyCommandRewriter.rewriteDeadItemTagPaths(componentBacked));

        // 4. the literal characters as *prose* are not a path. This is the whole
        //    reason the rule is quote-aware instead of being folded into
        //    rewriteForCompile — see the tier-two javadoc for the casualties.
        for (String prose : new String[] {
            "tellraw @a {\"text\":\"Item.tag.foo\"}",
            "tellraw @a [{\"text\":\"read Inventory[0].tag.Bar\"},{\"text\":\"!\"}]",
            "title @s actionbar {\"text\":\"SelectedItem.tag.Baz\"}",
            "say Item.tag.foo",
            "me holds Item.tag.foo",
            "data modify block ~ ~ ~ Text1 set value '{\"text\":\"Item.tag.foo\"}'",
        }) {
            assertEquals(prose, LegacyCommandRewriter.rewriteDeadItemTagPaths(prose),
                "quoted / free-text prose must not be rewritten");
        }

        // …and the selector ahead of a free-text tail is still repaired.
        assertEquals("execute if entity @e[nbt={Item:{components:{\"minecraft:custom_data\":{Foo:1}}}}] "
                + "run say Item.tag.foo",
            LegacyCommandRewriter.rewriteDeadItemTagPaths(
                "execute if entity @e[nbt={Item:{tag:{Foo:1}}}] run say Item.tag.foo"));
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
     * {@code generic.} from anything it sees, which mangles the sound id — and on
     * 26.x, where the attribute really is spelled {@code armor}, it would still
     * be the wrong layer to fix that in, because the same pass runs over sound
     * ids, scoreboard names and raw text.
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
            "execute store result score @s Minion_Armor run attribute @s armor get",
            // Already-1.21 item paths: the dead-path tier must be a no-op here.
            "data get entity @s SelectedItem.components.\"minecraft:custom_data\".Foo",
            "tp @e[nbt={Item:{components:{\"minecraft:custom_data\":{tagged:1}}}}] @s",
            // A legacy ArmorItems list is not a compound, so the brace anchor
            // cannot reach it — and SkullOwner is not custom data anyway.
            "summon minecraft:armor_stand ~ ~ ~ {ArmorItems:[{},{},{},"
                + "{id:\"minecraft:player_head\",Count:1b,tag:{SkullOwner:{Name:\"x\"}}}]}",
        }) {
            assertEquals(command, LegacyCommandRewriter.rewriteForCompile(command),
                "already-valid command must not be rewritten");
            assertEquals(command, LegacyCommandRewriter.rewriteDeadItemTagPaths(command),
                "the dead-path tier runs unconditionally too, so it must be a no-op here");
        }
        // Unparseable SNBT is left alone rather than half-converted.
        String broken = "give @s minecraft:apple{this is not: snbt,,} 1";
        assertEquals(broken, LegacyCommandRewriter.rewriteForCompile(broken));
    }
}
