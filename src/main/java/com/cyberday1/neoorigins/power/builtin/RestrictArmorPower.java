package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Prevents certain items from being equipped in certain slots. When a matching
 * item is put into a restricted slot, the power ejects it back to the player's
 * inventory (or drops it to the ground if inventory is full).
 *
 * <p>Each slot (head/chest/legs/feet/offhand/mainhand) can have an item-id or
 * tag filter. Matching items are rejected; non-matching pass through.
 *
 * <p>Pack authors typically pair this with a visual message on equip-reject via
 * an {@code action_on_event} on the same slot.
 *
 * <p>Handled via {@code LivingEquipmentChangeEvent} — see
 * {@code InteractionPowerEvents} for the dispatch wiring.
 */
public class RestrictArmorPower extends PowerType<RestrictArmorPower.Config> {

    public record SlotRestriction(
        String slot,
        Optional<Identifier> item,
        Optional<Identifier> tag
    ) {
        public static final Codec<SlotRestriction> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("slot").forGetter(SlotRestriction::slot),
            Identifier.CODEC.optionalFieldOf("item").forGetter(SlotRestriction::item),
            Identifier.CODEC.optionalFieldOf("tag").forGetter(SlotRestriction::tag)
        ).apply(inst, SlotRestriction::new));
    }

    public record Config(java.util.List<SlotRestriction> restrictions, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            SlotRestriction.CODEC.listOf().optionalFieldOf("restrictions", java.util.List.of())
                .forGetter(Config::restrictions),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /** True if the stack would be rejected by any restriction for the given slot. */
    public static boolean isRestricted(ItemStack stack, EquipmentSlot slot, Config config) {
        if (stack.isEmpty()) return false;
        String slotName = slot.getName();
        for (SlotRestriction r : config.restrictions()) {
            if (!r.slot().equalsIgnoreCase(slotName)) continue;
            if (r.item().isPresent()) {
                var holder = BuiltInRegistries.ITEM.get(r.item().get());
                if (holder.isPresent() && stack.is(holder.get())) return true;
            }
            if (r.tag().isPresent()) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, r.tag().get());
                if (stack.is(tag)) return true;
            }
            // If neither item nor tag is specified, reject all items in that slot.
            if (r.item().isEmpty() && r.tag().isEmpty()) return true;
        }
        return false;
    }
}
