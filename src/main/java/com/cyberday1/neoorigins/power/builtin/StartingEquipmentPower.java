package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives the player a specific item (optionally enchanted) the first time this power is granted —
 * but only after the player has committed to their full origin selection. During the initial
 * picker walk-through the player can click through multiple origins before confirming; granting
 * items on each preview click creates a dupe where backing out and picking a different origin
 * leaves the original origin's items in the inventory (issue #22).
 *
 * <p>Gate: {@link PlayerOriginData#isHadAllOrigins()} must be true when onGranted fires.
 * {@link NeoOriginsNetwork#handleChooseOrigin} calls {@link #grantAllPending} once that flag
 * flips so any deferred grants catch up.
 *
 * <p>Dedup: {@code grantId} is stored in {@link PlayerOriginData#grantedEquipmentPowers} so the
 * same item can't be given twice. The set is cleared by the Orb of Origin and full
 * {@code /origin reset} so users re-pay for re-granted items.
 *
 * <p><b>Multi-item shape (v2.1.6):</b> in addition to the legacy single-item shape
 * ({@code item} + {@code count} + {@code enchantments} + {@code legacy_tag} +
 * {@code components} at the power root), this power also accepts a plural
 * {@code stacks} array of stack entries with the same per-entry field names.
 * Field names mirror the singular shape exactly so author muscle-memory carries
 * over; the per-power {@code grant_id} still dedups the whole bundle as a unit.
 * Apoli's {@code origins:starting_equipment} (whose stack entries use
 * {@code item} + {@code amount} + {@code tag}) is exploded into N synthetic
 * single-stack native powers by
 * {@link com.cyberday1.neoorigins.compat.OriginsStartingEquipmentExpander} —
 * the plural shape here is for hand-authored NeoOrigins-native packs that want
 * one power to grant several items.
 */
public class StartingEquipmentPower extends PowerType<StartingEquipmentPower.Config> {

    public record EnchantEntry(String id, int level) {
        public static final Codec<EnchantEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(EnchantEntry::id),
            Codec.INT.optionalFieldOf("level", 1).forGetter(EnchantEntry::level)
        ).apply(inst, EnchantEntry::new));
    }

    /**
     * One stack inside the plural {@code stacks} array. Field names mirror the
     * legacy singular shape at the power root so authors can lift a working
     * single-item config into the array unchanged.
     */
    public record StackEntry(
        String item,
        int count,
        List<EnchantEntry> enchantments,
        String legacyTag,
        String components
    ) {
        public static final Codec<StackEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("item").forGetter(StackEntry::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(StackEntry::count),
            EnchantEntry.CODEC.listOf().optionalFieldOf("enchantments", List.of()).forGetter(StackEntry::enchantments),
            Codec.STRING.optionalFieldOf("legacy_tag", "").forGetter(StackEntry::legacyTag),
            Codec.STRING.optionalFieldOf("components", "").forGetter(StackEntry::components)
        ).apply(inst, StackEntry::new));
    }

    public record Config(
        String grantId,
        // Legacy singular fields — kept optional so the plural `stacks` form
        // can omit them entirely. `item` empty + `stacks` empty is the
        // negative-no-op case and produces a runtime WARN.
        String item,
        List<EnchantEntry> enchantments,
        int count,
        String type,
        String legacyTag,
        String components,
        // Plural shape (v2.1.6). When non-empty, takes precedence over the
        // singular root fields and N items are granted in array order.
        List<StackEntry> stacks
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("grant_id").forGetter(Config::grantId),
            // Singular `item` is now optional so authors can use just `stacks`.
            // Validation that at least one of item/stacks is non-empty happens
            // at grant time with a WARN log — mirrors the parser-canonical
            // tolerance the rest of the compat layer applies.
            Codec.STRING.optionalFieldOf("item", "").forGetter(Config::item),
            EnchantEntry.CODEC.listOf().optionalFieldOf("enchantments", List.of()).forGetter(Config::enchantments),
            Codec.INT.optionalFieldOf("count", 1).forGetter(Config::count),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            // Apoli pack authors set NBT via a flat SNBT string (Potion type,
            // Enchantments list, display.Name, etc.). Translated into data
            // components at grant time via LegacyTagToComponents.
            Codec.STRING.optionalFieldOf("legacy_tag", "").forGetter(Config::legacyTag),
            // Arbitrary data components as SNBT — allows modded components like
            // irons_spellbooks:spell_container to be set directly on the item.
            // Parsed via DataComponentPatch.CODEC with registry ops at grant time.
            Codec.STRING.optionalFieldOf("components", "").forGetter(Config::components),
            StackEntry.CODEC.listOf().optionalFieldOf("stacks", List.of()).forGetter(Config::stacks)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        // Defer until the player has committed to their full origin set.
        // Otherwise a player can pick an origin, receive its items, click "back",
        // and pick a different origin — keeping the first origin's items. See
        // handleChooseOrigin which calls grantAllPending once hadAllOrigins flips.
        if (!data.isHadAllOrigins()) return;
        grantIfUngranted(player, config, data);
    }

    /**
     * Runs all currently-active StartingEquipmentPower grants for the player.
     * Called by handleChooseOrigin once the picker commits (hadAllOrigins true)
     * so previously-deferred grants catch up. Idempotent: grantId flags prevent
     * double-granting.
     */
    public static void grantAllPending(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        if (!data.isHadAllOrigins()) return;
        ActiveOriginService.forEachOfType(player, StartingEquipmentPower.class,
            cfg -> grantIfUngranted(player, cfg, data));
    }

    private static void grantIfUngranted(ServerPlayer player, Config config, PlayerOriginData data) {
        if (data.hasGrantedEquipment(config.grantId())) return;

        // Normalize the two accepted shapes to a single List<StackEntry>.
        // Plural `stacks` wins when present; otherwise the legacy singular
        // root fields are wrapped as a one-entry list.
        List<StackEntry> effective = effectiveStacks(config);
        if (effective.isEmpty()) {
            com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                "[starting_equipment] grantId '{}' has neither 'item' nor non-empty 'stacks' — nothing to grant",
                config.grantId());
            return;
        }

        boolean grantedAny = false;
        for (int i = 0; i < effective.size(); i++) {
            StackEntry entry = effective.get(i);
            if (grantOneStack(player, config.grantId(), i, entry)) {
                grantedAny = true;
            }
        }

        // Only mark the bundle granted if at least one stack actually went in.
        // A pure-fail bundle (all items missing from registry) is left
        // un-dedup'd so a /reload after fixing the typo can retry — same
        // forgiveness the singular path always had for registry misses.
        if (grantedAny) {
            data.markEquipmentGranted(config.grantId());
        }
    }

    private static List<StackEntry> effectiveStacks(Config config) {
        if (!config.stacks().isEmpty()) return config.stacks();
        if (!config.item().isEmpty()) {
            return List.of(new StackEntry(
                config.item(), config.count(), config.enchantments(),
                config.legacyTag(), config.components()));
        }
        return new ArrayList<>();
    }

    private static boolean grantOneStack(ServerPlayer player, String grantId, int index, StackEntry entry) {
        var itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(entry.item()));
        if (itemOpt.isEmpty()) {
            com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                "[starting_equipment] item '{}' not in registry for grantId '{}' stack[{}] — skipped",
                entry.item(), grantId, index);
            return false;
        }

        ItemStack stack = new ItemStack(itemOpt.get(), entry.count());

        if (!entry.enchantments().isEmpty()) {
            var enchLookup = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments.Mutable enchMutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (var ench : entry.enchantments()) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(ench.id()));
                enchLookup.get(key).ifPresent(h -> enchMutable.set(h, ench.level()));
            }
            stack.set(DataComponents.ENCHANTMENTS, enchMutable.toImmutable());
        }

        // Apply translated Apoli SNBT — Potion type, Enchantments list,
        // display.Name/Lore, etc. — via the shared legacy-tag bridge.
        if (!entry.legacyTag().isEmpty()) {
            com.cyberday1.neoorigins.compat.LegacyTagToComponents.applySnbt(
                stack, entry.legacyTag(), player.registryAccess());
        }

        // Apply arbitrary data components from SNBT string — supports modded
        // DataComponentTypes (e.g. irons_spellbooks:spell_container).
        if (!entry.components().isEmpty()) {
            try {
                CompoundTag parsed = TagParser.parseTag(entry.components());
                RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(
                    net.minecraft.nbt.NbtOps.INSTANCE, player.registryAccess());
                DataComponentPatch patch = DataComponentPatch.CODEC.parse(ops, parsed)
                    .getOrThrow(e -> new IllegalArgumentException("Failed to parse components: " + e));
                stack.applyComponents(patch);
            } catch (Exception e) {
                com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                    "[starting_equipment] failed to parse components for grantId '{}' stack[{}]: {}",
                    grantId, index, e.getMessage());
            }
        }

        player.addItem(stack);
        return true;
    }
}
