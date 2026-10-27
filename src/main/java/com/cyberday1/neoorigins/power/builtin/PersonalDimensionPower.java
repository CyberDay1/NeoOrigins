package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.attachment.PersonalSpaceReturn;
import com.cyberday1.neoorigins.data.PersonalSpaceData;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.cyberday1.neoorigins.service.PersonalSpace;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Keybind toggle into and out of the holder's own box in the
 * {@code neoorigins:personal_space} dimension. Entering saves the exact return
 * point and starts the cooldown; leaving by key is free and goes back to it.
 * Box geometry and the exit rules live in {@link PersonalSpace}.
 */
public class PersonalDimensionPower extends AbstractActivePower<PersonalDimensionPower.Config> {

    public record Config(int size, int cooldownTicks, int xpLevelsCost, String type, String cooldownIcon,
        boolean cooldownCountdown, boolean alwaysShowIcon) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.intRange(PersonalSpace.MIN_SIZE, PersonalSpace.MAX_SIZE)
                .optionalFieldOf("size", PersonalSpace.DEFAULT_SIZE).forGetter(Config::size),
            Codec.INT.optionalFieldOf("cooldown_ticks", 1200).forGetter(Config::cooldownTicks),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("xp_levels_cost", 0).forGetter(Config::xpLevelsCost),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("cooldown_countdown", true).forGetter(Config::cooldownCountdown),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    /** Leaving is the free half of the toggle: no cooldown gate, cost or new cooldown. */
    @Override
    protected boolean isFreeActivation(ServerPlayer player, Config config) {
        return PersonalSpace.isPersonalSpace(player.level());
    }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        if (PersonalSpace.isPersonalSpace(player.level())) {
            leave(player);
            return true;
        }
        return enter(player, config);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        // Deferred one tick: an Orb re-pick revokes then re-grants, and a player
        // who keeps a personal_dimension power must not be thrown out.
        PersonalSpace.scheduleRevokeCheck(player);
    }

    private static boolean enter(ServerPlayer player, Config config) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        ServerLevel space = server.getLevel(PersonalSpace.DIMENSION);
        if (space == null) {
            player.sendSystemMessage(Component.translatable("power.neoorigins.personal_dimension.unavailable")
                .withStyle(ChatFormatting.RED), true);
            return false;
        }
        int cost = config.xpLevelsCost();
        boolean free = player.getAbilities().instabuild;
        if (cost > 0 && !free && player.experienceLevel < cost) {
            player.sendSystemMessage(Component.translatable("power.neoorigins.personal_dimension.not_enough_xp", cost)
                .withStyle(ChatFormatting.RED), true);
            return false;
        }

        PersonalSpaceData data = PersonalSpaceData.get(server);
        PersonalSpaceData.Box box = data.getOrAllocate(player.getUUID());
        int size = Math.max(box.builtSize(), config.size());
        PersonalSpace.buildBox(space, box.cell(), box.builtSize(), size);
        data.setBuiltSize(player.getUUID(), size);

        player.setData(EntityAttachments.personalSpaceReturn(), new PersonalSpaceReturn(
            player.level().dimension().identifier().toString(),
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));

        Vec3 dest = PersonalSpace.arrivalPoint(box.cell(), size);
        TeleportEffects.teleportWithEffects(player, space, dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
        if (cost > 0 && !free) player.giveExperienceLevels(-cost);
        return true;
    }

    /**
     * Sends the player back to their saved return point, or to their respawn
     * point when none is saved. The return point is cleared either way. No
     * safety check is made on the return point.
     */
    public static void leave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        PersonalSpaceReturn rp = player.getData(EntityAttachments.personalSpaceReturn());
        player.removeData(EntityAttachments.personalSpaceReturn());
        if (rp.isPresent()) {
            Identifier dimId = Identifier.tryParse(rp.dimension());
            ServerLevel level = dimId == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
            if (level != null) {
                TeleportEffects.teleportWithEffects(player, level, rp.x(), rp.y(), rp.z(), rp.yRot(), rp.xRot());
                return;
            }
        }
        TeleportTransition respawn = player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING);
        if (PersonalSpace.isPersonalSpace(respawn.newLevel())) {
            // A respawn point inside the dimension would leave the player stuck.
            respawn = TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING);
        }
        Vec3 pos = respawn.position();
        TeleportEffects.teleportWithEffects(player, respawn.newLevel(), pos.x, pos.y, pos.z,
            player.getYRot(), player.getXRot());
    }
}
