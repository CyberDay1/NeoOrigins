package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.power.*;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.cyberday1.neoorigins.rig.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.PowerReloadReconciler;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.*;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddedPowerTest {
    static final ResourceLocation ROOT = id("parent");
    static final ResourceLocation TYPE_ID = id("wrapper");
    static final ResourceLocation SOURCE = id("source");
    static final PowerDisplay DISPLAY = PowerDisplay.of(Component.literal("Parent"), Component.empty());
    static final ParentType TYPE = new ParentType();
    static boolean admin, toggles;
    MockedStatic<PowerTypes> registry;
    @TempDir Path pack;

    record Config(EmbeddedPower child, boolean managed) implements PowerConfiguration {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
            NeoOriginsAPI.powerCodec("child").fieldOf("child").forGetter(Config::child),
            Codec.BOOL.optionalFieldOf("managed", false).forGetter(Config::managed)
        ).apply(i, Config::new));
    }
    static final class ParentType extends PowerType<Config> {
        @Override public Codec<Config> codec() { return Config.CODEC; }
        @Override public boolean isActivePower(Config c) { return true; }
        @Override public void onGranted(ServerPlayer p, Config c) {
            if (c.managed()) NeoOriginsAPI.grantPower(p, c.child().id(), id("parent_owner"));
            else c.child().holder().onGranted(p);
        }
        @Override public void onRevoked(ServerPlayer p, Config c) {
            if (c.managed()) NeoOriginsAPI.revokePower(p, c.child().id(), id("parent_owner"));
            else c.child().holder().onRevoked(p);
        }
        @Override public void onTick(ServerPlayer p, Config c) { if (!c.managed()) c.child().holder().onTick(p); }
        @Override public void onActivated(ServerPlayer p, Config c) { c.child().holder().onActivated(p); }
    }
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("embeddedtest", path); }
    static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    static JsonObject parent(JsonElement child) {
        JsonObject body = new JsonObject(); body.addProperty("type", TYPE_ID.toString()); body.add("child", child); return body;
    }
    static ServerPlayer player() {
        var p = PlayerLifecycle.realPlayer(); p.connection = Mockito.mock(ServerGamePacketListenerImpl.class); return p;
    }
    static EmbeddedPower child(ResourceLocation parent) {
        return ((Config) PowerDataManager.INSTANCE.getPower(parent).config()).child();
    }
    @BeforeAll static void setup() {
        RealDatapack.load();
        admin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        toggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
    }
    @AfterAll static void unload() {
        if (admin) InMemoryConfig.unload(AdminConfig.SPEC);
        if (toggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
    }
    @BeforeEach void registerType() {
        registry = Mockito.mockStatic(PowerTypes.class, Mockito.CALLS_REAL_METHODS);
        registry.when(() -> PowerTypes.get(TYPE_ID)).thenReturn(TYPE);
    }
    @AfterEach void restore() {
        PowerDataManager.INSTANCE.unregisterCodePower(ROOT);
        PowerDataManager.INSTANCE.unregisterCodePower(id("second"));
        RealDatapack.restoreShipped();
        registry.close();
    }
    void write(String name, JsonObject body) throws Exception {
        Path dir = Files.createDirectories(pack.resolve("data/embeddedtest/origins/powers"));
        Files.writeString(dir.resolve(name + ".json"), body.toString());
    }
    JsonObject hp() {
        return PowerDataManager.INSTANCE.getRawPowerJson(ResourceLocation.parse("neoorigins:abyssal_apex_hp"));
    }

    @Test void inlinePowerHasStableIdentityAndDoesNotGrantItself() throws Exception {
        JsonObject body = parent(hp()); write("parent", body); RealDatapack.loadWith(pack);
        EmbeddedPower child = child(ROOT);
        assertEquals(id("parent/_embedded/child"), child.id());
        var p = player();
        assertEquals(20, p.getMaxHealth());
        assertFalse(ActiveOriginService.activePowerIds(p).contains(child.id()));
        assertEquals(body.get("child"), NeoOriginsAPI.powerCodec("child").encodeStart(JsonOps.INSTANCE, child).getOrThrow());
        assertEquals(GrantPowerResult.GRANTED, NeoOriginsAPI.grantPower(p, child.id(), SOURCE));
        assertEquals(26, p.getMaxHealth());
        assertTrue(PowerDataManager.INSTANCE.getAllPowers().containsKey(child.id()), "client registry includes explicit child grants");
        NeoOriginsAPI.grantPower(p, child.id(), id("second_source"));
        NeoOriginsAPI.revokePower(p, child.id(), SOURCE);
        assertEquals(26, p.getMaxHealth());
        NeoOriginsAPI.revokePower(p, child.id(), id("second_source"));
        assertEquals(20, p.getMaxHealth());
        RealDatapack.loadWith(pack);
        assertEquals(child.id(), child(ROOT).id());
    }

    @Test void nestedInlineChildrenAndSameTypeResourcesStayIndependent() throws Exception {
        JsonObject resource = json("{\"type\":\"neoorigins:resource\",\"max\":50,\"start\":9}");
        write("parent", parent(parent(resource))); write("second", parent(resource));
        RealDatapack.loadWith(pack);
        EmbeddedPower inner = ((Config) child(ROOT).holder().config()).child();
        EmbeddedPower other = child(id("second"));
        assertNotEquals(inner.id(), other.id());
        assertEquals(inner.id().toString(), ((ResourcePower.Config) inner.holder().config()).powerId());
        assertEquals(other.id().toString(), ((ResourcePower.Config) other.holder().config()).powerId());
    }

    @Test void referencesResolveRegardlessOfFileOrderAndKeepTheirJson() throws Exception {
        write("parent", parent(new JsonPrimitive("embeddedtest:second"))); write("second", hp());
        RealDatapack.loadWith(pack);
        assertEquals(id("second"), child(ROOT).id());
        assertSame(PowerDataManager.INSTANCE.getPower(id("second")), child(ROOT).holder());
        assertEquals(new JsonPrimitive("embeddedtest:second"), child(ROOT).definition());
    }

    @Test void missingAndCyclicReferencesDisableParentsWithoutOrphans() throws Exception {
        write("parent", parent(parent(new JsonPrimitive("embeddedtest:missing"))));
        RealDatapack.loadWith(pack);
        assertNull(PowerDataManager.INSTANCE.getPower(ROOT));
        assertNull(PowerDataManager.INSTANCE.getPower(id("parent/_embedded/child")));
        write("parent", parent(new JsonPrimitive("embeddedtest:second")));
        write("second", parent(new JsonPrimitive("embeddedtest:parent")));
        RealDatapack.loadWith(pack);
        assertNull(PowerDataManager.INSTANCE.getPower(ROOT));
        assertNull(PowerDataManager.INSTANCE.getPower(id("second")));
    }

    @Test void editorDecodingDoesNotPublishDefinitions() {
        int count = PowerDataManager.INSTANCE.getAllPowers().size();
        var decoded = Config.CODEC.parse(JsonOps.INSTANCE, parent(hp())).getOrThrow();
        assertThrows(IllegalStateException.class, () -> decoded.child().id());
        assertEquals(count, PowerDataManager.INSTANCE.getAllPowers().size());
        assertEquals(hp(), decoded.child().definition());
    }

    @Test void codeRegisteredParentKeepsChildrenAndUpkeepAcrossReload() {
        NeoOriginsAPI.registerPowerFromJson(ROOT, TYPE,
            parent(json("{\"type\":\"neoorigins:toggle\",\"hunger_cost\":2,\"toggle_messages\":false}")), DISPLAY);
        ResourceLocation child = child(ROOT).id();
        assertEquals(2, com.cyberday1.neoorigins.power.util.PowerUpkeep.forPower(child).hungerCost());
        RealDatapack.restoreShipped();
        assertNotNull(PowerDataManager.INSTANCE.getPower(child));
        assertEquals(2, com.cyberday1.neoorigins.power.util.PowerUpkeep.forPower(child).hungerCost());
        PowerDataManager.INSTANCE.unregisterCodePower(ROOT);
        assertNull(PowerDataManager.INSTANCE.getPower(child));
    }

    @Test void codeRegistrationCannotClaimGeneratedChildIds() {
        ResourceLocation reserved = id("parent/_embedded/child");
        JsonObject definition = parent(json("{\"type\":\"neoorigins:toggle\",\"hunger_cost\":2}"));
        Config probe = Config.CODEC.parse(JsonOps.INSTANCE, definition).getOrThrow();
        assertThrows(IllegalArgumentException.class,
            () -> NeoOriginsAPI.registerPower(reserved, TYPE, probe, DISPLAY));
        assertThrows(IllegalArgumentException.class,
            () -> NeoOriginsAPI.registerPowerFromJson(reserved, TYPE, definition, DISPLAY));
        assertNull(PowerDataManager.INSTANCE.getPower(reserved));
        assertNull(PowerDataManager.INSTANCE.getPower(id("parent/_embedded/child/_embedded/child")));
        NeoOriginsAPI.registerPowerFromJson(ROOT, TYPE, definition, DISPLAY);
        assertNotNull(PowerDataManager.INSTANCE.getPower(reserved));
        assertEquals(2, com.cyberday1.neoorigins.power.util.PowerUpkeep.forPower(reserved).hungerCost());
    }

    @Test void reloadRevokesTheOldChildBeforeApplyingTheNewOne() throws Exception {
        write("parent", parent(hp())); RealDatapack.loadWith(pack);
        var p = player(); NeoOriginsAPI.grantPower(p, ROOT, SOURCE);
        assertEquals(26, p.getMaxHealth());
        var before = PowerDataManager.INSTANCE.definitions();
        write("parent", parent(json("{\"type\":\"neoorigins:active_ability\",\"entity_action\":{\"type\":\"neoorigins:nothing\"}}")));
        RealDatapack.loadWith(pack);
        assertEquals(1, PowerReloadReconciler.reconcile(p, before, PowerDataManager.INSTANCE.definitions(), Set.of(ROOT), Set.of(ROOT)));
        assertEquals(20, p.getMaxHealth(), "revocation must use the old child, not the new nothing power");
        NeoOriginsAPI.revokePower(p, ROOT, SOURCE);
    }

    @Test void referencedChangesAlsoChangeTheParentFingerprint() throws Exception {
        write("parent", parent(new JsonPrimitive("embeddedtest:second"))); write("second", hp());
        RealDatapack.loadWith(pack);
        var p = player(); NeoOriginsAPI.grantPower(p, ROOT, SOURCE);
        var before = PowerDataManager.INSTANCE.definitions();
        write("second", json("{\"type\":\"neoorigins:active_ability\",\"entity_action\":{\"type\":\"neoorigins:nothing\"}}")); RealDatapack.loadWith(pack);
        var after = PowerDataManager.INSTANCE.definitions();
        assertNotEquals(before.fingerprint(ROOT), after.fingerprint(ROOT));
        PowerReloadReconciler.reconcile(p, before, after, Set.of(ROOT), Set.of(ROOT));
        assertEquals(20, p.getMaxHealth());
        NeoOriginsAPI.revokePower(p, ROOT, SOURCE);
    }

    @Test void anInlineConditionStillGatesActivation() throws Exception {
        write("parent", parent(json("""
            {"type":"neoorigins:active_ability","cooldown_ticks":0,
             "power_condition":{"type":"neoorigins:constant","value":true},
             "entity_action":{"type":"neoorigins:set_fall_distance","fall_distance":9}}
            """))); RealDatapack.loadWith(pack);
        var p = player(); PowerDataManager.INSTANCE.getPower(ROOT).onActivated(p);
        assertEquals(0, p.fallDistance, "power_condition uses the ordinary DENY default");
    }

    @Test void invalidInlineTypeDoesNotLeaveARegisteredParent() throws Exception {
        write("parent", parent(json("{\"type\":\"embeddedtest:missing\"}"))); RealDatapack.loadWith(pack);
        assertNull(PowerDataManager.INSTANCE.getPower(ROOT));
        assertNull(PowerDataManager.INSTANCE.getPower(id("parent/_embedded/child")));
        assertThrows(IllegalArgumentException.class, () -> NeoOriginsAPI.powerCodec("../child"));
    }

    @Test void removingAParentDefinitionRevokesAnIndependentlyGrantedChild() throws Exception {
        write("parent", parent(hp())); RealDatapack.loadWith(pack);
        ResourceLocation childId = child(ROOT).id();
        var p = player(); NeoOriginsAPI.grantPower(p, childId, SOURCE);
        var before = PowerDataManager.INSTANCE.definitions();
        RealDatapack.restoreShipped();
        PowerReloadReconciler.reconcile(p, before, PowerDataManager.INSTANCE.definitions(), Set.of(childId), Set.of(childId));
        assertEquals(20, p.getMaxHealth());
        assertNull(PowerDataManager.INSTANCE.getPower(childId));
        NeoOriginsAPI.revokePower(p, childId, SOURCE);
    }

    @Test void unchangedReloadLeavesParentAndChildStateAlone() throws Exception {
        write("parent", parent(hp())); RealDatapack.loadWith(pack);
        var p = player(); NeoOriginsAPI.grantPower(p, ROOT, SOURCE);
        var before = PowerDataManager.INSTANCE.definitions();
        RealDatapack.loadWith(pack);
        assertEquals(0, PowerReloadReconciler.reconcile(p, before, PowerDataManager.INSTANCE.definitions(), Set.of(ROOT), Set.of(ROOT)));
        assertEquals(26, p.getMaxHealth());
        NeoOriginsAPI.revokePower(p, ROOT, SOURCE);
    }

    @Test void overridingACodeParentHidesItsOldChildrenUntilTheOverrideIsRemoved() throws Exception {
        NeoOriginsAPI.registerPowerFromJson(ROOT, TYPE, parent(hp()), DISPLAY);
        ResourceLocation childId = child(ROOT).id();
        write("parent", json("{\"type\":\"neoorigins:active_ability\",\"entity_action\":{\"type\":\"neoorigins:nothing\"}}")); RealDatapack.loadWith(pack);
        assertNull(PowerDataManager.INSTANCE.getPower(childId));
        RealDatapack.restoreShipped();
        assertNotNull(PowerDataManager.INSTANCE.getPower(childId));
    }

    @Test void excessiveNestingFailsWithoutPublishingChildren() throws Exception {
        JsonObject body = hp();
        for (int i = 0; i < 35; i++) body = parent(body);
        write("parent", body); RealDatapack.loadWith(pack);
        assertNull(PowerDataManager.INSTANCE.getPower(ROOT));
        assertFalse(PowerDataManager.INSTANCE.getAllPowers().keySet().stream()
            .anyMatch(p -> p.getNamespace().equals("embeddedtest")));
    }

    @Test void forwardedChildrenHaveIndependentCooldowns() throws Exception {
        JsonObject ability = json("""
            {"type":"neoorigins:active_ability","cooldown_ticks":40,
             "entity_action":{"type":"neoorigins:set_fall_distance","fall_distance":7}}
            """);
        write("parent", parent(ability)); write("second", parent(ability)); RealDatapack.loadWith(pack);
        var p = player();
        PowerDataManager.INSTANCE.getPower(ROOT).onActivated(p);
        assertEquals(7, p.fallDistance);
        p.fallDistance = 0;
        PowerDataManager.INSTANCE.getPower(ROOT).onActivated(p);
        assertEquals(0, p.fallDistance, "the first child is still on cooldown");
        PowerDataManager.INSTANCE.getPower(id("second")).onActivated(p);
        assertEquals(7, p.fallDistance, "a different child of the same type has its own cooldown");
        assertNull(PowerHolder.currentDispatchId(), "nested dispatch restores its parent context");
    }

    @Test void childGrantsPersistAndLayerChangesOnlyRemoveTheAttachedSource() throws Exception {
        write("parent", parent(hp())); RealDatapack.loadWith(pack);
        var p = player();
        var childId = child(ROOT).id();
        var layer = ResourceLocation.parse("neoorigins:origin");
        NeoOriginsAPI.grantPower(p, childId, SOURCE, layer);
        NeoOriginsAPI.grantPower(p, childId, id("independent"));
        var data = p.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
        var codec = com.cyberday1.neoorigins.attachment.PlayerOriginData.CODEC;
        var saved = codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var restored = codec.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(data.getPowerGrants(), restored.getPowerGrants());
        ActiveOriginService.applyOriginPowers(p, layer, null, ResourceLocation.parse("neoorigins:human"));
        assertTrue(NeoOriginsAPI.grantedPowers(p, SOURCE).isEmpty());
        assertTrue(NeoOriginsAPI.grantedPowers(p, id("independent")).contains(childId));
        assertEquals(26, p.getMaxHealth());
        NeoOriginsAPI.revokePower(p, childId, id("independent"));
        assertEquals(20, p.getMaxHealth());
    }

    @Test void parentOwnedGrantsReconcileWithoutKeepingOldEffects() throws Exception {
        JsonObject body = parent(hp()); body.addProperty("managed", true);
        write("parent", body); RealDatapack.loadWith(pack);
        var p = player(); NeoOriginsAPI.grantPower(p, ROOT, SOURCE);
        var childId = child(ROOT).id();
        assertEquals(26, p.getMaxHealth());
        var before = PowerDataManager.INSTANCE.definitions();
        var held = Set.copyOf(ActiveOriginService.activePowerIds(p));
        assertEquals(Set.of(ROOT, childId), held);
        JsonObject replacement = parent(json("{\"type\":\"neoorigins:active_ability\",\"entity_action\":{\"type\":\"neoorigins:nothing\"}}"));
        replacement.addProperty("managed", true);
        write("parent", replacement); RealDatapack.loadWith(pack);
        PowerReloadReconciler.reconcile(p, before, PowerDataManager.INSTANCE.definitions(), held, held);
        assertEquals(20, p.getMaxHealth());
        assertEquals(Set.of(childId), NeoOriginsAPI.grantedPowers(p, id("parent_owner")));
        NeoOriginsAPI.revokePower(p, ROOT, SOURCE);
        assertTrue(NeoOriginsAPI.grantedPowers(p, id("parent_owner")).isEmpty());
    }

    @Test void sharedReferenceGraphsHaveBoundedFingerprintsAndDeepReferencesDoNotOverflow() {
        var holders = new java.util.HashMap<ResourceLocation, PowerHolder<?>>();
        var links = new java.util.HashMap<ResourceLocation, java.util.List<ResourceLocation>>();
        var template = PowerDataManager.INSTANCE.getPower(ResourceLocation.parse("neoorigins:abyssal_apex_hp"));
        for (int i = 0; i < 2000; i++) {
            holders.put(id("node_" + i), template);
            if (i > 0) links.put(id("node_" + i), java.util.List.of(id("node_" + (i - 1)), id("node_" + (i - 1))));
        }
        assertTrue(EmbeddedPowerLoading.invalid(holders, links).isEmpty());
        var definitions = new PowerDataManager.Definitions(java.util.Map.of(), java.util.Map.of(),
            java.util.Map.of(), java.util.Map.of(), holders, links);
        assertEquals(64, definitions.fingerprint(id("node_1999")).length());
    }
}
