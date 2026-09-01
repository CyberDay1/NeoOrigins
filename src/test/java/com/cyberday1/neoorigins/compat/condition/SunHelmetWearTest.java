package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Covers which helmets pay durability for shading a sun-damage origin.
 *
 * <p>The rule under test: a helmet that is innately fire/lava resistant does not
 * wear out from blocking sunlight. It still protects — the player does not burn —
 * it just stops paying for the privilege. In vanilla that is netherite and only
 * netherite; the check is on the {@code minecraft:fire_resistant} component
 * rather than an item list, so a modded helmet that grants itself the same
 * property is covered without any datapack work.
 *
 * <p>Every test here pins {@code helmet_dura_damage_chance} to 1.0. At the
 * shipped 0.07 a single evaluation usually does nothing, so an "it took no
 * damage" assertion would pass 93% of the time on a helmet that is in fact
 * wearing out — the test would be measuring the dice, not the rule.
 */
class SunHelmetWearTest {

    /** A player at clear morning under open sky, wearing {@code helmet}. */
    private static ServerPlayer sunnyPlayerWearing(ItemStack helmet) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDayTime()).thenReturn(1000L);
        when(level.canSeeSky(BlockPos.ZERO)).thenReturn(true);
        when(level.isRaining()).thenReturn(false);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(helmet);
        when(player.getRandom()).thenReturn(RandomSource.create(1234L));
        return player;
    }

    /**
     * Runs one evaluation with helmet protection on and wear guaranteed.
     *
     * <p>{@code ActiveOriginService} is stubbed out because mixins are live in
     * this harness: our own {@code ItemStackHurtAndBreakMixin} intercepts the
     * durability call and asks whether the player owns a
     * {@code prevent_item_damage} power, which walks the origin attachment a
     * mocked player does not have. Stubbing the lookup to its default false says
     * "this player has no such power" — the state we actually want to test
     * against — instead of fabricating an origin to satisfy the cache.
     */
    private static boolean evaluateWithCertainWear(ServerPlayer player) {
        try (MockedStatic<GameplayConfig> config = mockStatic(GameplayConfig.class);
             MockedStatic<ActiveOriginService> origins = mockStatic(ActiveOriginService.class)) {
            config.when(GameplayConfig::sunHelmetProtection).thenReturn(true);
            config.when(GameplayConfig::sunHelmetDuraDamageChance).thenReturn(1.0f);
            return ConditionParser.isExposedToSun(player);
        }
    }

    /**
     * The control. An ordinary helmet must still be worn down, otherwise every
     * assertion below would pass against a build where the durability cost had
     * been removed outright rather than narrowed to fire-resistant helmets.
     */
    @Test
    void anOrdinaryHelmetStillPaysDurability() {
        ItemStack iron = new ItemStack(Items.IRON_HELMET);
        ServerPlayer player = sunnyPlayerWearing(iron);

        assertFalse(evaluateWithCertainWear(player), "an iron helmet must still shade the player");
        assertEquals(1, iron.getDamageValue(),
            "an iron helmet must lose durability for absorbing the burn");
    }

    /** Netherite: vanilla's only innately fire-resistant helmet. */
    @Test
    void aFireResistantHelmetTakesNoDurabilityDamage() {
        ItemStack netherite = new ItemStack(Items.NETHERITE_HELMET);
        ServerPlayer player = sunnyPlayerWearing(netherite);

        assertFalse(evaluateWithCertainWear(player),
            "a netherite helmet must still shade the player");
        assertEquals(0, netherite.getDamageValue(),
            "a fire-resistant helmet must not wear out blocking sunlight");
    }

    /**
     * Repeated exposure, because the interesting failure is a slow leak: one
     * evaluation costing nothing but a hundred costing something.
     */
    @Test
    void aFireResistantHelmetSurvivesProlongedSun() {
        ItemStack netherite = new ItemStack(Items.NETHERITE_HELMET);
        ServerPlayer player = sunnyPlayerWearing(netherite);

        for (int i = 0; i < 100; i++) {
            evaluateWithCertainWear(player);
        }
        assertEquals(0, netherite.getDamageValue(),
            "100 evaluations in full sun must leave a netherite helmet untouched");
    }

    /**
     * The exemption is about durability only. Turning helmet protection off must
     * still burn the player through a netherite helmet — fire resistance is not
     * a licence to ignore the config.
     */
    @Test
    void theExemptionDoesNotOverrideTheProtectionToggle() {
        ItemStack netherite = new ItemStack(Items.NETHERITE_HELMET);
        ServerPlayer player = sunnyPlayerWearing(netherite);

        try (MockedStatic<GameplayConfig> config = mockStatic(GameplayConfig.class)) {
            config.when(GameplayConfig::sunHelmetProtection).thenReturn(false);
            assertTrue(ConditionParser.isExposedToSun(player),
                "helmet_protection = false must burn the player through any helmet");
        }
        assertEquals(0, netherite.getDamageValue(),
            "a helmet that is not protecting must not be charged for it either");
    }
}
