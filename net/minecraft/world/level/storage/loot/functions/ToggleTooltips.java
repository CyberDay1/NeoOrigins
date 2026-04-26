package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ToggleTooltips extends LootItemConditionalFunction {
    private static final Map<DataComponentType<?>, ToggleTooltips.ComponentToggle<?>> TOGGLES = Stream.of(
            new ToggleTooltips.ComponentToggle<>(DataComponents.TRIM, ArmorTrim::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.DYED_COLOR, DyedItemColor::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.ENCHANTMENTS, ItemEnchantments::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.UNBREAKABLE, Unbreakable::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.CAN_BREAK, AdventureModePredicate::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.CAN_PLACE_ON, AdventureModePredicate::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers::withTooltip),
            new ToggleTooltips.ComponentToggle<>(DataComponents.JUKEBOX_PLAYABLE, JukeboxPlayable::withTooltip)
        )
        .collect(Collectors.toMap(ToggleTooltips.ComponentToggle::type, p_335612_ -> (ToggleTooltips.ComponentToggle<?>)p_335612_));
    private static final Codec<ToggleTooltips.ComponentToggle<?>> TOGGLE_CODEC = BuiltInRegistries.DATA_COMPONENT_TYPE
        .byNameCodec()
        .comapFlatMap(
            p_336140_ -> {
                ToggleTooltips.ComponentToggle<?> componenttoggle = TOGGLES.get(p_336140_);
                return componenttoggle != null
                    ? DataResult.success(componenttoggle)
                    : DataResult.error(
                        () -> "Can't toggle tooltip visiblity for " + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey((DataComponentType<?>)p_336140_)
                    );
            },
            ToggleTooltips.ComponentToggle::type
        );
    public static final MapCodec<ToggleTooltips> CODEC = RecordCodecBuilder.mapCodec(
        p_335794_ -> commonFields(p_335794_)
                .and(Codec.unboundedMap(TOGGLE_CODEC, Codec.BOOL).fieldOf("toggles").forGetter(p_335699_ -> p_335699_.values))
                .apply(p_335794_, ToggleTooltips::new)
    );
    private final Map<ToggleTooltips.ComponentToggle<?>, Boolean> values;

    public ToggleTooltips(List<LootItemCondition> p_335479_, Map<ToggleTooltips.ComponentToggle<?>, Boolean> p_336164_) {
        super(p_335479_);
        this.values = p_336164_;
    }

    @Override
    protected ItemStack run(ItemStack p_335844_, LootContext p_335764_) {
        this.values.forEach((p_336152_, p_335653_) -> p_336152_.applyIfPresent(p_335844_, p_335653_));
        return p_335844_;
    }

    @Override
    public LootItemFunctionType<ToggleTooltips> getType() {
        return LootItemFunctions.TOGGLE_TOOLTIPS;
    }

    static record ComponentToggle<T>(DataComponentType<T> type, ToggleTooltips.TooltipWither<T> setter) {
        public void applyIfPresent(ItemStack p_335390_, boolean p_335686_) {
            T t = p_335390_.get(this.type);
            if (t != null) {
                p_335390_.set(this.type, this.setter.withTooltip(t, p_335686_));
            }
        }
    }

    @FunctionalInterface
    interface TooltipWither<T> {
        T withTooltip(T p_335804_, boolean p_335957_);
    }
}
