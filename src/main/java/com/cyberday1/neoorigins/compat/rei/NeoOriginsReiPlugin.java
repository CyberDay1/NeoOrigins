package com.cyberday1.neoorigins.compat.rei;

import com.cyberday1.neoorigins.content.ModItems;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

/**
 * Roughly Enough Items integration — soft dependency, dormant if REI is absent.
 *
 * <p>Adds the Orb of Origin to REI's entry list. Client-only; REI discovers this
 * class through the {@link REIPluginClient} annotation, so it is never
 * classloaded when REI is not installed.
 *
 * <p>REI is compile-only. Its 26.1 line ({@code 26.1.819}, which declares
 * {@code minecraft [26.1.2,)}) pulls {@code cloth-config-neoforge 26.1.154} and
 * {@code architectury-neoforge 20.0.6} onto the compile classpath transitively;
 * none of them are bundled or present at runtime.
 */
@REIPluginClient
public class NeoOriginsReiPlugin implements REIClientPlugin {

    @Override
    public void registerEntries(EntryRegistry registry) {
        registry.addEntry(EntryStacks.of(ModItems.ORB_OF_ORIGIN.get()));
    }
}
