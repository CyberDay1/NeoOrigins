package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.util.PowerUpkeep;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives the player an extra inventory (like the shulker_inventory from Origins).
 * The inventory is opened via the skill keybind (active power) and uses vanilla's
 * chest UI. Contents are persisted via player data NBT.
 *
 * <p>An optional cost ({@code hunger_cost}, {@code resource_cost} +
 * {@code resource_cost_amount}) is charged every {@code upkeep_interval} ticks
 * while this power's menu is open, through {@link PowerUpkeep}. The key does
 * nothing when the player can't pay one charge, and the menu closes at the
 * first charge they can't pay. Items stay in the stored container either way.
 *
 * <pre>{@code
 * { "type": "neoorigins:extra_inventory", "size": 9 }
 * }</pre>
 */
public class ExtraInventoryPower extends PowerType<ExtraInventoryPower.Config> {

    private static final String NBT_KEY = "neoorigins:extra_inventory";
    private static final Map<UUID, SimpleContainer> CONTAINERS = new ConcurrentHashMap<>();

    public record Config(int size, boolean dropOnDeath, String type, String title,
                         int hungerCost, String resourceCost, int resourceCostAmount,
                         int upkeepInterval) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("size", 9).forGetter(Config::size),
            Codec.BOOL.optionalFieldOf("drop_on_death", false).forGetter(Config::dropOnDeath),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("title", "").forGetter(Config::title),
            Codec.INT.optionalFieldOf("hunger_cost", 0).forGetter(Config::hungerCost),
            Codec.STRING.optionalFieldOf("resource_cost", "").forGetter(Config::resourceCost),
            Codec.INT.optionalFieldOf("resource_cost_amount", 0).forGetter(Config::resourceCostAmount),
            Codec.INT.optionalFieldOf("upkeep_interval", PowerUpkeep.Upkeep.DEFAULT_INTERVAL)
                .forGetter(Config::upkeepInterval)
        ).apply(inst, Config::new));

        /** No cost. */
        public Config(int size, boolean dropOnDeath, String type, String title) {
            this(size, dropOnDeath, type, title, 0, "", 0, PowerUpkeep.Upkeep.DEFAULT_INTERVAL);
        }

        /** The cost charged each interval while the menu is open. */
        public PowerUpkeep.Upkeep upkeep() {
            return new PowerUpkeep.Upkeep(hungerCost, resourceCost, resourceCostAmount, upkeepInterval, null);
        }
    }

    /**
     * The menu this power opens: a plain chest menu that remembers which power
     * opened it, so only that power charges for it.
     */
    static final class ExtraInventoryMenu extends ChestMenu {
        final Identifier powerId;

        ExtraInventoryMenu(MenuType<?> type, int windowId, Inventory playerInv, Container container,
                           int rows, Identifier powerId) {
            super(type, windowId, playerInv, container, rows);
            this.powerId = powerId;
        }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public boolean isActivePower(Config config) { return true; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        loadContainer(player, config);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        saveContainer(player, config);
        CONTAINERS.remove(player.getUUID());
    }

    /**
     * Charges the cost while this power's menu is the player's open container,
     * and closes it at the first charge the player can't pay. Closing goes
     * through the normal close path, so the stored items stay put and a stack
     * on the cursor goes back to the player's inventory.
     */
    @Override
    public void onTick(ServerPlayer player, Config config) {
        PowerUpkeep.Upkeep upkeep = config.upkeep();
        if (upkeep.isFree() || !player.isAlive()) return;
        if (!(player.containerMenu instanceof ExtraInventoryMenu menu)) return;
        if (!java.util.Objects.equals(menu.powerId, PowerHolder.currentDispatchId())) return;
        if (PowerUpkeep.chargeIfDue(player, upkeep) == PowerUpkeep.Outcome.SHORT) {
            player.closeContainer();
        }
    }

    @Override
    public void onActivated(ServerPlayer player, Config config) {
        // Opening is free, but only for a player who could pay the next charge.
        PowerUpkeep.Upkeep upkeep = config.upkeep();
        if (!upkeep.isFree() && !PowerUpkeep.Charge.of(upkeep.hungerCost(), upkeep.resourceCost(),
                upkeep.resourceCostAmount()).affordable(player)) {
            return;
        }
        Identifier powerId = PowerHolder.currentDispatchId();
        SimpleContainer container = getOrCreateContainer(player, config);
        // Clamp to valid chest row counts (1-6 rows of 9)
        int rows = Math.max(1, Math.min(6, (config.size() + 8) / 9));
        Component title = (config.title() != null && !config.title().isBlank())
            ? Component.literal(config.title())
            : Component.translatable("container.neoorigins.extra_inventory");
        player.openMenu(new SimpleMenuProvider(
            (windowId, playerInv, p) -> new ExtraInventoryMenu(menuTypeForRows(rows), windowId, playerInv, container, rows, powerId),
            title
        ));
    }

    private static MenuType<ChestMenu> menuTypeForRows(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> MenuType.GENERIC_9x3;
        };
    }

    /** Save inventory to player NBT on logout / world save. */
    public static void onPlayerSave(ServerPlayer player) {
        SimpleContainer container = CONTAINERS.get(player.getUUID());
        if (container == null) return;

        CompoundTag playerData = player.getPersistentData();
        CompoundTag modTag = playerData.contains(NeoOrigins.MOD_ID)
            ? playerData.getCompound(NeoOrigins.MOD_ID).orElseGet(CompoundTag::new)
            : new CompoundTag();

        var nbtOps = player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        ListTag items = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                final int slotIndex = i;
                ItemStack.CODEC.encodeStart(nbtOps, stack).result().ifPresent(tag -> {
                    if (tag instanceof CompoundTag slot) {
                        slot.putByte("Slot", (byte) slotIndex);
                        items.add(slot);
                    }
                });
            }
        }
        modTag.put(NBT_KEY, items);
        playerData.put(NeoOrigins.MOD_ID, modTag);
    }

    /** Load inventory from player NBT on login / power grant. */
    private static void loadContainer(ServerPlayer player, Config config) {
        SimpleContainer container = getOrCreateContainer(player, config);
        CompoundTag playerData = player.getPersistentData();

        if (!playerData.contains(NeoOrigins.MOD_ID)) return;
        var nbtOps = player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        playerData.getCompound(NeoOrigins.MOD_ID).ifPresent(modTag -> {
            modTag.getList(NBT_KEY).ifPresent(items -> {
                for (int i = 0; i < items.size(); i++) {
                    items.getCompound(i).ifPresent(slot -> {
                        int slotIdx = slot.getByteOr("Slot", (byte) 0) & 0xFF;
                        if (slotIdx < container.getContainerSize()) {
                            ItemStack.CODEC.parse(nbtOps, slot).result().ifPresent(
                                stack -> container.setItem(slotIdx, stack));
                        }
                    });
                }
            });
        });
    }

    private static void saveContainer(ServerPlayer player, Config config) {
        onPlayerSave(player);
    }

    private static SimpleContainer getOrCreateContainer(ServerPlayer player, Config config) {
        return CONTAINERS.computeIfAbsent(player.getUUID(), uuid -> {
            // Clamp to multiples of 9, max 54 (6 rows)
            int slots = Math.min(54, Math.max(9, ((config.size() + 8) / 9) * 9));
            return new SimpleContainer(slots);
        });
    }

    /** Drop contents on death if configured. Called from death event handler. */
    public static void onPlayerDeath(ServerPlayer player, boolean dropOnDeath) {
        SimpleContainer container = CONTAINERS.get(player.getUUID());
        if (container == null) return;

        if (dropOnDeath) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    com.cyberday1.neoorigins.util.ItemDrops.spawn(player, stack, true, false);
                }
            }
            container.clearContent();
        }
    }

    /** Clean up on logout. */
    public static void onPlayerLogout(ServerPlayer player) {
        onPlayerSave(player);
        CONTAINERS.remove(player.getUUID());
    }
}
