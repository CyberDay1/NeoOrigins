package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class OrbOfOriginItem extends Item {

    public OrbOfOriginItem(Item.Properties properties) {
        super(properties);
    }

    public static final int LEVELS_PER_USE = 5;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Server-authoritative: client prediction can't see XP or origin data reliably.
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.consume(stack);
        }

        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        int cost = data.getOrbUseCount() * LEVELS_PER_USE;

        if (!sp.isCreative() && cost > 0 && sp.experienceLevel < cost) {
            sp.sendSystemMessage(Component.translatable("item.neoorigins.orb_of_origin.not_enough_xp", cost)
                .withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }

        // Defer all destructive work (revoke, shrink, XP deduct, orbUseCount bump)
        // until the player actually commits a new origin. This lets players back
        // out of the picker without losing the orb or their existing origins.
        data.setPendingOrbCommit(true);
        NeoOriginsNetwork.openSelectionScreen(sp, true, true);

        return InteractionResultHolder.consume(stack);
    }
}
