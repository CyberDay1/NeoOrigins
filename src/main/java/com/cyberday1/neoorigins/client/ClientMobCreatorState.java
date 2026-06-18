package com.cyberday1.neoorigins.client;

/**
 * Client-side trampoline for opening the Mob Origin Creator. Kept in a
 * client-only class so the common-side payload handler never references a
 * {@code Screen} subclass directly (Dist-cleaner safety — same pattern as
 * {@link ClientOriginState#openEditorScreen()}). Save/Apply result feedback
 * reuses {@link ClientCreatorState} (the CreatorResultPayload is shared).
 */
public final class ClientMobCreatorState {

    private ClientMobCreatorState() {}

    public static void openMobCreatorScreen() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        ClientCreatorState.clear();
        mc.gui.setScreen(new com.cyberday1.neoorigins.screen.mobcreator.MobOriginCreatorScreen(
            mc.gui.screen(),
            new com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft()));
    }
}
