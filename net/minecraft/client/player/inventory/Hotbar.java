package net.minecraft.client.player.inventory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class Hotbar {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SIZE = Inventory.getSelectionSize();
    public static final Codec<Hotbar> CODEC = Codec.PASSTHROUGH
        .listOf()
        .validate(p_324442_ -> Util.fixedSize(p_324442_, SIZE))
        .xmap(Hotbar::new, p_323563_ -> p_323563_.items);
    private static final DynamicOps<Tag> DEFAULT_OPS = NbtOps.INSTANCE;
    private static final Dynamic<?> EMPTY_STACK = new Dynamic<>(DEFAULT_OPS, ItemStack.OPTIONAL_CODEC.encodeStart(DEFAULT_OPS, ItemStack.EMPTY).getOrThrow());
    private List<Dynamic<?>> items;

    private Hotbar(List<Dynamic<?>> p_324385_) {
        this.items = p_324385_;
    }

    public Hotbar() {
        this(Collections.nCopies(SIZE, EMPTY_STACK));
    }

    public List<ItemStack> load(HolderLookup.Provider p_323741_) {
        return this.items
            .stream()
            .map(
                p_337421_ -> ItemStack.OPTIONAL_CODEC
                        .parse(RegistryOps.injectRegistryContext((Dynamic<?>)p_337421_, p_323741_))
                        .resultOrPartial(p_323502_ -> LOGGER.warn("Could not parse hotbar item: {}", p_323502_))
                        .orElse(ItemStack.EMPTY)
            )
            .toList();
    }

    public void storeFrom(Inventory p_324559_, RegistryAccess p_324514_) {
        RegistryOps<Tag> registryops = p_324514_.createSerializationContext(DEFAULT_OPS);
        Builder<Dynamic<?>> builder = ImmutableList.builderWithExpectedSize(SIZE);

        for (int i = 0; i < SIZE; i++) {
            ItemStack itemstack = p_324559_.getItem(i);
            Optional<Dynamic<?>> optional = ItemStack.OPTIONAL_CODEC
                .encodeStart(registryops, itemstack)
                .resultOrPartial(p_323853_ -> LOGGER.warn("Could not encode hotbar item: {}", p_323853_))
                .map(p_323985_ -> new Dynamic<>(DEFAULT_OPS, p_323985_));
            builder.add(optional.orElse(EMPTY_STACK));
        }

        this.items = builder.build();
    }

    public boolean isEmpty() {
        for (Dynamic<?> dynamic : this.items) {
            if (!isEmpty(dynamic)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isEmpty(Dynamic<?> p_324499_) {
        return EMPTY_STACK.equals(p_324499_);
    }
}
