package com.cyberday1.neoorigins.compat.jade;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.Optional;

/**
 * Jade tooltip integration — soft dependency, dormant if Jade is absent.
 *
 * <p>Shows the looked-at living entity's NeoOrigins mob origin in the Jade
 * tooltip overlay. Only classloaded when Jade is installed (Jade discovers it
 * via the {@link WailaPlugin} annotation).
 *
 * <p>26.1 API drift vs the 1.21.1 source: {@code getUid()} returns
 * {@link Identifier} (was {@code ResourceLocation}), and the mob-origin
 * accessor returns {@code Optional<Identifier>}.
 */
@WailaPlugin
public class NeoOriginsJadePlugin implements IWailaPlugin {

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "origin");

    @Override
    public void registerClient(IWailaClientRegistration reg) {
        reg.registerEntityComponent(OriginProvider.INSTANCE, LivingEntity.class);
    }

    private enum OriginProvider implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            Entity entity = accessor.getEntity();
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            Optional<Identifier> origin =
                living.getData(EntityAttachments.mobOriginData()).getOriginId();
            origin.ifPresent(id ->
                tooltip.add(Component.translatable("tooltip.neoorigins.origin", id.toString())));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
