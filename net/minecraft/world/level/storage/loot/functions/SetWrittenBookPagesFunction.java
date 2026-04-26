package net.minecraft.world.level.storage.loot.functions;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetWrittenBookPagesFunction extends LootItemConditionalFunction {
    public static final Codec<Component> PAGE_CODEC = ComponentSerialization.CODEC
        .validate(p_338165_ -> WrittenBookContent.CONTENT_CODEC.encodeStart(JavaOps.INSTANCE, p_338165_).map(p_335363_ -> p_338165_));
    public static final MapCodec<SetWrittenBookPagesFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_340804_ -> commonFields(p_340804_)
                .and(
                    p_340804_.group(
                        WrittenBookContent.pagesCodec(PAGE_CODEC).fieldOf("pages").forGetter(p_333939_ -> p_333939_.pages),
                        ListOperation.UNLIMITED_CODEC.forGetter(p_333933_ -> p_333933_.pageOperation)
                    )
                )
                .apply(p_340804_, SetWrittenBookPagesFunction::new)
    );
    private final List<Filterable<Component>> pages;
    private final ListOperation pageOperation;

    public SetWrittenBookPagesFunction(List<LootItemCondition> p_333863_, List<Filterable<Component>> p_333788_, ListOperation p_334047_) {
        super(p_333863_);
        this.pages = p_333788_;
        this.pageOperation = p_334047_;
    }

    @Override
    protected ItemStack run(ItemStack p_333960_, LootContext p_333892_) {
        p_333960_.update(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY, this::apply);
        return p_333960_;
    }

    @VisibleForTesting
    public WrittenBookContent apply(WrittenBookContent p_333830_) {
        List<Filterable<Component>> list = this.pageOperation.apply(p_333830_.pages(), this.pages);
        return p_333830_.withReplacedPages(list);
    }

    @Override
    public LootItemFunctionType<SetWrittenBookPagesFunction> getType() {
        return LootItemFunctions.SET_WRITTEN_BOOK_PAGES;
    }
}
