package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.power.builtin.BareHandToolPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * Wires {@link BareHandToolPower} into the block-break pipeline via two
 * NeoForge events — no mixins needed.
 *
 * <ul>
 *   <li>{@code HarvestCheck} — when the player is bare-handed and has the
 *       {@code bare_hand_tool:<id>} capability, construct the virtual tool
 *       stack and delegate the harvest eligibility check to it. If the
 *       virtual tool can harvest the target, flip {@code canHarvest} on.</li>
 *   <li>{@code BreakSpeed} — scales the running speed by the virtual tool's
 *       {@code getDestroySpeed}, as vanilla does for a held tool (an empty
 *       hand's base is 1.0). Fires client-side as well, so mining animation
 *       predicts correctly.</li>
 * </ul>
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class BareHandToolEvents {

    private BareHandToolEvents() {}

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().isEmpty()) return;
        if (event.canHarvest()) return;

        ItemStack virtual = virtualToolFor(player);
        if (virtual.isEmpty()) return;

        if (virtual.isCorrectToolForDrops(event.getTargetBlock())) {
            event.setCanHarvest(true);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().isEmpty()) return;

        ItemStack virtual = virtualToolFor(player);
        if (virtual.isEmpty()) return;

        float scaled = bareHandSpeed(event.getNewSpeed(), virtual.getDestroySpeed(event.getState()));
        if (scaled != event.getNewSpeed()) {
            event.setNewSpeed(scaled);
        }
    }

    /**
     * The break speed with the virtual tool standing in for the empty hand.
     *
     * <p>Vanilla's empty-hand base speed is 1.0 and every later factor (Haste,
     * Mining Fatigue, {@code block_break_speed}, the submerged and off-ground
     * penalties) multiplies it, so multiplying by the tool's speed is exactly what
     * holding that tool would give. It used to REPLACE the running value instead,
     * which threw away whatever another listener had already multiplied in: a
     * Miner's or Mason's break-speed bonus vanished bare-handed whenever this
     * listener happened to run after {@code BreakSpeedModifierEvents}. A pure
     * multiply is order-independent.
     */
    static float bareHandSpeed(float current, float virtualSpeed) {
        return virtualSpeed > 1.0f ? current * virtualSpeed : current;
    }

    /**
     * Resolves the configured bare-hand tool for the given player into a
     * virtual {@link ItemStack}, or empty if the player has no
     * {@code BareHandToolPower} active.
     *
     * <p>Server uses {@link ActiveOriginService#allPowers(ServerPlayer)} to
     * read the config directly. Client parses the synced capability tag —
     * the tool ID is encoded into the tag after the
     * {@code bare_hand_tool:} prefix so no extra sync state is needed.
     */
    static ItemStack virtualToolFor(Player player) {
        Optional<ResourceLocation> toolId = Optional.empty();

        if (player instanceof ServerPlayer sp) {
            for (PowerHolder<?> holder : ActiveOriginService.allPowers(sp)) {
                if (holder.type() instanceof BareHandToolPower) {
                    BareHandToolPower.Config config = (BareHandToolPower.Config) holder.config();
                    toolId = Optional.of(config.tool());
                    break;
                }
            }
        } else if (player.level().isClientSide()) {
            for (String cap : ClientActivePowers.activeCapabilities()) {
                if (cap.startsWith(BareHandToolPower.CAPABILITY_PREFIX)) {
                    try {
                        toolId = Optional.of(ResourceLocation.parse(
                            cap.substring(BareHandToolPower.CAPABILITY_PREFIX.length())));
                        break;
                    } catch (Exception ignored) { /* bad tag, skip */ }
                }
            }
        }

        if (toolId.isEmpty()) return ItemStack.EMPTY;

        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(toolId.get());
        return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
}
