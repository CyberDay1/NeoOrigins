package com.cyberday1.neoorigins.compat.registry;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

import java.util.Set;

/**
 * The four compat-verb registries — {@code neoorigins:action_type},
 * {@code condition_type}, {@code item_action_type}, {@code item_condition_type}
 * — standing alongside {@code PowerTypes.POWER_TYPES}.
 *
 * <p>Phase-1 keystone (see {@code planning/REGISTRY_REFACTOR_PLAN.md}): each
 * action/condition verb becomes a registered descriptor, making the verb set
 * extensible by addons (they register descriptors keyed on these
 * {@code REGISTRY_KEY}s, exactly as addon power types ride
 * {@code PowerTypes.REGISTRY_KEY}).
 *
 * <p><b>Behavior-neutral.</b> Standing up these registries — even empty — does
 * not change parsing. The {@code ActionParser}/{@code ConditionParser} switches
 * are only retired (and {@code KNOWN_TYPES} re-pointed at {@link #actionKeys()}
 * etc.) in the later migration step, verb-by-verb, gated on the golden-master.
 */
public final class CompatRegistries {

    private CompatRegistries() {}

    // ── Registry keys ───────────────────────────────────────────────────────
    public static final ResourceKey<Registry<ActionType>> ACTION_TYPE_KEY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "action_type"));
    public static final ResourceKey<Registry<ConditionType>> CONDITION_TYPE_KEY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "condition_type"));
    public static final ResourceKey<Registry<ItemActionType>> ITEM_ACTION_TYPE_KEY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "item_action_type"));
    public static final ResourceKey<Registry<ItemConditionType>> ITEM_CONDITION_TYPE_KEY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "item_condition_type"));

    // ── DeferredRegisters ─────────────────────────────────────────────────────
    public static final DeferredRegister<ActionType> ACTION_TYPES =
        DeferredRegister.create(ACTION_TYPE_KEY, NeoOrigins.MOD_ID);
    public static final DeferredRegister<ConditionType> CONDITION_TYPES =
        DeferredRegister.create(CONDITION_TYPE_KEY, NeoOrigins.MOD_ID);
    public static final DeferredRegister<ItemActionType> ITEM_ACTION_TYPES =
        DeferredRegister.create(ITEM_ACTION_TYPE_KEY, NeoOrigins.MOD_ID);
    public static final DeferredRegister<ItemConditionType> ITEM_CONDITION_TYPES =
        DeferredRegister.create(ITEM_CONDITION_TYPE_KEY, NeoOrigins.MOD_ID);

    // ── Built-in registration helpers ─────────────────────────────────────────
    // Register a descriptor under its own id's path; the DeferredRegister supplies
    // the neoorigins namespace. (Addon verbs ride their own DeferredRegister on the
    // REGISTRY_KEYs above — public entry point lands in Phase 3.)

    public static DeferredHolder<ActionType, ActionType> reg(ActionType type) {
        return ACTION_TYPES.register(type.id().getPath(), () -> type);
    }

    public static DeferredHolder<ConditionType, ConditionType> reg(ConditionType type) {
        return CONDITION_TYPES.register(type.id().getPath(), () -> type);
    }

    public static DeferredHolder<ItemActionType, ItemActionType> reg(ItemActionType type) {
        return ITEM_ACTION_TYPES.register(type.id().getPath(), () -> type);
    }

    public static DeferredHolder<ItemConditionType, ItemConditionType> reg(ItemConditionType type) {
        return ITEM_CONDITION_TYPES.register(type.id().getPath(), () -> type);
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CompatRegistries::onNewRegistry);
        ACTION_TYPES.register(modEventBus);
        CONDITION_TYPES.register(modEventBus);
        ITEM_ACTION_TYPES.register(modEventBus);
        ITEM_CONDITION_TYPES.register(modEventBus);
    }

    // ── Live registry refs (captured at NewRegistryEvent, like PowerTypes) ──────
    // Looked up through the built registry rather than the DeferredRegister so
    // addon-contributed descriptors are visible to lookups.
    private static Registry<ActionType> ACTION_REGISTRY;
    private static Registry<ConditionType> CONDITION_REGISTRY;
    private static Registry<ItemActionType> ITEM_ACTION_REGISTRY;
    private static Registry<ItemConditionType> ITEM_CONDITION_REGISTRY;

    private static void onNewRegistry(NewRegistryEvent event) {
        ACTION_REGISTRY = event.create(new RegistryBuilder<>(ACTION_TYPE_KEY));
        CONDITION_REGISTRY = event.create(new RegistryBuilder<>(CONDITION_TYPE_KEY));
        ITEM_ACTION_REGISTRY = event.create(new RegistryBuilder<>(ITEM_ACTION_TYPE_KEY));
        ITEM_CONDITION_REGISTRY = event.create(new RegistryBuilder<>(ITEM_CONDITION_TYPE_KEY));
    }

    // ── Lookups ───────────────────────────────────────────────────────────────
    public static ActionType action(Identifier id) {
        return ACTION_REGISTRY == null ? null
            : ACTION_REGISTRY.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }
    public static ConditionType condition(Identifier id) {
        return CONDITION_REGISTRY == null ? null
            : CONDITION_REGISTRY.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }
    public static ItemActionType itemAction(Identifier id) {
        return ITEM_ACTION_REGISTRY == null ? null
            : ITEM_ACTION_REGISTRY.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }
    public static ItemConditionType itemCondition(Identifier id) {
        return ITEM_CONDITION_REGISTRY == null ? null
            : ITEM_CONDITION_REGISTRY.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }

    // ── Key sets (the future source for the parsers' KNOWN_TYPES) ───────────────
    public static Set<Identifier> actionKeys() {
        return ACTION_REGISTRY == null ? Set.of() : ACTION_REGISTRY.keySet();
    }
    public static Set<Identifier> conditionKeys() {
        return CONDITION_REGISTRY == null ? Set.of() : CONDITION_REGISTRY.keySet();
    }
    public static Set<Identifier> itemActionKeys() {
        return ITEM_ACTION_REGISTRY == null ? Set.of() : ITEM_ACTION_REGISTRY.keySet();
    }
    public static Set<Identifier> itemConditionKeys() {
        return ITEM_CONDITION_REGISTRY == null ? Set.of() : ITEM_CONDITION_REGISTRY.keySet();
    }
}
