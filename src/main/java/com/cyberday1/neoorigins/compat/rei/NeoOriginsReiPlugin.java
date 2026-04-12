package com.cyberday1.neoorigins.compat.rei;

import com.cyberday1.neoorigins.content.ModItems;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

@REIPluginClient
public class NeoOriginsReiPlugin implements REIClientPlugin {

    @Override
    public void registerEntries(EntryRegistry registry) {
        registry.addEntry(EntryStacks.of(ModItems.ORB_OF_ORIGIN.get()));
    }
}
