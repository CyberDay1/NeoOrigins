package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DamageResistant;
import org.junit.jupiter.api.BeforeAll;
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
 * it just stops paying for the privilege. The question is asked as
 * {@code ItemStack#canBeHurtBy(lava)} rather than against an item list, so a
 * modded helmet declaring the same resistance is covered without datapack work.
 *
 * <p>Every test here pins {@code helmet_dura_damage_chance} to 1.0. At the
 * shipped 0.07 a single evaluation usually does nothing, so an "it took no
 * damage" assertion would pass 93% of the time on a helmet that is in fact
 * wearing out — the test would be measuring the dice, not the rule.
 *
 * <p>⚠ What is and is not claimed here. Item components are unbound in a bare
 * bootstrap on 26.x, so the two helmets below are given their components by
 * hand and the fireproofing on the netherite one is synthetic. That means this
 * file pins the <b>branch</b> — fireproof helmet spared, ordinary helmet
 * charged — and not the fact that vanilla netherite is fireproof in the first
 * place. That half was read out of the 26.1 sources instead:
 * {@code Items.NETHERITE_HELMET} is built with {@code Item.Properties#fireResistant()},
 * which sets {@code DAMAGE_RESISTANT} to the {@code minecraft:is_fire} damage
 * type tag, and that tag contains {@code minecraft:lava}.
 */
class SunHelmetWearTest {

    /**
     * One damage type standing in for lava, used both as the source the code
     * tests against and as the thing the fireproof helmet resists — so the two
     * agree by construction rather than through a registry lookup no bare
     * bootstrap can serve.
     */
    private static final Holder<DamageType> LAVA =
        Holder.direct(new DamageType("lava", 0.1F));
    private static final DamageSource LAVA_SOURCE = new DamageSource(LAVA);

    private static final int MAX_DAMAGE = 165;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Plain armour: damageable, wears like anything else.
        bind(Items.IRON_HELMET, DataComponentMap.builder()
            .set(DataComponents.MAX_DAMAGE, MAX_DAMAGE)
            .set(DataComponents.DAMAGE, 0)
            .build());
        // Same again plus the resistance netherite carries in vanilla.
        bind(Items.NETHERITE_HELMET, DataComponentMap.builder()
            .set(DataComponents.MAX_DAMAGE, MAX_DAMAGE)
            .set(DataComponents.DAMAGE, 0)
            .set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(HolderSet.direct(LAVA)))
            .build());
    }

    private static void bind(Item item, DataComponentMap components) {
        item.builtInRegistryHolder().bindComponents(components);
    }

    /** A player at clear morning under open sky, wearing {@code helmet}. */
    private static ServerPlayer sunnyPlayerWearing(ItemStack helmet) {
        DamageSources sources = mock(DamageSources.class);
        when(sources.lava()).thenReturn(LAVA_SOURCE);

        ServerLevel level = mock(ServerLevel.class);
        when(level.getDefaultClockTime()).thenReturn(1000L);
        when(level.canSeeSky(BlockPos.ZERO)).thenReturn(true);
        when(level.isRaining()).thenReturn(false);
        when(level.damageSources()).thenReturn(sources);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(helmet);
        when(player.getRandom()).thenReturn(RandomSource.create(1234L));
        // Losing durability fires the item_durability_changed criterion, and
        // 26.x dereferences the advancement tracker before checking whether
        // anything listens. An empty tracker is enough to get past it.
        when(player.getAdvancements()).thenReturn(mock(PlayerAdvancements.class));
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
