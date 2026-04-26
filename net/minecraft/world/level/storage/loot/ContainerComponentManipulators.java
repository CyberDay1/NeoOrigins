package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;

public interface ContainerComponentManipulators {
    ContainerComponentManipulator<ItemContainerContents> CONTAINER = new ContainerComponentManipulator<ItemContainerContents>() {
        @Override
        public DataComponentType<ItemContainerContents> type() {
            return DataComponents.CONTAINER;
        }

        public Stream<ItemStack> getContents(ItemContainerContents p_340963_) {
            return p_340963_.stream();
        }

        public ItemContainerContents empty() {
            return ItemContainerContents.EMPTY;
        }

        public ItemContainerContents setContents(ItemContainerContents p_341159_, Stream<ItemStack> p_340907_) {
            return ItemContainerContents.fromItems(p_340907_.toList());
        }
    };
    ContainerComponentManipulator<BundleContents> BUNDLE_CONTENTS = new ContainerComponentManipulator<BundleContents>() {
        @Override
        public DataComponentType<BundleContents> type() {
            return DataComponents.BUNDLE_CONTENTS;
        }

        public BundleContents empty() {
            return BundleContents.EMPTY;
        }

        public Stream<ItemStack> getContents(BundleContents p_341255_) {
            return p_341255_.itemCopyStream();
        }

        public BundleContents setContents(BundleContents p_341134_, Stream<ItemStack> p_341127_) {
            BundleContents.Mutable bundlecontents$mutable = new BundleContents.Mutable(p_341134_).clearItems();
            p_341127_.forEach(bundlecontents$mutable::tryInsert);
            return bundlecontents$mutable.toImmutable();
        }
    };
    ContainerComponentManipulator<ChargedProjectiles> CHARGED_PROJECTILES = new ContainerComponentManipulator<ChargedProjectiles>() {
        @Override
        public DataComponentType<ChargedProjectiles> type() {
            return DataComponents.CHARGED_PROJECTILES;
        }

        public ChargedProjectiles empty() {
            return ChargedProjectiles.EMPTY;
        }

        public Stream<ItemStack> getContents(ChargedProjectiles p_341086_) {
            return p_341086_.getItems().stream();
        }

        public ChargedProjectiles setContents(ChargedProjectiles p_341319_, Stream<ItemStack> p_341213_) {
            return ChargedProjectiles.of(p_341213_.toList());
        }
    };
    Map<DataComponentType<?>, ContainerComponentManipulator<?>> ALL_MANIPULATORS = Stream.of(CONTAINER, BUNDLE_CONTENTS, CHARGED_PROJECTILES)
        .collect(Collectors.toMap(ContainerComponentManipulator::type, p_340858_ -> (ContainerComponentManipulator<?>)p_340858_));
    Codec<ContainerComponentManipulator<?>> CODEC = BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().comapFlatMap(p_341089_ -> {
        ContainerComponentManipulator<?> containercomponentmanipulator = ALL_MANIPULATORS.get(p_341089_);
        return containercomponentmanipulator != null ? DataResult.success(containercomponentmanipulator) : DataResult.error(() -> "No items in component");
    }, ContainerComponentManipulator::type);
}
