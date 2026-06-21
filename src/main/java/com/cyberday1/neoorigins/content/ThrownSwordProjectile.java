package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown spectral sword: a single blade hurled along the caster's aim that
 * flies under real physics and reads as a wuxia "flying sword". It carries no
 * intrinsic impact behaviour — the payoff is the {@code on_hit_action}
 * registered by {@link com.cyberday1.neoorigins.compat.action.BuiltinActions
 * spawn_projectile}, which (for the "Ten Thousand Swords" cast) fires a
 * {@code spawn_projectile_rain} with {@code origin: "impact"} so the storm
 * centres exactly where this sword lands.
 *
 * <p>Physics (gravity, drag, collision, auto-discard on impact) are inherited
 * from {@link AbstractNeoProjectile}. The flight visual is the spectral-sword
 * baked mesh drawn by {@code ThrownSwordRenderer}; {@link #getVisualItem()} is
 * only the fallback if that renderer isn't registered.
 */
public class ThrownSwordProjectile extends AbstractNeoProjectile {

    public ThrownSwordProjectile(EntityType<? extends ThrownSwordProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getVisualItem() {
        // Fallback only — the custom mesh renderer normally draws the spectral
        // blade. An iron sword at least reads as a thrown blade if it isn't.
        return Items.IRON_SWORD;
    }

    @Override
    protected void onImpact(ServerLevel level, HitResult result) {
        // No intrinsic impact behaviour. The DSL-side on_hit_action (drained in
        // CombatPowerEvents.onProjectileImpact with the ProjectileHitContext
        // installed) spawns the storm at the impact point.
    }
}
