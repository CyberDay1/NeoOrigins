package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.EncoderCache;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Unit;
import net.minecraft.world.LockCode;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DebugStickState;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.saveddata.maps.MapId;

public class DataComponents {
    static final EncoderCache ENCODER_CACHE = new EncoderCache(512);
    public static final DataComponentType<CustomData> CUSTOM_DATA = register("custom_data", p_331418_ -> p_331418_.persistent(CustomData.CODEC));
    public static final DataComponentType<Integer> MAX_STACK_SIZE = register(
        "max_stack_size", p_335179_ -> p_335179_.persistent(ExtraCodecs.intRange(1, 99)).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DataComponentType<Integer> MAX_DAMAGE = register(
        "max_damage", p_335177_ -> p_335177_.persistent(ExtraCodecs.POSITIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DataComponentType<Integer> DAMAGE = register(
        "damage", p_331382_ -> p_331382_.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DataComponentType<Unbreakable> UNBREAKABLE = register(
        "unbreakable", p_330880_ -> p_330880_.persistent(Unbreakable.CODEC).networkSynchronized(Unbreakable.STREAM_CODEC)
    );
    public static final DataComponentType<Component> CUSTOM_NAME = register(
        "custom_name",
        p_341853_ -> p_341853_.persistent(ComponentSerialization.FLAT_CODEC).networkSynchronized(ComponentSerialization.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Component> ITEM_NAME = register(
        "item_name",
        p_341844_ -> p_341844_.persistent(ComponentSerialization.FLAT_CODEC).networkSynchronized(ComponentSerialization.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ItemLore> LORE = register(
        "lore", p_341842_ -> p_341842_.persistent(ItemLore.CODEC).networkSynchronized(ItemLore.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Rarity> RARITY = register(
        "rarity", p_335176_ -> p_335176_.persistent(Rarity.CODEC).networkSynchronized(Rarity.STREAM_CODEC)
    );
    public static final DataComponentType<ItemEnchantments> ENCHANTMENTS = register(
        "enchantments", p_341840_ -> p_341840_.persistent(ItemEnchantments.CODEC).networkSynchronized(ItemEnchantments.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<AdventureModePredicate> CAN_PLACE_ON = register(
        "can_place_on",
        p_341861_ -> p_341861_.persistent(AdventureModePredicate.CODEC).networkSynchronized(AdventureModePredicate.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<AdventureModePredicate> CAN_BREAK = register(
        "can_break", p_341837_ -> p_341837_.persistent(AdventureModePredicate.CODEC).networkSynchronized(AdventureModePredicate.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ItemAttributeModifiers> ATTRIBUTE_MODIFIERS = register(
        "attribute_modifiers",
        p_341845_ -> p_341845_.persistent(ItemAttributeModifiers.CODEC).networkSynchronized(ItemAttributeModifiers.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<CustomModelData> CUSTOM_MODEL_DATA = register(
        "custom_model_data", p_330559_ -> p_330559_.persistent(CustomModelData.CODEC).networkSynchronized(CustomModelData.STREAM_CODEC)
    );
    public static final DataComponentType<Unit> HIDE_ADDITIONAL_TOOLTIP = register(
        "hide_additional_tooltip", p_344188_ -> p_344188_.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
    );
    public static final DataComponentType<Unit> HIDE_TOOLTIP = register(
        "hide_tooltip", p_331610_ -> p_331610_.persistent(Codec.unit(Unit.INSTANCE)).networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
    );
    public static final DataComponentType<Integer> REPAIR_COST = register(
        "repair_cost", p_331555_ -> p_331555_.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DataComponentType<Unit> CREATIVE_SLOT_LOCK = register(
        "creative_slot_lock", p_331031_ -> p_331031_.networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
    );
    public static final DataComponentType<Boolean> ENCHANTMENT_GLINT_OVERRIDE = register(
        "enchantment_glint_override", p_330231_ -> p_330231_.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL)
    );
    public static final DataComponentType<Unit> INTANGIBLE_PROJECTILE = register("intangible_projectile", p_344189_ -> p_344189_.persistent(Unit.CODEC));
    public static final DataComponentType<FoodProperties> FOOD = register(
        "food", p_341858_ -> p_341858_.persistent(FoodProperties.DIRECT_CODEC).networkSynchronized(FoodProperties.DIRECT_STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Unit> FIRE_RESISTANT = register(
        "fire_resistant", p_344190_ -> p_344190_.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
    );
    public static final DataComponentType<Tool> TOOL = register(
        "tool", p_341839_ -> p_341839_.persistent(Tool.CODEC).networkSynchronized(Tool.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ItemEnchantments> STORED_ENCHANTMENTS = register(
        "stored_enchantments", p_341841_ -> p_341841_.persistent(ItemEnchantments.CODEC).networkSynchronized(ItemEnchantments.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<DyedItemColor> DYED_COLOR = register(
        "dyed_color", p_331088_ -> p_331088_.persistent(DyedItemColor.CODEC).networkSynchronized(DyedItemColor.STREAM_CODEC)
    );
    public static final DataComponentType<MapItemColor> MAP_COLOR = register(
        "map_color", p_330449_ -> p_330449_.persistent(MapItemColor.CODEC).networkSynchronized(MapItemColor.STREAM_CODEC)
    );
    public static final DataComponentType<MapId> MAP_ID = register(
        "map_id", p_330363_ -> p_330363_.persistent(MapId.CODEC).networkSynchronized(MapId.STREAM_CODEC)
    );
    public static final DataComponentType<MapDecorations> MAP_DECORATIONS = register(
        "map_decorations", p_341862_ -> p_341862_.persistent(MapDecorations.CODEC).cacheEncoding()
    );
    public static final DataComponentType<MapPostProcessing> MAP_POST_PROCESSING = register(
        "map_post_processing", p_331962_ -> p_331962_.networkSynchronized(MapPostProcessing.STREAM_CODEC)
    );
    public static final DataComponentType<ChargedProjectiles> CHARGED_PROJECTILES = register(
        "charged_projectiles", p_341859_ -> p_341859_.persistent(ChargedProjectiles.CODEC).networkSynchronized(ChargedProjectiles.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<BundleContents> BUNDLE_CONTENTS = register(
        "bundle_contents", p_341857_ -> p_341857_.persistent(BundleContents.CODEC).networkSynchronized(BundleContents.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<PotionContents> POTION_CONTENTS = register(
        "potion_contents", p_341836_ -> p_341836_.persistent(PotionContents.CODEC).networkSynchronized(PotionContents.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<SuspiciousStewEffects> SUSPICIOUS_STEW_EFFECTS = register(
        "suspicious_stew_effects",
        p_341847_ -> p_341847_.persistent(SuspiciousStewEffects.CODEC).networkSynchronized(SuspiciousStewEffects.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<WritableBookContent> WRITABLE_BOOK_CONTENT = register(
        "writable_book_content",
        p_341848_ -> p_341848_.persistent(WritableBookContent.CODEC).networkSynchronized(WritableBookContent.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<WrittenBookContent> WRITTEN_BOOK_CONTENT = register(
        "written_book_content",
        p_341852_ -> p_341852_.persistent(WrittenBookContent.CODEC).networkSynchronized(WrittenBookContent.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ArmorTrim> TRIM = register(
        "trim", p_341838_ -> p_341838_.persistent(ArmorTrim.CODEC).networkSynchronized(ArmorTrim.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<DebugStickState> DEBUG_STICK_STATE = register(
        "debug_stick_state", p_341865_ -> p_341865_.persistent(DebugStickState.CODEC).cacheEncoding()
    );
    public static final DataComponentType<CustomData> ENTITY_DATA = register(
        "entity_data", p_332024_ -> p_332024_.persistent(CustomData.CODEC_WITH_ID).networkSynchronized(CustomData.STREAM_CODEC)
    );
    public static final DataComponentType<CustomData> BUCKET_ENTITY_DATA = register(
        "bucket_entity_data", p_331109_ -> p_331109_.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC)
    );
    public static final DataComponentType<CustomData> BLOCK_ENTITY_DATA = register(
        "block_entity_data", p_330408_ -> p_330408_.persistent(CustomData.CODEC_WITH_ID).networkSynchronized(CustomData.STREAM_CODEC)
    );
    public static final DataComponentType<Holder<Instrument>> INSTRUMENT = register(
        "instrument", p_341855_ -> p_341855_.persistent(Instrument.CODEC).networkSynchronized(Instrument.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Integer> OMINOUS_BOTTLE_AMPLIFIER = register(
        "ominous_bottle_amplifier", p_337458_ -> p_337458_.persistent(ExtraCodecs.intRange(0, 4)).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    public static final DataComponentType<JukeboxPlayable> JUKEBOX_PLAYABLE = register(
        "jukebox_playable", p_349913_ -> p_349913_.persistent(JukeboxPlayable.CODEC).networkSynchronized(JukeboxPlayable.STREAM_CODEC)
    );
    public static final DataComponentType<List<ResourceLocation>> RECIPES = register(
        "recipes", p_341850_ -> p_341850_.persistent(ResourceLocation.CODEC.listOf()).cacheEncoding()
    );
    public static final DataComponentType<LodestoneTracker> LODESTONE_TRACKER = register(
        "lodestone_tracker", p_341854_ -> p_341854_.persistent(LodestoneTracker.CODEC).networkSynchronized(LodestoneTracker.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<FireworkExplosion> FIREWORK_EXPLOSION = register(
        "firework_explosion", p_341843_ -> p_341843_.persistent(FireworkExplosion.CODEC).networkSynchronized(FireworkExplosion.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<Fireworks> FIREWORKS = register(
        "fireworks", p_341860_ -> p_341860_.persistent(Fireworks.CODEC).networkSynchronized(Fireworks.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ResolvableProfile> PROFILE = register(
        "profile", p_341851_ -> p_341851_.persistent(ResolvableProfile.CODEC).networkSynchronized(ResolvableProfile.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ResourceLocation> NOTE_BLOCK_SOUND = register(
        "note_block_sound", p_330798_ -> p_330798_.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC)
    );
    public static final DataComponentType<BannerPatternLayers> BANNER_PATTERNS = register(
        "banner_patterns", p_341863_ -> p_341863_.persistent(BannerPatternLayers.CODEC).networkSynchronized(BannerPatternLayers.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<DyeColor> BASE_COLOR = register(
        "base_color", p_331467_ -> p_331467_.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
    );
    public static final DataComponentType<PotDecorations> POT_DECORATIONS = register(
        "pot_decorations", p_341864_ -> p_341864_.persistent(PotDecorations.CODEC).networkSynchronized(PotDecorations.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<ItemContainerContents> CONTAINER = register(
        "container", p_341846_ -> p_341846_.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<BlockItemStateProperties> BLOCK_STATE = register(
        "block_state",
        p_341856_ -> p_341856_.persistent(BlockItemStateProperties.CODEC).networkSynchronized(BlockItemStateProperties.STREAM_CODEC).cacheEncoding()
    );
    public static final DataComponentType<List<BeehiveBlockEntity.Occupant>> BEES = register(
        "bees",
        p_341849_ -> p_341849_.persistent(BeehiveBlockEntity.Occupant.LIST_CODEC)
                .networkSynchronized(BeehiveBlockEntity.Occupant.STREAM_CODEC.apply(ByteBufCodecs.list()))
                .cacheEncoding()
    );
    public static final DataComponentType<LockCode> LOCK = register("lock", p_330909_ -> p_330909_.persistent(LockCode.CODEC));
    public static final DataComponentType<SeededContainerLoot> CONTAINER_LOOT = register(
        "container_loot", p_331929_ -> p_331929_.persistent(SeededContainerLoot.CODEC)
    );
    public static final DataComponentMap COMMON_ITEM_COMPONENTS = DataComponentMap.builder()
        .set(MAX_STACK_SIZE, 64)
        .set(LORE, ItemLore.EMPTY)
        .set(ENCHANTMENTS, ItemEnchantments.EMPTY)
        .set(REPAIR_COST, 0)
        .set(ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        .set(RARITY, Rarity.COMMON)
        .build();

    public static DataComponentType<?> bootstrap(Registry<DataComponentType<?>> p_330821_) {
        return CUSTOM_DATA;
    }

    private static <T> DataComponentType<T> register(String p_332092_, UnaryOperator<DataComponentType.Builder<T>> p_331261_) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, p_332092_, p_331261_.apply(DataComponentType.builder()).build());
    }
}
