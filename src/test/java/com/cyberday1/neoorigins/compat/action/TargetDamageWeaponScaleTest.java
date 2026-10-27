package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * A {@code target_action} {@code damage} on a mob adds {@code weapon_damage_scale}
 * times the actor's attack damage to {@code amount}, as the self-targeted
 * {@code damage} does. The mob used to take the flat amount.
 */
class TargetDamageWeaponScaleTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TargetAction parse(String json) {
        return TargetActionParser.parse(JsonParser.parseString(json).getAsJsonObject(), "test:weapon_scale");
    }

    private static ServerPlayer actorWithAttack(double attack) {
        ServerPlayer actor = Mockito.mock(ServerPlayer.class);
        Mockito.when(actor.getAttributeValue(Attributes.ATTACK_DAMAGE)).thenReturn(attack);
        return actor;
    }

    @Test
    void theActorsWeaponScalesTheHit() {
        LivingEntity mob = Mockito.mock(LivingEntity.class, Mockito.RETURNS_DEEP_STUBS);
        parse("{ \"type\": \"origins:damage\", \"amount\": 2, \"weapon_damage_scale\": 0.5 }")
            .execute(mob, actorWithAttack(8.0));
        verify(mob).hurt(any(DamageSource.class), eq(6.0f));
    }

    @Test
    void withoutTheFieldTheHitIsFlat() {
        LivingEntity mob = Mockito.mock(LivingEntity.class, Mockito.RETURNS_DEEP_STUBS);
        parse("{ \"type\": \"origins:damage\", \"amount\": 2 }").execute(mob, actorWithAttack(8.0));
        verify(mob).hurt(any(DamageSource.class), eq(2.0f));
    }
}
