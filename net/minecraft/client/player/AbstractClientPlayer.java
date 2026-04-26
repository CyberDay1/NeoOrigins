package net.minecraft.client.player;

import com.mojang.authlib.GameProfile;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractClientPlayer extends Player {
    @Nullable
    private PlayerInfo playerInfo;
    protected Vec3 deltaMovementOnPreviousTick = Vec3.ZERO;
    public float elytraRotX;
    public float elytraRotY;
    public float elytraRotZ;
    public final ClientLevel clientLevel;

    public AbstractClientPlayer(ClientLevel p_250460_, GameProfile p_249912_) {
        super(p_250460_, p_250460_.getSharedSpawnPos(), p_250460_.getSharedSpawnAngle(), p_249912_);
        this.clientLevel = p_250460_;
    }

    @Override
    public boolean isSpectator() {
        PlayerInfo playerinfo = this.getPlayerInfo();
        return playerinfo != null && playerinfo.getGameMode() == GameType.SPECTATOR;
    }

    @Override
    public boolean isCreative() {
        PlayerInfo playerinfo = this.getPlayerInfo();
        return playerinfo != null && playerinfo.getGameMode() == GameType.CREATIVE;
    }

    @Nullable
    protected PlayerInfo getPlayerInfo() {
        if (this.playerInfo == null) {
            this.playerInfo = Minecraft.getInstance().getConnection().getPlayerInfo(this.getUUID());
        }

        return this.playerInfo;
    }

    @Override
    public void tick() {
        this.deltaMovementOnPreviousTick = this.getDeltaMovement();
        super.tick();
    }

    public Vec3 getDeltaMovementLerped(float p_272943_) {
        return this.deltaMovementOnPreviousTick.lerp(this.getDeltaMovement(), (double)p_272943_);
    }

    public PlayerSkin getSkin() {
        PlayerInfo playerinfo = this.getPlayerInfo();
        return playerinfo == null ? DefaultPlayerSkin.get(this.getUUID()) : playerinfo.getSkin();
    }

    public float getFieldOfViewModifier() {
        float f = 1.0F;
        if (this.getAbilities().flying) {
            f *= 1.1F;
        }

        f *= ((float)this.getAttributeValue(Attributes.MOVEMENT_SPEED) / this.getAbilities().getWalkingSpeed() + 1.0F) / 2.0F;
        if (this.getAbilities().getWalkingSpeed() == 0.0F || Float.isNaN(f) || Float.isInfinite(f)) {
            f = 1.0F;
        }

        ItemStack itemstack = this.getUseItem();
        if (this.isUsingItem()) {
            if (itemstack.is(Items.BOW)) {
                int i = this.getTicksUsingItem();
                float f1 = (float)i / 20.0F;
                if (f1 > 1.0F) {
                    f1 = 1.0F;
                } else {
                    f1 *= f1;
                }

                f *= 1.0F - f1 * 0.15F;
            } else if (Minecraft.getInstance().options.getCameraType().isFirstPerson() && this.isScoping()) {
                return 0.1F;
            }
        }

        return net.neoforged.neoforge.client.ClientHooks.getFieldOfViewModifier(this, f);
    }
}
