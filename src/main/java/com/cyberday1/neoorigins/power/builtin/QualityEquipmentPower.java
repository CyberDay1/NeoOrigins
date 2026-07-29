package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;

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

    private static final ResourceLocation QUALITY_MINING_SPEED =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "quality_mining_speed");
    private static final ResourceLocation QUALITY_ATTACK_DAMAGE =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "quality_attack_damage");
    // Armor toughness uses a UNIQUE id per equipment slot. An AttributeInstance
    // dedupes modifiers by their ResourceLocation id, so a single shared id would
    // collapse all four worn pieces of a full set into ONE +toughness instead of
    // four (GitHub full-set bug). Mining-speed/attack-damage stay single-id because
    // only one held item is ever in play, so they never collide.
    private static final ResourceLocation QUALITY_ARMOR_TOUGHNESS_HEAD =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "quality_armor_toughness_head");
    private static final ResourceLocation QUALITY_ARMOR_TOUGHNESS_CHEST =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "quality_armor_toughness_chest");
    private static final ResourceLocation QUALITY_ARMOR_TOUGHNESS_LEGS =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "quality_armor_toughness_legs");
    private static final ResourceLocation QUALITY_ARMOR_TOUGHNESS_FEET =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "quality_armor_toughness_feet");

    public record Config(
        double bonusMiningSpeed,
        double bonusAttackDamage,
        double bonusArmorToughness,
        double durabilityMultiplier,
        String type,
        java.util.List<String> interceptMenus
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("bonus_mining_speed", 0.25).forGetter(Config::bonusMiningSpeed),
            Codec.DOUBLE.optionalFieldOf("bonus_attack_damage", 0.20).forGetter(Config::bonusAttackDamage),
            Codec.DOUBLE.optionalFieldOf("bonus_armor_toughness", 1.0).forGetter(Config::bonusArmorToughness),
            Codec.DOUBLE.optionalFieldOf("durability_multiplier", 0.10).forGetter(Config::durabilityMultiplier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            // Data-driven soft-dep hook: menu-type ids (e.g. Overgeared's smithing
            // anvils) whose result slot should also trigger the quality buff when
            // taken. Unknown ids simply never match, so this is harmless when the
            // referenced mod is absent. Appended last to keep the CODEC field order
            // stable for existing packs.
            Codec.STRING.listOf().optionalFieldOf("intercept_menus", java.util.List.of())
                .forGetter(Config::interceptMenus)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Shared gear-eligibility test used by both the vanilla craft path
     * ({@link #onItemCrafted}) and the generic result-slot interceptor
     * ({@link com.cyberday1.neoorigins.event.CraftingPowerEvents#onGenericResultTake}).
     * Matches tools, weapons, armor, or any damageable item.
     */
    public static boolean isQualityEligible(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        boolean isTool = item instanceof DiggerItem || item instanceof ShearsItem;
        boolean isWeapon = item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem;
        boolean isArmor = item instanceof ArmorItem;
        return isTool || isWeapon || isArmor || stack.isDamageableItem();
    }

    public static void onItemCrafted(ServerPlayer player, ItemStack stack, Config config) {
        if (!isQualityEligible(stack)) return;
        Item item = stack.getItem();

        boolean isTool = item instanceof DiggerItem || item instanceof ShearsItem;
        boolean isWeapon = item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem;
        boolean isArmor = item instanceof ArmorItem;

        // Seed from the EFFECTIVE attribute modifiers, not the raw component.
        // Freshly crafted armor/tools usually have no ATTRIBUTE_MODIFIERS
        // component patch — their base stats come from
        // Item#getDefaultAttributeModifiers(stack). Reading the raw component
        // (or item.getDefaultAttributeModifiers() no-arg) returns EMPTY there,
        // so the stack.set(...) below would wipe all base armor/tool/weapon
        // stats (GitHub #89 / #67). ItemStack#getAttributeModifiers() resolves
        // component → item default → stack-sensitive default correctly.
        ItemAttributeModifiers modifiers = stack.getAttributeModifiers();

        if (isTool && config.bonusMiningSpeed > 0) {
            modifiers = stripModifier(modifiers, QUALITY_MINING_SPEED)
                .withModifierAdded(Attributes.MINING_EFFICIENCY,
                    new AttributeModifier(QUALITY_MINING_SPEED,
                        config.bonusMiningSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    EquipmentSlotGroup.MAINHAND);
        }

        if (isWeapon && config.bonusAttackDamage > 0) {
            modifiers = stripModifier(modifiers, QUALITY_ATTACK_DAMAGE)
                .withModifierAdded(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(QUALITY_ATTACK_DAMAGE,
                        config.bonusAttackDamage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    EquipmentSlotGroup.MAINHAND);
        }

        if (isArmor && config.bonusArmorToughness > 0) {
            var armorSlot = ((ArmorItem) item).getEquipmentSlot();
            EquipmentSlotGroup slot = switch (armorSlot) {
                case HEAD  -> EquipmentSlotGroup.HEAD;
                case CHEST -> EquipmentSlotGroup.CHEST;
                case LEGS  -> EquipmentSlotGroup.LEGS;
                case FEET  -> EquipmentSlotGroup.FEET;
                default    -> EquipmentSlotGroup.ARMOR;
            };
            // Per-slot id so a full worn set stacks to +4 instead of deduping to +1.
            ResourceLocation toughnessId = switch (armorSlot) {
                case HEAD  -> QUALITY_ARMOR_TOUGHNESS_HEAD;
                case CHEST -> QUALITY_ARMOR_TOUGHNESS_CHEST;
                case LEGS  -> QUALITY_ARMOR_TOUGHNESS_LEGS;
                case FEET  -> QUALITY_ARMOR_TOUGHNESS_FEET;
                default    -> QUALITY_ARMOR_TOUGHNESS_CHEST;
            };
            modifiers = stripModifier(modifiers, toughnessId)
                .withModifierAdded(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(toughnessId,
                        config.bonusArmorToughness, AttributeModifier.Operation.ADD_VALUE),
                    slot);
        }

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);

        // Durability boost — increase max damage (durability) by the configured
        // multiplier, computed against the ITEM'S OWN default durability (the
        // prototype value), never the stack's current component. Reading
        // stack.getMaxDamage() here re-reads any previously applied absolute
        // value, which compounds on re-application and goes stale after a
        // smithing upgrade (GitHub #103).
        if (stack.isDamageableItem() && config.durabilityMultiplier > 0) {
            applyDurabilityBonus(stack, config.durabilityMultiplier);
        }
    }

    /** CUSTOM_DATA key recording the quality durability multiplier applied to a stack. */
    private static final String DURABILITY_MULTIPLIER_KEY = "neoorigins:quality_durability_multiplier";

    /**
     * Recompute quality data when a smithing-table upgrade is taken (GitHub #103).
     * Vanilla smithing copies the source item's components onto the output, so the
     * absolute {@code MAX_DAMAGE} / {@code ATTRIBUTE_MODIFIERS} snapshots written
     * at craft time carry the OLD material's values — a netherite pickaxe kept
     * diamond-level durability forever. Re-derives both against the upgraded
     * item's own base stats. Called from
     * {@link com.cyberday1.neoorigins.mixin.SmithingMenuTakeMixin} via
     * {@link com.cyberday1.neoorigins.event.CraftingPowerEvents#onSmithingTake}
     * BEFORE the input slots shrink, so {@code baseInput} is the pre-upgrade item.
     */
    public static void onSmithingUpgrade(ItemStack baseInput, ItemStack result) {
        if (result.isEmpty()) return;
        refreshAttributeSnapshot(result);
        refreshDurability(baseInput, result);
    }

    /**
     * Apply the durability bonus relative to the item's prototype max damage and
     * record the multiplier in CUSTOM_DATA so a later smithing upgrade can
     * recompute against the upgraded item's base. Idempotent.
     */
    private static void applyDurabilityBonus(ItemStack stack, double multiplier) {
        int base = defaultMaxDamage(stack);
        if (base <= 0) return;
        int bonus = (int) Math.ceil(base * multiplier);
        stack.set(DataComponents.MAX_DAMAGE, base + bonus);
        net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, stack,
            tag -> tag.putDouble(DURABILITY_MULTIPLIER_KEY, multiplier));
    }

    /** The item's own (prototype) MAX_DAMAGE, ignoring any stack component patch. */
    private static int defaultMaxDamage(ItemStack stack) {
        return stack.getItem().components().getOrDefault(DataComponents.MAX_DAMAGE, 0);
    }

    /**
     * Re-derive the stack's durability after a smithing upgrade. With the
     * recorded multiplier the bonus is recomputed against the OUTPUT item's
     * prototype durability. Items quality-crafted before the marker existed
     * carry a locked absolute value with no way to recover the multiplier — for
     * those, when the copied component still equals the SOURCE item's modified
     * value but not the output's default, the component is dropped so the
     * upgraded item falls back to its own base durability (a blacksmith taking
     * the upgrade re-applies their bonus right after).
     */
    private static void refreshDurability(ItemStack baseInput, ItemStack result) {
        Integer component = result.get(DataComponents.MAX_DAMAGE);
        if (component == null) return;
        var tag = result.getOrDefault(DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (tag.contains(DURABILITY_MULTIPLIER_KEY)) {
            applyDurabilityBonus(result, tag.getDouble(DURABILITY_MULTIPLIER_KEY));
            return;
        }
        int outputDefault = defaultMaxDamage(result);
        if (outputDefault > 0 && component != outputDefault
                && !baseInput.isEmpty()
                && component == baseInput.getMaxDamage()
                && baseInput.getMaxDamage() != defaultMaxDamage(baseInput)) {
            result.remove(DataComponents.MAX_DAMAGE);
        }
    }

    /**
     * Rebuild the ATTRIBUTE_MODIFIERS snapshot after a smithing upgrade. The
     * craft-time snapshot is absolute — it bakes the SOURCE item's base stats in
     * alongside the quality modifiers, so an upgraded item kept e.g. diamond
     * attack damage. When the copied component contains quality modifiers, it is
     * rebuilt from the output item's own effective defaults plus the carried
     * quality modifiers (which are relative/flat bonuses and stay valid).
     * Components without any quality modifier are not ours — left untouched.
     */
    private static void refreshAttributeSnapshot(ItemStack result) {
        ItemAttributeModifiers component = result.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (component == null) return;
        java.util.List<ItemAttributeModifiers.Entry> quality = new java.util.ArrayList<>();
        for (var entry : component.modifiers()) {
            if (isQualityModifierId(entry.modifier().id())) {
                quality.add(entry);
            }
        }
        if (quality.isEmpty()) return;
        // Rebuild from the OUTPUT item's PROTOTYPE component. 1.21.1 tools and
        // armor bake their base attack-damage/speed/armor entries into the
        // item's default component map — NOT Item#getDefaultAttributeModifiers()
        // (which is EMPTY for them). ItemStack#remove() writes a REMOVAL patch
        // over the prototype rather than restoring it, so the previous
        // remove() + stack.getAttributeModifiers() rebuild resolved to EMPTY and
        // produced a quality-only component: the upgraded item lost its base
        // attribute lines (tooltip) and stats.
        ItemAttributeModifiers fresh = result.getItem().components()
            .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (fresh.modifiers().isEmpty()) {
            // Component-less items (modded, stack-sensitive defaults).
            fresh = result.getItem().getDefaultAttributeModifiers(result);
        }
        for (var entry : quality) {
            fresh = fresh.withModifierAdded(entry.attribute(), entry.modifier(), entry.slot());
        }
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, fresh);
    }

    /**
     * True for any modifier id this power applies: the single mining-speed and
     * attack-damage ids, plus all four per-slot armor-toughness ids. Used by the
     * smithing-upgrade re-derive path to recognize carried-over quality modifiers.
     */
    private static boolean isQualityModifierId(ResourceLocation id) {
        return id.equals(QUALITY_MINING_SPEED)
            || id.equals(QUALITY_ATTACK_DAMAGE)
            || id.equals(QUALITY_ARMOR_TOUGHNESS_HEAD)
            || id.equals(QUALITY_ARMOR_TOUGHNESS_CHEST)
            || id.equals(QUALITY_ARMOR_TOUGHNESS_LEGS)
            || id.equals(QUALITY_ARMOR_TOUGHNESS_FEET);
    }

    /** Rebuild an ItemAttributeModifiers without any entry whose modifier has the given ID. */
    private static ItemAttributeModifiers stripModifier(ItemAttributeModifiers modifiers, ResourceLocation id) {
        ItemAttributeModifiers result = ItemAttributeModifiers.EMPTY;
        for (var entry : modifiers.modifiers()) {
            if (!entry.modifier().id().equals(id)) {
                result = result.withModifierAdded(entry.attribute(), entry.modifier(), entry.slot());
            }
        }
        return result;
    }
}
