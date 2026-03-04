package com.cyberday1.neoorigins.attachment;

import com.cyberday1.neoorigins.NeoOrigins;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class OriginAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NeoOrigins.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerOriginData>> ORIGIN_DATA =
        ATTACHMENT_TYPES.register("origin_data", () ->
            AttachmentType.builder(PlayerOriginData::new)
                .serialize(PlayerOriginData.CODEC)
                .copyOnDeath()
                .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    /** Convenience accessor */
    public static AttachmentType<PlayerOriginData> originData() {
        return ORIGIN_DATA.get();
    }
}
