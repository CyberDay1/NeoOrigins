package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Cheaper sibling of the Orb of Origin: resets ONLY the {@code neoorigins:class}
 * layer and reopens the picker scoped to just that layer (the main origin layer
 * stays chosen, so the picker's unfilled-layer filter skips it). Destructive
 * work (XP deduct + orb consume) is deferred until the player commits a new
 * class, so closing the picker refunds the orb and restores the prior class.
 */
public class OrbOfClassItem extends Item {

    /** The single layer this orb resets. */
    public static final Identifier CLASS_LAYER =
        Identifier.fromNamespaceAndPath("neoorigins", "class");

    public OrbOfClassItem(Item.Properties properties) {
        super(properties);
    }

    /** Flat XP-level cost for a class-orb use, from config. */
    public static int computeCost() {
        return GameplayConfig.orbOfClassLevelsPerUse();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Server-authoritative: client prediction can't see XP or origin data.
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.CONSUME;
        }

        PlayerOriginData data = sp.getData(OriginAttachments.originData());

        // Nothing to reset if the player has no class picked yet (e.g. still in
        // the initial walkthrough). Let the normal flow handle it.
        if (!data.hasOriginForLayer(CLASS_LAYER)) {
            return InteractionResult.FAIL;
        }

        int cost = computeCost();
        if (!sp.isCreative() && cost > 0 && sp.experienceLevel < cost) {
            sp.sendSystemMessage(Component.translatable("item.neoorigins.orb_of_class.not_enough_xp", cost)
                .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        // Clear ONLY the class layer up front so the picker shows just that layer.
        // XP + orb consumption are deferred to the first pick, and a cancelled pick
        // rolls the class back. Routed through the reusable scoped layer-picker session.
        NeoOriginsNetwork.beginLayerPicker(sp, java.util.List.of(CLASS_LAYER), true, cost, stack.getItem(), null);

        return InteractionResult.CONSUME;
    }
}
