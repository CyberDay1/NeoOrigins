package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.power.CodePowerOptions;
import com.cyberday1.neoorigins.api.power.PowerDisplay;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import com.cyberday1.neoorigins.power.builtin.TogglePower;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.cyberday1.neoorigins.power.util.PowerUpkeep;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Code powers get the condition gate, toggle upkeep, own id and variable declaration a JSON power gets. */
class CodePowerOptionsTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("testmod", "code_option_power");
    private static final PowerDisplay DISPLAY = PowerDisplay.of(Component.literal("Test"), Component.literal("Test"));

    @TempDir static Path pack;

    @BeforeAll
    static void load() {
        RealDatapack.load();
    }

    @AfterEach
    void unregister() {
        PowerDataManager.INSTANCE.unregisterCodePower(ID);
    }

    private static PowerType<?> type(String path) {
        return PowerTypes.get(ResourceLocation.fromNamespaceAndPath("neoorigins", path));
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    private static void registerToggle(CodePowerOptions options) {
        NeoOriginsAPI.registerPower(ID, (PowerType<TogglePower.Config>) type("toggle"),
            new TogglePower.Config(false, ""), DISPLAY, options);
    }

    @Test
    void aConditionGatesThePowerAndDefaultsToAllow() {
        AtomicBoolean underwater = new AtomicBoolean(false);
        registerToggle(CodePowerOptions.builder().condition(p -> underwater.get()).build());
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(ID);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        assertEquals(PowerHolder.ConditionMode.ALLOW, holder.conditionMode());
        assertFalse(holder.isConditionSatisfied(sp));
        underwater.set(true);
        assertTrue(holder.isConditionSatisfied(sp));
    }

    @Test
    void denyModeBlocksWhileTheConditionHolds() {
        registerToggle(CodePowerOptions.builder()
            .condition(NeoOriginsAPI.parseCondition(json("{\"type\": \"neoorigins:constant\", \"value\": true}"), "t"),
                PowerHolder.ConditionMode.DENY)
            .build());
        assertFalse(PowerDataManager.INSTANCE.getPower(ID).isConditionSatisfied(PlayerLifecycle.realPlayer()));
    }

    @Test
    void noOptionsMeansNoGate() {
        registerToggle(CodePowerOptions.NONE);
        assertNull(PowerDataManager.INSTANCE.getPower(ID).condition());
    }

    @Test
    void toggleUpkeepAndMessagesSurviveAReload() {
        registerToggle(CodePowerOptions.builder().hungerCost(2).upkeepInterval(40).toggleMessages(false).build());
        assertEquals(2, PowerUpkeep.forPower(ID).hungerCost());
        assertFalse(PowerUpkeep.sendsToggleMessages(ID));

        RealDatapack.restoreShipped(); // a full PowerDataManager.apply, which clears the upkeep table
        assertEquals(2, PowerUpkeep.forPower(ID).hungerCost());
        assertEquals(40, PowerUpkeep.forPower(ID).interval());
        assertFalse(PowerUpkeep.sendsToggleMessages(ID));

        PowerDataManager.INSTANCE.unregisterCodePower(ID);
        assertTrue(PowerUpkeep.forPower(ID).isFree());
        assertTrue(PowerUpkeep.sendsToggleMessages(ID));
    }

    @Test
    void aDatapackPowerWithTheSameIdTakesOverTheUpkeep() throws Exception {
        registerToggle(CodePowerOptions.builder().hungerCost(2).build());
        Path powers = Files.createDirectories(pack.resolve("data/testmod/origins/powers"));
        Files.writeString(powers.resolve("code_option_power.json"), "{\"type\": \"neoorigins:toggle\"}");
        try {
            RealDatapack.loadWith(pack);
            assertTrue(PowerUpkeep.forPower(ID).isFree(), "the overriding datapack toggle has no upkeep");
        } finally {
            RealDatapack.restoreShipped();
        }
        assertEquals(2, PowerUpkeep.forPower(ID).hungerCost(), "the code power's upkeep returns with it");
    }

    @Test
    void jsonConfigIsDecodedLikeADatapackPower() {
        NeoOriginsAPI.registerPowerFromJson(ID, type("toggle"), json(
            "{\"resource_cost\": \"testmod:mana\", \"resource_cost_amount\": 3, \"toggle_messages\": false,"
                + " \"power_condition\": {\"type\": \"neoorigins:constant\", \"value\": true}}"), DISPLAY);
        PowerUpkeep.Upkeep upkeep = PowerUpkeep.forPower(ID);
        assertEquals("testmod:mana", upkeep.resourceCost());
        assertEquals(3, upkeep.resourceCostAmount());
        assertFalse(PowerUpkeep.sendsToggleMessages(ID));
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(ID);
        assertEquals(PowerHolder.ConditionMode.DENY, holder.conditionMode(), "power_condition defaults to DENY, as in a datapack");
        assertFalse(holder.isConditionSatisfied(PlayerLifecycle.realPlayer()));
    }

    @Test
    void aResourceKnowsItsOwnIdEitherWay() {
        NeoOriginsAPI.registerPowerFromJson(ID, type("resource"), json("{\"max\": 50}"), DISPLAY);
        assertEquals(ID.toString(), ((ResourcePower.Config) PowerDataManager.INSTANCE.getPower(ID).config()).powerId());
        ResourcePower.Config decoded = (ResourcePower.Config) PowerDataManager.INSTANCE.getPower(ID).config();
        PowerDataManager.INSTANCE.unregisterCodePower(ID);

        @SuppressWarnings("unchecked")
        PowerType<ResourcePower.Config> resource = (PowerType<ResourcePower.Config>) type("resource");
        NeoOriginsAPI.registerPower(ID, resource, decoded.withPowerId("neoorigins:resource"), DISPLAY);
        assertEquals(ID.toString(), ((ResourcePower.Config) PowerDataManager.INSTANCE.getPower(ID).config()).powerId());
    }

    @Test
    void aVariableIsDeclaredAndStaysDeclaredAcrossAReload() {
        NeoOriginsAPI.registerPowerFromJson(ID, type("variable"), json("{\"start\": 3, \"min\": 0, \"max\": 9}"), DISPLAY);
        assertEquals(3, CompatAttachments.variableStart(ID.toString()));
        RealDatapack.restoreShipped();
        assertEquals(3, CompatAttachments.variableStart(ID.toString()));
        PowerDataManager.INSTANCE.unregisterCodePower(ID);
        assertFalse(CompatAttachments.isDeclaredVariable(ID.toString()));
    }

    @Test
    void badConfigJsonThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> NeoOriginsAPI.registerPowerFromJson(ID, type("variable"), json("{\"start\": \"abc\"}"), DISPLAY));
        assertNull(PowerDataManager.INSTANCE.getPower(ID));
    }
}
