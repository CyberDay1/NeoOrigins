package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetWritableBookPagesFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetWritableBookPagesFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_335359_ -> commonFields(p_335359_)
                .and(
                    p_335359_.group(
                        WritableBookContent.PAGES_CODEC.fieldOf("pages").forGetter(p_333827_ -> p_333827_.pages),
                        ListOperation.codec(100).forGetter(p_334060_ -> p_334060_.pageOperation)
                    )
                )
                .apply(p_335359_, SetWritableBookPagesFunction::new)
    );
    private final List<Filterable<String>> pages;
    private final ListOperation pageOperation;

    public SetWritableBookPagesFunction(List<LootItemCondition> p_333911_, List<Filterable<String>> p_333983_, ListOperation p_333754_) {
        super(p_333911_);
        this.pages = p_333983_;
        this.pageOperation = p_333754_;
    }

    @Override
    protected ItemStack run(ItemStack p_333832_, LootContext p_333929_) {
        p_333832_.update(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY, this::apply);
        return p_333832_;
    }

    public WritableBookContent apply(WritableBookContent p_334044_) {
        List<Filterable<String>> list = this.pageOperation.apply(p_334044_.pages(), this.pages, 100);
        return p_334044_.withReplacedPages(list);
    }

    @Override
    public LootItemFunctionType<SetWritableBookPagesFunction> getType() {
        return LootItemFunctions.SET_WRITABLE_BOOK_PAGES;
    }
}
