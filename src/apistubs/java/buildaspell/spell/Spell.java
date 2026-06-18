package buildaspell.spell;

/**
 * Compile-only API stub for Build A Spell's {@code Spell} type.
 *
 * <p>This is NOT Build A Spell's real class — it is a minimal signature stand-in
 * so NeoOrigins can compile its {@code cast_spell} bridge without the BaS jar on
 * the classpath (BaS is published only to the author's local maven). It lives in
 * the {@code apiStubs} source set, which is added to main's COMPILE classpath only;
 * it is never on the runtime classpath and never bundled into the mod jar. At
 * runtime the real BaS classes load when the mod is installed, gated behind
 * {@code ModList.isLoaded("buildaspell")}.
 */
public class Spell {}
