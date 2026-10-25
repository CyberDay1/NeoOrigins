package com.cyberday1.neoorigins.compat.kubejs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KubeJS calls every plugin's {@code clearCaches()} at the top of each script reload,
 * and the SERVER scripts reload at world load, long after {@code startup_scripts/} have
 * run for their only time this session. Wiping the whole registry there made
 * {@code NeoOrigins.registerPower} from a startup script a silent no-op: the same code
 * in {@code server_scripts/} worked, because it ran after the wipe.
 */
class JsPowerRegistryReloadTest {

    /** All three hooks default to no-ops, so the interface is not a lambda target. */
    private static final JsPassivePower PASSIVE = new JsPassivePower() {};

    @AfterEach
    void tearDown() {
        JsPowerRegistry.clearAll();
        KubeJSCallbacks.clearAll();
    }

    @Test
    void aStartupRegistrationSurvivesTheServerScriptReload() {
        JsPowerRegistry.registerPassive("mypack:aura", PASSIVE, true);
        JsPowerRegistry.registerActive("mypack:blink", player -> true, true);
        KubeJSCallbacks.register("mypack:shout", player -> {}, true);

        JsPowerRegistry.clearReloadable();
        KubeJSCallbacks.clearReloadable();

        assertTrue(JsPowerRegistry.hasPassive("mypack:aura"), "startup passive power was dropped on reload");
        assertTrue(JsPowerRegistry.hasActive("mypack:blink"), "startup active power was dropped on reload");
        assertTrue(KubeJSCallbacks.has("mypack:shout"), "startup callback was dropped on reload");
    }

    @Test
    void aServerRegistrationIsDroppedSoItCanBeReplaced() {
        JsPowerRegistry.registerPassive("mypack:aura", PASSIVE);
        KubeJSCallbacks.register("mypack:shout", player -> {});

        JsPowerRegistry.clearReloadable();
        KubeJSCallbacks.clearReloadable();

        assertFalse(JsPowerRegistry.hasPassive("mypack:aura"), "a reloadable handler must not outlive its script");
        assertFalse(KubeJSCallbacks.has("mypack:shout"));
    }

    @Test
    void aServerScriptCanTakeOverAStartupId() {
        JsPowerRegistry.registerPassive("mypack:aura", PASSIVE, true);
        JsPowerRegistry.registerPassive("mypack:aura", PASSIVE, false);

        JsPowerRegistry.clearReloadable();

        assertFalse(JsPowerRegistry.hasPassive("mypack:aura"),
            "the last registration owns the id, so a server script's goes with its reload");
    }
}
