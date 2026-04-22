package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

public class OrbOfOriginItem extends Item {

    public OrbOfOriginItem(Item.Properties properties) {
        super(properties);
    }

    private static final int LEVELS_PER_USE = 5;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Client prediction can't see XP state reliably across the XP check,
        // so defer the whole decision — including the stack shrink — to the
        // server. The server's inventory sync is authoritative.
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

        if (!sp.isCreative() && cost > 0) {
            sp.giveExperienceLevels(-cost);
        }
        data.incrementOrbUseCount();

        ActiveOriginService.revokeAllPowers(sp);
        for (var layer : LayerDataManager.INSTANCE.getLayers().values()) {
            data.removeOrigin(layer.id());
        }
        data.setHadAllOrigins(false);
        // Clear equipment-grant ledger so re-picked origins re-grant their items.
        // Player already paid XP (LEVELS_PER_USE × orbUseCount) for this reset.
        data.clearGrantedEquipment();

        NeoOriginsNetwork.syncRegistryToPlayer(sp);
        NeoOriginsNetwork.syncToPlayer(sp);
        NeoOriginsNetwork.openSelectionScreen(sp, true, true);

        if (!sp.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }
}
