package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.service.ActionContextHolder;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.cyberday1.neoorigins.service.MinionTracker;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #155 part 2: if_else, if_else_list, chance and choice run on mobs when every part is entity-general. */
class TargetActionControlFlowTest {

    private static final TagKey<EntityType<?>> UNDEAD = TagKey.create(Registries.ENTITY_TYPE,
        ResourceLocation.withDefaultNamespace("undead"));
    private static final TagKey<EntityType<?>> MONSTER = TagKey.create(Registries.ENTITY_TYPE,
        ResourceLocation.withDefaultNamespace("monster"));

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
        minions = Mockito.mockStatic(MinionTracker.class);
        BuiltInRegistries.ENTITY_TYPE.bindTags(Map.of(
            UNDEAD, List.of(EntityType.ZOMBIE.builtInRegistryHolder()),
            MONSTER, List.of(EntityType.ZOMBIE.builtInRegistryHolder())));
    }

    @AfterEach
    void tearDown() {
        BuiltInRegistries.ENTITY_TYPE.resetTags();
        minions.close();
        config.close();
    }

    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    private ServerPlayer caster(LivingEntity... inRange) {
        ServerPlayer source = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(source.level()).thenReturn(level);
        when(source.position()).thenReturn(Vec3.ZERO);
        when(source.getBoundingBox()).thenReturn(new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3));
        when(source.getUUID()).thenReturn(casterId);
        doReturn(EntityType.PLAYER).when(source).getType();
        when(level.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class))).thenReturn(List.of(inRange));
        return source;
    }

    private static <T extends LivingEntity> T mob(Class<T> cls, EntityType<?> type) {
        T m = mock(cls);
        when(m.position()).thenReturn(new Vec3(1, 0, 0));
        doReturn(type).when(m).getType();
        ServerLevel level = mock(ServerLevel.class);
        when(m.level()).thenReturn(level);
        when(level.damageSources()).thenReturn(mock(DamageSources.class));
        return m;
    }

    private static Object projectileHit(LivingEntity hit) {
        return new EventPowerIndex.ProjectileHitContext(null, new EntityHitResult(hit, new Vec3(1, 0, 0)), null);
    }

    private static void runUnder(Object ctx, EntityAction action, ServerPlayer p) {
        Object prev = ActionContextHolder.set(ctx);
        try { action.execute(p); } finally { ActionContextHolder.restore(prev); }
    }

    private static EntityAction aoe(String entityAction) {
        return ActionParser.parse(json("""
            {"type": "neoorigins:area_of_effect", "radius": 3.0, "include_source": false,
             "ignore_friendly_fire": true, "entity_action": %s}""".formatted(entityAction)), "test");
    }

    @Test
    void reporterAlwaysActiveExampleHealsTheSheep() {
        Sheep sheep = mob(Sheep.class, EntityType.SHEEP);
        EntityAction a = aoe("""
            {"type": "neoorigins:if_else", "condition": {"type": "neoorigins:always_active"},
             "if_action": {"type": "neoorigins:heal", "amount": 5},
             "else_action": {"type": "neoorigins:damage", "amount": 1}}""");
        runUnder(projectileHit(sheep), a, caster(sheep));
        verify(sheep).heal(5.0F);
        verify(sheep, never()).hurt(any(), anyFloat());
    }

    @Test
    void reporterGoalHealsTheLivingAndDamagesTheUndead() {
        Sheep sheep = mob(Sheep.class, EntityType.SHEEP);
        Zombie zombie = mob(Zombie.class, EntityType.ZOMBIE);
        EntityAction a = aoe("""
            {"type": "neoorigins:if_else",
             "condition": {"type": "neoorigins:target_type", "entity_type": "#minecraft:undead", "inverted": true},
             "if_action": {"type": "neoorigins:heal", "amount": 5},
             "else_action": {"type": "neoorigins:damage", "amount": 1}}""");
        runUnder(projectileHit(sheep), a, caster(sheep, zombie));
        verify(sheep).heal(5.0F);
        verify(sheep, never()).hurt(any(), anyFloat());
        verify(zombie, never()).heal(anyFloat());
        verify(zombie).hurt(any(), eq(1.0F));
    }

    @Test
    void entityTypeTagAndTheTargetConditionWrapperWorkInsideTheAoe() {
        for (String cond : List.of(
                "{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"#minecraft:undead\"}",
                "{\"type\": \"apoli:target_condition\", \"condition\": "
                    + "{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"#minecraft:undead\"}}")) {
            Sheep sheep = mob(Sheep.class, EntityType.SHEEP);
            Zombie zombie = mob(Zombie.class, EntityType.ZOMBIE);
            EntityAction a = aoe("""
                {"type": "neoorigins:if_else", "condition": %s,
                 "if_action": {"type": "neoorigins:damage", "amount": 1},
                 "else_action": {"type": "neoorigins:heal", "amount": 5}}""".formatted(cond));
            runUnder(projectileHit(sheep), a, caster(sheep, zombie));
            verify(sheep).heal(5.0F);
            verify(zombie).hurt(any(), eq(1.0F));
        }
    }

    @Test
    void exampleTidecallerBoltHitsOnlyMonstersInTheRadius() throws Exception {
        JsonObject power = json(Files.readString(Path.of(System.getProperty("neoorigins.projectDir"),
            "example-pack/data/examplepack/origins/powers/tidecaller_drowning_bolt.json")));
        JsonObject onHit = power.getAsJsonObject("entity_action").getAsJsonObject("on_hit_action");
        EntityAction a = ActionParser.parse(onHit, "test:tidecaller");
        Sheep sheep = mob(Sheep.class, EntityType.SHEEP);
        Zombie zombie = mob(Zombie.class, EntityType.ZOMBIE);
        ServerPlayer bystander = caster();
        when(bystander.position()).thenReturn(new Vec3(1, 0, 0));
        // The bolt struck the zombie; the bystander used to be tested against that zombie.
        runUnder(projectileHit(zombie), a, caster(sheep, zombie, bystander));
        verify(zombie).addEffect(any());
        verify(zombie).hurt(any(), eq(1.0F));
        verify(sheep, never()).addEffect(any());
        verify(sheep, never()).hurt(any(), anyFloat());
        verify(bystander, never()).addEffect(any());
        verify(bystander, never()).hurt(any(), anyFloat());
    }

    @Test
    void aPlayerOnlyPartKeepsTheVerbOnThePlayerPath() {
        assertNull(TargetActionParser.parse(json("""
            {"type": "neoorigins:if_else", "condition": {"type": "neoorigins:always_active"},
             "if_action": {"type": "neoorigins:grant_power", "power": "neoorigins:x"}}"""), "test"));
        assertNull(TargetActionParser.parse(json("""
            {"type": "neoorigins:if_else", "condition": {"type": "neoorigins:sneaking"},
             "if_action": {"type": "neoorigins:heal", "amount": 1}}"""), "test"));
        assertNull(TargetActionParser.parse(json("""
            {"type": "neoorigins:chance", "action": {"type": "neoorigins:delay", "action": {"type": "neoorigins:heal"}}}"""),
            "test"));
        assertNull(TargetActionParser.parse(json("""
            {"type": "neoorigins:choice", "actions": [{"element": {"type": "neoorigins:heal"}},
             {"element": {"type": "neoorigins:grant_power", "power": "neoorigins:x"}}]}"""), "test"));
    }

    @Test
    void allOfAndIfElseListCompile() {
        assertNotNull(TargetActionParser.parse(json("""
            {"type": "neoorigins:all_of", "actions": [{"type": "neoorigins:heal", "amount": 1}]}"""), "test"));
        TargetAction list = TargetActionParser.parse(json("""
            {"type": "neoorigins:if_else_list", "actions": [
              {"condition": {"type": "neoorigins:entity_type", "entity_type": "#minecraft:undead"},
               "action": {"type": "neoorigins:damage", "amount": 2}},
              {"condition": {"type": "neoorigins:always_active"}, "action": [{"type": "neoorigins:heal", "amount": 3}]}]}"""),
            "test");
        assertNotNull(list);
        Sheep sheep = mob(Sheep.class, EntityType.SHEEP);
        Zombie zombie = mob(Zombie.class, EntityType.ZOMBIE);
        ServerPlayer actor = caster();
        list.execute(sheep, actor);
        list.execute(zombie, actor);
        verify(sheep).heal(3.0F);
        verify(sheep, never()).hurt(any(), anyFloat());
        verify(zombie).hurt(any(), eq(2.0F));
        verify(zombie, never()).heal(anyFloat());
    }

    @Test
    void targetActionWithAnIfElseRunsOnAMob() {
        EntityAction a = ActionParser.parse(json("""
            {"type": "neoorigins:target_action", "action": {"type": "neoorigins:if_else",
              "condition": {"type": "neoorigins:entity_type", "entity_type": "#minecraft:undead"},
              "if_action": {"type": "neoorigins:damage", "amount": 1},
              "else_action": {"type": "neoorigins:heal", "amount": 5}}}"""), "test");
        Sheep sheep = mob(Sheep.class, EntityType.SHEEP);
        runUnder(projectileHit(sheep), a, caster());
        verify(sheep).heal(5.0F);
        verify(sheep, never()).hurt(any(), anyFloat());
    }

    @Test
    void chanceRollsOnTheTarget() {
        TargetAction a = TargetActionParser.parse(json("""
            {"type": "neoorigins:chance", "chance": 0.5, "action": {"type": "neoorigins:heal", "amount": 2}}"""), "test");
        assertNotNull(a);
        Sheep lucky = mob(Sheep.class, EntityType.SHEEP);
        Sheep unlucky = mob(Sheep.class, EntityType.SHEEP);
        RandomSource low = mock(RandomSource.class);
        when(low.nextFloat()).thenReturn(0.1F);
        RandomSource high = mock(RandomSource.class);
        when(high.nextFloat()).thenReturn(0.9F);
        when(lucky.getRandom()).thenReturn(low);
        when(unlucky.getRandom()).thenReturn(high);
        a.execute(lucky, caster());
        a.execute(unlucky, caster());
        verify(lucky).heal(2.0F);
        verify(unlucky, never()).heal(anyFloat());
    }

    @Test
    void choicePicksByWeight() {
        TargetAction a = TargetActionParser.parse(json("""
            {"type": "neoorigins:choice", "actions": [
              {"element": {"type": "neoorigins:heal", "amount": 1}, "weight": 1},
              {"action": {"type": "neoorigins:heal", "amount": 7}, "weight": 3}]}"""), "test");
        assertNotNull(a);
        Sheep first = mob(Sheep.class, EntityType.SHEEP);
        Sheep second = mob(Sheep.class, EntityType.SHEEP);
        RandomSource rollFirst = mock(RandomSource.class);
        when(rollFirst.nextInt(anyInt())).thenReturn(0);
        RandomSource rollSecond = mock(RandomSource.class);
        when(rollSecond.nextInt(anyInt())).thenReturn(3);
        when(first.getRandom()).thenReturn(rollFirst);
        when(second.getRandom()).thenReturn(rollSecond);
        a.execute(first, caster());
        a.execute(second, caster());
        verify(first).heal(1.0F);
        verify(second).heal(7.0F);
        verify(rollFirst).nextInt(4);
    }
}
