package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.Objects;

/**
 * Active power-type (v2.1.6 backlog #3) that rolls a vanilla loot table on
 * activation and grants the rolled stacks to the player. Pack authors get the
 * full vanilla loot infrastructure — weighted entries, conditions, functions,
 * modifiers — for free, instead of being limited to a fixed item list.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:loot_pool_grant",
 *   "name": "...",
 *   "description": "...",
 *   "loot_table": "neoorigins:rewards/my_pool",
 *   "rolls": 1,
 *   "bonus_rolls": 0,
 *   "active": "key.use_skill_1",
 *   "cooldown": 0,
 *   "grant_id": "my_grant_id"
 * }
 * }</pre>
 *
 * <p>{@code rolls} / {@code bonus_rolls} are forwarded as guidance and exposed to
 * authors so they can keep the power-level config close to the loot table they
 * point at; the authoritative roll counts still live in the loot table itself.
 * The power applies the configured roll count by invoking the table N+M times
 * (rolls + bonus_rolls), defaulting to a single invocation when both are unset.
 *
 * <p>Dedup: like {@link StartingEquipmentPower}, the {@code grant_id} is stored
 * in {@link PlayerOriginData}'s shared grant-tracking attachment. Once granted,
 * subsequent activations are silent no-ops. The Orb of Origin and
 * {@code /origin reset} clear the set so re-rolls are possible.
 *
 * <p>FTBQ soft-compat: when {@code ftbquests} is loaded, the
 * {@link com.cyberday1.neoorigins.compat.FtbQuestsCompat} listener catches
 * quest-completed events and routes them through
 * {@link #fireLootPoolGrant(ServerPlayer, Identifier, String)} so the
 * same roll-and-grant pipeline is used for both code paths.
 */
public class LootPoolGrantPower extends AbstractActivePower<LootPoolGrantPower.Config> {

    public record Config(
        String grantId,
        String lootTable,
        int rolls,
        int bonusRolls,
        String active,
        int cooldownTicks
    ) implements AbstractActivePower.Config {
        @Override public int cooldownTicks() { return cooldownTicks; }

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("grant_id").forGetter(Config::grantId),
            Codec.STRING.fieldOf("loot_table").forGetter(Config::lootTable),
            // `rolls` defaults to 1 and clamps negative -> 0 at runtime with WARN
            // (matches the harness lint that surfaces non-positive rolls).
            Codec.INT.optionalFieldOf("rolls", 1).forGetter(Config::rolls),
            Codec.INT.optionalFieldOf("bonus_rolls", 0).forGetter(Config::bonusRolls),
            // Display-only string mirroring the apoli `key` convention so the
            // power JSON visually advertises which slot it expects. Actual
            // dispatch still flows through AbstractActivePower / skill-slot.
            Codec.STRING.optionalFieldOf("active", "").forGetter(Config::active),
            // Field is named `cooldown` in JSON to match the requested shape;
            // mapped onto AbstractActivePower's cooldownTicks contract.
            Codec.INT.optionalFieldOf("cooldown", 0).forGetter(Config::cooldownTicks)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        if (config.lootTable() == null || config.lootTable().isBlank()) {
            NeoOrigins.LOGGER.warn(
                "[loot_pool_grant] grantId '{}' has empty 'loot_table' — nothing to grant",
                config.grantId());
            return false;
        }
        Identifier tableId;
        try {
            tableId = Identifier.parse(config.lootTable());
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn(
                "[loot_pool_grant] grantId '{}' has invalid loot_table id '{}': {}",
                config.grantId(), config.lootTable(), e.getMessage());
            return false;
        }
        int rolls = Math.max(0, config.rolls()) + Math.max(0, config.bonusRolls());
        if (rolls <= 0) {
            NeoOrigins.LOGGER.warn(
                "[loot_pool_grant] grantId '{}' has non-positive rolls — nothing to grant",
                config.grantId());
            return false;
        }
        return fireLootPoolGrant(player, tableId, config.grantId(), rolls);
    }

    /**
     * Roll the named loot table {@code totalRolls} times against {@code player}
     * and deposit each stack into the player's inventory (overflow drops at the
     * player's feet). Dedup'd by {@code grantId} via the shared grant-tracking
     * attachment so a re-fire of the same id is a silent no-op.
     *
     * <p>Public entry point for the FTBQ soft-compat layer: a quest-completed
     * listener can call this directly with the table id pulled from the quest's
     * opt-in tag.
     *
     * @return true if at least one stack was actually granted (so a cooldown
     *         should start); false on dedup hit, missing table, or empty roll.
     */
    public static boolean fireLootPoolGrant(ServerPlayer player, Identifier tableId, String grantId) {
        return fireLootPoolGrant(player, tableId, grantId, 1);
    }

    public static boolean fireLootPoolGrant(ServerPlayer player, Identifier tableId,
                                             String grantId, int totalRolls) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(grantId, "grantId");

        PlayerOriginData data = player.getData(OriginAttachments.originData());
        if (data.hasGrantedEquipment(grantId)) return false;

        MinecraftServer server = player.level().getServer();
        if (server == null) return false;

        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, tableId);
        LootTable table = server.reloadableRegistries().getLootTable(key);
        // Vanilla returns an empty table sentinel on miss; detect by identity
        // against the EMPTY constant so a typo'd id surfaces as a WARN instead
        // of silently no-op'ing the grant.
        if (table == LootTable.EMPTY) {
            NeoOrigins.LOGGER.warn(
                "[loot_pool_grant] loot_table '{}' not found for grantId '{}' — skipped",
                tableId, grantId);
            return false;
        }

        ServerLevel level = player.level();
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, player.position())
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .create(LootContextParamSets.GIFT);

        int rolls = Math.max(1, totalRolls);
        int grantedStacks = 0;
        for (int r = 0; r < rolls; r++) {
            ObjectArrayList<ItemStack> stacks = table.getRandomItems(params);
            for (ItemStack stack : stacks) {
                if (stack.isEmpty()) continue;
                if (!player.getInventory().add(stack.copy())) {
                    // Overflow: drop at the player's feet so nothing is silently lost.
                    ItemEntity drop = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack);
                    drop.setDefaultPickUpDelay();
                    level.addFreshEntity(drop);
                }
                grantedStacks++;
            }
        }

        if (grantedStacks > 0) {
            data.markEquipmentGranted(grantId);
            NeoOrigins.LOGGER.debug(
                "[loot_pool_grant] granted {} stacks from '{}' to {} (grantId '{}')",
                grantedStacks, tableId, player.getName().getString(), grantId);
            return true;
        }
        // Empty roll — don't mark granted so a re-fire after the pack author
        // adds entries to the table still works. Same forgiveness pattern as
        // StartingEquipmentPower.grantIfUngranted's all-fail branch.
        return false;
    }
}
