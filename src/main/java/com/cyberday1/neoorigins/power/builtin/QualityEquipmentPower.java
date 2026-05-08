package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Blacksmith quality craftsmanship — equipment the player crafts or upgrades
 * at a smithing table receives bonus attributes:
 * <ul>
 *   <li>Tools (pickaxe, axe, shovel, hoe): +mining speed</li>
 *   <li>Weapons (sword, axe, trident): +attack damage</li>
 *   <li>Armor (helmet, chest, legs, boots): +armor toughness</li>
 *   <li>All damageable items: +10% max durability</li>
 * </ul>
 *
 * <p>Called from {@link com.cyberday1.neoorigins.event.CraftingPowerEvents}
 * on crafting and smithing events.
 */
public class QualityEquipmentPower extends PowerType<QualityEquipmentPower.Config> {

    private static final Identifier QUALITY_MINING_SPEED =
        Identifier.fromNamespaceAndPath("neoorigins", "quality_mining_speed");
    private static final Identifier QUALITY_ATTACK_DAMAGE =
        Identifier.fromNamespaceAndPath("neoorigins", "quality_attack_damage");
    private static final Identifier QUALITY_ARMOR_TOUGHNESS =
        Identifier.fromNamespaceAndPath("neoorigins", "quality_armor_toughness");

    public record Config(
        double bonusMiningSpeed,
        double bonusAttackDamage,
        double bonusArmorToughness,
        double durabilityMultiplier,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("bonus_mining_speed", 0.25).forGetter(Config::bonusMiningSpeed),
            Codec.DOUBLE.optionalFieldOf("bonus_attack_damage", 0.20).forGetter(Config::bonusAttackDamage),
            Codec.DOUBLE.optionalFieldOf("bonus_armor_toughness", 1.0).forGetter(Config::bonusArmorToughness),
            Codec.DOUBLE.optionalFieldOf("durability_multiplier", 0.10).forGetter(Config::durabilityMultiplier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    public static void onItemCrafted(ServerPlayer player, ItemStack stack, Config config) {
        if (stack.isEmpty()) return;

        boolean isTool = stack.has(DataComponents.TOOL);
        boolean isWeapon = stack.has(DataComponents.WEAPON);
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        boolean isArmor = equippable != null && isArmorSlot(equippable.slot());

        if (!isTool && !isWeapon && !isArmor && !stack.isDamageableItem()) return;

        // Apply attribute modifiers via withModifierAdded (preserves existing modifiers)
        ItemAttributeModifiers modifiers = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        if (isTool && config.bonusMiningSpeed > 0) {
            modifiers = modifiers.withModifierAdded(Attributes.MINING_EFFICIENCY,
                new AttributeModifier(QUALITY_MINING_SPEED,
                    config.bonusMiningSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                EquipmentSlotGroup.MAINHAND);
        }

        if (isWeapon && config.bonusAttackDamage > 0) {
            modifiers = modifiers.withModifierAdded(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(QUALITY_ATTACK_DAMAGE,
                    config.bonusAttackDamage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                EquipmentSlotGroup.MAINHAND);
        }

        if (isArmor && config.bonusArmorToughness > 0) {
            EquipmentSlotGroup slot = switch (equippable.slot()) {
                case HEAD  -> EquipmentSlotGroup.HEAD;
                case CHEST -> EquipmentSlotGroup.CHEST;
                case LEGS  -> EquipmentSlotGroup.LEGS;
                case FEET  -> EquipmentSlotGroup.FEET;
                default    -> EquipmentSlotGroup.ARMOR;
            };
            modifiers = modifiers.withModifierAdded(Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(QUALITY_ARMOR_TOUGHNESS,
                    config.bonusArmorToughness, AttributeModifier.Operation.ADD_VALUE),
                slot);
        }

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);

        // Durability boost — increase max damage (durability) by the configured multiplier
        if (stack.isDamageableItem() && config.durabilityMultiplier > 0) {
            int baseDurability = stack.getMaxDamage();
            int bonus = (int) Math.ceil(baseDurability * config.durabilityMultiplier);
            stack.set(DataComponents.MAX_DAMAGE, baseDurability + bonus);
        }
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
            || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }
}
