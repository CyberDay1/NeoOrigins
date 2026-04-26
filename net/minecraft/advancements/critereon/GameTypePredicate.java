package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.GameType;

public record GameTypePredicate(List<GameType> types) {
    public static final GameTypePredicate ANY = of(GameType.values());
    public static final GameTypePredicate SURVIVAL_LIKE = of(GameType.SURVIVAL, GameType.ADVENTURE);
    public static final Codec<GameTypePredicate> CODEC = GameType.CODEC.listOf().xmap(GameTypePredicate::new, GameTypePredicate::types);

    public static GameTypePredicate of(GameType... p_350351_) {
        return new GameTypePredicate(Arrays.stream(p_350351_).toList());
    }

    public boolean matches(GameType p_351023_) {
        return this.types.contains(p_351023_);
    }
}
