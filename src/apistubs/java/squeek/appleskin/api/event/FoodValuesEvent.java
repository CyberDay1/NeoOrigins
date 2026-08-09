package squeek.appleskin.api.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/**
 * Compile-only stub of AppleSkin's {@code squeek.appleskin.api.event.FoodValuesEvent}.
 *
 * <p><b>This file is never shipped and never loaded at runtime.</b> It exists so
 * {@link com.cyberday1.neoorigins.compat.appleskin.AppleSkinBridge} can be compiled
 * without pulling AppleSkin off a maven - matching the existing Build A Spell /
 * Cold Sweat / Iron's Spellbooks stubs under {@code src/apistubs/java} (see the
 * {@code apiStubs} source set in build.gradle: compile classpath only, never on
 * the runtime classpath, never in the jar). At runtime the real AppleSkin class
 * loads instead, exactly as a {@code compileOnly} dependency would behave.
 *
 * <p>Descriptors verified with {@code javap} against the real
 * {@code appleskin-neoforge-mc26.1-3.0.9.jar}:
 * <pre>
 * public class squeek.appleskin.api.event.FoodValuesEvent extends net.neoforged.bus.api.Event {
 *   public net.minecraft.world.food.FoodProperties defaultFoodProperties;
 *   public net.minecraft.world.food.FoodProperties modifiedFoodProperties;
 *   public final net.minecraft.world.item.ItemStack itemStack;
 *   public final net.minecraft.world.entity.player.Player player;
 *   public FoodValuesEvent(Player, ItemStack, FoodProperties, FoodProperties);
 * }
 * </pre>
 *
 * <p>AppleSkin posts it on {@code NeoForge.EVENT_BUS} from
 * {@code FoodHelper.query}, whose only callers are the client-side
 * {@code HUDOverlayHandler} and {@code TooltipOverlayHandler} - so it is a
 * <b>logical-client-only</b> event in practice. AppleSkin is released under The
 * Unlicense (public domain), so mirroring these signatures carries no licence
 * obligation.
 *
 * <p>If the bridge ever needs more of the AppleSkin API, mirror those signatures
 * here and re-verify them with {@code javap} against the real jar.
 */
public class FoodValuesEvent extends Event {

    public FoodProperties defaultFoodProperties;
    public FoodProperties modifiedFoodProperties;
    public final ItemStack itemStack;
    public final Player player;

    public FoodValuesEvent(Player player, ItemStack itemStack,
                           FoodProperties defaultFoodProperties,
                           FoodProperties modifiedFoodProperties) {
        this.player = player;
        this.itemStack = itemStack;
        this.defaultFoodProperties = defaultFoodProperties;
        this.modifiedFoodProperties = modifiedFoodProperties;
    }
}
