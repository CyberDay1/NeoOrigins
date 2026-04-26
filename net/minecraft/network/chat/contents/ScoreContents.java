package net.minecraft.network.chat.contents;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

public class ScoreContents implements ComponentContents {
    public static final MapCodec<ScoreContents> INNER_CODEC = RecordCodecBuilder.mapCodec(
        p_304795_ -> p_304795_.group(
                    Codec.STRING.fieldOf("name").forGetter(ScoreContents::getName), Codec.STRING.fieldOf("objective").forGetter(ScoreContents::getObjective)
                )
                .apply(p_304795_, ScoreContents::new)
    );
    public static final MapCodec<ScoreContents> CODEC = INNER_CODEC.fieldOf("score");
    public static final ComponentContents.Type<ScoreContents> TYPE = new ComponentContents.Type<>(CODEC, "score");
    private final String name;
    @Nullable
    private final EntitySelector selector;
    private final String objective;

    @Nullable
    private static EntitySelector parseSelector(String p_237448_) {
        try {
            return new EntitySelectorParser(new StringReader(p_237448_), true).parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
            return null;
        }
    }

    public ScoreContents(String p_237438_, String p_237439_) {
        this.name = p_237438_;
        this.selector = parseSelector(p_237438_);
        this.objective = p_237439_;
    }

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }

    public String getName() {
        return this.name;
    }

    @Nullable
    public EntitySelector getSelector() {
        return this.selector;
    }

    public String getObjective() {
        return this.objective;
    }

    private ScoreHolder findTargetName(CommandSourceStack p_237442_) throws CommandSyntaxException {
        if (this.selector != null) {
            List<? extends Entity> list = this.selector.findEntities(p_237442_);
            if (!list.isEmpty()) {
                if (list.size() != 1) {
                    throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
                }

                return list.get(0);
            }
        }

        return ScoreHolder.forNameOnly(this.name);
    }

    private MutableComponent getScore(ScoreHolder p_313863_, CommandSourceStack p_237451_) {
        MinecraftServer minecraftserver = p_237451_.getServer();
        if (minecraftserver != null) {
            Scoreboard scoreboard = minecraftserver.getScoreboard();
            Objective objective = scoreboard.getObjective(this.objective);
            if (objective != null) {
                ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(p_313863_, objective);
                if (readonlyscoreinfo != null) {
                    return readonlyscoreinfo.formatValue(objective.numberFormatOrDefault(StyledFormat.NO_STYLE));
                }
            }
        }

        return Component.empty();
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack p_237444_, @Nullable Entity p_237445_, int p_237446_) throws CommandSyntaxException {
        if (p_237444_ == null) {
            return Component.empty();
        } else {
            ScoreHolder scoreholder = this.findTargetName(p_237444_);
            ScoreHolder scoreholder1 = (ScoreHolder)(p_237445_ != null && scoreholder.equals(ScoreHolder.WILDCARD) ? p_237445_ : scoreholder);
            return this.getScore(scoreholder1, p_237444_);
        }
    }

    @Override
    public boolean equals(Object p_237455_) {
        if (this == p_237455_) {
            return true;
        } else {
            if (p_237455_ instanceof ScoreContents scorecontents && this.name.equals(scorecontents.name) && this.objective.equals(scorecontents.objective)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = this.name.hashCode();
        return 31 * i + this.objective.hashCode();
    }

    @Override
    public String toString() {
        return "score{name='" + this.name + "', objective='" + this.objective + "'}";
    }
}
