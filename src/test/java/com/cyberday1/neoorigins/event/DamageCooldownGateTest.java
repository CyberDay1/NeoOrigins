package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hit reactions skip a hit vanilla's damage cooldown drops after LivingIncomingDamageEvent. */
class DamageCooldownGateTest {

    @Test
    void smallerOrEqualHitInsideTheCooldownIsDropped() {
        assertTrue(DamageCooldownGate.discards(19, 4.0F, false, 2.0F));
        assertTrue(DamageCooldownGate.discards(11, 4.0F, false, 4.0F));
        assertTrue(DamageCooldownGate.discards(15, 4.0F, false, 0.0F));
    }

    @Test
    void largerHitInsideTheCooldownLandsForTheExcess() {
        assertFalse(DamageCooldownGate.discards(19, 4.0F, false, 4.5F));
    }

    @Test
    void secondHalfOfTheCooldownAndNoCooldownLetEveryHitThrough() {
        assertFalse(DamageCooldownGate.discards(10, 4.0F, false, 1.0F));
        assertFalse(DamageCooldownGate.discards(0, 0.0F, false, 0.0F));
    }

    @Test
    void cooldownBypassingSourceAlwaysLands() {
        assertFalse(DamageCooldownGate.discards(19, 4.0F, true, 1.0F));
    }

    @Test
    void helmetScalingIsAppliedBeforeTheComparison() {
        var player = PlayerLifecycle.realPlayer();
        DamageSource anvil = Mockito.mock(DamageSource.class);
        Mockito.when(anvil.is(DamageTypeTags.DAMAGES_HELMET)).thenReturn(true);
        assertEquals(4.0F, DamageCooldownGate.vanillaScaled(player, anvil, 4.0F));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        assertEquals(3.0F, DamageCooldownGate.vanillaScaled(player, anvil, 4.0F));
    }
}
