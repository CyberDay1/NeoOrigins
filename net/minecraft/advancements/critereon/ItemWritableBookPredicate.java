package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;

public record ItemWritableBookPredicate(Optional<CollectionPredicate<Filterable<String>, ItemWritableBookPredicate.PagePredicate>> pages)
    implements SingleComponentItemPredicate<WritableBookContent> {
    public static final Codec<ItemWritableBookPredicate> CODEC = RecordCodecBuilder.create(
        p_341337_ -> p_341337_.group(
                    CollectionPredicate.<Filterable<String>, ItemWritableBookPredicate.PagePredicate>codec(ItemWritableBookPredicate.PagePredicate.CODEC)
                        .optionalFieldOf("pages")
                        .forGetter(ItemWritableBookPredicate::pages)
                )
                .apply(p_341337_, ItemWritableBookPredicate::new)
    );

    @Override
    public DataComponentType<WritableBookContent> componentType() {
        return DataComponents.WRITABLE_BOOK_CONTENT;
    }

    public boolean matches(ItemStack p_340860_, WritableBookContent p_341176_) {
        return !this.pages.isPresent() || this.pages.get().test(p_341176_.pages());
    }

    public static record PagePredicate(String contents) implements Predicate<Filterable<String>> {
        public static final Codec<ItemWritableBookPredicate.PagePredicate> CODEC = Codec.STRING
            .xmap(ItemWritableBookPredicate.PagePredicate::new, ItemWritableBookPredicate.PagePredicate::contents);

        public boolean test(Filterable<String> p_341135_) {
            return p_341135_.raw().equals(this.contents);
        }
    }
}
