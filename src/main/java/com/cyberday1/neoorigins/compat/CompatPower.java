package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Consumer;

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
        Consumer<ServerPlayer> onKill
    ) implements PowerConfiguration {

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private Consumer<ServerPlayer> onGranted, onRevoked, onTick, onActivated, onRespawn, onHit, onKill;

            public Builder onGranted(Consumer<ServerPlayer> c)   { onGranted   = c; return this; }
            public Builder onRevoked(Consumer<ServerPlayer> c)   { onRevoked   = c; return this; }
            public Builder onTick(Consumer<ServerPlayer> c)      { onTick      = c; return this; }
            public Builder onActivated(Consumer<ServerPlayer> c) { onActivated = c; return this; }
            public Builder onRespawn(Consumer<ServerPlayer> c)   { onRespawn   = c; return this; }
            public Builder onHit(Consumer<ServerPlayer> c)       { onHit       = c; return this; }
            public Builder onKill(Consumer<ServerPlayer> c)      { onKill      = c; return this; }

            public Config build() {
                return new Config(onGranted, onRevoked, onTick, onActivated, onRespawn, onHit, onKill);
            }
        }
    }

    @Override
    public Codec<Config> codec() {
        // Never called for Route B powers — they are injected directly, not codec-decoded.
        return MapCodec.unit(() -> new Config(null, null, null, null, null, null, null)).codec();
    }

    /** Active only when this specific config has an onActivated consumer. */
    @Override
    public boolean isActivePower(Config config) {
        return config.onActivated() != null;
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
        if (config.onActivated() != null) config.onActivated().accept(player);
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
