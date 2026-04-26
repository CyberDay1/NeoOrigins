package net.minecraft.world.item;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class AnimalArmorItem extends ArmorItem {
    private final ResourceLocation textureLocation;
    @Nullable
    private final ResourceLocation overlayTextureLocation;
    private final AnimalArmorItem.BodyType bodyType;

    public AnimalArmorItem(Holder<ArmorMaterial> p_323836_, AnimalArmorItem.BodyType p_324315_, boolean p_331679_, Item.Properties p_316341_) {
        super(p_323836_, ArmorItem.Type.BODY, p_316341_);
        this.bodyType = p_324315_;
        ResourceLocation resourcelocation = p_324315_.textureLocator.apply(p_323836_.unwrapKey().orElseThrow().location());
        this.textureLocation = resourcelocation.withSuffix(".png");
        if (p_331679_) {
            this.overlayTextureLocation = resourcelocation.withSuffix("_overlay.png");
        } else {
            this.overlayTextureLocation = null;
        }
    }

    public ResourceLocation getTexture() {
        return this.textureLocation;
    }

    @Nullable
    public ResourceLocation getOverlayTexture() {
        return this.overlayTextureLocation;
    }

    public AnimalArmorItem.BodyType getBodyType() {
        return this.bodyType;
    }

    @Override
    public SoundEvent getBreakingSound() {
        return this.bodyType.breakingSound;
    }

    @Override
    public boolean isEnchantable(ItemStack p_341697_) {
        return false;
    }

    public static enum BodyType {
        EQUESTRIAN(p_323547_ -> p_323547_.withPath(p_323717_ -> "textures/entity/horse/armor/horse_armor_" + p_323717_), SoundEvents.ITEM_BREAK),
        CANINE(p_323678_ -> p_323678_.withPath("textures/entity/wolf/wolf_armor"), SoundEvents.WOLF_ARMOR_BREAK);

        final Function<ResourceLocation, ResourceLocation> textureLocator;
        final SoundEvent breakingSound;

        private BodyType(Function<ResourceLocation, ResourceLocation> p_324258_, SoundEvent p_331429_) {
            this.textureLocator = p_324258_;
            this.breakingSound = p_331429_;
        }
    }
}
