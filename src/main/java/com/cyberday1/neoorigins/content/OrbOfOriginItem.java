package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

public class OrbOfOriginItem extends Item {

    public OrbOfOriginItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            // Revoke all current powers
            ActiveOriginService.revokeAllPowers(sp);

            // Reset all layer origins
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            for (var layer : LayerDataManager.INSTANCE.getLayers().values()) {
                data.removeOrigin(layer.id());
            }
            data.setHadAllOrigins(false);

            // Sync and open selection screen
            NeoOriginsNetwork.syncToPlayer(sp);
            NeoOriginsNetwork.openSelectionScreen(sp, true, true);
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
