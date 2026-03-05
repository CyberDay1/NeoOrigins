package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player state attachments for Route B powers:
 *   ResourceState — integer resource bar values keyed by power ID string
 *   ToggleState   — boolean toggle states keyed by power ID string
 */
public class CompatAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NeoOrigins.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResourceState>> RESOURCE_STATE =
        ATTACHMENT_TYPES.register("resource_state", () ->
            AttachmentType.builder(ResourceState::new)
                .serialize(new IAttachmentSerializer<>() {
                    @Override
                    public ResourceState read(IAttachmentHolder holder, ValueInput input) {
                        return input.read("rs", ResourceState.CODEC).orElseGet(ResourceState::new);
                    }
                    @Override
                    public boolean write(ResourceState attachment, ValueOutput output) {
                        output.store("rs", ResourceState.CODEC, attachment);
                        return true;
                    }
                })
                .copyOnDeath()
                .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ToggleState>> TOGGLE_STATE =
        ATTACHMENT_TYPES.register("toggle_state", () ->
            AttachmentType.builder(ToggleState::new)
                .serialize(new IAttachmentSerializer<>() {
                    @Override
                    public ToggleState read(IAttachmentHolder holder, ValueInput input) {
                        return input.read("ts", ToggleState.CODEC).orElseGet(ToggleState::new);
                    }
                    @Override
                    public boolean write(ToggleState attachment, ValueOutput output) {
                        output.store("ts", ToggleState.CODEC, attachment);
                        return true;
                    }
                })
                .copyOnDeath()
                .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static AttachmentType<ResourceState> resourceState() { return RESOURCE_STATE.get(); }
    public static AttachmentType<ToggleState>   toggleState()   { return TOGGLE_STATE.get(); }

    // ---- ResourceState ----

    public static class ResourceState {
        private final Map<String, Integer> values = new HashMap<>();

        public static final Codec<ResourceState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                .optionalFieldOf("values", Map.of())
                .forGetter(s -> Map.copyOf(s.values))
        ).apply(inst, map -> {
            ResourceState state = new ResourceState();
            state.values.putAll(map);
            return state;
        }));

        public int get(String key, int defaultValue) { return values.getOrDefault(key, defaultValue); }
        public void set(String key, int value)       { values.put(key, value); }

        public void clampedAdd(String key, int delta, int min, int max) {
            int cur = values.getOrDefault(key, 0);
            values.put(key, Math.max(min, Math.min(max, cur + delta)));
        }
    }

    // ---- ToggleState ----

    public static class ToggleState {
        private final Map<String, Boolean> states = new HashMap<>();

        public static final Codec<ToggleState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.BOOL)
                .optionalFieldOf("states", Map.of())
                .forGetter(s -> Map.copyOf(s.states))
        ).apply(inst, map -> {
            ToggleState state = new ToggleState();
            state.states.putAll(map);
            return state;
        }));

        public boolean isActive(String key, boolean defaultValue) {
            return states.getOrDefault(key, defaultValue);
        }

        public boolean toggle(String key, boolean defaultValue) {
            boolean next = !states.getOrDefault(key, defaultValue);
            states.put(key, next);
            return next;
        }

        public void set(String key, boolean value) { states.put(key, value); }
    }
}
