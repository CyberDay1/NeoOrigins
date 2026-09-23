package com.cyberday1.neoorigins.rig;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.mockito.Mockito;

import java.util.UUID;

/**
 * Drives the server-side power lifecycle — grant, respawn, login, logout — from a
 * unit test, so a test can assert what a power DOES across a death or a relog
 * rather than only what its JSON parses to.
 */
// hub: neoorigins/lifecycle-rig.md
public final class PlayerLifecycle {

    private PlayerLifecycle() {}

    /** A mocked player carrying only a UUID and real abilities. */
    public static ServerPlayer player() {
        ServerPlayer sp = Mockito.mock(ServerPlayer.class);
        UUID uuid = UUID.randomUUID();
        Mockito.when(sp.getUUID()).thenReturn(uuid);
        Mockito.when(sp.getAbilities()).thenReturn(new Abilities());
        return sp;
    }

    /** A genuine {@link ServerPlayer} over a deep-stubbed level: real attributes, food and attachments. */
    public static ServerPlayer realPlayer() {
        ServerLevel level = Mockito.mock(ServerLevel.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(level.getSharedSpawnPos()).thenReturn(new BlockPos(0, 64, 0));
        Mockito.when(level.getSharedSpawnAngle()).thenReturn(0f);
        Mockito.when(level.getWorldBorder()).thenReturn(new WorldBorder());
        Mockito.when(level.noCollision(Mockito.any(Entity.class), Mockito.any(AABB.class))).thenReturn(false);
        MinecraftServer server = Mockito.mock(MinecraftServer.class, Mockito.RETURNS_DEEP_STUBS);
        UUID uuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(uuid, "rig_" + uuid.toString().substring(0, 8));
        return new ServerPlayer(server, level, profile, ClientInformation.createDefault());
    }

    public static void grant(PowerHolder<?> holder, ServerPlayer sp)   { holder.onGranted(sp); }
    public static void revoke(PowerHolder<?> holder, ServerPlayer sp)  { holder.onRevoked(sp); }
    public static void respawn(PowerHolder<?> holder, ServerPlayer sp) { holder.onRespawn(sp); }
    public static void login(PowerHolder<?> holder, ServerPlayer sp)   { holder.onLogin(sp); }

    /** The real logout handler, exactly as the event bus would run it. */
    public static void logout(ServerPlayer sp) {
        PlayerLifecycleEvents.onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(sp));
    }

    /** Logout followed by the login re-grant, i.e. one relog. */
    public static void relog(PowerHolder<?> holder, ServerPlayer sp) {
        logout(sp);
        login(holder, sp);
    }
}
