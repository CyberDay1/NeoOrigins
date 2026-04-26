package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class DropInvalidSignDataFix extends NamedEntityFix {
    private static final String[] FIELDS_TO_DROP = new String[]{
        "Text1", "Text2", "Text3", "Text4", "FilteredText1", "FilteredText2", "FilteredText3", "FilteredText4", "Color", "GlowingText"
    };

    public DropInvalidSignDataFix(Schema p_296288_, String p_294804_, String p_295913_) {
        super(p_296288_, false, p_294804_, References.BLOCK_ENTITY, p_295913_);
    }

    private static <T> Dynamic<T> fix(Dynamic<T> p_295232_) {
        p_295232_ = p_295232_.update("front_text", DropInvalidSignDataFix::fixText);
        p_295232_ = p_295232_.update("back_text", DropInvalidSignDataFix::fixText);

        for (String s : FIELDS_TO_DROP) {
            p_295232_ = p_295232_.remove(s);
        }

        return p_295232_;
    }

    private static <T> Dynamic<T> fixText(Dynamic<T> p_296074_) {
        boolean flag = p_296074_.get("_filtered_correct").asBoolean(false);
        if (flag) {
            return p_296074_.remove("_filtered_correct");
        } else {
            Optional<Stream<Dynamic<T>>> optional = p_296074_.get("filtered_messages").asStreamOpt().result();
            if (optional.isEmpty()) {
                return p_296074_;
            } else {
                Dynamic<T> dynamic = ComponentDataFixUtils.createEmptyComponent(p_296074_.getOps());
                List<Dynamic<T>> list = p_296074_.get("messages").asStreamOpt().result().orElse(Stream.of()).toList();
                List<Dynamic<T>> list1 = Streams.mapWithIndex(optional.get(), (p_294909_, p_296017_) -> {
                    Dynamic<T> dynamic1 = p_296017_ < (long)list.size() ? list.get((int)p_296017_) : dynamic;
                    return p_294909_.equals(dynamic) ? dynamic1 : p_294909_;
                }).toList();
                return list1.stream().allMatch(p_296400_ -> p_296400_.equals(dynamic))
                    ? p_296074_.remove("filtered_messages")
                    : p_296074_.set("filtered_messages", p_296074_.createList(list1.stream()));
            }
        }
    }

    @Override
    protected Typed<?> fix(Typed<?> p_295043_) {
        return p_295043_.update(DSL.remainderFinder(), DropInvalidSignDataFix::fix);
    }
}
