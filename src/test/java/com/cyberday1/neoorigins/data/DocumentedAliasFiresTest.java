package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code neoorigins:action_on_kill} and {@code neoorigins:action_over_time}, authored
 * exactly as docs/POWER_TYPES.md shows them, must DO something once loaded. Both
 * loaders run in reload order and the events are dispatched the way the game does.
 */
// hub: neoorigins/salvage-vs-alias.md
class DocumentedAliasFiresTest {

    private static final String KILL = """
        {
          "type": "neoorigins:action_on_kill",
          "action": "restore_health",
          "amount": 2.0,
          "name": "Vampiric",
          "description": "Restores health by killing enemies."
        }
        """;

    private static final String OVER_TIME = """
        {
          "type": "neoorigins:action_over_time",
          "interval": 40,
          "entity_action": { "type": "neoorigins:feed", "food": 1, "saturation": 0.2 }
        }
        """;

    /** else_action is a documented field that only condition_passive reads. */
    private static final String OVER_TIME_ELSE = """
        {
          "type": "neoorigins:action_over_time",
          "interval": 40,
          "condition": { "type": "neoorigins:constant", "value": false },
          "entity_action": { "type": "neoorigins:heal", "amount": 1.0 },
          "else_action": { "type": "neoorigins:feed", "food": 1, "saturation": 0.2 }
        }
        """;

    @TempDir static Path pack;

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path powers = Files.createDirectories(pack.resolve("data/testpack/origins/powers"));
        Files.writeString(powers.resolve("vampiric.json"), KILL);
        Files.writeString(powers.resolve("periodic_feed.json"), OVER_TIME);
        Files.writeString(powers.resolve("else_feed.json"), OVER_TIME_ELSE);
        RealDatapack.loadWith(pack);
        // Route B runs after PowerDataManager in the reload, and would pick up
        // anything the first loader dropped.
        Map<ResourceLocation, JsonElement> compat = new HashMap<>();
        compat.put(id("vampiric"), JsonParser.parseString(KILL));
        compat.put(id("periodic_feed"), JsonParser.parseString(OVER_TIME));
        compat.put(id("else_feed"), JsonParser.parseString(OVER_TIME_ELSE));
        applyCompat(compat);
    }

    @AfterAll
    static void restore() {
        RealDatapack.restoreShipped();
    }

    @Test
    void actionOnKillHealsOnAKill() {
        PowerHolder<?> holder = loaded("vampiric");
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        PlayerLifecycle.grant(holder, sp);
        sp.setHealth(10.0f);

        // Both channels CombatPowerEvents#onLivingDeath fires for a player kill.
        LivingEntity killed = Mockito.mock(LivingEntity.class);
        holder.onKill(sp, killed);
        EventPowerIndex.dispatch(sp, EventPowerIndex.Event.KILL, new EventPowerIndex.KillContext(killed));

        assertEquals(12.0f, sp.getHealth(), 1e-4,
            "action_on_kill (restore_health 2.0) did not heal on a kill; loaded as "
                + holder.type().getClass().getSimpleName());
    }

    @Test
    void actionOverTimeFeedsOnItsInterval() {
        assertFedTwice("periodic_feed", "feed 1 every 40 ticks");
    }

    @Test
    void actionOverTimeRunsItsElseActionWhileTheConditionFails() {
        assertFedTwice("else_feed", "else_action feed 1 every 40 ticks, condition false");
    }

    private static void assertFedTwice(String path, String what) {
        PowerHolder<?> holder = loaded(path);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        int[] tick = {0};
        Mockito.when(sp.serverLevel().getServer().getTickCount()).thenAnswer(inv -> tick[0]);
        PlayerLifecycle.grant(holder, sp);
        sp.getFoodData().setFoodLevel(10);

        // 80 ticks at interval 40 is exactly two firings on either route's clock.
        for (tick[0] = 1; tick[0] <= 80; tick[0]++) {
            sp.tickCount = tick[0];
            holder.onTick(sp);
        }

        assertEquals(12, sp.getFoodData().getFoodLevel(),
            "action_over_time (" + what + ") did not feed twice in 80 ticks; loaded as "
                + holder.type().getClass().getSimpleName());
    }

    private static PowerHolder<?> loaded(String path) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(id(path));
        assertNotNull(holder, "testpack:" + path + " was not loaded by either route");
        return holder;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("testpack", path);
    }

    private static void applyCompat(Map<ResourceLocation, JsonElement> json) {
        try {
            Method m = OriginsCompatPowerLoader.class.getDeclaredMethod("apply", Map.class,
                net.minecraft.server.packs.resources.ResourceManager.class,
                net.minecraft.util.profiling.ProfilerFiller.class);
            m.setAccessible(true);
            m.invoke(OriginsCompatPowerLoader.INSTANCE, json, null,
                net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not drive OriginsCompatPowerLoader.apply", e);
        }
    }
}
