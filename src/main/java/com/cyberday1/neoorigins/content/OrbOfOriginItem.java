package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class OrbOfOriginItem extends Item {

    /**
     * The single layer this orb re-picks. The Orb of Origin resets ONLY the main
     * origin layer, leaving any Class (or other) layer chosen — changing class is
     * the Orb of Class's job (issue #113). Sub-layers whose conditions no longer
     * pass after the new origin are cleared automatically by the cascade in
     * {@link com.cyberday1.neoorigins.network.NeoOriginsNetwork#handleChooseOrigin}.
     */
    public static final ResourceLocation ORIGIN_LAYER =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "origin");

    public OrbOfOriginItem(Item.Properties properties) {
        super(properties);
    }

    /** @deprecated Use {@link com.cyberday1.neoorigins.config.GameplayConfig#orbLevelsPerUse()} instead. Kept for binary compat. */
    @Deprecated
    public static final int LEVELS_PER_USE = 5;

    /** Compute the XP level cost for an orb use based on config and prior use count. */
    public static int computeCost(int orbUseCount) {
        // Flat mode: every use (including the first) costs a fixed levels_per_use.
        if (!GameplayConfig.orbScaleCost()) {
            return GameplayConfig.orbLevelsPerUse();
        }
        // Scaling mode: first use free, then ramps with prior use count.
        return orbUseCount * GameplayConfig.orbLevelsPerUse();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Server-authoritative: client prediction can't see XP or origin data reliably.
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.consume(stack);
        }

        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        int cost = computeCost(data.getOrbUseCount());

        if (!sp.isCreative() && cost > 0 && sp.experienceLevel < cost) {
            sp.sendSystemMessage(Component.translatable("item.neoorigins.orb_of_origin.not_enough_xp", cost)
                .withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }

        // Defer all destructive work (revoke, shrink, XP deduct, orbUseCount bump)
        // until the player actually commits a new origin. This lets players back
        // out of the picker without losing the orb or their existing origins.
        data.setPendingOrbCommit(true);
        // Scope the picker to just the origin layer so the Orb of Origin re-picks
        // origin only. forceReselect re-shows the (still-filled) layer; the actual
        // clear/revoke is deferred to commitOrbUse on the first pick.
        NeoOriginsNetwork.openSelectionScreen(sp, true, true, java.util.List.of(ORIGIN_LAYER));

        return InteractionResultHolder.consume(stack);
    }
}
