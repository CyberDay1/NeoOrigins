package com.cyberday1.neoorigins.compat.condition;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/** #156: entity_type takes a #tag and a bare id on the holder side too, through one matcher. */
class EntityTypeTagMatchTest {

    private static final TagKey<EntityType<?>> UNDEAD = TagKey.create(Registries.ENTITY_TYPE,
        Identifier.withDefaultNamespace("undead"));
    private static final TagKey<EntityType<?>> PLAYERS = TagKey.create(Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("test", "players"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void bindTags() {
        applyTags(Map.of(
            UNDEAD, List.of(EntityType.ZOMBIE.builtInRegistryHolder()),
            PLAYERS, List.of(EntityType.PLAYER.builtInRegistryHolder())));
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
        return m;
    }

    private static boolean holder(String condition) {
        return ConditionParser.parse(json(condition), "test").test(player());
    }

    @Test
    void holderEntityTypeTakesATagABareIdAndTypeId() {
        assertTrue(holder("{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"#test:players\"}"));
        assertFalse(holder("{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"#minecraft:undead\"}"));
        assertTrue(holder("{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"player\"}"));
        assertTrue(holder("{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"minecraft:player\"}"));
        assertTrue(holder("{\"type\": \"origins:entity_type\", \"type_id\": \"player\"}"));
        assertFalse(holder("{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"minecraft:sheep\"}"));
    }

    @Test
    void aMalformedTagFailsClosedAtLoad() {
        assertDoesNotThrow(() -> holder("{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"#Bad Tag\"}"));
        assertFalse(holder("{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"#Bad Tag\"}"));
        assertThrows(RuntimeException.class, () -> EntityTypeMatcher.compile("#Bad Tag"));
    }

    @Test
    void holderTargetTypeHonoursTypeId() {
        EntityCondition c = ConditionParser.parse(
            json("{\"type\": \"neoorigins:target_type\", \"type_id\": \"#minecraft:undead\"}"), "test");
        assertTrue(struck(mob(Zombie.class, EntityType.ZOMBIE), c));
        assertFalse(struck(mob(Sheep.class, EntityType.SHEEP), c));
    }

    private static boolean struck(LivingEntity hit, EntityCondition c) {
        Object prev = ActionContextHolder.set(
            new EventPowerIndex.ProjectileHitContext(null, new EntityHitResult(hit, Vec3.ZERO), null));
        try { return c.test(player()); } finally { ActionContextHolder.restore(prev); }
    }

    @Test
    void targetSideKeepsBareIdsAndTags() {
        TargetCondition bare = TargetConditionParser.parse(
            json("{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"sheep\"}"), "test");
        assertTrue(bare.test(mob(Sheep.class, EntityType.SHEEP), player()));
        assertFalse(bare.test(mob(Zombie.class, EntityType.ZOMBIE), player()));
        TargetCondition tag = TargetConditionParser.parse(
            json("{\"type\": \"neoorigins:entity_type\", \"entity_type\": \"#minecraft:undead\"}"), "test");
        assertTrue(tag.test(mob(Zombie.class, EntityType.ZOMBIE), player()));
        assertFalse(tag.test(mob(Sheep.class, EntityType.SHEEP), player()));
    }
}
