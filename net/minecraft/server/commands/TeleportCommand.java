package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class TeleportCommand {
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(
        Component.translatable("commands.teleport.invalidPosition")
    );

    public static void register(CommandDispatcher<CommandSourceStack> p_139009_) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = p_139009_.register(
            Commands.literal("teleport")
                .requires(p_139039_ -> p_139039_.hasPermission(2))
                .then(
                    Commands.argument("location", Vec3Argument.vec3())
                        .executes(
                            p_139051_ -> teleportToPos(
                                    p_139051_.getSource(),
                                    Collections.singleton(p_139051_.getSource().getEntityOrException()),
                                    p_139051_.getSource().getLevel(),
                                    Vec3Argument.getCoordinates(p_139051_, "location"),
                                    WorldCoordinates.current(),
                                    null
                                )
                        )
                )
                .then(
                    Commands.argument("destination", EntityArgument.entity())
                        .executes(
                            p_139049_ -> teleportToEntity(
                                    p_139049_.getSource(),
                                    Collections.singleton(p_139049_.getSource().getEntityOrException()),
                                    EntityArgument.getEntity(p_139049_, "destination")
                                )
                        )
                )
                .then(
                    Commands.argument("targets", EntityArgument.entities())
                        .then(
                            Commands.argument("location", Vec3Argument.vec3())
                                .executes(
                                    p_139047_ -> teleportToPos(
                                            p_139047_.getSource(),
                                            EntityArgument.getEntities(p_139047_, "targets"),
                                            p_139047_.getSource().getLevel(),
                                            Vec3Argument.getCoordinates(p_139047_, "location"),
                                            null,
                                            null
                                        )
                                )
                                .then(
                                    Commands.argument("rotation", RotationArgument.rotation())
                                        .executes(
                                            p_139045_ -> teleportToPos(
                                                    p_139045_.getSource(),
                                                    EntityArgument.getEntities(p_139045_, "targets"),
                                                    p_139045_.getSource().getLevel(),
                                                    Vec3Argument.getCoordinates(p_139045_, "location"),
                                                    RotationArgument.getRotation(p_139045_, "rotation"),
                                                    null
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("facing")
                                        .then(
                                            Commands.literal("entity")
                                                .then(
                                                    Commands.argument("facingEntity", EntityArgument.entity())
                                                        .executes(
                                                            p_326749_ -> teleportToPos(
                                                                    p_326749_.getSource(),
                                                                    EntityArgument.getEntities(p_326749_, "targets"),
                                                                    p_326749_.getSource().getLevel(),
                                                                    Vec3Argument.getCoordinates(p_326749_, "location"),
                                                                    null,
                                                                    new TeleportCommand.LookAtEntity(
                                                                        EntityArgument.getEntity(p_326749_, "facingEntity"), EntityAnchorArgument.Anchor.FEET
                                                                    )
                                                                )
                                                        )
                                                        .then(
                                                            Commands.argument("facingAnchor", EntityAnchorArgument.anchor())
                                                                .executes(
                                                                    p_326750_ -> teleportToPos(
                                                                            p_326750_.getSource(),
                                                                            EntityArgument.getEntities(p_326750_, "targets"),
                                                                            p_326750_.getSource().getLevel(),
                                                                            Vec3Argument.getCoordinates(p_326750_, "location"),
                                                                            null,
                                                                            new TeleportCommand.LookAtEntity(
                                                                                EntityArgument.getEntity(p_326750_, "facingEntity"),
                                                                                EntityAnchorArgument.getAnchor(p_326750_, "facingAnchor")
                                                                            )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.argument("facingLocation", Vec3Argument.vec3())
                                                .executes(
                                                    p_326751_ -> teleportToPos(
                                                            p_326751_.getSource(),
                                                            EntityArgument.getEntities(p_326751_, "targets"),
                                                            p_326751_.getSource().getLevel(),
                                                            Vec3Argument.getCoordinates(p_326751_, "location"),
                                                            null,
                                                            new TeleportCommand.LookAtPosition(Vec3Argument.getVec3(p_326751_, "facingLocation"))
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.argument("destination", EntityArgument.entity())
                                .executes(
                                    p_139011_ -> teleportToEntity(
                                            p_139011_.getSource(),
                                            EntityArgument.getEntities(p_139011_, "targets"),
                                            EntityArgument.getEntity(p_139011_, "destination")
                                        )
                                )
                        )
                )
        );
        p_139009_.register(Commands.literal("tp").requires(p_139013_ -> p_139013_.hasPermission(2)).redirect(literalcommandnode));
    }

    private static int teleportToEntity(CommandSourceStack p_139033_, Collection<? extends Entity> p_139034_, Entity p_139035_) throws CommandSyntaxException {
        for (Entity entity : p_139034_) {
            performTeleport(
                p_139033_,
                entity,
                (ServerLevel)p_139035_.level(),
                p_139035_.getX(),
                p_139035_.getY(),
                p_139035_.getZ(),
                EnumSet.noneOf(RelativeMovement.class),
                p_139035_.getYRot(),
                p_139035_.getXRot(),
                null
            );
        }

        if (p_139034_.size() == 1) {
            p_139033_.sendSuccess(
                () -> Component.translatable(
                        "commands.teleport.success.entity.single", p_139034_.iterator().next().getDisplayName(), p_139035_.getDisplayName()
                    ),
                true
            );
        } else {
            p_139033_.sendSuccess(() -> Component.translatable("commands.teleport.success.entity.multiple", p_139034_.size(), p_139035_.getDisplayName()), true);
        }

        return p_139034_.size();
    }

    private static int teleportToPos(
        CommandSourceStack p_139026_,
        Collection<? extends Entity> p_139027_,
        ServerLevel p_139028_,
        Coordinates p_139029_,
        @Nullable Coordinates p_139030_,
        @Nullable TeleportCommand.LookAt p_139031_
    ) throws CommandSyntaxException {
        Vec3 vec3 = p_139029_.getPosition(p_139026_);
        Vec2 vec2 = p_139030_ == null ? null : p_139030_.getRotation(p_139026_);
        Set<RelativeMovement> set = EnumSet.noneOf(RelativeMovement.class);
        if (p_139029_.isXRelative()) {
            set.add(RelativeMovement.X);
        }

        if (p_139029_.isYRelative()) {
            set.add(RelativeMovement.Y);
        }

        if (p_139029_.isZRelative()) {
            set.add(RelativeMovement.Z);
        }

        if (p_139030_ == null) {
            set.add(RelativeMovement.X_ROT);
            set.add(RelativeMovement.Y_ROT);
        } else {
            if (p_139030_.isXRelative()) {
                set.add(RelativeMovement.X_ROT);
            }

            if (p_139030_.isYRelative()) {
                set.add(RelativeMovement.Y_ROT);
            }
        }

        for (Entity entity : p_139027_) {
            if (p_139030_ == null) {
                performTeleport(p_139026_, entity, p_139028_, vec3.x, vec3.y, vec3.z, set, entity.getYRot(), entity.getXRot(), p_139031_);
            } else {
                performTeleport(p_139026_, entity, p_139028_, vec3.x, vec3.y, vec3.z, set, vec2.y, vec2.x, p_139031_);
            }
        }

        if (p_139027_.size() == 1) {
            p_139026_.sendSuccess(
                () -> Component.translatable(
                        "commands.teleport.success.location.single",
                        p_139027_.iterator().next().getDisplayName(),
                        formatDouble(vec3.x),
                        formatDouble(vec3.y),
                        formatDouble(vec3.z)
                    ),
                true
            );
        } else {
            p_139026_.sendSuccess(
                () -> Component.translatable(
                        "commands.teleport.success.location.multiple", p_139027_.size(), formatDouble(vec3.x), formatDouble(vec3.y), formatDouble(vec3.z)
                    ),
                true
            );
        }

        return p_139027_.size();
    }

    private static String formatDouble(double p_142776_) {
        return String.format(Locale.ROOT, "%f", p_142776_);
    }

    private static void performTeleport(
        CommandSourceStack p_139015_,
        Entity p_139016_,
        ServerLevel p_139017_,
        double p_139018_,
        double p_139019_,
        double p_139020_,
        Set<RelativeMovement> p_139021_,
        float p_139022_,
        float p_139023_,
        @Nullable TeleportCommand.LookAt p_139024_
    ) throws CommandSyntaxException {
        net.neoforged.neoforge.event.entity.EntityTeleportEvent.TeleportCommand event = net.neoforged.neoforge.event.EventHooks.onEntityTeleportCommand(p_139016_, p_139018_, p_139019_, p_139020_);
        if (event.isCanceled()) {
             return;
        }
        p_139018_ = event.getTargetX();
        p_139019_ = event.getTargetY();
        p_139020_ = event.getTargetZ();

        BlockPos blockpos = BlockPos.containing(p_139018_, p_139019_, p_139020_);
        if (!Level.isInSpawnableBounds(blockpos)) {
            throw INVALID_POSITION.create();
        } else {
            float f = Mth.wrapDegrees(p_139022_);
            float f1 = Mth.wrapDegrees(p_139023_);
            if (p_139016_.teleportTo(p_139017_, p_139018_, p_139019_, p_139020_, p_139021_, f, f1)) {
                if (p_139024_ != null) {
                    p_139024_.perform(p_139015_, p_139016_);
                }

                if (!(p_139016_ instanceof LivingEntity livingentity) || !livingentity.isFallFlying()) {
                    p_139016_.setDeltaMovement(p_139016_.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                    p_139016_.setOnGround(true);
                }

                if (p_139016_ instanceof PathfinderMob pathfindermob) {
                    pathfindermob.getNavigation().stop();
                }
            }
        }
    }

    @FunctionalInterface
    interface LookAt {
        void perform(CommandSourceStack p_139061_, Entity p_139062_);
    }

    static record LookAtEntity(Entity entity, EntityAnchorArgument.Anchor anchor) implements TeleportCommand.LookAt {
        @Override
        public void perform(CommandSourceStack p_326864_, Entity p_326807_) {
            if (p_326807_ instanceof ServerPlayer serverplayer) {
                serverplayer.lookAt(p_326864_.getAnchor(), this.entity, this.anchor);
            } else {
                p_326807_.lookAt(p_326864_.getAnchor(), this.anchor.apply(this.entity));
            }
        }
    }

    static record LookAtPosition(Vec3 position) implements TeleportCommand.LookAt {
        @Override
        public void perform(CommandSourceStack p_326870_, Entity p_326894_) {
            p_326894_.lookAt(p_326870_.getAnchor(), this.position);
        }
    }
}
