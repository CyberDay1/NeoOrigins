package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.service.MinionTracker;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** area_of_effect's ignore_friendly_fire skips all five [friendly_fire] filters, never the caster. */
class AreaOfEffectFriendlyFireTest {

    private MockedStatic<GameplayConfig> config;
    private MockedStatic<MinionTracker> minions;
    private final UUID casterId = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void rig() {
        config = Mockito.mockStatic(GameplayConfig.class);
        config.when(GameplayConfig::ffProtectOwnedPets).thenReturn(true);
        config.when(GameplayConfig::ffProtectMinions).thenReturn(true);
        config.when(GameplayConfig::ffProtectAnimals).thenReturn(true);
        config.when(GameplayConfig::ffProtectVillagers).thenReturn(true);
        config.when(GameplayConfig::ffProtectIronGolems).thenReturn(true);
        minions = Mockito.mockStatic(MinionTracker.class);
    }

    @AfterEach
    void tearDown() {
        minions.close();
        config.close();
    }

    private ServerPlayer caster(LivingEntity... inRange) {
        ServerPlayer source = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(source.level()).thenReturn(level);
        when(source.position()).thenReturn(Vec3.ZERO);
        when(source.getBoundingBox()).thenReturn(new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3));
        when(source.getUUID()).thenReturn(casterId);
        when(level.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class))).thenReturn(List.of(inRange));
        return source;
    }

    private static <T extends Mob> T near(Class<T> type) {
        T mob = mock(type);
        when(mob.position()).thenReturn(new Vec3(2, 0, 0));
        return mob;
    }

    private List<Mob> protectedMobs() {
        Wolf pet = near(Wolf.class);
        when(pet.getOwnerUUID()).thenReturn(casterId);
        Mob minion = near(net.minecraft.world.entity.monster.Zombie.class);
        minions.when(() -> MinionTracker.isTrackedMinionOf(minion, casterId)).thenReturn(true);
        return List.of(pet, minion, near(Cow.class), near(Villager.class), near(IronGolem.class));
    }

    private static EntityAction heal(String extra) {
        return ActionParser.parse(JsonParser.parseString("""
            { "type": "neoorigins:area_of_effect", "radius": 8, "include_source": false%s,
              "entity_action": { "type": "neoorigins:heal", "amount": 2 } }
            """.formatted(extra)).getAsJsonObject(), "test:aoe");
    }

    @Test
    void theGameplayFiltersSpareEveryCategoryByDefault() {
        var mobs = protectedMobs();
        heal("").execute(caster(mobs.toArray(LivingEntity[]::new)));
        for (Mob mob : mobs) verify(mob, never()).heal(anyFloat());
    }

    @Test
    void ignoreFriendlyFireHitsEveryCategory() {
        var mobs = protectedMobs();
        heal(", \"ignore_friendly_fire\": true").execute(caster(mobs.toArray(LivingEntity[]::new)));
        for (Mob mob : mobs) verify(mob).heal(2.0F);
    }

    @Test
    void ignoreFriendlyFireStillSparesTheCaster() {
        ServerPlayer source = caster();
        when(source.level().getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class)))
            .thenReturn(List.<LivingEntity>of(source));
        heal(", \"ignore_friendly_fire\": true").execute(source);
        verify(source, never()).heal(anyFloat());
    }
}
