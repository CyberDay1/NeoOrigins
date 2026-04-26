package net.minecraft.client.renderer.item;

import com.google.common.collect.Maps;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemProperties {
    private static final Map<ResourceLocation, ItemPropertyFunction> GENERIC_PROPERTIES = Maps.newHashMap();
    private static final ResourceLocation DAMAGED = ResourceLocation.withDefaultNamespace("damaged");
    private static final ResourceLocation DAMAGE = ResourceLocation.withDefaultNamespace("damage");
    private static final ClampedItemPropertyFunction PROPERTY_DAMAGED = (p_174660_, p_174661_, p_174662_, p_174663_) -> p_174660_.isDamaged() ? 1.0F : 0.0F;
    private static final ClampedItemPropertyFunction PROPERTY_DAMAGE = (p_174655_, p_174656_, p_174657_, p_174658_) -> Mth.clamp(
            (float)p_174655_.getDamageValue() / (float)p_174655_.getMaxDamage(), 0.0F, 1.0F
        );
    private static final Map<Item, Map<ResourceLocation, ItemPropertyFunction>> PROPERTIES = Maps.newHashMap();

    private static ClampedItemPropertyFunction registerGeneric(ResourceLocation p_174582_, ClampedItemPropertyFunction p_174583_) {
        return (ClampedItemPropertyFunction) registerGeneric(p_174582_, (ItemPropertyFunction) p_174583_);
    }
    public static ItemPropertyFunction registerGeneric(ResourceLocation p_174582_, ItemPropertyFunction p_174583_) {
        GENERIC_PROPERTIES.put(p_174582_, p_174583_);
        return p_174583_;
    }

    private static void registerCustomModelData(ItemPropertyFunction p_174580_) {
        GENERIC_PROPERTIES.put(ResourceLocation.withDefaultNamespace("custom_model_data"), p_174580_);
    }

    private static void register(Item p_174571_, ResourceLocation p_174572_, ClampedItemPropertyFunction p_174573_) {
        register(p_174571_, p_174572_, (ItemPropertyFunction) p_174573_);
    }

    public static void register(Item p_174571_, ResourceLocation p_174572_, ItemPropertyFunction p_174573_) {
        PROPERTIES.computeIfAbsent(p_174571_, p_117828_ -> Maps.newHashMap()).put(p_174572_, p_174573_);
    }

    @Nullable
    public static ItemPropertyFunction getProperty(ItemStack p_336078_, ResourceLocation p_117831_) {
        if (p_336078_.getMaxDamage() > 0) {
            if (DAMAGE.equals(p_117831_)) {
                return PROPERTY_DAMAGE;
            }

            if (DAMAGED.equals(p_117831_)) {
                return PROPERTY_DAMAGED;
            }
        }

        ItemPropertyFunction itempropertyfunction = GENERIC_PROPERTIES.get(p_117831_);
        if (itempropertyfunction != null) {
            return itempropertyfunction;
        } else {
            Map<ResourceLocation, ItemPropertyFunction> map = PROPERTIES.get(p_336078_.getItem());
            return map == null ? null : map.get(p_117831_);
        }
    }

    static {
        registerGeneric(
            ResourceLocation.withDefaultNamespace("lefthanded"),
            (p_174650_, p_174651_, p_174652_, p_174653_) -> p_174652_ != null && p_174652_.getMainArm() != HumanoidArm.RIGHT ? 1.0F : 0.0F
        );
        registerGeneric(
            ResourceLocation.withDefaultNamespace("cooldown"),
            (p_174645_, p_174646_, p_174647_, p_174648_) -> p_174647_ instanceof Player
                    ? ((Player)p_174647_).getCooldowns().getCooldownPercent(p_174645_.getItem(), 0.0F)
                    : 0.0F
        );
        ClampedItemPropertyFunction clampeditempropertyfunction = (p_329803_, p_329804_, p_329805_, p_329806_) -> {
            ArmorTrim armortrim = p_329803_.get(DataComponents.TRIM);
            return armortrim != null ? armortrim.material().value().itemModelIndex() : Float.NEGATIVE_INFINITY;
        };
        registerGeneric(ItemModelGenerators.TRIM_TYPE_PREDICATE_ID, clampeditempropertyfunction);
        registerCustomModelData(
            (p_329792_, p_329793_, p_329794_, p_329795_) -> (float)p_329792_.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value()
        );
        register(Items.BOW, ResourceLocation.withDefaultNamespace("pull"), (p_344163_, p_344164_, p_344165_, p_344166_) -> {
            if (p_344165_ == null) {
                return 0.0F;
            } else {
                return p_344165_.getUseItem() != p_344163_ ? 0.0F : (float)(p_344163_.getUseDuration(p_344165_) - p_344165_.getUseItemRemainingTicks()) / 20.0F;
            }
        });
        register(
            Items.BRUSH,
            ResourceLocation.withDefaultNamespace("brushing"),
            (p_272332_, p_272333_, p_272334_, p_272335_) -> p_272334_ != null && p_272334_.getUseItem() == p_272332_
                    ? (float)(p_272334_.getUseItemRemainingTicks() % 10) / 10.0F
                    : 0.0F
        );
        register(
            Items.BOW,
            ResourceLocation.withDefaultNamespace("pulling"),
            (p_174630_, p_174631_, p_174632_, p_174633_) -> p_174632_ != null && p_174632_.isUsingItem() && p_174632_.getUseItem() == p_174630_ ? 1.0F : 0.0F
        );
        register(
            Items.BUNDLE,
            ResourceLocation.withDefaultNamespace("filled"),
            (p_174625_, p_174626_, p_174627_, p_174628_) -> BundleItem.getFullnessDisplay(p_174625_)
        );
        register(Items.CLOCK, ResourceLocation.withDefaultNamespace("time"), new ClampedItemPropertyFunction() {
            private double rotation;
            private double rota;
            private long lastUpdateTick;

            @Override
            public float unclampedCall(ItemStack p_174665_, @Nullable ClientLevel p_174666_, @Nullable LivingEntity p_174667_, int p_174668_) {
                Entity entity = (Entity)(p_174667_ != null ? p_174667_ : p_174665_.getEntityRepresentation());
                if (entity == null) {
                    return 0.0F;
                } else {
                    if (p_174666_ == null && entity.level() instanceof ClientLevel) {
                        p_174666_ = (ClientLevel)entity.level();
                    }

                    if (p_174666_ == null) {
                        return 0.0F;
                    } else {
                        double d0;
                        if (p_174666_.dimensionType().natural()) {
                            d0 = (double)p_174666_.getTimeOfDay(1.0F);
                        } else {
                            d0 = Math.random();
                        }

                        d0 = this.wobble(p_174666_, d0);
                        return (float)d0;
                    }
                }
            }

            private double wobble(Level p_117904_, double p_117905_) {
                if (p_117904_.getGameTime() != this.lastUpdateTick) {
                    this.lastUpdateTick = p_117904_.getGameTime();
                    double d0 = p_117905_ - this.rotation;
                    d0 = Mth.positiveModulo(d0 + 0.5, 1.0) - 0.5;
                    this.rota += d0 * 0.1;
                    this.rota *= 0.9;
                    this.rotation = Mth.positiveModulo(this.rotation + this.rota, 1.0);
                }

                return this.rotation;
            }
        });
        register(Items.COMPASS, ResourceLocation.withDefaultNamespace("angle"), new CompassItemPropertyFunction((p_332536_, p_332537_, p_332538_) -> {
            LodestoneTracker lodestonetracker = p_332537_.get(DataComponents.LODESTONE_TRACKER);
            return lodestonetracker != null ? lodestonetracker.target().orElse(null) : CompassItem.getSpawnPosition(p_332536_);
        }));
        register(
            Items.RECOVERY_COMPASS,
            ResourceLocation.withDefaultNamespace("angle"),
            new CompassItemPropertyFunction(
                (p_234983_, p_234984_, p_234985_) -> p_234985_ instanceof Player player ? player.getLastDeathLocation().orElse(null) : null
            )
        );
        register(
            Items.CROSSBOW,
            ResourceLocation.withDefaultNamespace("pull"),
            (p_351682_, p_351683_, p_351684_, p_351685_) -> {
                if (p_351684_ == null) {
                    return 0.0F;
                } else {
                    return CrossbowItem.isCharged(p_351682_)
                        ? 0.0F
                        : (float)(p_351682_.getUseDuration(p_351684_) - p_351684_.getUseItemRemainingTicks())
                            / (float)CrossbowItem.getChargeDuration(p_351682_, p_351684_);
                }
            }
        );
        register(
            Items.CROSSBOW,
            ResourceLocation.withDefaultNamespace("pulling"),
            (p_174605_, p_174606_, p_174607_, p_174608_) -> p_174607_ != null
                        && p_174607_.isUsingItem()
                        && p_174607_.getUseItem() == p_174605_
                        && !CrossbowItem.isCharged(p_174605_)
                    ? 1.0F
                    : 0.0F
        );
        register(
            Items.CROSSBOW,
            ResourceLocation.withDefaultNamespace("charged"),
            (p_275891_, p_275892_, p_275893_, p_275894_) -> CrossbowItem.isCharged(p_275891_) ? 1.0F : 0.0F
        );
        register(Items.CROSSBOW, ResourceLocation.withDefaultNamespace("firework"), (p_329796_, p_329797_, p_329798_, p_329799_) -> {
            ChargedProjectiles chargedprojectiles = p_329796_.get(DataComponents.CHARGED_PROJECTILES);
            return chargedprojectiles != null && chargedprojectiles.contains(Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
        });
        register(
            Items.ELYTRA,
            ResourceLocation.withDefaultNamespace("broken"),
            (p_174590_, p_174591_, p_174592_, p_174593_) -> ElytraItem.isFlyEnabled(p_174590_) ? 0.0F : 1.0F
        );
        register(Items.FISHING_ROD, ResourceLocation.withDefaultNamespace("cast"), (p_174585_, p_174586_, p_174587_, p_174588_) -> {
            if (p_174587_ == null) {
                return 0.0F;
            } else {
                boolean flag = p_174587_.getMainHandItem() == p_174585_;
                boolean flag1 = p_174587_.getOffhandItem() == p_174585_;
                if (p_174587_.getMainHandItem().getItem() instanceof FishingRodItem) {
                    flag1 = false;
                }

                return (flag || flag1) && p_174587_ instanceof Player && ((Player)p_174587_).fishing != null ? 1.0F : 0.0F;
            }
        });
        register(
            Items.SHIELD,
            ResourceLocation.withDefaultNamespace("blocking"),
            (p_174575_, p_174576_, p_174577_, p_174578_) -> p_174577_ != null && p_174577_.isUsingItem() && p_174577_.getUseItem() == p_174575_ ? 1.0F : 0.0F
        );
        register(
            Items.TRIDENT,
            ResourceLocation.withDefaultNamespace("throwing"),
            (p_234996_, p_234997_, p_234998_, p_234999_) -> p_234998_ != null && p_234998_.isUsingItem() && p_234998_.getUseItem() == p_234996_ ? 1.0F : 0.0F
        );
        register(Items.LIGHT, ResourceLocation.withDefaultNamespace("level"), (p_329788_, p_329789_, p_329790_, p_329791_) -> {
            BlockItemStateProperties blockitemstateproperties = p_329788_.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
            Integer integer = blockitemstateproperties.get(LightBlock.LEVEL);
            return integer != null ? (float)integer.intValue() / 16.0F : 1.0F;
        });
        register(
            Items.GOAT_HORN,
            ResourceLocation.withDefaultNamespace("tooting"),
            (p_234978_, p_234979_, p_234980_, p_234981_) -> p_234980_ != null && p_234980_.isUsingItem() && p_234980_.getUseItem() == p_234978_ ? 1.0F : 0.0F
        );
    }
}
