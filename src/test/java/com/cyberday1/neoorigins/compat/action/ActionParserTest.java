package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ActionParserTest {

    @Test
    void ifElseWithoutConditionExecutesElseAction() {
        JsonObject actionJson = new JsonObject();
        actionJson.addProperty("type", "origins:if_else");

        JsonObject ifAction = new JsonObject();
        ifAction.addProperty("type", "origins:heal");
        ifAction.addProperty("amount", 2.0F);
        actionJson.add("if_action", ifAction);

        JsonObject elseAction = new JsonObject();
        elseAction.addProperty("type", "origins:heal");
        elseAction.addProperty("amount", 4.0F);
        actionJson.add("else_action", elseAction);

        ServerPlayer player = mock(ServerPlayer.class);
        ActionParser.parse(actionJson, "test:if_else_missing_condition").execute(player);

        verify(player, never()).heal(2.0F);
        verify(player).heal(4.0F);
    }

    @Test
    void ifElseListSkipsBranchWithoutCondition() {
        JsonObject actionJson = new JsonObject();
        actionJson.addProperty("type", "origins:if_else_list");

        JsonArray branches = new JsonArray();

        JsonObject firstBranch = new JsonObject();
        JsonObject firstAction = new JsonObject();
        firstAction.addProperty("type", "origins:heal");
        firstAction.addProperty("amount", 1.0F);
        firstBranch.add("action", firstAction);
        branches.add(firstBranch);

        JsonObject secondBranch = new JsonObject();
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "origins:constant");
        condition.addProperty("value", true);
        secondBranch.add("condition", condition);
        JsonObject secondAction = new JsonObject();
        secondAction.addProperty("type", "origins:heal");
        secondAction.addProperty("amount", 3.0F);
        secondBranch.add("action", secondAction);
        branches.add(secondBranch);

        actionJson.add("actions", branches);

        ServerPlayer player = mock(ServerPlayer.class);
        ActionParser.parse(actionJson, "test:if_else_list_missing_condition").execute(player);

        verify(player, never()).heal(1.0F);
        verify(player).heal(3.0F);
    }

    @Test
    void parseFieldRunsEveryActionInArrayInOrder() {
        JsonObject parent = new JsonObject();

        JsonArray actions = new JsonArray();
        JsonObject first = new JsonObject();
        first.addProperty("type", "origins:heal");
        first.addProperty("amount", 1.0F);
        actions.add(first);
        JsonObject second = new JsonObject();
        second.addProperty("type", "origins:heal");
        second.addProperty("amount", 2.0F);
        actions.add(second);
        parent.add("entity_action", actions);

        ServerPlayer player = mock(ServerPlayer.class);
        ActionParser.parseField(parent, "entity_action", "test:array_field").execute(player);

        verify(player).heal(1.0F);
        verify(player).heal(2.0F);
    }

    @Test
    void parseFieldStillAcceptsSingleObject() {
        JsonObject parent = new JsonObject();
        JsonObject action = new JsonObject();
        action.addProperty("type", "origins:heal");
        action.addProperty("amount", 5.0F);
        parent.add("entity_action", action);

        ServerPlayer player = mock(ServerPlayer.class);
        ActionParser.parseField(parent, "entity_action", "test:object_field").execute(player);

        verify(player).heal(5.0F);
    }

    @Test
    void parseFieldMissingFieldIsNoop() {
        JsonObject parent = new JsonObject();
        ServerPlayer player = mock(ServerPlayer.class);
        // Must not throw and must be the shared no-op singleton.
        ActionParser.parseField(parent, "entity_action", "test:missing_field").execute(player);
        verify(player, never()).heal(org.mockito.ArgumentMatchers.anyFloat());
    }
}
