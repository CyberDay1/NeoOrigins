package net.minecraft.network.chat.contents;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

public class SelectorContents implements ComponentContents {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SelectorContents> CODEC = RecordCodecBuilder.mapCodec(
        p_337511_ -> p_337511_.group(
                    Codec.STRING.fieldOf("selector").forGetter(SelectorContents::getPattern),
                    ComponentSerialization.CODEC.optionalFieldOf("separator").forGetter(SelectorContents::getSeparator)
                )
                .apply(p_337511_, SelectorContents::new)
    );
    public static final ComponentContents.Type<SelectorContents> TYPE = new ComponentContents.Type<>(CODEC, "selector");
    private final String pattern;
    @Nullable
    private final EntitySelector selector;
    protected final Optional<Component> separator;

    public SelectorContents(String p_237464_, Optional<Component> p_237465_) {
        this.pattern = p_237464_;
        this.separator = p_237465_;
        this.selector = parseSelector(p_237464_);
    }

    @Nullable
    private static EntitySelector parseSelector(String p_237472_) {
        EntitySelector entityselector = null;

        try {
            EntitySelectorParser entityselectorparser = new EntitySelectorParser(new StringReader(p_237472_), true);
            entityselector = entityselectorparser.parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
            LOGGER.warn("Invalid selector component: {}: {}", p_237472_, commandsyntaxexception.getMessage());
        }

        return entityselector;
    }

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }

    public String getPattern() {
        return this.pattern;
    }

    @Nullable
    public EntitySelector getSelector() {
        return this.selector;
    }

    public Optional<Component> getSeparator() {
        return this.separator;
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack p_237468_, @Nullable Entity p_237469_, int p_237470_) throws CommandSyntaxException {
        if (p_237468_ != null && this.selector != null) {
            Optional<? extends Component> optional = ComponentUtils.updateForEntity(p_237468_, this.separator, p_237469_, p_237470_);
            return ComponentUtils.formatList(this.selector.findEntities(p_237468_), optional, Entity::getDisplayName);
        } else {
            return Component.empty();
        }
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> p_237476_, Style p_237477_) {
        return p_237476_.accept(p_237477_, this.pattern);
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> p_237474_) {
        return p_237474_.accept(this.pattern);
    }

    @Override
    public boolean equals(Object p_237481_) {
        if (this == p_237481_) {
            return true;
        } else {
            if (p_237481_ instanceof SelectorContents selectorcontents
                && this.pattern.equals(selectorcontents.pattern)
                && this.separator.equals(selectorcontents.separator)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = this.pattern.hashCode();
        return 31 * i + this.separator.hashCode();
    }

    @Override
    public String toString() {
        return "pattern{" + this.pattern + "}";
    }
}
