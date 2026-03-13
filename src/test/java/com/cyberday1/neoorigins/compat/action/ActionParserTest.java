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
}
