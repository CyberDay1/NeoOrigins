package com.cyberday1.neoorigins.compat.ftbquests;

import com.cyberday1.neoorigins.content.ModItems;
import com.cyberday1.neoorigins.power.builtin.LootPoolGrantPower;
import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * First-class FTB Quests reward that grants a NeoOrigins loot pool on quest
 * claim. Mirrors the {@code neoorigins_loot_pool_grant:<table_id>} quest-tag
 * marker path: both routes funnel into
 * {@link LootPoolGrantPower#fireLootPoolGrant(ServerPlayer, Identifier, String, int)}
 * so the roll-and-grant pipeline (table lookup, GIFT loot context, inventory
 * overflow handling, per-grant dedup) is shared — this class only adds the
 * in-editor reward GUI on top.
 *
 * <p>This type references FTBQ + FTB Library symbols directly (compile-only
 * soft deps), so it must never be classloaded unless {@code ftbquests} is
 * present. {@link FtbQuestsRewardRegistration} is the only caller and is itself
 * behind the mod-list gate. FTBQ 26.1 stores quest data as JSON5, so the two
 * fields round-trip through {@link Json5Object} rather than NBT.
 */
public class NeoOriginsLootPoolReward extends Reward {

    /** Reward type id: {@code neoorigins:loot_pool}. */
    public static final Identifier TYPE_ID =
        Identifier.fromNamespaceAndPath("neoorigins", "loot_pool");

    /** Registered RewardType handle, set by {@link FtbQuestsRewardRegistration#register()}. */
    public static RewardType TYPE;

    /** Loot table id to roll, e.g. {@code neoorigins:gameplay/origin_starter}. */
    private String lootTable = "";
    /** Number of times to roll the table on claim. */
    private int rolls = 1;

    public NeoOriginsLootPoolReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return TYPE;
    }

    @Override
    public void writeData(Json5Object json, HolderLookup.Provider provider) {
        super.writeData(json, provider);
        json.addProperty("loot_table", lootTable);
        if (rolls != 1) {
            json.addProperty("rolls", rolls);
        }
    }

    @Override
    public void readData(Json5Object json, HolderLookup.Provider provider) {
        super.readData(json, provider);
        lootTable = Json5Util.getString(json, "loot_table").orElse("");
        rolls = Math.max(1, Json5Util.getInt(json, "rolls").orElse(1));
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(lootTable);
        buf.writeVarInt(rolls);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buf) {
        super.readNetData(buf);
        lootTable = buf.readUtf();
        rolls = Math.max(1, buf.readVarInt());
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("loot_table", lootTable, v -> lootTable = v, "")
            .setNameKey("reward.neoorigins.loot_pool.loot_table");
        config.addInt("rolls", rolls, v -> rolls = Math.max(1, v), 1, 1, 256)
            .setNameKey("reward.neoorigins.loot_pool.rolls");
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        if (lootTable == null || lootTable.isBlank()) {
            return;
        }
        Identifier tableId = Identifier.tryParse(lootTable.trim());
        if (tableId == null) {
            return;
        }
        // Dedup id scoped to this reward instance so a player can't double-claim
        // the same configured pool, while two distinct quests granting the same
        // table still each fire once.
        String grantId = "ftbq_reward:" + id + ":" + lootTable.trim();
        LootPoolGrantPower.fireLootPoolGrant(player, tableId, grantId, Math.max(1, rolls));
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("reward.neoorigins.loot_pool");
    }

    @Override
    public Icon<?> getAltIcon() {
        return defaultIcon();
    }

    /** Icon supplier handed to {@link RewardTypes#register} at registration. */
    static Icon<?> defaultIcon() {
        return ItemIcon.ofItem(ModItems.ORB_OF_ORIGIN.get());
    }
}
