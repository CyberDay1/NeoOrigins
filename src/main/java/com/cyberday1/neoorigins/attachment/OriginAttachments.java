package com.cyberday1.neoorigins.attachment;

import com.cyberday1.neoorigins.NeoOrigins;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class OriginAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NeoOrigins.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerOriginData>> ORIGIN_DATA =
        ATTACHMENT_TYPES.register("origin_data", () ->
            AttachmentType.builder(PlayerOriginData::new)
                .serialize(new IAttachmentSerializer<>() {
                    @Override
                    public PlayerOriginData read(IAttachmentHolder holder, ValueInput input) {
                        return input.read("data", PlayerOriginData.CODEC).orElseGet(PlayerOriginData::new);
                    }

                    @Override
                    public boolean write(PlayerOriginData attachment, ValueOutput output) {
                        output.store("data", PlayerOriginData.CODEC, attachment);
                        return true;
                    }
                })
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
