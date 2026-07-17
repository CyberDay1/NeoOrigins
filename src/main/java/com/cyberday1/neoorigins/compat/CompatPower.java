package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A single compiled power type used by Route B.
 * Each power is represented as a Config holding Consumer<ServerPlayer> lambdas
 * built at load time from the Origins JSON by OriginsCompatPowerLoader.
 *
 * The codec is never used for parsing — Route B powers are injected directly
 * into PowerDataManager via injectExternalPowers().
 */
public class CompatPower extends PowerType<CompatPower.Config> {

    public static final CompatPower INSTANCE = new CompatPower();

    public record Config(
        Consumer<ServerPlayer> onGranted,
        Consumer<ServerPlayer> onRevoked,
        Consumer<ServerPlayer> onTick,
        Consumer<ServerPlayer> onActivated,
        Consumer<ServerPlayer> onRespawn,
        Consumer<ServerPlayer> onHit,
        Consumer<ServerPlayer> onKill,
        Consumer<LivingIncomingDamageEvent> onIncomingDamage,
        BiConsumer<ServerPlayer, LivingEntity> onDealDamage,
        int cooldownTicks,
        // When true the power is activatable (has onActivated) but declines a
        // hotkey/skill slot — reachable only via the activate_power action.
        boolean hotkeyless,
        // Capability tags this power publishes to the client-predicted mixin
        // layer (see PowerType.capabilities), gated per-tick by
        // capabilityCondition when one is present. Null/empty = none.
        Set<String> capabilities,
        Predicate<ServerPlayer> capabilityCondition
    ) implements PowerConfiguration {

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private Consumer<ServerPlayer> onGranted, onRevoked, onTick, onActivated, onRespawn, onHit, onKill;
            private Consumer<LivingIncomingDamageEvent> onIncomingDamage;
            private BiConsumer<ServerPlayer, LivingEntity> onDealDamage;
            private int cooldownTicks;
            private boolean hotkeyless;
            private Set<String> capabilities;
            private Predicate<ServerPlayer> capabilityCondition;

            public Builder onGranted(Consumer<ServerPlayer> c)   { onGranted   = c; return this; }
            public Builder onRevoked(Consumer<ServerPlayer> c)   { onRevoked   = c; return this; }
            public Builder onTick(Consumer<ServerPlayer> c)      { onTick      = c; return this; }
            public Builder onActivated(Consumer<ServerPlayer> c) { onActivated = c; return this; }
            public Builder onRespawn(Consumer<ServerPlayer> c)   { onRespawn   = c; return this; }
            public Builder onHit(Consumer<ServerPlayer> c)       { onHit       = c; return this; }
            public Builder onKill(Consumer<ServerPlayer> c)      { onKill      = c; return this; }
            public Builder onIncomingDamage(Consumer<LivingIncomingDamageEvent> c) {
                onIncomingDamage = c; return this;
            }
            /** Fires when the holder DEALS damage to a living entity (actor=holder, target=victim). */
            public Builder onDealDamage(BiConsumer<ServerPlayer, LivingEntity> c) {
                onDealDamage = c; return this;
            }
            public Builder cooldownTicks(int ticks) { cooldownTicks = ticks; return this; }
            /** Mark this active power as having no hotkey — fired only via activate_power. */
            public Builder hotkeyless(boolean v) { hotkeyless = v; return this; }
            /**
             * Publish a capability tag, optionally gated by a per-tick condition
             * (evaluated at active-power sync time — see PowerType.capabilities).
             */
            public Builder capability(String tag, Predicate<ServerPlayer> condition) {
                capabilities = Set.of(tag);
                capabilityCondition = condition;
                return this;
            }

            public Config build() {
                return new Config(onGranted, onRevoked, onTick, onActivated, onRespawn,
                    onHit, onKill, onIncomingDamage, onDealDamage, cooldownTicks, hotkeyless,
                    capabilities, capabilityCondition);
            }
        }
    }

    @Override
    public Codec<Config> codec() {
        // Never called for Route B powers — they are injected directly, not codec-decoded.
        return MapCodec.unit(() -> new Config(null, null, null, null, null, null, null, null, null, 0, false, null, null)).codec();
    }

    /** Active only when this specific config has an onActivated consumer. */
    @Override
    public boolean isActivePower(Config config) {
        return config.onActivated() != null;
    }

    @Override
    public java.util.Set<String> capabilities(Config config) {
        return config.capabilities() == null ? Set.of() : config.capabilities();
    }

    /**
     * Player-aware variant used by active-power sync and the server-side
     * capability query: the config's capabilityCondition (the Origins power's
     * `condition`, e.g. "in lava" for origins:swimming) gates the tags per tick.
     */
    @Override
    public Set<String> capabilities(ServerPlayer player, Config config) {
        Set<String> caps = capabilities(config);
        if (caps.isEmpty()) return caps;
        if (config.capabilityCondition() != null && !config.capabilityCondition().test(player)) {
            return Set.of();
        }
        return caps;
    }

    /**
     * Hotkey-less active powers ({@code "disable_hotkey": true}) stay active
     * (so {@code activate_power} can reach them) but decline a skill slot, so no
     * key press triggers them.
     */
    @Override
    public boolean occupiesHotkeySlot(Config config) {
        return config.onActivated() != null && !config.hotkeyless();
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        if (config.onGranted() != null) config.onGranted().accept(player);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        if (config.onRevoked() != null) config.onRevoked().accept(player);
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.onTick() != null) config.onTick().accept(player);
    }

    @Override
    public void onActivated(ServerPlayer player, Config config) {
        if (config.onActivated() != null) {
            config.onActivated().accept(player);
            // Compat consumers gate internally (cooldown/resource checks live
            // inside the lambda) and expose no success signal, so this fires
            // per dispatched attempt — best available fidelity for Route B.
            com.cyberday1.neoorigins.service.EventPowerIndex.dispatchPowerActivated(
                player, com.cyberday1.neoorigins.api.power.PowerHolder.currentDispatchId());
        }
    }

    @Override
    public void onRespawn(ServerPlayer player, Config config) {
        if (config.onRespawn() != null) {
            config.onRespawn().accept(player);
        } else {
            // Fall back to onGranted so effects (attribute modifiers, state init, etc.)
            // are re-applied after death — matches base PowerType.onRespawn behaviour.
            onGranted(player, config);
        }
    }

    @Override
    public void onHit(ServerPlayer player, Config config, float amount) {
        if (config.onHit() != null) config.onHit().accept(player);
    }

    @Override
    public void onKill(ServerPlayer player, Config config, LivingEntity killed) {
        if (config.onKill() != null) config.onKill().accept(player);
    }
}
