package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.EntityExclusions;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** mobs_ignore_player's include_boss_tier opt-in (#145) and the exclusions it leaves in place. */
class MobsIgnoreBossTierTest {

    private MockedStatic<AdminConfig> admin;
    private List<String> configBlacklist = List.of();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void rig() {
        admin = Mockito.mockStatic(AdminConfig.class);
        admin.when(AdminConfig::tameScareEntityBlacklist).thenAnswer(inv -> configBlacklist);
    }

    @AfterEach
    void tearDown() {
        admin.close();
    }

    private static LivingEntity of(EntityType<?> type) {
        LivingEntity e = mock(LivingEntity.class);
        Mockito.doReturn(type).when(e).getType();
        return e;
    }

    private static MobsIgnorePlayerPower.Config ignore(List<String> types, List<String> blacklist,
                                                       boolean passive, boolean includeBoss) {
        return new MobsIgnorePlayerPower.Config(types, blacklist, passive, includeBoss, "neoorigins:mobs_ignore_player");
    }

    @Test
    void bossTierStaysExcludedWithoutTheOptIn() {
        var warden = of(EntityTypes.WARDEN);
        assertTrue(EntityExclusions.isExcluded(warden, List.of()));
        assertFalse(MobsIgnorePlayerPower.matches(ignore(List.of(), List.of(), false, false), warden));
        assertFalse(MobsIgnorePlayerPower.matches(ignore(List.of("minecraft:warden"), List.of(), false, false), warden));
    }

    @Test
    void theOptInIncludesWhateverTheListMatches() {
        var warden = of(EntityTypes.WARDEN);
        var wither = of(EntityTypes.WITHER);
        assertTrue(MobsIgnorePlayerPower.matches(ignore(List.of("minecraft:warden"), List.of(), false, true), warden));
        assertFalse(MobsIgnorePlayerPower.matches(ignore(List.of("minecraft:warden"), List.of(), false, true), wither));
        assertTrue(MobsIgnorePlayerPower.matches(ignore(List.of(), List.of(), false, true), wither), "match-all includes bosses once opted in");
    }

    @Test
    void theOptInKeepsBothBlacklists() {
        var warden = of(EntityTypes.WARDEN);
        assertFalse(MobsIgnorePlayerPower.matches(ignore(List.of(), List.of("minecraft:warden"), false, true), warden));
        configBlacklist = List.of("minecraft:warden");
        assertFalse(MobsIgnorePlayerPower.matches(ignore(List.of(), List.of(), false, true), warden));
    }

    @Test
    void ordinaryMobsAreUnaffectedByTheOptIn() {
        var zombie = of(EntityTypes.ZOMBIE);
        assertTrue(MobsIgnorePlayerPower.matches(ignore(List.of(), List.of(), false, false), zombie));
        assertTrue(MobsIgnorePlayerPower.matches(ignore(List.of(), List.of(), false, true), zombie));
    }

    @Test
    void theWardenHookHonoursTheRetaliationWindow() {
        ServerPlayer player = mock(ServerPlayer.class);
        LivingEntity warden = of(EntityTypes.WARDEN);
        for (boolean passive : new boolean[] {false, true}) {
            var config = ignore(List.of("minecraft:warden"), List.of(), passive, true);
            try (MockedStatic<ActiveOriginService> active = Mockito.mockStatic(ActiveOriginService.class)) {
                active.when(() -> ActiveOriginService.has(eq(player), eq(MobsIgnorePlayerPower.class), any()))
                    .thenAnswer(inv -> inv.<Predicate<MobsIgnorePlayerPower.Config>>getArgument(2).test(config));
                when(warden.getLastHurtByMob()).thenReturn(null);
                assertTrue(MobsIgnorePlayerPower.hidesFrom(player, warden));
                when(warden.getLastHurtByMob()).thenReturn(player);
                assertTrue(MobsIgnorePlayerPower.hidesFrom(player, warden) == passive,
                    passive ? "passive ignores even after a hit" : "a hit lets the Warden retaliate");
            }
        }
    }
}
