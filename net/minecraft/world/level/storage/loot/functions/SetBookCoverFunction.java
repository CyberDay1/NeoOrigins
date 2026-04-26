package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetBookCoverFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetBookCoverFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_338146_ -> commonFields(p_338146_)
                .and(
                    p_338146_.group(
                        Filterable.codec(Codec.string(0, 32)).optionalFieldOf("title").forGetter(p_333759_ -> p_333759_.title),
                        Codec.STRING.optionalFieldOf("author").forGetter(p_333817_ -> p_333817_.author),
                        ExtraCodecs.intRange(0, 3).optionalFieldOf("generation").forGetter(p_333964_ -> p_333964_.generation)
                    )
                )
                .apply(p_338146_, SetBookCoverFunction::new)
    );
    private final Optional<String> author;
    private final Optional<Filterable<String>> title;
    private final Optional<Integer> generation;

    public SetBookCoverFunction(
        List<LootItemCondition> p_333787_, Optional<Filterable<String>> p_333956_, Optional<String> p_334034_, Optional<Integer> p_333739_
    ) {
        super(p_333787_);
        this.author = p_334034_;
        this.title = p_333956_;
        this.generation = p_333739_;
    }

    @Override
    protected ItemStack run(ItemStack p_334048_, LootContext p_334012_) {
        p_334048_.update(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY, this::apply);
        return p_334048_;
    }

    private WrittenBookContent apply(WrittenBookContent p_333821_) {
        return new WrittenBookContent(
            this.title.orElseGet(p_333821_::title),
            this.author.orElseGet(p_333821_::author),
            this.generation.orElseGet(p_333821_::generation),
            p_333821_.pages(),
            p_333821_.resolved()
        );
    }

    @Override
    public LootItemFunctionType<SetBookCoverFunction> getType() {
        return LootItemFunctions.SET_BOOK_COVER;
    }
}
