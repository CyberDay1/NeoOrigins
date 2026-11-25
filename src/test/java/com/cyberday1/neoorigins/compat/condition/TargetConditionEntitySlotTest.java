package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.service.ActionContextHolder;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #155 part 1: target_condition in an entity-condition slot reads the dispatch context's target. */
class TargetConditionEntitySlotTest {

    private static final TagKey<EntityType<?>> UNDEAD = TagKey.create(Registries.ENTITY_TYPE,
        Identifier.withDefaultNamespace("undead"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void bindTags() {
        applyTags(Map.of(UNDEAD, List.of(EntityType.ZOMBIE.builtInRegistryHolder())));
    }

    @AfterEach
    void unbindTags() {
        applyTags(Map.of());
    }

    private static void applyTags(Map<TagKey<EntityType<?>>, List<Holder<EntityType<?>>>> tags) {
        BuiltInRegistries.ENTITY_TYPE.prepareTagReload(new TagLoader.LoadResult<>(Registries.ENTITY_TYPE, tags)).apply();
    }

    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    private static ServerPlayer player() {
        ServerPlayer p = mock(ServerPlayer.class);
        doReturn(EntityType.PLAYER).when(p).getType();
        return p;
    }

    private static <T extends LivingEntity> T mob(Class<T> cls, EntityType<?> type) {
        T m = mock(cls);
        doReturn(type).when(m).getType();
        ServerLevel level = mock(ServerLevel.class);
        when(m.level()).thenReturn(level);
        when(level.damageSources()).thenReturn(mock(DamageSources.class));
        return m;
    }

    private static Object projectileHit(LivingEntity hit) {
        return new EventPowerIndex.ProjectileHitContext(null, new EntityHitResult(hit, Vec3.ZERO), null);
    }

    private static boolean testUnder(Object ctx, EntityCondition c, ServerPlayer p) {
        Object prev = ActionContextHolder.set(ctx);
        try { return c.test(p); } finally { ActionContextHolder.restore(prev); }
    }

    private static EntityCondition cond(String s) { return ConditionParser.parse(json(s), "test"); }

    @Test
    void registeredAsABuiltIn() {
        assertNotNull(BuiltinConditions.get("neoorigins:target_condition"));
        assertTrue(ConditionParser.KNOWN_TYPES.contains("neoorigins:target_condition"));
    }

    @Test
    void reporterOnHitExampleHealsTheSheepInEveryNamespace() {
        for (String ns : List.of("neoorigins:", "origins:", "apoli:")) {
            EntityAction onHit = ActionParser.parse(json("""
                {"type": "neoorigins:if_else",
                 "condition": {"type": "%starget_condition",
                   "condition": {"type": "neoorigins:entity_type", "entity_type": "minecraft:sheep"}},
                 "if_action": {"type": "neoorigins:target_action", "action": {"type": "neoorigins:heal", "amount": 5}},
                 "else_action": {"type": "neoorigins:target_action", "action": {"type": "neoorigins:damage", "amount": 1}}}
                """.formatted(ns)), "test");
            Sheep sheep = mob(Sheep.class, EntityType.SHEEP);
            Object prev = ActionContextHolder.set(projectileHit(sheep));
            try { onHit.execute(player()); } finally { ActionContextHolder.restore(prev); }
            verify(sheep).heal(5.0F);
            verify(sheep, never()).hurt(any(), anyFloat());
        }
    }

    @Test
    void falseWithoutATargetAndInvertedFlipsIt() {
        assertFalse(testUnder(null, cond("""
            {"type": "neoorigins:target_condition", "condition": {"type": "neoorigins:constant", "value": true}}"""), player()));
        assertTrue(testUnder(null, cond("""
            {"type": "neoorigins:target_condition", "inverted": true,
             "condition": {"type": "neoorigins:constant", "value": true}}"""), player()));
    }

    @Test
    void aPlayerTargetIsReadRatherThanTheCaster() {
        ServerPlayer caster = player();
        ServerPlayer victim = player();
        when(caster.isShiftKeyDown()).thenReturn(false);
        when(victim.isShiftKeyDown()).thenReturn(true);
        EntityCondition c = cond("""
            {"type": "neoorigins:target_condition", "condition": {"type": "neoorigins:sneaking"}}""");
        assertTrue(testUnder(projectileHit(victim), c, caster));
        assertFalse(testUnder(projectileHit(caster), c, victim));
    }

    @Test
    void aPlayerOnlyInnerConditionIsFalseForAMob() {
        EntityCondition c = cond("""
            {"type": "neoorigins:target_condition", "condition": {"type": "neoorigins:sneaking"}}""");
        assertFalse(testUnder(projectileHit(mob(Sheep.class, EntityType.SHEEP)), c, player()));
    }

    @Test
    void anEntityTypeTagInsideMatchesTheTarget() {
        EntityCondition c = cond("""
            {"type": "neoorigins:target_condition",
             "condition": {"type": "neoorigins:entity_type", "entity_type": "#minecraft:undead"}}""");
        assertTrue(testUnder(projectileHit(mob(Zombie.class, EntityType.ZOMBIE)), c, player()));
        assertFalse(testUnder(projectileHit(mob(Sheep.class, EntityType.SHEEP)), c, player()));
    }

    @Test
    void aMissingInnerConditionFailsClosed() {
        EntityCondition c = cond("{\"type\": \"neoorigins:target_condition\"}");
        assertFalse(testUnder(projectileHit(mob(Sheep.class, EntityType.SHEEP)), c, player()));
    }

    @Test
    void readsTheInteractAndHitDealtTargets() {
        EntityCondition c = cond("""
            {"type": "neoorigins:target_condition",
             "condition": {"type": "neoorigins:entity_type", "entity_type": "minecraft:sheep"}}""");
        Sheep sheep = mob(Sheep.class, EntityType.SHEEP);
        Zombie zombie = mob(Zombie.class, EntityType.ZOMBIE);
        assertTrue(testUnder(new EventPowerIndex.EntityInteractContext(sheep), c, player()));
        assertFalse(testUnder(new EventPowerIndex.EntityInteractContext(zombie), c, player()));
        assertTrue(testUnder(new EventPowerIndex.HitDealtContext(1.0F, sheep, null), c, player()));
        assertFalse(testUnder(new EventPowerIndex.HitDealtContext(1.0F, zombie, null), c, player()));
    }
}
