package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public record DebugStickState(Map<Holder<Block>, Property<?>> properties) {
    public static final DebugStickState EMPTY = new DebugStickState(Map.of());
    public static final Codec<DebugStickState> CODEC = Codec.<Holder<Block>, Property<?>>dispatchedMap(
            BuiltInRegistries.BLOCK.holderByNameCodec(),
            p_331051_ -> Codec.STRING
                    .comapFlatMap(
                        p_330359_ -> {
                            Property<?> property = p_331051_.value().getStateDefinition().getProperty(p_330359_);
                            return property != null
                                ? DataResult.success(property)
                                : DataResult.error(() -> "No property on " + p_331051_.getRegisteredName() + " with name: " + p_330359_);
                        },
                        Property::getName
                    )
        )
        .xmap(DebugStickState::new, DebugStickState::properties);

    public DebugStickState withProperty(Holder<Block> p_331264_, Property<?> p_330373_) {
        return new DebugStickState(Util.copyAndPut(this.properties, p_331264_, p_330373_));
    }
}
