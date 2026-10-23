package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Bumped on every tag update so tag-derived caches (the edible-item use
 * animation) rebuild. A client that joins a server gets its tags without a
 * power reload, so the power version alone would leave them stale.
 *
 * <p>Read from common code (ItemStack mixin): keep this free of client-only classes.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public final class ClientTagEpoch {

    private static volatile int epoch = 0;

    private ClientTagEpoch() {}

    public static int get() { return epoch; }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        epoch++;
    }
}
